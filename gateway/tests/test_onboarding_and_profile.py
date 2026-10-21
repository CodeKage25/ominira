"""Onboarding, sign in, preferences, accounts and the audit chain."""

from sqlalchemy import update

from ominira.database import SessionFactory
from ominira.tables import AuditRow
from tests.conftest import CUSTOMER, PIN_BAD, PIN_OK, confirm, make_draft


# ---- onboarding ---------------------------------------------------------------------------------

def test_onboarding_happy_path(client):
    r = client.post("/v1/onboarding/sessions")
    assert r.status_code == 201
    sid, digits = r.json()["id"], r.json()["liveness_digits"]
    assert sid.startswith("ob_") and len(digits) == 4 and all(0 <= d <= 9 for d in digits)
    assert client.post(f"/v1/onboarding/{sid}/selfie", json={"spoken_digits": digits}).json() == {"live": True}
    assert client.post(f"/v1/onboarding/{sid}/id-document").json() == {
        "full_name": "Ronke Adeyemi", "nin_last4": "5521", "born_on": "1961-03-14"}
    bvn = client.post(f"/v1/onboarding/{sid}/bvn", json={"bvn": "22212345678"}).json()
    assert bvn == {"match": True, "say": "Your BVN matches your card and your face."}
    done = client.post(f"/v1/onboarding/{sid}/complete")
    assert done.json() == {"customer_id": "cus_ronke", "token": "demo-customer"}


def test_selfie_without_digits_passes(client):
    sid = client.post("/v1/onboarding/sessions").json()["id"]
    assert client.post(f"/v1/onboarding/{sid}/selfie").json() == {"live": True}


def test_wrong_liveness_digits_block_completion(client):
    s = client.post("/v1/onboarding/sessions").json()
    wrong = [(d + 1) % 10 for d in s["liveness_digits"]]
    assert client.post(f"/v1/onboarding/{s['id']}/selfie", json={"spoken_digits": wrong}).json() == {"live": False}
    client.post(f"/v1/onboarding/{s['id']}/bvn", json={"bvn": "22212345678"})
    assert client.post(f"/v1/onboarding/{s['id']}/complete").json()["code"] == "onboarding_incomplete"


def test_someone_elses_bvn(client):
    sid = client.post("/v1/onboarding/sessions").json()["id"]
    r = client.post(f"/v1/onboarding/{sid}/bvn", json={"bvn": "12345678901"}).json()
    assert r["match"] is False and r["say"].startswith("That BVN belongs to someone else")
    c = client.post(f"/v1/onboarding/{sid}/complete")
    assert c.status_code == 409 and c.json()["code"] == "onboarding_incomplete"


def test_bvn_must_be_eleven_digits(client):
    sid = client.post("/v1/onboarding/sessions").json()["id"]
    r = client.post(f"/v1/onboarding/{sid}/bvn", json={"bvn": "123"})
    assert r.status_code == 422 and r.json()["code"] == "invalid_request"


def test_unknown_onboarding_session(client):
    assert client.post("/v1/onboarding/ob_nope/selfie").status_code == 404


def test_bvn_is_never_in_the_audit_log(client):
    sid = client.post("/v1/onboarding/sessions").json()["id"]
    client.post(f"/v1/onboarding/{sid}/bvn", json={"bvn": "22212345678"})
    client.post(f"/v1/onboarding/{sid}/complete")
    log = str(client.get("/v1/audit", headers=CUSTOMER).json())
    assert "account_opened" in log and "22212345678" not in log


# ---- sign in ------------------------------------------------------------------------------------

def test_unlock_with_the_pulse_pin(client):
    assert client.post("/v1/auth/unlock", headers=CUSTOMER, json={"pin_block": PIN_OK}).json() == {
        "ok": True, "say": "Welcome back."}
    r = client.post("/v1/auth/unlock", headers=CUSTOMER, json={"pin_block": PIN_BAD})
    assert r.status_code == 401 and r.json()["code"] == "pin_incorrect" and r.json()["tries_left"] == 2


def test_unlock_tries_and_confirm_tries_are_the_same_counter(client):
    client.post("/v1/auth/unlock", headers=CUSTOMER, json={"pin_block": PIN_BAD})
    client.post("/v1/auth/unlock", headers=CUSTOMER, json={"pin_block": PIN_BAD})
    d = make_draft(client, payee_id="py_tolu", amount_kobo=100_000)
    assert confirm(client, d, pin=PIN_BAD).json()["tries_left"] == 0


def test_a_good_pin_resets_the_count(client):
    client.post("/v1/auth/unlock", headers=CUSTOMER, json={"pin_block": PIN_BAD})
    client.post("/v1/auth/unlock", headers=CUSTOMER, json={"pin_block": PIN_OK})
    r = client.post("/v1/auth/unlock", headers=CUSTOMER, json={"pin_block": PIN_BAD})
    assert r.json()["tries_left"] == 2


# ---- preferences --------------------------------------------------------------------------------

def test_preferences_partial_update(client):
    before = client.get("/v1/me/preferences", headers=CUSTOMER).json()
    assert before == {"lang": "en", "modes": ["voice"], "theme": "system", "speech_rate": 0.95, "pulse_gap_ms": 1000,
                      "daily_limit_kobo": None, "pause_threshold_kobo": None}
    after = client.put("/v1/me/preferences", headers=CUSTOMER, json={"theme": "contrast", "speech_rate": 1.2}).json()
    assert after == before | {"theme": "contrast", "speech_rate": 1.2}


def test_preferences_validation(client):
    for body in ({"lang": "fr"}, {"speech_rate": 3}, {"pulse_gap_ms": 100}, {"modes": ["telepathy"]},
                 {"theme": "neon"}, {"daily_limit_kobo": 0}):
        r = client.put("/v1/me/preferences", headers=CUSTOMER, json=body)
        assert r.status_code == 422, body


def test_limits_can_be_cleared_with_null(client):
    client.put("/v1/me/preferences", headers=CUSTOMER, json={"daily_limit_kobo": 1_000_000})
    cleared = client.put("/v1/me/preferences", headers=CUSTOMER,
                         json={"daily_limit_kobo": None, "pin_block": PIN_OK}).json()
    assert cleared["daily_limit_kobo"] is None


def test_language_changes_every_later_say(client):
    client.put("/v1/me/preferences", headers=CUSTOMER, json={"lang": "pcm"})
    assert client.get("/v1/accounts/ac_1/balance", headers=CUSTOMER).json()["say"].startswith("You get ")
    d = make_draft(client, payee_id="py_tolu", amount_kobo=2_000_000)
    assert d["readback"]["lang"] == "pcm" and d["readback"]["spoken"][-1] == "Pulse your PIN make e go."
    assert confirm(client, d).json()["say"] == "E don go. Tolu don get am."


def test_yoruba_hausa_igbo_fall_back_to_english_for_now(client):
    client.put("/v1/me/preferences", headers=CUSTOMER, json={"lang": "yo"})
    assert client.get("/v1/accounts/ac_1/balance", headers=CUSTOMER).json()["say"].startswith("You have ")


def test_customers_own_pause_threshold(client):
    client.put("/v1/me/preferences", headers=CUSTOMER, json={"pause_threshold_kobo": 1_000_000})
    d = make_draft(client, payee_id="py_musa", amount_kobo=1_000_000)
    assert confirm(client, d, call_active=True).json()["code"] == "pause_required"


# ---- accounts -----------------------------------------------------------------------------------

def test_accounts_balance_transactions(client):
    assert client.get("/v1/accounts", headers=CUSTOMER).json() == [
        {"id": "ac_1", "name": "Current account", "last4": "4567", "balance_kobo": 84_230_000}]
    bal = client.get("/v1/accounts/ac_1/balance", headers=CUSTOMER).json()
    assert bal == {"balance_kobo": 84_230_000,
                   "say": "You have eight hundred and forty-two thousand, three hundred naira in your current account."}
    rows = client.get("/v1/accounts/ac_1/transactions", headers=CUSTOMER).json()
    assert [r["who"] for r in rows] == ["Mama", "Salary, Adewale Stores", "Shoprite, Ikeja"]
    assert all(r["at"].endswith("Z") for r in rows)
    ins = client.get("/v1/accounts/ac_1/transactions?direction=in&limit=1", headers=CUSTOMER).json()
    assert len(ins) == 1 and ins[0]["incoming"] is True
    outs = client.get("/v1/accounts/ac_1/transactions?direction=out", headers=CUSTOMER).json()
    assert [r["who"] for r in outs] == ["Shoprite, Ikeja"]
    assert client.get("/v1/accounts/ac_9/balance", headers=CUSTOMER).status_code == 404


def test_a_transfer_shows_in_transactions(client):
    confirm(client, make_draft(client, payee_id="py_tolu", amount_kobo=2_000_000))
    top = client.get("/v1/accounts/ac_1/transactions?limit=1", headers=CUSTOMER).json()[0]
    assert top["who"] == "Adebayo Tolulope" and top["amount_kobo"] == 2_000_000 and top["incoming"] is False


def test_income_summary(client):
    assert client.get("/v1/accounts/ac_1/income-summary", headers=CUSTOMER).json() == {
        "say": "One hundred and eighty-five thousand naira came in this week, from two people."}


def test_banks_and_cards(client):
    banks = client.get("/v1/banks", headers=CUSTOMER).json()
    assert {"code": "057", "name": "Zenith Bank Plc", "short": "Zenith Bank"} in banks
    assert client.get("/v1/cards", headers=CUSTOMER).json() == [
        {"id": "cd_1", "last4": "8830", "holder": "Ronke Adeyemi", "status": "active"}]


def test_payees(client):
    payees = {p["id"]: p for p in client.get("/v1/payees", headers=CUSTOMER).json()}
    assert payees["py_musa"] == {"id": "py_musa", "nickname": "Musa", "name": "Musa Ibrahim", "bank": "OPay",
                                 "account_last4": "7710", "is_new": True, "flagged": False}
    assert payees["py_tolu"]["is_new"] is False and payees["py_mama"]["bank"] == "GTBank"


def test_approvers_replace_and_remove(client):
    assert client.get("/v1/approvers", headers=CUSTOMER).json() == [
        {"id": "ap_funmi", "name": "Funmi", "threshold_kobo": 5_000_000}]
    new = client.post("/v1/approvers", headers=CUSTOMER, json={"name": "Kehinde", "phone": "+2348012345678",
                                                              "threshold_kobo": 10_000_000, "pin_block": PIN_OK})
    assert new.status_code == 201
    assert [a["name"] for a in client.get("/v1/approvers", headers=CUSTOMER).json()] == ["Kehinde"]
    assert client.delete("/v1/approvers/ap_nope", headers=CUSTOMER | {"X-Pin-Block": PIN_OK}).status_code == 404


# ---- audit chain --------------------------------------------------------------------------------

def test_audit_chain_verifies_and_detects_tampering(client):
    confirm(client, make_draft(client, payee_id="py_tolu", amount_kobo=2_000_000))
    confirm(client, make_draft(client, payee_id="py_mama", amount_kobo=1_000_000))
    ok = client.get("/v1/audit/verify", headers=CUSTOMER).json()
    assert ok["ok"] is True and ok["entries"] == 6
    with SessionFactory() as db:
        db.execute(update(AuditRow).where(AuditRow.seq == 3).values(detail={"amount_kobo": 1}))
        db.commit()
    broken = client.get("/v1/audit/verify", headers=CUSTOMER).json()
    assert broken["ok"] is False and broken["broken_at"] == 3


def test_audit_detects_a_deleted_row(client):
    confirm(client, make_draft(client, payee_id="py_tolu", amount_kobo=2_000_000))
    with SessionFactory() as db:
        db.query(AuditRow).filter(AuditRow.seq == 2).delete()
        db.commit()
    assert client.get("/v1/audit/verify", headers=CUSTOMER).json()["broken_at"] == 3


def test_audit_detects_a_rewritten_hash(client):
    confirm(client, make_draft(client, payee_id="py_tolu", amount_kobo=2_000_000))
    with SessionFactory() as db:
        db.execute(update(AuditRow).where(AuditRow.seq == 1).values(entry_hash="f" * 64))
        db.commit()
    assert client.get("/v1/audit/verify", headers=CUSTOMER).json() == {"ok": False, "broken_at": 1, "entries": 1}


def test_customer_sees_only_their_own_entries(client):
    confirm(client, make_draft(client, payee_id="py_tolu", amount_kobo=2_000_000))
    entries = client.get("/v1/audit", headers=CUSTOMER).json()
    assert entries[0]["action"] == "executed" and entries[0]["detail"]["account_last4"] == "4471"
    assert set(entries[0]) == {"seq", "at", "actor", "action", "draft_id", "detail", "entry_hash"}


# ---- the language picked first -----------------------------------------------------------------

def test_onboarding_carries_the_picked_language(client):
    s = client.post("/v1/onboarding/sessions", json={"lang": "pcm"}).json()
    bvn = client.post(f"/v1/onboarding/{s['id']}/bvn", json={"bvn": "12345678901"}).json()
    assert bvn["say"] == "That BVN na another person own. Talk your eleven numbers again."
    client.post(f"/v1/onboarding/{s['id']}/bvn", json={"bvn": "22212345678"})
    client.post(f"/v1/onboarding/{s['id']}/complete")
    assert client.get("/v1/me/preferences", headers=CUSTOMER).json()["lang"] == "pcm"
    assert client.post("/v1/auth/unlock", headers=CUSTOMER, json={"pin_block": PIN_BAD}).json()["say"].startswith(
        "That PIN no match.")


def test_onboarding_without_a_language_leaves_the_preference_alone(client):
    client.put("/v1/me/preferences", headers=CUSTOMER, json={"lang": "pcm"})
    s = client.post("/v1/onboarding/sessions").json()
    client.post(f"/v1/onboarding/{s['id']}/bvn", json={"bvn": "22212345678"})
    client.post(f"/v1/onboarding/{s['id']}/complete")
    assert client.get("/v1/me/preferences", headers=CUSTOMER).json()["lang"] == "pcm"


def test_onboarding_rejects_an_unknown_language(client):
    assert client.post("/v1/onboarding/sessions", json={"lang": "fr"}).status_code == 422
