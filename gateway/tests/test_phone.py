"""The phone line: Twilio's calls, signed, through the same assistant and the same confirm gate."""

import pytest

from ominira.database import SessionFactory
from ominira.demo_world import CUSTOMER_ID
from ominira.routes import phone
from ominira.settings import settings
from ominira.tables import Customer
from tests.conftest import balance, pin_tries_left

TOKEN = "test-twilio-token"
RONKE_PHONE = "+2348031234567"
STRANGER = "+2348039999999"


@pytest.fixture
def line(client, monkeypatch):
    monkeypatch.setattr(settings, "twilio_auth_token", TOKEN)
    monkeypatch.setattr(settings, "assistant", "rules")
    monkeypatch.setattr(settings, "phone_stream", False)  # the step-by-step call; the live one is test_phone_stream
    phone._calls.clear()

    def post(path: str, **form) -> str:
        form = {"CallSid": "CA1", "From": RONKE_PHONE, **{k: str(v) for k, v in form.items()}}
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


def test_a_short_account_number_is_asked_for_again(line):
    line("/phone/language", Digits=1)
    assert "I could not find that account." in line("/phone/account", Digits="12345")


def _ronke() -> tuple[str, str]:
    with SessionFactory() as db:
        customer = db.get(Customer, CUSTOMER_ID)
        return customer.phone, customer.lang


def test_a_stranger_hears_nothing_until_the_pin_passes_then_is_known(line):
    line("/phone/language", Digits=3, From=STRANGER)
    asked = line("/phone/account", Digits="2081234567", From=STRANGER)
    assert "Ronke" not in asked and 'action="/phone/caller"' in asked
    assert _ronke() == (RONKE_PHONE, "en")  # nothing changed yet
    hello = line("/phone/caller", Digits="2075", From=STRANGER)
    assert "Ronke" in hello and "<Record " in hello
    assert _ronke() == (STRANGER, "yo")
    # The next call from that number is known: straight to Ronke, no PIN first.
    line("/phone/language", Digits=1, CallSid="CA2", From=STRANGER)
    assert "Hello Ronke." in line("/phone/account", Digits="2081234567", CallSid="CA2", From=STRANGER)


def test_a_made_up_account_sounds_like_a_real_one(line):
    line("/phone/language", Digits=1, From=STRANGER)
    real = line("/phone/account", Digits="2081234567", From=STRANGER)
    line("/phone/language", Digits=1, CallSid="CA2", From=STRANGER)
    assert line("/phone/account", Digits="1234567890", CallSid="CA2", From=STRANGER) == real
    refused = line("/phone/caller", Digits="2075", CallSid="CA2", From=STRANGER)
    assert "registered phone number" in refused and "<Hangup/>" in refused


def test_strangers_get_two_pin_tries_an_hour_so_the_customer_is_not_locked_out(line, client):
    for n, caller in enumerate((STRANGER, "+2348037777777")):
        line("/phone/language", Digits=1, CallSid=f"CW{n}", From=caller)
        line("/phone/account", Digits="2081234567", CallSid=f"CW{n}", From=caller)
        wrong = line("/phone/caller", Digits="1111", CallSid=f"CW{n}", From=caller)
        assert "registered phone number" in wrong and "<Hangup/>" in wrong and "Ronke" not in wrong
    assert pin_tries_left(client) == 1
    # A third number, even with the right PIN: the account has had its two strangers' tries this hour,
    # so a third wrong one can never lock Ronke out.
    other = "+2348038888888"
    line("/phone/language", Digits=1, CallSid="CA2", From=other)
    line("/phone/account", Digits="2081234567", CallSid="CA2", From=other)
    assert "registered phone number" in line("/phone/caller", Digits="2075", CallSid="CA2", From=other)
    assert pin_tries_left(client) == 1 and _ronke() == (RONKE_PHONE, "en")
    # Ronke from her own number is unaffected.
    line("/phone/language", Digits=1, CallSid="CA3")
    assert "Hello Ronke." in line("/phone/account", Digits="2081234567", CallSid="CA3")


def test_a_stranger_may_try_ten_account_numbers_an_hour(line):
    for i in range(10):
        line("/phone/language", Digits=1, CallSid=f"CA{i}", From=STRANGER)
        assert 'action="/phone/caller"' in line("/phone/account", Digits=f"200000000{i}", CallSid=f"CA{i}",
                                                From=STRANGER)
    line("/phone/language", Digits=1, CallSid="CA9", From=STRANGER)
    eleventh = line("/phone/account", Digits="2081234567", CallSid="CA9", From=STRANGER)
    assert "registered phone number" in eleventh and "<Hangup/>" in eleventh and 'action="/phone/caller"' not in eleventh


def test_the_slow_work_of_a_call_runs_off_the_event_loop(line, monkeypatch):
    import asyncio

    from ominira.assistant import run_turn

    def turn(*args, **kwargs):
        with pytest.raises(RuntimeError):
            asyncio.get_running_loop()  # a worker thread has none
        seen.append(True)
        return run_turn(*args, **kwargs)
    seen: list[bool] = []
    monkeypatch.setattr(phone, "run_turn", turn)
    line("/phone/language", Digits=1)
    line("/phone/account", Digits="2081234567")
    line("/phone/heard", SpeechResult="what is my balance")
    assert seen == [True]


def test_yoruba_prompts_are_in_yoruba(line):
    line("/phone/language", Digits=3)
    # No YarnGPT key in tests: the line falls back to Twilio's voice, still saying the Yorùbá words.
    hello = line("/phone/account", Digits="2081234567")
    assert "Kí ni ẹ fẹ́ ṣe?" in hello and "<Record " in hello
