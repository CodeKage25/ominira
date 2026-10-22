"""Google Speech-to-Text, for the one thing YarnGPT's recogniser does badly on a phone line: a ten-digit account
number. On Cloud Run the service's own account signs the request through the metadata server, so no key is kept;
anywhere else it is off and the caller falls back to YarnGPT."""

import base64
import logging
import time

import httpx

log = logging.getLogger("ominira.speech")

_TOKEN_URL = "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token"
_RECOGNIZE = "https://speech.googleapis.com/v1/speech:recognize"
# Tried together; the first answer that is ten digits wins. Nigerian English hears a Nigerian voice best,
# the phone-call model hears fast speech best.
MODELS = (("en-NG", "default"), ("en-US", "phone_call"))

_token: tuple[str, float] | None = None
_unavailable_until = 0.0
transport: httpx.BaseTransport | None = None  # tests swap this


class Unavailable(Exception):
    pass


def _access_token() -> str:
    global _token, _unavailable_until
    if time.monotonic() < _unavailable_until:
        raise Unavailable("not on Cloud Run")
    if _token and time.monotonic() < _token[1]:
        return _token[0]
    try:
        with httpx.Client(timeout=2.0, transport=transport) as client:
            r = client.get(_TOKEN_URL, headers={"Metadata-Flavor": "Google"})
        r.raise_for_status()
        data = r.json()
    except (httpx.HTTPError, ValueError) as exc:
        _unavailable_until = time.monotonic() + 600  # not on Google Cloud: do not ask again for a while
        raise Unavailable(str(exc)) from exc
    _token = (data["access_token"], time.monotonic() + max(60, int(data.get("expires_in", 300)) - 60))
    return _token[0]


def recognize(ulaw: bytes, lang: str, model: str) -> str:
    """8 kHz μ-law in, the transcript out, with digit sequences favoured. Blocking."""
    token = _access_token()
    body = {
        "config": {
            "encoding": "MULAW", "sampleRateHertz": 8000, "languageCode": lang, "model": model,
            "speechContexts": [{"phrases": ["$OOV_CLASS_DIGIT_SEQUENCE"], "boost": 15}],
        },
        "audio": {"content": base64.b64encode(ulaw).decode()},
    }
    try:
        with httpx.Client(timeout=10.0, transport=transport) as client:
            r = client.post(_RECOGNIZE, json=body, headers={"Authorization": f"Bearer {token}"})
    except httpx.HTTPError as exc:
        raise Unavailable(str(exc)) from exc
    if r.status_code != 200:
        raise Unavailable(f"http_{r.status_code}: {r.text[:200]}")
    return " ".join(res["alternatives"][0]["transcript"] for res in r.json().get("results", []) if res.get("alternatives"))
