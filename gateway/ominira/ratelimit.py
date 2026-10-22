"""Sliding windows in memory: one instance only (Cloud Run max-instances 1)."""

import math
import threading
import time
from collections import defaultdict, deque

from ominira.problems import Problem

_windows: dict[str, deque[float]] = defaultdict(deque)
_lock = threading.Lock()


def allow(key: str, limit: int, title: str = "Too many requests", window_s: float = 60) -> None:
    """Count one call against `key`, or raise 429 rate_limited with retry_after in seconds."""
    now = time.monotonic()
    with _lock:
        window = _windows[key]
        while window and now - window[0] >= window_s:
            window.popleft()
        if len(window) >= limit:
            wait = max(1, math.ceil(window_s - (now - window[0])))
            raise Problem(429, "rate_limited", title, retry_after=wait)
        window.append(now)


def forget(key: str) -> None:
    with _lock:
        _windows.pop(key, None)


def clear() -> None:
    with _lock:
        _windows.clear()
