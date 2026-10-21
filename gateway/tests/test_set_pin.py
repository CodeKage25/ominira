"""A new account sets its own Pulse PIN; easy PINs are refused by the core."""

from ominira.core.mock import weak_pin
from tests.conftest import CUSTOMER


def _seal(pin: str) -> str:
    return "demo-" + "".join(str(9 - int(d)) for d in pin)


def _open_account(c) -> str:
    sid = c.post("/v1/onboarding/sessions").json()["id"]
    assert c.post(f"/v1/onboarding/{sid}/bvn-check").json()["match"] is True
    assert c.post(f"/v1/onboarding/{sid}/complete").status_code == 200
    return sid


def test_weak_pins():
    for pin in ("1111", "1234", "9876", "1212", "0123"):
        assert weak_pin(pin), pin
    for pin in ("2075", "1961", "4826"):
        assert not weak_pin(pin), pin


def test_pin_waits_for_the_account(client):
    sid = client.post("/v1/onboarding/sessions").json()["id"]
    r = client.post(f"/v1/onboarding/{sid}/pin", json={"pin_block": _seal("4826")})
    assert r.status_code == 409 and r.json()["code"] == "onboarding_incomplete"


def test_new_pin_signs_in_and_the_old_one_does_not(client):
    sid = _open_account(client)
    assert client.post(f"/v1/onboarding/{sid}/pin", json={"pin_block": _seal("1234")}).json()["code"] == "weak_pin"
    assert client.post(f"/v1/onboarding/{sid}/pin", json={"pin_block": _seal("4826")}).json()["ok"] is True
    assert client.post("/v1/auth/unlock", headers=CUSTOMER, json={"pin_block": _seal("4826")}).json()["ok"] is True
    wrong = client.post("/v1/auth/unlock", headers=CUSTOMER, json={"pin_block": _seal("2075")})
    assert wrong.json().get("ok") is not True
