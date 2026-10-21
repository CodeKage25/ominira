"""Configuration from environment variables. Every default is safe for a laptop demo.

`settings` is read once at import. Tests change attributes on it directly.
"""

import os
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
    # SQLite on a laptop. Postgres on Cloud SQL looks like
    # postgresql+psycopg://user:pass@/ominira?host=/cloudsql/PROJECT:REGION:INSTANCE
    database_url: str = field(default_factory=lambda: _env("DATABASE_URL", "sqlite:///./ominira.db"))

    # "claude" uses Claude when ANTHROPIC_API_KEY is set; "rules" always uses the offline parser.
    assistant: str = field(default_factory=lambda: _env("ASSISTANT", "claude"))
    claude_model: str = field(default_factory=lambda: _env("CLAUDE_MODEL", "claude-sonnet-5-5"))
    anthropic_api_key: str = field(default_factory=lambda: _env("ANTHROPIC_API_KEY", ""))
    # A voice turn cannot wait long. After this the rule parser answers instead.
    claude_timeout_s: float = field(default_factory=lambda: _float("CLAUDE_TIMEOUT_S", 8.0))

    # Mock core behaviour: realistic (NIP-like delays), fast (tests), flaky (failures on purpose).
    zenith_profile: str = field(default_factory=lambda: _env("ZENITH_PROFILE", "realistic"))

    # Confirm gate. Amounts in kobo, times in seconds.
    pause_threshold_kobo: int = field(default_factory=lambda: _int("PAUSE_THRESHOLD_KOBO", 5_000_000))
    draft_lifetime_s: int = field(default_factory=lambda: _int("DRAFT_LIFETIME_S", 600))
    approval_lifetime_s: int = field(default_factory=lambda: _int("APPROVAL_LIFETIME_S", 900))
    # How long confirm keeps re-checking an "in progress" transfer before answering 202 processing.
    requery_wait_s: float = field(default_factory=lambda: _float("REQUERY_WAIT_S", 3.0))
    lookups_per_minute: int = field(default_factory=lambda: _int("LOOKUPS_PER_MINUTE", 5))

    # Speech. Without a key the app uses the phone's own voice and recogniser.
    yarngpt_api_key: str = field(default_factory=lambda: _env("YARNGPT_API_KEY", ""))
    yarngpt_base: str = field(default_factory=lambda: _env("YARNGPT_BASE", "https://api.yarngpt.ai"))
    # Pin a voice per language, e.g. "yo=idera,ha=umar,ig=chinenye".
    yarngpt_voices: str = field(default_factory=lambda: _env("YARNGPT_VOICES", ""))

    # Rehearsal controls at /_mock/*. Set MOCK_ADMIN_TOKEN on any public deployment.
    mock_admin: bool = field(default_factory=lambda: _flag("MOCK_ADMIN", True))
    mock_admin_token: str = field(default_factory=lambda: _env("MOCK_ADMIN_TOKEN", ""))

    @property
    def claude_enabled(self) -> bool:
        return self.assistant == "claude" and bool(self.anthropic_api_key)

    @property
    def speech_enabled(self) -> bool:
        return bool(self.yarngpt_api_key)


settings = Settings()
