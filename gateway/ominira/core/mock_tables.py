"""Tables standing in for Zenith's systems. Only MockZenith queries them."""

from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from ominira.database import Base, utcnow


class MockCustomer(Base):
    __tablename__ = "mock_customers"

    ref: Mapped[str] = mapped_column(String(64), primary_key=True)
    full_name: Mapped[str] = mapped_column(String(120))
    first_name: Mapped[str] = mapped_column(String(60))
    bvn: Mapped[str] = mapped_column(String(11))
    pin: Mapped[str] = mapped_column(String(4))  # mock only; a real core keeps PIN offsets in an HSM
    pin_tries_left: Mapped[int] = mapped_column(Integer, default=3)
    per_transaction_kobo: Mapped[int] = mapped_column(BigInteger)
    daily_kobo: Mapped[int] = mapped_column(BigInteger)


class MockAccount(Base):
    __tablename__ = "mock_accounts"

    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    customer_ref: Mapped[str] = mapped_column(ForeignKey("mock_customers.ref"))
    number: Mapped[str] = mapped_column(String(10))
    name: Mapped[str] = mapped_column(String(60))
    balance_kobo: Mapped[int] = mapped_column(BigInteger)


class MockTxn(Base):
    __tablename__ = "mock_transactions"

    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    account_id: Mapped[str] = mapped_column(ForeignKey("mock_accounts.id"))
    who: Mapped[str] = mapped_column(String(120))
    amount_kobo: Mapped[int] = mapped_column(BigInteger)
    incoming: Mapped[bool] = mapped_column(Boolean)
    reference: Mapped[str] = mapped_column(String(40))
    at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class MockDirectoryEntry(Base):
    """Accounts that name enquiry knows by name. Other valid numbers get a generated name."""

    __tablename__ = "mock_directory"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    bank_code: Mapped[str] = mapped_column(String(10))
    number: Mapped[str] = mapped_column(String(10))
    name: Mapped[str] = mapped_column(String(120))
    status: Mapped[str] = mapped_column(String(12), default="active")  # active | dormant


class MockBeneficiary(Base):
    __tablename__ = "mock_beneficiaries"

    ref: Mapped[str] = mapped_column(String(64), primary_key=True)
    customer_ref: Mapped[str] = mapped_column(ForeignKey("mock_customers.ref"))
    bank_code: Mapped[str] = mapped_column(String(10))
    number: Mapped[str] = mapped_column(String(10))


class MockEnquiry(Base):
    __tablename__ = "mock_enquiries"

    ref: Mapped[str] = mapped_column(String(40), primary_key=True)
    bank_code: Mapped[str] = mapped_column(String(10))
    number: Mapped[str] = mapped_column(String(10))
    name: Mapped[str] = mapped_column(String(120))
    at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class MockMove(Base):
    """One transfer or card block, keyed by the caller's idempotency key."""

    __tablename__ = "mock_moves"

    idempotency_key: Mapped[str] = mapped_column(String(64), primary_key=True)
    customer_ref: Mapped[str] = mapped_column(String(64))
    kind: Mapped[str] = mapped_column(String(16))  # transfer | card_block
    amount_kobo: Mapped[int] = mapped_column(BigInteger, default=0)
    code: Mapped[str] = mapped_column(String(2))
    reference: Mapped[str | None] = mapped_column(String(40), nullable=True)
    settles_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class MockCard(Base):
    __tablename__ = "mock_cards"

    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    customer_ref: Mapped[str] = mapped_column(ForeignKey("mock_customers.ref"))
    last4: Mapped[str] = mapped_column(String(4))
    holder: Mapped[str] = mapped_column(String(60))
    status: Mapped[str] = mapped_column(String(12), default="active")
