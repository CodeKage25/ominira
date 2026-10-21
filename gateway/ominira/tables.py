"""The Gateway's own tables.

What Zenith owns stays at Zenith: no PIN, PIN block, BVN, NIN or balance is stored here. A full
account number lives in a draft's payload only while that draft is open (the gate must repeat the
name enquiry), and is removed when the draft closes.
"""

from datetime import datetime

from sqlalchemy import JSON, BigInteger, Boolean, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from ominira.database import Base, utcnow


class Customer(Base):
    __tablename__ = "customers"

    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    core_ref: Mapped[str] = mapped_column(String(64), unique=True)
    phone: Mapped[str] = mapped_column(String(20))
    lang: Mapped[str] = mapped_column(String(4), default="en")
    modes: Mapped[list] = mapped_column(JSON, default=lambda: ["voice"])
    theme: Mapped[str] = mapped_column(String(16), default="system")
    speech_rate: Mapped[float] = mapped_column(default=0.95)
    pulse_gap_ms: Mapped[int] = mapped_column(Integer, default=1000)
    # The customer's own limits. Zenith's limits are the ceiling; these only ever lower them.
    daily_limit_kobo: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    pause_threshold_kobo: Mapped[int | None] = mapped_column(BigInteger, nullable=True)


class Approver(Base):
    __tablename__ = "approvers"

    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    customer_id: Mapped[str] = mapped_column(ForeignKey("customers.id"))
    name: Mapped[str] = mapped_column(String(60))
    phone: Mapped[str] = mapped_column(String(20))
    threshold_kobo: Mapped[int] = mapped_column(BigInteger)
    active: Mapped[bool] = mapped_column(Boolean, default=True)


class Token(Base):
    """Stand-in for device-bound passkeys. role: customer, approver or assistant."""

    __tablename__ = "tokens"

    token: Mapped[str] = mapped_column(String(64), primary_key=True)
    role: Mapped[str] = mapped_column(String(16))
    customer_id: Mapped[str] = mapped_column(ForeignKey("customers.id"))
    approver_id: Mapped[str | None] = mapped_column(ForeignKey("approvers.id"), nullable=True)


class Payee(Base):
    __tablename__ = "payees"

    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    customer_id: Mapped[str] = mapped_column(ForeignKey("customers.id"))
    nickname: Mapped[str] = mapped_column(String(40))
    name: Mapped[str] = mapped_column(String(120))
    bank_code: Mapped[str] = mapped_column(String(10))
    bank: Mapped[str] = mapped_column(String(60))
    account_last4: Mapped[str] = mapped_column(String(4))
    beneficiary_ref: Mapped[str] = mapped_column(String(64))
    first_paid_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    # Set when a scam pause on a payment to this person ended in "stop".
    flagged: Mapped[bool] = mapped_column(Boolean, default=False)

    @property
    def is_new(self) -> bool:
        return self.first_paid_at is None


class Draft(Base):
    __tablename__ = "drafts"

    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    customer_id: Mapped[str] = mapped_column(ForeignKey("customers.id"))
    kind: Mapped[str] = mapped_column(String(16))  # transfer | card_block
    status: Mapped[str] = mapped_column(String(20), default="awaiting_confirm")
    source: Mapped[str] = mapped_column(String(12), default="app")
    # transfer: amount_kobo plus payee_id, or bank_code and account_number; enquiry_ref.
    # card_block: card_id.
    payload: Mapped[dict] = mapped_column(JSON)
    readback: Mapped[dict] = mapped_column(JSON)
    readback_hash: Mapped[str] = mapped_column(String(80))
    # Zenith's idempotency key is "{id}:{attempt}". It moves on only after a definite
    # "not sent" (NIP 91 or 96), never after a timeout or "in progress".
    attempt: Mapped[int] = mapped_column(Integer, default=1)
    pause_answer: Mapped[str | None] = mapped_column(String(16), nullable=True)
    paused_on_call: Mapped[bool] = mapped_column(Boolean, default=False)
    reference: Mapped[str | None] = mapped_column(String(40), nullable=True)
    last_say: Mapped[str | None] = mapped_column(Text, nullable=True)
    # Why an expired draft expired: "draft" (10 minutes) or "approval" (approver too slow).
    expired_by: Mapped[str | None] = mapped_column(String(12), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    expires_at: Mapped[datetime] = mapped_column(DateTime)


class Approval(Base):
    __tablename__ = "approvals"

    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    draft_id: Mapped[str] = mapped_column(ForeignKey("drafts.id"))
    approver_id: Mapped[str] = mapped_column(ForeignKey("approvers.id"))
    # pending | approved | declined | expired | withdrawn
    status: Mapped[str] = mapped_column(String(12), default="pending")
    asked_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    decided_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)


class Conversation(Base):
    __tablename__ = "conversations"

    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    customer_id: Mapped[str] = mapped_column(ForeignKey("customers.id"))
    # "Send money to Tolu", then "twenty thousand": who the unfinished request is for.
    waiting_payee_id: Mapped[str | None] = mapped_column(String(32), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class Turn(Base):
    __tablename__ = "turns"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    conversation_id: Mapped[str] = mapped_column(ForeignKey("conversations.id"))
    heard: Mapped[str] = mapped_column(Text)  # masked: no account numbers, BVNs or PINs
    reply: Mapped[str] = mapped_column(Text)
    engine: Mapped[str] = mapped_column(String(12))
    draft_id: Mapped[str | None] = mapped_column(String(32), nullable=True)
    took_ms: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class Onboarding(Base):
    __tablename__ = "onboarding"

    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    liveness_digits: Mapped[list] = mapped_column(JSON)
    lang: Mapped[str | None] = mapped_column(String(4), nullable=True)  # picked on the app's first screen
    live: Mapped[bool | None] = mapped_column(Boolean, nullable=True)
    bvn_matched: Mapped[bool] = mapped_column(Boolean, default=False)
    completed: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class AuditRow(Base):
    """Append-only and hash-chained. In Postgres the app's role gets INSERT and SELECT only."""

    __tablename__ = "audit_log"

    seq: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=False)
    at: Mapped[datetime] = mapped_column(DateTime)
    customer_id: Mapped[str | None] = mapped_column(String(32), nullable=True)
    actor: Mapped[str] = mapped_column(String(16))  # customer | approver | assistant | system
    action: Mapped[str] = mapped_column(String(40))
    draft_id: Mapped[str | None] = mapped_column(String(32), nullable=True)
    detail: Mapped[dict] = mapped_column(JSON)
    prev_hash: Mapped[str] = mapped_column(String(64))
    entry_hash: Mapped[str] = mapped_column(String(64), unique=True)


class StoredResponse(Base):
    """A response kept for Idempotency-Key replays."""

    __tablename__ = "idempotency"

    scope_key: Mapped[str] = mapped_column(String(64), primary_key=True)
    request_hash: Mapped[str] = mapped_column(String(64))
    status_code: Mapped[int] = mapped_column(Integer)
    headers: Mapped[dict] = mapped_column(JSON)
    body: Mapped[bytes] = mapped_column()
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
