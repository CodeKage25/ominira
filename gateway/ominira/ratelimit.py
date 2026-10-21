"""Sliding one-minute windows kept in memory. Good for one instance (the demo runs max-instances 1)."""

import math
import threading
import time
from collections import defaultdict, deque

from ominira.problems import Problem

_windows: dict[str, deque[float]] = defaultdict(deque)
_lock = threading.Lock()


def allow(key: str, per_minute: int, title: str = "Too many requests") -> None:
    """Count one call against `key`, or raise 429 rate_limited with retry_after in seconds."""
    now = time.monotonic()
    with _lock:
        window = _windows[key]
        while window and now - window[0] >= 60:
            window.popleft()
        if len(window) >= per_minute:
            wait = max(1, math.ceil(60 - (now - window[0])))
            raise Problem(429, "rate_limited", title, retry_after=wait)
        window.append(now)


def clear() -> None:
    with _lock:
        _windows.clear()
