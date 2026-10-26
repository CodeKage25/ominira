"""The speech every caller uses: ElevenLabs or YarnGPT, chosen by SPEECH_PROVIDER."""

import hashlib
import logging
import secrets
import shutil
import threading
import time
from collections import OrderedDict

from ominira import phone_audio as codec
from ominira.settings import settings
from ominira.speech import SpeechUnavailable, elevenlabs, yarngpt

log = logging.getLogger("ominira.speech")

AUDIO_TTL_S = 300
AUDIO_KEPT = 200
# YarnGPT's call voices: Jude reads English and Pidgin cleanly on a phone line.
YARN_CALL_VOICES = {"en": "jude", "pcm": "jude"}

_audio: "OrderedDict[str, tuple[float, bytes]]" = OrderedDict()
_audio_lock = threading.Lock()


def name() -> str:
    return "yarngpt" if settings.speech_provider == "yarngpt" else "elevenlabs"


def _yarn() -> bool:
    return name() == "yarngpt"


def enabled() -> bool:
    return settings.speech_enabled


def key_name() -> str:
    return "YARNGPT_API_KEY" if _yarn() else "ELEVENLABS_API_KEY"


def voice_for(lang: str) -> str | None:
    return yarngpt.voice_for(lang) if _yarn() else elevenlabs.voice_for(lang)


def pinned_voices() -> dict[str, str]:
    return yarngpt.pinned_voices() if _yarn() else elevenlabs.pinned_voices()


def pin_voices(pairs: list[str]) -> None:
    """Pin voices for this process, as lang=id pairs, over the configured ones."""
    attr = "yarngpt_voices" if _yarn() else "elevenlabs_voices"
    setattr(settings, attr, ",".join([getattr(settings, attr), *pairs]).strip(","))


def call_voice(lang: str) -> str | None:
    """The live call's voice, for its bundled prompts and live lines alike, so a call keeps one voice."""
    if _yarn():
        return YARN_CALL_VOICES.get(lang) or yarngpt.voice_for(lang)
    return elevenlabs.voice_for(lang)


def keep(audio: bytes) -> str:
    """Hold audio for AUDIO_TTL_S under a random ticket."""
    ticket = secrets.token_urlsafe(16)
    now = time.monotonic()
    with _audio_lock:
        while _audio and (len(_audio) >= AUDIO_KEPT or now - next(iter(_audio.values()))[0] > AUDIO_TTL_S):
            _audio.popitem(last=False)
        _audio[ticket] = (now, audio)
    return ticket


def kept(ticket: str) -> bytes | None:
    with _audio_lock:
        held = _audio.get(ticket)
        if held is None or time.monotonic() - held[0] > AUDIO_TTL_S:
            _audio.pop(ticket, None)
            return None
        return held[1]


def prepare(text: str, lang: str, base_url: str) -> dict:
    """A link the phone or Twilio plays without a key. ElevenLabs' audio is held here, at `base_url`."""
    if _yarn():
        return yarngpt.prepare(text, lang)
    voice = elevenlabs.voice_for(lang)
    ticket = keep(elevenlabs.synthesize(text, lang, voice=voice))
    return {"audio_url": f"{base_url.rstrip('/')}/v1/speech/audio/{ticket}.mp3", "expires_in": AUDIO_TTL_S,
            "lang": lang, "voice": voice}


def mp3(text: str, lang: str, voice: str | None = None) -> bytes:
    """MP3 now: YarnGPT's ticket route, or ElevenLabs."""
    if _yarn():
        return yarngpt.download(yarngpt.prepare(text, lang, output_format="mp3", voice=voice)["audio_url"])
    return elevenlabs.synthesize(text, lang, voice=voice)


def render(text: str, lang: str, key: str, output_format: str = "mp3", voice: str | None = None) -> bytes:
    """Batch speech for the clip scripts. `key` is YarnGPT's Idempotency-Key, unused by ElevenLabs."""
    if _yarn():
        return yarngpt.render(text, voice, key, output_format=output_format)
    return elevenlabs.synthesize(text, lang, elevenlabs.MP3 if output_format == "mp3" else output_format,
                                 voice=voice)


def phone_audio(text: str, lang: str) -> bytes:
    """8 kHz mu-law for the live call. Blocking."""
    if not _yarn():
        return elevenlabs.synthesize(text, lang, "ulaw_8000", settings.elevenlabs_live_model, call_voice(lang))
    voice = call_voice(lang)
    # MP3 arrives about three times sooner than WAV, but needs ffmpeg.
    fmt = "mp3" if shutil.which("ffmpeg") else "wav"
    to_ulaw = codec.mp3_to_ulaw if fmt == "mp3" else codec.wav_to_ulaw
    try:
        return to_ulaw(yarngpt.download(yarngpt.prepare(text, lang, output_format=fmt, voice=voice)["audio_url"]))
    except (SpeechUnavailable, ValueError) as err:
        log.warning("live speech on the prepare route failed: %s", err)
    key = hashlib.sha256((lang + text).strip().encode()).hexdigest()[:16]
    return to_ulaw(yarngpt.render(text, voice, key, output_format=fmt, wait_s=20))


def transcribe(audio: bytes, content_type: str, lang: str | None = None) -> dict:
    """{"text", "seconds"}. YarnGPT detects the language itself; ElevenLabs is told it when known."""
    if _yarn():
        return yarngpt.transcribe(audio, content_type)
    return elevenlabs.transcribe(audio, content_type, lang)
