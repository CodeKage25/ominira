"""Two customers of the mock bank: money sent from one lands with the other."""

from tests.conftest import CUSTOMER, PIN_OK, balance, confirm, make_draft


def _seal(pin: str) -> str:
    return "demo-" + "".join(str(9 - int(d)) for d in pin)


def _sign_up(c, name: str, pin: str) -> tuple[dict, str]:
    sid = c.post("/v1/onboarding/sessions").json()["id"]
    c.post(f"/v1/onboarding/{sid}/id-document", json={"full_name": name, "nin_last4": "8910", "born_on": "1960-10-01"})
    c.post(f"/v1/onboarding/{sid}/bvn-check")
    token = c.post(f"/v1/onboarding/{sid}/complete").json()["token"]
    assert c.post(f"/v1/onboarding/{sid}/pin", json={"pin_block": _seal(pin)}).json()["ok"] is True
    mine = {"Authorization": f"Bearer {token}"}
    return mine, c.get("/v1/accounts", headers=mine).json()[0]["number"]


def test_money_sent_lands_with_the_other_customer(client):
    chinedu, number = _sign_up(client, "Chinedu Okafor", "4826")
    # Name enquiry knows the bank's own customer by the name read off their card.
    found = client.post("/v1/payees/lookup", headers=CUSTOMER, json={"bank_code": "057", "account_number": number}).json()
    assert found["name"] == "Chinedu Okafor"

    before = balance(client)
    draft = make_draft(client, amount_kobo=500_000, bank_code="057", account_number=number)
    assert confirm(client, draft, PIN_OK).json()["status"] == "executed"
    assert balance(client) == before - 500_000
    mine = client.get("/v1/accounts", headers=chinedu).json()[0]
    assert mine["balance_kobo"] == 10_000_000 + 500_000
    came_in = client.get(f"/v1/accounts/{mine['id']}/transactions?limit=1", headers=chinedu).json()[0]
    assert came_in["incoming"] is True and came_in["who"] == "Ronke Adeyemi" and came_in["amount_kobo"] == 500_000


def test_and_back_again(client):
    chinedu, _ = _sign_up(client, "Chinedu Okafor", "4826")
    ronke = client.get("/v1/accounts", headers=CUSTOMER).json()[0]
    draft = make_draft(client, headers=chinedu, amount_kobo=200_000, bank_code="057", account_number=ronke["number"])
    assert draft["readback"]["name"] == "Ronke Adeyemi"
    assert confirm(client, draft, _seal("4826"), headers=chinedu).json()["status"] == "executed"
    assert balance(client) == ronke["balance_kobo"] + 200_000


def test_the_assistant_never_sees_the_full_number(client):
    from tests.conftest import ASSISTANT
    assert "number" not in client.get("/v1/accounts", headers=ASSISTANT).json()[0]


def _turn(c, headers: dict, text: str) -> dict:
    conv = c.post("/v1/conversations", headers=headers).json()["id"]
    r = c.post(f"/v1/conversations/{conv}/turns", headers=headers, json={"text": text})
    assert r.status_code == 200, r.text
    return r.json()


def test_a_new_customer_starts_with_people_saved(client):
    chinedu, _ = _sign_up(client, "Chinedu Okafor", "4826")
    saved = {p["nickname"]: p for p in client.get("/v1/payees", headers=chinedu).json()}
    assert {"Tolu", "Mama", "Musa", "Chika", "Aisha", "Emeka"} <= saved.keys()
    assert saved["Chika"]["name"] == "Okonkwo Chika" and saved["Chika"]["bank"] == "Access Bank"
    said = _turn(client, chinedu, "send twenty thousand to Tolu")
    assert said["draft"]["readback"]["name"] == "Adebayo Tolulope"


def test_a_short_form_finds_the_saved_name(client):
    said = _turn(client, CUSTOMER, "send five thousand to Tolulo")
    assert said["draft"]["readback"]["name"] == "Adebayo Tolulope"


def test_a_spoken_zenith_number_needs_no_bank(client):
    _, number = _sign_up(client, "Chinedu Okafor", "4826")
    said = _turn(client, CUSTOMER, f"send five thousand to {number}")
    assert said["draft"]["readback"]["name"] == "Chinedu Okafor"
    # Not a Zenith customer's number: the bank is still asked for.
    assert "Which bank" in _turn(client, CUSTOMER, "send five thousand to 0691234567")["say"]
