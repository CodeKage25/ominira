"""Pulse PIN checks: Zenith verifies; each check counts against a per-token limit."""

from ominira import audit_log, lines, ratelimit
from ominira.core import get_core
from ominira.problems import Problem
from ominira.settings import settings
from ominira.tables import Customer


def require(customer: Customer, token: str, pin_block: str | None, *, purpose: str,
            draft_id: str | None = None) -> None:
    """Raise unless `pin_block` is this customer's PIN. Never log or store the block."""
    if not pin_block:
        raise Problem(401, "pin_required", "This needs the customer's Pulse PIN", lines.say(customer.lang, "only_customer"))
    ratelimit.allow(f"pin:{token}", settings.pin_attempts_per_minute, "Too many PIN attempts")
    check = get_core().verify_pin(customer.core_ref, pin_block)
    if check.ok:
        return
    audit_log.record("pin_incorrect", actor="customer", customer_id=customer.id, draft_id=draft_id,
                     purpose=purpose, tries_left=check.tries_left)
    raise Problem(401, "pin_incorrect", "The PIN did not match", lines.pin_wrong(customer.lang, check.tries_left),
                  tries_left=check.tries_left)
