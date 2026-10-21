"""Hash-chained audit log. Never put a PIN, BVN or full account number in `detail`."""

import hashlib
import json
import threading

from sqlalchemy import select, text
from sqlalchemy.orm import Session

from ominira.database import SessionFactory, is_postgres, iso, utcnow
from ominira.tables import AuditRow

GENESIS = "0" * 64
_write_lock = threading.Lock()


def _content(row: AuditRow) -> dict:
    return {
        "seq": row.seq,
        "at": row.at.isoformat(timespec="microseconds"),
        "customer_id": row.customer_id,
        "actor": row.actor,
        "action": row.action,
        "draft_id": row.draft_id,
        "detail": row.detail,
    }


def _digest(prev_hash: str, content: dict) -> str:
    canonical = json.dumps(content, sort_keys=True, separators=(",", ":"), ensure_ascii=False, default=str)
    return hashlib.sha256((prev_hash + canonical).encode()).hexdigest()


def record(action: str, *, actor: str, customer_id: str | None = None, draft_id: str | None = None,
           **detail) -> str:
    """Own transaction. Call after committing your writes: SQLite has one writer."""
    with _write_lock, SessionFactory() as db:
        if is_postgres():
            db.execute(text("SELECT pg_advisory_xact_lock(74471)"))  # one writer across instances
        last = db.scalars(select(AuditRow).order_by(AuditRow.seq.desc()).limit(1)).first()
        row = AuditRow(
            seq=(last.seq + 1) if last else 1,
            at=utcnow(),
            customer_id=customer_id,
            actor=actor,
            action=action,
            draft_id=draft_id,
            detail=detail,
            prev_hash=last.entry_hash if last else GENESIS,
            entry_hash="",
        )
        row.entry_hash = _digest(row.prev_hash, _content(row))
        db.add(row)
        db.commit()
        return row.entry_hash


def verify(db: Session) -> dict:
    expected_prev = GENESIS
    expected_seq = 1
    count = 0
    for row in db.scalars(select(AuditRow).order_by(AuditRow.seq)):
        count += 1
        if (row.seq != expected_seq or row.prev_hash != expected_prev
                or _digest(row.prev_hash, _content(row)) != row.entry_hash):
            return {"ok": False, "broken_at": row.seq, "entries": count}
        expected_prev = row.entry_hash
        expected_seq += 1
    return {"ok": True, "entries": count}


def for_customer(db: Session, customer_id: str, limit: int = 50) -> list[dict]:
    rows = db.scalars(
        select(AuditRow).where(AuditRow.customer_id == customer_id).order_by(AuditRow.seq.desc()).limit(limit)
    )
    return [{"seq": r.seq, "at": iso(r.at), "actor": r.actor, "action": r.action, "draft_id": r.draft_id,
             "detail": r.detail, "entry_hash": r.entry_hash} for r in rows]
