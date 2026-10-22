"""Opening an account by voice. No auth: the person has no account yet."""

import secrets
from typing import Literal

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from ominira import audit_log, lines
from ominira.core import get_core, mock_core
from ominira.database import get_session
from ominira.demo_world import give_starter_payees
from ominira.drafting import new_id
from ominira.problems import Problem
from ominira.tables import Customer, Onboarding, Token

router = APIRouter(prefix="/v1/onboarding")


class StartIn(BaseModel):
    lang: Literal["en", "pcm", "yo", "ha", "ig"] | None = None


class SelfieIn(BaseModel):
    spoken_digits: list[int] | None = None


class BvnIn(BaseModel):
    bvn: str = Field(pattern=r"^\d{11}$")


class PinIn(BaseModel):
    pin_block: str


class IdIn(BaseModel):
    """The customer's name, said or typed (a real bank reads it from the BVN record), with the card's details
    when a card was read. Only the name is kept; the rest is echoed back."""
    full_name: str = Field(min_length=2, max_length=120)
    nin_last4: str = Field("", pattern=r"^(\d{4})?$")
    born_on: str = Field("", pattern=r"^(\d{4}-\d{2}-\d{2})?$")


def _customer_of(db: Session, row: Onboarding) -> Customer:
    customer = db.get(Customer, row.customer_id) if row.customer_id else None
    if customer is None:
        raise Problem(409, "onboarding_incomplete", "Open the account first")
    return customer


def _session(db: Session, sid: str) -> Onboarding:
    row = db.get(Onboarding, sid)
    if row is None:
        raise Problem(404, "not_found", "No such onboarding session")
    return row


@router.post("/sessions", status_code=201)
def start(body: StartIn | None = None, db: Session = Depends(get_session)):
    lang = body.lang if body else None
    row = Onboarding(id=new_id("ob_"), liveness_digits=[secrets.randbelow(10) for _ in range(4)], lang=lang)
    db.add(row)
    db.commit()
    return {"id": row.id, "liveness_digits": row.liveness_digits}


@router.post("/{sid}/selfie")
def selfie(sid: str, body: SelfieIn | None = None, db: Session = Depends(get_session)):
    row = _session(db, sid)
    said = body.spoken_digits if body else None
    row.live = said is None or list(said) == list(row.liveness_digits)
    db.commit()
    return {"live": row.live}


@router.post("/{sid}/id-document")
def id_document(sid: str, body: IdIn | None = None, db: Session = Depends(get_session)):
    row = _session(db, sid)
    if body is not None:
        row.full_name = body.full_name.strip()
        db.commit()
        return {"full_name": row.full_name, "nin_last4": body.nin_last4, "born_on": body.born_on}
    doc = get_core().read_id_document(sid)
    return {"full_name": doc.full_name, "nin_last4": doc.nin_last4, "born_on": doc.born_on}


@router.post("/{sid}/bvn")
def bvn(sid: str, body: BvnIn, db: Session = Depends(get_session)):
    row = _session(db, sid)
    core = get_core()
    result = core.check_bvn(body.bvn, core.read_id_document(sid))
    row.bvn_matched = result.match
    db.commit()
    return {"match": result.match, "say": lines.say(row.lang or "en", "bvn_ok" if result.match else "bvn_mismatch")}


@router.post("/{sid}/bvn-check")
def bvn_check(sid: str, db: Session = Depends(get_session)):
    """With the customer's consent: Zenith asks NIBSS for the BVN behind their NIN and phone."""
    row = _session(db, sid)
    core = get_core()
    result = core.look_up_bvn(core.read_id_document(sid), None)
    row.bvn_matched = result.match
    db.commit()
    return {"match": result.match, "say": lines.say(row.lang or "en", "bvn_found" if result.match else "bvn_mismatch")}


@router.post("/{sid}/pin")
def pin(sid: str, body: PinIn, db: Session = Depends(get_session)):
    """The new account's own Pulse PIN. The sealed block goes straight to the core, which refuses easy ones."""
    row = _session(db, sid)
    if not row.completed:
        raise Problem(409, "onboarding_incomplete", "Open the account before setting its PIN")
    if row.pin_set:
        raise Problem(409, "onboarding_closed", "This sign-up is finished; change the PIN with Zenith")
    say_lang = row.lang or "en"
    customer = _customer_of(db, row)
    if not get_core().set_pin(customer.core_ref, body.pin_block):
        raise Problem(422, "weak_pin", "Choose a PIN that is harder to guess", lines.say(say_lang, "pin_weak"))
    row.pin_set = True
    db.commit()
    audit_log.record("pin_set", actor="customer", customer_id=customer.id, onboarding_id=sid)
    return {"ok": True, "say": lines.say(say_lang, "pin_set")}


@router.post("/{sid}/complete")
def complete(sid: str, db: Session = Depends(get_session)):
    row = _session(db, sid)
    if row.completed:
        # The token is handed out once: a session id must not fetch it again.
        raise Problem(409, "onboarding_closed", "This account is already open")
    if not row.bvn_matched or row.live is False:
        raise Problem(409, "onboarding_incomplete", "Finish the selfie and the BVN check first")
    # Each sign-up is its own customer, named from the card the phone read.
    name = row.full_name or get_core().read_id_document(sid).full_name
    core_ref = get_core().open_customer(name)
    customer = Customer(id=new_id("cus_"), core_ref=core_ref, phone="", lang=row.lang or "en")
    db.add(customer)
    db.flush()
    token = Token(token=secrets.token_urlsafe(24), role="customer", customer_id=customer.id)
    db.add(token)
    if mock_core() is not None:
        give_starter_payees(db, customer.id, core_ref)
    row.customer_id = customer.id
    row.completed = True
    db.commit()
    audit_log.record("account_opened", actor="customer", customer_id=customer.id, onboarding_id=sid)
    return {"customer_id": customer.id, "token": token.token}
