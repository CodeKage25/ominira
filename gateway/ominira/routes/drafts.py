"""Drafts, the confirm gate, co-approvers and approvals."""

from typing import Literal

from fastapi import APIRouter, Depends, Header
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from ominira import audit_log, drafting, gate, pin
from ominira.core import get_core
from ominira.database import get_session, iso
from ominira.problems import Problem
from ominira.security import Caller, approver, customer, reader
from ominira.tables import Approval, Approver, Draft, Payee

router = APIRouter(prefix="/v1")


def _respond(answer: gate.Answer) -> JSONResponse:
    return JSONResponse(answer.body, status_code=answer.status_code)


# ---- drafts -------------------------------------------------------------------------------------

class DraftIn(BaseModel):
    kind: Literal["transfer", "card_block"]
    amount_kobo: int | None = None
    payee_id: str | None = None
    bank_code: str | None = None
    account_number: str | None = None
    card_id: str | None = None
    source: Literal["app", "assistant", "voice", "ussd"] = "app"


@router.post("/drafts", status_code=201)
def create_draft(body: DraftIn, caller: Caller = Depends(reader), db: Session = Depends(get_session)):
    # The assistant cannot claim another source.
    source = "assistant" if caller.role == "assistant" else body.source
    actor = caller.role
    if body.kind == "card_block":
        if not body.card_id:
            raise Problem(422, "invalid_request", "card_id is required for a card_block")
        draft = drafting.new_card_block(db, caller.customer, card_id=body.card_id, source=source, actor=actor)
    else:
        if body.amount_kobo is None:
            raise Problem(422, "invalid_amount", "amount_kobo is required for a transfer")
        draft = drafting.new_transfer(db, caller.customer, amount_kobo=body.amount_kobo, payee_id=body.payee_id,
                                      bank_code=body.bank_code, account_number=body.account_number,
                                      source=source, actor=actor)
    return drafting.view(draft)


@router.get("/drafts/{draft_id}")
def get_draft(draft_id: str, caller: Caller = Depends(customer), db: Session = Depends(get_session)):
    return drafting.view(gate.refresh(db, caller, draft_id))


class ConfirmIn(BaseModel):
    readback_hash: str = Field(min_length=1, max_length=100)
    pin_block: str = Field(min_length=1, max_length=200)
    call_active: bool = False


@router.post("/drafts/{draft_id}/confirm")
def confirm(draft_id: str, body: ConfirmIn, caller: Caller = Depends(customer), db: Session = Depends(get_session)):
    return _respond(gate.confirm(db, caller, draft_id, body.readback_hash, body.pin_block, body.call_active))


class PauseAnswerIn(BaseModel):
    answer: Literal["stop", "chose_myself"]


@router.post("/drafts/{draft_id}/pause-answer")
def pause_answer(draft_id: str, body: PauseAnswerIn, caller: Caller = Depends(customer),
                 db: Session = Depends(get_session)):
    return _respond(gate.answer_pause(db, caller, draft_id, body.answer))


@router.post("/drafts/{draft_id}/cancel")
def cancel(draft_id: str, caller: Caller = Depends(customer), db: Session = Depends(get_session)):
    return _respond(gate.cancel(db, caller, draft_id))


# ---- co-approvers -------------------------------------------------------------------------------

class ApproverIn(BaseModel):
    name: str = Field(min_length=1, max_length=60)
    phone: str = Field(min_length=7, max_length=20)
    threshold_kobo: int = Field(gt=0)
    pin_block: str | None = Field(None, max_length=200)


def _approver_view(a: Approver) -> dict:
    return {"id": a.id, "name": a.name, "threshold_kobo": a.threshold_kobo}


@router.get("/approvers")
def list_approvers(caller: Caller = Depends(customer), db: Session = Depends(get_session)):
    rows = db.scalars(select(Approver).where(Approver.customer_id == caller.customer.id, Approver.active.is_(True)))
    return [_approver_view(a) for a in rows]


@router.post("/approvers", status_code=201)
def add_approver(body: ApproverIn, caller: Caller = Depends(customer), db: Session = Depends(get_session)):
    pin.require(caller.customer, caller.token, body.pin_block, purpose="approver_set")
    replaced = list(db.scalars(select(Approver).where(Approver.customer_id == caller.customer.id,
                                                      Approver.active.is_(True))))
    for old in replaced:
        old.active = False  # one approver at a time in this build
    a = Approver(id=drafting.new_id("ap_", 4), customer_id=caller.customer.id, name=body.name, phone=body.phone,
                 threshold_kobo=body.threshold_kobo)
    db.add(a)
    db.commit()
    for old in replaced:
        gate.withdraw_for_approver(db, old.id)
    audit_log.record("approver_set", actor="customer", customer_id=caller.customer.id, approver_id=a.id,
                     threshold_kobo=a.threshold_kobo)
    return _approver_view(a)


@router.delete("/approvers/{approver_id}")
def remove_approver(approver_id: str, x_pin_block: str | None = Header(default=None),
                    caller: Caller = Depends(customer), db: Session = Depends(get_session)):
    a = db.get(Approver, approver_id)
    if a is None or a.customer_id != caller.customer.id or not a.active:
        raise Problem(404, "not_found", "No such approver")
    pin.require(caller.customer, caller.token, x_pin_block, purpose="approver_removed")
    a.active = False
    db.commit()
    withdrawn = gate.withdraw_for_approver(db, a.id)
    audit_log.record("approver_removed", actor="customer", customer_id=caller.customer.id, approver_id=a.id,
                     approvals_withdrawn=withdrawn)
    return {"removed": a.id}


# ---- approvals (the approver's side) ------------------------------------------------------------

@router.get("/approvals")
def approvals(caller: Caller = Depends(approver), db: Session = Depends(get_session)):
    gate.sweep_approvals(db, caller.approver_id)
    first_name = get_core().profile(caller.customer.core_ref).first_name
    rows = db.scalars(select(Approval).where(Approval.approver_id == caller.approver_id)
                      .order_by(Approval.asked_at.desc()).limit(20)
                      .execution_options(populate_existing=True))
    out = []
    for a in rows:
        d = db.get(Draft, a.draft_id, populate_existing=True)
        rb = d.readback
        payee = db.get(Payee, d.payload["payee_id"], populate_existing=True) if d.payload.get("payee_id") else None
        out.append({"id": a.id, "status": a.status, "customer": first_name, "amount_kobo": rb.get("amount_kobo"),
                    "amount_text": rb.get("amount_text"), "to": rb.get("name"), "bank": rb.get("bank"),
                    "account_last4": rb.get("account_last4"), "asked_at": iso(a.asked_at), "draft_status": d.status,
                    "flagged": bool(payee and payee.flagged)})
    return out


class DecisionIn(BaseModel):
    decision: Literal["approve", "decline"]


@router.post("/approvals/{approval_id}/decision")
def decide(approval_id: str, body: DecisionIn, caller: Caller = Depends(approver), db: Session = Depends(get_session)):
    return _respond(gate.decide(db, caller, approval_id, body.decision))
