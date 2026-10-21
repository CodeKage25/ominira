"""Make the language picker's clips: one line per language, in that language's own voice.

The app's first screen speaks these before sign-in, so they ship inside the APK and play offline.

    set -a; . ../.env; set +a                       # YARNGPT_API_KEY, and YARNGPT_VOICES if pinned
    uv run python scripts/make_language_clips.py
    uv run python scripts/make_language_clips.py --only yo ha --voice yo=idera
    uv run python scripts/make_language_clips.py --out /tmp/clips

Voice per language: --voice or YARNGPT_VOICES first, then the first voice YarnGPT's catalogue lists
for that language, then YarnGPT's own default. Writes lang_<code>.mp3 into
android/app/src/main/assets/audio/ unless --out says otherwise, and prints each clip's size and
length. Each run spends one or two YarnGPT calls per language.
"""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from ominira.settings import settings  # noqa: E402
from ominira.speech import yarngpt  # noqa: E402

# Native speakers: edit these lines, then run the script again.
LINES = {
    "en": "For English, say English, or tap English.",
    "pcm": "For Pidgin, talk Pidgin, or touch Pidgin.",
    "yo": "Fún Yorùbá, sọ Yorùbá, tàbí tẹ Yorùbá.",
    "ha": "Don Hausa, ka ce Hausa, ko ka taɓa Hausa.",
    "ig": "Maka Igbo, kwuo Igbo, ma ọ bụ pịa Igbo.",
}

DEFAULT_OUT = Path(__file__).resolve().parents[2] / "android" / "app" / "src" / "main" / "assets" / "audio"

# MPEG audio frame tables, for working out a clip's length without extra libraries.
_BITRATES_KBPS = {
    ("1", 1): (0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448),
    ("1", 2): (0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384),
    ("1", 3): (0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320),
    ("2", 1): (0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256),
    ("2", 2): (0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160),
    ("2", 3): (0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160),
}
_SAMPLE_RATES = {"1": (44100, 48000, 32000), "2": (22050, 24000, 16000), "2.5": (11025, 12000, 8000)}


def mp3_seconds(data: bytes) -> float | None:
    """Length of an MP3 by walking its frames. None when no frame is found."""
    pos = 0
    if data[:3] == b"ID3" and len(data) >= 10:
        size = (data[6] << 21) | (data[7] << 14) | (data[8] << 7) | data[9]
        pos = 10 + size + (10 if data[5] & 0x10 else 0)
    seconds, frames = 0.0, 0
    while pos + 4 <= len(data):
        b1, b2 = data[pos + 1], data[pos + 2]
        if data[pos] != 0xFF or (b1 & 0xE0) != 0xE0:
            pos += 1
            continue
        version = {3: "1", 2: "2", 0: "2.5"}.get((b1 >> 3) & 3)
        layer = {3: 1, 2: 2, 1: 3}.get((b1 >> 1) & 3)
        bitrate_index, rate_index, padding = b2 >> 4, (b2 >> 2) & 3, (b2 >> 1) & 1
        if version is None or layer is None or bitrate_index in (0, 15) or rate_index == 3:
            pos += 1
            continue
        bitrate = _BITRATES_KBPS[("1" if version == "1" else "2", layer)][bitrate_index] * 1000
        rate = _SAMPLE_RATES[version][rate_index]
        if layer == 1:
            samples, length = 384, (12 * bitrate // rate + padding) * 4
        else:
            samples = 576 if (layer == 3 and version != "1") else 1152
            length = samples // 8 * bitrate // rate + padding
        if length <= 4:
            pos += 1
            continue
        seconds += samples / rate
        frames += 1
        pos += length
    return round(seconds, 2) if frames else None


def choose_voice(lang: str) -> tuple[str | None, str]:
    """(voice id, where it came from)."""
    pinned = yarngpt.pinned_voices()
    if lang in pinned:
        return pinned[lang], "pinned"
    voice = yarngpt.voice_for(lang)
    if voice:
        return voice, "catalogue"
    return None, "YarnGPT default"


def make_clip(lang: str, out_dir: Path) -> dict:
    voice, source = choose_voice(lang)
    ticket = yarngpt.prepare(LINES[lang], lang, output_format="mp3", voice=voice)
    audio = yarngpt.download(ticket["audio_url"])
    if not audio:
        raise yarngpt.SpeechUnavailable("empty_audio", "YarnGPT returned no audio")
    path = out_dir / f"lang_{lang}.mp3"
    path.write_bytes(audio)
    return {"lang": lang, "voice": voice, "source": source, "path": path, "bytes": len(audio),
            "seconds": mp3_seconds(audio)}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT, help=f"folder for the clips (default {DEFAULT_OUT})")
    ap.add_argument("--only", nargs="*", default=list(LINES), choices=list(LINES))
    ap.add_argument("--voice", action="append", default=[], help="pin a voice for this run, e.g. yo=idera")
    args = ap.parse_args(argv)

    if not settings.yarngpt_api_key:
        raise SystemExit("YARNGPT_API_KEY is not set. Put it in the environment (for example "
                         "`set -a; . ../.env; set +a`) and run this again. Nothing was written.")
    if args.voice:
        settings.yarngpt_voices = ",".join([settings.yarngpt_voices, *args.voice]).strip(",")

    args.out.mkdir(parents=True, exist_ok=True)
    failed = []
    for lang in args.only:
        try:
            clip = make_clip(lang, args.out)
        except yarngpt.SpeechUnavailable as err:
            print(f"{lang:3}  FAILED: {err}")
            failed.append(lang)
            continue
        length = f"{clip['seconds']:.2f} s" if clip["seconds"] is not None else "unknown length"
        print(f"{lang:3}  {clip['bytes']:>7,} bytes  {length:>9}  voice {clip['voice'] or 'default'} "
              f"({clip['source']})  -> {clip['path']}")
    if failed:
        print(f"\nNo clip for: {', '.join(failed)}. Fix the cause and run again with --only {' '.join(failed)}.")
        return 1
    print("\nPlay each clip to a native speaker before shipping it.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
