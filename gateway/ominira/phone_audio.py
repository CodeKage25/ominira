"""Phone audio for the live call: Twilio sends and takes 8 kHz μ-law. Pure Python, so the image needs no ffmpeg."""

import io
import math
import sys
import wave
from array import array

RATE = 8000
FRAME = 160  # 20 ms, as Twilio sends it

_BIAS = 0x84
_CLIP = 32635


def _encode_one(sample: int) -> int:
    sign = 0x80 if sample < 0 else 0
    if sign:
        sample = -sample
    sample = min(sample, _CLIP) + _BIAS
    exponent = max(0, (sample >> 7).bit_length() - 1)
    mantissa = (sample >> (exponent + 3)) & 0x0F
    return ~(sign | (exponent << 4) | mantissa) & 0xFF


def _decode_one(byte: int) -> int:
    byte = ~byte & 0xFF
    exponent = (byte >> 4) & 0x07
    sample = ((((byte & 0x0F) << 3) + _BIAS) << exponent) - _BIAS
    return -sample if byte & 0x80 else sample


_ENCODE = bytes(_encode_one(s - 65536 if s >= 32768 else s) for s in range(65536))
_DECODE = [_decode_one(b) for b in range(256)]


def _samples(pcm16: bytes) -> array:
    a = array("h")
    a.frombytes(pcm16)
    if sys.byteorder == "big":
        a.byteswap()
    return a


def _pcm(samples) -> bytes:
    a = array("h", samples)
    if sys.byteorder == "big":
        a.byteswap()
    return a.tobytes()


def encode(pcm16: bytes) -> bytes:
    return bytes(_ENCODE[s & 0xFFFF] for s in _samples(pcm16))


def decode(ulaw: bytes) -> list[int]:
    return [_DECODE[b] for b in ulaw]


def level(ulaw: bytes) -> float:
    """Root mean square of a frame, on the 16-bit scale."""
    if not ulaw:
        return 0.0
    return math.sqrt(sum(_DECODE[b] ** 2 for b in ulaw) / len(ulaw))


def wav_to_ulaw(wav: bytes) -> bytes:
    """Any mono or stereo 16-bit WAV, resampled to 8 kHz."""
    with wave.open(io.BytesIO(wav), "rb") as w:
        rate, width, channels = w.getframerate(), w.getsampwidth(), w.getnchannels()
        raw = w.readframes(w.getnframes())
    if width != 2:
        raise ValueError("16-bit WAV only")
    s = _samples(raw)
    if channels > 1:
        s = array("h", (sum(s[i:i + channels]) // channels for i in range(0, len(s), channels)))
    if rate != RATE:
        step = rate / RATE
        n = int(len(s) / step)
        # Averaging the samples each output sample stands for keeps the hiss of plain decimation out.
        out = []
        for i in range(n):
            a, b = int(i * step), max(int((i + 1) * step), int(i * step) + 1)
            chunk = s[a:b]
            out.append(sum(chunk) // len(chunk))
        s = array("h", out)
    return encode(_pcm(s))


def ulaw_to_wav(ulaw: bytes, rate: int = 16000) -> bytes:
    """What the caller said, as a WAV for the speech recogniser, upsampled from 8 kHz."""
    s = decode(ulaw)
    if rate == 2 * RATE:
        up = []
        for i, v in enumerate(s):
            nxt = s[i + 1] if i + 1 < len(s) else v
            up += (v, (v + nxt) // 2)
        s = up
    out = io.BytesIO()
    with wave.open(out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate if rate == 2 * RATE else RATE)
        w.writeframes(_pcm(s))
    return out.getvalue()


def tone(freq: float, ms: int, volume: float = 0.3) -> bytes:
    n = RATE * ms // 1000
    fade = min(40, n // 4)  # no click at either end
    samples = []
    for i in range(n):
        env = min(1.0, i / fade, (n - i) / fade) if fade else 1.0
        samples.append(int(32767 * volume * env * math.sin(2 * math.pi * freq * i / RATE)))
    return encode(_pcm(samples))


def silence(ms: int) -> bytes:
    return b"\xff" * (RATE * ms // 1000)


# The Pulse PIN's count: a plain tick for one to nine, a high double tick for zero, as in the app's earphones.
TICK = tone(784, 80, 0.25)
ZERO = tone(1175, 90, 0.3) + silence(60) + tone(1175, 90, 0.3)
GOT_IT = tone(660, 70, 0.25) + silence(30) + tone(990, 90, 0.25)
THINKING = tone(440, 50, 0.08)


class Listener:
    """Finds where the caller starts and stops speaking, frame by frame, against the line's own noise."""

    def __init__(self, end_silence_ms: int = 700, max_ms: int = 12_000):
        self.floor = 300.0
        self.end_frames = end_silence_ms // 20
        self.max_frames = max_ms // 20
        self.voiced_run = 0
        self.quiet_run = 0
        self.speaking = False
        self.buffer = bytearray()
        self.lead = bytearray()  # the moment before speech was noticed, so the first syllable is kept

    def threshold(self) -> float:
        return max(700.0, self.floor * 3.0)

    def feed(self, frame: bytes) -> tuple[bool, bytes | None]:
        """(speech just began, a finished utterance or None)."""
        loud = level(frame)
        voiced = loud > self.threshold()
        if not voiced and not self.speaking:
            self.floor = 0.95 * self.floor + 0.05 * loud
        began = False
        if not self.speaking:
            self.lead += frame
            del self.lead[:-FRAME * 15]
            self.voiced_run = self.voiced_run + 1 if voiced else 0
            if self.voiced_run >= 3:
                self.speaking, began = True, True
                self.buffer = bytearray(self.lead)
                self.quiet_run = 0
            return began, None
        self.buffer += frame
        self.quiet_run = 0 if voiced else self.quiet_run + 1
        if self.quiet_run >= self.end_frames or len(self.buffer) >= FRAME * self.max_frames:
            said = bytes(self.buffer)
            self.speaking, self.voiced_run, self.buffer = False, 0, bytearray()
            return False, said
        return False, None
