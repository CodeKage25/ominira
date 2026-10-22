"""Who is on the phone line. A customer is known by the number they call from. Anyone else is a stranger until
the account's Pulse PIN passes on the call, and hears the same whether or not the account number is real.

A stranger may give a few account numbers an hour, and an account takes two strangers' PINs an hour: one
retry for a slip, never the three wrong tries that lock the account at Zenith. So the line can neither find
out who banks here nor lock the customer out."""

import logging

from sqlalchemy import select

from ominira import pin, ratelimit
from ominira.core import get_core
from ominira.database import SessionFactory
from ominira.problems import Problem
from ominira.tables import Customer, Token

HOUR_S = 3600
ACCOUNTS_PER_HOUR = 10
PINS_PER_HOUR = 2

log = logging.getLogger("ominira.phone")


def known(caller: str) -> Customer | None:
    """The customer who calls from this number, detached from the session."""
    if not caller:
        return None
    with SessionFactory() as db:
        customer = db.scalars(select(Customer).where(Customer.phone == caller)).first()
        if customer is not None:
            db.expunge(customer)
        return customer


def owner(number: str) -> Customer | None:
    """The customer with this account number, detached from the session."""
    core_ref = get_core().customer_for_account(number) if len(number) == 10 else None
    if core_ref is None:
        return None
    with SessionFactory() as db:
        customer = db.scalars(select(Customer).where(Customer.core_ref == core_ref)).first()
        if customer is not None:
            db.expunge(customer)
        return customer


def may_try(caller: str) -> bool:
    """Count one account number from a stranger; False once they have given too many this hour."""
    try:
        ratelimit.allow(f"phone_account:{caller}", ACCOUNTS_PER_HOUR, window_s=HOUR_S)
        return True
    except Problem:
        return False


def prove(caller: str, number: str, pin_block: str, lang: str) -> tuple[str, str] | None:
    """(customer id, first name) once the account's PIN passes; from then on this number is the customer's.
    None for every failure, so a wrong PIN and a made-up account sound the same."""
    found = owner(number)
    if found is None:
        log.info("phone caller: no customer has that account number")  # the caller hears the same either way
        return None
    key = f"phone_pin:{found.id}"
    with SessionFactory() as db:
        customer = db.get(Customer, found.id)
        token = db.scalars(select(Token).where(Token.customer_id == customer.id, Token.role == "customer")).first()
        try:
            ratelimit.allow(key, PINS_PER_HOUR, window_s=HOUR_S)
        except Problem:
            log.info("phone caller: this account's PIN tries for the hour are used up")
            return None
        try:
            pin.require(customer, token.token if token else customer.id, pin_block, purpose="phone_caller")
        except Problem as problem:
            log.info("phone caller: PIN refused (%s)", problem.code)
            return None
        ratelimit.forget(key)
        customer.lang = lang
        if caller:
            customer.phone = caller
        db.commit()
        return customer.id, get_core().profile(customer.core_ref).first_name
