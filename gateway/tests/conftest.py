"""Test setup: a temporary SQLite file, the fast mock profile, no network. Claude and YarnGPT are
replaced by fakes in the tests that need them."""

import os
import tempfile

_DB_DIR = tempfile.mkdtemp(prefix="ominira-tests-")
os.environ.update({
    # TEST_DATABASE_URL runs the suite against another database, e.g. a throwaway Postgres.
    "DATABASE_URL": os.environ.get("TEST_DATABASE_URL") or f"sqlite:///{_DB_DIR}/test.db",
    "ZENITH_PROFILE": "fast",
    "ASSISTANT": "rules",
    "REQUERY_WAIT_S": "0",
    "MOCK_ADMIN": "true",
    "MOCK_ADMIN_TOKEN": "",
    "ANTHROPIC_API_KEY": "",
    "YARNGPT_API_KEY": "",
    "YARNGPT_VOICES": "",
})

import dataclasses  # noqa: E402

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy import select  # noqa: E402

from ominira import ratelimit  # noqa: E402
from ominira.core import get_core  # noqa: E402
from ominira.core.mock import Controls  # noqa: E402
from ominira.core.mock_tables import MockMove  # noqa: E402
from ominira.database import SessionFactory, engine  # noqa: E402
from ominira.demo_world import rebuild  # noqa: E402
from ominira.main import app  # noqa: E402
from ominira.settings import settings  # noqa: E402

PIN_OK = "demo-7924"  # PIN 2 0 7 5, sealed the way the app seals it
PIN_BAD = "demo-0000"
CUSTOMER = {"Authorization": "Bearer demo-customer"}
APPROVER = {"Authorization": "Bearer demo-approver"}
ASSISTANT = {"Authorization": "Bearer demo-assistant"}

_DEFAULTS = dataclasses.asdict(settings)


@pytest.fixture
def client():
    for name, value in _DEFAULTS.items():
        setattr(settings, name, value)
    rebuild(engine)
    ratelimit.clear()
    get_core().controls = Controls.for_profile("fast")
    with TestClient(app) as c:
        yield c
    for name, value in _DEFAULTS.items():
        setattr(settings, name, value)


@pytest.fixture
def core():
    return get_core()


def balance(c) -> int:
    return c.get("/v1/accounts", headers=CUSTOMER).json()[0]["balance_kobo"]


def make_draft(c, headers=CUSTOMER, **body) -> dict:
    body = {"kind": "transfer", **body}
    r = c.post("/v1/drafts", headers=headers, json=body)
    assert r.status_code == 201, r.text
    return r.json()


def confirm(c, draft: dict, pin: str = PIN_OK, call_active: bool = False, key: str | None = None,
            headers=CUSTOMER, readback_hash: str | None = None):
    h = dict(headers) | ({"Idempotency-Key": key} if key else {})
    return c.post(f"/v1/drafts/{draft['id']}/confirm", headers=h,
                  json={"readback_hash": readback_hash or draft["readback_hash"], "pin_block": pin,
                        "call_active": call_active})


def moves() -> list[MockMove]:
    """What the mock core actually did, in order."""
    with SessionFactory() as db:
        return list(db.scalars(select(MockMove).order_by(MockMove.at, MockMove.idempotency_key)))


def pin_tries_left(c) -> int:
    return next(iter(c.get("/_mock/state").json()["pin_tries_left"].values()))
