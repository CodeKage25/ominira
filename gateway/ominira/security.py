"""Who is calling, and what they may do.

  customer   the account holder's phone: everything for that customer, and the only role that
             can confirm, cancel or answer a scam pause
  approver   a trusted co-approver: sees and decides their own queue, never the balance
  assistant  the language model's identity: reads and drafts, refused by everything that moves
             money or decides
"""

from dataclasses import dataclass

from fastapi import Depends, Header
from sqlalchemy.orm import Session

from ominira import lines
from ominira.database import get_session
from ominira.problems import Problem
from ominira.tables import Customer, Token


@dataclass
class Caller:
    role: str
    customer: Customer
    token: str
    approver_id: str | None = None

    @property
    def lang(self) -> str:
        return self.customer.lang


def authenticated(authorization: str | None = Header(default=None),
                  db: Session = Depends(get_session)) -> Caller:
    scheme, _, token = (authorization or "").partition(" ")
    if scheme.lower() != "bearer" or not token.strip():
        raise Problem(401, "unauthorized", "Sign in first")
    row = db.get(Token, token.strip())
    if row is None:
        raise Problem(401, "unauthorized", "Unknown token")
    customer = db.get(Customer, row.customer_id)
    return Caller(role=row.role, customer=customer, token=row.token, approver_id=row.approver_id)


def _refuse(caller: Caller) -> Problem:
    return Problem(403, "forbidden", "Not allowed for this caller", lines.say(caller.lang, "only_customer"))


def reader(caller: Caller = Depends(authenticated)) -> Caller:
    """Customer or assistant: reads, lookups and drafts."""
    if caller.role not in ("customer", "assistant"):
        raise _refuse(caller)
    return caller


def customer(caller: Caller = Depends(authenticated)) -> Caller:
    """The customer's own phone only. The assistant is refused outright."""
    if caller.role != "customer":
        raise _refuse(caller)
    return caller


def approver(caller: Caller = Depends(authenticated)) -> Caller:
    if caller.role != "approver" or caller.approver_id is None:
        raise Problem(403, "forbidden", "Only an approver can do this")
    return caller
