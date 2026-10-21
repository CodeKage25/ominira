"""What a conversation remembers about account numbers between turns.

ACCOUNT_n labels are per conversation: the model sees earlier turns, so ACCOUNT_1 must mean the
same number in turn three as in turn one. The label map is kept in this process's memory, not in
the database, so full account numbers are never stored at rest; it is dropped after an hour of
quiet, and on a restart (a label the Gateway no longer knows is refused, never guessed).

It also holds a new account the customer named without an amount ("send money to 0123456789 at
GTBank"), so the next turn ("twenty thousand") can finish the request.
"""

import threading
import time
from dataclasses import dataclass, field

IDLE_S = 3600


@dataclass
class Memory:
    labels: dict[str, str] = field(default_factory=dict)
    waiting_account: tuple[str, str] | None = None  # (label, bank_code)
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
