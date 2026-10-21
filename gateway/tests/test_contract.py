"""The contract test: every endpoint in docs/api.md is called, and its answer has the keys the
contract promises. A new endpoint in the doc fails `test_every_documented_endpoint_is_walked`
until it is added here."""

import re
from pathlib import Path

import httpx
import pytest

from ominira.settings import settings
from ominira.speech import yarngpt
from tests.conftest import APPROVER, CUSTOMER, PIN_OK
from tests.test_speech import WAV, FakeYarn

API_MD = Path(__file__).resolve().parents[2] / "docs" / "api.md"

DRAFT = {"id", "kind", "status", "source", "readback", "readback_hash", "expires_at", "reference", "say"}
READBACK = {"kind", "name", "bank", "account_last4", "amount_kobo", "amount_text", "lang", "spoken"}
PROBLEM = {"type", "title", "status", "code"}
PREFS = {"lang", "modes", "theme", "speech_rate", "pulse_gap_ms", "daily_limit_kobo", "pause_threshold_kobo"}
TURN = {"heard", "reply", "say", "engine", "draft"}

# Every call the walk makes, as "METHOD /path/{}" with path parameters blanked.
WALKED: set[str] = set()


def _norm(method: str, path: str) -> str:
    path = path.split("?")[0]
    path = re.sub(r"\{[^}]*\}", "{}", path)
    return f"{method} {path}"


def _call(client, method: str, template: str, *args, expect: int, headers=None, **kwargs) -> httpx.Response:
    WALKED.add(_norm(method, template))
    url = template.format(*args) if args else template
    r = client.request(method, url, headers=headers or {}, **kwargs)
    assert r.status_code == expect, f"{method} {url}: {r.status_code} {r.text}"
    return r


def _keys(obj: dict, expected: set[str]) -> None:
    missing = expected - set(obj)
    assert not missing, f"missing {sorted(missing)} in {obj}"


def _documented() -> set[str]:
    text = API_MD.read_text(encoding="utf-8")
    found = re.findall(r"`(GET|POST|PUT|DELETE) (/[^`\s]+)", text)
    return {_norm(m, p) for m, p in found}


@pytest.fixture
def yarn(monkeypatch):
    fake = FakeYarn()
    settings.yarngpt_api_key = "test-key"
    monkeypatch.setattr(yarngpt, "transport", httpx.MockTransport(fake))
    monkeypatch.setattr(yarngpt, "_voice_cache", None)
    monkeypatch.setattr(yarngpt.time, "sleep", lambda s: None)
    return fake


def test_walk_every_endpoint(client, yarn):
    c = client
    C, A = CUSTOMER, APPROVER

    # Health and pages
    _keys(_call(c, "GET", "/health", expect=200).json(), {"ok", "zenith", "assistant", "speech"})
    page = _call(c, "GET", "/approve?token=demo-approver", expect=200)
    assert page.headers["content-type"].startswith("text/html")

    # Onboarding
    s = _call(c, "POST", "/v1/onboarding/sessions", expect=201).json()
    _keys(s, {"id", "liveness_digits"})
    _keys(_call(c, "POST", "/v1/onboarding/{}/selfie", s["id"], expect=200, json={"spoken_digits": s["liveness_digits"]}).json(), {"live"})
    _keys(_call(c, "POST", "/v1/onboarding/{}/id-document", s["id"], expect=200).json(), {"full_name", "nin_last4", "born_on"})
    _keys(_call(c, "POST", "/v1/onboarding/{}/bvn", s["id"], expect=200, json={"bvn": "22212345678"}).json(), {"match", "say"})
    _keys(_call(c, "POST", "/v1/onboarding/{}/complete", s["id"], expect=200).json(), {"customer_id", "token"})

    # Sign in
    _keys(_call(c, "POST", "/v1/auth/unlock", expect=200, headers=C, json={"pin_block": PIN_OK}).json(), {"ok", "say"})

    # Money
    accts = _call(c, "GET", "/v1/accounts", expect=200, headers=C).json()
    _keys(accts[0], {"id", "name", "last4", "balance_kobo"})
    aid = accts[0]["id"]
    _keys(_call(c, "GET", "/v1/accounts/{}/balance", aid, expect=200, headers=C).json(), {"balance_kobo", "say"})
    tx = _call(c, "GET", "/v1/accounts/{}/transactions?limit=20&direction=in", aid, expect=200, headers=C).json()
    _keys(tx[0], {"id", "who", "amount_kobo", "incoming", "at", "reference"})
    _keys(_call(c, "GET", "/v1/accounts/{}/income-summary", aid, expect=200, headers=C).json(), {"say"})
    _keys(_call(c, "GET", "/v1/banks", expect=200, headers=C).json()[0], {"code", "name", "short"})
    _keys(_call(c, "GET", "/v1/payees", expect=200, headers=C).json()[0],
          {"id", "nickname", "name", "bank", "account_last4", "is_new", "flagged"})
    _keys(_call(c, "POST", "/v1/payees/lookup", expect=200, headers=C,
                json={"bank_code": "058", "account_number": "0123450213"}).json(),
          {"name", "bank", "account_last4", "say"})
    _keys(_call(c, "GET", "/v1/cards", expect=200, headers=C).json()[0], {"id", "last4", "holder", "status"})

    # Drafts and the gate
    d = _call(c, "POST", "/v1/drafts", expect=201, headers=C,
              json={"kind": "transfer", "amount_kobo": 2_000_000, "payee_id": "py_tolu"}).json()
    _keys(d, DRAFT)
    _keys(d["readback"], READBACK)
    _keys(_call(c, "GET", "/v1/drafts/{}", d["id"], expect=200, headers=C).json(), DRAFT)
    done = _call(c, "POST", "/v1/drafts/{}/confirm", d["id"], expect=200, headers=C,
                 json={"readback_hash": d["readback_hash"], "pin_block": PIN_OK, "call_active": False}).json()
    assert set(done) == {"status", "reference", "say"} and done["status"] == "executed"

    by_account = _call(c, "POST", "/v1/drafts", expect=201, headers=C,
                       json={"kind": "transfer", "amount_kobo": 100_000, "bank_code": "044",
                             "account_number": "1234567890", "source": "voice"}).json()
    assert by_account["source"] == "voice"
    cancelled = _call(c, "POST", "/v1/drafts/{}/cancel", by_account["id"], expect=200, headers=C).json()
    assert set(cancelled) == {"status", "say"} and cancelled["status"] == "cancelled"

    card = _call(c, "POST", "/v1/drafts", expect=201, headers=C, json={"kind": "card_block", "card_id": "cd_1"}).json()
    assert card["readback"]["amount_kobo"] is None and card["readback"]["name"] == "Card ending 8830"

    musa = _call(c, "POST", "/v1/drafts", expect=201, headers=C,
                 json={"kind": "transfer", "amount_kobo": 15_000_000, "payee_id": "py_musa"}).json()
    paused = _call(c, "POST", "/v1/drafts/{}/confirm", musa["id"], expect=409, headers=C,
                   json={"readback_hash": musa["readback_hash"], "pin_block": PIN_OK, "call_active": True}).json()
    _keys(paused, PROBLEM | {"say", "draft_id"})
    answered = _call(c, "POST", "/v1/drafts/{}/pause-answer", musa["id"], expect=200, headers=C,
                     json={"answer": "chose_myself"}).json()
    assert set(answered) == {"status", "say"}
    waiting = _call(c, "POST", "/v1/drafts/{}/confirm", musa["id"], expect=202, headers=C,
                    json={"readback_hash": musa["readback_hash"], "pin_block": PIN_OK, "call_active": True}).json()
    assert set(waiting) == {"status", "code", "approver", "say"} and waiting["code"] == "approval_required"

    # Co-approvers
    approvers = _call(c, "GET", "/v1/approvers", expect=200, headers=C).json()
    _keys(approvers[0], {"id", "name", "threshold_kobo"})
    queue = _call(c, "GET", "/v1/approvals", expect=200, headers=A).json()
    _keys(queue[0], {"id", "status", "customer", "amount_kobo", "amount_text", "to", "bank", "account_last4",
                     "asked_at", "draft_status"})
    decided = _call(c, "POST", "/v1/approvals/{}/decision", queue[0]["id"], expect=200, headers=A,
                    json={"decision": "approve"}).json()
    _keys(decided, {"approval", "status", "say", "reference"})
    added = _call(c, "POST", "/v1/approvers", expect=201, headers=C,
                  json={"name": "Kehinde", "phone": "+2348012345678", "threshold_kobo": 10_000_000}).json()
    _keys(added, {"id", "name", "threshold_kobo"})
    assert _call(c, "DELETE", "/v1/approvers/{}", added["id"], expect=200, headers=C).json() == {"removed": added["id"]}

    # Preferences
    _keys(_call(c, "GET", "/v1/me/preferences", expect=200, headers=C).json(), PREFS)
    _keys(_call(c, "PUT", "/v1/me/preferences", expect=200, headers=C, json={"theme": "dark"}).json(), PREFS)

    # Assistant
    conv = _call(c, "POST", "/v1/conversations", expect=201, headers=C).json()
    assert set(conv) == {"id"}
    turn = _call(c, "POST", "/v1/conversations/{}/turns", conv["id"], expect=200, headers=C,
                 json={"text": "send twenty thousand to Tolu"}).json()
    assert set(turn) == TURN and turn["engine"] in ("claude", "rules")
    _keys(turn["draft"], DRAFT)
    voice = _call(c, "POST", "/v1/conversations/{}/voice-turns", conv["id"], expect=200,
                  headers=C | {"Content-Type": "audio/wav"}, content=WAV).json()
    assert set(voice) == TURN | {"reply_audio_url", "timings"}

    # Speech
    assert set(_call(c, "GET", "/v1/speech/status", expect=200, headers=C).json()) == {"enabled", "provider"}
    _keys(_call(c, "POST", "/v1/speech/say", expect=200, headers=C, json={"text": "Sent. Tolu has it."}).json(),
          {"audio_url", "expires_in", "lang", "voice"})
    yarn.polls = 0
    _keys(_call(c, "POST", "/v1/speech/listen", expect=200, headers=C | {"Content-Type": "audio/wav"},
                content=WAV).json(), {"text", "seconds", "say"})

    # Audit
    entries = _call(c, "GET", "/v1/audit", expect=200, headers=C).json()
    assert entries and all(e["action"] for e in entries)
    verify = _call(c, "GET", "/v1/audit/verify", expect=200, headers=C).json()
    assert verify["ok"] is True and isinstance(verify["entries"], int)

    # Mock admin
    _keys(_call(c, "GET", "/_mock/state", expect=200).json(), {"balances", "cards", "controls"})
    _keys(_call(c, "POST", "/_mock/controls", expect=200,
                json={"profile": "fast", "banks_down": ["999992"], "next_transfer_code": "09"}).json(), {"controls"})
    _keys(_call(c, "POST", "/_mock/reset", expect=200).json(), {"balances", "cards", "controls"})


def test_every_documented_endpoint_is_walked(client, yarn):
    if not API_MD.exists():
        pytest.skip("docs/api.md is not next to this checkout")
    if not WALKED:
        test_walk_every_endpoint(client, yarn)
    documented = _documented()
    assert documented, "no endpoints found in docs/api.md"
    missing = documented - WALKED
    assert not missing, f"documented but not walked: {sorted(missing)}"


def test_problem_shape(client):
    r = client.get("/v1/drafts/dr_nope", headers=CUSTOMER)
    assert r.headers["content-type"].startswith("application/problem+json")
    body = r.json()
    _keys(body, PROBLEM)
    assert body["type"] == "https://docs.ominira.app/errors/not_found" and body["status"] == 404


def test_times_are_utc_with_z(client):
    d = client.post("/v1/drafts", headers=CUSTOMER, json={"kind": "transfer", "amount_kobo": 100, "payee_id": "py_tolu"}).json()
    assert re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ", d["expires_at"])
