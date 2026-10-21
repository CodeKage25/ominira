"""Make the app's bundled speech: every fixed line it says, in each language's own voice.

Phones have no Yorùbá, Hausa or Igbo voice, so without these the app falls back to English
when the network is down. Lines with a name or an amount in them cannot be recorded ahead;
those still go to the cloud voice, or the phone's.

    set -a; . ../.env; set +a                       # YARNGPT_API_KEY, and YARNGPT_VOICES if pinned
    uv run python scripts/make_speech_clips.py --dry-run
    uv run python scripts/make_speech_clips.py
    uv run python scripts/make_speech_clips.py --only en pcm

The lines come from the app itself: the script runs the Android ClipLinesTest, which writes them
as JSON (JAVA_HOME must point at JDK 17 or 21), or reads --lines. Only lines the app speaks are
kept: the names it reads from `s.`, `spoken.` and the bank inside the app.

Writes android/app/src/main/assets/audio/<lang>/<key>.mp3 plus index.json, which maps the key of
each line, and of its English twin, to its clip. The key is ClipKey.of(text) in the app: a changed
line gets a new clip, an unchanged one is never made twice, and clips no line uses are deleted.
With ffmpeg on the PATH, each clip is re-encoded to 32 kbps mono, and a line too short for YarnGPT
is said a few times over and cut at its first pause.

Clips go through YarnGPT's job route, billed in credits (about 1 per 1,000 characters), so they
never use the 120 prepare calls a day the Gateway's live voice needs. --max-clips caps a run, and
the run stops cleanly if YarnGPT rate-limits it; run it again and it carries on where it stopped.
"""

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from make_language_clips import choose_voice, mp3_seconds  # noqa: E402
from ominira.settings import settings  # noqa: E402
from ominira.speech import yarngpt  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / "android"
SOURCES = ANDROID / "app" / "src" / "main" / "java" / "app" / "ominira"
DEFAULT_OUT = ANDROID / "app" / "src" / "main" / "assets" / "audio"
LANGS = ("en", "pcm", "yo", "ha", "ig")

# Words lines read through these names are spoken; the rest only show on screen.
_SPOKEN = re.compile(r"\b(?:s|spoken)\.([a-z]\w*)")
_BANK = re.compile(r"\bw\.([a-z]\w*)")
_PICK = re.compile(r"\{ ([a-z]\w*) \}")
# Spoken through a Words parameter, so no name above reaches them.
_ALSO = {"isThisYou"}


def clip_key(text: str) -> str:
    return hashlib.sha256(text.strip().encode("utf-8")).hexdigest()[:16]


def export_lines() -> list[dict]:
    out = Path(tempfile.gettempdir()) / "ominira_clip_lines.json"
    gradlew = ANDROID / ("gradlew.bat" if os.name == "nt" else "gradlew")
    cmd = [str(gradlew), "-q", "testDebugUnitTest", "--tests", "app.ominira.core.ClipLinesTest", "--rerun"]
    subprocess.run(cmd, cwd=ANDROID, env={**os.environ, "OMINIRA_CLIP_LINES": str(out)}, check=True)
    return json.loads(out.read_text(encoding="utf-8"))


def spoken_names() -> set[str]:
    names = set(_ALSO)
    for path in SOURCES.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        # In the bank, s is a sign-up record and w the customer's Words.
        if path.name == "FakeOminiraApi.kt":
            names |= set(_BANK.findall(text))
        else:
            names |= set(_SPOKEN.findall(text)) | set(_PICK.findall(text))
    return names


def wanted(rows: list[dict], lang: str, spoken: set[str]) -> tuple[dict[str, str], dict[str, str]]:
    """({key: text} to record, {key: file} for index.json)."""
    record, index = {}, {}
    for row in rows:
        name = row["name"]
        if "." not in name or name.split(".")[0] not in ("Onboarding", "Lines", "LanguagePrompts"):
            if name.split(".")[0] not in spoken:
                continue
        text, english = row["text"][lang], row["text"]["en"]
        # Left in English: the phone's English voice already reads it well.
        if lang not in ("en", "pcm") and text == english:
            continue
        key = clip_key(text)
        record[key] = text
        index[key] = f"{key}.mp3"
        if row["alias"] and english != text:
            index.setdefault(clip_key(english), f"{key}.mp3")
    return record, index


def squeeze(audio: bytes) -> bytes:
    if not shutil.which("ffmpeg"):
        return audio
    with tempfile.TemporaryDirectory() as tmp:
        src, dst = Path(tmp) / "in.mp3", Path(tmp) / "out.mp3"
        src.write_bytes(audio)
        done = subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(src), "-ac", "1", "-codec:a", "libmp3lame",
                               "-b:a", "32k", str(dst)], capture_output=True)
        if done.returncode != 0 or not dst.exists():
            return audio
        small = dst.read_bytes()
    return small if len(small) < len(audio) else audio


# Below YarnGPT's 20 billable characters (padding dots do not count), a line is said a few times.
SHORT = 24


def first_pause_s(audio: bytes) -> float | None:
    """Where the first pause starts, after some speech. None when ffmpeg finds none."""
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / "in.mp3"
        src.write_bytes(audio)
        done = subprocess.run(["ffmpeg", "-hide_banner", "-i", str(src), "-af", "silencedetect=noise=-35dB:d=0.25",
                               "-f", "null", "-"], capture_output=True, text=True)
    starts = [float(m) for m in re.findall(r"silence_start: ([\d.]+)", done.stderr)]
    return next((t for t in starts if t > 0.3), None)


def cut(audio: bytes, seconds: float) -> bytes:
    with tempfile.TemporaryDirectory() as tmp:
        src, dst = Path(tmp) / "in.mp3", Path(tmp) / "out.mp3"
        src.write_bytes(audio)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(src), "-t", f"{seconds:.2f}", str(dst)], check=True)
        return dst.read_bytes()


def fetch(text: str, lang: str, voice: str | None) -> bytes:
    # Same line, same key: a retry gets the first job back and is not billed again.
    audio = yarngpt.render(text, voice, key=f"ominira-clip-{lang}-{clip_key(text)}")
    if not audio:
        raise yarngpt.SpeechUnavailable("empty_audio", "YarnGPT returned no audio")
    return audio


def make(text: str, lang: str, voice: str | None) -> bytes:
    try:
        return squeeze(fetch(text, lang, voice))
    except yarngpt.SpeechUnavailable as err:
        if err.code != "INPUT_TOO_SHORT":
            raise
    if not shutil.which("ffmpeg"):
        raise yarngpt.SpeechUnavailable("too_short", "needs ffmpeg to cut a repeated short line")
    said = " ".join([text.rstrip(".") + "."] * max(2, -(-SHORT // len(text)) + 1))
    audio = fetch(said, lang, voice)
    pause = first_pause_s(audio)
    if pause is None:
        raise yarngpt.SpeechUnavailable("no_pause", "could not find where the first saying ends")
    return squeeze(cut(audio, pause + 0.15))


def main(argv: list[str] | None = None) -> int:
    sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT, help=f"folder for the clips (default {DEFAULT_OUT})")
    ap.add_argument("--only", nargs="*", default=["yo", "ha", "ig"], choices=LANGS)
    ap.add_argument("--lines", type=Path, help="JSON from ClipLinesTest, instead of running it")
    ap.add_argument("--voice", action="append", default=[], help="pin a voice for this run, e.g. yo=idera")
    ap.add_argument("--max-clips", type=int, default=1000, help="most clips this run makes (default 1000)")
    ap.add_argument("--dry-run", action="store_true", help="count what would be made, spend nothing")
    args = ap.parse_args(argv)

    rows = json.loads(args.lines.read_text(encoding="utf-8")) if args.lines else export_lines()
    spoken = spoken_names()

    if not args.dry_run and not settings.yarngpt_api_key:
        raise SystemExit("YARNGPT_API_KEY is not set. Put it in the environment (for example "
                         "`set -a; . ../.env; set +a`) and run this again. Nothing was written.")
    if args.voice:
        settings.yarngpt_voices = ",".join([settings.yarngpt_voices, *args.voice]).strip(",")

    failed = 0
    budget = args.max_clips
    stopped = None
    for lang in args.only:
        record, index = wanted(rows, lang, spoken)
        folder = args.out / lang
        missing = {k: t for k, t in record.items() if not (folder / f"{k}.mp3").exists()}
        chars = sum(len(t) for t in missing.values())
        print(f"{lang:3}  {len(record)} lines, {len(missing)} to make ({chars:,} characters)")
        if args.dry_run or stopped:
            continue

        folder.mkdir(parents=True, exist_ok=True)
        voice, source = choose_voice(lang)
        print(f"     voice {voice or 'default'} ({source})")
        made = 0
        for n, (key, text) in enumerate(missing.items(), 1):
            if budget <= 0:
                stopped = f"made the {args.max_clips} clips this run allows"
                break
            budget -= 1
            try:
                audio = make(text, lang, voice)
            except yarngpt.SpeechUnavailable as err:
                if err.code == "RATE_LIMITED":
                    stopped = "YarnGPT's daily limit is reached"
                    break
                print(f"     FAILED {key}: {err}  {text[:60]}")
                failed += 1
                index = {k: f for k, f in index.items() if f != f"{key}.mp3"}
                continue
            (folder / f"{key}.mp3").write_bytes(audio)
            made += 1
            seconds = mp3_seconds(audio)
            print(f"     {n:>4}/{len(missing)}  {len(audio):>7,} bytes  {seconds or 0:5.2f} s  {text[:60]}")

        # Never index a clip that is not on disk, and drop clips no line uses.
        index = {k: f for k, f in index.items() if (folder / f).exists()}
        (folder / "index.json").write_bytes((json.dumps(index, indent=0, sort_keys=True) + "\n").encode("utf-8"))
        stale = [p for p in folder.glob("*.mp3") if p.name not in set(index.values())]
        for p in stale:
            p.unlink()
        size = sum(p.stat().st_size for p in folder.glob("*.mp3"))
        print(f"     made {made}, removed {len(stale)}, {size / 1_000_000:.1f} MB in {folder}")

    if stopped:
        print(f"\nStopped: {stopped}. The clips made are kept; run again tomorrow to make the rest.")
        return 1
    if failed:
        print(f"\n{failed} clips failed. Run again: only the missing ones are made.")
        return 1
    if not args.dry_run:
        print("\nPlay the clips to native speakers before shipping them.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
