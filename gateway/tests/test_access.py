"""Who may call what, Idempotency-Key, problem details, and the mock admin guard."""

import pytest

from ominira.settings import settings
from tests.conftest import APPROVER, ASSISTANT, CUSTOMER, PIN_OK, balance, confirm, make_draft, moves

START = 84_230_000


# ---- roles --------------------------------------------------------------------------------------

def test_no_token_and_unknown_token_are_401(client):
    for headers in ({}, {"Authorization": "Bearer nope"}, {"Authorization": "Basic abc"}):
        r = client.get("/v1/accounts", headers=headers)
        assert r.status_code == 401 and r.json()["code"] == "unauthorized"
        assert r.headers["content-type"].startswith("application/problem+json")


def test_assistant_can_read_and_draft(client):
    assert client.get("/v1/accounts", headers=ASSISTANT).status_code == 200
    assert client.get("/v1/payees", headers=ASSISTANT).status_code == 200
    assert client.get("/v1/cards", headers=ASSISTANT).status_code == 200
    d = make_draft(client, headers=ASSISTANT, payee_id="py_tolu", amount_kobo=2_000_000, source="app")
    assert d["source"] == "assistant"  # the assistant cannot claim to be the app


@pytest.mark.parametrize("call", ["confirm", "cancel", "pause", "decide", "unlock", "get_draft", "prefs", "approvers"])
def test_assistant_is_refused_everything_that_moves_money_or_decides(client, call):
    d = make_draft(client, payee_id="py_musa", amount_kobo=15_000_000)
    paths = {
        "confirm": ("post", f"/v1/drafts/{d['id']}/confirm", {"readback_hash": d["readback_hash"], "pin_block": PIN_OK}),
        "cancel": ("post", f"/v1/drafts/{d['id']}/cancel", None),
        "pause": ("post", f"/v1/drafts/{d['id']}/pause-answer", {"answer": "chose_myself"}),
        "decide": ("post", "/v1/approvals/apv_x/decision", {"decision": "approve"}),
        "unlock": ("post", "/v1/auth/unlock", {"pin_block": PIN_OK}),
        "get_draft": ("get", f"/v1/drafts/{d['id']}", None),
        "prefs": ("put", "/v1/me/preferences", {"lang": "pcm"}),
        "approvers": ("post", "/v1/approvers", {"name": "Eve", "phone": "+2348000000000", "threshold_kobo": 1}),
    }
    method, path, body = paths[call]
    r = getattr(client, method)(path, headers=ASSISTANT, **({"json": body} if body is not None else {}))
    assert r.status_code == 403 and r.json()["code"] == "forbidden"
    assert balance(client) == START and moves() == []


def test_assistant_cannot_see_approvals(client):
    assert client.get("/v1/approvals", headers=ASSISTANT).status_code == 403


def test_approver_cannot_act_as_the_customer(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=2_000_000)
    assert confirm(client, d, headers=APPROVER).status_code == 403
    assert client.get("/v1/accounts", headers=APPROVER).status_code == 403  # never the balance
    assert client.post("/v1/drafts", headers=APPROVER, json={"kind": "card_block", "card_id": "cd_1"}).status_code == 403


def test_customer_cannot_decide_approvals(client):
    assert client.get("/v1/approvals", headers=CUSTOMER).status_code == 403
    r = client.post("/v1/approvals/apv_x/decision", headers=CUSTOMER, json={"decision": "approve"})
    assert r.status_code == 403


def test_approver_cannot_decide_someone_elses_approval(client):
    r = client.post("/v1/approvals/apv_unknown/decision", headers=APPROVER, json={"decision": "approve"})
    assert r.status_code == 404


# ---- problem details ----------------------------------------------------------------------------

def test_validation_errors_are_problems(client):
    r = client.post("/v1/drafts", headers=CUSTOMER, json={"kind": "teleport"})
    assert r.status_code == 422 and r.json()["code"] == "invalid_request" and r.json()["errors"]
    assert r.json()["type"].endswith("/invalid_request") and r.json()["status"] == 422


def test_bad_amounts_and_accounts(client):
    def post(body):
        return client.post("/v1/drafts", headers=CUSTOMER, json={"kind": "transfer", **body}).json()["code"]
    assert post({"payee_id": "py_tolu", "amount_kobo": 0}) == "invalid_amount"
    assert post({"payee_id": "py_tolu"}) == "invalid_amount"
    assert post({"payee_id": "py_ghost", "amount_kobo": 100}) == "payee_not_found"
    assert post({"bank_code": "058", "account_number": "1111111111", "amount_kobo": 100}) == "invalid_account"
    assert post({"bank_code": "058", "account_number": "123", "amount_kobo": 100}) == "invalid_account"
    assert post({"bank_code": "058", "account_number": "1234500006", "amount_kobo": 100}) == "account_dormant"
    assert post({"bank_code": "033", "account_number": "2109876543", "amount_kobo": 100}) == "account_dormant"
    assert post({"bank_code": "999", "account_number": "1234567890", "amount_kobo": 100}) == "unknown_bank"
    assert post({"amount_kobo": 100}) == "invalid_request"
    r = client.post("/v1/drafts", headers=CUSTOMER, json={"kind": "card_block", "card_id": "cd_9"})
    assert r.status_code == 404 and r.json()["code"] == "card_not_found"


def test_lookup_is_rate_limited_to_five_a_minute(client):
    body = {"bank_code": "058", "account_number": "0123450213"}
    for _ in range(5):
        r = client.post("/v1/payees/lookup", headers=CUSTOMER, json=body)
        assert r.status_code == 200
    assert r.json() == {"name": "Adeyemi Folake", "bank": "GTBank", "account_last4": "0213",
                        "say": "Adeyemi Folake, GTBank, ending zero, two, one, three."}
    r = client.post("/v1/payees/lookup", headers=CUSTOMER, json=body)
    assert r.status_code == 429 and r.json()["code"] == "rate_limited"
    assert int(r.headers["Retry-After"]) == r.json()["retry_after"] > 0


def test_generated_names_are_consistent(client):
    body = {"bank_code": "044", "account_number": "1234567890"}
    a = client.post("/v1/payees/lookup", headers=CUSTOMER, json=body).json()["name"]
    b = client.post("/v1/payees/lookup", headers=CUSTOMER, json=body).json()["name"]
    c = client.post("/v1/payees/lookup", headers=CUSTOMER, json={"bank_code": "044", "account_number": "1234567891"}).json()
    assert a == b and len(a.split()) == 2 and c["name"]


# ---- idempotency --------------------------------------------------------------------------------

def test_same_key_same_body_replays(client):
    h = CUSTOMER | {"Idempotency-Key": "k-1"}
    body = {"kind": "transfer", "payee_id": "py_tolu", "amount_kobo": 2_000_000}
    first = client.post("/v1/drafts", headers=h, json=body)
    second = client.post("/v1/drafts", headers=h, json=body)
    assert first.status_code == second.status_code == 201
    assert first.json()["id"] == second.json()["id"]
    assert second.headers["idempotent-replayed"] == "true"


def test_same_key_different_body_is_refused(client):
    h = CUSTOMER | {"Idempotency-Key": "k-2"}
    client.post("/v1/drafts", headers=h, json={"kind": "transfer", "payee_id": "py_tolu", "amount_kobo": 2_000_000})
    r = client.post("/v1/drafts", headers=h, json={"kind": "transfer", "payee_id": "py_tolu", "amount_kobo": 9_000_000})
    assert r.status_code == 422 and r.json()["code"] == "idempotency_key_reused"


def test_keys_are_scoped_to_the_token(client):
    body = {"kind": "transfer", "payee_id": "py_tolu", "amount_kobo": 2_000_000}
    a = client.post("/v1/drafts", headers=CUSTOMER | {"Idempotency-Key": "shared"}, json=body).json()["id"]
    b = client.post("/v1/drafts", headers=ASSISTANT | {"Idempotency-Key": "shared"}, json=body).json()["id"]
    assert a != b


def test_server_errors_are_not_kept(client):
    client.post("/_mock/controls", json={"next_transfer_code": "91"})
    d = make_draft(client, payee_id="py_tolu", amount_kobo=2_000_000)
    first = confirm(client, d, key="retry-me")
    assert first.status_code == 503
    second = confirm(client, d, key="retry-me")
    assert second.status_code == 200 and "idempotent-replayed" not in second.headers


def test_problems_are_replayed_too(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=2_000_000)
    first = confirm(client, d, pin="demo-0000", key="wrong-pin")
    second = confirm(client, d, pin="demo-0000", key="wrong-pin")
    assert first.json()["tries_left"] == second.json()["tries_left"] == 2  # counted once
    assert second.headers["idempotent-replayed"] == "true"


def test_gets_ignore_the_key(client):
    r = client.get("/v1/accounts", headers=CUSTOMER | {"Idempotency-Key": "x"})
    assert r.status_code == 200 and "idempotent-replayed" not in r.headers


# ---- mock admin ---------------------------------------------------------------------------------

def test_mock_admin_open_without_a_token(client):
    state = client.get("/_mock/state").json()
    assert set(state) >= {"controls", "balances", "cards", "pin_tries_left"}
    assert state["balances"][0]["balance_kobo"] == START


def test_mock_admin_needs_the_header_when_a_token_is_set(client):
    settings.mock_admin_token = "s3cret"
    assert client.get("/_mock/state").status_code == 403
    assert client.post("/_mock/reset", headers={"X-Mock-Admin": "wrong"}).status_code == 403
    assert client.get("/_mock/state", headers={"X-Mock-Admin": "s3cret"}).status_code == 200


def test_mock_admin_off_is_404(client):
    settings.mock_admin = False
    assert client.get("/_mock/state").status_code == 404
    assert client.post("/_mock/reset").status_code == 404


def test_reset_restores_the_demo_world(client):
    confirm(client, make_draft(client, payee_id="py_tolu", amount_kobo=2_000_000))
    client.post("/_mock/controls", json={"banks_down": ["999992"]})
    state = client.post("/_mock/reset").json()
    assert state["balances"][0]["balance_kobo"] == START
    assert state["controls"]["banks_down"] == [] and state["controls"]["profile"] == "fast"
    assert client.get("/v1/audit/verify", headers=CUSTOMER).json() == {"ok": True, "entries": 0}


def test_controls_validate_and_clear(client):
    assert client.post("/_mock/controls", json={"next_transfer_code": "9"}).status_code == 422
    assert client.post("/_mock/controls", json={"profile": "chaos"}).status_code == 422
    state = client.post("/_mock/controls", json={"profile": "flaky"}).json()["controls"]
    assert state["profile"] == "flaky" and state["banks_down"] == ["50211"] and state["failure_rate"] > 0
    state = client.post("/_mock/controls", json={"next_transfer_code": "09", "banks_down": None}).json()["controls"]
    assert state["next_transfer_code"] == "09" and state["banks_down"] == []
    state = client.post("/_mock/controls", json={"next_transfer_code": None}).json()["controls"]
    assert state["next_transfer_code"] is None
