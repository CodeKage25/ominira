"""Rehearsal controls for the mock core: reset the demo world, take a bank down, force the next
transfer's answer. Off when MOCK_ADMIN=false (404). With MOCK_ADMIN_TOKEN set, every call needs
the header X-Mock-Admin with that value (403 otherwise)."""

import hmac

from fastapi import APIRouter, Depends, Header
from pydantic import BaseModel, Field

from ominira import ratelimit
from ominira.core import mock_core
from ominira.core.mock import TIMEOUT_MODES, Controls
from ominira.database import engine
from ominira.demo_world import rebuild
from ominira.problems import Problem
from ominira.settings import settings


def _guard(x_mock_admin: str | None = Header(default=None)) -> None:
    if not settings.mock_admin or mock_core() is None:
        raise Problem(404, "not_found", "Not found")
    expected = settings.mock_admin_token
    if expected and not hmac.compare_digest((x_mock_admin or "").encode(), expected.encode()):
        raise Problem(403, "forbidden", "Send the X-Mock-Admin header")


router = APIRouter(prefix="/_mock", dependencies=[Depends(_guard)], include_in_schema=False)


class ControlsIn(BaseModel):
    profile: str | None = Field(None, pattern="^(realistic|fast|flaky)$")
    banks_down: list[str] | None = None
    next_transfer_code: str | None = Field(None, pattern="^[0-9]{2}$")
    next_transfer_timeout: str | None = Field(None, pattern="^(" + "|".join(TIMEOUT_MODES) + ")$")
    pending_rate: float | None = Field(None, ge=0, le=1)
    failure_rate: float | None = Field(None, ge=0, le=1)
    settle_after_s: float | None = Field(None, ge=0, le=600)


@router.get("/state")
def state():
    return mock_core().snapshot()


@router.post("/reset")
def reset():
    """Back to the start: balance ₦842,300, three PIN tries, no drafts, an empty audit log, and
    the controls of ZENITH_PROFILE."""
    rebuild(engine)
    ratelimit.clear()
    core = mock_core()
    core.controls = Controls.for_profile(settings.zenith_profile)
    return core.snapshot()


@router.post("/controls")
def controls(body: ControlsIn):
    core = mock_core()
    sent = body.model_dump(exclude_unset=True)
    profile = sent.pop("profile", None)
    if profile:
        # A new profile brings its own defaults; other fields in the same call apply on top.
        core.controls = Controls.for_profile(profile)
    for name, value in sent.items():
        if name == "banks_down":
            core.controls.banks_down = set(value or [])
        elif value is not None or name in ("next_transfer_code", "next_transfer_timeout"):
            setattr(core.controls, name, value)  # null clears a one-shot control
    return core.snapshot()
