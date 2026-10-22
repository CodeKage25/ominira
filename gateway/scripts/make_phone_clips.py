"""Make the phone line's fixed prompts, so a call only uses live YarnGPT for lines with a name or an amount.

    set -a; . ../.env; set +a
    uv run python scripts/make_phone_clips.py            # job route
    uv run python scripts/make_phone_clips.py --route prepare

Writes ominira/static/phone/<lang>/<key>.ul, 8 kHz μ-law for the live call, in all five languages, and
<key>.mp3 in Yorùbá, Hausa and Igbo for the step-by-step call (English and Pidgin there are Twilio's voice).
Needs ffmpeg. Existing clips are kept; an .ul is made from its .mp3 when there is one.
"""

import argparse
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import make_speech_clips as clips  # noqa: E402
from ominira import answers, lines, phone_audio  # noqa: E402
from ominira.routes.phone_stream import voice as call_voice  # noqa: E402
from ominira.settings import settings  # noqa: E402
from ominira.speech import yarngpt  # noqa: E402

OUT = Path(__file__).resolve().parents[1] / "ominira" / "static" / "phone"
LANGS = ("en", "pcm", "yo", "ha", "ig")
MP3_LANGS = ("yo", "ha", "ig")


def prompts(lang: str) -> set[str]:
    """Fixed lines only: one with {first} has a name in it. A menu line is said only in its own language."""
    out = set()
    for key in lines.keys():
        if not key.startswith("phone_") or "{" in lines._COPY[key].get(lang, lines._COPY[key]["en"]):
            continue
        if "_menu_" in key and not key.endswith("_" + lang):
            continue
        if key.startswith("phone_menu_") or key in ("phone_account", "phone_confirm", "phone_pin",
                                                    "phone_pause_keys", "phone_record_hint", "phone_hello"):
            # The keypad call's own lines: only its Yorùbá, Hausa and Igbo clips are needed.
            if lang not in MP3_LANGS:
                continue
        out.add(lines.say(lang, key))
    return out


def to_ulaw(mp3: bytes) -> bytes:
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / "in.mp3"
        src.write_bytes(mp3)
        done = subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(src), "-ar", "8000", "-ac", "1",
                               "-f", "mulaw", "-"], capture_output=True)
    if done.returncode != 0 or not done.stdout:
        raise RuntimeError(done.stderr.decode()[:200])
    return done.stdout


def heard_right(text: str, ul: bytes) -> bool:
    """Most of the line's words come back when the clip is transcribed: a garbled or repeating clip does not."""
    try:
        heard = yarngpt.transcribe(phone_audio.ulaw_to_wav(ul), "audio/wav")["text"]
    except yarngpt.SpeechUnavailable:
        return True  # no way to check: keep it
    want, got = answers.plain(text).split(), answers.plain(heard).split()
    if not want:
        return True
    found = sum(1 for w in want if w in got)
    return found / len(want) >= 0.7 and len(got) <= len(want) + 3


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--route", choices=("job", "prepare"), default="job")
    ap.add_argument("--only", nargs="*", default=list(LANGS), choices=LANGS)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--remake", nargs="*", default=[], choices=LANGS, help="make these languages' clips again")
    ap.add_argument("--check", action="store_true", help="transcribe each new clip back and retry a garbled one")
    args = ap.parse_args()
    if not shutil.which("ffmpeg"):
        raise SystemExit("ffmpeg is needed on the PATH.")
    clips.ROUTE = args.route
    if not args.dry_run and not settings.yarngpt_api_key:
        raise SystemExit("YARNGPT_API_KEY is not set.")
    failed = 0
    for lang in args.only:
        folder = OUT / lang
        texts = {clips.clip_key(t): t for t in prompts(lang)}
        remake = lang in args.remake
        missing = {k: t for k, t in texts.items() if remake or not (folder / f"{k}.ul").exists()}
        print(f"{lang}  {len(texts)} prompts, {len(missing)} to make")
        if args.dry_run or not missing:
            continue
        folder.mkdir(parents=True, exist_ok=True)
        voice = call_voice(lang)
        for key, text in missing.items():
            mp3_path = folder / f"{key}.mp3"
            try:
                ul = b""
                for attempt in range(4 if args.check else 1):
                    fresh = remake or not mp3_path.exists() or attempt > 0
                    mp3 = clips.fetch(clips.voice_text(text), lang, voice) if fresh and attempt else (
                        clips.make(text, lang, voice) if fresh else mp3_path.read_bytes())
                    ul = to_ulaw(mp3)
                    if not args.check or heard_right(text, ul):
                        break
                    print(f"     garbled, again  {text[:50]}")
                if lang in MP3_LANGS and (remake or not mp3_path.exists()):
                    mp3_path.write_bytes(mp3)
                (folder / f"{key}.ul").write_bytes(ul)
                print(f"     made  {text[:60]}")
            except (yarngpt.SpeechUnavailable, RuntimeError) as err:
                failed += 1
                print(f"     FAILED {err}  {text[:60]}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
