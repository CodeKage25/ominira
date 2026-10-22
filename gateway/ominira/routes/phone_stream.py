"""The live call: Twilio streams the call's audio here both ways, so the whole call is by voice. The caller's
words go to YarnGPT; the replies are bundled clips or YarnGPT's voice; the Pulse PIN is four key presses timed
against a count of ticks, so no digit is ever pressed or said. The same assistant and confirm gate as the app.

/phone/voice answers a call with a one-time ticket for this socket, so only a call Twilio signed gets in."""

import asyncio
import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import logging
import secrets
import shutil
import time
from collections import OrderedDict
from dataclasses import dataclass, field
from pathlib import Path

from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from sqlalchemy import select

from ominira import answers, drafting, gate, lines, phone_audio, phone_callers
from ominira.assistant import run_turn
from ominira.core import get_core
from ominira.database import SessionFactory
from ominira.problems import Problem
from ominira.security import Caller
from ominira.settings import settings
from ominira.speech import google_stt, yarngpt
from ominira.tables import Conversation, Customer, Token

log = logging.getLogger("ominira.phone")
router = APIRouter(prefix="/phone")

LANGS = ("en", "pcm", "yo", "ha", "ig")
CLIPS = Path(__file__).resolve().parents[1] / "static" / "phone"
TICKET_TTL_S = 60
HEAR_S = 8.0
# The call's voice for each language, for the bundled prompts and live lines alike, so a call keeps one voice.
# Jude reads English and Pidgin cleanly on a phone line; the others keep YarnGPT's choice.
VOICES = {"en": "jude", "pcm": "jude"}
DIGIT_PAUSE_S = 0.6  # between one digit's count and the next
DTMF_LAG_S = 0.8  # how long after a key press Twilio's report of it arrives, when no tone was heard
TYPED_WITHIN_S = 2.5  # after the first key: the other three of a typed PIN come within this
KEY_TONE_LEVEL = 2500.0  # a keypad tone is loud and sudden on the line

# Tests set this to learn when the call starts listening.
on_listen = None


@dataclass
class Ticket:
    caller: str
    made: float = field(default_factory=time.monotonic)


_tickets: dict[str, Ticket] = {}


def ticket_for(caller: str) -> str:
    now = time.monotonic()
    for old in [k for k, t in _tickets.items() if now - t.made > TICKET_TTL_S]:
        _tickets.pop(old, None)
    ticket = secrets.token_urlsafe(18)
    _tickets[ticket] = Ticket(caller)
    return ticket


def _take(ticket: str) -> Ticket | None:
    t = _tickets.pop(ticket, None)
    return t if t is not None and time.monotonic() - t.made <= TICKET_TTL_S else None


# ---- speech -------------------------------------------------------------------------------------

def clip_key(text: str) -> str:
    return hashlib.sha256(text.strip().encode()).hexdigest()[:16]


_live: "OrderedDict[tuple[str, str], bytes]" = OrderedDict()


def _voice_text(text: str) -> str:
    for word, spelled in (("NIN", "N.I.N."), ("BVN", "B.V.N.")):
        text = text.replace(word, spelled)
    return text


def voice(lang: str) -> str | None:
    return VOICES.get(lang) or yarngpt.voice_for(lang)


def has_clip(lang: str, text: str) -> bool:
    return (CLIPS / lang / f"{clip_key(text)}.ul").is_file()


def _fetch(said: str, lang: str) -> bytes:
    """MP3 when ffmpeg is here (about three times sooner), else WAV."""
    if shutil.which("ffmpeg"):
        return phone_audio.mp3_to_ulaw(yarngpt.download(
            yarngpt.prepare(said, lang, output_format="mp3", voice=voice(lang))["audio_url"]))
    return phone_audio.wav_to_ulaw(yarngpt.download(
        yarngpt.prepare(said, lang, output_format="wav", voice=voice(lang))["audio_url"]))


def speech(lang: str, text: str) -> bytes:
    """Phone audio for one line: a bundled clip, else YarnGPT live (the daily calls, then credits). Blocking."""
    path = CLIPS / lang / f"{clip_key(text)}.ul"
    if path.is_file():
        return path.read_bytes()
    cached = _live.get((lang, text))
    if cached is not None:
        _live.move_to_end((lang, text))
        return cached
    if not settings.speech_enabled:
        return b""
    said = _voice_text(text)
    audio = b""
    try:
        audio = _fetch(said, lang)
    except (yarngpt.SpeechUnavailable, ValueError) as err:
        log.warning("live speech on the prepare route failed: %s", err)
        try:
            fmt = "mp3" if shutil.which("ffmpeg") else "wav"
            done = yarngpt.render(said, voice(lang), clip_key(lang + said), output_format=fmt, wait_s=20)
            audio = phone_audio.mp3_to_ulaw(done) if fmt == "mp3" else phone_audio.wav_to_ulaw(done)
        except (yarngpt.SpeechUnavailable, ValueError) as err2:
            log.warning("live speech failed: %s", err2)
    if audio:
        _live[(lang, text)] = audio
        while len(_live) > 200:
            _live.popitem(last=False)
    return audio


def transcribe(said: bytes) -> str:
    try:
        return yarngpt.transcribe(phone_audio.ulaw_to_wav(said), "audio/wav")["text"]
    except yarngpt.SpeechUnavailable as err:
        log.warning("phone listening failed: %s", err)
        return ""


def _google(said: bytes, lang: str, model: str) -> str | None:
    try:
        return google_stt.recognize(said, lang, model)
    except google_stt.Unavailable as err:
        log.warning("Google speech unavailable: %s", err)
        return None


def transcribe_number(said: bytes) -> str:
    """The digits of a spoken account number. Google's two models at once, the first ten-digit answer winning;
    YarnGPT when neither gives ten. Blocking."""
    with ThreadPoolExecutor(len(google_stt.MODELS)) as pool:
        heard = list(pool.map(lambda m: _google(said, *m), google_stt.MODELS))
    found = [answers.digits(h) for h in heard if h]
    ten = [d for d in found if len(d) == 10]
    if ten:
        return ten[0]
    found.append(answers.digits(transcribe(said)))
    ten = [d for d in found if len(d) == 10]
    return ten[0] if ten else max(found, key=len, default="")


def start_digit() -> int:
    """Where each count starts: random, so the moment of a press tells a listener nothing."""
    return secrets.randbelow(10)


def seal(digits: list[int]) -> str:
    """The mock bank's placeholder PIN block, made at once. On a real line the PIN goes to Zenith's HSM."""
    return "demo-" + "".join(str(9 - d) for d in digits)


# ---- the line -----------------------------------------------------------------------------------

class Gone(Exception):
    """The caller hung up."""


class Line:
    def __init__(self, ws: WebSocket):
        self.ws = ws
        self.loop = asyncio.get_running_loop()
        self.sid = ""
        self.caller = ""
        self.lang = "en"
        self.customer_id: str | None = None
        self.conversation_id: str | None = None
        self.pin_explained = False
        self.listener = phone_audio.Listener()
        self.utterances: asyncio.Queue[bytes] = asyncio.Queue()
        self.presses: asyncio.Queue[float] = asyncio.Queue()
        self.tones: list[float] = []  # when the last few loud sounds began on the caller's side
        self.was_loud = False
        self.typed: list[str] = []  # keypad digits, for an account number typed instead of said
        self.typed_ten = asyncio.Event()
        self.typed_more = asyncio.Event()
        self.began = asyncio.Event()
        self.started = asyncio.Event()
        self.closed = asyncio.Event()
        self.marks: dict[str, float] = {}
        self.waiting: dict[str, asyncio.Future] = {}
        self.sending = asyncio.Lock()
        self.count = 0
        self.speaking = False
        self.barge = False
        self.barged = False

    # -- Twilio's side --

    async def send(self, message: dict) -> None:
        if self.closed.is_set():
            raise Gone
        async with self.sending:
            try:
                await self.ws.send_text(json.dumps(message))
            except (WebSocketDisconnect, RuntimeError) as exc:
                self.closed.set()
                raise Gone from exc

    async def read(self) -> None:
        try:
            while True:
                msg = json.loads(await self.ws.receive_text())
                event = msg.get("event")
                if event == "start":
                    start = msg.get("start", {})
                    ticket = _take((start.get("customParameters") or {}).get("ticket", ""))
                    if ticket is None:
                        log.warning("a stream without a valid ticket was refused")
                        return
                    self.sid = msg.get("streamSid") or start.get("streamSid", "")
                    self.caller = ticket.caller
                    self.started.set()
                elif event == "media" and self.started.is_set():
                    media = msg.get("media", {})
                    if media.get("track", "inbound") != "inbound":
                        continue
                    began, said = self.listener.feed(base64.b64decode(media.get("payload", "")))
                    # A key's tone reaches here well before Twilio's report of the key: note when one starts.
                    loud = self.listener.last_level > KEY_TONE_LEVEL
                    if loud and not self.was_loud:
                        self.tones.append(self.loop.time())
                        del self.tones[:-8]
                    self.was_loud = loud
                    if began:
                        self.began.set()
                        if self.speaking and self.barge and not self.barged:
                            # The caller talked over the app: stop talking and listen.
                            self.barged = True
                            await self.send({"event": "clear", "streamSid": self.sid})
                    if said:
                        self.utterances.put_nowait(said)
                elif event == "dtmf":
                    self.presses.put_nowait(self.loop.time())
                    digit = str(msg.get("dtmf", {}).get("digit", ""))
                    if digit.isdigit():
                        self.typed.append(digit)
                        self.typed_more.set()
                        if len(self.typed) >= 10:
                            self.typed_ten.set()
                elif event == "mark":
                    name = msg.get("mark", {}).get("name", "")
                    self.marks[name] = self.loop.time()
                    waiter = self.waiting.pop(name, None)
                    if waiter is not None and not waiter.done():
                        waiter.set_result(True)
                elif event == "stop":
                    return
        except (WebSocketDisconnect, Gone, RuntimeError, json.JSONDecodeError):
            return
        finally:
            self.closed.set()
            for waiter in self.waiting.values():
                if not waiter.done():
                    waiter.set_result(False)

    async def race(self, awaitable, timeout: float | None = None):
        """Await something, or fail with Gone when the caller hangs up first."""
        task = asyncio.ensure_future(awaitable)
        gone = asyncio.ensure_future(self.closed.wait())
        try:
            done, _ = await asyncio.wait({task, gone}, timeout=timeout, return_when=asyncio.FIRST_COMPLETED)
        finally:
            gone.cancel()
        if task in done:
            return task.result()
        task.cancel()
        if gone in done:
            raise Gone
        raise TimeoutError

    async def mark(self, name: str | None = None) -> asyncio.Future:
        self.count += 1
        name = name or f"m{self.count}"
        waiter = self.loop.create_future()
        self.waiting[name] = waiter
        await self.send({"event": "mark", "streamSid": self.sid, "mark": {"name": name}})
        return waiter

    async def stream(self, audio: bytes) -> None:
        for i in range(0, len(audio), 3200):
            chunk = base64.b64encode(audio[i:i + 3200]).decode()
            await self.send({"event": "media", "streamSid": self.sid, "media": {"payload": chunk}})

    # -- speaking and listening --

    async def say(self, *texts: str, barge: bool = True) -> None:
        """Say lines in the caller's language, then wait until they have played. With barge, the caller may
        talk over them; without, anything said meanwhile is dropped."""
        # Lines with no clip go to YarnGPT together: one request is quicker, and a short line alone is refused.
        merged: list[str] = []
        live_last = False
        for t in (t.strip() for t in texts if t and t.strip()):
            live = not has_clip(self.lang, t)
            if live and live_last:
                merged[-1] += " " + t
            else:
                merged.append(t)
            live_last = live
        texts = tuple(merged)
        if not texts:
            return
        clips = await self.busy(asyncio.gather(*(asyncio.to_thread(speech, self.lang, t) for t in texts)))
        audio = phone_audio.silence(450).join(c for c in clips if c)
        if not audio:
            return
        self.speaking, self.barge, self.barged = True, barge, False
        if barge:
            self.forget_speech()
        try:
            await self.stream(audio)
            await self.race(await self.mark())
        finally:
            self.speaking = self.barge = False
        if not barge:
            self.forget_speech()

    def forget_speech(self) -> None:
        while not self.utterances.empty():
            self.utterances.get_nowait()
        if not self.listener.speaking:
            self.began.clear()

    async def line(self, key: str, **values) -> None:
        await self.say(lines.say(self.lang, key, **values))

    async def busy(self, awaitable):
        """Await something slow, with a soft tick every second so the caller knows the line is alive."""
        task = asyncio.ensure_future(awaitable)
        while True:
            try:
                return await self.race(asyncio.shield(task), 1.2)
            except TimeoutError:
                await self.stream(phone_audio.THINKING)

    async def hear_number(self) -> str:
        """An account number, said in groups with pauses between: longer pauses are allowed, and what is heard
        is added up until there are ten digits or the caller stops."""
        self.listener.end_frames = 1600 // 20
        self.typed.clear()
        self.typed_ten.clear()
        digits = ""
        try:
            for _ in range(4):
                said = await self.utterance(12 if not digits else 4, stop=self.typed_ten)
                if self.typed_ten.is_set():
                    return "".join(self.typed)[:10]
                if not said:
                    break
                digits += await self.busy(asyncio.to_thread(transcribe_number, said))
                if len(digits) >= 10:
                    break
        finally:
            self.listener.end_frames = phone_audio.Listener().end_frames
        log.info("account number on a call: %d digits heard", len(digits))  # how many, never which
        return digits[:10] if len(digits) >= 10 else digits

    async def hear(self, timeout: float = HEAR_S, stop: asyncio.Event | None = None) -> str:
        """What the caller says next, as text; empty when they said nothing, it could not be understood, or
        `stop` was set first."""
        said = await self.utterance(timeout, stop)
        return (await self.busy(asyncio.to_thread(transcribe, said))).strip() if said else ""

    async def utterance(self, timeout: float = HEAR_S, stop: asyncio.Event | None = None) -> bytes | None:
        """The audio of what the caller says next; None when they said nothing or `stop` was set first."""
        if on_listen is not None:
            on_listen()
        if self.utterances.empty() and not self.listener.speaking:
            waits = [asyncio.ensure_future(self.began.wait())]
            if stop is not None:
                waits.append(asyncio.ensure_future(stop.wait()))
            try:
                await self.race(asyncio.wait(waits, return_when=asyncio.FIRST_COMPLETED), timeout)
            except TimeoutError:
                return None
            finally:
                for w in waits:
                    w.cancel()
            if stop is not None and stop.is_set() or not self.began.is_set():
                return None
        try:
            said = await self.race(self.utterances.get(), 15)
        except TimeoutError:
            return None
        self.began.clear()
        return said

    # -- the Pulse PIN --

    async def pulse_digit(self, run: int) -> int | None:
        """One digit: ticks from a random start until a key is pressed. The digit is the tick that had begun
        playing when the press came, as the app counts a tap; which key was pressed does not matter."""
        while not self.presses.empty():
            self.presses.get_nowait()
        start, gap = start_digit(), settings.phone_pulse_gap_s
        ticks: list[tuple[str, int]] = []
        for i in range(31):
            # A press that came in late, after the last wait ended, is taken before another tick is played.
            if self.presses.empty() and i < 30:
                digit = (start + i) % 10
                name = f"t{run}-{i}"
                ticks.append((name, digit))
                await self.mark(name)  # comes back as this tick starts to play
                await self.stream(phone_audio.ZERO if digit == 0 else phone_audio.TICK)
            try:
                pressed = await self.race(self.presses.get(), gap)
            except TimeoutError:
                continue
            # Twilio reports a key a moment after it is pressed: count from when it was pressed, which is when
            # its tone began on the line if one was heard in the last 1.5 s, else an allowance for the delay.
            began = self.marks.get(ticks[0][0], pressed) if ticks else pressed
            tone = next((t for t in reversed(self.tones) if 0 <= pressed - t <= 1.5 and t >= began), None)
            when = tone if tone is not None else pressed - DTMF_LAG_S
            playing = [(n, d) for n, d in ticks if self.marks.get(n, float("inf")) <= when]
            if not playing:
                continue  # pressed before the count began
            log.info("Pulse PIN press reported %d ms after its tick began; its tone %s",
                     int((pressed - self.marks[playing[-1][0]]) * 1000),
                     f"{int((pressed - tone) * 1000)} ms before the report" if tone is not None else "not heard")
            await self.stream(phone_audio.GOT_IT)
            return playing[-1][1]
        return None

    async def typed_four(self, within: float) -> bool:
        """True once four keypad digits have come in, within `within` seconds of now."""
        deadline = self.loop.time() + within
        while len(self.typed) < 4:
            left = deadline - self.loop.time()
            if left <= 0:
                return False
            self.typed_more.clear()
            try:
                await self.race(self.typed_more.wait(), left)
            except TimeoutError:
                return False
        return True

    async def pulse_pin(self) -> str | None:
        await self.say(lines.say(self.lang, "phone_v_pin_short" if self.pin_explained else "phone_v_pin"),
                       barge=False)
        self.pin_explained = True
        self.typed.clear()
        digits: list[int] = []
        for run in range(4):
            digit = await self.pulse_digit(self.count * 10 + run)
            if digit is None:
                return None
            if run == 0 and settings.phone_typed_pin and await self.typed_four(TYPED_WITHIN_S):
                # Four keys in a moment: the PIN was typed, not pulsed.
                block = seal([int(d) for d in self.typed[:4]])
                self.typed.clear()
                return block
            digits.append(digit)
            await asyncio.sleep(DIGIT_PAUSE_S)
        block = seal(digits)
        digits.clear()
        return block

    # -- the call --

    async def run(self) -> None:
        reader = asyncio.ensure_future(self.read())
        try:
            await self.race(self.started.wait(), 15)
            await self.converse()
        except (Gone, TimeoutError):
            pass
        except Exception:
            log.exception("the live call failed")
        finally:
            reader.cancel()
            try:
                await self.ws.close()
            except RuntimeError:
                pass

    def known_caller(self) -> tuple[Customer, str] | None:
        customer = phone_callers.known(self.caller)
        return None if customer is None else (customer, get_core().profile(customer.core_ref).first_name)

    def start_conversation(self, customer_id: str) -> None:
        with SessionFactory() as db:
            conv = Conversation(id=drafting.new_id("cv_"), customer_id=customer_id)
            db.add(conv)
            db.commit()
            self.customer_id, self.conversation_id = customer_id, conv.id

    async def choose_language(self) -> bool:
        menu = [lines.say(lang, f"phone_v_menu_{lang}") for lang in LANGS]
        for _ in range(3):
            clips = await asyncio.gather(*(asyncio.to_thread(speech, lang, text) for lang, text in zip(LANGS, menu)))
            self.speaking, self.barge, self.barged = True, True, False
            self.forget_speech()
            try:
                await self.stream(phone_audio.silence(1100).join(c for c in clips if c))
                await self.race(await self.mark())
            finally:
                self.speaking = self.barge = False
            chosen = answers.language(await self.hear())
            if chosen:
                self.lang = chosen
                return True
        return False

    async def identify(self) -> bool:
        known = await asyncio.to_thread(self.known_caller)
        if known is not None:
            customer, first = known
            self.lang = customer.lang if customer.lang in LANGS else "en"
            await self.say(lines.say(self.lang, "phone_v_welcome"), barge=False)
            await asyncio.to_thread(self.start_conversation, customer.id)
            await self.say(lines.say(self.lang, "phone_v_hi", first=first), lines.say(self.lang, "phone_v_what"))
            return True
        if not await self.choose_language():
            return False
        await self.say(lines.say(self.lang, "phone_v_welcome"), barge=False)
        await self.line("phone_v_account")
        for _ in range(3):
            number = await self.hear_number()
            if len(number) == 10:
                return await self.prove(number)
            await self.say(lines.say(self.lang, "phone_no_account"), lines.say(self.lang, "phone_v_account"))
        return False

    async def prove(self, number: str) -> bool:
        """A stranger's account: nothing about it is said or changed until its Pulse PIN passes on this call."""
        found = None
        if await asyncio.to_thread(phone_callers.may_try, self.caller):
            for _ in range(2):
                block = await self.pulse_pin()
                if block is not None:
                    found = await self.busy(asyncio.to_thread(phone_callers.prove, self.caller, number, block,
                                                              self.lang))
                    break
                await self.line("phone_not_heard")
        if found is None:
            await self.say(lines.say(self.lang, "phone_stranger"), barge=False)
            return False
        customer_id, first = found
        await asyncio.to_thread(self.start_conversation, customer_id)
        await self.say(lines.say(self.lang, "phone_v_hi", first=first), lines.say(self.lang, "phone_v_what"))
        return True

    async def converse(self) -> None:
        if not await self.identify():
            await self.say(lines.say(self.lang, "phone_bye"), barge=False)
            return
        misses = 0
        while True:
            text = await self.hear()
            if not text:
                misses += 1
                if misses > 2:
                    break
                await self.line("phone_v_still_there" if misses == 2 else "phone_not_heard")
                continue
            misses = 0
            if answers.is_bye(text) or (answers.is_no(text) and len(text.split()) <= 3):
                break
            reply, view = await self.busy(asyncio.to_thread(self.turn, text))
            if view is None:
                await self.say(reply, lines.say(self.lang, "phone_v_more"))
                continue
            await self.settle(view)
            await self.line("phone_v_more")
        await self.say(lines.say(self.lang, "phone_bye"), barge=False)

    def turn(self, text: str) -> tuple[str, dict | None]:
        with SessionFactory() as db:
            customer = db.get(Customer, self.customer_id)
            conv = db.get(Conversation, self.conversation_id)
            result = run_turn(db, customer, conv, text, source="phone")
            return result.reply, (drafting.view(result.draft) if result.draft is not None else None)

    def caller_for(self, db) -> Caller:
        customer = db.get(Customer, self.customer_id)
        token = db.scalars(select(Token).where(Token.customer_id == customer.id, Token.role == "customer")).first()
        return Caller(role="customer", customer=customer, token=token.token)

    def gate_call(self, fn, *args, **kwargs) -> tuple[str, str | None, dict]:
        """(what to say, the problem's code or None, the problem's extra)."""
        with SessionFactory() as db:
            try:
                return fn(db, self.caller_for(db), *args, **kwargs).body.get("say") or "", None, {}
            except Problem as problem:
                return problem.say or lines.say(self.lang, "failed"), problem.code, dict(problem.extra)

    async def settle(self, view: dict) -> None:
        """Readback, yes or no, then the Pulse PIN and the gate."""
        draft_id, readback_hash = view["id"], view["readback_hash"]
        await self.say(*view["readback"].get("spoken", []), barge=False)
        agreed = False
        for _ in range(3):
            await self.line("phone_v_confirm")
            said = await self.hear()
            if answers.is_no(said):
                break
            if answers.is_yes(said):
                agreed = True
                break
        if not agreed:
            await asyncio.to_thread(self.gate_call, gate.cancel, draft_id)
            await self.line("phone_v_cancelled")
            return
        await self.pin_and_send(draft_id, readback_hash)

    async def pin_and_send(self, draft_id: str, readback_hash: str, rounds: int = 3) -> None:
        for _ in range(rounds):
            block = await self.pulse_pin()
            if block is None:
                await self.line("phone_not_heard")
                continue
            # A phone call is always a call: the scam pause applies to every large transfer to someone new.
            say, code, extra = await self.busy(asyncio.to_thread(
                self.gate_call, gate.confirm, draft_id, readback_hash, block, call_active=True))
            if code == "pause_required":
                await self.say(say, barge=False)
                await self.pause(draft_id, readback_hash)
                return
            if code == "pin_incorrect" and extra.get("tries_left", 0) > 0:
                await self.say(say, barge=False)
                continue
            await self.say(say, barge=False)
            return
        await asyncio.to_thread(self.gate_call, gate.cancel, draft_id)
        await self.line("phone_v_cancelled")

    async def pause(self, draft_id: str, readback_hash: str) -> None:
        for _ in range(3):
            await self.line("phone_v_pause")
            said = await self.hear()
            if not said:
                continue
            if answers.is_stop(said):
                say, _, _ = await asyncio.to_thread(self.gate_call, gate.answer_pause, draft_id, "stop")
                await self.say(say, barge=False)
                return
            if answers.is_no(said):
                say, code, _ = await asyncio.to_thread(self.gate_call, gate.answer_pause, draft_id, "chose_myself")
                await self.say(say, barge=False)
                if code is None:
                    # Carrying on needs the PIN again, as in the app.
                    await self.pin_and_send(draft_id, readback_hash, rounds=2)
                return
        say, _, _ = await asyncio.to_thread(self.gate_call, gate.answer_pause, draft_id, "stop")
        await self.say(say, barge=False)


@router.websocket("/stream")
async def stream(ws: WebSocket):
    await ws.accept()
    await Line(ws).run()
