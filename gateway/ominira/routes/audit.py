"""The audit log: the customer's own entries, and a check of the whole chain."""

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from ominira import audit_log
from ominira.database import get_session
from ominira.security import Caller, authenticated, customer

router = APIRouter(prefix="/v1/audit")


@router.get("")
def mine(limit: int = Query(50, ge=1, le=200), caller: Caller = Depends(customer), db: Session = Depends(get_session)):
    return audit_log.for_customer(db, caller.customer.id, limit)


@router.get("/verify")
def verify(_: Caller = Depends(authenticated), db: Session = Depends(get_session)):
    return audit_log.verify(db)
