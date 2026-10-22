"""The confirm gate: plain code, no model. Check order is in docs/api.md."""

import time
from dataclasses import dataclass
from datetime import timedelta

from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from ominira import amounts, audit_log, drafting, lines, pin, readback
from ominira.core import base, get_core
from ominira.core.base import CoreTimeout, MoveResult
from ominira.database import is_postgres, utcnow
from ominira.locks import customer_lock, draft_lock
from ominira.problems import Problem
from ominira.security import Caller
from ominira.settings import settings
from ominira.tables import Approval, Approver, Customer, Draft, Payee


@dataclass
class Answer:
    status_code: int
    body: dict


# ---- small helpers ----------------------------------------------------------------------------

def _load(db: Session, draft_id: str, customer_id: str | None, lang: str) -> Draft:
    query = select(Draft).where(Draft.id == draft_id).execution_options(populate_existing=True)
    if is_postgres():
        query = query.with_for_update()
    draft = db.scalars(query).first()
    if draft is None or (customer_id is not None and draft.customer_id != customer_id):
        raise Problem(404, "not_found", "No such draft", lines.say(lang, "not_found"))
    return draft


def _key(draft: Draft) -> str:
    return f"{draft.id}:{draft.attempt}"


def _close(draft: Draft, status: str, say: str) -> None:
    """Every way a draft ends. Drops the full account number."""
    draft.status = status
    draft.last_say = say
    if "account_number" in draft.payload:
        draft.payload = {k: v for k, v in draft.payload.items() if k != "account_number"}


def _active_approver(db: Session, customer_id: str) -> Approver | None:
    return db.scalars(
        select(Approver).where(Approver.customer_id == customer_id, Approver.active.is_(True))
    ).first()


def _pending_approval(db: Session, draft_id: str) -> Approval | None:
    return db.scalars(
        select(Approval).where(Approval.draft_id == draft_id, Approval.status == "pending")
        .execution_options(populate_existing=True)
    ).first()


def _approver_name(db: Session, approver_id: str | None) -> str:
    ap = db.get(Approver, approver_id) if approver_id else None
    return ap.name if ap else "Your approver"


def _pause_line(db: Session, draft: Draft, lang: str) -> str:
    payee = drafting.payee_of(db, draft)
    return lines.say(lang, "pause_flagged" if payee is not None and payee.flagged else "pause")


def _withdraw(db: Session, draft: Draft) -> int:
    count = 0
    for approval in db.scalars(select(Approval).where(Approval.draft_id == draft.id, Approval.status == "pending")):
        approval.status, approval.decided_at = "withdrawn", utcnow()
        count += 1
    return count


def _expire_if_due(db: Session, draft: Draft, customer: Customer) -> bool:
    """A processing draft never expires: its outcome is Zenith's to give."""
    now = utcnow()
    if draft.status in ("awaiting_confirm", "paused") and now > draft.expires_at:
        _close(draft, "expired", lines.say(customer.lang, "draft_expired"))
        draft.expired_by = "draft"
        db.commit()
        audit_log.record("draft_expired", actor="system", customer_id=customer.id, draft_id=draft.id)
        return True
    if draft.status == "awaiting_approval":
        approval = _pending_approval(db, draft.id)
        if approval is not None and now - approval.asked_at > timedelta(seconds=settings.approval_lifetime_s):
            approval.status, approval.decided_at = "expired", now
            name = _approver_name(db, approval.approver_id)
            _close(draft, "expired", lines.say(customer.lang, "approval_expired", approver=name))
            draft.expired_by = "approval"
            db.commit()
            audit_log.record("approval_expired", actor="system", customer_id=customer.id, draft_id=draft.id,
                             approval_id=approval.id)
            return True
    return False


def _closed_problem(draft: Draft, lang: str) -> Problem:
    if draft.status == "executed":
        return Problem(409, "already_executed", "This draft was already carried out",
                       lines.say(lang, "already_sent"), reference=draft.reference)
    if draft.status == "processing":
        return Problem(409, "processing", "This transfer is already on its way", lines.say(lang, "processing"))
    if draft.status == "expired":
        if draft.expired_by == "approval":
            return Problem(410, "approval_expired", "The co-approver did not answer in time", draft.last_say,
                           draft_id=draft.id)
        return Problem(410, "draft_expired", "This draft has expired", lines.say(lang, "draft_expired"),
                       draft_id=draft.id)
    return Problem(409, "draft_closed", f"This draft is {draft.status}",
                   draft.last_say or lines.say(lang, "cancelled"), status_now=draft.status)


# ---- step 2: heard it ---------------------------------------------------------------------------

def _check_heard(db: Session, customer: Customer, draft: Draft, client_hash: str) -> None:
    lang = customer.lang
    try:
        rb, enquiry_ref = drafting.rebuild_readback(db, customer, draft)
    except Problem as p:
        if p.code == "already_blocked":
            _close(draft, "cancelled", p.say)
            db.commit()
        raise
    if enquiry_ref:
        draft.payload = draft.payload | {"enquiry_ref": enquiry_ref}
    fresh = readback.readback_hash(rb)
    changed = fresh != draft.readback_hash
    draft.readback, draft.readback_hash = rb, fresh  # also picks up a new warning line
    if changed:
        draft.last_say = lines.say(lang, "readback_mismatch")
        db.commit()
        audit_log.record("readback_changed", actor="system", customer_id=customer.id, draft_id=draft.id)
        raise Problem(409, "readback_mismatch", "What will happen has changed", draft.last_say,
                      readback=rb, readback_hash=fresh)
    db.commit()
    if client_hash != draft.readback_hash:
        raise Problem(409, "readback_mismatch", "The client heard a different readback",
                      lines.say(lang, "readback_mismatch"), readback=draft.readback, readback_hash=draft.readback_hash)


# ---- step 3: limits -----------------------------------------------------------------------------

def _limit_problem(customer: Customer, amount: int) -> Problem | None:
    lang = customer.lang
    lim = get_core().limits(customer.core_ref)
    if amount > lim.per_transaction_kobo:
        return Problem(422, "limit_exceeded", "Over the per-transaction limit",
                       lines.say(lang, "limit_single", max=amounts.spoken(lim.per_transaction_kobo, lang)),
                       limit="per_transaction", max_kobo=lim.per_transaction_kobo)
    daily = lim.daily_kobo
    if customer.daily_limit_kobo:
        daily = min(daily, customer.daily_limit_kobo)
    remaining = max(0, daily - lim.used_today_kobo)
    if amount > remaining:
        return Problem(422, "limit_exceeded", "Over the daily limit",
                       lines.say(lang, "limit_daily", remaining=amounts.spoken(remaining, lang)),
                       limit="daily", remaining_today_kobo=remaining)
    return None


# ---- the gate -----------------------------------------------------------------------------------

def confirm(db: Session, caller: Caller, draft_id: str, client_hash: str, pin_block: str,
            call_active: bool) -> Answer:
    with draft_lock(draft_id):
        return _confirm(db, caller, draft_id, client_hash, pin_block, call_active)


def _confirm(db: Session, caller: Caller, draft_id: str, client_hash: str, pin_block: str,
             call_active: bool) -> Answer:
    customer = db.get(Customer, caller.customer.id, populate_existing=True)
    lang = customer.lang

    # 1. Real draft.
    draft = _load(db, draft_id, customer.id, lang)
    _expire_if_due(db, draft, customer)
    if draft.status == "awaiting_approval":
        approval = _pending_approval(db, draft.id)
        name = _approver_name(db, approval.approver_id if approval else None)
        return Answer(202, {"status": "awaiting_approval", "code": "approval_required", "approver": name,
                            "say": lines.say(lang, "approval_still_waiting", approver=name)})
    if draft.status == "paused":
        raise Problem(409, "pause_required", "Answer the scam pause first", _pause_line(db, draft, lang),
                      draft_id=draft.id)
    if draft.status != "awaiting_confirm":
        raise _closed_problem(draft, lang)

    # 2. Heard it.
    _check_heard(db, customer, draft, client_hash)

    if draft.kind == "card_block":
        pin.require(customer, caller.token, pin_block, purpose="confirm", draft_id=draft.id)  # 5
        return _execute(db, draft, customer, actor="customer")  # 7 and 8

    with customer_lock(customer.id):
        amount = draft.payload["amount_kobo"]
        # 3. Limits.
        problem = _limit_problem(customer, amount)
        if problem is not None:
            raise problem

        # 4. Scam pause.
        payee = drafting.payee_of(db, draft)
        is_new = payee is None or payee.is_new
        flagged = payee is not None and payee.flagged
        threshold = customer.pause_threshold_kobo or settings.pause_threshold_kobo
        if draft.pause_answer is None and ((is_new and amount >= threshold and call_active) or flagged):
            draft.status = "paused"
            draft.paused_on_call = bool(call_active)
            draft.last_say = _pause_line(db, draft, lang)
            db.commit()
            audit_log.record("scam_pause", actor="system", customer_id=customer.id, draft_id=draft.id,
                             amount_kobo=amount, new_payee=is_new, flagged=flagged, call_active=bool(call_active))
            raise Problem(409, "pause_required", "Scam pause", draft.last_say, draft_id=draft.id)

        # 5. Pulse PIN, before the approver is asked about anything.
        pin.require(customer, caller.token, pin_block, purpose="confirm", draft_id=draft.id)

        # 6. Co-approver.
        approver = _active_approver(db, customer.id)
        if approver is not None and amount > approver.threshold_kobo:
            approval = Approval(id=drafting.new_id("apv_"), draft_id=draft.id, approver_id=approver.id)
            db.add(approval)
            draft.status = "awaiting_approval"
            draft.last_say = lines.say(lang, "approval_wait", approver=approver.name,
                                       threshold=amounts.spoken(approver.threshold_kobo, lang))
            db.commit()
            audit_log.record("approval_requested", actor="customer", customer_id=customer.id, draft_id=draft.id,
                             approval_id=approval.id, approver_id=approver.id, amount_kobo=amount)
            return Answer(202, {"status": "awaiting_approval", "code": "approval_required",
                                "approver": approver.name, "say": draft.last_say})

        # 7 and 8.
        return _execute(db, draft, customer, actor="customer")


# ---- steps 7 and 8 ------------------------------------------------------------------------------

def _requery(key: str) -> MoveResult:
    """Poll by key until not in progress, or REQUERY_WAIT_S runs out."""
    core = get_core()
    deadline = time.monotonic() + settings.requery_wait_s
    result = core.transfer_status(key)
    while result.in_progress and time.monotonic() < deadline:
        time.sleep(min(1.0, max(0.0, deadline - time.monotonic())))
        result = core.transfer_status(key)
    return result


def _send(customer: Customer, draft: Draft) -> MoveResult | None:
    """None means no answer (a timeout): the outcome is unknown."""
    core = get_core()
    key = _key(draft)
    try:
        if draft.kind == "card_block":
            return core.block_card(customer.core_ref, draft.payload["card_id"], key)
        return core.transfer(customer.core_ref, draft.payload["enquiry_ref"], draft.payload["amount_kobo"], key,
                             narration=f"Ominira {draft.id}")
    except CoreTimeout:
        audit_log.record("core_timeout", actor="system", customer_id=customer.id, draft_id=draft.id, key=key)
        return None


def _execute(db: Session, draft: Draft, customer: Customer, actor: str) -> Answer:
    key = _key(draft)
    # Mark processing before calling Zenith, so no second request can send it.
    draft.status = "processing"
    db.commit()
    audit_log.record("sending", actor=actor, customer_id=customer.id, draft_id=draft.id, key=key,
                     kind=draft.kind, amount_kobo=draft.payload.get("amount_kobo"))

    result = _send(customer, draft)
    if result is None:
        # After a timeout "no record" means "not yet", never "not sent": keep the key.
        result = _requery(key) if draft.kind == "transfer" else None
        if result is None or result.code == base.NO_RECORD:
            return _still_processing(db, draft, customer, None, actor)
    elif result.in_progress:
        result = _requery(key)
    return _settle(db, draft, customer, result, actor)


def _still_processing(db: Session, draft: Draft, customer: Customer, reference: str | None, actor: str) -> Answer:
    draft.status = "processing"
    if reference:
        draft.reference = reference
    draft.last_say = lines.say(customer.lang, "processing")
    db.commit()
    audit_log.record("in_progress", actor=actor, customer_id=customer.id, draft_id=draft.id, reference=reference)
    return Answer(202, {"status": "processing", "code": "processing", "reference": draft.reference,
                        "say": draft.last_say})


def _settle(db: Session, draft: Draft, customer: Customer, result: MoveResult, actor: str) -> Answer:
    lang = customer.lang
    if result.done:
        return _finish(db, draft, customer, result, actor)
    if result.in_progress:
        return _still_processing(db, draft, customer, result.reference, actor)

    key = _key(draft)
    bank = draft.readback.get("bank") or "The other bank"
    if result.code in base.NOT_SENT_RETRYABLE:
        # 91/96: definitely not sent, so only here does the key move on.
        draft.attempt += 1
        draft.status = "awaiting_confirm"
        draft.last_say = lines.say(lang, "bank_unavailable", bank=bank)
        problem = Problem(503, "bank_unavailable", "The bank did not answer", draft.last_say, retry_after=30,
                          zenith_code=result.code)
    elif result.code == base.INSUFFICIENT_FUNDS:
        _close(draft, "failed", lines.say(lang, "insufficient_funds"))
        problem = Problem(422, "insufficient_funds", "Not enough money", draft.last_say)
    elif result.code == base.LIMIT_EXCEEDED:
        _close(draft, "failed", lines.say(lang, "failed"))
        db.commit()
        detail = _limit_problem(customer, draft.payload.get("amount_kobo") or 0)
        extra = dict(detail.extra) if detail else {"limit": "daily", "remaining_today_kobo": 0}
        problem = Problem(422, "limit_exceeded", "Zenith refused it as over a limit", draft.last_say,
                          zenith_code=result.code, **extra)
    else:
        _close(draft, "failed", lines.say(lang, "failed"))
        problem = Problem(422, "failed", "Zenith refused it", draft.last_say, zenith_code=result.code)
    db.commit()
    audit_log.record("not_sent", actor="system", customer_id=customer.id, draft_id=draft.id, key=key,
                     zenith_code=result.code, status=draft.status)
    raise problem


def _save_new_payee(db: Session, draft: Draft, customer: Customer) -> Payee:
    ben_ref = get_core().save_beneficiary(customer.core_ref, draft.payload["enquiry_ref"])
    payee = db.scalars(select(Payee).where(Payee.customer_id == customer.id,
                                           Payee.beneficiary_ref == ben_ref)).first()
    if payee is None:
        name = draft.readback["name"]
        payee = Payee(id=drafting.new_id("py_"), customer_id=customer.id, nickname=name.split()[-1], name=name,
                      bank_code=draft.payload["bank_code"], bank=draft.readback["bank"],
                      account_last4=draft.readback["account_last4"], beneficiary_ref=ben_ref)
        db.add(payee)
        db.flush()
    draft.payload = draft.payload | {"payee_id": payee.id}
    return payee


def _finish(db: Session, draft: Draft, customer: Customer, result: MoveResult, actor: str) -> Answer:
    lang = customer.lang
    saved = None
    if draft.kind == "transfer":
        payee = drafting.payee_of(db, draft)
        if payee is None:
            payee = saved = _save_new_payee(db, draft, customer)
        if payee.first_paid_at is None:
            payee.first_paid_at = utcnow()
        approved = db.scalars(select(Approval).where(Approval.draft_id == draft.id,
                                                     Approval.status == "approved")).first()
        if approved is not None:
            line = lines.say(lang, "approved_sent", approver=_approver_name(db, approved.approver_id),
                             nick=payee.nickname)
        else:
            line = lines.say(lang, "sent", nick=payee.nickname)
    else:
        line = lines.say(lang, "card_blocked")
    draft.reference = result.reference
    _close(draft, "executed", line)
    db.commit()
    audit_log.record("executed", actor=actor, customer_id=customer.id, draft_id=draft.id, kind=draft.kind,
                     amount_kobo=draft.payload.get("amount_kobo"), account_last4=draft.readback.get("account_last4"),
                     reference=result.reference, key=_key(draft))
    if saved is not None:
        audit_log.record("payee_saved", actor="system", customer_id=customer.id, draft_id=draft.id,
                         payee_id=saved.id, account_last4=saved.account_last4)
    return Answer(200, {"status": "executed", "reference": result.reference, "say": line})


# ---- re-checks and sweeps -----------------------------------------------------------------------

def refresh(db: Session, caller: Caller, draft_id: str) -> Draft:
    """GET /v1/drafts/{id}: expire if stale, re-check if processing."""
    with draft_lock(draft_id):
        customer = db.get(Customer, caller.customer.id, populate_existing=True)
        draft = _load(db, draft_id, customer.id, customer.lang)
        _expire_if_due(db, draft, customer)
        if draft.status == "processing":
            _recheck(db, draft, customer)
        return draft


def _recheck(db: Session, draft: Draft, customer: Customer) -> None:
    """No record at Zenith: resend under the same key, so a late arrival and it are one."""
    if draft.kind == "transfer":
        result = get_core().transfer_status(_key(draft))
        if result.code == base.NO_RECORD:
            result = _send(customer, draft)
    else:
        result = _send(customer, draft)  # block_card is idempotent on the key
    if result is None or (result.in_progress and draft.status == "processing" and draft.reference == result.reference):
        return
    with customer_lock(customer.id):
        try:
            _settle(db, draft, customer, result, actor="system")
        except Problem:
            pass  # the draft already carries the outcome and its line


def sweep_expired(db: Session) -> int:
    now = utcnow()
    candidates = db.scalars(select(Draft.id).where(or_(
        Draft.status == "awaiting_approval",
        (Draft.status.in_(("awaiting_confirm", "paused"))) & (Draft.expires_at < now),
    ))).all()
    closed = 0
    for draft_id in candidates:
        with draft_lock(draft_id):
            draft = _load(db, draft_id, None, "en")
            if _expire_if_due(db, draft, db.get(Customer, draft.customer_id)):
                closed += 1
    return closed


def sweep_approvals(db: Session, approver_id: str) -> None:
    limit = utcnow() - timedelta(seconds=settings.approval_lifetime_s)
    stale = db.scalars(select(Approval).where(Approval.approver_id == approver_id, Approval.status == "pending",
                                              Approval.asked_at < limit)).all()
    for approval in stale:
        with draft_lock(approval.draft_id):
            draft = _load(db, approval.draft_id, None, "en")
            _expire_if_due(db, draft, db.get(Customer, draft.customer_id))


# ---- pause, cancel, decide ----------------------------------------------------------------------

def answer_pause(db: Session, caller: Caller, draft_id: str, answer: str) -> Answer:
    with draft_lock(draft_id):
        customer = db.get(Customer, caller.customer.id, populate_existing=True)
        lang = customer.lang
        draft = _load(db, draft_id, customer.id, lang)
        _expire_if_due(db, draft, customer)
        if draft.status == "expired":
            raise _closed_problem(draft, lang)
        if draft.status != "paused":
            raise Problem(409, "not_paused", "This draft is not paused", status_now=draft.status)
        if answer == "chose_myself":
            draft.status = "awaiting_confirm"
            draft.pause_answer = "chose_myself"
            draft.last_say = lines.say(lang, "pause_continue_call" if draft.paused_on_call else "pause_continue")
            db.commit()
            audit_log.record("pause_chose_myself", actor="customer", customer_id=customer.id, draft_id=draft.id)
            return Answer(200, {"status": "awaiting_confirm", "say": draft.last_say})

        payee = drafting.payee_of(db, draft)
        if payee is not None:
            payee.flagged = True
        _close(draft, "cancelled", lines.say(lang, "pause_stopped"))
        db.commit()
        audit_log.record("pause_stop", actor="customer", customer_id=customer.id, draft_id=draft.id,
                         payee_flagged=payee is not None)
        answer_body = {"status": "cancelled", "say": draft.last_say}
    # Outside this draft's lock: never hold two draft locks at once.
    if payee is not None:
        _cancel_open_drafts_to(db, customer, payee, except_id=draft_id)
    return Answer(200, answer_body)


def _cancel_open_drafts_to(db: Session, customer: Customer, payee: Payee, except_id: str) -> None:
    open_ids = db.scalars(select(Draft.id).where(Draft.customer_id == customer.id, Draft.kind == "transfer",
                                                 Draft.status.in_(drafting.OPEN_STATUSES),
                                                 Draft.id != except_id)).all()
    for other_id in open_ids:
        with draft_lock(other_id):
            other = _load(db, other_id, customer.id, customer.lang)
            if other.status not in drafting.OPEN_STATUSES:
                continue
            target = drafting.attach_known_payee(db, customer, other)
            if target is None or target.id != payee.id:
                continue
            withdrawn = _withdraw(db, other)
            _close(other, "cancelled", lines.say(customer.lang, "cancelled"))
            db.commit()
            audit_log.record("cancelled", actor="system", customer_id=customer.id, draft_id=other.id,
                             reason="payee_stopped", approvals_withdrawn=withdrawn)


def cancel(db: Session, caller: Caller, draft_id: str) -> Answer:
    with draft_lock(draft_id):
        customer = db.get(Customer, caller.customer.id, populate_existing=True)
        lang = customer.lang
        draft = _load(db, draft_id, customer.id, lang)
        _expire_if_due(db, draft, customer)
        if draft.status not in drafting.OPEN_STATUSES:
            raise _closed_problem(draft, lang)  # processing answers 409 processing: Zenith decides
        withdrawn = _withdraw(db, draft)
        _close(draft, "cancelled", lines.say(lang, "cancelled"))
        db.commit()
        audit_log.record("cancelled", actor="customer", customer_id=customer.id, draft_id=draft.id,
                         approvals_withdrawn=withdrawn)
        return Answer(200, {"status": "cancelled", "say": draft.last_say})


def withdraw_for_approver(db: Session, approver_id: str) -> int:
    pending = db.scalars(select(Approval).where(Approval.approver_id == approver_id,
                                                Approval.status == "pending")).all()
    count = 0
    for approval in pending:
        with draft_lock(approval.draft_id):
            draft = _load(db, approval.draft_id, None, "en")
            db.refresh(approval)
            if approval.status != "pending":
                continue
            customer = db.get(Customer, draft.customer_id)
            withdrawn = _withdraw(db, draft)
            if draft.status == "awaiting_approval":
                _close(draft, "cancelled", lines.say(customer.lang, "cancelled"))
            db.commit()
            audit_log.record("cancelled", actor="customer", customer_id=customer.id, draft_id=draft.id,
                             reason="approver_removed", approvals_withdrawn=withdrawn)
            count += 1
    return count


def decide(db: Session, caller: Caller, approval_id: str, decision: str) -> Answer:
    approval = db.get(Approval, approval_id)
    if approval is None or approval.approver_id != caller.approver_id:
        raise Problem(404, "not_found", "No such approval")
    with draft_lock(approval.draft_id):
        db.refresh(approval)
        draft = _load(db, approval.draft_id, None, "en")
        customer = db.get(Customer, draft.customer_id, populate_existing=True)
        lang = customer.lang
        name = _approver_name(db, approval.approver_id)
        _expire_if_due(db, draft, customer)
        db.refresh(approval)
        if approval.status == "expired":
            raise Problem(410, "approval_expired", "This request waited too long and expired",
                          lines.say(lang, "approval_expired", approver=name), draft_id=draft.id)
        if approval.status != "pending" or draft.status != "awaiting_approval":
            raise Problem(409, "already_decided", f"This request is already {approval.status}",
                          status_now=draft.status)

        approval.decided_at = utcnow()
        if decision == "decline":
            approval.status = "declined"
            _close(draft, "cancelled", lines.say(lang, "approval_declined", approver=name))
            db.commit()
            audit_log.record("approval_declined", actor="approver", customer_id=customer.id, draft_id=draft.id,
                             approval_id=approval.id)
            return Answer(200, {"approval": "declined", "status": "cancelled", "say": draft.last_say})

        approval.status = "approved"
        db.commit()
        audit_log.record("approval_given", actor="approver", customer_id=customer.id, draft_id=draft.id,
                         approval_id=approval.id)

        with customer_lock(customer.id):
            # Time passed while the approver thought: re-check payee, account and limits.
            try:
                _check_heard(db, customer, draft, draft.readback_hash)
                payee = drafting.payee_of(db, draft)
                if payee is not None and payee.flagged and draft.pause_answer is None:
                    raise Problem(409, "pause_required", "The customer flagged this person while waiting",
                                  lines.say(lang, "pause_flagged"), draft_id=draft.id)
                problem = _limit_problem(customer, draft.payload.get("amount_kobo") or 0)
                if problem is not None:
                    raise problem
            except Problem as p:
                if p.code == "pause_required":
                    draft.status = "paused"
                    draft.paused_on_call = False
                    draft.last_say = p.say
                elif p.code in ("readback_mismatch", "bank_unavailable"):
                    draft.status = "awaiting_confirm"
                    draft.last_say = p.say
                else:
                    _close(draft, "failed", p.say or lines.say(lang, "failed"))
                db.commit()
                audit_log.record("approved_but_held", actor="system", customer_id=customer.id, draft_id=draft.id,
                                 reason=p.code)
                return Answer(200, {"approval": "approved", "status": draft.status, "say": p.say, "code": p.code})

            try:
                answer = _execute(db, draft, customer, actor="approver")
            except Problem as p:
                return Answer(200, {"approval": "approved", "status": draft.status, "say": p.say, "code": p.code})
            return Answer(200, {"approval": "approved", **answer.body})
