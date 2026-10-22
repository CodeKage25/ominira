"""The assistant on Claude: masked text in, read and draft tools only, no confirm tool."""

import json
import logging

import anthropic
from sqlalchemy import select
from sqlalchemy.orm import Session

from ominira import drafting, lines
from ominira.assistant import toolbox
from ominira.core import get_core
from ominira.problems import Problem
from ominira.settings import settings
from ominira.tables import Conversation, Customer, Draft, Turn

log = logging.getLogger("ominira.assistant")

LANGUAGE_NAMES = {"en": "English", "pcm": "Nigerian Pidgin", "yo": "Yorùbá", "ha": "Hausa", "ig": "Igbo"}

SYSTEM_PROMPT = """You are Òmìnira, the voice of a Zenith Bank account. Many of the people you talk to are blind or have low vision; some are deaf, have limited movement, or read little. Every word you write is spoken aloud by text to speech, so write for the ear.

Replies: one or two short, plain sentences. No lists, symbols, emoji or markdown. Say amounts in words, in the language you reply in: "twenty thousand naira" in English, "naira dubu ashirin" in Hausa. Call people by their name. If something is unclear, ask one short question.

You can: say the balance, say what came in or went out, find saved people, look up a new account, and prepare a transfer or a card block.

You never move money. draft_transfer and draft_card_block only prepare a request; the app then reads it back and the customer confirms it with their Pulse PIN. After you draft, never say the money has gone.

Prepare at most one request per turn. Never guess an amount or a person. If the customer has not given both, ask for what is missing. A saved person counts as named when the customer uses their nickname, their name, or a short form of it (Tolu for Tolulope).

Account numbers reach you as labels like ACCOUNT_1. A label keeps its meaning for the whole conversation. Pass the label as account_ref; never make one up. If no bank is said, leave bank empty: an account a Zenith customer holds is found without one; otherwise ask which bank. Never ask for a PIN, BVN or card number. If the customer starts to say one, tell them never to say it aloud.

If the customer asks to send all or most of their money, ask for the exact amount first. Scammers push people to empty their accounts.

If the customer asks how the Pulse PIN works, call explain_pulse_pin and add nothing about digits.

If asked for something you cannot do, say briefly what you can do."""

_EMPTY = {"type": "object", "properties": {}, "additionalProperties": False}

TOOLS = [
    {"name": "get_balance", "description": "The customer's current account balance.", "input_schema": _EMPTY},
    {"name": "list_payees", "description": "Saved people: payee_id, nickname, name, bank, and whether they are new (never paid).",
     "input_schema": _EMPTY},
    {"name": "recent_transactions", "description": "The latest transactions, newest first.",
     "input_schema": {"type": "object", "properties": {"limit": {"type": "integer", "minimum": 1, "maximum": 10}},
                      "additionalProperties": False}},
    {"name": "money_in_this_week", "description": "One spoken sentence: how much came in over the last seven days, from how many people.",
     "input_schema": _EMPTY},
    {"name": "list_cards", "description": "The customer's cards: card_id, last four digits, status.", "input_schema": _EMPTY},
    {"name": "find_account", "description": "Name enquiry for an account that is not saved: who owns it, at which bank.",
     "input_schema": {"type": "object", "properties": {
         "bank": {"type": "string", "description": "The bank as the customer said it, e.g. GTBank, OPay, Access"},
         "account_ref": {"type": "string", "description": "The ACCOUNT_n label from the customer's words"}},
         "required": ["bank", "account_ref"], "additionalProperties": False}},
    {"name": "draft_transfer",
     "description": "Prepare a transfer for the customer to hear and confirm with their PIN. Give payee_id for a saved person, or bank and account_ref for a new account.",
     "input_schema": {"type": "object", "properties": {
         "amount_naira": {"type": "number", "description": "The amount in naira, exactly as the customer said it"},
         "payee_id": {"type": "string"}, "bank": {"type": "string"}, "account_ref": {"type": "string"}},
         "required": ["amount_naira"], "additionalProperties": False}},
    {"name": "draft_card_block", "description": "Prepare blocking a card, for the customer to confirm with their PIN.",
     "input_schema": {"type": "object", "properties": {"card_id": {"type": "string"}}, "required": ["card_id"],
                      "additionalProperties": False}},
    {"name": "explain_pulse_pin", "description": "The fixed explanation of how to enter a Pulse PIN.", "input_schema": _EMPTY},
]

ALLOWED_TOOLS = frozenset(t["name"] for t in TOOLS)


class ClaudeUnavailable(Exception):
    """Claude could not answer this turn; the rules answer instead."""


def make_client() -> anthropic.Anthropic:
    """Fail fast: one try, a short timeout. Tests replace this function."""
    return anthropic.Anthropic(api_key=settings.anthropic_api_key, timeout=settings.claude_timeout_s, max_retries=0)


class _ToolRunner:
    def __init__(self, db: Session, customer: Customer, accounts: dict[str, str], source: str):
        self.db, self.customer, self.accounts, self.source = db, customer, accounts, source
        self.draft: Draft | None = None
        self.explained = False

    def run(self, name: str, args: dict) -> str:
        c = self.customer
        if name not in ALLOWED_TOOLS:
            return json.dumps({"error": f"There is no tool called {name}."})
        if name == "get_balance":
            return json.dumps(toolbox.balance(c))
        if name == "list_payees":
            return json.dumps([{"payee_id": p.id, "nickname": p.nickname, "name": p.name, "bank": p.bank,
                                "new": p.is_new} for p in toolbox.payees(self.db, c)])
        if name == "recent_transactions":
            return json.dumps(toolbox.recent(c, int(args.get("limit", 5))))
        if name == "money_in_this_week":
            return toolbox.money_in_this_week(c)
        if name == "list_cards":
            return json.dumps(toolbox.cards(c))
        if name == "explain_pulse_pin":
            self.explained = True
            return lines.say(c.lang, "pulse_pin_explain")
        if name == "find_account":
            number = self.accounts.get(str(args.get("account_ref", "")))
            code = toolbox.bank_for(str(args.get("bank", "")), number)
            if number is None:
                return json.dumps({"error": "Unknown account label. Ask the customer to say the account number."})
            if code is None and not str(args.get("bank", "")).strip():
                return json.dumps({"error": lines.say(c.lang, "ask_bank")})
            if code is None:
                return json.dumps({"error": lines.say(c.lang, "unknown_bank", bank=args.get("bank", ""))})
            return json.dumps(toolbox.find_account(c, code, number))
        if self.draft is not None:
            return json.dumps({"error": "One request at a time. A request is already waiting for the customer."})
        if name == "draft_card_block":
            self.draft = drafting.new_card_block(self.db, c, card_id=str(args.get("card_id", "")), source=self.source,
                                                 actor="assistant")
            return json.dumps(toolbox.draft_summary(self.draft))
        return self._draft_transfer(args)

    def _draft_transfer(self, args: dict) -> str:
        try:
            kobo = round(float(args["amount_naira"]) * 100)
        except (KeyError, TypeError, ValueError):
            return json.dumps({"error": "amount_naira must be a number."})
        if args.get("payee_id"):
            self.draft = drafting.new_transfer(self.db, self.customer, amount_kobo=kobo, payee_id=str(args["payee_id"]),
                                               source=self.source, actor="assistant")
        else:
            ref = str(args.get("account_ref", ""))
            number = self.accounts.get(ref)
            code = toolbox.bank_for(str(args.get("bank", "")), number)
            if ref and number is None:
                return json.dumps({"error": f"Unknown account label {ref}. Ask the customer to say the account number."})
            if number is None or code is None:
                return json.dumps({"error": "Give a saved payee_id, or a known bank and an ACCOUNT_n label."})
            self.draft = drafting.new_transfer(self.db, self.customer, amount_kobo=kobo, bank_code=code,
                                               account_number=number, source=self.source, actor="assistant")
        return json.dumps(toolbox.draft_summary(self.draft))


def _history(db: Session, conv: Conversation, keep: int = 6) -> list[dict]:
    rows = list(db.scalars(select(Turn).where(Turn.conversation_id == conv.id).order_by(Turn.id.desc()).limit(keep)))
    messages: list[dict] = []
    for row in reversed(rows):
        messages.append({"role": "user", "content": row.heard})
        messages.append({"role": "assistant", "content": row.reply})
    return messages


def _system(db: Session, customer: Customer) -> list[dict]:
    first_name = get_core().profile(customer.core_ref).first_name
    saved = "; ".join(
        f"{p.nickname} = {p.name}, {p.bank}, payee_id {p.id}" + (", new (never paid)" if p.is_new else "")
        for p in toolbox.payees(db, customer)
    )
    return [
        {"type": "text", "text": SYSTEM_PROMPT, "cache_control": {"type": "ephemeral"}},
        {"type": "text", "text": f"The customer is {first_name}. Reply in {LANGUAGE_NAMES.get(customer.lang, 'English')}.\n"
                                 f"Saved people: {saved or 'none yet'}."},
    ]


def turn(db: Session, customer: Customer, conv: Conversation, masked: str, accounts: dict[str, str],
         source: str = "assistant") -> tuple[str, Draft | None]:
    """Raises ClaudeUnavailable when Claude cannot answer and nothing was drafted."""
    runner = _ToolRunner(db, customer, accounts, source)
    try:
        client = make_client()
        system = _system(db, customer)
        messages = _history(db, conv) + [{"role": "user", "content": masked}]
        for _ in range(4):
            response = client.messages.create(model=settings.claude_model, max_tokens=1024, system=system,
                                              tools=TOOLS, messages=messages)
            if response.stop_reason == "refusal":
                raise ClaudeUnavailable("Claude declined this turn")
            uses = [b for b in response.content if b.type == "tool_use"]
            if not uses:
                reply = " ".join(b.text for b in response.content if b.type == "text").strip()
                return reply or lines.say(customer.lang, "help"), None
            messages.append({"role": "assistant", "content": response.content})
            results = []
            for use in uses:
                try:
                    output = runner.run(use.name, dict(use.input or {}))
                    failed = output.startswith('{"error"')
                except Problem as p:
                    output, failed = json.dumps({"error": p.say or p.title}), True
                results.append({"type": "tool_result", "tool_use_id": use.id, "content": output, "is_error": failed})
            if runner.draft is not None:  # a fixed line, no second model call
                return toolbox.drafted_line(db, customer.lang, runner.draft), runner.draft
            if runner.explained:
                # A fixed line, like a readback: spoken as written, never reworded.
                return lines.say(customer.lang, "pulse_pin_explain"), None
            messages.append({"role": "user", "content": results})
        raise ClaudeUnavailable("too many tool rounds")
    except ClaudeUnavailable:
        raise
    except Exception as exc:  # any failure: the rules answer
        if runner.draft is not None:
            return toolbox.drafted_line(db, customer.lang, runner.draft), runner.draft
        raise ClaudeUnavailable(f"{type(exc).__name__}: {exc}") from exc
