"""The demo world, the same story the app's offline fake tells (see README)."""

from datetime import timedelta

from sqlalchemy import Engine, inspect, select, text
from sqlalchemy.orm import Session

from ominira.core.mock_tables import (
    MockAccount, MockBeneficiary, MockCard, MockCustomer, MockDirectoryEntry, MockTxn,
)
from ominira.database import Base, utcnow
from ominira.tables import Approver, Customer, Payee, Token

CUSTOMER_ID = "cus_ronke"
CORE_REF = "ZC-2210934471"
ACCOUNT_NUMBER = "2081234567"
APPROVER_ID = "ap_funmi"
CUSTOMER_TOKEN = "demo-customer"
APPROVER_TOKEN = "demo-approver"
ASSISTANT_TOKEN = "demo-assistant"

# The people every new sign-up starts with saved, so "send to Tolu" works from the first minute.
# (nickname, bank code, bank, account number, name as name enquiry gives it, paid before)
STARTERS = (
    ("Tolu", "057", "Zenith Bank", "2044714471", "ADEBAYO TOLULOPE", True),
    ("Mama", "058", "GTBank", "0123450213", "ADEYEMI FOLAKE", True),
    ("Musa", "999992", "OPay", "8031447710", "MUSA IBRAHIM", False),
    ("Chika", "044", "Access Bank", "0691234567", "OKONKWO CHIKA", True),
    ("Aisha", "033", "UBA", "2150987654", "BELLO AISHA", True),
    ("Emeka", "057", "Zenith Bank", "2234567890", "NWOSU EMEKA", True),
)

# The same people are customers too, so money sent to them lands. nickname: (language, first name, card ending)
PEOPLE = {
    "Tolu": ("yo", "Tolulope", "3104"),
    "Mama": ("yo", "Folake", "5530"),
    "Musa": ("ha", "Musa", "7702"),
    "Chika": ("ig", "Chika", "6618"),
    "Aisha": ("ha", "Aisha", "2290"),
    "Emeka": ("ig", "Emeka", "4437"),
}

# Columns added after a database was first made: create_all only adds missing tables.
_ADDED_COLUMNS = (
    ("mock_accounts", "bank_code", "VARCHAR(10) NOT NULL DEFAULT '057'"),
    ("onboarding", "pin_set", "BOOLEAN NOT NULL DEFAULT FALSE"),
)


def ensure(engine: Engine) -> None:
    """Create tables, and seed the demo world if it is not there yet."""
    Base.metadata.create_all(engine)
    _add_columns(engine)
    with Session(engine) as db:
        if db.get(Customer, CUSTOMER_ID) is None:
            _seed(db)
        _seed_people(db)
        # Sign-ups from before starters existed get them too.
        for customer in db.scalars(select(Customer).where(Customer.id != CUSTOMER_ID)):
            if db.scalars(select(Payee).where(Payee.customer_id == customer.id)).first() is None:
                give_starter_payees(db, customer.id, customer.core_ref)
        db.commit()


def _add_columns(engine: Engine) -> None:
    tables = inspect(engine)
    with engine.begin() as conn:
        for table, column, ddl in _ADDED_COLUMNS:
            if column not in {c["name"] for c in tables.get_columns(table)}:
                conn.execute(text(f"ALTER TABLE {table} ADD COLUMN {column} {ddl}"))


def rebuild(engine: Engine) -> None:
    """Drop everything and start the demo again."""
    Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
    with Session(engine) as db:
        _seed(db)


def _seed(db: Session) -> None:
    now = utcnow()

    # The core's side: what Zenith knows.
    db.add(MockCustomer(ref=CORE_REF, full_name="Ronke Adeyemi", first_name="Ronke", bvn="22212345678",
                        pin="2075", pin_tries_left=3,
                        per_transaction_kobo=100_000_000,  # ₦1,000,000
                        daily_kobo=500_000_000))  # ₦5,000,000
    db.flush()
    db.add(MockAccount(id="ac_1", customer_ref=CORE_REF, number=ACCOUNT_NUMBER, name="Current account",
                       balance_kobo=84_230_000))  # ₦842,300
    db.flush()
    db.add_all([
        MockTxn(id="tx_1", account_id="ac_1", who="Shoprite, Ikeja", amount_kobo=845_000, incoming=False,
                reference="ZEN-40118273", at=now - timedelta(days=6)),
        MockTxn(id="tx_2", account_id="ac_1", who="Salary, Adewale Stores", amount_kobo=17_000_000, incoming=True,
                reference="ZEN-40221904", at=now - timedelta(days=4)),
        MockTxn(id="tx_3", account_id="ac_1", who="Mama", amount_kobo=1_500_000, incoming=True,
                reference="ZEN-40337716", at=now - timedelta(days=2)),
        MockDirectoryEntry(bank_code="057", number="2044714471", name="ADEBAYO TOLULOPE"),
        MockDirectoryEntry(bank_code="058", number="0123450213", name="ADEYEMI FOLAKE"),
        MockDirectoryEntry(bank_code="999992", number="8031447710", name="MUSA IBRAHIM"),
        MockDirectoryEntry(bank_code="033", number="2109876543", name="OKORO EMMANUEL", status="dormant"),
        MockDirectoryEntry(bank_code="044", number="0691234567", name="OKONKWO CHIKA"),
        MockDirectoryEntry(bank_code="033", number="2150987654", name="BELLO AISHA"),
        MockDirectoryEntry(bank_code="057", number="2234567890", name="NWOSU EMEKA"),
        MockBeneficiary(ref="BEN-tolu", customer_ref=CORE_REF, bank_code="057", number="2044714471"),
        MockBeneficiary(ref="BEN-mama", customer_ref=CORE_REF, bank_code="058", number="0123450213"),
        MockBeneficiary(ref="BEN-musa", customer_ref=CORE_REF, bank_code="999992", number="8031447710"),
        MockCard(id="cd_1", customer_ref=CORE_REF, last4="8830", holder="Ronke Adeyemi"),
    ])
    db.flush()

    # The Gateway's side.
    db.add(Customer(id=CUSTOMER_ID, core_ref=CORE_REF, phone="+2348031234567", lang="en"))
    db.flush()
    db.add_all([
        Payee(id="py_tolu", customer_id=CUSTOMER_ID, nickname="Tolu", name="Adebayo Tolulope", bank_code="057",
              bank="Zenith Bank", account_last4="4471", beneficiary_ref="BEN-tolu",
              first_paid_at=now - timedelta(days=90)),
        Payee(id="py_mama", customer_id=CUSTOMER_ID, nickname="Mama", name="Adeyemi Folake", bank_code="058",
              bank="GTBank", account_last4="0213", beneficiary_ref="BEN-mama",
              first_paid_at=now - timedelta(days=400)),
        Payee(id="py_musa", customer_id=CUSTOMER_ID, nickname="Musa", name="Musa Ibrahim", bank_code="999992",
              bank="OPay", account_last4="7710", beneficiary_ref="BEN-musa"),
        Approver(id=APPROVER_ID, customer_id=CUSTOMER_ID, name="Funmi", phone="+2348059876543",
                 threshold_kobo=5_000_000),  # ₦50,000
    ])
    db.flush()
    db.add_all([
        Token(token=CUSTOMER_TOKEN, role="customer", customer_id=CUSTOMER_ID),
        Token(token=APPROVER_TOKEN, role="approver", customer_id=CUSTOMER_ID, approver_id=APPROVER_ID),
        Token(token=ASSISTANT_TOKEN, role="assistant", customer_id=CUSTOMER_ID),
    ])
    _seed_people(db)
    db.commit()


def _seed_people(db: Session) -> None:
    """Each saved person as a customer in their own language, with Ronke saved so they can send back."""
    now = utcnow()
    for i, (nickname, code, _, number, name, _) in enumerate(STARTERS, start=1):
        key = nickname.lower()
        customer_id = f"cus_{key}"
        if db.get(Customer, customer_id) is not None:
            continue
        lang, first_name, card = PEOPLE[nickname]
        ref = f"ZC-DEMO-{nickname.upper()}"
        full_name = " ".join(part.capitalize() for part in name.split())
        db.add(MockCustomer(ref=ref, full_name=full_name, first_name=first_name, bvn="", pin="2075",
                            pin_tries_left=3, per_transaction_kobo=100_000_000, daily_kobo=500_000_000))
        db.flush()
        db.add_all([
            MockAccount(id=f"ac_{key}", customer_ref=ref, bank_code=code, number=number, name="Current account",
                        balance_kobo=25_000_000),  # ₦250,000
            MockCard(id=f"cd_{key}", customer_ref=ref, last4=card, holder=full_name),
            MockBeneficiary(ref=f"BEN-ronke-{key}", customer_ref=ref, bank_code="057", number=ACCOUNT_NUMBER),
            Customer(id=customer_id, core_ref=ref, phone=f"+2348000000{i:03d}", lang=lang),
        ])
        db.flush()
        db.add_all([
            Payee(id=f"py_ronke_{key}", customer_id=customer_id, nickname="Ronke", name="Ronke Adeyemi",
                  bank_code="057", bank="Zenith Bank", account_last4=ACCOUNT_NUMBER[-4:],
                  beneficiary_ref=f"BEN-ronke-{key}", first_paid_at=now - timedelta(days=30)),
            Token(token=f"demo-{key}", role="customer", customer_id=customer_id),
        ])
        db.flush()


def give_starter_payees(db: Session, customer_id: str, core_ref: str) -> None:
    """A new sign-up's saved people: the bank's beneficiaries and the Gateway's payees, as for Ronke."""
    now = utcnow()
    for nickname, code, bank, number, name, paid in STARTERS:
        known = db.scalars(select(MockDirectoryEntry).where(MockDirectoryEntry.bank_code == code,
                                                           MockDirectoryEntry.number == number)).first()
        if known is None:
            db.add(MockDirectoryEntry(bank_code=code, number=number, name=name))
        ref = f"BEN-{nickname.lower()}-{core_ref[-6:]}"
        db.add(MockBeneficiary(ref=ref, customer_ref=core_ref, bank_code=code, number=number))
        db.flush()
        db.add(Payee(id=f"py_{nickname.lower()}_{customer_id[-6:]}", customer_id=customer_id, nickname=nickname,
                     name=" ".join(part.capitalize() for part in name.split()), bank_code=code, bank=bank,
                     account_last4=number[-4:], beneficiary_ref=ref,
                     first_paid_at=now - timedelta(days=60) if paid else None))
    db.flush()
