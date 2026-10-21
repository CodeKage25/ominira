"""One assistant turn: guard in code, then Claude (or the rules), then store the masked turn."""

import logging
import time
from dataclasses import dataclass

from sqlalchemy.orm import Session

from ominira import audit_log, lines
from ominira.assistant import claude, guard, memory, rules
from ominira.settings import settings
from ominira.tables import Conversation, Customer, Draft, Turn

log = logging.getLogger("ominira.assistant")


@dataclass
class TurnResult:
    heard: str  # masked
    reply: str
    engine: str  # claude | rules
    draft: Draft | None


def run_turn(db: Session, customer: Customer, conv: Conversation, text: str, source: str = "assistant") -> TurnResult:
    started = time.monotonic()
    mem = memory.get(conv.id)
    # Mask first: only masked text may reach a model or the database.
    masked, accounts = guard.mask(text, mem.labels)
    mem.labels = accounts  # ACCOUNT_1 must mean the same number every turn

    if guard.spoken_pin(text):
        # A turn that carried a PIN does nothing, and no model sees it.
        conv.waiting_payee_id = None
        mem.waiting_account = None
        reply, draft, engine = lines.say(customer.lang, "pin_spoken"), None, "rules"
        audit_log.record("pin_spoken_blocked", actor="system", customer_id=customer.id)
    elif settings.claude_enabled:
        try:
            reply, draft = claude.turn(db, customer, conv, masked, accounts, source)
            engine = "claude"
        except claude.ClaudeUnavailable as exc:
            log.warning("Claude unavailable, the rules answer: %s", exc)
            reply, draft = rules.turn(db, customer, conv, masked, mem, source)
            engine = "rules"
    else:
        reply, draft = rules.turn(db, customer, conv, masked, mem, source)
        engine = "rules"

    db.add(Turn(conversation_id=conv.id, heard=masked, reply=reply, engine=engine,
                draft_id=draft.id if draft else None, took_ms=int((time.monotonic() - started) * 1000)))
    db.commit()
    return TurnResult(heard=masked, reply=reply, engine=engine, draft=draft)
