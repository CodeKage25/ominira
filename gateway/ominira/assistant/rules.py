"""The offline assistant: keyword rules in English and Pidgin, for when Claude is not."""

import re

from sqlalchemy.orm import Session

from ominira import amounts, drafting, lines
from ominira.assistant import toolbox
from ominira.assistant.memory import Memory
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


# Words of a request, never the start of a name.
_NOT_NAMES = {"send", "transfer", "money", "naira", "thousand", "hundred", "million", "please", "abeg", "account",
              "give", "dash", "wire", "remit", "make", "with", "from", "bank", "zenith"}


def _named_payee(text: str, people: list[Payee]) -> Payee | None:
    for p in people:
        if re.search(rf"\b{re.escape(p.nickname.lower())}\b", text):
            return p
    for p in people:
        for part in p.name.lower().split():
            if len(part) > 3 and re.search(rf"\b{re.escape(part)}\b", text):
                return p
    # A short form: "Tolu" for Tolulope.
    said = [w for w in re.findall(r"[a-z]+", text) if len(w) >= 4 and w not in _NOT_NAMES]
    for p in people:
        for part in (p.nickname.lower(), *p.name.lower().split()):
            if any(part.startswith(w) for w in said):
                return p
    return None


def turn(db: Session, customer: Customer, conv: Conversation, masked: str, mem: Memory,
         source: str = "assistant") -> tuple[str, Draft | None]:
    lang = customer.lang
    text = masked.lower()
    try:
        return _turn(db, customer, conv, text, mem, lang, source)
    except Problem as p:
        return p.say or lines.say(lang, "failed"), None


def _turn(db, customer, conv, text, mem: Memory, lang, source):
    people = toolbox.payees(db, customer)

    if _any(text, r"\b(pulse|pin)\b") and _any(text, _EXPLAIN):
        return lines.say(lang, "pulse_pin_explain"), None

    if re.search(r"\bcard\b", text) and _any(text, _CARD_TROUBLE):
        cards = [c for c in toolbox.cards(customer) if c["status"] == "active"]
        if not cards:
            return lines.say(lang, "no_card"), None
        draft = drafting.new_card_block(db, customer, card_id=cards[0]["card_id"], source=source, actor="assistant")
        _forget(db, conv, mem)
        return toolbox.drafted_line(db, lang, draft), draft

    if _any(text, _CAME_IN):
        return toolbox.money_in_this_week(customer), None

    amount = amounts.parse_kobo(text)
    named = _named_payee(text, people)
    label = next((m.upper() for m in re.findall(r"\baccount_\d+\b", text) if m.upper() in mem.labels), None)
    bank_code = (toolbox.bank_in_text(text) or toolbox.zenith_if_own(mem.labels[label])) if label else None
    waiting = next((p for p in people if p.id == conv.waiting_payee_id), None)
    sending = _any(text, _SEND)

    if label and not named:
        if bank_code is None:
            return lines.say(lang, "ask_bank"), None
        if amount is None:
            found = toolbox.find_account(customer, bank_code, mem.labels[label])
            mem.waiting_account = (label, bank_code)
            conv.waiting_payee_id = None
            db.commit()
            return lines.say(lang, "ask_amount", nick=found["name"]), None
        return _draft_to_account(db, customer, conv, mem, amount, label, bank_code, lang, source)

    if amount is not None and not named and mem.waiting_account and mem.waiting_account[0] in mem.labels:
        label, bank_code = mem.waiting_account
        return _draft_to_account(db, customer, conv, mem, amount, label, bank_code, lang, source)

    if sending or named or (waiting and amount):
        payee = named or (waiting if amount is not None else None)
        if payee is None:
            if amount is None and _any(text, _BALANCE):
                return toolbox.balance_line(customer), None  # "give me my balance": "give" sends
            if not sending and not amount:
                return lines.say(lang, "help"), None
            names = " or ".join(p.nickname for p in people[:2]) or "Mama"
            return lines.say(lang, "ask_who", names=names), None
        if amount is None:
            conv.waiting_payee_id = payee.id
            mem.waiting_account = None
            db.commit()
            return lines.say(lang, "ask_amount", nick=payee.nickname), None
        draft = drafting.new_transfer(db, customer, amount_kobo=amount, payee_id=payee.id, source=source,
                                      actor="assistant")
        _forget(db, conv, mem)
        return toolbox.drafted_line(db, lang, draft), draft

    if _any(text, _BALANCE):
        return toolbox.balance_line(customer), None

    return lines.say(lang, "help"), None


def _draft_to_account(db, customer, conv, mem, amount, label, bank_code, lang, source):
    draft = drafting.new_transfer(db, customer, amount_kobo=amount, bank_code=bank_code,
                                  account_number=mem.labels[label], source=source, actor="assistant")
    _forget(db, conv, mem)
    return toolbox.drafted_line(db, lang, draft), draft


def _forget(db: Session, conv: Conversation, mem: Memory) -> None:
    mem.waiting_account = None
    if conv.waiting_payee_id is not None:
        conv.waiting_payee_id = None
        db.commit()
