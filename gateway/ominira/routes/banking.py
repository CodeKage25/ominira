"""Sign in, accounts, banks, payees, cards and preferences."""

from typing import Literal

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from ominira import amounts, audit_log, lines, pin, readback
from ominira.assistant import toolbox
from ominira.core import get_core
from ominira.database import get_session
from ominira.drafting import enquire_account
from ominira.problems import Problem
from ominira.security import Caller, customer, reader
from ominira.settings import settings
from ominira.tables import Customer

router = APIRouter(prefix="/v1")


# ---- sign in ----------------------------------------------------------------------------------

class UnlockIn(BaseModel):
    pin_block: str = Field(min_length=1, max_length=200)


@router.post("/auth/unlock")
def unlock(body: UnlockIn, caller: Caller = Depends(customer)):
    pin.require(caller.customer, caller.token, body.pin_block, purpose="unlock")
    audit_log.record("unlock", actor="customer", customer_id=caller.customer.id)
    return {"ok": True, "say": lines.say(caller.lang, "unlocked")}


# ---- accounts ---------------------------------------------------------------------------------

def _account(caller: Caller, account_id: str):
    acct = next((a for a in get_core().accounts(caller.customer.core_ref) if a.id == account_id), None)
    if acct is None:
        raise Problem(404, "not_found", "No such account")
    return acct


@router.get("/accounts")
def accounts(caller: Caller = Depends(reader)):
    return [{"id": a.id, "name": a.name, "last4": a.last4, "balance_kobo": a.balance_kobo}
            for a in get_core().accounts(caller.customer.core_ref)]


@router.get("/accounts/{account_id}/balance")
def balance(account_id: str, caller: Caller = Depends(reader)):
    acct = _account(caller, account_id)
    return {"balance_kobo": acct.balance_kobo,
            "say": lines.say(caller.lang, "balance", amount=amounts.spoken(acct.balance_kobo))}


@router.get("/accounts/{account_id}/transactions")
def transactions(account_id: str, limit: int = Query(20, ge=1, le=100),
                 direction: Literal["in", "out"] | None = None, caller: Caller = Depends(reader)):
    _account(caller, account_id)
    rows = get_core().transactions(caller.customer.core_ref, 100 if direction else limit)
    if direction:
        rows = [t for t in rows if t.incoming == (direction == "in")]
    return [{"id": t.id, "who": t.who, "amount_kobo": t.amount_kobo, "incoming": t.incoming, "at": t.at,
             "reference": t.reference} for t in rows[:limit]]


@router.get("/accounts/{account_id}/income-summary")
def income_summary(account_id: str, caller: Caller = Depends(reader)):
    _account(caller, account_id)
    return {"say": toolbox.money_in_this_week(caller.customer)}


@router.get("/banks")
def banks(_: Caller = Depends(reader)):
    return [{"code": b.code, "name": b.name, "short": b.short} for b in get_core().banks()]


# ---- payees -------------------------------------------------------------------------------------

@router.get("/payees")
def payees(caller: Caller = Depends(reader), db: Session = Depends(get_session)):
    return [{"id": p.id, "nickname": p.nickname, "name": p.name, "bank": p.bank, "account_last4": p.account_last4,
             "is_new": p.is_new, "flagged": p.flagged} for p in toolbox.payees(db, caller.customer)]


class LookupIn(BaseModel):
    bank_code: str = Field(min_length=1, max_length=10)
    account_number: str = Field(min_length=1, max_length=20)


@router.post("/payees/lookup")
def lookup(body: LookupIn, caller: Caller = Depends(reader)):
    enquiry = enquire_account(caller.lang, body.bank_code, body.account_number.strip(), limit_for=caller.customer.id)
    name = readback.nip_name(enquiry.account_name)
    return {"name": name, "bank": enquiry.bank.short, "account_last4": enquiry.last4,
            "say": lines.say(caller.lang, "lookup_found", name=name, bank=enquiry.bank.short,
                             digits=amounts.digit_words(enquiry.last4))}


# ---- cards --------------------------------------------------------------------------------------

@router.get("/cards")
def cards(caller: Caller = Depends(reader)):
    return [{"id": c.id, "last4": c.last4, "holder": c.holder, "status": c.status}
            for c in get_core().cards(caller.customer.core_ref)]


# ---- preferences --------------------------------------------------------------------------------

class PreferencesIn(BaseModel):
    lang: Literal["en", "pcm", "yo", "ha", "ig"] | None = None
    modes: list[Literal["voice", "sign", "switch", "plain"]] | None = Field(None, min_length=1)
    theme: Literal["system", "light", "dark", "contrast"] | None = None
    speech_rate: float | None = Field(None, ge=0.5, le=2.0)
    pulse_gap_ms: int | None = Field(None, ge=400, le=3000)
    daily_limit_kobo: int | None = Field(None, gt=0)
    pause_threshold_kobo: int | None = Field(None, gt=0)
    # Only when a change weakens a protection.
    pin_block: str | None = Field(None, max_length=200)


_CLEARABLE = {"daily_limit_kobo", "pause_threshold_kobo"}


def _preferences(c: Customer) -> dict:
    return {"lang": c.lang, "modes": list(c.modes), "theme": c.theme, "speech_rate": c.speech_rate,
            "pulse_gap_ms": c.pulse_gap_ms, "daily_limit_kobo": c.daily_limit_kobo,
            "pause_threshold_kobo": c.pause_threshold_kobo}


def weakens_protection(c: Customer, changes: dict) -> bool:
    """A scammer on the phone must not talk anyone into switching protections off."""
    if "pause_threshold_kobo" in changes:
        now = c.pause_threshold_kobo or settings.pause_threshold_kobo
        new = changes["pause_threshold_kobo"] or settings.pause_threshold_kobo
        if new > now:
            return True
    if "daily_limit_kobo" in changes and c.daily_limit_kobo is not None:
        new = changes["daily_limit_kobo"]
        if new is None or new > c.daily_limit_kobo:
            return True
    return False


@router.get("/me/preferences")
def get_preferences(caller: Caller = Depends(customer)):
    return _preferences(caller.customer)


@router.put("/me/preferences")
def put_preferences(body: PreferencesIn, caller: Caller = Depends(customer), db: Session = Depends(get_session)):
    c = caller.customer
    changes = {}
    for name in body.model_fields_set - {"pin_block"}:
        value = getattr(body, name)
        if value is None and name not in _CLEARABLE:
            continue
        changes[name] = value
    if weakens_protection(c, changes):
        pin.require(c, caller.token, body.pin_block, purpose="preferences")
    for name, value in changes.items():
        setattr(c, name, value)
    db.commit()
    if changes:
        audit_log.record("preferences_changed", actor="customer", customer_id=c.id, fields=sorted(changes))
    return _preferences(c)
