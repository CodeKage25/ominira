"""The phone line: any phone, no app. Twilio hands each call here; the same assistant drafts and the same
confirm gate moves money, so the readback, PIN, limits, scam pause and co-approver all hold on a call.

Every request must carry Twilio's signature. A call's state lives in this process for the few minutes of
the call: the service runs as one instance."""

import base64
import hashlib
import hmac
import logging
import time
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import parse_qsl
from xml.sax.saxutils import escape

import httpx
from fastapi import APIRouter, Request
from fastapi.responses import FileResponse, Response
from sqlalchemy import select

from ominira import drafting, gate, lines
from ominira.assistant import run_turn
from ominira.core import get_core
from ominira.database import SessionFactory
from ominira.problems import Problem
from ominira.security import Caller
from ominira.settings import settings
from ominira.speech import yarngpt
from ominira.tables import Conversation, Customer, Token

log = logging.getLogger("ominira.phone")
router = APIRouter(prefix="/phone")

LANGS = ("en", "pcm", "yo", "ha", "ig")
CLIPS = Path(__file__).resolve().parents[1] / "static" / "phone"
# English and Pidgin use Twilio's voice, which answers at once; the others are YarnGPT's.
TWILIO_VOICES = ("en", "pcm")
CALL_TTL_S = 3600


@dataclass
class Call:
    lang: str = "en"
    customer_id: str | None = None
    conversation_id: str | None = None
    draft_id: str | None = None
    readback_hash: str | None = None
    misses: int = 0
    started: float = field(default_factory=time.monotonic)


_calls: dict[str, Call] = {}


def _call(sid: str) -> Call:
    now = time.monotonic()
    for old in [k for k, c in _calls.items() if now - c.started > CALL_TTL_S]:
        _calls.pop(old, None)
    return _calls.setdefault(sid, Call())


# ---- Twilio's side --------------------------------------------------------------------------------

def _signature(url: str, form: dict[str, str]) -> str:
    data = url + "".join(k + form[k] for k in sorted(form))
    digest = hmac.new(settings.twilio_auth_token.encode(), data.encode(), hashlib.sha1).digest()
    return base64.b64encode(digest).decode()


async def _form(request: Request) -> dict[str, str]:
    """The call's form fields, or 403 unless Twilio signed them."""
    form = dict(parse_qsl((await request.body()).decode(), keep_blank_values=True))
    sent = request.headers.get("X-Twilio-Signature", "")
    if not settings.twilio_auth_token or not hmac.compare_digest(sent, _signature(str(request.url), form)):
        raise Problem(403, "forbidden", "Not signed by Twilio")
    return form


def _twiml(*parts: str) -> Response:
    return Response('<?xml version="1.0" encoding="UTF-8"?><Response>' + "".join(parts) + "</Response>",
                    media_type="application/xml")


def _base(request: Request) -> str:
    return f"{request.url.scheme}://{request.url.netloc}"


def _voice_text(text: str) -> str:
    for word, spelled in (("NIN", "N.I.N."), ("BVN", "B.V.N.")):
        text = text.replace(word, spelled)
    return text


def _say(request: Request, lang: str, text: str) -> str:
    """Speech in the caller's language: a bundled clip, else YarnGPT live, else Twilio's English voice."""
    if lang not in TWILIO_VOICES:
        key = hashlib.sha256(text.strip().encode()).hexdigest()[:16]
        if (CLIPS / lang / f"{key}.mp3").exists():
            return f"<Play>{escape(_base(request))}/phone/clips/{lang}/{key}.mp3</Play>"
        if settings.speech_enabled:
            try:
                return f"<Play>{escape(yarngpt.prepare(_voice_text(text), lang)['audio_url'])}</Play>"
            except yarngpt.SpeechUnavailable as err:
                log.warning("phone speech fell back: %s", err)
    return f'<Say voice="Polly.Amy-Neural" language="en-GB">{escape(_voice_text(text))}</Say>'


def _line(request: Request, lang: str, key: str, **values) -> str:
    return _say(request, lang, lines.say(lang, key, **values))


# ---- the call ---------------------------------------------------------------------------------------

@router.post("/voice")
async def voice(request: Request):
    """A new call. Live: the call's audio streams to /phone/stream. Otherwise the five languages, by keypad."""
    form = await _form(request)
    if settings.phone_stream:
        from ominira.routes.phone_stream import ticket_for
        url = f"wss://{request.url.netloc}/phone/stream"
        ticket = ticket_for(form.get("From", ""))
        return _twiml(f'<Connect><Stream url="{escape(url)}"><Parameter name="ticket" value="{ticket}"/></Stream></Connect>')
    menu = "".join(_line(request, lang, f"phone_menu_{lang}") for lang in LANGS)
    return _twiml(f'<Gather numDigits="1" action="/phone/language" timeout="8">{menu}</Gather>',
                  '<Redirect method="POST">/phone/voice</Redirect>')


def _ask_account(request: Request, call: Call, lead: str = "") -> Response:
    if call.misses > 3:
        return _twiml(_line(request, call.lang, "phone_bye"), "<Hangup/>")
    prompt = lead + _line(request, call.lang, "phone_account")
    return _twiml(f'<Gather numDigits="10" action="/phone/account" timeout="20">{prompt}</Gather>',
                  '<Redirect method="POST">/phone/account</Redirect>')


@router.post("/language")
async def language(request: Request):
    form = await _form(request)
    call = _call(form["CallSid"])
    digit = form.get("Digits", "")
    if digit not in ("1", "2", "3", "4", "5"):
        return _twiml('<Redirect method="POST">/phone/voice</Redirect>')
    call.lang = LANGS[int(digit) - 1]
    return _ask_account(request, call)


@router.post("/account")
async def account(request: Request):
    form = await _form(request)
    call = _call(form["CallSid"])
    number = form.get("Digits", "")
    core_ref = get_core().customer_for_account(number) if len(number) == 10 else None
    with SessionFactory() as db:
        customer = db.scalars(select(Customer).where(Customer.core_ref == core_ref)).first() if core_ref else None
        if customer is None:
            call.misses += 1
            return _ask_account(request, call, _line(request, call.lang, "phone_no_account"))
        # The language chosen on the call is the one the bank answers in, here and in the app.
        customer.lang = call.lang
        conv = Conversation(id=drafting.new_id("cv_"), customer_id=customer.id)
        db.add(conv)
        db.commit()
        call.customer_id, call.conversation_id, call.misses = customer.id, conv.id, 0
        first = get_core().profile(customer.core_ref).first_name
    return _ask(request, call, _line(request, call.lang, "phone_hello", first=first))


def _ask(request: Request, call: Call, prompt: str) -> Response:
    """Listen for a request: Twilio hears English and Pidgin; YarnGPT hears the other three."""
    if call.lang in TWILIO_VOICES:
        return _twiml(f'<Gather input="speech" language="en-NG" speechTimeout="auto" timeout="6" '
                      f'action="/phone/heard">{prompt}</Gather>',
                      '<Redirect method="POST">/phone/heard</Redirect>')
    hint = _line(request, call.lang, "phone_record_hint")
    return _twiml(prompt, hint, '<Record action="/phone/recorded" maxLength="12" timeout="3" playBeep="true" '
                                'finishOnKey="#" trim="trim-silence"/>')


@router.post("/heard")
async def heard(request: Request):
    form = await _form(request)
    return _request(request, _call(form["CallSid"]), form.get("SpeechResult", "").strip())


@router.post("/recorded")
async def recorded(request: Request):
    form = await _form(request)
    call = _call(form["CallSid"])
    text = ""
    url = form.get("RecordingUrl", "")
    if url:
        audio = b""
        for _ in range(3):  # the recording can take a moment to be ready
            r = httpx.get(url + ".wav", auth=(settings.twilio_account_sid, settings.twilio_auth_token), timeout=20)
            if r.status_code == 200:
                audio = r.content
                break
            time.sleep(1)
        if audio:
            try:
                text = yarngpt.transcribe(audio, "audio/wav")["text"]
            except yarngpt.SpeechUnavailable as err:
                log.warning("phone listening failed: %s", err)
    return _request(request, call, text)


def _request(request: Request, call: Call, text: str) -> Response:
    if call.customer_id is None:
        return _twiml('<Redirect method="POST">/phone/voice</Redirect>')
    if not text:
        call.misses += 1
        if call.misses > 2:
            return _twiml(_line(request, call.lang, "phone_bye"), "<Hangup/>")
        return _ask(request, call, _line(request, call.lang, "phone_not_heard"))
    call.misses = 0
    with SessionFactory() as db:
        customer = db.get(Customer, call.customer_id)
        conv = db.get(Conversation, call.conversation_id)
        result = run_turn(db, customer, conv, text, source="phone")
        if result.draft is None:
            return _ask(request, call, _say(request, call.lang, result.reply) + _line(request, call.lang, "phone_more"))
        view = drafting.view(result.draft)
    call.draft_id, call.readback_hash = view["id"], view["readback_hash"]
    readback = "".join(_say(request, call.lang, line) for line in view["readback"].get("spoken", []))
    keys = _line(request, call.lang, "phone_confirm")
    return _twiml(f'<Gather numDigits="1" action="/phone/choice" timeout="10">{readback}{keys}</Gather>',
                  '<Redirect method="POST">/phone/choice</Redirect>')


def _caller(db, call: Call) -> Caller:
    customer = db.get(Customer, call.customer_id)
    token = db.scalars(select(Token).where(Token.customer_id == customer.id, Token.role == "customer")).first()
    return Caller(role="customer", customer=customer, token=token.token)


def _ask_pin(request: Request, call: Call, lead: str = "") -> Response:
    prompt = lead + _line(request, call.lang, "phone_pin")
    return _twiml(f'<Gather numDigits="4" finishOnKey="#" action="/phone/pin" timeout="20">{prompt}</Gather>',
                  '<Redirect method="POST">/phone/choice</Redirect>')


def _after(request: Request, call: Call, say: str | None) -> Response:
    """Back to listening once the gate has answered."""
    call.draft_id = call.readback_hash = None
    lead = _say(request, call.lang, say) if say else ""
    return _ask(request, call, lead + _line(request, call.lang, "phone_more"))


@router.post("/choice")
async def choice(request: Request):
    form = await _form(request)
    call = _call(form["CallSid"])
    if call.draft_id is None:
        return _ask(request, call, _line(request, call.lang, "phone_more"))
    digit = form.get("Digits", "")
    if digit == "1":
        return _ask_pin(request, call)
    if digit == "2":
        with SessionFactory() as db:
            try:
                answer = gate.cancel(db, _caller(db, call), call.draft_id)
                return _after(request, call, answer.body.get("say"))
            except Problem as problem:
                return _after(request, call, problem.say)
    return _twiml(f'<Gather numDigits="1" action="/phone/choice" timeout="10">'
                  f'{_line(request, call.lang, "phone_confirm")}</Gather>',
                  '<Redirect method="POST">/phone/choice</Redirect>')


def _seal(digits: str) -> str:
    """The mock bank's placeholder PIN block, made at once. On a real line the PIN goes to Zenith's HSM."""
    return "demo-" + "".join(str(9 - int(d)) for d in digits)


@router.post("/pin")
async def pin(request: Request):
    form = await _form(request)
    call = _call(form["CallSid"])
    digits = form.get("Digits", "")
    if call.draft_id is None:
        return _ask(request, call, _line(request, call.lang, "phone_more"))
    if len(digits) != 4 or not digits.isdigit():
        return _ask_pin(request, call)
    block = _seal(digits)
    with SessionFactory() as db:
        try:
            # A phone call is always a call: the scam pause applies to every large transfer to someone new.
            answer = gate.confirm(db, _caller(db, call), call.draft_id, call.readback_hash, block, call_active=True)
            return _after(request, call, answer.body.get("say"))
        except Problem as problem:
            if problem.code == "pause_required":
                keys = _line(request, call.lang, "phone_pause_keys")
                return _twiml(f'<Gather numDigits="1" action="/phone/pause" timeout="15">'
                              f'{_say(request, call.lang, problem.say or "")}{keys}</Gather>',
                              '<Redirect method="POST">/phone/pause</Redirect>')
            if problem.code == "pin_incorrect" and problem.extra.get("tries_left", 0) > 0:
                return _ask_pin(request, call, _say(request, call.lang, problem.say or ""))
            return _after(request, call, problem.say)


@router.post("/pause")
async def pause(request: Request):
    form = await _form(request)
    call = _call(form["CallSid"])
    digit = form.get("Digits", "")
    if call.draft_id is None:
        return _ask(request, call, _line(request, call.lang, "phone_more"))
    if digit not in ("1", "2"):
        return _twiml(f'<Gather numDigits="1" action="/phone/pause" timeout="15">'
                      f'{_line(request, call.lang, "phone_pause_keys")}</Gather>',
                      '<Redirect method="POST">/phone/pause</Redirect>')
    with SessionFactory() as db:
        try:
            answer = gate.answer_pause(db, _caller(db, call), call.draft_id, "stop" if digit == "1" else "chose_myself")
        except Problem as problem:
            return _after(request, call, problem.say)
    if digit == "1":
        return _after(request, call, answer.body.get("say"))
    # Carrying on needs the PIN again, as in the app.
    return _ask_pin(request, call, _say(request, call.lang, answer.body.get("say", "")))


@router.get("/clips/{lang}/{name}")
def clip(lang: str, name: str):
    path = CLIPS / lang / name
    if lang not in LANGS or not name.endswith(".mp3") or "/" in name or "\\" in name or not path.is_file():
        raise Problem(404, "not_found", "No such clip")
    return FileResponse(path, media_type="audio/mpeg")
