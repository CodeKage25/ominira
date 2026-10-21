"""Conversations with the assistant. Text turns here; voice turns are in routes/speech.py."""

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from ominira import drafting
from ominira.assistant import run_turn
from ominira.database import get_session
from ominira.problems import Problem
from ominira.security import Caller, customer
from ominira.tables import Conversation

router = APIRouter(prefix="/v1")


def conversation_of(db: Session, caller: Caller, conversation_id: str) -> Conversation:
    conv = db.get(Conversation, conversation_id)
    if conv is None or conv.customer_id != caller.customer.id:
        raise Problem(404, "not_found", "No such conversation")
    return conv


def turn_view(result) -> dict:
    return {"heard": result.heard, "reply": result.reply, "say": result.reply, "engine": result.engine,
            "draft": drafting.view(result.draft) if result.draft else None}


@router.post("/conversations", status_code=201)
def new_conversation(caller: Caller = Depends(customer), db: Session = Depends(get_session)):
    conv = Conversation(id=drafting.new_id("cv_"), customer_id=caller.customer.id)
    db.add(conv)
    db.commit()
    return {"id": conv.id}


class TurnIn(BaseModel):
    text: str = Field(min_length=1, max_length=500)


@router.post("/conversations/{conversation_id}/turns")
def new_turn(conversation_id: str, body: TurnIn, caller: Caller = Depends(customer),
             db: Session = Depends(get_session)):
    conv = conversation_of(db, caller, conversation_id)
    return turn_view(run_turn(db, caller.customer, conv, body.text, source="assistant"))
