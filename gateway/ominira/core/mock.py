"""A mock of Zenith's core that behaves like the rails instead of always saying yes."""

import hashlib
import random
import secrets
import threading
import time
from dataclasses import asdict, dataclass, field
from datetime import timedelta

from sqlalchemy import func, select
from sqlalchemy.orm import Session, sessionmaker

from ominira.core import base
from ominira.core.base import (
    Account, Bank, BvnCheck, Card, CoreError, CoreTimeout, IdDocument, Limits, MoveResult, NameEnquiry,
    PinCheck, Profile, Txn, ZenithCore,
)
from ominira.core.mock_tables import (
    MockAccount, MockBeneficiary, MockCard, MockCustomer, MockDirectoryEntry, MockEnquiry, MockMove, MockTxn,
)
from ominira.database import iso, utcnow

BANKS = (
    Bank("057", "Zenith Bank Plc", "Zenith Bank"),
    Bank("058", "Guaranty Trust Bank Plc", "GTBank"),
    Bank("044", "Access Bank Plc", "Access Bank"),
    Bank("011", "First Bank of Nigeria Ltd", "First Bank"),
    Bank("033", "United Bank for Africa Plc", "UBA"),
    Bank("232", "Sterling Bank Plc", "Sterling Bank"),
    Bank("035", "Wema Bank Plc", "Wema Bank"),
    Bank("999992", "OPay Digital Services Ltd", "OPay"),
    Bank("999991", "PalmPay Ltd", "PalmPay"),
    Bank("50515", "Moniepoint MFB", "Moniepoint"),
    Bank("50211", "Kuda Microfinance Bank", "Kuda"),
)
_BY_CODE = {b.code: b for b in BANKS}

_GIVEN = ("Chinedu", "Aisha", "Oluwaseun", "Ngozi", "Ibrahim", "Funmilayo", "Emeka", "Halima",
          "Babatunde", "Chiamaka", "Yusuf", "Temitope", "Obinna", "Zainab", "Adewale", "Amaka")
_FAMILY = ("Okafor", "Bello", "Adeleke", "Eze", "Abubakar", "Ogunleye", "Nwosu", "Danjuma",
           "Balogun", "Okonkwo", "Lawal", "Adeyemi", "Umeh", "Garba", "Olatunji", "Chukwu")

# Seconds each kind of call takes: (low, high).
_LATENCY = {
    "realistic": {"enquiry": (0.4, 1.2), "pin": (0.2, 0.5), "move": (0.8, 2.2), "read": (0.1, 0.3)},
    "flaky": {"enquiry": (0.6, 2.0), "pin": (0.2, 0.6), "move": (1.0, 3.5), "read": (0.1, 0.4)},
    "fast": {"enquiry": (0, 0), "pin": (0, 0), "move": (0, 0), "read": (0, 0)},
}

TIMEOUT_MODES = ("after_send", "before_send")


@dataclass
class Controls:
    """Steering for rehearsals: show judges a failure on purpose."""

    profile: str = "realistic"
    banks_down: set[str] = field(default_factory=set)
    next_transfer_code: str | None = None  # used once, then cleared
    next_transfer_timeout: str | None = None  # "after_send" or "before_send"; used once
    pending_rate: float = 0.0  # share of transfers answered 09 first
    failure_rate: float = 0.0  # share of transfers answered 91 or 96
    settle_after_s: float = 3.0  # an "in progress" transfer settles this long after it was sent

    @classmethod
    def for_profile(cls, profile: str) -> "Controls":
        if profile == "flaky":
            return cls(profile, banks_down={"50211"}, pending_rate=0.15, failure_rate=0.08)
        return cls(profile if profile in _LATENCY else "realistic")

    def as_dict(self) -> dict:
        d = asdict(self)
        d["banks_down"] = sorted(self.banks_down)
        return d


def unseal_pin_block(pin_block: str) -> str | None:
    """'demo-' then each digit as 9 minus the digit: PIN 2075 is 'demo-7924'."""
    if not isinstance(pin_block, str) or not pin_block.startswith("demo-"):
        return None
    sealed = pin_block[len("demo-"):]
    if len(sealed) != 4 or not sealed.isdigit():
        return None
    return "".join(str(9 - int(c)) for c in sealed)


def generated_name(bank_code: str, number: str) -> str:
    """The same account always gets the same name, NIP style: SURNAME FIRSTNAME."""
    h = int.from_bytes(hashlib.sha256(f"{bank_code}/{number}".encode()).digest()[:8], "big")
    return f"{_FAMILY[h % len(_FAMILY)]} {_GIVEN[(h >> 16) % len(_GIVEN)]}".upper()


class MockZenith(ZenithCore):
    kind = "mock"

    def __init__(self, sessions: sessionmaker, controls: Controls, rng: random.Random | None = None):
        self._sessions = sessions
        self.controls = controls
        self._rng = rng or random.Random()
        self._money_lock = threading.Lock()  # one money movement at a time, like an account lock

    # ---- plumbing ---------------------------------------------------------------------------

    def _pause(self, op: str) -> None:
        low, high = _LATENCY.get(self.controls.profile, _LATENCY["realistic"])[op]
        if high > 0:
            time.sleep(self._rng.uniform(low, high))

    def _reference(self, prefix: str, n: int) -> str:
        return prefix + "".join(str(self._rng.randint(0, 9)) for _ in range(n))

    @staticmethod
    def _customer(db: Session, ref: str) -> MockCustomer:
        c = db.get(MockCustomer, ref)
        if c is None:
            raise CoreError(base.NO_RECORD, "unknown customer")
        return c

    @staticmethod
    def _main_account(db: Session, ref: str) -> MockAccount:
        acct = db.scalars(select(MockAccount).where(MockAccount.customer_ref == ref).order_by(MockAccount.id)).first()
        if acct is None:
            raise CoreError(base.NO_RECORD, "no account")
        return acct

    @staticmethod
    def _used_today(db: Session, ref: str) -> int:
        midnight = utcnow().replace(hour=0, minute=0, second=0, microsecond=0)
        total = db.scalar(
            select(func.coalesce(func.sum(MockMove.amount_kobo), 0)).where(
                MockMove.customer_ref == ref,
                MockMove.kind == "transfer",
                MockMove.code.in_((base.APPROVED, base.IN_PROGRESS)),
                MockMove.at >= midnight,
            )
        )
        return int(total or 0)

    # ---- reads ------------------------------------------------------------------------------

    def banks(self) -> list[Bank]:
        return list(BANKS)

    def profile(self, customer_ref: str) -> Profile:
        with self._sessions() as db:
            c = self._customer(db, customer_ref)
            return Profile(c.full_name, c.first_name)

    def accounts(self, customer_ref: str) -> list[Account]:
        self._pause("read")
        with self._sessions() as db:
            rows = db.scalars(select(MockAccount).where(MockAccount.customer_ref == customer_ref).order_by(MockAccount.id))
            return [Account(a.id, a.name, a.number[-4:], a.balance_kobo) for a in rows]

    def transactions(self, customer_ref: str, limit: int = 20) -> list[Txn]:
        self._pause("read")
        with self._sessions() as db:
            acct = self._main_account(db, customer_ref)
            rows = db.scalars(
                select(MockTxn).where(MockTxn.account_id == acct.id)
                .order_by(MockTxn.at.desc(), MockTxn.id.desc()).limit(limit)
            )
            return [Txn(t.id, t.who, t.amount_kobo, t.incoming, iso(t.at), t.reference) for t in rows]

    def cards(self, customer_ref: str) -> list[Card]:
        with self._sessions() as db:
            rows = db.scalars(select(MockCard).where(MockCard.customer_ref == customer_ref).order_by(MockCard.id))
            return [Card(c.id, c.last4, c.holder, c.status) for c in rows]

    def limits(self, customer_ref: str) -> Limits:
        with self._sessions() as db:
            c = self._customer(db, customer_ref)
            return Limits(c.per_transaction_kobo, c.daily_kobo, self._used_today(db, customer_ref))

    # ---- name enquiry -----------------------------------------------------------------------

    def name_enquiry(self, bank_code: str, account_number: str) -> NameEnquiry:
        self._pause("enquiry")
        bank = _BY_CODE.get(bank_code)
        if bank is None:
            raise CoreError(base.UNKNOWN_BANK, "unknown bank code")
        if bank_code in self.controls.banks_down:
            raise CoreError(base.BANK_UNAVAILABLE, f"{bank.short} is not responding")
        number = account_number.strip()
        if len(number) != 10 or not number.isdigit() or len(set(number)) == 1:
            raise CoreError(base.INVALID_ACCOUNT, "invalid account number")
        with self._sessions() as db:
            known = db.scalars(
                select(MockDirectoryEntry).where(MockDirectoryEntry.bank_code == bank_code,
                                                 MockDirectoryEntry.number == number)
            ).first()
            if (known is not None and known.status != "active") or (known is None and number.endswith("0006")):
                raise CoreError(base.DORMANT, "account is dormant")
            name = known.name if known else generated_name(bank_code, number)
            enquiry = MockEnquiry(ref="NE" + secrets.token_hex(8), bank_code=bank_code, number=number, name=name)
            db.add(enquiry)
            db.commit()
            return NameEnquiry(enquiry.ref, name, bank, number[-4:])

    def beneficiary_enquiry(self, customer_ref: str, beneficiary_ref: str) -> NameEnquiry:
        with self._sessions() as db:
            ben = db.get(MockBeneficiary, beneficiary_ref)
            if ben is None or ben.customer_ref != customer_ref:
                raise CoreError(base.NO_RECORD, "unknown beneficiary")
            bank_code, number = ben.bank_code, ben.number
        return self.name_enquiry(bank_code, number)

    def save_beneficiary(self, customer_ref: str, enquiry_ref: str) -> str:
        with self._sessions() as db:
            enquiry = db.get(MockEnquiry, enquiry_ref)
            if enquiry is None:
                raise CoreError(base.NO_RECORD, "unknown name enquiry")
            existing = db.scalars(
                select(MockBeneficiary).where(MockBeneficiary.customer_ref == customer_ref,
                                              MockBeneficiary.bank_code == enquiry.bank_code,
                                              MockBeneficiary.number == enquiry.number)
            ).first()
            if existing is not None:
                return existing.ref
            ref = "BEN-" + secrets.token_hex(6)
            db.add(MockBeneficiary(ref=ref, customer_ref=customer_ref, bank_code=enquiry.bank_code,
                                   number=enquiry.number))
            db.commit()
            return ref

    def find_beneficiary(self, customer_ref: str, bank_code: str, account_number: str) -> str | None:
        with self._sessions() as db:
            row = db.scalars(
                select(MockBeneficiary).where(MockBeneficiary.customer_ref == customer_ref,
                                              MockBeneficiary.bank_code == bank_code,
                                              MockBeneficiary.number == account_number.strip())
            ).first()
            return row.ref if row else None

    # ---- PIN --------------------------------------------------------------------------------

    def verify_pin(self, customer_ref: str, pin_block: str) -> PinCheck:
        self._pause("pin")
        with self._sessions() as db:
            c = self._customer(db, customer_ref)
            if c.pin_tries_left <= 0:
                return PinCheck(False, 0, base.PIN_LOCKED)
            if unseal_pin_block(pin_block) == c.pin:
                c.pin_tries_left = 3
                db.commit()
                return PinCheck(True, 3)
            c.pin_tries_left -= 1
            db.commit()
            return PinCheck(False, c.pin_tries_left, base.PIN_LOCKED if c.pin_tries_left == 0 else base.WRONG_PIN)

    # ---- moving money -----------------------------------------------------------------------

    def _decide_code(self, db: Session, c: MockCustomer, acct: MockAccount, enquiry: MockEnquiry,
                     amount: int) -> str:
        forced, self.controls.next_transfer_code = self.controls.next_transfer_code, None
        if forced:
            return forced
        if enquiry.bank_code in self.controls.banks_down:
            return base.BANK_UNAVAILABLE
        if amount > c.per_transaction_kobo:
            return base.LIMIT_EXCEEDED
        if self._used_today(db, c.ref) + amount > c.daily_kobo:
            return base.LIMIT_EXCEEDED
        if amount > acct.balance_kobo:
            return base.INSUFFICIENT_FUNDS
        if self.controls.failure_rate and self._rng.random() < self.controls.failure_rate:
            return self._rng.choice((base.BANK_UNAVAILABLE, base.SYSTEM_MALFUNCTION))
        if self.controls.pending_rate and self._rng.random() < self.controls.pending_rate:
            return base.IN_PROGRESS
        return base.APPROVED

    def transfer(self, customer_ref: str, enquiry_ref: str, amount_kobo: int, idempotency_key: str,
                 narration: str) -> MoveResult:
        timeout, self.controls.next_transfer_timeout = self.controls.next_transfer_timeout, None
        if timeout == "before_send":
            self._pause("move")
            raise CoreTimeout("no answer; the request never reached the core")
        with self._money_lock:
            with self._sessions() as db:
                earlier = db.get(MockMove, idempotency_key)
                if earlier is not None:
                    result = self._settle(db, earlier)
                    if timeout == "after_send":
                        raise CoreTimeout("no answer in time")
                    return result
            self._pause("move")
            with self._sessions() as db:
                c = self._customer(db, customer_ref)
                acct = self._main_account(db, customer_ref)
                enquiry = db.get(MockEnquiry, enquiry_ref)
                if enquiry is None:
                    code = base.NO_RECORD
                else:
                    code = self._decide_code(db, c, acct, enquiry, amount_kobo)
                move = MockMove(idempotency_key=idempotency_key, customer_ref=customer_ref, kind="transfer",
                                amount_kobo=amount_kobo, code=code)
                if code in (base.APPROVED, base.IN_PROGRESS):
                    # Sent or in flight: the money leaves the account now, as a hold would.
                    move.reference = self._reference("ZEN-", 8)
                    if code == base.IN_PROGRESS:
                        move.settles_at = utcnow() + timedelta(seconds=self.controls.settle_after_s)
                    acct.balance_kobo -= amount_kobo
                    db.add(MockTxn(id="tx_" + secrets.token_hex(5), account_id=acct.id,
                                   who=" ".join(p.capitalize() for p in enquiry.name.split()),
                                   amount_kobo=amount_kobo, incoming=False, reference=move.reference))
                db.add(move)
                db.commit()
                result = MoveResult(move.code, move.reference)
        if timeout == "after_send":
            raise CoreTimeout("no answer in time; the transfer may have gone")
        return result

    def transfer_status(self, idempotency_key: str) -> MoveResult:
        self._pause("read")
        with self._sessions() as db:
            move = db.get(MockMove, idempotency_key)
            if move is None:
                return MoveResult(base.NO_RECORD)
            return self._settle(db, move)

    @staticmethod
    def _settle(db: Session, move: MockMove) -> MoveResult:
        if move.code == base.IN_PROGRESS and move.settles_at is not None and utcnow() >= move.settles_at:
            move.code = base.APPROVED
            db.commit()
        return MoveResult(move.code, move.reference)

    def block_card(self, customer_ref: str, card_id: str, idempotency_key: str) -> MoveResult:
        with self._money_lock:
            with self._sessions() as db:
                earlier = db.get(MockMove, idempotency_key)
                if earlier is not None:
                    return MoveResult(earlier.code, earlier.reference)
            self._pause("pin")
            with self._sessions() as db:
                card = db.get(MockCard, card_id)
                move = MockMove(idempotency_key=idempotency_key, customer_ref=customer_ref, kind="card_block",
                                code=base.APPROVED)
                if card is None or card.customer_ref != customer_ref:
                    move.code = base.NO_RECORD
                else:
                    card.status = "blocked"
                    move.reference = self._reference("CRD-", 6)
                db.add(move)
                db.commit()
                return MoveResult(move.code, move.reference)

    # ---- onboarding -------------------------------------------------------------------------

    def read_id_document(self, session_id: str) -> IdDocument:
        # The camera is simulated, so every ID card reads as the demo customer's.
        self._pause("enquiry")
        return IdDocument("Ronke Adeyemi", "5521", "1961-03-14")

    def check_bvn(self, bvn: str, document: IdDocument) -> BvnCheck:
        """Any 11 digits match, except 12345678901, which belongs to someone else."""
        self._pause("enquiry")
        return BvnCheck(len(bvn) == 11 and bvn.isdigit() and bvn != "12345678901")

    # ---- rehearsal helpers (not part of ZenithCore) ------------------------------------------

    def settle_all_now(self) -> int:
        """Lets every 'in progress' transfer settle on its next status check."""
        with self._sessions() as db:
            rows = list(db.scalars(select(MockMove).where(MockMove.code == base.IN_PROGRESS)))
            for move in rows:
                move.settles_at = utcnow()
            db.commit()
            return len(rows)

    def snapshot(self) -> dict:
        with self._sessions() as db:
            customers = list(db.scalars(select(MockCustomer).order_by(MockCustomer.ref)))
            accounts = list(db.scalars(select(MockAccount).order_by(MockAccount.id)))
            cards = list(db.scalars(select(MockCard).order_by(MockCard.id)))
            return {
                "controls": self.controls.as_dict(),
                "balances": [{"account_id": a.id, "last4": a.number[-4:], "balance_kobo": a.balance_kobo}
                             for a in accounts],
                "cards": [{"id": c.id, "last4": c.last4, "status": c.status} for c in cards],
                "pin_tries_left": {c.ref: c.pin_tries_left for c in customers},
            }
