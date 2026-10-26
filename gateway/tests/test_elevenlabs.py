"""ElevenLabs as the speech provider, against a fake ElevenLabs (httpx.MockTransport). No network."""

import json
import re
from collections import OrderedDict

import httpx
import pytest

from ominira import lines, phone_audio
from ominira.routes import phone, phone_stream
from ominira.settings import settings
from ominira.speech import elevenlabs, provider
from tests.conftest import CUSTOMER
from tests.test_speech import AUDIO, WAV, _wav

MP3 = b"ID3\x03\x00\x00\x00\x00\x00\x00" + b"\xff\xfb\x90\x00" * 50
ULAW = b"\x7f" * 800
TOKEN = "test-twilio-token"


class FakeEleven:
    def __init__(self):
        self.requests: list[httpx.Request] = []
        self.transcript = "send 20000 to Tolu"
        self.refuse: tuple[int, dict] | None = None

    def __call__(self, req: httpx.Request) -> httpx.Response:
        assert req.headers["xi-api-key"] == "test-key"
        self.requests.append(req)
        if self.refuse:
            return httpx.Response(self.refuse[0], json=self.refuse[1])
        if req.url.path.startswith("/v1/text-to-speech/"):
            audio = ULAW if req.url.params["output_format"] == "ulaw_8000" else MP3
            return httpx.Response(200, content=audio, headers={"content-type": "audio/mpeg"})
        if req.url.path == "/v1/speech-to-text":
            return httpx.Response(200, json={"text": self.transcript, "language_code": "yor",
                                             "language_probability": 0.9, "words": []})
        return httpx.Response(404, json={"detail": {"status": "not_found", "message": "no"}})

    def last(self, path: str) -> httpx.Request:
        return next(r for r in reversed(self.requests) if r.url.path.startswith(path))


def fields(req: httpx.Request) -> dict[str, bytes]:
    """The multipart form's parts, by name."""
    parts = re.findall(rb'name="([^"]+)"(?:; filename="[^"]*")?\r\n(?:Content-Type: [^\r]*\r\n)?\r\n(.*?)\r\n--',
                       req.content, re.S)
    return {k.decode(): v for k, v in parts}


@pytest.fixture
def eleven(monkeypatch):
    fake = FakeEleven()
    monkeypatch.setattr(settings, "speech_provider", "elevenlabs")
    monkeypatch.setattr(settings, "elevenlabs_api_key", "test-key")
    monkeypatch.setattr(settings, "elevenlabs_voices", "yo=yoVoice1,ha=haVoice1")
    monkeypatch.setattr(elevenlabs, "transport", httpx.MockTransport(fake))
    monkeypatch.setattr(provider, "_audio", OrderedDict())
    return fake


@pytest.mark.parametrize("lang, voice, code", [
    ("en", "JBFqnCBsd6RMkjVDRZzb", "en"), ("yo", "yoVoice1", "yo"), ("ha", "haVoice1", "ha"),
    ("ig", "JBFqnCBsd6RMkjVDRZzb", None), ("pcm", "JBFqnCBsd6RMkjVDRZzb", None),
])
def test_speech_request_shape(eleven, lang, voice, code):
    assert elevenlabs.synthesize(" Twenty thousand naira to Tolu. ", lang) == MP3
    req = eleven.last("/v1/text-to-speech/")
    assert req.method == "POST" and req.url.host == "api.elevenlabs.io"
    assert req.url.path == f"/v1/text-to-speech/{voice}"
    assert dict(req.url.params) == {"output_format": "mp3_44100_128"}
    body = json.loads(req.content)
    assert body["text"] == "Twenty thousand naira to Tolu." and body["model_id"] == "eleven_v4"
    assert body.get("language_code") == code


def test_zero_retention_turns_logging_off_on_both_calls(eleven, monkeypatch):
    monkeypatch.setattr(settings, "elevenlabs_zero_retention", True)
    elevenlabs.synthesize("Twenty thousand naira", "en")
    elevenlabs.transcribe(WAV, "audio/wav", "en")
    assert all(r.url.params["enable_logging"] == "false" for r in eleven.requests) and len(eleven.requests) == 2


def test_status_names_the_provider(client, eleven):
    assert client.get("/v1/speech/status", headers=CUSTOMER).json() == {"enabled": True, "provider": "elevenlabs"}
    assert client.get("/health").json()["speech"] is True


def test_no_key_is_off_and_a_spoken_503(client, monkeypatch):
    monkeypatch.setattr(settings, "speech_provider", "elevenlabs")
    monkeypatch.setattr(settings, "yarngpt_api_key", "a-yarngpt-key")  # not the selected provider's
    assert client.get("/v1/speech/status", headers=CUSTOMER).json() == {"enabled": False, "provider": None}
    r = client.post("/v1/speech/say", headers=CUSTOMER, json={"text": "hello there, Ronke"})
    assert r.status_code == 503 and r.json()["provider_code"] == "not_configured"


def test_say_gives_a_gateway_link_that_plays_without_a_token(client, eleven):
    r = client.post("/v1/speech/say", headers=CUSTOMER, json={"text": "Ẹ káàbọ̀", "lang": "yo"})
    assert r.status_code == 200
    body = r.json()
    assert re.fullmatch(r"http://testserver/v1/speech/audio/[\w-]{22}\.mp3", body["audio_url"])
    assert body["expires_in"] == 300 and body["lang"] == "yo" and body["voice"] == "yoVoice1"
    played = client.get(body["audio_url"])
    assert played.status_code == 200 and played.content == MP3
    assert played.headers["content-type"] == "audio/mpeg" and played.headers["cache-control"] == "no-store"
    # No padding: ElevenLabs takes short text as it is.
    assert json.loads(eleven.last("/v1/text-to-speech/").content)["text"] == "Ẹ káàbọ̀"


def test_the_link_is_on_the_address_the_request_came_to(client, eleven):
    r = client.post("https://gateway.example/v1/speech/say", headers=CUSTOMER, json={"text": "Sent."})
    assert r.json()["audio_url"].startswith("https://gateway.example/v1/speech/audio/")


def test_expired_or_unknown_audio_is_404(client, eleven, monkeypatch):
    url = client.post("/v1/speech/say", headers=CUSTOMER, json={"text": "Sent. Tolu has it."}).json()["audio_url"]
    monkeypatch.setattr(provider, "AUDIO_TTL_S", -1)
    gone = client.get(url)
    assert gone.status_code == 404 and gone.json()["code"] == "not_found"
    assert client.get("/v1/speech/audio/made-up.mp3").status_code == 404


def test_held_audio_is_bounded(eleven, monkeypatch):
    monkeypatch.setattr(provider, "AUDIO_KEPT", 3)
    tickets = [provider.keep(bytes([i])) for i in range(4)]
    assert provider.kept(tickets[0]) is None and provider.kept(tickets[3]) == b"\x03"
    assert len(provider._audio) == 3


def test_listen_sends_the_customers_language_and_raw_pcm(client, eleven):
    client.put("/v1/me/preferences", headers=CUSTOMER, json={"lang": "yo"})
    r = client.post("/v1/speech/listen", headers=CUSTOMER | AUDIO, content=WAV)
    assert r.status_code == 200 and r.json()["text"] == "send 20000 to Tolu" and r.json()["say"] is None
    req = eleven.last("/v1/speech-to-text")
    assert req.method == "POST" and req.headers["content-type"].startswith("multipart/form-data")
    sent = fields(req)
    assert sent["model_id"] == b"scribe_v2" and sent["language_code"] == b"yo"
    assert sent["file_format"] == b"pcm_s16le_16" and sent["file"] == b"\x10\x00" * 16000
    assert "keyterms" not in sent and "enable_logging" not in req.url.params


def test_pidgin_is_left_to_detection_and_other_audio_goes_as_it_is(client, eleven):
    client.put("/v1/me/preferences", headers=CUSTOMER, json={"lang": "pcm"})
    client.post("/v1/speech/listen", headers=CUSTOMER | {"Content-Type": "audio/mp4"}, content=b"\0" * 2048)
    sent = fields(eleven.last("/v1/speech-to-text"))
    assert "language_code" not in sent and "file_format" not in sent and len(sent["file"]) == 2048
    assert b'filename="turn.mp4"' in eleven.last("/v1/speech-to-text").content


def test_under_a_tenth_of_a_second_is_not_sent(client, eleven):
    body = client.post("/v1/speech/listen", headers=CUSTOMER | AUDIO, content=_wav(0.05)).json()
    assert body["text"] == "" and "didn't hear" in body["say"]
    assert not eleven.requests


def test_a_spoken_pin_never_comes_back_as_text(client, eleven):
    eleven.transcript = "my PIN is 2075 send money to Tolu"
    body = client.post("/v1/speech/listen", headers=CUSTOMER | AUDIO, content=WAV).json()
    assert body["text"] == "" and body["pin_spoken"] is True and body["say"].startswith("Never say your PIN")


def test_voice_turn_listens_and_answers_with_a_gateway_link(client, eleven):
    conv = client.post("/v1/conversations", headers=CUSTOMER).json()["id"]
    body = client.post(f"/v1/conversations/{conv}/voice-turns", headers=CUSTOMER | AUDIO, content=WAV).json()
    assert body["draft"]["readback"]["amount_kobo"] == 2_000_000
    assert body["reply_audio_url"].startswith("http://testserver/v1/speech/audio/")
    assert client.get(body["reply_audio_url"]).content == MP3
    assert fields(eleven.last("/v1/speech-to-text"))["language_code"] == b"en"


@pytest.mark.parametrize("status, detail", [
    (402, {"status": "payment_required", "message": "add credit"}),
    (429, {"status": "too_many_concurrent_requests", "message": "slow down"}),
    (401, {"status": "quota_exceeded", "message": "This request exceeds your quota"}),
])
def test_out_of_quota_is_a_503(client, eleven, status, detail):
    eleven.refuse = (status, {"detail": detail})
    r = client.post("/v1/speech/say", headers=CUSTOMER, json={"text": "Twenty thousand naira to Tolu"})
    assert r.status_code == 503 and r.json()["code"] == "speech_unavailable"
    assert r.json()["provider_code"] == "quota_exceeded" and r.json()["say"]
    r = client.post("/v1/speech/listen", headers=CUSTOMER | AUDIO, content=WAV)
    assert r.status_code == 503 and r.json()["provider_code"] == "quota_exceeded"


def test_other_refusals_name_their_cause(eleven):
    eleven.refuse = (401, {"detail": {"status": "invalid_api_key", "message": "Invalid API key"}})
    with pytest.raises(provider.SpeechUnavailable) as err:
        elevenlabs.synthesize("Twenty thousand naira", "en")
    assert err.value.code == "bad_key"
    eleven.refuse = (422, {"detail": [{"loc": ["body", "text"], "msg": "field required"}]})
    with pytest.raises(provider.SpeechUnavailable) as err:
        elevenlabs.synthesize("Twenty thousand naira", "en")
    assert err.value.code == "http_422" and "field required" in str(err.value)


def test_network_failure_is_a_503(client, eleven, monkeypatch):
    def boom(_):
        raise httpx.ConnectError("no route")

    monkeypatch.setattr(elevenlabs, "transport", httpx.MockTransport(boom))
    r = client.post("/v1/speech/say", headers=CUSTOMER, json={"text": "Twenty thousand naira to Tolu"})
    assert r.status_code == 503 and r.json()["provider_code"] == "network"
    conv = client.post("/v1/conversations", headers=CUSTOMER).json()["id"]
    r = client.post(f"/v1/conversations/{conv}/voice-turns", headers=CUSTOMER | AUDIO, content=WAV)
    assert r.status_code == 503 and r.json()["provider_code"] == "network"


def test_the_live_call_asks_for_mulaw_from_the_live_model(eleven, monkeypatch):
    monkeypatch.setattr(phone_stream, "_live", OrderedDict())
    line = "Hello Adaeze, twenty thousand naira is on its way."
    assert phone_stream.speech("ha", line) == ULAW
    req = eleven.last("/v1/text-to-speech/")
    assert req.url.path == "/v1/text-to-speech/haVoice1" and req.url.params["output_format"] == "ulaw_8000"
    body = json.loads(req.content)
    assert body["model_id"] == "eleven_v4_turbo" and body["language_code"] == "ha"
    assert phone_stream.speech("ha", line) == ULAW and len(eleven.requests) == 1  # the call's cache


def test_the_live_call_still_plays_bundled_clips_first(eleven):
    bye = lines.say("en", "phone_bye")
    bundled = phone_stream.CLIPS / "en" / f"{phone_stream.clip_key(bye)}.ul"
    assert phone_stream.speech("en", bye) == bundled.read_bytes() and not eleven.requests


def test_the_live_call_tells_scribe_its_language(eleven):
    assert phone_stream.transcribe(phone_audio.tone(440, 500), "ig") == "send 20000 to Tolu"
    sent = fields(eleven.last("/v1/speech-to-text"))
    assert sent["language_code"] == b"ig" and sent["file_format"] == b"pcm_s16le_16"
    phone_stream.transcribe(phone_audio.tone(440, 500))
    assert "language_code" not in fields(eleven.last("/v1/speech-to-text"))


def test_the_keypad_call_plays_a_gateway_link(client, eleven, monkeypatch):
    monkeypatch.setattr(settings, "twilio_auth_token", TOKEN)
    monkeypatch.setattr(settings, "phone_stream", False)
    phone._calls.clear()

    def post(path: str, **form) -> str:
        form = {"CallSid": "CA9", "From": "+2348031234567", **form}
        signature = phone._signature("http://testserver" + path, form)
        r = client.post(path, data=form, headers={"X-Twilio-Signature": signature})
        assert r.status_code == 200, r.text
        return r.text

    post("/phone/voice")
    post("/phone/language", Digits="3")
    hello = post("/phone/account", Digits="2081234567")
    url = re.search(r"<Play>(http://testserver/v1/speech/audio/[^<]+\.mp3)</Play>", hello).group(1)
    assert client.get(url).content == MP3


def test_yarngpt_is_used_when_selected(client, eleven, monkeypatch):
    monkeypatch.setattr(settings, "speech_provider", "yarngpt")
    assert client.get("/v1/speech/status", headers=CUSTOMER).json() == {"enabled": False, "provider": None}
    r = client.post("/v1/speech/say", headers=CUSTOMER, json={"text": "Twenty thousand naira to Tolu"})
    assert r.status_code == 503 and r.json()["provider_code"] == "not_configured" and not eleven.requests
