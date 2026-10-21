"""Per-conversation memory, in process only, so account numbers are never at rest."""

import threading
import time
from dataclasses import dataclass, field

IDLE_S = 3600


@dataclass
class Memory:
    labels: dict[str, str] = field(default_factory=dict)
    waiting_account: tuple[str, str] | None = None  # (label, bank_code) said without an amount
    touched: float = field(default_factory=time.monotonic)


_store: dict[str, Memory] = {}
_lock = threading.Lock()


def get(conversation_id: str) -> Memory:
    now = time.monotonic()
    with _lock:
        for cid in [c for c, m in _store.items() if now - m.touched > IDLE_S]:
            del _store[cid]
        mem = _store.setdefault(conversation_id, Memory())
        mem.touched = now
        return mem


def clear() -> None:
    with _lock:
        _store.clear()
