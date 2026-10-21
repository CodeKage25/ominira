"""Idempotency-Key replay for state-changing calls (rules in docs/api.md)."""

import asyncio
import hashlib
import hmac
from datetime import timedelta

from sqlalchemy import delete
from starlette.concurrency import run_in_threadpool
from starlette.types import ASGIApp, Message, Receive, Scope, Send

from ominira.database import SessionFactory, utcnow
from ominira.problems import Problem
from ominira.settings import settings
from ominira.tables import StoredResponse

TTL = timedelta(hours=24)
METHODS = {"POST", "PUT", "PATCH", "DELETE"}
_SKIP_HEADERS = {b"content-length", b"date", b"server"}


def _fingerprint(data: bytes) -> str:
    """Keyed, so a stored confirm can't be matched against all 10,000 PIN blocks."""
    return hmac.new(settings.idempotency_secret.encode(), data, hashlib.sha256).hexdigest()


def _load(scope_key: str) -> StoredResponse | None:
    with SessionFactory() as db:
        row = db.get(StoredResponse, scope_key)
        if row is not None and utcnow() - row.created_at > TTL:
            db.execute(delete(StoredResponse).where(StoredResponse.scope_key == scope_key))
            db.commit()
            return None
        return row


def _save(row: StoredResponse) -> None:
    with SessionFactory() as db:
        if db.get(StoredResponse, row.scope_key) is None:
            db.add(row)
            db.commit()


class _KeyLocks:
    """Serialises a key in flight, so a concurrent repeat gets the replay, not a rerun."""

    def __init__(self) -> None:
        self._locks: dict[str, tuple[asyncio.Lock, int]] = {}

    def get(self, key: str) -> asyncio.Lock:
        lock, users = self._locks.get(key, (None, 0))
        if lock is None:
            lock = asyncio.Lock()
        self._locks[key] = (lock, users + 1)
        return lock

    def release(self, key: str) -> None:
        lock, users = self._locks[key]
        if users <= 1:
            del self._locks[key]
        else:
            self._locks[key] = (lock, users - 1)


async def _send_problem(send: Send, problem: Problem) -> None:
    response = problem.response()
    await send({"type": "http.response.start", "status": response.status_code, "headers": response.raw_headers})
    await send({"type": "http.response.body", "body": response.body})


class IdempotencyMiddleware:
    def __init__(self, app: ASGIApp) -> None:
        self.app = app
        self.locks = _KeyLocks()

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http" or scope["method"] not in METHODS:
            await self.app(scope, receive, send)
            return
        headers = dict(scope["headers"])
        client_key = headers.get(b"idempotency-key", b"").decode("latin-1").strip()
        if not client_key:
            await self.app(scope, receive, send)
            return
        if len(client_key) > 200:
            await _send_problem(send, Problem(422, "invalid_request", "Idempotency-Key is too long"))
            return

        body = bytearray()
        while True:
            message = await receive()
            if message["type"] == "http.disconnect":
                return
            body += message.get("body", b"")
            if not message.get("more_body"):
                break
        body = bytes(body)

        auth = headers.get(b"authorization", b"")
        scope_key = _fingerprint(b"\n".join([auth, scope["method"].encode(), scope["path"].encode(),
                                             client_key.encode()]))
        request_hash = _fingerprint(body)

        lock = self.locks.get(scope_key)
        try:
            async with lock:
                await self._handle(scope, send, body, scope_key, request_hash)
        finally:
            self.locks.release(scope_key)

    async def _handle(self, scope: Scope, send: Send, body: bytes, scope_key: str, request_hash: str) -> None:
        stored = await run_in_threadpool(_load, scope_key)
        if stored is not None:
            if stored.request_hash != request_hash:
                await _send_problem(send, Problem(422, "idempotency_key_reused",
                                                  "This Idempotency-Key was used for a different request"))
                return
            replay_headers = [(k.encode("latin-1"), v.encode("latin-1")) for k, v in stored.headers.items()]
            replay_headers += [(b"idempotent-replayed", b"true"),
                               (b"content-length", str(len(stored.body)).encode())]
            await send({"type": "http.response.start", "status": stored.status_code, "headers": replay_headers})
            await send({"type": "http.response.body", "body": stored.body})
            return

        sent_body = False

        async def replay_receive() -> Message:
            nonlocal sent_body
            if not sent_body:
                sent_body = True
                return {"type": "http.request", "body": body, "more_body": False}
            return {"type": "http.disconnect"}

        status = 500
        kept_headers: dict[str, str] = {}
        chunks: list[bytes] = []

        async def capture(message: Message) -> None:
            nonlocal status
            if message["type"] == "http.response.start":
                status = message["status"]
                for k, v in message.get("headers", []):
                    if k.lower() not in _SKIP_HEADERS:
                        kept_headers[k.decode("latin-1").lower()] = v.decode("latin-1")
            elif message["type"] == "http.response.body":
                chunks.append(message.get("body", b""))
            await send(message)

        await self.app(scope, replay_receive, capture)

        if status < 500 and status != 429:
            await run_in_threadpool(_save, StoredResponse(
                scope_key=scope_key, request_hash=request_hash, status_code=status,
                headers=kept_headers, body=b"".join(chunks),
            ))
