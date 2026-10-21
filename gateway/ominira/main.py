"""The Òmìnira Gateway application.

Run: uv run uvicorn ominira.main:app --port 8080
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from ominira import problems
from ominira.database import SessionFactory, engine
from ominira.gate import sweep_expired
from ominira.demo_world import ensure
from ominira.idempotency import IdempotencyMiddleware
from ominira.routes import assistant, audit, banking, drafts, mock_admin, onboarding, pages, speech

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s %(message)s")


@asynccontextmanager
async def _lifespan(_: FastAPI):
    # Hackathon: tables are created at start and the demo world is seeded if missing.
    # The pilot moves to migrations.
    ensure(engine)
    with SessionFactory() as db:
        sweep_expired(db)  # no stale draft (or full account number) survives a restart
    yield


def create_app() -> FastAPI:
    application = FastAPI(title="Òmìnira Gateway", version="0.2.0", lifespan=_lifespan)
    application.add_middleware(IdempotencyMiddleware)
    problems.install(application)
    for module in (pages, onboarding, banking, drafts, assistant, speech, audit, mock_admin):
        application.include_router(module.router)
    return application


app = create_app()
