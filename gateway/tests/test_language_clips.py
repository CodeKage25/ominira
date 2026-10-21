"""scripts/make_language_clips.py against a fake YarnGPT. No network, nothing written under android/."""

import json

import httpx
import pytest

from ominira.settings import settings
from ominira.speech import yarngpt
from scripts import make_language_clips as clips

FRAME = b"\xff\xfb\x90\x00" + b"\x00" * 413  # MPEG-1 Layer III, 128 kbps, 44.1 kHz: 417 bytes, 1152 samples


def fake_mp3(frames: int = 100, id3: bool = True) -> bytes:
    tag = b"ID3\x03\x00\x00\x00\x00\x00\x0a" + b"\x00" * 10 if id3 else b""
    return tag + FRAME * frames


class FakeYarn:
    def __init__(self):
        self.prepared: list[dict] = []
        self.fail_prepare_for: str | None = None

    def __call__(self, req: httpx.Request) -> httpx.Response:
        path = req.url.path
        if path == "/api/v1/voices":
            assert req.headers["authorization"] == "Bearer test-key"
            return httpx.Response(200, json=[{"id": "umar", "language": "English"},
                                             {"id": "idera", "language": "Yoruba"},
                                             {"id": "zainab", "language": "Hausa"}])
        if path == "/api/v1/tts/prepare":
            assert req.headers["authorization"] == "Bearer test-key"
            body = json.loads(req.content)
            if self.fail_prepare_for and self.fail_prepare_for in body["text"]:
                return httpx.Response(503, json={"error": {"code": "SERVICE_UNAVAILABLE", "message": "busy"}})
            self.prepared.append(body)
            return httpx.Response(200, json={"ticket": f"tk{len(self.prepared)}",
                                              "stream_url": f"/api/v1/tts/stream/tk{len(self.prepared)}"})
        if path.startswith("/api/v1/tts/stream/"):
            assert "authorization" not in req.headers  # the phone's path: the ticket is the credential
            return httpx.Response(200, content=fake_mp3(), headers={"content-type": "audio/mpeg"})
        return httpx.Response(404)


@pytest.fixture
def yarn(monkeypatch):
    fake = FakeYarn()
    monkeypatch.setattr(settings, "yarngpt_api_key", "test-key")
    monkeypatch.setattr(settings, "yarngpt_voices", "")
    monkeypatch.setattr(yarngpt, "transport", httpx.MockTransport(fake))
    monkeypatch.setattr(yarngpt, "_voice_cache", None)
    return fake


def test_the_five_lines_are_the_agreed_ones():
    assert clips.LINES == {
        "en": "For English, say English, or tap English.",
        "pcm": "For Pidgin, talk Pidgin, or touch Pidgin.",
        "yo": "Fún Yorùbá, sọ Yorùbá, tàbí tẹ Yorùbá.",
        "ha": "Don Hausa, ka ce Hausa, ko ka taɓa Hausa.",
        "ig": "Maka Igbo, kwuo Igbo, ma ọ bụ pịa Igbo.",
    }


def test_default_output_is_the_android_assets_audio_folder():
    assert clips.DEFAULT_OUT.parts[-6:] == ("android", "app", "src", "main", "assets", "audio")


def test_writes_one_clip_per_language_with_its_voice(yarn, tmp_path, capsys):
    out = tmp_path / "audio"
    assert clips.main(["--out", str(out)]) == 0
    assert sorted(p.name for p in out.iterdir()) == [f"lang_{c}.mp3" for c in sorted(clips.LINES)]
    assert (out / "lang_yo.mp3").read_bytes() == fake_mp3()
    voices = {b["text"]: b.get("voice") for b in yarn.prepared}
    assert voices[clips.LINES["yo"]] == "idera" and voices[clips.LINES["ha"]] == "zainab"
    assert voices[clips.LINES["en"]] == "umar"
    assert voices[clips.LINES["pcm"]] is None and voices[clips.LINES["ig"]] is None  # YarnGPT's default
    assert all(b["output_format"] == "mp3" for b in yarn.prepared)
    printed = capsys.readouterr().out
    assert f"{len(fake_mp3()):,} bytes" in printed and "2.61 s" in printed


def test_pinned_voices_win(yarn, tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "yarngpt_voices", "ig=chinenye")
    assert clips.main(["--out", str(tmp_path), "--only", "ig", "yo", "--voice", "yo=femi"]) == 0
    voices = {b["text"]: b.get("voice") for b in yarn.prepared}
    assert voices == {clips.LINES["ig"]: "chinenye", clips.LINES["yo"]: "femi"}
    assert sorted(p.name for p in tmp_path.iterdir()) == ["lang_ig.mp3", "lang_yo.mp3"]


def test_missing_key_fails_clearly_and_writes_nothing(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "yarngpt_api_key", "")
    out = tmp_path / "audio"
    with pytest.raises(SystemExit) as stop:
        clips.main(["--out", str(out)])
    assert "YARNGPT_API_KEY is not set" in str(stop.value)
    assert not out.exists()


def test_a_failed_language_is_reported_and_exits_non_zero(yarn, tmp_path, capsys):
    yarn.fail_prepare_for = "Hausa"
    assert clips.main(["--out", str(tmp_path)]) == 1
    assert not (tmp_path / "lang_ha.mp3").exists() and (tmp_path / "lang_en.mp3").exists()
    assert "ha   FAILED" in capsys.readouterr().out


def test_mp3_length():
    assert clips.mp3_seconds(fake_mp3(100)) == 2.61
    assert clips.mp3_seconds(fake_mp3(100, id3=False)) == 2.61
    assert clips.mp3_seconds(b"not audio at all") is None
    mpeg2 = b"\xff\xf3\x90\x00" + b"\x00" * 257  # MPEG-2 Layer III, 80 kbps, 22.05 kHz: 261 bytes, 576 samples
    assert clips.mp3_seconds(mpeg2 * 50) == round(50 * 576 / 22050, 2)
