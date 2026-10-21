"""In-process locks for one instance; Postgres adds row locks on top."""

import threading
import zlib
from collections import defaultdict
from contextlib import contextmanager

# Draft lock, then customer lock. Never two draft locks: stripes collide.
_DRAFT_STRIPES = [threading.Lock() for _ in range(64)]
_customer_locks: defaultdict[str, threading.Lock] = defaultdict(threading.Lock)
_customer_guard = threading.Lock()


@contextmanager
def draft_lock(draft_id: str):
    with _DRAFT_STRIPES[zlib.crc32(draft_id.encode()) % len(_DRAFT_STRIPES)]:
        yield


@contextmanager
def customer_lock(customer_id: str):
    """Limit check to end of transfer, so two drafts can't both fit the daily limit."""
    with _customer_guard:
        lock = _customer_locks[customer_id]
    with lock:
        yield
