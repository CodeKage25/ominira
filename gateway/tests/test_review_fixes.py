"""Regression tests for the independent review of the Gateway. Each asserts the fixed behaviour."""

import hashlib
import json
import threading
import time
from datetime import timedelta
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select, update

from ominira.assistant import claude, guard
from ominira.core.base import CoreTimeout
from ominira.database import SessionFactory, utcnow
from ominira.main import app
from ominira.settings import settings
from ominira.tables import Draft, Payee, StoredResponse, Turn
from tests.conftest import APPROVER, ASSISTANT, CUSTOMER, PIN_BAD, PIN_OK, balance, confirm, make_draft, moves

START = 84_230_000
K20 = 2_000_000
K60 = 6_000_000
K150 = 15_000_000


def _flag_musa(client):
    d = make_draft(client, payee_id="py_musa", amount_kobo=K60)
    assert confirm(client, d, call_active=True).json()["code"] == "pause_required"
    r = client.post(f"/v1/drafts/{d['id']}/pause-answer", headers=CUSTOMER, json={"answer": "stop"})
    assert r.json()["status"] == "cancelled"


def _draft_row(draft_id):
    with SessionFactory() as db:
        return db.get(Draft, draft_id)


def _status(client, draft_id):
    return client.get(f"/v1/drafts/{draft_id}", headers=CUSTOMER).json()["status"]


# ---- 1. a saved payee typed as an account number is still that payee ---------------------------

def test_flagged_payee_typed_as_an_account_number_is_paused(client):
    _flag_musa(client)
    d = make_draft(client, bank_code="999992", account_number="8031447710", amount_kobo=K20)
    assert d["readback"]["spoken"][0].startswith("You stopped a payment to this person")
    assert _draft_row(d["id"]).payload["payee_id"] == "py_musa"
    assert "account_number" not in _draft_row(d["id"]).payload
    r = confirm(client, d)  # flagged pauses even without a call
    assert r.status_code == 409 and r.json()["code"] == "pause_required"
    assert balance(client) == START


def test_known_payee_typed_as_a_number_counts_as_known(client):
    d = make_draft(client, bank_code="057", account_number="2044714471", amount_kobo=K60 - 1_000_100)
    assert confirm(client, d, call_active=True).status_code == 200  # Tolu is not new: no pause
    tolus = [p for p in client.get("/v1/payees", headers=CUSTOMER).json() if p["account_last4"] == "4471"]
    assert len(tolus) == 1  # no duplicate payee


def test_new_person_by_number_is_new_and_paused_on_a_call(client):
    d = make_draft(client, bank_code="999992", account_number="8031447710", amount_kobo=K60)  # Musa, never paid
    assert confirm(client, d, call_active=True).json()["code"] == "pause_required"


# ---- 2. a payee flagged while an approval waits is never paid -----------------------------------

def test_stop_cancels_every_other_open_request_to_that_person(client):
    waiting = make_draft(client, payee_id="py_musa", amount_kobo=K150)
    assert confirm(client, waiting).json()["code"] == "approval_required"
    by_number = make_draft(client, bank_code="999992", account_number="8031447710", amount_kobo=100_000)
    other_person = make_draft(client, payee_id="py_tolu", amount_kobo=100_000)
    _flag_musa(client)
    assert _status(client, waiting["id"]) == "cancelled"
    assert _status(client, by_number["id"]) == "cancelled"
    assert _status(client, other_person["id"]) == "awaiting_confirm"
    row = client.get("/v1/approvals", headers=APPROVER).json()[0]
    assert row["status"] == "withdrawn" and row["flagged"] is True
    r = client.post(f"/v1/approvals/{row['id']}/decision", headers=APPROVER, json={"decision": "approve"})
    assert r.status_code == 409 and balance(client) == START and moves() == []


def test_approval_is_held_if_the_payee_is_flagged_by_the_time_of_the_yes(client):
    d = make_draft(client, payee_id="py_musa", amount_kobo=K150)
    confirm(client, d)
    with SessionFactory() as db:  # flagged by some other route while Funmi was thinking
        db.execute(update(Payee).where(Payee.id == "py_musa").values(flagged=True))
        db.commit()
    row = client.get("/v1/approvals", headers=APPROVER).json()[0]
    assert row["flagged"] is True and row["status"] == "pending"
    r = client.post(f"/v1/approvals/{row['id']}/decision", headers=APPROVER, json={"decision": "approve"}).json()
    assert r["approval"] == "approved" and r["status"] == "paused" and r["code"] == "pause_required"
    assert balance(client) == START and moves() == []
    stop = client.post(f"/v1/drafts/{d['id']}/pause-answer", headers=CUSTOMER, json={"answer": "stop"})
    assert stop.json()["status"] == "cancelled"


def test_approvals_show_flagged_false_normally(client):
    confirm(client, make_draft(client, payee_id="py_tolu", amount_kobo=K150))
    assert client.get("/v1/approvals", headers=APPROVER).json()[0]["flagged"] is False


# ---- 3. ACCOUNT_n labels last for the conversation ---------------------------------------------

def _scripted_claude(monkeypatch, script):
    settings.assistant, settings.anthropic_api_key = "claude", "test-key"
    seen = []

    class Fake:
        def __init__(self):
            self.messages = self

        def create(self, **kw):
            seen.append(json.loads(json.dumps(kw["messages"], default=str)))
            return script.pop(0)

    monkeypatch.setattr(claude, "make_client", Fake)
    return seen


def _tool(name, args):
    return SimpleNamespace(content=[SimpleNamespace(type="tool_use", id="t1", name=name, input=args)],
                           stop_reason="tool_use")


def _text(t):
    return SimpleNamespace(content=[SimpleNamespace(type="text", text=t)], stop_reason="end_turn")


def test_a_label_keeps_its_number_across_turns(client, monkeypatch):
    _scripted_claude(monkeypatch, [
        _tool("find_account", {"bank": "Zenith", "account_ref": "ACCOUNT_1"}),
        _text("That is Adebayo Tolulope at Zenith Bank."),
        _tool("draft_transfer", {"amount_naira": 20000, "bank": "Zenith", "account_ref": "ACCOUNT_1"}),
    ])
    conv = client.post("/v1/conversations", headers=CUSTOMER).json()["id"]
    one = client.post(f"/v1/conversations/{conv}/turns", headers=CUSTOMER, json={"text": "who owns 2044714471 at Zenith"})
    assert "ACCOUNT_1" in one.json()["heard"]
    two = client.post(f"/v1/conversations/{conv}/turns", headers=CUSTOMER,
                      json={"text": "send twenty thousand to that one, my sister's is 2081112222"}).json()
    assert "ACCOUNT_2" in two["heard"] and "ACCOUNT_1" not in two["heard"]
    assert two["draft"]["readback"]["account_last4"] == "4471"
    assert two["draft"]["readback"]["name"] == "Adebayo Tolulope"


def test_an_unknown_label_is_refused(client, monkeypatch):
    seen = _scripted_claude(monkeypatch, [
        _tool("draft_transfer", {"amount_naira": 20000, "bank": "GTBank", "account_ref": "ACCOUNT_7"}),
        _text("Please say the account number again."),
    ])
    conv = client.post("/v1/conversations", headers=CUSTOMER).json()["id"]
    body = client.post(f"/v1/conversations/{conv}/turns", headers=CUSTOMER, json={"text": "send to that account"}).json()
    assert body["draft"] is None
    assert "Unknown account label" in json.dumps(seen[1])


def test_rules_account_then_amount_on_the_next_turn(client):
    conv = client.post("/v1/conversations", headers=CUSTOMER).json()["id"]
    one = client.post(f"/v1/conversations/{conv}/turns", headers=CUSTOMER,
                      json={"text": "send money to 0123456789 at GTBank"}).json()
    assert one["draft"] is None and one["say"].startswith("How much should I send to ")
    two = client.post(f"/v1/conversations/{conv}/turns", headers=CUSTOMER, json={"text": "twenty thousand"}).json()
    assert two["draft"]["readback"]["account_last4"] == "6789" and two["draft"]["readback"]["bank"] == "GTBank"
    assert two["draft"]["readback"]["amount_kobo"] == K20
    with SessionFactory() as db:
        assert all("0123456789" not in t.heard for t in db.scalars(select(Turn)))


def test_rules_ask_for_the_bank(client):
    conv = client.post("/v1/conversations", headers=CUSTOMER).json()["id"]
    body = client.post(f"/v1/conversations/{conv}/turns", headers=CUSTOMER, json={"text": "send 5000 to 0123456789"}).json()
    assert body["draft"] is None and body["say"] == "Which bank is that account with?"


# ---- 4. the PIN guard and masking ---------------------------------------------------------------

@pytest.mark.parametrize("text", [
    "2075 is my PIN, send twenty thousand to Tolu",
    "my PIN, which is 2075, send twenty thousand to Tolu",
    "pin mi ni 2075",
    "my pin na im be 2075",
    "the code to my account is 2075",
    "my pin is 2.0.7.5",
    "PIN 20/75",
    "lambar sirri ta 2075",
    "my password na two zero seven five",
])
def test_spoken_pins_are_caught_and_masked(text):
    assert guard.spoken_pin(text)
    masked, _ = guard.mask(text)
    assert "2075" not in masked.replace(" ", "").replace(".", "").replace("/", "") and "[PIN]" in masked


@pytest.mark.parametrize("text", [
    "pulse your PIN to send 2000",
    "send 2000 naira to Tolu, how does the pin work",
    "my pin is fine, send ₦5000 to Mama",
    "send 20k to Tolu",
])
def test_amounts_near_pin_words_are_not_pins(text):
    assert not guard.spoken_pin(text)


@pytest.mark.parametrize("text,secret,label", [
    ("send 5000 to 204.471.4471 at Zenith", "4471", "ACCOUNT_1"),
    ("send 5000 to acct2044714471 Zenith", "2044714471", "ACCOUNT_1"),
    ("my card 5399 8312 3456 8830 was stolen", "8830", "[CARD]"),
    ("my card 5399831234568830 was stolen", "8830", "[CARD]"),
    ("my bvn is 222.123.456.78", "78", "[BVN]"),
])
def test_masking_catches_split_and_glued_numbers(text, secret, label):
    masked, _ = guard.mask(text)
    assert secret not in masked and label in masked


def test_masking_keeps_amounts_and_dates():
    masked, accounts = guard.mask("send 20,000 to Tolu on 21/10/2026, or ₦1.5 million")
    assert masked == "send 20,000 to Tolu on 21/10/2026, or ₦1.5 million" and accounts == {}


def test_a_pin_said_after_the_word_never_reaches_the_model(client, monkeypatch):
    settings.assistant, settings.anthropic_api_key = "claude", "test-key"
    monkeypatch.setattr(claude, "make_client", lambda: pytest.fail("the model must not be called"))
    conv = client.post("/v1/conversations", headers=CUSTOMER).json()["id"]
    body = client.post(f"/v1/conversations/{conv}/turns", headers=CUSTOMER,
                       json={"text": "2075 is my PIN, what is my balance"}).json()
    assert "2075" not in body["heard"] and body["say"].startswith("Never say your PIN")
    with SessionFactory() as db:
        assert all("2075" not in t.heard for t in db.scalars(select(Turn)))


# ---- 5. no PIN can be recovered from stored idempotency fingerprints ----------------------------

def test_stored_fingerprints_are_keyed(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    body = {"readback_hash": d["readback_hash"], "pin_block": PIN_OK, "call_active": False}
    raw = json.dumps(body, separators=(",", ":")).encode()
    headers = CUSTOMER | {"Idempotency-Key": "k1", "Content-Type": "application/json"}
    first = client.post(f"/v1/drafts/{d['id']}/confirm", content=raw, headers=headers)
    with SessionFactory() as db:
        stored = {s.request_hash for s in db.scalars(select(StoredResponse))}
    for n in range(10_000):
        block = "demo-" + "".join(str(9 - int(c)) for c in f"{n:04d}")
        candidate = json.dumps(body | {"pin_block": block}, separators=(",", ":")).encode()
        assert hashlib.sha256(candidate).hexdigest() not in stored
    replay = client.post(f"/v1/drafts/{d['id']}/confirm", content=raw, headers=headers)
    assert replay.json() == first.json() and replay.headers["idempotent-replayed"] == "true"


# ---- 6. a timed-out transfer is never cancelled or expired into "nothing sent" ------------------

def test_timeout_then_cancel_keeps_checking_the_same_key(client, core, monkeypatch):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    real = core.transfer
    held = {}

    def in_flight(customer_ref, enquiry_ref, amount, key, narration):
        held["args"] = (customer_ref, enquiry_ref, amount, key, narration)
        raise CoreTimeout("slow network")

    monkeypatch.setattr(core, "transfer", in_flight)
    r = confirm(client, d)
    assert r.status_code == 202 and r.json()["code"] == "processing"
    cancel = client.post(f"/v1/drafts/{d['id']}/cancel", headers=CUSTOMER)
    assert cancel.status_code == 409 and cancel.json()["code"] == "processing"
    with SessionFactory() as db:
        db.execute(update(Draft).where(Draft.id == d["id"]).values(expires_at=utcnow() - timedelta(minutes=5)))
        db.commit()
    assert _status(client, d["id"]) == "processing"  # never expired while the outcome is unknown

    monkeypatch.setattr(core, "transfer", real)
    real(*held["args"])  # the in-flight request lands late
    view = client.get(f"/v1/drafts/{d['id']}", headers=CUSTOMER).json()
    assert view["status"] == "executed" and view["reference"] and view["say"] == "Sent. Tolu has it."
    assert balance(client) == START - K20 and len(moves()) == 1


# ---- 7. one name-enquiry limit for lookups, drafts and the assistant ----------------------------

def test_new_account_drafts_share_the_lookup_limit(client):
    for i in range(5):
        r = client.post("/v1/drafts", headers=CUSTOMER if i % 2 else ASSISTANT,
                        json={"kind": "transfer", "amount_kobo": 100, "bank_code": "058",
                              "account_number": f"01234{i:03d}77"})
        assert r.status_code == 201
    sixth = client.post("/v1/drafts", headers=CUSTOMER, json={"kind": "transfer", "amount_kobo": 100,
                                                              "bank_code": "058", "account_number": "0123499977"})
    assert sixth.status_code == 429 and sixth.json()["code"] == "rate_limited"
    lookup = client.post("/v1/payees/lookup", headers=CUSTOMER, json={"bank_code": "058", "account_number": "0123499977"})
    assert lookup.status_code == 429
    assert make_draft(client, payee_id="py_tolu", amount_kobo=100)  # saved payees are not counted


def test_confirming_a_new_account_draft_does_not_use_up_the_limit(client):
    drafts = [make_draft(client, bank_code="058", account_number=f"01234{i:03d}77", amount_kobo=100) for i in range(5)]
    assert confirm(client, drafts[0]).status_code == 200


# ---- 8. the customer's own daily limit holds under concurrent confirms --------------------------

def test_own_daily_limit_holds_under_concurrent_confirms(client, core, monkeypatch):
    client.put("/v1/me/preferences", headers=CUSTOMER, json={"daily_limit_kobo": 3_000_000})
    d1 = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    d2 = make_draft(client, payee_id="py_mama", amount_kobo=K20)
    real = core.verify_pin

    def slow(*a, **k):
        time.sleep(0.3)
        return real(*a, **k)

    monkeypatch.setattr(core, "verify_pin", slow)
    out = []
    threads = [threading.Thread(target=lambda d=d: out.append(confirm(client, d))) for d in (d1, d2)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    assert sorted(r.status_code for r in out) == [200, 422]
    assert next(r for r in out if r.status_code == 422).json()["code"] == "limit_exceeded"
    assert balance(client) == START - K20


# ---- 9a. PIN attempts are rate limited per token ------------------------------------------------

def test_pin_attempts_are_limited_per_token(client):
    for _ in range(5):
        assert client.post("/v1/auth/unlock", headers=CUSTOMER, json={"pin_block": PIN_OK}).status_code == 200
    r = client.post("/v1/auth/unlock", headers=CUSTOMER, json={"pin_block": PIN_OK})
    assert r.status_code == 429 and r.json()["code"] == "rate_limited" and "Retry-After" in r.headers
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K20)
    assert confirm(client, d).status_code == 429  # the same counter
    assert balance(client) == START


# ---- 9b. removing or replacing an approver ------------------------------------------------------

def test_removing_the_approver_withdraws_their_requests_and_their_access(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K150)
    assert confirm(client, d).status_code == 202
    apv = client.get("/v1/approvals", headers=APPROVER).json()[0]["id"]
    r = client.delete("/v1/approvers/ap_funmi", headers=CUSTOMER | {"X-Pin-Block": PIN_OK})
    assert r.json() == {"removed": "ap_funmi"}
    assert _status(client, d["id"]) == "cancelled"
    assert client.get("/v1/approvals", headers=APPROVER).status_code == 403
    late = client.post(f"/v1/approvals/{apv}/decision", headers=APPROVER, json={"decision": "approve"})
    assert late.status_code == 403 and balance(client) == START and moves() == []


def test_replacing_the_approver_withdraws_the_old_ones_requests(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=K150)
    confirm(client, d)
    client.post("/v1/approvers", headers=CUSTOMER, json={"name": "Kehinde", "phone": "+2348012345678",
                                                         "threshold_kobo": 10_000_000, "pin_block": PIN_OK})
    assert _status(client, d["id"]) == "cancelled"
    assert client.get("/v1/approvals", headers=APPROVER).status_code == 403


# ---- 9c. weakening a protection needs the PIN ---------------------------------------------------

def test_approver_changes_need_the_pin(client):
    body = {"name": "Eve", "phone": "+2348000000000", "threshold_kobo": 99_000_000}
    r = client.post("/v1/approvers", headers=CUSTOMER, json=body)
    assert r.status_code == 401 and r.json()["code"] == "pin_required"
    r = client.post("/v1/approvers", headers=CUSTOMER, json=body | {"pin_block": PIN_BAD})
    assert r.status_code == 401 and r.json()["code"] == "pin_incorrect" and r.json()["tries_left"] == 2
    r = client.delete("/v1/approvers/ap_funmi", headers=CUSTOMER)
    assert r.status_code == 401 and r.json()["code"] == "pin_required"
    assert client.get("/v1/approvers", headers=CUSTOMER).json()[0]["name"] == "Funmi"


@pytest.mark.parametrize("before,change,needs_pin", [
    ({}, {"pause_threshold_kobo": 9_000_000}, True),            # raise over the ₦50,000 default
    ({}, {"pause_threshold_kobo": 1_000_000}, False),           # lower
    ({"pause_threshold_kobo": 1_000_000}, {"pause_threshold_kobo": None}, True),   # clear: back up to default
    ({}, {"daily_limit_kobo": 3_000_000}, False),               # set one: lower than Zenith's
    ({"daily_limit_kobo": 3_000_000}, {"daily_limit_kobo": 4_000_000}, True),      # raise
    ({"daily_limit_kobo": 3_000_000}, {"daily_limit_kobo": None}, True),           # clear
    ({"daily_limit_kobo": 3_000_000}, {"daily_limit_kobo": 1_000_000}, False),     # lower
    ({}, {"lang": "pcm", "theme": "dark", "speech_rate": 1.2}, False),
])
def test_preferences_need_the_pin_only_to_weaken_a_protection(client, before, change, needs_pin):
    if before:
        assert client.put("/v1/me/preferences", headers=CUSTOMER, json=before).status_code == 200
    r = client.put("/v1/me/preferences", headers=CUSTOMER, json=change)
    if needs_pin:
        assert r.status_code == 401 and r.json()["code"] == "pin_required"
        ok = client.put("/v1/me/preferences", headers=CUSTOMER, json=change | {"pin_block": PIN_OK})
        assert ok.status_code == 200
        r = ok
    assert r.status_code == 200
    for k, v in change.items():
        assert r.json()[k] == v


# ---- 9d. expired drafts are swept --------------------------------------------------------------

def test_expired_drafts_are_swept_on_the_next_draft(client):
    old = make_draft(client, bank_code="058", account_number="0123456789", amount_kobo=100)
    with SessionFactory() as db:
        db.execute(update(Draft).where(Draft.id == old["id"]).values(expires_at=utcnow() - timedelta(seconds=1)))
        db.commit()
    make_draft(client, payee_id="py_tolu", amount_kobo=100)
    row = _draft_row(old["id"])
    assert row.status == "expired" and "account_number" not in row.payload


def test_expired_drafts_are_swept_at_start(client):
    old = make_draft(client, bank_code="058", account_number="0123456789", amount_kobo=100)
    with SessionFactory() as db:
        db.execute(update(Draft).where(Draft.id == old["id"]).values(expires_at=utcnow() - timedelta(seconds=1)))
        db.commit()
    with TestClient(app):
        pass
    assert "account_number" not in _draft_row(old["id"]).payload
