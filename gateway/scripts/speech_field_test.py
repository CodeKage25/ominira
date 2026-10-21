"""Field test for YarnGPT speech, run on a laptop with internet before relying on it in a demo.

    set -a; . ../.env; set +a                      # YARNGPT_API_KEY, and YARNGPT_VOICES if pinned
    uv run python scripts/speech_field_test.py                       # all five languages
    uv run python scripts/speech_field_test.py --only yo ha          # some of them
    uv run python scripts/speech_field_test.py --voice yo=idera      # try a voice for one language
    uv run python scripts/speech_field_test.py --listen me.wav       # transcribe your own recordings
    uv run python scripts/speech_field_test.py --gateway http://localhost:8080   # through the Gateway too

What it does, timing each step:
  1. lists the voices and the languages transcription covers;
  2. for each language, prepares a ticket exactly as the Gateway does, downloads the audio from the
     stream URL exactly as the phone does, saves it to speech_out/<lang>.mp3, and transcribes it back;
  3. with --gateway, calls /v1/speech/status, /say and /listen on a running Gateway.

Every call spends YarnGPT credit; a full run is about fifteen calls. Judge the voices with a native
speaker of each language, not by ear alone.
"""

import argparse
import os
import sys
import time
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from ominira.settings import settings  # noqa: E402
from ominira.speech import yarngpt  # noqa: E402

SAMPLES = {
    "en": "Twenty thousand naira. To Adebayo Tolulope. Zenith Bank, ending four, four, seven, one. Pulse your PIN to send.",
    "pcm": "Twenty thousand naira. To Adebayo Tolulope. Zenith Bank, wey end with four, four, seven, one. Pulse your PIN make e go.",
    "yo": "Ẹ káàbọ̀. Kí ni kí n ṣe fún yín? Kí ni ẹ fẹ́ ṣe?",
    "ha": "Sannu. Me zan yi muku? Me kuke so ku yi?",
    "ig": "Nnọọ. Gịnị ka m ga-emere gị? Gịnị ka ị chọrọ ime?",
}
MEDIA = {".wav": "audio/wav", ".mp3": "audio/mpeg", ".m4a": "audio/mp4", ".ogg": "audio/ogg",
         ".webm": "audio/webm", ".flac": "audio/flac", ".aac": "audio/aac"}
OUT = Path("speech_out")


def step(title: str) -> None:
    print(f"\n{title}")


def show_catalogue() -> None:
    step("1. Voices")
    started = time.monotonic()
    try:
        for v in yarngpt.voices():
            print("  ", {k: v[k] for k in list(v)[:5]})
        print(f"   ({time.monotonic() - started:.2f} s)")
    except yarngpt.SpeechUnavailable as err:
        sys.exit(f"   FAILED: {err}")

    step("   Languages transcription covers")
    with httpx.Client(base_url=settings.yarngpt_base, timeout=20,
                      headers={"Authorization": f"Bearer {settings.yarngpt_api_key}"}) as c:
        for _ in range(2):
            r = c.get("/api/v1/asr/languages")
            if r.status_code != 503:
                break
            time.sleep(2)
        print("  ", r.text[:500] if r.status_code < 400 else f"{r.status_code} {r.text[:200]}")


def round_trip(lang: str) -> None:
    text = SAMPLES[lang]
    t0 = time.monotonic()
    try:
        ticket = yarngpt.prepare(text, lang)
    except yarngpt.SpeechUnavailable as err:
        print(f"   {lang:3} prepare FAILED: {err}")
        return
    t1 = time.monotonic()
    first_byte = None
    audio = bytearray()
    with httpx.Client(timeout=60) as c, c.stream("GET", ticket["audio_url"]) as r:  # no key: the ticket is the credential
        if r.status_code >= 400:
            r.read()
            print(f"   {lang:3} stream FAILED: {r.status_code} {r.text[:200]}")
            return
        for chunk in r.iter_bytes():
            if first_byte is None:
                first_byte = time.monotonic() - t1
            audio += chunk
    t2 = time.monotonic()
    path = OUT / f"{lang}.mp3"
    path.write_bytes(bytes(audio))
    try:
        heard = yarngpt.transcribe(bytes(audio), "audio/mpeg", wait_s=60)
    except yarngpt.SpeechUnavailable as err:
        print(f"   {lang:3} transcribe FAILED: {err}")
        return
    print(f"   {lang:3} voice={ticket['voice'] or 'default'}  ticket {t1 - t0:.2f} s, first audio {first_byte or 0:.2f} s, "
          f"all audio {t2 - t1:.2f} s, transcript {heard['seconds']:.2f} s  -> {path}")
    print(f"       said:  {text}")
    print(f"       heard: {heard['text']}")


def listen_files(files: list[str]) -> None:
    step("3. Your recordings")
    for name in files:
        p = Path(name)
        try:
            heard = yarngpt.transcribe(p.read_bytes(), MEDIA.get(p.suffix.lower(), "audio/wav"), wait_s=60)
            print(f"   {p.name}: {heard['text']!r} ({heard['seconds']:.2f} s)")
        except (OSError, yarngpt.SpeechUnavailable) as err:
            print(f"   {p.name} FAILED: {err}")


def through_gateway(base: str, files: list[str]) -> None:
    step(f"4. Through the Gateway at {base}")
    auth = {"Authorization": "Bearer demo-customer"}
    with httpx.Client(base_url=base.rstrip("/"), timeout=60, headers=auth) as c:
        print("   status:", c.get("/v1/speech/status").json())
        t0 = time.monotonic()
        r = c.post("/v1/speech/say", json={"text": "Sent. Tolu has it.", "lang": "en"})
        print(f"   say: {r.status_code} {r.json()} ({time.monotonic() - t0:.2f} s)")
        sample = Path(files[0]) if files else OUT / "en.mp3"
        if sample.exists():
            t0 = time.monotonic()
            r = c.post("/v1/speech/listen", content=sample.read_bytes(),
                       headers={"Content-Type": MEDIA.get(sample.suffix.lower(), "audio/wav")})
            print(f"   listen ({sample.name}): {r.status_code} {r.json()} ({time.monotonic() - t0:.2f} s)")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--only", nargs="*", default=list(SAMPLES), choices=list(SAMPLES))
    ap.add_argument("--voice", action="append", default=[], help="pin a voice for this run, e.g. yo=idera")
    ap.add_argument("--listen", nargs="*", default=[], help="your own recordings to transcribe")
    ap.add_argument("--gateway", help="also test a running Gateway, e.g. http://localhost:8080")
    args = ap.parse_args()

    if not os.environ.get("YARNGPT_API_KEY"):
        sys.exit("Set YARNGPT_API_KEY first.")
    if args.voice:
        settings.yarngpt_voices = ",".join([settings.yarngpt_voices, *args.voice]).strip(",")
    OUT.mkdir(exist_ok=True)

    show_catalogue()
    step("2. Speak each language the way the phone hears it, then transcribe it back")
    for lang in args.only:
        round_trip(lang)
    if args.listen:
        listen_files(args.listen)
    if args.gateway:
        through_gateway(args.gateway, args.listen)
    print(f"\nPlay the files in {OUT}/ to a native speaker of each language before deciding.")


if __name__ == "__main__":
    main()
