"""The live call, driven the way Twilio drives it: a signed call, then the call's audio over a WebSocket."""

import base64
import json
import queue
import threading

import pytest

from ominira import answers, phone_audio
from ominira.routes import phone, phone_stream
from ominira.settings import settings
from tests.conftest import balance

TOKEN = "test-twilio-token"
LOUD = phone_audio.tone(500, 20, 0.4)
QUIET = phone_audio.silence(20)


class FakeTwilio:
    """Echoes marks as Twilio does once audio has played, speaks when the call listens, and presses a key
    when the Pulse PIN's count reaches the digit it is entering."""

    def __init__(self, ws, said: list[str], pin: list[int]):
        self.ws, self.said, self.pin = ws, list(said), list(pin)
        self.listening = queue.Queue()
        self.lock = threading.Lock()
        self.heard_media = 0
        self.closed = False
        self.ticks: dict[str, int] = {}

    def send(self, message: dict) -> None:
        with self.lock:
            self.ws.send_text(json.dumps(message))

    def speak(self) -> None:
        frames = [LOUD] * 10 + [QUIET] * 40
        for f in frames:
            self.send({"event": "media", "media": {"track": "inbound", "payload": base64.b64encode(f).decode()}})

    def receive(self) -> None:
        try:
            while True:
                msg = json.loads(self.ws.receive_text())
                if msg["event"] == "media":
                    self.heard_media += 1
                elif msg["event"] == "mark":
                    name = msg["mark"]["name"]
                    self.send({"event": "mark", "mark": {"name": name}})
                    if name.startswith("t"):
                        run, i = name[1:].split("-")
                        # The count starts at zero in this test, so tick i is digit i.
                        if self.pin and int(i) % 10 == self.pin[0]:
                            self.pin.pop(0)
                            self.send({"event": "dtmf", "dtmf": {"digit": "5"}})
        except Exception:
            self.closed = True


@pytest.fixture
def live(client, monkeypatch):
    monkeypatch.setattr(settings, "twilio_auth_token", TOKEN)
    monkeypatch.setattr(settings, "assistant", "rules")
    monkeypatch.setattr(settings, "phone_stream", True)
    monkeypatch.setattr(settings, "phone_pulse_gap_s", 0.05)
    monkeypatch.setattr(phone_stream, "speech", lambda lang, text: phone_audio.silence(40))
    monkeypatch.setattr(phone_stream, "start_digit", lambda: 0)
    monkeypatch.setattr(phone_stream, "DIGIT_PAUSE_S", 0)

    def call(said: list[str], pin: list[int] = (), caller: str = "+2348030000001"):
        heard = list(said)
        monkeypatch.setattr(phone_stream, "transcribe", lambda audio: heard.pop(0) if heard else "")
        form = {"CallSid": "CA1", "From": caller}
        r = client.post("/phone/voice", data=form,
                        headers={"X-Twilio-Signature": phone._signature("http://testserver/phone/voice", form)})
        assert r.status_code == 200 and "<Connect><Stream" in r.text, r.text
        ticket = r.text.split('name="ticket" value="')[1].split('"')[0]
        with client.websocket_connect("/phone/stream") as ws:
            twilio = FakeTwilio(ws, said, list(pin))
            monkeypatch.setattr(phone_stream, "on_listen", lambda: twilio.listening.put(True))
            twilio.send({"event": "connected"})
            twilio.send({"event": "start", "streamSid": "MZ1",
                         "start": {"streamSid": "MZ1", "callSid": "CA1", "customParameters": {"ticket": ticket}}})
            reader = threading.Thread(target=twilio.receive, daemon=True)
            reader.start()
            for _ in said:
                twilio.listening.get(timeout=10)
                twilio.speak()
            reader.join(timeout=10)
        return twilio
    return call


def test_a_whole_call_by_voice(live, client):
    before = balance(client)
    live(["English", "two oh eight one two three four five six seven", "send twenty thousand to Tolu", "yes",
          "goodbye"], pin=[2, 0, 7, 5])
    assert balance(client) == before - 2_000_000


def test_saying_no_sends_nothing(live, client):
    before = balance(client)
    live(["Pidgin", "2081234567", "send twenty thousand to Tolu", "no", "bye"])
    assert balance(client) == before


def test_a_known_number_skips_the_account(live, client):
    live(["English", "2081234567", "goodbye"], caller="+2348031111111")
    before = balance(client)
    # Next call from the same number: straight to the request.
    live(["send twenty thousand to Tolu", "yes", "goodbye"], pin=[2, 0, 7, 5], caller="+2348031111111")
    assert balance(client) == before - 2_000_000


def test_a_wrong_pin_moves_nothing(live, client):
    before = balance(client)
    live(["English", "2081234567", "send twenty thousand to Tolu", "yes", "goodbye"], pin=[1, 1, 1, 1])
    assert balance(client) == before


def test_a_stream_without_a_ticket_is_refused(client):
    with client.websocket_connect("/phone/stream") as ws:
        ws.send_text(json.dumps({"event": "start", "start": {"customParameters": {"ticket": "made-up"}}}))
        with pytest.raises(Exception):
            ws.receive_text()


def test_spoken_answers():
    assert answers.language("Yorùbá") == "yo" and answers.language("I want Hausa") == "ha"
    assert answers.digits("two oh eight, 1 2 3") == "208123"
    assert answers.is_yes("Yes, send it") and not answers.is_yes("No, don't send it")
    assert answers.is_no("Rárá") and answers.is_stop("stop it") and answers.is_stop("yes")
    assert answers.is_bye("that's all, bye")


def test_phone_audio_round_trip():
    wav = phone_audio.ulaw_to_wav(phone_audio.tone(440, 200))
    assert len(phone_audio.wav_to_ulaw(wav)) == 1600  # 200 ms at 8 kHz
    listener = phone_audio.Listener(end_silence_ms=200)
    out = [listener.feed(LOUD) for _ in range(5)] + [listener.feed(QUIET) for _ in range(12)]
    assert any(began for began, _ in out) and any(said for _, said in out)
