"""ElevenLabs speech: text to speech and Scribe transcription. The key lives only here."""

import io
import time
import wave

import httpx

from ominira.settings import settings
from ominira.speech import SpeechUnavailable

MP3 = "mp3_44100_128"
TTS_TIMEOUT_S = 15.0
STT_TIMEOUT_S = 25.0
# No model lists Igbo or Pidgin: those are sent without a language code.
TTS_LANGS = {"en", "yo", "ha"}
# Pidgin is left to Scribe's own detection.
STT_LANGS = {"en", "yo", "ha", "ig"}
MIN_PCM_BYTES = 3200  # Scribe refuses under 100 ms: 16 kHz, 16-bit

# Tests put an httpx.MockTransport here.
transport: httpx.BaseTransport | None = None


def enabled() -> bool:
    return bool(settings.elevenlabs_api_key)


def pinned_voices() -> dict[str, str]:
    pinned = {}
    for pair in settings.elevenlabs_voices.split(","):
        lang, _, voice = pair.partition("=")
        if lang.strip() and voice.strip():
            pinned[lang.strip()] = voice.strip()
    return pinned


def voice_for(lang: str) -> str:
    return pinned_voices().get(lang) or settings.elevenlabs_default_voice


def _params(**params: str) -> dict[str, str]:
    return params | ({"enable_logging": "false"} if settings.elevenlabs_zero_retention else {})


def _refusal(response: httpx.Response) -> SpeechUnavailable:
    status, message = "", response.text[:200]
    try:
        detail = response.json().get("detail")
    except (ValueError, AttributeError):
        detail = None
    if isinstance(detail, dict):
        status, message = str(detail.get("status") or ""), str(detail.get("message") or "")
    elif isinstance(detail, list):
        message = "; ".join(str(d.get("msg", d)) if isinstance(d, dict) else str(d) for d in detail)[:200]
    # Out of characters comes back as 401 quota_exceeded, not only as 402.
    if response.status_code in (402, 429) or status == "quota_exceeded":
        return SpeechUnavailable("quota_exceeded", message)
    if response.status_code == 401:
        return SpeechUnavailable("bad_key", message)
    return SpeechUnavailable(f"http_{response.status_code}", message)


def _post(path: str, timeout: float, **kwargs) -> httpx.Response:
    if not enabled():
        raise SpeechUnavailable("not_configured", "ELEVENLABS_API_KEY is not set")
    try:
        with httpx.Client(base_url=settings.elevenlabs_base, timeout=timeout, transport=transport,
                          headers={"xi-api-key": settings.elevenlabs_api_key}) as client:
            response = client.post(path, **kwargs)
    except httpx.HTTPError as exc:
        raise SpeechUnavailable("network", str(exc)) from exc
    if response.status_code >= 400:
        raise _refusal(response)
    return response


def synthesize(text: str, lang: str, output_format: str = MP3, model: str | None = None,
               voice: str | None = None) -> bytes:
    body = {"text": text.strip(), "model_id": model or settings.elevenlabs_tts_model}
    if lang in TTS_LANGS:
        body["language_code"] = lang
    response = _post(f"/v1/text-to-speech/{voice or voice_for(lang)}", TTS_TIMEOUT_S,
                     params=_params(output_format=output_format), json=body)
    if not response.content:
        raise SpeechUnavailable("bad_response", "no audio")
    return response.content


def _pcm16k(audio: bytes) -> bytes | None:
    """The samples of a 16 kHz 16-bit mono WAV, which Scribe takes raw and starts on sooner."""
    try:
        with wave.open(io.BytesIO(audio), "rb") as src:
            if (src.getnchannels(), src.getsampwidth(), src.getframerate()) != (1, 2, 16000):
                return None
            return src.readframes(src.getnframes())
    except (wave.Error, EOFError):
        return None


def transcribe(audio: bytes, content_type: str, lang: str | None = None) -> dict:
    media = content_type.split(";")[0].strip().lower() or "audio/wav"
    data = {"model_id": settings.elevenlabs_stt_model}
    if lang in STT_LANGS:
        data["language_code"] = lang
    upload = (f"turn.{media.rsplit('/', 1)[-1]}", audio, media)
    pcm = _pcm16k(audio) if "wav" in media else None
    started = time.monotonic()
    if pcm is not None:
        if len(pcm) < MIN_PCM_BYTES:
            return {"text": "", "seconds": 0.0, "language": lang}
        data["file_format"] = "pcm_s16le_16"
        upload = ("turn.pcm", pcm, "application/octet-stream")
    response = _post("/v1/speech-to-text", STT_TIMEOUT_S, params=_params(), data=data, files={"file": upload})
    try:
        body = response.json()
    except ValueError as exc:
        raise SpeechUnavailable("bad_response", response.text[:200]) from exc
    return {"text": (body.get("text") or "").strip(), "seconds": round(time.monotonic() - started, 2),
            "language": body.get("language_code")}
