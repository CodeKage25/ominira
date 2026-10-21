"""What the customer hears before they confirm, and the hash that proves they heard it.

readback_hash = "sha256:" + hex(sha256(canonical JSON of {kind, name, bank, account_last4, amount_kobo}))
with sorted keys and (",", ":") separators. Clients never compute it; they echo it back.
"""

import hashlib
import json

from ominira import amounts, lines

HASHED_FIELDS = ("kind", "name", "bank", "account_last4", "amount_kobo")


def readback_hash(rb: dict) -> str:
    core = {k: rb.get(k) for k in HASHED_FIELDS}
    canonical = json.dumps(core, sort_keys=True, separators=(",", ":"))
    return "sha256:" + hashlib.sha256(canonical.encode()).hexdigest()


def nip_name(raw: str) -> str:
    """NIP returns 'ADEBAYO TOLULOPE'; we say 'Adebayo Tolulope'."""
    return " ".join(part.capitalize() for part in raw.split())


def for_transfer(lang: str, *, name: str, bank: str, last4: str, amount_kobo: int,
                 warning: str | None = None) -> dict:
    spoken = [
        amounts.sentence(amount_kobo),
        lines.say(lang, "rb_to", name=name),
        lines.say(lang, "rb_bank", bank=bank, digits=amounts.digit_words(last4)),
        lines.say(lang, "rb_pulse_send"),
    ]
    if warning:
        spoken.insert(0, warning)
    return {
        "kind": "transfer", "name": name, "bank": bank, "account_last4": last4,
        "amount_kobo": amount_kobo, "amount_text": amounts.naira(amount_kobo), "lang": lang, "spoken": spoken,
    }


def for_card_block(lang: str, *, last4: str) -> dict:
    spoken = [
        lines.say(lang, "rb_block", digits=amounts.digit_words(last4)),
        lines.say(lang, "rb_block_effect"),
        lines.say(lang, "rb_pulse_block"),
    ]
    return {
        "kind": "card_block", "name": f"Card ending {last4}", "bank": "Zenith Bank", "account_last4": last4,
        "amount_kobo": None, "amount_text": None, "lang": lang, "spoken": spoken,
    }
