"""The demo world, the same story the app's offline fake tells.

Ronke Adeyemi banks with Zenith; her Pulse PIN is 2 0 7 5. She has paid Tolu and Mama before.
Musa Ibrahim is saved but never paid, so a large transfer to him during a phone call triggers the
scam pause. Funmi co-approves transfers above ₦50,000.

Tokens: demo-customer (Ronke's phone), demo-approver (Funmi), demo-assistant (the assistant's
read-and-draft identity).
"""

from datetime import timedelta

from sqlalchemy import Engine
from sqlalchemy.orm import Session

from ominira.core.mock_tables import (
    MockAccount, MockBeneficiary, MockCard, MockCustomer, MockDirectoryEntry, MockTxn,
)
from ominira.database import Base, utcnow
from ominira.tables import Approver, Customer, Payee, Token

CUSTOMER_ID = "cus_ronke"
CORE_REF = "ZC-2210934471"
APPROVER_ID = "ap_funmi"
CUSTOMER_TOKEN = "demo-customer"
APPROVER_TOKEN = "demo-approver"
ASSISTANT_TOKEN = "demo-assistant"


def ensure(engine: Engine) -> None:
    """Create tables, and seed the demo world if it is not there yet."""
    Base.metadata.create_all(engine)
    with Session(engine) as db:
        if db.get(Customer, CUSTOMER_ID) is None:
            _seed(db)


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
    db.add(MockAccount(id="ac_1", customer_ref=CORE_REF, number="2081234567", name="Current account",
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
    db.commit()
