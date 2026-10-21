"""Engine and sessions. SQLite by default, Postgres through psycopg when DATABASE_URL says so."""

from collections.abc import Iterator
from datetime import UTC, datetime

from sqlalchemy import Engine, create_engine, event
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from ominira.settings import settings


class Base(DeclarativeBase):
    pass


def utcnow() -> datetime:
    """Naive UTC. SQLite and Postgres then store and compare the same values."""
    return datetime.now(UTC).replace(tzinfo=None)


def iso(t: datetime | None) -> str | None:
    """Wire format for times: ISO 8601 UTC, whole seconds, trailing Z."""
    return None if t is None else t.strftime("%Y-%m-%dT%H:%M:%SZ")


def _build_engine(url: str) -> Engine:
    if url.startswith("sqlite"):
        eng = create_engine(url, connect_args={"check_same_thread": False, "timeout": 15})

        @event.listens_for(eng, "connect")
        def _pragmas(conn, _record):
            conn.execute("PRAGMA foreign_keys=ON")

        return eng
    return create_engine(url, pool_pre_ping=True, pool_size=5, max_overflow=5)


engine = _build_engine(settings.database_url)
SessionFactory = sessionmaker(bind=engine, expire_on_commit=False)


def is_postgres() -> bool:
    return engine.dialect.name == "postgresql"


def get_session() -> Iterator[Session]:
    with SessionFactory() as session:
        yield session
