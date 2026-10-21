"""Drafts: every money action starts as one. The customer or the assistant creates it, the app
reads the readback aloud, and only the confirm gate can carry it out."""

import secrets
from datetime import timedelta

from sqlalchemy.orm import Session

from ominira import audit_log, lines, readback
from ominira.core import base, get_core
from ominira.core.base import CoreError, NameEnquiry
from ominira.database import iso, utcnow
from ominira.problems import Problem
from ominira.settings import settings
from ominira.tables import Customer, Draft, Payee

OPEN_STATUSES = ("awaiting_confirm", "paused", "awaiting_approval")
SOURCES = ("app", "assistant", "voice", "ussd")


def new_id(prefix: str, nbytes: int = 5) -> str:
    return prefix + secrets.token_hex(nbytes)


def enquiry_problem(err: CoreError, lang: str, bank_label: str) -> Problem:
    """What a refused name enquiry means for the customer."""
    if err.code in (base.INVALID_ACCOUNT, base.NO_RECORD):
        return Problem(422, "invalid_account", "Account not found", lines.say(lang, "invalid_account"))
    if err.code == base.DORMANT:
        return Problem(422, "account_dormant", "Account cannot receive money", lines.say(lang, "dormant"))
    if err.code == base.UNKNOWN_BANK:
        return Problem(422, "unknown_bank", "Unknown bank code", lines.say(lang, "invalid_account"))
    return Problem(503, "bank_unavailable", "The bank is not answering",
                   lines.say(lang, "bank_unavailable", bank=bank_label), retry_after=30, zenith_code=err.code)


def enquire_account(lang: str, bank_code: str, number: str) -> NameEnquiry:
    core = get_core()
    bank = core.bank(bank_code)
    try:
        return core.name_enquiry(bank_code, number)
    except CoreError as err:
        raise enquiry_problem(err, lang, bank.short if bank else "The other bank") from err


def enquire_payee(lang: str, customer: Customer, payee: Payee) -> NameEnquiry:
    try:
        return get_core().beneficiary_enquiry(customer.core_ref, payee.beneficiary_ref)
    except CoreError as err:
        raise enquiry_problem(err, lang, payee.bank) from err


def _flag_warning(lang: str, payee: Payee | None) -> str | None:
    return lines.say(lang, "payee_flagged") if payee is not None and payee.flagged else None


def _transfer_readback(lang: str, enquiry: NameEnquiry, amount_kobo: int, payee: Payee | None) -> dict:
    return readback.for_transfer(lang, name=readback.nip_name(enquiry.account_name), bank=enquiry.bank.short,
                                 last4=enquiry.last4, amount_kobo=amount_kobo, warning=_flag_warning(lang, payee))


def _store(db: Session, customer: Customer, kind: str, payload: dict, rb: dict, source: str, actor: str) -> Draft:
    draft = Draft(id=new_id("dr_", 6), customer_id=customer.id, kind=kind, status="awaiting_confirm",
                  source=source, payload=payload, readback=rb, readback_hash=readback.readback_hash(rb),
                  expires_at=utcnow() + timedelta(seconds=settings.draft_lifetime_s))
    db.add(draft)
    db.commit()
    audit_log.record("draft_created", actor=actor, customer_id=customer.id, draft_id=draft.id, kind=kind,
                     source=source, amount_kobo=rb.get("amount_kobo"), account_last4=rb.get("account_last4"))
    return draft


def new_transfer(db: Session, customer: Customer, *, amount_kobo: int, payee_id: str | None = None,
                 bank_code: str | None = None, account_number: str | None = None, source: str = "app",
                 actor: str = "customer") -> Draft:
    lang = customer.lang
    if amount_kobo is None or amount_kobo <= 0:
        raise Problem(422, "invalid_amount", "The amount must be more than zero")
    if payee_id:
        payee = db.get(Payee, payee_id)
        if payee is None or payee.customer_id != customer.id:
            raise Problem(404, "payee_not_found", "No such payee")
        enquiry = enquire_payee(lang, customer, payee)
        payload = {"amount_kobo": amount_kobo, "payee_id": payee.id}
    elif bank_code and account_number:
        payee = None
        enquiry = enquire_account(lang, bank_code, account_number)
        # Kept only while the draft is open: the gate repeats the name enquiry before sending.
        payload = {"amount_kobo": amount_kobo, "bank_code": bank_code, "account_number": account_number}
    else:
        raise Problem(422, "invalid_request", "Give payee_id, or bank_code and account_number")
    payload["enquiry_ref"] = enquiry.ref
    rb = _transfer_readback(lang, enquiry, amount_kobo, payee)
    return _store(db, customer, "transfer", payload, rb, source, actor)


def new_card_block(db: Session, customer: Customer, *, card_id: str, source: str = "app",
                   actor: str = "customer") -> Draft:
    card = next((c for c in get_core().cards(customer.core_ref) if c.id == card_id), None)
    if card is None:
        raise Problem(404, "card_not_found", "No such card")
    if card.status == "blocked":
        raise Problem(409, "already_blocked", "That card is already blocked", lines.say(customer.lang, "card_blocked"))
    rb = readback.for_card_block(customer.lang, last4=card.last4)
    return _store(db, customer, "card_block", {"card_id": card.id}, rb, source, actor)


def payee_of(db: Session, draft: Draft) -> Payee | None:
    pid = draft.payload.get("payee_id")
    return db.get(Payee, pid) if pid else None


def rebuild_readback(db: Session, customer: Customer, draft: Draft) -> tuple[dict, str | None]:
    """Run the name enquiry again and rebuild what the customer should hear, in the language the
    draft was read in. Returns (readback, fresh enquiry ref). Raises the enquiry's Problem."""
    lang = draft.readback.get("lang", customer.lang)
    if draft.kind == "card_block":
        card = next((c for c in get_core().cards(customer.core_ref) if c.id == draft.payload["card_id"]), None)
        if card is None or card.status == "blocked":
            raise Problem(409, "already_blocked", "That card is already blocked", lines.say(lang, "card_blocked"))
        return readback.for_card_block(lang, last4=card.last4), None
    payee = payee_of(db, draft)
    if payee is not None:
        enquiry = enquire_payee(lang, customer, payee)
    else:
        enquiry = enquire_account(lang, draft.payload["bank_code"], draft.payload["account_number"])
    return _transfer_readback(lang, enquiry, draft.payload["amount_kobo"], payee), enquiry.ref


def view(draft: Draft) -> dict:
    return {
        "id": draft.id,
        "kind": draft.kind,
        "status": draft.status,
        "source": draft.source,
        "readback": draft.readback,
        "readback_hash": draft.readback_hash,
        "expires_at": iso(draft.expires_at),
        "reference": draft.reference,
        "say": draft.last_say,
    }
