"""The confirm gate: each check, their order, and every money path."""

import threading
from datetime import timedelta

from sqlalchemy import select, update

from ominira.core.mock_tables import MockDirectoryEntry
from ominira.database import SessionFactory, utcnow
from ominira.tables import Approval, Customer, Draft, Payee, Token
from tests.conftest import APPROVER, CUSTOMER, PIN_BAD, PIN_OK, balance, confirm, make_draft, moves, pin_tries_left

START = 84_230_000
K20 = 2_000_000  # ₦20,000
K150 = 15_000_000  # ₦150,000


def _draft_row(draft_id: str) -> Draft:
    with SessionFactory() as db:
        return db.get(Draft, draft_id)


def _set_draft(draft_id: str, **values) -> None:
    with SessionFactory() as db:
        db.execute(update(Draft).where(Draft.id == draft_id).values(**values))
        db.commit()


def _rename(bank_code: str, number: str, new_name: str) -> None:
    with SessionFactory() as db:
        db.execute(update(MockDirectoryEntry).where(MockDirectoryEntry.bank_code == bank_code,
                                                    MockDirectoryEntry.number == number).values(name=new_name))
        db.commit()


def _to_approval(client, payee_id="py_tolu", amount=K150) -> dict:
    d = make_draft(client, payee_id=payee_id, amount_kobo=amount)
    r = confirm(client, d)
    assert r.status_code == 202 and r.json()["code"] == "approval_required", r.text
    return d


def _approval_id(client) -> str:
    return client.get("/v1/approvals", headers=APPROVER).json()[0]["id"]


# ---- the five demo scripts --------------------------------------------------------------------

def test_demo_1_send_twenty_thousand_to_tolu(client):
    conv = client.post("/v1/conversations", headers=CUSTOMER).json()["id"]
    turn = client.post(f"/v1/conversations/{conv}/turns", headers=CUSTOMER,
                       json={"text": "Send twenty thousand to Tolu"}).json()
    d = turn["draft"]
    assert d["readback"]["spoken"] == ["Twenty thousand naira.", "To Adebayo Tolulope.",
                                       "Zenith Bank, ending four, four, seven, one.", "Pulse your PIN to send."]
    r = confirm(client, d)
    assert r.status_code == 200
    assert r.json()["status"] == "executed" and r.json()["say"] == "Sent. Tolu has it."
    assert r.json()["reference"].startswith("ZEN-")
    assert balance(client) == START - K20


def test_demo_2_two_turns_make_the_same_draft_as_one(client):
    conv = client.post("/v1/conversations", headers=CUSTOMER).json()["id"]
    first = client.post(f"/v1/conversations/{conv}/turns", headers=CUSTOMER, json={"text": "Send money to Tolu"}).json()
    assert first["draft"] is None and first["say"] == "How much should I send to Tolu?"
    second = client.post(f"/v1/conversations/{conv}/turns", headers=CUSTOMER, json={"text": "twenty thousand"}).json()
    one_go = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    assert second["draft"]["readback_hash"] == one_go["readback_hash"]
    assert second["draft"]["readback"]["spoken"] == one_go["readback"]["spoken"]
    assert confirm(client, second["draft"]).json()["say"] == "Sent. Tolu has it."


def test_demo_3_musa_scam_pause_then_funmi_approves(client):
    d = make_draft(client, payee_id="py_musa", amount_kobo=K150)
    r = confirm(client, d, call_active=True)
    assert r.status_code == 409 and r.json()["code"] == "pause_required" and r.json()["draft_id"] == d["id"]
    assert r.json()["say"].startswith("Is someone on the phone telling you to send this?")

    r = client.post(f"/v1/drafts/{d['id']}/pause-answer", headers=CUSTOMER, json={"answer": "chose_myself"})
    assert r.status_code == 200 and r.json()["status"] == "awaiting_confirm"
    assert r.json()["say"].startswith("Zenith will never call you to move money.")

    r = confirm(client, d, call_active=True)
    assert r.status_code == 202
    assert r.json() | {"say": None} == {"status": "awaiting_approval", "code": "approval_required", "approver": "Funmi",
                                        "say": None}
    assert client.get(f"/v1/drafts/{d['id']}", headers=CUSTOMER).json()["status"] == "awaiting_approval"

    page = client.get("/approve?token=demo-approver")
    assert page.status_code == 200 and "text/html" in page.headers["content-type"]
    queue = client.get("/v1/approvals", headers=APPROVER).json()
    assert queue[0]["status"] == "pending" and queue[0]["to"] == "Musa Ibrahim" and queue[0]["amount_text"] == "₦150,000"
    assert "balance_kobo" not in queue[0]

    r = client.post(f"/v1/approvals/{queue[0]['id']}/decision", headers=APPROVER, json={"decision": "approve"})
    assert r.status_code == 200
    assert r.json()["approval"] == "approved" and r.json()["status"] == "executed"
    assert r.json()["say"] == "Funmi approved. Sent. Musa has it."

    polled = client.get(f"/v1/drafts/{d['id']}", headers=CUSTOMER).json()
    assert polled["status"] == "executed" and polled["say"] == "Funmi approved. Sent. Musa has it."
    assert balance(client) == START - K150
    musa = next(p for p in client.get("/v1/payees", headers=CUSTOMER).json() if p["id"] == "py_musa")
    assert musa["is_new"] is False


def test_demo_4_block_my_card(client):
    conv = client.post("/v1/conversations", headers=CUSTOMER).json()["id"]
    turn = client.post(f"/v1/conversations/{conv}/turns", headers=CUSTOMER, json={"text": "Block my card"}).json()
    d = turn["draft"]
    assert d["kind"] == "card_block" and d["readback"]["name"] == "Card ending 8830"
    assert d["readback"]["bank"] == "Zenith Bank" and d["readback"]["amount_kobo"] is None
    r = confirm(client, d)
    assert r.status_code == 200 and r.json()["say"] == "Card blocked. A new one is on its way."
    assert client.get("/v1/cards", headers=CUSTOMER).json()[0]["status"] == "blocked"
    assert balance(client) == START


def test_demo_5_wrong_pin_three_times_locks(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    answers = [confirm(client, d, pin=PIN_BAD) for _ in range(3)]
    assert [a.status_code for a in answers] == [401, 401, 401]
    assert [a.json()["tries_left"] for a in answers] == [2, 1, 0]
    assert all(a.json()["code"] == "pin_incorrect" for a in answers)
    assert "two tries left" in answers[0].json()["say"]
    assert "one try left" in answers[1].json()["say"]
    assert "locked" in answers[2].json()["say"]
    locked = confirm(client, d, pin=PIN_OK)
    assert locked.status_code == 401 and locked.json()["tries_left"] == 0
    assert balance(client) == START and moves() == []


# ---- step 1: real draft ----------------------------------------------------------------------

def test_unknown_draft_is_not_found(client):
    r = client.post("/v1/drafts/dr_nope/confirm", headers=CUSTOMER, json={"readback_hash": "x", "pin_block": PIN_OK})
    assert r.status_code == 404 and r.json()["code"] == "not_found"


def test_another_customers_draft_is_not_found(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    with SessionFactory() as db:
        db.add(Customer(id="cus_other", core_ref="ZC-OTHER", phone="+234000"))
        db.flush()
        db.add(Token(token="other-customer", role="customer", customer_id="cus_other"))
        db.commit()
    r = confirm(client, d, headers={"Authorization": "Bearer other-customer"})
    assert r.status_code == 404
    assert client.get(f"/v1/drafts/{d['id']}", headers={"Authorization": "Bearer other-customer"}).status_code == 404


def test_expired_draft_answers_410_before_any_other_check(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    _set_draft(d["id"], expires_at=utcnow() - timedelta(seconds=1))
    r = confirm(client, d, pin=PIN_BAD, readback_hash="sha256:wrong")
    assert r.status_code == 410 and r.json()["code"] == "draft_expired" and r.json()["draft_id"] == d["id"]
    assert pin_tries_left(client) == 3
    view = client.get(f"/v1/drafts/{d['id']}", headers=CUSTOMER).json()
    assert view["status"] == "expired" and "ten minutes" in view["say"]


def test_get_expires_a_stale_draft(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    _set_draft(d["id"], expires_at=utcnow() - timedelta(seconds=1))
    assert client.get(f"/v1/drafts/{d['id']}", headers=CUSTOMER).json()["status"] == "expired"


def test_cancelled_draft_is_closed(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    r = client.post(f"/v1/drafts/{d['id']}/cancel", headers=CUSTOMER)
    assert r.json() == {"status": "cancelled", "say": "Cancelled. Nothing was sent."}
    r = confirm(client, d)
    assert r.status_code == 409 and r.json()["code"] == "draft_closed" and r.json()["status_now"] == "cancelled"
    again = client.post(f"/v1/drafts/{d['id']}/cancel", headers=CUSTOMER)
    assert again.status_code == 409 and again.json()["code"] == "draft_closed"


def test_executed_draft_cannot_pay_twice(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    first = confirm(client, d)
    second = confirm(client, d)
    assert second.status_code == 409 and second.json()["code"] == "already_executed"
    assert second.json()["reference"] == first.json()["reference"]
    assert balance(client) == START - K20 and len(moves()) == 1
    cancel = client.post(f"/v1/drafts/{d['id']}/cancel", headers=CUSTOMER)
    assert cancel.status_code == 409 and cancel.json()["code"] == "already_executed"


# ---- step 2: heard it --------------------------------------------------------------------------

def test_wrong_hash_comes_back_with_the_current_readback(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    r = confirm(client, d, readback_hash="sha256:something-else", pin=PIN_BAD)
    assert r.status_code == 409 and r.json()["code"] == "readback_mismatch"
    assert r.json()["readback_hash"] == d["readback_hash"] and r.json()["readback"]["name"] == "Adebayo Tolulope"
    assert pin_tries_left(client) == 3  # the PIN was never looked at
    assert confirm(client, d, readback_hash=r.json()["readback_hash"]).status_code == 200


def test_name_changed_at_the_bank_means_listen_again(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    _rename("057", "2044714471", "ADEBAYO TOLULOPE GRACE")
    r = confirm(client, d)
    assert r.status_code == 409 and r.json()["code"] == "readback_mismatch"
    assert r.json()["say"] == "Something changed since I read it to you. Listen again."
    assert r.json()["readback"]["name"] == "Adebayo Tolulope Grace"
    assert r.json()["readback_hash"] != d["readback_hash"]
    assert balance(client) == START
    ok = confirm(client, d, readback_hash=r.json()["readback_hash"])
    assert ok.status_code == 200 and balance(client) == START - K20


def test_readback_hash_is_the_documented_canonical_sha256(client):
    import hashlib
    import json
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    core = {"kind": "transfer", "name": "Adebayo Tolulope", "bank": "Zenith Bank", "account_last4": "4471",
            "amount_kobo": K20}
    expected = "sha256:" + hashlib.sha256(json.dumps(core, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    assert d["readback_hash"] == expected


def test_bank_down_at_confirm_keeps_the_draft_open(client):
    d = make_draft(client, payee_id="py_musa", amount_kobo=1_000_000)
    client.post("/_mock/controls", json={"banks_down": ["999992"]})
    r = confirm(client, d)
    assert r.status_code == 503 and r.json()["code"] == "bank_unavailable" and r.headers["Retry-After"] == "30"
    assert r.json()["zenith_code"] == "91" and r.json()["say"].startswith("OPay is not answering")
    assert client.get(f"/v1/drafts/{d['id']}", headers=CUSTOMER).json()["status"] == "awaiting_confirm"
    client.post("/_mock/controls", json={"banks_down": []})
    assert confirm(client, d).status_code == 200


# ---- step 3: limits ----------------------------------------------------------------------------

def test_per_transaction_limit_comes_before_pause_and_pin(client):
    d = make_draft(client, payee_id="py_musa", amount_kobo=150_000_000)  # ₦1.5m, over ₦1m
    r = confirm(client, d, call_active=True, pin=PIN_BAD)
    assert r.status_code == 422 and r.json()["code"] == "limit_exceeded"
    assert r.json()["limit"] == "per_transaction" and r.json()["max_kobo"] == 100_000_000
    assert "The most is one million naira" in r.json()["say"]
    assert pin_tries_left(client) == 3


def test_customers_own_daily_limit_is_lower(client):
    client.put("/v1/me/preferences", headers=CUSTOMER, json={"daily_limit_kobo": 3_000_000})
    assert confirm(client, make_draft(client, payee_id="py_tolu", amount_kobo=K20)).status_code == 200
    r = confirm(client, make_draft(client, payee_id="py_tolu", amount_kobo=K20))
    assert r.status_code == 422 and r.json()["limit"] == "daily" and r.json()["remaining_today_kobo"] == 1_000_000
    assert "ten thousand naira more today" in r.json()["say"]


def test_insufficient_funds_fails_the_draft(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=90_000_000)  # ₦900,000 > ₦842,300
    client.post("/v1/approvers", headers=CUSTOMER, json={"name": "Funmi", "phone": "+2348059876543",
                                                         "threshold_kobo": 99_000_000})
    r = confirm(client, d)
    assert r.status_code == 422 and r.json()["code"] == "insufficient_funds"
    view = client.get(f"/v1/drafts/{d['id']}", headers=CUSTOMER).json()
    assert view["status"] == "failed" and view["say"] == "That is more than you have. Nothing was sent."
    again = confirm(client, d)
    assert again.status_code == 409 and again.json()["status_now"] == "failed"
    assert balance(client) == START


# ---- step 4: scam pause ------------------------------------------------------------------------

def test_pause_comes_before_the_pin(client):
    d = make_draft(client, payee_id="py_musa", amount_kobo=K150)
    r = confirm(client, d, call_active=True, pin=PIN_BAD)
    assert r.status_code == 409 and r.json()["code"] == "pause_required"
    assert pin_tries_left(client) == 3
    again = confirm(client, d, call_active=True)
    assert again.status_code == 409 and again.json()["code"] == "pause_required"


def test_pause_threshold_is_inclusive_and_needs_a_call(client):
    below = make_draft(client, payee_id="py_musa", amount_kobo=4_999_900)  # ₦49,999
    assert confirm(client, below, call_active=True).status_code == 200
    with SessionFactory() as db:  # Musa is known now; make him new again for the next case
        db.execute(update(Payee).where(Payee.id == "py_musa").values(first_paid_at=None))
        db.commit()
    at = make_draft(client, payee_id="py_musa", amount_kobo=5_000_000)  # ₦50,000
    assert confirm(client, at, call_active=False).status_code == 200  # no call, no pause
    with SessionFactory() as db:
        db.execute(update(Payee).where(Payee.id == "py_musa").values(first_paid_at=None))
        db.commit()
    at2 = make_draft(client, payee_id="py_musa", amount_kobo=5_000_000)
    assert confirm(client, at2, call_active=True).json()["code"] == "pause_required"


def test_known_payee_is_not_paused(client):
    d = make_draft(client, payee_id="py_mama", amount_kobo=5_000_000)
    assert confirm(client, d, call_active=True).status_code == 200


def test_stop_cancels_and_flags_the_payee(client):
    d = make_draft(client, payee_id="py_musa", amount_kobo=K150)
    confirm(client, d, call_active=True)
    r = client.post(f"/v1/drafts/{d['id']}/pause-answer", headers=CUSTOMER, json={"answer": "stop"})
    assert r.json() == {"status": "cancelled", "say": "Stopped. Your money is safe. Nothing was sent."}
    musa = next(p for p in client.get("/v1/payees", headers=CUSTOMER).json() if p["id"] == "py_musa")
    assert musa["flagged"] is True

    small = make_draft(client, payee_id="py_musa", amount_kobo=100_000)
    assert small["readback"]["spoken"][0].startswith("You stopped a payment to this person")
    r = confirm(client, small)  # flagged pauses even without a call
    assert r.status_code == 409 and r.json()["say"].startswith("You stopped a payment to this person after a phone call before")
    answer = client.post(f"/v1/drafts/{small['id']}/pause-answer", headers=CUSTOMER, json={"answer": "chose_myself"})
    assert answer.json()["say"] == "Okay. Pulse your PIN to carry on."  # no call this time
    assert confirm(client, small).status_code == 200


def test_pause_answer_needs_a_paused_draft(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    r = client.post(f"/v1/drafts/{d['id']}/pause-answer", headers=CUSTOMER, json={"answer": "stop"})
    assert r.status_code == 409 and r.json()["code"] == "not_paused"


# ---- step 5 and 6: PIN, then co-approver ------------------------------------------------------

def test_pin_is_checked_before_the_approver_is_asked(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K150)
    assert confirm(client, d, pin=PIN_BAD).status_code == 401
    assert client.get("/v1/approvals", headers=APPROVER).json() == []
    r = confirm(client, d)
    assert r.status_code == 202 and "Funmi checks transfers above fifty thousand naira" in r.json()["say"]


def test_approver_only_above_threshold(client):
    at = make_draft(client, payee_id="py_tolu", amount_kobo=5_000_000)
    assert confirm(client, at).status_code == 200
    above = make_draft(client, payee_id="py_tolu", amount_kobo=5_000_100)
    assert confirm(client, above).status_code == 202


def test_confirming_again_while_waiting_does_not_ask_twice(client):
    d = _to_approval(client)
    r = confirm(client, d)
    assert r.status_code == 202 and r.json()["say"] == "Still waiting for Funmi. Nothing has moved."
    assert len(client.get("/v1/approvals", headers=APPROVER).json()) == 1


def test_decline_cancels_and_nothing_moves(client):
    d = _to_approval(client)
    r = client.post(f"/v1/approvals/{_approval_id(client)}/decision", headers=APPROVER, json={"decision": "decline"})
    assert r.json() == {"approval": "declined", "status": "cancelled", "say": "Funmi said no. Nothing was sent."}
    assert client.get(f"/v1/drafts/{d['id']}", headers=CUSTOMER).json()["status"] == "cancelled"
    assert balance(client) == START and moves() == []


def test_deciding_twice_is_refused(client):
    _to_approval(client)
    aid = _approval_id(client)
    client.post(f"/v1/approvals/{aid}/decision", headers=APPROVER, json={"decision": "approve"})
    r = client.post(f"/v1/approvals/{aid}/decision", headers=APPROVER, json={"decision": "decline"})
    assert r.status_code == 409 and r.json()["code"] == "already_decided"
    assert balance(client) == START - K150


def test_approval_expires_after_fifteen_minutes(client):
    d = _to_approval(client)
    with SessionFactory() as db:
        db.execute(update(Approval).values(asked_at=utcnow() - timedelta(minutes=16)))
        db.commit()
    view = client.get(f"/v1/drafts/{d['id']}", headers=CUSTOMER).json()
    assert view["status"] == "expired"
    assert view["say"] == "Funmi did not answer in time, so nothing was sent. You can start again."
    r = confirm(client, d)
    assert r.status_code == 410 and r.json()["code"] == "approval_expired"
    queue = client.get("/v1/approvals", headers=APPROVER).json()
    assert queue[0]["status"] == "expired"
    late = client.post(f"/v1/approvals/{queue[0]['id']}/decision", headers=APPROVER, json={"decision": "approve"})
    assert late.status_code == 410 and late.json()["code"] == "approval_expired"
    assert balance(client) == START and moves() == []


def test_late_yes_without_any_poll_still_expires(client):
    _to_approval(client)
    aid = _approval_id(client)
    with SessionFactory() as db:
        db.execute(update(Approval).values(asked_at=utcnow() - timedelta(minutes=16)))
        db.commit()
    r = client.post(f"/v1/approvals/{aid}/decision", headers=APPROVER, json={"decision": "approve"})
    assert r.status_code == 410
    assert balance(client) == START


def test_account_is_checked_again_just_before_an_approved_transfer(client):
    d = _to_approval(client, payee_id="py_musa")
    _rename("999992", "8031447710", "MUSA IBRAHIM ABDUL")
    r = client.post(f"/v1/approvals/{_approval_id(client)}/decision", headers=APPROVER, json={"decision": "approve"})
    body = r.json()
    assert body["approval"] == "approved" and body["status"] == "awaiting_confirm" and body["code"] == "readback_mismatch"
    assert balance(client) == START and moves() == []
    view = client.get(f"/v1/drafts/{d['id']}", headers=CUSTOMER).json()
    assert view["readback"]["name"] == "Musa Ibrahim Abdul" and view["readback_hash"] != d["readback_hash"]


def test_cancel_withdraws_the_pending_approval(client):
    d = _to_approval(client)
    r = client.post(f"/v1/drafts/{d['id']}/cancel", headers=CUSTOMER)
    assert r.json()["status"] == "cancelled"
    queue = client.get("/v1/approvals", headers=APPROVER).json()
    assert queue[0]["status"] == "withdrawn" and queue[0]["draft_status"] == "cancelled"
    late = client.post(f"/v1/approvals/{queue[0]['id']}/decision", headers=APPROVER, json={"decision": "approve"})
    assert late.status_code == 409 and late.json()["code"] == "already_decided"
    assert balance(client) == START


def test_removing_the_approver_means_no_approval_needed(client):
    approver_id = client.get("/v1/approvers", headers=CUSTOMER).json()[0]["id"]
    assert client.delete(f"/v1/approvers/{approver_id}", headers=CUSTOMER).json() == {"removed": approver_id}
    assert confirm(client, make_draft(client, payee_id="py_tolu", amount_kobo=K150)).status_code == 200


# ---- step 7: execute ---------------------------------------------------------------------------

def test_zenith_key_is_draft_and_attempt(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    confirm(client, d)
    assert [m.idempotency_key for m in moves()] == [f"{d['id']}:1"]


def test_in_progress_is_processing_then_requeried_by_get(client, core):
    client.post("/_mock/controls", json={"next_transfer_code": "09", "settle_after_s": 600})
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    r = confirm(client, d)
    assert r.status_code == 202 and r.json()["code"] == "processing" and r.json()["status"] == "processing"
    assert r.json()["say"].endswith("Do not send it again.")
    again = confirm(client, d)
    assert again.status_code == 409 and again.json()["code"] == "processing"
    assert client.get(f"/v1/drafts/{d['id']}", headers=CUSTOMER).json()["status"] == "processing"
    core.settle_all_now()
    done = client.get(f"/v1/drafts/{d['id']}", headers=CUSTOMER).json()
    assert done["status"] == "executed" and done["reference"] == r.json()["reference"]
    assert done["say"] == "Sent. Tolu has it."
    assert balance(client) == START - K20 and len(moves()) == 1


def test_in_progress_that_settles_during_requery_is_executed(client, core):
    client.post("/_mock/controls", json={"next_transfer_code": "09", "settle_after_s": 0})
    r = confirm(client, make_draft(client, payee_id="py_tolu", amount_kobo=K20))
    assert r.status_code == 200 and r.json()["status"] == "executed"


def test_retry_after_91_uses_a_new_key_and_pays_once(client):
    client.post("/_mock/controls", json={"next_transfer_code": "91"})
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    r = confirm(client, d)
    assert r.status_code == 503 and r.json()["code"] == "bank_unavailable" and r.json()["zenith_code"] == "91"
    assert r.headers["Retry-After"] == "30" and r.json()["retry_after"] == 30
    assert client.get(f"/v1/drafts/{d['id']}", headers=CUSTOMER).json()["status"] == "awaiting_confirm"
    assert balance(client) == START
    ok = confirm(client, d)
    assert ok.status_code == 200
    keys = {m.idempotency_key: m.code for m in moves()}
    assert keys == {f"{d['id']}:1": "91", f"{d['id']}:2": "00"}
    assert balance(client) == START - K20


def test_retry_after_96_too(client):
    client.post("/_mock/controls", json={"next_transfer_code": "96"})
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    assert confirm(client, d).json()["zenith_code"] == "96"
    assert confirm(client, d).status_code == 200
    assert balance(client) == START - K20


def test_timeout_after_the_money_went_is_found_by_requery(client):
    client.post("/_mock/controls", json={"next_transfer_timeout": "after_send"})
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    r = confirm(client, d)
    assert r.status_code == 200 and r.json()["status"] == "executed"
    assert confirm(client, d).json()["code"] == "already_executed"
    assert len(moves()) == 1 and balance(client) == START - K20


def test_timeout_before_arrival_keeps_the_same_key(client):
    client.post("/_mock/controls", json={"next_transfer_timeout": "before_send"})
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    r = confirm(client, d)
    assert r.status_code == 503 and r.json()["code"] == "bank_unavailable" and r.json()["zenith_code"] == "timeout"
    assert client.get(f"/v1/drafts/{d['id']}", headers=CUSTOMER).json()["status"] == "awaiting_confirm"
    assert confirm(client, d).status_code == 200
    assert [m.idempotency_key for m in moves()] == [f"{d['id']}:1"]
    assert balance(client) == START - K20


def test_refused_transfer_fails_the_draft(client):
    client.post("/_mock/controls", json={"next_transfer_code": "57"})
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    r = confirm(client, d)
    assert r.status_code == 422 and r.json()["code"] == "failed" and r.json()["zenith_code"] == "57"
    assert client.get(f"/v1/drafts/{d['id']}", headers=CUSTOMER).json()["status"] == "failed"


def test_double_tap_pays_once(client, core):
    import time as _time
    real_pause = core._pause
    core._pause = lambda op: _time.sleep(0.2) if op == "move" else None
    try:
        d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
        results = []
        threads = [threading.Thread(target=lambda: results.append(confirm(client, d))) for _ in range(3)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()
    finally:
        core._pause = real_pause
    codes = sorted(r.status_code for r in results)
    assert codes == [200, 409, 409]
    assert balance(client) == START - K20 and len(moves()) == 1


def test_idempotent_confirm_replays_the_first_answer(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    first = confirm(client, d, key="tap-1")
    second = confirm(client, d, key="tap-1")
    assert second.status_code == 200 and second.json() == first.json()
    assert second.headers.get("idempotent-replayed") == "true"
    assert len(moves()) == 1


# ---- first payment, cards ---------------------------------------------------------------------

def test_paying_someone_new_saves_them(client):
    look = client.post("/v1/payees/lookup", headers=CUSTOMER, json={"bank_code": "058", "account_number": "0123456789"})
    assert look.status_code == 200
    name = look.json()["name"]
    d = make_draft(client, bank_code="058", account_number="0123456789", amount_kobo=1_000_000)
    assert d["readback"]["name"] == name and d["readback"]["account_last4"] == "6789"
    assert confirm(client, d).json()["say"] == f"Sent. {name.split()[-1]} has it."
    saved = [p for p in client.get("/v1/payees", headers=CUSTOMER).json() if p["account_last4"] == "6789"]
    assert len(saved) == 1 and saved[0]["is_new"] is False and saved[0]["bank"] == "GTBank"
    assert "account_number" not in _draft_row(d["id"]).payload

    again = make_draft(client, bank_code="058", account_number="0123456789", amount_kobo=1_000_000)
    confirm(client, again)
    assert len([p for p in client.get("/v1/payees", headers=CUSTOMER).json() if p["account_last4"] == "6789"]) == 1


def test_a_new_account_on_a_call_is_paused(client):
    d = make_draft(client, bank_code="044", account_number="1234567890", amount_kobo=K150)
    assert confirm(client, d, call_active=True).json()["code"] == "pause_required"


def test_account_number_is_removed_when_a_draft_closes(client):
    d = make_draft(client, bank_code="058", account_number="0123456789", amount_kobo=1_000_000)
    assert _draft_row(d["id"]).payload["account_number"] == "0123456789"
    client.post(f"/v1/drafts/{d['id']}/cancel", headers=CUSTOMER)
    assert "account_number" not in _draft_row(d["id"]).payload


def test_card_already_blocked(client):
    confirm(client, make_draft(client, kind="card_block", card_id="cd_1"))
    r = client.post("/v1/drafts", headers=CUSTOMER, json={"kind": "card_block", "card_id": "cd_1"})
    assert r.status_code == 409 and r.json()["code"] == "already_blocked"


def test_card_blocked_elsewhere_before_confirm(client, core):
    first = make_draft(client, kind="card_block", card_id="cd_1")
    second = make_draft(client, kind="card_block", card_id="cd_1")
    confirm(client, first)
    r = confirm(client, second)
    assert r.status_code == 409 and r.json()["code"] == "already_blocked"
    assert client.get(f"/v1/drafts/{second['id']}", headers=CUSTOMER).json()["status"] == "cancelled"


# ---- step 8: audit ----------------------------------------------------------------------------

def test_every_step_is_in_the_audit_chain(client):
    d = make_draft(client, payee_id="py_musa", amount_kobo=K150)
    confirm(client, d, call_active=True)
    client.post(f"/v1/drafts/{d['id']}/pause-answer", headers=CUSTOMER, json={"answer": "chose_myself"})
    confirm(client, d, pin=PIN_BAD)
    confirm(client, d)
    client.post(f"/v1/approvals/{_approval_id(client)}/decision", headers=APPROVER, json={"decision": "approve"})
    actions = [e["action"] for e in reversed(client.get("/v1/audit", headers=CUSTOMER).json())]
    assert actions == ["draft_created", "scam_pause", "pause_chose_myself", "pin_incorrect", "approval_requested",
                       "approval_given", "sending", "executed"]
    assert client.get("/v1/audit/verify", headers=CUSTOMER).json() == {"ok": True, "entries": len(actions)}
    text = str(client.get("/v1/audit", headers=CUSTOMER).json())
    assert PIN_OK not in text and PIN_BAD not in text and "8031447710" not in text
