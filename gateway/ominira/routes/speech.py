"""Speech through YarnGPT. Recordings arrive as the raw request body with an audio Content-Type
(audio/wav from the app). Audio is never stored."""

import time
from typing import Literal

from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session
from starlette.concurrency import run_in_threadpool

from ominira import lines, ratelimit
from ominira.assistant import guard, run_turn
from ominira.database import get_session
from ominira.problems import Problem
from ominira.routes.assistant import conversation_of, turn_view
from ominira.security import Caller, authenticated, customer
from ominira.speech import yarngpt

router = APIRouter(prefix="/v1")

MIN_AUDIO_BYTES = 1024
MAX_AUDIO_BYTES = 4 * 1024 * 1024  # about two minutes of 16 kHz mono WAV; a turn is a few seconds
SAY_PER_MINUTE = 90
LISTEN_PER_MINUTE = 30


def _unavailable(lang: str, err: yarngpt.SpeechUnavailable) -> Problem:
    return Problem(503, "speech_unavailable", "Cloud speech is not available", lines.say(lang, "speech_unavailable"),
                   provider_code=err.code)


async def _read_audio(request: Request) -> tuple[bytes, str]:
    content_type = request.headers.get("content-type", "").lower()
    if not content_type.startswith("audio/"):
        raise Problem(415, "audio_expected", "Send the recording as the body with an audio/* Content-Type")
    declared = request.headers.get("content-length")
    if declared and declared.isdigit() and int(declared) > MAX_AUDIO_BYTES:
        raise Problem(413, "audio_too_long", "Keep one recording under two minutes")
    audio = bytearray()
    async for chunk in request.stream():
        audio += chunk
        if len(audio) > MAX_AUDIO_BYTES:
            raise Problem(413, "audio_too_long", "Keep one recording under two minutes")
    if len(audio) < MIN_AUDIO_BYTES:
        raise Problem(422, "audio_too_short", "The recording is empty or too short")
    return bytes(audio), content_type


@router.get("/speech/status")
def status(_: Caller = Depends(authenticated)):
    on = yarngpt.enabled()
    return {"enabled": on, "provider": "yarngpt" if on else None}


class SayIn(BaseModel):
    text: str = Field(min_length=1, max_length=600)
    lang: Literal["en", "pcm", "yo", "ha", "ig"] | None = None


@router.post("/speech/say")
def say(body: SayIn, caller: Caller = Depends(customer)):
    ratelimit.allow(f"say:{caller.customer.id}", SAY_PER_MINUTE, "Too many speech requests")
    lang = body.lang or caller.lang
    try:
        return yarngpt.prepare(body.text, lang)
    except yarngpt.SpeechUnavailable as err:
        raise _unavailable(caller.lang, err) from err


@router.post("/speech/listen")
async def listen(request: Request, caller: Caller = Depends(customer)):
    """For short answers on a screen (yes, no, a language). Agent turns use voice-turns, so a
    spoken account number never travels back to the phone."""
    audio, content_type = await _read_audio(request)
    ratelimit.allow(f"listen:{caller.customer.id}", LISTEN_PER_MINUTE, "Too many speech requests")
    try:
        heard = await run_in_threadpool(yarngpt.transcribe, audio, content_type)
    except yarngpt.SpeechUnavailable as err:
        raise _unavailable(caller.lang, err) from err
    if guard.spoken_pin(heard["text"]):
        return {"text": "", "pin_spoken": True, "seconds": heard["seconds"], "say": lines.say(caller.lang, "pin_spoken")}
    # Not masked: this goes back to the customer's own phone, which may be reading a BVN or an
    # account number off the customer's voice on purpose.
    return {"text": heard["text"], "seconds": heard["seconds"],
            "say": None if heard["text"] else lines.say(caller.lang, "speech_not_heard")}


@router.post("/conversations/{conversation_id}/voice-turns")
async def voice_turn(conversation_id: str, request: Request, caller: Caller = Depends(customer),
                     db: Session = Depends(get_session)):
    """Listen, run the same turn as text (guard, assistant, draft), then speak the reply."""
    conv = conversation_of(db, caller, conversation_id)
    audio, content_type = await _read_audio(request)
    ratelimit.allow(f"listen:{caller.customer.id}", LISTEN_PER_MINUTE, "Too many speech requests")
    t0 = time.monotonic()
    try:
        heard = await run_in_threadpool(yarngpt.transcribe, audio, content_type)
    except yarngpt.SpeechUnavailable as err:
        raise _unavailable(caller.lang, err) from err
    t1 = time.monotonic()
    if not heard["text"]:
        line = lines.say(caller.lang, "speech_not_heard")
        return {"heard": "", "reply": line, "say": line, "engine": "rules", "draft": None, "reply_audio_url": None,
                "timings": {"listen_s": round(t1 - t0, 2), "turn_s": 0.0, "speak_s": 0.0}}
    result = await run_in_threadpool(run_turn, db, caller.customer, conv, heard["text"], "voice")
    t2 = time.monotonic()
    audio_url = None
    try:
        audio_url = (await run_in_threadpool(yarngpt.prepare, result.reply, caller.lang))["audio_url"]
    except yarngpt.SpeechUnavailable:
        pass  # the app speaks the reply with the phone's own voice
    t3 = time.monotonic()
    return turn_view(result) | {"reply_audio_url": audio_url,
                                "timings": {"listen_s": round(t1 - t0, 2), "turn_s": round(t2 - t1, 2),
                                            "speak_s": round(t3 - t2, 2)}}
