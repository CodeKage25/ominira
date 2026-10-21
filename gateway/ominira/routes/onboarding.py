"""Opening an account by voice. No auth: the person has no account yet."""

import secrets
from typing import Literal

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from ominira import audit_log, lines
from ominira.core import get_core
from ominira.database import get_session
from ominira.demo_world import CUSTOMER_ID, CUSTOMER_TOKEN
from ominira.drafting import new_id
from ominira.problems import Problem
from ominira.tables import Customer, Onboarding

router = APIRouter(prefix="/v1/onboarding")


class StartIn(BaseModel):
    lang: Literal["en", "pcm", "yo", "ha", "ig"] | None = None


class SelfieIn(BaseModel):
    spoken_digits: list[int] | None = None


class BvnIn(BaseModel):
    bvn: str = Field(pattern=r"^\d{11}$")


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
def id_document(sid: str, db: Session = Depends(get_session)):
    _session(db, sid)
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


@router.post("/{sid}/complete")
def complete(sid: str, db: Session = Depends(get_session)):
    row = _session(db, sid)
    if not row.bvn_matched or row.live is False:
        raise Problem(409, "onboarding_incomplete", "Finish the selfie and the BVN check first")
    if not row.completed:
        row.completed = True
        customer = db.get(Customer, CUSTOMER_ID)
        if customer is not None and row.lang:
            customer.lang = row.lang
        db.commit()
        audit_log.record("account_opened", actor="customer", customer_id=CUSTOMER_ID, onboarding_id=sid)
    # Hackathon: every new account is the demo customer.
    return {"customer_id": CUSTOMER_ID, "token": CUSTOMER_TOKEN}
