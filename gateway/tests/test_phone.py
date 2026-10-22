"""The phone line: Twilio's calls, signed, through the same assistant and the same confirm gate."""

import pytest

from ominira.routes import phone
from ominira.settings import settings
from tests.conftest import balance

TOKEN = "test-twilio-token"


@pytest.fixture
def line(client, monkeypatch):
    monkeypatch.setattr(settings, "twilio_auth_token", TOKEN)
    monkeypatch.setattr(settings, "assistant", "rules")
    monkeypatch.setattr(settings, "phone_stream", False)  # the step-by-step call; the live one is test_phone_stream
    phone._calls.clear()

    def post(path: str, **form) -> str:
        form = {"CallSid": "CA1", **{k: str(v) for k, v in form.items()}}
        signature = phone._signature("http://testserver" + path, form)
        r = client.post(path, data=form, headers={"X-Twilio-Signature": signature})
        assert r.status_code == 200, r.text
        return r.text
    return post


def test_unsigned_calls_are_refused(client, monkeypatch):
    monkeypatch.setattr(settings, "twilio_auth_token", TOKEN)
    assert client.post("/phone/voice", data={"CallSid": "CA1"}).status_code == 403
    bad = client.post("/phone/voice", data={"CallSid": "CA1"}, headers={"X-Twilio-Signature": "nope"})
    assert bad.status_code == 403


def test_no_auth_token_no_phone_line(client, monkeypatch):
    monkeypatch.setattr(settings, "twilio_auth_token", "")
    assert client.post("/phone/voice", data={"CallSid": "CA1"}).status_code == 403


def test_send_money_by_phone(line, client):
    menu = line("/phone/voice")
    assert "For English, press 1." in menu and 'action="/phone/language"' in menu
    assert "Please enter your ten-digit Zenith account number." in line("/phone/language", Digits=1)
    hello = line("/phone/account", Digits="2081234567")
    assert "Hello Ronke." in hello and 'input="speech"' in hello

    before = balance(client)
    readback = line("/phone/heard", SpeechResult="Send twenty thousand to Tolu")
    assert "Twenty thousand naira." in readback and "Press 1 to send it" in readback
    assert "Enter your four-digit PIN" in line("/phone/choice", Digits=1)
    done = line("/phone/pin", Digits="2075")
    assert "Tolu has it." in done and "Anything else?" in done
    assert balance(client) == before - 2_000_000


def test_a_call_is_always_a_call_so_the_scam_pause_holds(line, client):
    line("/phone/language", Digits=1)
    line("/phone/account", Digits="2081234567")
    line("/phone/heard", SpeechResult="Send one hundred and fifty thousand to Musa")
    line("/phone/choice", Digits=1)
    paused = line("/phone/pin", Digits="2075")
    assert "telling you to send this" in paused and "Press 1 to stop it" in paused
    before = balance(client)
    stopped = line("/phone/pause", Digits=1)
    assert "Your money is safe" in stopped
    assert balance(client) == before


def test_a_wrong_pin_asks_again_and_moves_nothing(line, client):
    line("/phone/language", Digits=1)
    line("/phone/account", Digits="2081234567")
    line("/phone/heard", SpeechResult="Send twenty thousand to Tolu")
    line("/phone/choice", Digits=1)
    before = balance(client)
    again = line("/phone/pin", Digits="1111")
    assert "That PIN did not match" in again and 'action="/phone/pin"' in again
    assert balance(client) == before


def test_an_unknown_account_is_asked_for_again(line):
    line("/phone/language", Digits=1)
    assert "I could not find that account." in line("/phone/account", Digits="1234567890")


def test_yoruba_prompts_are_in_yoruba(line):
    line("/phone/language", Digits=3)
    # No YarnGPT key in tests: the line falls back to Twilio's voice, still saying the Yorùbá words.
    hello = line("/phone/account", Digits="2081234567")
    assert "Kí ni ẹ fẹ́ ṣe?" in hello and "<Record " in hello
