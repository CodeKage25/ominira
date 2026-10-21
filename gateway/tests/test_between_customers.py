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
