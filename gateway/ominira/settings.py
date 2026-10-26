"""Env config with safe laptop defaults, read once at import (tests set attributes)."""

import os
import secrets
from dataclasses import dataclass, field


def _env(name: str, default: str) -> str:
    return os.environ.get(name, default).strip()


def _flag(name: str, default: bool) -> bool:
    raw = os.environ.get(name)
    if raw is None or not raw.strip():
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def _int(name: str, default: int) -> int:
    raw = os.environ.get(name, "").strip()
    return int(raw) if raw else default


def _float(name: str, default: float) -> float:
    raw = os.environ.get(name, "").strip()
    return float(raw) if raw else default


@dataclass
class Settings:
    database_url: str = field(default_factory=lambda: _env("DATABASE_URL", "sqlite:///./ominira.db"))

    assistant: str = field(default_factory=lambda: _env("ASSISTANT", "claude"))  # claude | rules
    claude_model: str = field(default_factory=lambda: _env("CLAUDE_MODEL", "claude-sonnet-5-5"))
    anthropic_api_key: str = field(default_factory=lambda: _env("ANTHROPIC_API_KEY", ""))
    claude_timeout_s: float = field(default_factory=lambda: _float("CLAUDE_TIMEOUT_S", 8.0))

    zenith_profile: str = field(default_factory=lambda: _env("ZENITH_PROFILE", "realistic"))  # fast | flaky

    pause_threshold_kobo: int = field(default_factory=lambda: _int("PAUSE_THRESHOLD_KOBO", 5_000_000))
    draft_lifetime_s: int = field(default_factory=lambda: _int("DRAFT_LIFETIME_S", 600))
    approval_lifetime_s: int = field(default_factory=lambda: _int("APPROVAL_LIFETIME_S", 900))
    # How long confirm re-checks an "in progress" transfer before answering 202.
    requery_wait_s: float = field(default_factory=lambda: _float("REQUERY_WAIT_S", 3.0))
    lookups_per_minute: int = field(default_factory=lambda: _int("LOOKUPS_PER_MINUTE", 5))
    pin_attempts_per_minute: int = field(default_factory=lambda: _int("PIN_ATTEMPTS_PER_MINUTE", 5))

    idempotency_secret: str = field(default_factory=lambda: _env("IDEMPOTENCY_SECRET", "") or secrets.token_hex(32))

    speech_provider: str = field(default_factory=lambda: _env("SPEECH_PROVIDER", "elevenlabs").lower())  # or yarngpt
    elevenlabs_api_key: str = field(default_factory=lambda: _env("ELEVENLABS_API_KEY", ""))
    elevenlabs_base: str = field(default_factory=lambda: _env("ELEVENLABS_BASE", "https://api.elevenlabs.io"))
    elevenlabs_tts_model: str = field(default_factory=lambda: _env("ELEVENLABS_TTS_MODEL", "eleven_v4"))
    elevenlabs_live_model: str = field(default_factory=lambda: _env("ELEVENLABS_LIVE_MODEL", "eleven_v4_turbo"))
    elevenlabs_stt_model: str = field(default_factory=lambda: _env("ELEVENLABS_STT_MODEL", "scribe_v2"))
    elevenlabs_voices: str = field(default_factory=lambda: _env("ELEVENLABS_VOICES", ""))
    elevenlabs_default_voice: str = field(default_factory=lambda: _env("ELEVENLABS_DEFAULT_VOICE", "JBFqnCBsd6RMkjVDRZzb"))
    # enable_logging=false on every call: an enterprise plan feature, refused on the others.
    elevenlabs_zero_retention: bool = field(default_factory=lambda: _flag("ELEVENLABS_ZERO_RETENTION", False))

    yarngpt_api_key: str = field(default_factory=lambda: _env("YARNGPT_API_KEY", ""))
    # YARNGPT_API_KEY2 to YARNGPT_API_KEY9, tried in turn when the key before is out of calls or credit.
    yarngpt_more_keys: list[str] = field(
        default_factory=lambda: [k for k in (_env(f"YARNGPT_API_KEY{n}", "") for n in range(2, 10)) if k])
    yarngpt_base: str = field(default_factory=lambda: _env("YARNGPT_BASE", "https://api.yarngpt.ai"))
    yarngpt_voices: str = field(default_factory=lambda: _env("YARNGPT_VOICES", ""))

    # The phone line: Twilio signs every request with the account's auth token.
    twilio_account_sid: str = field(default_factory=lambda: _env("TWILIO_ACCOUNT_SID", ""))
    twilio_auth_token: str = field(default_factory=lambda: _env("TWILIO_AUTH_TOKEN", ""))
    # On: the call streams live audio, all by voice. Off: the step-by-step call with the keypad.
    phone_stream: bool = field(default_factory=lambda: _flag("PHONE_STREAM", True))
    # Seconds between the Pulse PIN's ticks on a call.
    # Dev bypass, for moving fast in demos: on a call, the PIN may also be typed on the keypad, four keys at
    # once. Not part of the product (the app's "Type PIN" is the same bypass); set PHONE_TYPED_PIN=false to remove it.
    phone_typed_pin: bool = field(default_factory=lambda: _flag("PHONE_TYPED_PIN", True))
    phone_pulse_gap_s: float = field(default_factory=lambda: _float("PHONE_PULSE_GAP_S", 2.0))

    # Set MOCK_ADMIN_TOKEN on any public deployment.
    mock_admin: bool = field(default_factory=lambda: _flag("MOCK_ADMIN", True))
    mock_admin_token: str = field(default_factory=lambda: _env("MOCK_ADMIN_TOKEN", ""))

    @property
    def claude_enabled(self) -> bool:
        return self.assistant == "claude" and bool(self.anthropic_api_key)

    @property
    def speech_enabled(self) -> bool:
        return bool(self.yarngpt_api_key if self.speech_provider == "yarngpt" else self.elevenlabs_api_key)


settings = Settings()
