"""What the assistant can do: read, look up, draft. Nothing here moves money."""

import re
from datetime import datetime, timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from ominira import amounts, lines, readback
from ominira.core import get_core
from ominira.database import utcnow
from ominira.drafting import enquire_account
from ominira.tables import Customer, Draft, Payee

ZENITH = "057"

_BANK_ALIASES = {
    "gtb": "058", "gt": "058", "gtbank": "058", "guaranty": "058", "guaranty trust": "058",
    "firstbank": "011", "first": "011", "first bank": "011", "uba": "033", "united bank": "033",
    "zenith": "057", "access": "044", "sterling": "232", "wema": "035", "opay": "999992",
    "palmpay": "999991", "palm pay": "999991", "moniepoint": "50515", "kuda": "50211",
}


def payees(db: Session, customer: Customer) -> list[Payee]:
    return list(db.scalars(select(Payee).where(Payee.customer_id == customer.id).order_by(Payee.nickname)))


def balance(customer: Customer) -> dict:
    acct = get_core().accounts(customer.core_ref)[0]
    return {"account": acct.name, "balance_kobo": acct.balance_kobo,
            "balance_words": amounts.spoken(acct.balance_kobo, customer.lang)}


def balance_line(customer: Customer) -> str:
    return lines.say(customer.lang, "balance", amount=balance(customer)["balance_words"])


def recent(customer: Customer, limit: int = 5) -> list[dict]:
    return [{"who": t.who, "amount_words": amounts.spoken(t.amount_kobo, customer.lang),
             "direction": "in" if t.incoming else "out", "at": t.at}
            for t in get_core().transactions(customer.core_ref, limit)]


def money_in_this_week(customer: Customer) -> str:
    """'One hundred and eighty-five thousand naira came in this week, from two people.'"""
    since = utcnow() - timedelta(days=7)
    came_in = [t for t in get_core().transactions(customer.core_ref, 100)
               if t.incoming and datetime.fromisoformat(t.at.removesuffix("Z")) >= since]
    if not came_in:
        return lines.say(customer.lang, "came_in_none")
    total = sum(t.amount_kobo for t in came_in)
    people = len({t.who for t in came_in})
    lang = customer.lang
    return lines.say(lang, "came_in", amount=amounts.sentence(total, lang)[:-1], count=amounts.words(people, lang),
                     people=lines.say(lang, "person" if people == 1 else "people"))


def cards(customer: Customer) -> list[dict]:
    return [{"card_id": c.id, "last4": c.last4, "status": c.status} for c in get_core().cards(customer.core_ref)]


def bank_code_for(said: str) -> str | None:
    """'GTBank', 'gt bank', 'Guaranty Trust', '058' -> '058'."""
    name = re.sub(r"\b(bank|plc|ltd|mfb|microfinance)\b", " ", said.lower())
    name = re.sub(r"\s+", " ", name).strip()
    if not name:
        return None
    for b in get_core().banks():
        if name == b.code or name == b.short.lower() or name == b.name.lower():
            return b.code
    if name in _BANK_ALIASES:
        return _BANK_ALIASES[name]
    squashed = name.replace(" ", "")
    if squashed in _BANK_ALIASES:
        return _BANK_ALIASES[squashed]
    for b in get_core().banks():
        if name in b.short.lower() or name in b.name.lower():
            return b.code
    return None


def bank_in_text(text: str) -> str | None:
    """The first bank mentioned anywhere in a sentence."""
    low = text.lower()
    for alias in sorted(_BANK_ALIASES, key=len, reverse=True):
        if re.search(rf"\b{re.escape(alias)}\b", low):
            return _BANK_ALIASES[alias]
    return None


def zenith_if_own(number: str) -> str | None:
    """No bank said: a number one of Zenith's own customers holds is taken as Zenith's."""
    return ZENITH if get_core().customer_for_account(number) else None


def bank_for(said: str, number: str | None) -> str | None:
    if said.strip():
        return bank_code_for(said)
    return zenith_if_own(number) if number else None


def find_account(customer: Customer, bank_code: str, number: str) -> dict:
    enquiry = enquire_account(customer.lang, bank_code, number, limit_for=customer.id)
    return {"name": readback.nip_name(enquiry.account_name), "bank": enquiry.bank.short, "last4": enquiry.last4}


def drafted_line(db: Session, lang: str, draft: Draft) -> str:
    """Fixed wording, never a model's: the readback after it is what they confirm."""
    if draft.kind == "card_block":
        return lines.say(lang, "drafted_card_block")
    payee = db.get(Payee, draft.payload["payee_id"]) if draft.payload.get("payee_id") else None
    new = lines.say(lang, "drafted_new_payee") if payee is None or payee.is_new else ""
    return lines.say(lang, "drafted_transfer", name=draft.readback["name"], bank=draft.readback["bank"], new=new)


def draft_summary(draft: Draft) -> dict:
    return {"draft_id": draft.id, "kind": draft.kind, "readback_the_app_will_speak": draft.readback["spoken"]}
