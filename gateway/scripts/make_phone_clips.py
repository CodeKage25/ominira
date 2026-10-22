"""Make the phone line's fixed prompts in Yorùbá, Hausa and Igbo, so a call only uses live YarnGPT for
lines with a name or an amount in them. English and Pidgin are spoken by Twilio's voice.

    set -a; . ../.env; set +a
    uv run python scripts/make_phone_clips.py            # job route
    uv run python scripts/make_phone_clips.py --route prepare

Writes ominira/static/phone/<lang>/<key>.mp3, named as the phone line looks them up. Existing clips are kept.
"""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import make_speech_clips as clips  # noqa: E402
from ominira import lines  # noqa: E402
from ominira.settings import settings  # noqa: E402
from ominira.speech import yarngpt  # noqa: E402

OUT = Path(__file__).resolve().parents[1] / "ominira" / "static" / "phone"


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--route", choices=("job", "prepare"), default="job")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    clips.ROUTE = args.route
    if not args.dry_run and not settings.yarngpt_api_key:
        raise SystemExit("YARNGPT_API_KEY is not set.")
    failed = 0
    for lang in ("yo", "ha", "ig"):
        # Fixed lines only: one with {first} has a name in it.
        texts = {lines.say(lang, key) for key in lines.keys()
                 if key.startswith("phone_") and lang in lines._COPY[key] and "{" not in lines._COPY[key][lang]}
        folder = OUT / lang
        missing = {clips.clip_key(t): t for t in texts if not (folder / f"{clips.clip_key(t)}.mp3").exists()}
        print(f"{lang}  {len(texts)} prompts, {len(missing)} to make")
        if args.dry_run:
            continue
        folder.mkdir(parents=True, exist_ok=True)
        voice, _ = clips.choose_voice(lang)
        for key, text in missing.items():
            try:
                (folder / f"{key}.mp3").write_bytes(clips.make(text, lang, voice))
                print(f"     made  {text[:60]}")
            except yarngpt.SpeechUnavailable as err:
                failed += 1
                print(f"     FAILED {err}  {text[:60]}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
