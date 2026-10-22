"""Yorùbá, Hausa and Igbo end to end, the six saved people as customers, and the closed sign-up."""

import string

import pytest
from sqlalchemy import create_engine, inspect, select, text
from sqlalchemy.orm import Session

from ominira import amounts, demo_world, lines, readback
from ominira.tables import Customer, Payee, Token
from tests.conftest import CUSTOMER, PIN_OK, confirm, make_draft

PEOPLE = {
    "Tolu": ("yo", "057", "2044714471", "Adebayo Tolulope", "Tolulope"),
    "Mama": ("yo", "058", "0123450213", "Adeyemi Folake", "Folake"),
    "Musa": ("ha", "999992", "8031447710", "Musa Ibrahim", "Musa"),
    "Aisha": ("ha", "033", "2150987654", "Bello Aisha", "Aisha"),
    "Emeka": ("ig", "057", "2234567890", "Nwosu Emeka", "Emeka"),
    "Chika": ("ig", "044", "0691234567", "Okonkwo Chika", "Chika"),
}


def _as(nickname: str) -> dict:
    return {"Authorization": f"Bearer demo-{nickname.lower()}"}


def _turn(c, headers: dict, said: str) -> dict:
    conv = c.post("/v1/conversations", headers=headers).json()["id"]
    r = c.post(f"/v1/conversations/{conv}/turns", headers=headers, json={"text": said})
    assert r.status_code == 200, r.text
    return r.json()


def _account(c, headers: dict) -> dict:
    return c.get("/v1/accounts", headers=headers).json()[0]


# ---- amounts in words ---------------------------------------------------------------------------

@pytest.mark.parametrize("lang,kobo,said", [
    ("yo", 200_000, "náírà ẹgbẹ̀rún méjì"),
    ("yo", 500_000, "náírà ẹgbẹ̀rún márùn-ún"),
    ("yo", 1_500_000, "náírà ẹgbẹ̀rún márùndínlógún"),
    ("yo", 2_000_000, "náírà ẹgbẹ̀rún ogún"),
    ("yo", 5_000_000, "náírà ẹgbẹ̀rún àádọ́ta"),
    ("yo", 15_000_000, "náírà ẹgbẹ̀rún àádọ́jọ"),
    ("yo", 100_000_000, "náírà mílíọ̀nù kan"),
    ("yo", 84_230_000, "náírà ẹgbẹ̀rún ẹgbẹ̀rin ó lé méjìlélógójì, ó lé ọ̀ọ́dúnrún"),
    ("ha", 200_000, "naira dubu biyu"),
    ("ha", 500_000, "naira dubu biyar"),
    ("ha", 1_500_000, "naira dubu goma sha biyar"),
    ("ha", 2_000_000, "naira dubu ashirin"),
    ("ha", 5_000_000, "naira dubu hamsin"),
    ("ha", 15_000_000, "naira dubu ɗari da hamsin"),
    ("ha", 100_000_000, "naira miliyan ɗaya"),
    ("ha", 84_230_000, "naira dubu ɗari takwas da arba'in da biyu, da ɗari uku"),
    ("ig", 200_000, "naịra puku abụọ"),
    ("ig", 500_000, "naịra puku ise"),
    ("ig", 1_500_000, "naịra puku iri na ise"),
    ("ig", 2_000_000, "naịra puku iri abụọ"),
    ("ig", 5_000_000, "naịra puku iri ise"),
    ("ig", 15_000_000, "naịra puku narị na iri ise"),
    ("ig", 100_000_000, "naịra otu nde"),
    ("ig", 84_230_000, "naịra puku narị asatọ na iri anọ na abụọ, na narị atọ"),
    ("pcm", 2_000_000, "twenty thousand naira"),
])
def test_amounts_in_each_language(lang, kobo, said):
    assert amounts.spoken(kobo, lang) == said
    assert amounts.parse_kobo(said, lang) == kobo
    assert amounts.parse_kobo(amounts.fold(said), lang) == kobo  # speech to text drops tone marks


@pytest.mark.parametrize("lang", ["yo", "ha", "ig"])
def test_every_amount_reads_back_to_itself(lang):
    for naira in [*range(1, 3_001), 99_999, 100_050, 999_999, 1_000_001, 2_300_000, 123_456_789, 999_999_999]:
        assert amounts.parse_kobo(amounts.spoken(naira * 100, lang), lang) == naira * 100, naira


@pytest.mark.parametrize("lang,said,kobo", [
    ("yo", "fi ẹgbẹ̀rún ogún ránṣẹ́ sí Tolu", 2_000_000),
    ("yo", "fi egberun ogun ranse si Tolu", 2_000_000),
    ("yo", "fi 20000 ranse si Tolu", 2_000_000),
    ("yo", "ẹgbàá mẹ́wàá náírà", 2_000_000),
    ("yo", "send twenty thousand to Tolu", 2_000_000),
    ("ha", "aika dubu ashirin zuwa ga Musa", 2_000_000),
    ("ha", "aika dubu 20 zuwa ga Musa", 2_000_000),
    ("ig", "ziga puku iri abụọ nye Emeka", 2_000_000),
    ("ig", "ziga puku iri abuo nye Emeka", 2_000_000),
    ("yo", "Elo lo wa ninu account mi", None),
    ("ha", "nawa ne a asusuna", None),
    ("ig", "ego ole dị n'akaụntụ m", None),
    ("yo", "fi owó kan ránṣẹ́ sí Mama", None),
    ("yo", "fi owo kan ranse si Mama", None),
])
def test_spoken_amounts(lang, said, kobo):
    assert amounts.parse_kobo(said, lang) == kobo


def test_digits_in_each_language():
    assert amounts.digit_words("4471", "yo") == "mẹ́rin, mẹ́rin, méje, ọ̀kan"
    assert amounts.digit_words("0213", "ha") == "sifili, biyu, ɗaya, uku"
    assert amounts.digit_words("7710", "ig") == "asaa, asaa, otu, efu"
    assert amounts.digit_words("4471", "pcm") == "four, four, seven, one"


# ---- lines --------------------------------------------------------------------------------------

def _fields(line: str) -> set[str]:
    return {name for _, name, _, _ in string.Formatter().parse(line) if name}


def test_every_line_has_all_five_languages_with_the_same_placeholders():
    for key in lines.keys():
        row = lines._COPY[key]
        if "_menu_" in key:
            continue  # a menu line is said only in its own language
        assert set(row) == set(lines.LANGS), key
        for lang, line in row.items():
            assert _fields(line) == _fields(row["en"]), (key, lang)
            assert "\u2014" not in line  # no em dash


def test_the_readback_hash_does_not_depend_on_the_language():
    words = {lang: readback.for_transfer(lang, name="Adebayo Tolulope", bank="Zenith Bank", last4="4471",
                                         amount_kobo=2_000_000) for lang in lines.LANGS}
    assert len({readback.readback_hash(rb) for rb in words.values()}) == 1
    assert len({rb["spoken"][0] for rb in words.values()}) == 4  # Pidgin keeps the English words


# ---- end to end, one language at a time -----------------------------------------------------------

CASES = [
    # (language, who sends, what they say, who receives, readback, sent line)
    ("yo", "Ronke", "fi ẹgbẹ̀rún ogún ránṣẹ́ sí Tolu", "Tolu",
     ["Náírà ẹgbẹ̀rún ogún.", "Sí Adebayo Tolulope.", "Zenith Bank, tí ó parí sí mẹ́rin, mẹ́rin, méje, ọ̀kan.",
      "Ẹ pulse PIN yín láti fi ránṣẹ́."], "Ó ti lọ. Tolu ti gbà á."),
    ("ha", "Ronke", "aika dubu ashirin zuwa ga Musa", "Musa",
     ["Naira dubu ashirin.", "Zuwa ga Musa Ibrahim.", "OPay, mai ƙarewa da bakwai, bakwai, ɗaya, sifili.",
      "Ku yi pulse PIN don aikawa."], "An aika. Musa ya samu."),
    ("ig", "Emeka", "ziga puku iri abuo nye Ronke", "Ronke",
     ["Naịra puku iri abụọ.", "Nye Ronke Adeyemi.", "Zenith Bank, nke na-ejedebe na anọ, ise, isii, asaa.",
      "Pulse PIN gị ka i ziga."], "Ezigala ya. Ronke enwetala ya."),
]


def _drafted_line(lang: str, draft: dict) -> str:
    rb = draft["readback"]
    new = lines.say(lang, "drafted_new_payee") if rb["name"] == "Musa Ibrahim" else ""
    return lines.say(lang, "drafted_transfer", name=rb["name"], bank=rb["bank"], new=new)


def _headers(who: str) -> dict:
    return CUSTOMER if who == "Ronke" else _as(who)


@pytest.mark.parametrize("lang,sender,said,receiver,spoken,sent", CASES)
def test_send_and_receive_in_ones_own_language(client, lang, sender, said, receiver, spoken, sent):
    c = client
    for who in (sender, receiver):
        c.put("/v1/me/preferences", headers=_headers(who), json={"lang": lang})
    before = _account(c, _headers(receiver))["balance_kobo"]

    turn = _turn(c, _headers(sender), said)
    draft = turn["draft"]
    assert draft and draft["readback"]["lang"] == lang and draft["readback"]["spoken"] == spoken
    assert turn["say"] == _drafted_line(lang, draft)
    done = confirm(c, draft, PIN_OK, headers=_headers(sender))
    assert done.status_code == 200 and done.json()["say"] == sent

    mine = _account(c, _headers(receiver))
    assert mine["balance_kobo"] == before + 2_000_000
    came = c.get(f"/v1/accounts/{mine['id']}/transactions?limit=1", headers=_headers(receiver)).json()[0]
    assert came["incoming"] is True and came["amount_kobo"] == 2_000_000
    balance = c.get(f"/v1/accounts/{mine['id']}/balance", headers=_headers(receiver)).json()["say"]
    assert balance == lines.say(lang, "balance", amount=amounts.spoken(mine["balance_kobo"], lang))
    summary = c.get(f"/v1/accounts/{mine['id']}/income-summary", headers=_headers(receiver)).json()["say"]
    if receiver == "Tolu":
        assert summary == "Náírà ẹgbẹ̀rún ogún ló wọlé lọ́sẹ̀ yìí, láti ọ̀dọ̀ ènìyàn kan."
    if receiver == "Musa":
        assert summary == "Naira dubu ashirin ya shigo wannan mako, daga mutum ɗaya."
    if receiver == "Ronke":
        assert summary.endswith("batara n'izu a, site n'aka mmadụ atọ.")


@pytest.mark.parametrize("who,said,start", [
    ("Tolu", "Elo lo wa ninu account mi", "Ẹ ní náírà ẹgbẹ̀rún igba ó lé àádọ́ta nínú"),
    ("Musa", "nawa ne a asusuna", "Kuna da naira dubu ɗari biyu da hamsin a"),
    ("Emeka", "ego ole dị n'akaụntụ m", "Ị nwere naịra puku narị abụọ na iri ise na"),
    ("Tolu", "kí ló wọlé lọ́sẹ̀ yìí", "Kò sí owó tó wọlé lọ́sẹ̀ yìí."),
    ("Aisha", "me ya shigo wannan mako", "Babu abin da ya shigo wannan mako."),
    ("Chika", "gịnị batara n'izu a", "Ọ dịghị ihe batara n'izu a."),
])
def test_reading_the_account_in_each_language(client, who, said, start):
    assert _turn(client, _as(who), said)["say"].startswith(start)


@pytest.mark.parametrize("who,said", [("Mama", "ẹ dí káàdì mi"), ("Aisha", "ku toshe katina"),
                                      ("Chika", "gbochie kaadị m")])
def test_blocking_the_card_in_each_language(client, who, said):
    turn = _turn(client, _as(who), said)
    assert turn["draft"]["kind"] == "card_block"
    assert turn["say"] == lines.say(PEOPLE[who][0], "drafted_card_block")


def test_a_wrong_pin_counts_down_in_words(client):
    client.put("/v1/me/preferences", headers=CUSTOMER, json={"lang": "ha"})
    d = make_draft(client, payee_id="py_tolu", amount_kobo=100_000)
    assert confirm(client, d, "demo-0000").json()["say"].endswith("Kuna da sauran damar biyu.")


def test_limits_are_said_in_the_customers_words(client):
    client.put("/v1/me/preferences", headers=CUSTOMER, json={"lang": "ig"})
    d = make_draft(client, payee_id="py_tolu", amount_kobo=200_000_000)
    assert "Nke kachasị bụ naịra otu nde." in confirm(client, d).json()["say"]


# ---- the saved people are customers -----------------------------------------------------------------

def test_the_six_people_are_customers_in_their_languages(client):
    for nickname, (lang, code, number, name, first) in PEOPLE.items():
        me = _as(nickname)
        assert client.post("/v1/auth/unlock", headers=me, json={"pin_block": PIN_OK}).status_code == 200
        prefs = client.get("/v1/me/preferences", headers=me).json()
        assert prefs["lang"] == lang and prefs["first_name"] == first
        acct = _account(client, me)
        assert acct["balance_kobo"] == 25_000_000 and acct["number"] == number
        saved = client.get("/v1/payees", headers=me).json()
        assert [(p["nickname"], p["name"], p["is_new"]) for p in saved] == [("Ronke", "Ronke Adeyemi", False)]
        found = client.post("/v1/payees/lookup", headers=me, json={"bank_code": code, "account_number": number})
        assert found.json()["name"] == name
    # Ronke's own world is as it was.
    ronke = {p["nickname"]: p["is_new"] for p in client.get("/v1/payees", headers=CUSTOMER).json()}
    assert ronke == {"Mama": False, "Musa": True, "Tolu": False}
    assert client.get("/v1/approvers", headers=_as("Tolu")).json() == []


def test_money_to_another_bank_lands_when_the_mock_holds_the_account(client):
    before = _account(client, _as("Mama"))["balance_kobo"]
    d = make_draft(client, payee_id="py_mama", amount_kobo=500_000)
    assert confirm(client, d).json()["status"] == "executed"
    assert _account(client, _as("Mama"))["balance_kobo"] == before + 500_000


def test_reset_brings_the_people_back(client):
    d = make_draft(client, payee_id="py_tolu", amount_kobo=500_000)
    confirm(client, d)
    client.post("/_mock/reset")
    assert _account(client, _as("Tolu"))["balance_kobo"] == 25_000_000
    assert client.get("/v1/payees", headers=_as("Emeka")).json()[0]["nickname"] == "Ronke"


def test_an_older_database_gets_the_people_and_columns_once(tmp_path):
    engine = create_engine(f"sqlite:///{tmp_path}/old.db")
    demo_world.ensure(engine)
    with engine.begin() as db:
        db.execute(text("DELETE FROM tokens WHERE token LIKE 'demo-%' AND customer_id != 'cus_ronke'"))
        db.execute(text("DELETE FROM payees WHERE id LIKE 'py_ronke_%'"))
        db.execute(text("DELETE FROM customers WHERE id != 'cus_ronke'"))
        for table, key in (("mock_cards", "customer_ref"), ("mock_beneficiaries", "customer_ref"),
                           ("mock_accounts", "customer_ref"), ("mock_customers", "ref")):
            db.execute(text(f"DELETE FROM {table} WHERE {key} LIKE 'ZC-DEMO-%'"))
        db.execute(text("ALTER TABLE mock_accounts DROP COLUMN bank_code"))
        db.execute(text("ALTER TABLE onboarding DROP COLUMN pin_set"))
    demo_world.ensure(engine)
    demo_world.ensure(engine)
    assert "bank_code" in {c["name"] for c in inspect(engine).get_columns("mock_accounts")}
    assert "pin_set" in {c["name"] for c in inspect(engine).get_columns("onboarding")}
    with Session(engine) as db:
        assert len(db.scalars(select(Customer)).all()) == 7
        assert len(db.scalars(select(Token).where(Token.token == "demo-tolu")).all()) == 1
        assert db.get(Payee, "py_musa").is_new
    engine.dispose()


# ---- a sign-up is closed once its PIN is set --------------------------------------------------------

def _seal(pin: str) -> str:
    return "demo-" + "".join(str(9 - int(d)) for d in pin)


def test_the_sign_up_closes_after_its_pin(client):
    c = client
    sid = c.post("/v1/onboarding/sessions", json={"lang": "yo"}).json()["id"]
    assert c.post(f"/v1/onboarding/{sid}/pin", json={"pin_block": _seal("4826")}).json()["code"] == "onboarding_incomplete"
    c.post(f"/v1/onboarding/{sid}/bvn-check")
    assert c.post(f"/v1/onboarding/{sid}/complete").status_code == 200
    weak = c.post(f"/v1/onboarding/{sid}/pin", json={"pin_block": _seal("1234")})
    assert weak.status_code == 422 and weak.json()["say"] == "PIN yẹn rọrùn jù láti mọ̀. Ẹ yan òmíràn."
    ok = c.post(f"/v1/onboarding/{sid}/pin", json={"pin_block": _seal("4826")})
    assert ok.status_code == 200 and ok.json()["say"].startswith("A ti ṣètò Pulse PIN yín.")
    for path, body in (("pin", {"pin_block": _seal("5937")}), ("complete", None)):
        again = c.post(f"/v1/onboarding/{sid}/{path}", json=body)
        assert again.status_code == 409 and again.json()["code"] == "onboarding_closed"
