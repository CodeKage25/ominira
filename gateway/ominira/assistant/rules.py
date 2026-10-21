"""The offline assistant: plain keyword rules, English and Pidgin. It answers when Claude is
switched off, has no key, or cannot be reached, so the demo survives bad venue Wi-Fi.

It understands:
  send <amount> to <payee>        "send twenty thousand to Tolu", "abeg send 20k give Mama"
  send money to <payee>, then <amount> on the next turn
  send <amount> to ACCOUNT_n at <bank>
  balance                          "what's my balance", "how much I get", "how much dey my account"
  what came in                     "what came in this week", "wetin enter my account", "who send me money"
  block my card                    "block my card", "my card don lost"
  how the Pulse PIN works
"""

import re

from sqlalchemy.orm import Session

from ominira import amounts, drafting, lines
from ominira.assistant import toolbox
from ominira.problems import Problem
from ominira.tables import Conversation, Customer, Draft, Payee

_SEND = r"\b(send|sen|transfer|pay|wire|remit|give|dash)\b"
_CAME_IN = (r"\bcame in\b", r"\bcome in\b", r"\benter(ed)?\b", r"\breceived?\b", r"\bincome\b",
            r"\b(sent|send|pay|paid)\s+me\b", r"\bmoney in\b")
_BALANCE = (r"\bbalance\b", r"\bhow much (do )?i (have|get|get left)\b", r"\bhow much (dey|remain|i get)\b",
            r"\bmy money\b", r"\bwetin remain\b", r"\bhow much money\b")
_CARD_TROUBLE = r"\b(block|lost|lose|missing|stolen|steal|tief|thief|cancel)\b"
_EXPLAIN = r"\b(how|explain|work|works|what is|wetin be|teach)\b"


def _any(text: str, patterns) -> bool:
    if isinstance(patterns, str):
        patterns = (patterns,)
    return any(re.search(p, text) for p in patterns)


def _named_payee(text: str, people: list[Payee]) -> Payee | None:
    for p in people:
        if re.search(rf"\b{re.escape(p.nickname.lower())}\b", text):
            return p
    for p in people:
        for part in p.name.lower().split():
            if len(part) > 3 and re.search(rf"\b{re.escape(part)}\b", text):
                return p
    return None


def turn(db: Session, customer: Customer, conv: Conversation, masked: str, accounts: dict[str, str],
         source: str = "assistant") -> tuple[str, Draft | None]:
    lang = customer.lang
    text = masked.lower()
    try:
        return _turn(db, customer, conv, text, accounts, lang, source)
    except Problem as p:
        # A refusal from a lookup or a draft (unknown account, bank down): say it, draft nothing.
        return p.say or lines.say(lang, "failed"), None


def _turn(db, customer, conv, text, accounts, lang, source):
    people = toolbox.payees(db, customer)

    if _any(text, r"\b(pulse|pin)\b") and _any(text, _EXPLAIN):
        return lines.say(lang, "pulse_pin_explain"), None

    if re.search(r"\bcard\b", text) and _any(text, _CARD_TROUBLE):
        cards = [c for c in toolbox.cards(customer) if c["status"] == "active"]
        if not cards:
            return lines.say(lang, "no_card"), None
        draft = drafting.new_card_block(db, customer, card_id=cards[0]["card_id"], source=source, actor="assistant")
        _forget(db, conv)
        return toolbox.drafted_line(db, lang, draft), draft

    if _any(text, _CAME_IN):
        return toolbox.money_in_this_week(customer), None

    amount = amounts.parse_kobo(text)
    named = _named_payee(text, people)
    label = next((k for k in accounts if k.lower() in text), None)
    bank_code = toolbox.bank_in_text(text) if label else None
    waiting = next((p for p in people if p.id == conv.waiting_payee_id), None)
    sending = _any(text, _SEND)

    if sending or named or label or (waiting and amount):
        if label and bank_code:
            if amount is None:
                return lines.say(lang, "ask_amount_and_account"), None
            draft = drafting.new_transfer(db, customer, amount_kobo=amount, bank_code=bank_code,
                                          account_number=accounts[label], source=source, actor="assistant")
            _forget(db, conv)
            return toolbox.drafted_line(db, lang, draft), draft
        payee = named or (waiting if amount is not None else None)
        if payee is None:
            if amount is None and _any(text, _BALANCE):
                return toolbox.balance_line(customer), None  # "give me my balance"
            if not sending and not amount:
                return lines.say(lang, "help"), None
            names = " or ".join(p.nickname for p in people[:2]) or "Mama"
            return lines.say(lang, "ask_who", names=names), None
        if amount is None:
            conv.waiting_payee_id = payee.id
            db.commit()
            return lines.say(lang, "ask_amount", nick=payee.nickname), None
        draft = drafting.new_transfer(db, customer, amount_kobo=amount, payee_id=payee.id, source=source,
                                      actor="assistant")
        _forget(db, conv)
        return toolbox.drafted_line(db, lang, draft), draft

    if _any(text, _BALANCE):
        return toolbox.balance_line(customer), None

    return lines.say(lang, "help"), None


def _forget(db: Session, conv: Conversation) -> None:
    if conv.waiting_payee_id is not None:
        conv.waiting_payee_id = None
        db.commit()
