"""YarnGPT speech. The key lives only here; the phone gets ticket URLs."""

import io
import logging
import time
import uuid
import wave

import httpx

from ominira.settings import settings

log = logging.getLogger("ominira.speech")

MIN_TEXT_CHARS = 20  # YarnGPT refuses shorter text (INPUT_TOO_SHORT)
MIN_AUDIO_S = 2.0  # and shorter recordings
VOICES_TTL_S = 600

_LANGUAGE_WORDS = {
    "en": ("english", "en"),
    "pcm": ("pidgin", "pcm"),
    "yo": ("yoruba", "yorùbá", "yo"),
    "ha": ("hausa", "ha"),
    "ig": ("igbo", "ìgbò", "ig"),
}

_EXTENSIONS = {"audio/wav": "wav", "audio/x-wav": "wav", "audio/wave": "wav", "audio/mpeg": "mp3",
               "audio/mp3": "mp3", "audio/mp4": "m4a", "audio/aac": "aac", "audio/ogg": "ogg",
               "audio/webm": "webm", "audio/flac": "flac", "audio/amr": "amr"}

# Tests put an httpx.MockTransport here.
transport: httpx.BaseTransport | None = None
_voice_cache: tuple[float, list[dict]] | None = None


class SpeechUnavailable(Exception):
    def __init__(self, code: str, detail: str = ""):
        super().__init__(f"{code}: {detail}" if detail else code)
        self.code = code


def enabled() -> bool:
    return settings.speech_enabled


def _client(timeout: float) -> httpx.Client:
    if not enabled():
        raise SpeechUnavailable("not_configured", "YARNGPT_API_KEY is not set")
    return httpx.Client(base_url=settings.yarngpt_base, timeout=timeout, transport=transport,
                        headers={"Authorization": f"Bearer {settings.yarngpt_api_key}"})


def _json(response: httpx.Response) -> dict | list:
    if response.status_code >= 400:
        code, detail = f"http_{response.status_code}", ""
        try:
            err = response.json().get("error") or {}
            code = err.get("code") or code
            detail = err.get("message") or ""
        except (ValueError, AttributeError):
            detail = response.text[:200]
        raise SpeechUnavailable(code, detail)
    try:
        return response.json()
    except ValueError as exc:
        raise SpeechUnavailable("bad_response", response.text[:200]) from exc


def _call(timeout: float, fn):
    try:
        with _client(timeout) as client:
            return fn(client)
    except httpx.HTTPError as exc:
        raise SpeechUnavailable("network", str(exc)) from exc


def voices() -> list[dict]:
    """The voice catalogue, cached ten minutes. Ids can change, so they are never hard-coded."""
    global _voice_cache
    if _voice_cache and time.monotonic() - _voice_cache[0] < VOICES_TTL_S:
        return _voice_cache[1]
    data = _call(10.0, lambda c: _json(c.get("/api/v1/voices")))
    items = data.get("voices", data.get("data", [])) if isinstance(data, dict) else data
    catalogue = [v if isinstance(v, dict) else {"id": str(v)} for v in (items or [])]
    _voice_cache = (time.monotonic(), catalogue)
    return catalogue


def pinned_voices() -> dict[str, str]:
    pinned = {}
    for pair in settings.yarngpt_voices.split(","):
        lang, _, voice = pair.partition("=")
        if lang.strip() and voice.strip():
            pinned[lang.strip()] = voice.strip()
    return pinned


def voice_for(lang: str) -> str | None:
    """Pinned, else the first catalogue voice for the language, else None (default)."""
    pinned = pinned_voices()
    if lang in pinned:
        return pinned[lang]
    try:
        catalogue = voices()
    except SpeechUnavailable:
        return None
    wanted = _LANGUAGE_WORDS.get(lang, (lang,))
    for v in catalogue:
        described = " ".join(str(v.get(k, "")) for k in ("language", "languages", "lang", "locale", "accent")).lower()
        tokens = set(described.replace(",", " ").split())
        if any(w in tokens for w in wanted) or any(len(w) > 3 and w in described for w in wanted):
            for k in ("id", "voice", "name", "slug"):
                if isinstance(v.get(k), str):
                    return v[k]
    return None


def pad_text(text: str) -> str:
    """Pad with tight dots: spaced dots made YarnGPT babble."""
    text = text.strip()
    if len(text) >= MIN_TEXT_CHARS:
        return text
    stem = text.rstrip(".")
    return stem + "." * (MIN_TEXT_CHARS - len(stem))


def prepare(text: str, lang: str, output_format: str = "mp3", voice: str | None = None) -> dict:
    """Mint a ticket for `text`. `voice` overrides the voice chosen for `lang`."""
    voice = voice or voice_for(lang)
    body = {"text": pad_text(text), "output_format": output_format}
    if voice:
        body["voice"] = voice
    data = _call(10.0, lambda c: _json(c.post("/api/v1/tts/prepare", json=body)))
    stream = data.get("stream_url") or (f"/api/v1/tts/stream/{data['ticket']}" if data.get("ticket") else None)
    if not stream:
        raise SpeechUnavailable("bad_response", "no ticket in the answer")
    url = stream if stream.startswith("http") else settings.yarngpt_base.rstrip("/") + stream
    return {"audio_url": url, "expires_in": int(data.get("expires_in", 300)), "lang": lang, "voice": voice}


def download(audio_url: str, timeout: float = 60.0) -> bytes:
    """Fetch the audio behind a ticket URL, the way the phone does: no key, the ticket is enough."""
    try:
        with httpx.Client(timeout=timeout, transport=transport) as client:
            response = client.get(audio_url)
    except httpx.HTTPError as exc:
        raise SpeechUnavailable("network", str(exc)) from exc
    if response.status_code >= 400:
        raise SpeechUnavailable(f"http_{response.status_code}", response.text[:200])
    return response.content


def pad_wav(audio: bytes, min_s: float = MIN_AUDIO_S + 0.2) -> bytes:
    """Add silence to the end of a short PCM WAV. Anything else comes back unchanged."""
    try:
        with wave.open(io.BytesIO(audio), "rb") as src:
            params = src.getparams()
            frames = src.readframes(src.getnframes())
    except (wave.Error, EOFError):
        return audio
    frame_bytes = params.sampwidth * params.nchannels
    missing = int(params.framerate * min_s) - len(frames) // max(1, frame_bytes)
    if missing <= 0:
        return audio
    out = io.BytesIO()
    with wave.open(out, "wb") as dst:
        dst.setnchannels(params.nchannels)
        dst.setsampwidth(params.sampwidth)
        dst.setframerate(params.framerate)
        dst.writeframes(frames + b"\x00" * (missing * frame_bytes))
    return out.getvalue()


def transcribe(audio: bytes, content_type: str, wait_s: float = 25.0) -> dict:
    """One recording in, its transcript out. YarnGPT detects the language itself."""
    media = content_type.split(";")[0].strip().lower()
    ext = _EXTENSIONS.get(media, "wav")
    if ext == "wav":
        audio = pad_wav(audio)
    started = time.monotonic()

    def run(client: httpx.Client) -> dict:
        job = _json(client.post("/api/v1/asr", headers={"Idempotency-Key": uuid.uuid4().hex},
                                files={"file": (f"turn.{ext}", audio, media or "audio/wav")}))
        job_id = job.get("job_id") if isinstance(job, dict) else None
        if not job_id:
            raise SpeechUnavailable("bad_response", "no job_id")
        pause = 0.3
        while time.monotonic() - started < wait_s:
            time.sleep(pause)
            state = _json(client.get(f"/api/v1/asr/{job_id}"))
            status = state.get("status")
            if status == "completed":
                return {"text": (state.get("transcript") or "").strip(), "seconds": round(time.monotonic() - started, 2)}
            if status == "failed":
                raise SpeechUnavailable(state.get("error_code") or "asr_failed", state.get("error_message") or "")
            pause = min(pause * 1.5, 1.0)
        raise SpeechUnavailable("timeout", f"no transcript after {wait_s:.0f} s")

    return _call(20.0, run)
