"""The interface to Zenith's core. Codes follow NIP and ISO 8583."""

from abc import ABC, abstractmethod
from dataclasses import dataclass

APPROVED = "00"
DORMANT = "06"
INVALID_ACCOUNT = "07"
IN_PROGRESS = "09"
UNKNOWN_BANK = "16"
NO_RECORD = "25"
INSUFFICIENT_FUNDS = "51"
WRONG_PIN = "55"
LIMIT_EXCEEDED = "61"
PIN_LOCKED = "75"
BANK_UNAVAILABLE = "91"
SYSTEM_MALFUNCTION = "96"

# Definitely not sent: safe to retry under a new key.
NOT_SENT_RETRYABLE = frozenset({BANK_UNAVAILABLE, SYSTEM_MALFUNCTION})


class CoreError(Exception):
    """The core answered with a refusal (a NIP code other than 00)."""

    def __init__(self, code: str, message: str = ""):
        super().__init__(f"{code} {message}".strip())
        self.code = code
        self.message = message


class CoreTimeout(Exception):
    """Outcome unknown. Re-query by the same key; never resend under a new one."""


@dataclass(frozen=True)
class Bank:
    code: str
    name: str
    short: str  # what we say aloud: "GTBank", not "Guaranty Trust Bank Plc"


@dataclass(frozen=True)
class NameEnquiry:
    ref: str  # quoted by the transfer, as NIP's session id is
    account_name: str  # as NIP returns it, e.g. "ADEBAYO TOLULOPE"
    bank: Bank
    last4: str


@dataclass(frozen=True)
class PinCheck:
    ok: bool
    tries_left: int
    code: str = APPROVED


@dataclass(frozen=True)
class MoveResult:
    """Outcome of a transfer or card block. 00 done, 09 still in progress, anything else refused."""

    code: str
    reference: str | None = None

    @property
    def done(self) -> bool:
        return self.code == APPROVED

    @property
    def in_progress(self) -> bool:
        return self.code == IN_PROGRESS


@dataclass(frozen=True)
class Limits:
    per_transaction_kobo: int
    daily_kobo: int
    used_today_kobo: int


@dataclass(frozen=True)
class Account:
    id: str
    name: str
    last4: str
    balance_kobo: int
    number: str = ""  # the customer's own, to give to someone paying them; never to the assistant


@dataclass(frozen=True)
class Txn:
    id: str
    who: str
    amount_kobo: int
    incoming: bool
    at: str  # ISO 8601 UTC with Z
    reference: str


@dataclass(frozen=True)
class Card:
    id: str
    last4: str
    holder: str
    status: str  # active | blocked


@dataclass(frozen=True)
class Profile:
    full_name: str
    first_name: str


@dataclass(frozen=True)
class IdDocument:
    full_name: str
    nin_last4: str
    born_on: str  # YYYY-MM-DD


@dataclass(frozen=True)
class BvnCheck:
    match: bool


class ZenithCore(ABC):
    kind: str = "abstract"

    # Reads
    @abstractmethod
    def banks(self) -> list[Bank]: ...

    @abstractmethod
    def profile(self, customer_ref: str) -> Profile: ...

    @abstractmethod
    def accounts(self, customer_ref: str) -> list[Account]: ...

    @abstractmethod
    def transactions(self, customer_ref: str, limit: int = 20) -> list[Txn]:
        """Newest first."""

    @abstractmethod
    def cards(self, customer_ref: str) -> list[Card]: ...

    @abstractmethod
    def limits(self, customer_ref: str) -> Limits: ...

    # Name enquiry and beneficiaries. Refusals raise CoreError.
    @abstractmethod
    def name_enquiry(self, bank_code: str, account_number: str) -> NameEnquiry: ...

    @abstractmethod
    def beneficiary_enquiry(self, customer_ref: str, beneficiary_ref: str) -> NameEnquiry: ...

    @abstractmethod
    def save_beneficiary(self, customer_ref: str, enquiry_ref: str) -> str: ...

    @abstractmethod
    def find_beneficiary(self, customer_ref: str, bank_code: str, account_number: str) -> str | None: ...

    # The PIN block goes to the core; the Gateway learns only ok or tries left.
    @abstractmethod
    def verify_pin(self, customer_ref: str, pin_block: str) -> PinCheck: ...

    # Moving money. The same idempotency key always returns the first outcome.
    @abstractmethod
    def transfer(self, customer_ref: str, enquiry_ref: str, amount_kobo: int, idempotency_key: str,
                 narration: str) -> MoveResult:
        """May raise CoreTimeout: then re-query with transfer_status."""

    @abstractmethod
    def transfer_status(self, idempotency_key: str) -> MoveResult:
        """25 (no record) means the request never arrived."""

    @abstractmethod
    def block_card(self, customer_ref: str, card_id: str, idempotency_key: str) -> MoveResult: ...

    # Onboarding
    @abstractmethod
    def read_id_document(self, session_id: str) -> IdDocument: ...

    @abstractmethod
    def check_bvn(self, bvn: str, document: IdDocument) -> BvnCheck: ...

    # The phone line: who owns this Zenith account number. None when nobody does.
    @abstractmethod
    def customer_for_account(self, account_number: str) -> str | None: ...

    # A new customer: their own account, card and an empty PIN until they choose one. Returns the core ref.
    @abstractmethod
    def open_customer(self, full_name: str) -> str: ...

    # The customer allows Zenith to find their BVN with NIBSS from their NIN and phone: nothing to say aloud.
    @abstractmethod
    def look_up_bvn(self, document: IdDocument, phone: str | None) -> BvnCheck: ...

    # The new customer's own PIN, sealed on the phone. False when it is too easy to guess.
    # A real core keeps only an offset, in an HSM.
    @abstractmethod
    def set_pin(self, customer_ref: str, pin_block: str) -> bool: ...

    def bank(self, code: str) -> Bank | None:
        return next((b for b in self.banks() if b.code == code), None)
