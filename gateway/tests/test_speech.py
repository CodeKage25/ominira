"""Speech endpoints against a fake YarnGPT (httpx.MockTransport). No network."""

import io
import json
import wave

import httpx
import pytest

from ominira.settings import settings
from ominira.speech import yarngpt
from tests.conftest import ASSISTANT, CUSTOMER

AUDIO = {"Content-Type": "audio/wav"}


def _wav(seconds: float, rate: int = 16000) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(b"\x10\x00" * int(rate * seconds))
    return buf.getvalue()


WAV = _wav(1.0)


class FakeYarn:
    def __init__(self):
        self.transcript = "send 20000 to Tolu"
        self.fail_with: int | None = None
        self.asr_status = "completed"
        self.polls = 0
        self.prepared: list[dict] = []
        self.uploads: list[httpx.Request] = []

    def __call__(self, req: httpx.Request) -> httpx.Response:
        assert req.headers["authorization"] == "Bearer test-key"
        if self.fail_with:
            return httpx.Response(self.fail_with, json={"error": {"code": "SERVICE_UNAVAILABLE", "message": "down"}})
        path = req.url.path
        if path == "/api/v1/voices":
            return httpx.Response(200, json={"voices": [{"id": "umar", "language": "English"},
                                                        {"id": "idera", "language": "Yoruba"},
                                                        {"id": "zainab", "languages": "Hausa, English"}]})
        if path == "/api/v1/tts/prepare":
            self.prepared.append(json.loads(req.content))
            return httpx.Response(200, json={"ticket": "tk1", "stream_url": "/api/v1/tts/stream/tk1", "expires_in": 300})
        if path == "/api/v1/asr" and req.method == "POST":
            self.uploads.append(req)
            return httpx.Response(202, json={"job_id": "job1", "status": "queued"})
        if path == "/api/v1/asr/job1":
            self.polls += 1
            if self.polls < 2:
                return httpx.Response(200, json={"status": "processing"})
            if self.asr_status == "failed":
                return httpx.Response(200, json={"status": "failed", "error_code": "BAD_AUDIO", "error_message": "no"})
            return httpx.Response(200, json={"status": "completed", "transcript": self.transcript})
        return httpx.Response(404, json={"error": {"code": "NOT_FOUND"}})


@pytest.fixture
def yarn(monkeypatch):
    fake = FakeYarn()
    settings.yarngpt_api_key = "test-key"
    monkeypatch.setattr(yarngpt, "transport", httpx.MockTransport(fake))
    monkeypatch.setattr(yarngpt, "_voice_cache", None)
    monkeypatch.setattr(yarngpt.time, "sleep", lambda s: None)
    return fake


def test_status_off_without_a_key(client):
    assert client.get("/v1/speech/status", headers=CUSTOMER).json() == {"enabled": False, "provider": None}
    assert client.get("/health").json()["speech"] is False


def test_status_on_with_a_key(client, yarn):
    assert client.get("/v1/speech/status", headers=CUSTOMER).json() == {"enabled": True, "provider": "yarngpt"}
    assert client.get("/health").json()["speech"] is True


def test_off_means_a_spoken_503(client):
    r = client.post("/v1/speech/say", headers=CUSTOMER, json={"text": "hello there, Ronke"})
    assert r.status_code == 503 and r.json()["code"] == "speech_unavailable"
    assert r.json()["provider_code"] == "not_configured" and "tap your choice" in r.json()["say"]


def test_say_returns_a_ticket_url_in_the_language_voice(client, yarn):
    r = client.post("/v1/speech/say", headers=CUSTOMER, json={"text": "Ẹ káàbọ̀", "lang": "yo"})
    assert r.status_code == 200
    assert r.json() == {"audio_url": "https://api.yarngpt.ai/api/v1/tts/stream/tk1", "expires_in": 300,
                        "lang": "yo", "voice": "idera"}
    assert yarn.prepared[-1] == {"text": yarngpt.pad_text("Ẹ káàbọ̀"), "output_format": "mp3", "voice": "idera"}


def test_say_uses_the_customers_language_by_default(client, yarn):
    client.put("/v1/me/preferences", headers=CUSTOMER, json={"lang": "ha"})
    r = client.post("/v1/speech/say", headers=CUSTOMER, json={"text": "Sannu da zuwa, Ronke"})
    assert r.json()["lang"] == "ha" and r.json()["voice"] == "zainab"


def test_pinned_voice_wins(client, yarn):
    settings.yarngpt_voices = "yo=femi, ha=umar"
    r = client.post("/v1/speech/say", headers=CUSTOMER, json={"text": "Sannu da zuwa", "lang": "ha"})
    assert r.json()["voice"] == "umar"


def test_listen_polls_until_ready(client, yarn):
    r = client.post("/v1/speech/listen", headers=CUSTOMER | AUDIO, content=WAV)
    assert r.status_code == 200
    assert r.json()["text"] == "send 20000 to Tolu" and r.json()["say"] is None and "seconds" in r.json()
    assert yarn.polls == 2 and yarn.uploads[0].headers["idempotency-key"]


def test_short_wav_is_padded_before_upload(client, yarn):
    client.post("/v1/speech/listen", headers=CUSTOMER | AUDIO, content=WAV)
    body = yarn.uploads[0].content
    start = body.index(b"RIFF")
    with wave.open(io.BytesIO(body[start:]), "rb") as w:
        assert w.getnframes() / w.getframerate() >= yarngpt.MIN_AUDIO_S


def test_a_spoken_pin_never_comes_back_as_text(client, yarn):
    yarn.transcript = "my PIN is 2075 send money to Tolu"
    body = client.post("/v1/speech/listen", headers=CUSTOMER | AUDIO, content=WAV).json()
    assert body["text"] == "" and body["pin_spoken"] is True and body["say"].startswith("Never say your PIN")


def test_silence_is_said_plainly(client, yarn):
    yarn.transcript = ""
    body = client.post("/v1/speech/listen", headers=CUSTOMER | AUDIO, content=WAV).json()
    assert body["text"] == "" and "didn't hear" in body["say"]


def test_asr_failure_is_a_503(client, yarn):
    yarn.asr_status = "failed"
    r = client.post("/v1/speech/listen", headers=CUSTOMER | AUDIO, content=WAV)
    assert r.status_code == 503 and r.json()["provider_code"] == "BAD_AUDIO"


def test_provider_down_is_a_503(client, yarn):
    yarn.fail_with = 503
    r = client.post("/v1/speech/say", headers=CUSTOMER, json={"text": "Twenty thousand naira to Tolu"})
    assert r.status_code == 503 and r.json()["provider_code"] == "SERVICE_UNAVAILABLE"


def test_network_failure_is_a_503(client, monkeypatch):
    settings.yarngpt_api_key = "test-key"

    def boom(_):
        raise httpx.ConnectError("no route")

    monkeypatch.setattr(yarngpt, "transport", httpx.MockTransport(boom))
    monkeypatch.setattr(yarngpt, "_voice_cache", None)
    r = client.post("/v1/speech/say", headers=CUSTOMER, json={"text": "Twenty thousand naira to Tolu"})
    assert r.status_code == 503 and r.json()["provider_code"] == "network"


def test_audio_body_rules(client, yarn):
    r = client.post("/v1/speech/listen", headers=CUSTOMER | {"Content-Type": "application/json"}, content=b"{}")
    assert r.status_code == 415 and r.json()["code"] == "audio_expected"
    r = client.post("/v1/speech/listen", headers=CUSTOMER | AUDIO, content=b"RIFF" + b"\0" * 100)
    assert r.status_code == 422 and r.json()["code"] == "audio_too_short"
    big = b"\0" * (4 * 1024 * 1024 + 1)
    r = client.post("/v1/speech/listen", headers=CUSTOMER | AUDIO, content=big)
    assert r.status_code == 413 and r.json()["code"] == "audio_too_long"


def test_listen_is_rate_limited(client, yarn):
    for _ in range(30):
        yarn.polls = 0
        assert client.post("/v1/speech/listen", headers=CUSTOMER | AUDIO, content=WAV).status_code == 200
    r = client.post("/v1/speech/listen", headers=CUSTOMER | AUDIO, content=WAV)
    assert r.status_code == 429 and "Retry-After" in r.headers


def test_voice_turn_listens_drafts_and_speaks(client, yarn):
    conv = client.post("/v1/conversations", headers=CUSTOMER).json()["id"]
    r = client.post(f"/v1/conversations/{conv}/voice-turns", headers=CUSTOMER | AUDIO, content=WAV)
    assert r.status_code == 200
    body = r.json()
    assert body["draft"]["readback"]["amount_kobo"] == 2_000_000 and body["draft"]["source"] == "voice"
    assert body["reply_audio_url"].endswith("/tk1") and body["engine"] == "rules"
    assert set(body["timings"]) == {"listen_s", "turn_s", "speak_s"}
    assert yarn.prepared[-1]["text"] == body["reply"]


def test_voice_turn_masks_what_was_heard(client, yarn):
    yarn.transcript = "send 5000 to 0123456789 at GTBank"
    conv = client.post("/v1/conversations", headers=CUSTOMER).json()["id"]
    body = client.post(f"/v1/conversations/{conv}/voice-turns", headers=CUSTOMER | AUDIO, content=WAV).json()
    assert "0123456789" not in body["heard"] and "ACCOUNT_1" in body["heard"]


def test_voice_turn_with_a_spoken_pin_drafts_nothing(client, yarn):
    yarn.transcript = "my pin is 2075 send twenty thousand to Tolu"
    conv = client.post("/v1/conversations", headers=CUSTOMER).json()["id"]
    body = client.post(f"/v1/conversations/{conv}/voice-turns", headers=CUSTOMER | AUDIO, content=WAV).json()
    assert body["draft"] is None and "2075" not in body["heard"] and body["say"].startswith("Never say your PIN")


def test_voice_turn_silence(client, yarn):
    yarn.transcript = ""
    conv = client.post("/v1/conversations", headers=CUSTOMER).json()["id"]
    body = client.post(f"/v1/conversations/{conv}/voice-turns", headers=CUSTOMER | AUDIO, content=WAV).json()
    assert body["draft"] is None and body["reply_audio_url"] is None and "didn't hear" in body["say"]


def test_voice_turn_still_answers_when_speaking_fails(client, yarn, monkeypatch):
    def no_voice(*_):
        raise yarngpt.SpeechUnavailable("http_503")

    monkeypatch.setattr(yarngpt, "prepare", no_voice)
    conv = client.post("/v1/conversations", headers=CUSTOMER).json()["id"]
    body = client.post(f"/v1/conversations/{conv}/voice-turns", headers=CUSTOMER | AUDIO, content=WAV).json()
    assert body["draft"] is not None and body["reply_audio_url"] is None


def test_the_assistant_identity_cannot_use_speech(client, yarn):
    assert client.post("/v1/speech/say", headers=ASSISTANT, json={"text": "hello there"}).status_code == 403
    assert client.post("/v1/speech/listen", headers=ASSISTANT | AUDIO, content=WAV).status_code == 403


def test_short_text_gets_tight_dots():
    assert yarngpt.pad_text("Sent. Tolu has it.") == "Sent. Tolu has it..."
    assert len(yarngpt.pad_text("Okay.")) >= yarngpt.MIN_TEXT_CHARS and ". ." not in yarngpt.pad_text("Okay.")
    long = "Twenty thousand naira to Adebayo Tolulope."
    assert yarngpt.pad_text(long) == long


def test_wav_padding_keeps_the_words():
    padded = yarngpt.pad_wav(_wav(0.6))
    with wave.open(io.BytesIO(padded)) as w:
        assert w.getnframes() / w.getframerate() > yarngpt.MIN_AUDIO_S
        assert w.readframes(9600) == b"\x10\x00" * 9600
    assert yarngpt.pad_wav(b"not a wav") == b"not a wav"
    three_s = _wav(3.0)
    assert yarngpt.pad_wav(three_s) == three_s
