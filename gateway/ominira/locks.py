"""In-process locks. The demo runs one instance; Postgres adds row locks on top.

Order, always: a draft lock first, then the customer's lock. Never hold two draft locks at once
(two drafts can share a stripe, and these locks are not re-entrant).
"""

import threading
import zlib
from collections import defaultdict
from contextlib import contextmanager

_DRAFT_STRIPES = [threading.Lock() for _ in range(64)]
_customer_locks: defaultdict[str, threading.Lock] = defaultdict(threading.Lock)
_customer_guard = threading.Lock()


@contextmanager
def draft_lock(draft_id: str):
    with _DRAFT_STRIPES[zlib.crc32(draft_id.encode()) % len(_DRAFT_STRIPES)]:
        yield


@contextmanager
def customer_lock(customer_id: str):
    """Held from the limit check to the end of a transfer, so two drafts cannot both fit under
    the same daily limit."""
    with _customer_guard:
        lock = _customer_locks[customer_id]
    with lock:
        yield
