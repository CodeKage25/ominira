"""Health and the co-approver's page. No auth, outside /v1."""

from pathlib import Path

from fastapi import APIRouter
from fastapi.responses import FileResponse

from ominira.core import get_core
from ominira.settings import settings

router = APIRouter()
_STATIC = Path(__file__).resolve().parent.parent / "static"


@router.get("/health")
def health():
    """Cloud Run reserves /healthz, so this lives at /health."""
    return {
        "ok": True,
        "zenith": get_core().kind,
        "assistant": "claude" if settings.claude_enabled else "rules",
        "speech": settings.speech_enabled,
    }


@router.get("/approve", include_in_schema=False)
def approve_page():
    """Funmi's page: open /approve?token=demo-approver on a second phone."""
    return FileResponse(_STATIC / "approve.html", media_type="text/html")
