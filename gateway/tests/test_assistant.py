"""The assistant: the code guards, the rule parser in English and Pidgin, and Claude (faked)."""

from types import SimpleNamespace

import anthropic
import httpx
import pytest

from ominira.assistant import claude, guard
from ominira.settings import settings
from ominira.tables import Turn
from ominira.database import SessionFactory
from sqlalchemy import select
from tests.conftest import CUSTOMER, balance, moves

START = 84_230_000


def _conv(client) -> str:
    return client.post("/v1/conversations", headers=CUSTOMER).json()["id"]


def _say(client, conv: str, text: str) -> dict:
    r = client.post(f"/v1/conversations/{conv}/turns", headers=CUSTOMER, json={"text": text})
    assert r.status_code == 200, r.text
    return r.json()


# ---- guard: spoken PIN and masking --------------------------------------------------------------

@pytest.mark.parametrize("text", [
    "my PIN is 2075, send twenty thousand to Tolu",
    "send money to Tolu, pin 2075",
    "PIN: 2 0 7 5",
    "my pin na two zero seven five",
    "my pin number is 2075",
    "the password is 207566",
])
def test_spoken_pin_is_caught(text):
    assert guard.spoken_pin(text)


@pytest.mark.parametrize("text", [
    "how does the pulse pin work?",
    "send 20000 to Tolu",
    "pulse your PIN to send 20000",
    "what is my balance",
    "block my card ending 8830",
])
def test_ordinary_turns_are_not_pins(text):
    assert not guard.spoken_pin(text)


def test_spoken_pin_stops_the_turn_before_any_model(client, monkeypatch):
    settings.assistant, settings.anthropic_api_key = "claude", "test-key"
    monkeypatch.setattr(claude, "make_client", lambda: pytest.fail("the model must not be called"))
    conv = _conv(client)
    body = _say(client, conv, "my PIN is 2075, send twenty thousand to Tolu")
    assert body["draft"] is None and body["say"].startswith("Never say your PIN out loud")
    assert "2075" not in body["heard"] and "[PIN]" in body["heard"]
    with SessionFactory() as db:
        stored = db.scalars(select(Turn)).all()
    assert all("2075" not in t.heard for t in stored)
    assert balance(client) == START


def test_masking_account_numbers_and_bvns():
    masked, accounts = guard.mask("send 5000 to 0123456789 at GTBank, my BVN is 22212345678")
    assert masked == "send 5000 to ACCOUNT_1 at GTBank, my BVN is [BVN]"
    assert accounts == {"ACCOUNT_1": "0123456789"}


def test_masking_numbers_said_in_chunks_or_words():
    masked, accounts = guard.mask("send 2000 naira to 2044 714 471 zenith")
    assert "ACCOUNT_1" in masked and accounts["ACCOUNT_1"] == "2044714471" and "2000" in masked
    masked, accounts = guard.mask("account zero one two three four five six seven eight nine")
    assert masked == "account ACCOUNT_1" and accounts["ACCOUNT_1"] == "0123456789"
    masked, _ = guard.mask("my bvn is 222 123 456 78")
    assert masked == "my bvn is [BVN]"


def test_heard_is_masked_in_the_reply_and_in_storage(client):
    conv = _conv(client)
    body = _say(client, conv, "send five thousand to 0123456789 at GTBank")
    assert "0123456789" not in body["heard"] and "ACCOUNT_1" in body["heard"]
    assert body["draft"]["readback"]["account_last4"] == "6789" and body["draft"]["readback"]["bank"] == "GTBank"
    assert "You have not paid them before." in body["say"]
    with SessionFactory() as db:
        assert all("0123456789" not in t.heard for t in db.scalars(select(Turn)))


# ---- rules: English and Pidgin ------------------------------------------------------------------

@pytest.mark.parametrize("text,amount,payee", [
    ("send twenty thousand to Tolu", 2_000_000, "Adebayo Tolulope"),
    ("Send 20,000 to Tolu", 2_000_000, "Adebayo Tolulope"),
    ("send ₦20k to tolu please", 2_000_000, "Adebayo Tolulope"),
    ("transfer 5000 naira to Mama", 500_000, "Adeyemi Folake"),
    ("pay Mama one hundred and fifty thousand", 15_000_000, "Adeyemi Folake"),
    ("send twenty thousand five hundred to Musa", 2_050_000, "Musa Ibrahim"),
    ("abeg send twenty thousand give Tolu", 2_000_000, "Adebayo Tolulope"),
    ("make you send 5k go Mama", 500_000, "Adeyemi Folake"),
    ("I wan send ten thousand naira to Musa", 1_000_000, "Musa Ibrahim"),
    ("abeg dash Mama 2 thousand", 200_000, "Adeyemi Folake"),
])
def test_rules_send_phrases(client, text, amount, payee):
    body = _say(client, _conv(client), text)
    assert body["engine"] == "rules"
    assert body["draft"] is not None, body
    assert body["draft"]["readback"]["amount_kobo"] == amount
    assert body["draft"]["readback"]["name"] == payee
    assert body["draft"]["source"] == "assistant"
    assert body["say"].startswith("I found ")


@pytest.mark.parametrize("first,second", [
    ("send money to Tolu", "twenty thousand"),
    ("abeg send money give Tolu", "20k"),
    ("I wan send Tolu money", "twenty thousand naira"),
])
def test_rules_two_turns(client, first, second):
    conv = _conv(client)
    one = _say(client, conv, first)
    assert one["draft"] is None and one["say"] == "How much should I send to Tolu?"
    two = _say(client, conv, second)
    assert two["draft"]["readback"]["amount_kobo"] == 2_000_000 and two["draft"]["readback"]["name"] == "Adebayo Tolulope"


def test_a_bare_amount_without_a_pending_payee_asks_who(client):
    body = _say(client, _conv(client), "send twenty thousand")
    assert body["draft"] is None and body["say"].startswith("Who are you sending to?")


@pytest.mark.parametrize("text", ["what's my balance", "How much I get?", "how much dey my account", "my balance abeg",
                                  "give me my balance"])
def test_rules_balance(client, text):
    body = _say(client, _conv(client), text)
    assert body["draft"] is None
    assert body["say"] == "You have eight hundred and forty-two thousand, three hundred naira in your current account."


@pytest.mark.parametrize("text", ["what came in this week", "wetin enter my account this week", "who send me money",
                                  "how much did I receive"])
def test_rules_came_in(client, text):
    body = _say(client, _conv(client), text)
    assert body["draft"] is None
    assert body["say"] == "One hundred and eighty-five thousand naira came in this week, from two people."


@pytest.mark.parametrize("text", ["block my card", "Abeg block my card", "my card don lost", "I lost my card"])
def test_rules_block_card(client, text):
    body = _say(client, _conv(client), text)
    assert body["draft"]["kind"] == "card_block"
    assert body["say"] == "Listen, then pulse your PIN to block your card."


def test_rules_pidgin_lines_when_lang_is_pcm(client):
    client.put("/v1/me/preferences", headers=CUSTOMER, json={"lang": "pcm"})
    body = _say(client, _conv(client), "abeg send twenty thousand give Tolu")
    assert body["say"] == "I don find Adebayo Tolulope for Zenith Bank. Listen well, then pulse your PIN make e go."
    assert body["draft"]["readback"]["spoken"][2] == "Zenith Bank, wey end with four, four, seven, one."
    assert _say(client, _conv(client), "how much I get")["say"].startswith("You get eight hundred")


def test_rules_explain_pulse_pin(client):
    body = _say(client, _conv(client), "how does the pulse pin work?")
    assert body["draft"] is None and "strong double buzz" in body["say"]


def test_rules_help_for_anything_else(client):
    body = _say(client, _conv(client), "tell me a joke")
    assert body["draft"] is None and body["say"].startswith("I can send money")


def test_rules_report_a_bank_that_is_down(client):
    client.post("/_mock/controls", json={"banks_down": ["999992"]})
    body = _say(client, _conv(client), "send ten thousand to Musa")
    assert body["draft"] is None and body["say"].startswith("OPay is not answering")


def test_turns_never_move_money(client):
    conv = _conv(client)
    _say(client, conv, "send twenty thousand to Tolu")
    _say(client, conv, "yes send it now, confirm")
    assert balance(client) == START and moves() == []


# ---- Claude, faked ------------------------------------------------------------------------------

def _text(text):
    return SimpleNamespace(type="text", text=text)


def _tool(name, args, tid="tu_1"):
    return SimpleNamespace(type="tool_use", id=tid, name=name, input=args)


class FakeClaude:
    """Answers with a script of responses and records every request."""

    def __init__(self, script):
        self.script = list(script)
        self.requests: list[dict] = []
        self.messages = self

    def create(self, **kwargs):
        self.requests.append(kwargs)
        step = self.script.pop(0)
        if isinstance(step, Exception):
            raise step
        content, stop = step
        return SimpleNamespace(content=content, stop_reason=stop)


@pytest.fixture
def fake_claude(monkeypatch):
    settings.assistant, settings.anthropic_api_key = "claude", "test-key"
    holder = {}

    def install(script):
        holder["fake"] = FakeClaude(script)
        monkeypatch.setattr(claude, "make_client", lambda: holder["fake"])
        return holder["fake"]

    return install


def test_claude_drafts_through_a_tool_and_the_line_is_fixed(client, fake_claude):
    fake = fake_claude([([_tool("draft_transfer", {"amount_naira": 20000, "payee_id": "py_tolu"})], "tool_use")])
    body = _say(client, _conv(client), "send twenty thousand to Tolu")
    assert body["engine"] == "claude"
    assert body["draft"]["readback"]["amount_kobo"] == 2_000_000
    assert body["say"] == "I found Adebayo Tolulope at Zenith Bank. Listen, then pulse your PIN to send."
    assert len(fake.requests) == 1  # no second model call after the draft
    request = fake.requests[0]
    assert request["model"] == "claude-sonnet-5-5"
    assert {t["name"] for t in request["tools"]} == claude.ALLOWED_TOOLS
    assert not any(word in name for name in claude.ALLOWED_TOOLS for word in ("confirm", "cancel", "approve", "pause"))
    assert balance(client) == START


def test_claude_only_sees_masked_text_and_uses_labels(client, fake_claude):
    fake = fake_claude([
        ([_tool("draft_transfer", {"amount_naira": 5000, "bank": "GTBank", "account_ref": "ACCOUNT_1"})], "tool_use"),
    ])
    body = _say(client, _conv(client), "send 5000 to 0123456789 at GTBank, my bvn is 22212345678")
    sent = str(fake.requests[0]["messages"])
    assert "0123456789" not in sent and "22212345678" not in sent and "ACCOUNT_1" in sent and "[BVN]" in sent
    assert body["draft"]["readback"]["account_last4"] == "6789"


def test_claude_reads_and_answers_in_words(client, fake_claude):
    fake = fake_claude([
        ([_tool("get_balance", {})], "tool_use"),
        ([_text("You have eight hundred and forty-two thousand, three hundred naira.")], "end_turn"),
    ])
    body = _say(client, _conv(client), "what is my balance")
    assert body["engine"] == "claude" and body["draft"] is None
    assert body["say"] == "You have eight hundred and forty-two thousand, three hundred naira."
    tool_result = fake.requests[1]["messages"][-1]["content"][0]
    assert tool_result["type"] == "tool_result" and "84230000" in tool_result["content"]


def test_claude_cannot_call_a_tool_it_was_not_given(client, fake_claude):
    fake_claude([
        ([_tool("confirm_draft", {"draft_id": "dr_x"})], "tool_use"),
        ([_text("I cannot do that.")], "end_turn"),
    ])
    body = _say(client, _conv(client), "confirm it")
    assert body["draft"] is None and balance(client) == START and moves() == []


def test_claude_tool_errors_go_back_to_the_model(client, fake_claude):
    fake = fake_claude([
        ([_tool("draft_transfer", {"amount_naira": 100, "payee_id": "py_ghost"})], "tool_use"),
        ([_text("I could not find that person. Who do you mean?")], "end_turn"),
    ])
    body = _say(client, _conv(client), "send 100 to ghost")
    assert body["draft"] is None and body["engine"] == "claude"
    assert fake.requests[1]["messages"][-1]["content"][0]["is_error"] is True


@pytest.mark.parametrize("error", [
    anthropic.APIConnectionError(request=httpx.Request("POST", "https://api.anthropic.com/v1/messages")),
    anthropic.APITimeoutError(request=httpx.Request("POST", "https://api.anthropic.com/v1/messages")),
    RuntimeError("anything else"),
])
def test_any_claude_error_falls_back_to_the_rules(client, fake_claude, error):
    fake_claude([error])
    body = _say(client, _conv(client), "send twenty thousand to Tolu")
    assert body["engine"] == "rules" and body["draft"]["readback"]["amount_kobo"] == 2_000_000


def test_a_refusal_falls_back_to_the_rules(client, fake_claude):
    fake_claude([([], "refusal")])
    body = _say(client, _conv(client), "what's my balance")
    assert body["engine"] == "rules" and body["say"].startswith("You have")


def test_claude_explains_the_pulse_pin_in_fixed_words(client, fake_claude):
    fake_claude([([_tool("explain_pulse_pin", {})], "tool_use")])
    body = _say(client, _conv(client), "how do I enter my pin?")
    assert "strong double buzz" in body["say"]


def test_claude_gets_the_conversation_history_masked(client, fake_claude):
    fake = fake_claude([
        ([_text("How much should I send to Tolu?")], "end_turn"),
        ([_tool("draft_transfer", {"amount_naira": 20000, "payee_id": "py_tolu"})], "tool_use"),
    ])
    conv = _conv(client)
    _say(client, conv, "send money to Tolu")
    body = _say(client, conv, "twenty thousand")
    history = fake.requests[1]["messages"]
    assert history[0] == {"role": "user", "content": "send money to Tolu"}
    assert history[1] == {"role": "assistant", "content": "How much should I send to Tolu?"}
    assert body["draft"]["readback"]["amount_kobo"] == 2_000_000


def test_without_a_key_the_rules_answer(client, monkeypatch):
    settings.assistant, settings.anthropic_api_key = "claude", ""
    monkeypatch.setattr(claude, "make_client", lambda: pytest.fail("no key, no model"))
    assert _say(client, _conv(client), "what's my balance")["engine"] == "rules"
    assert client.get("/health").json()["assistant"] == "rules"


def test_conversations_belong_to_the_customer(client):
    r = client.post("/v1/conversations/cv_nope/turns", headers=CUSTOMER, json={"text": "hi"})
    assert r.status_code == 404


def test_the_next_turn_follows_a_language_change(client):
    conv = _conv(client)
    assert _say(client, conv, "what's my balance")["say"].startswith("You have ")
    client.put("/v1/me/preferences", headers=CUSTOMER, json={"lang": "pcm"})
    assert _say(client, conv, "how much I get")["say"].startswith("You get ")
    body = _say(client, conv, "my PIN na 2075")
    assert body["say"].startswith("No ever talk your PIN for mouth")
    card = _say(client, conv, "abeg block my card")
    assert card["say"] == "Listen well, then pulse your PIN make I block the card."
    assert card["draft"]["readback"]["lang"] == "pcm"


@pytest.mark.parametrize("lang", ["yo", "ha", "ig"])
def test_yoruba_hausa_igbo_customers_get_english_lines_until_reviewed(client, lang):
    client.put("/v1/me/preferences", headers=CUSTOMER, json={"lang": lang})
    body = _say(client, _conv(client), "send twenty thousand to Tolu")
    assert body["say"].startswith("I found Adebayo Tolulope") and body["draft"]["readback"]["lang"] == lang


def test_claude_is_told_the_customers_language(client, fake_claude):
    client.put("/v1/me/preferences", headers=CUSTOMER, json={"lang": "ha"})
    fake = fake_claude([([_text("Sannu.")], "end_turn")])
    _say(client, _conv(client), "hello")
    assert "Reply in Hausa." in fake.requests[0]["system"][1]["text"]
