"""Money in words, words in money, and the mock's account rules."""

import pytest

from ominira import amounts
from ominira.core.mock import generated_name, unseal_pin_block


@pytest.mark.parametrize("n,text", [
    (0, "zero"), (7, "seven"), (19, "nineteen"), (20, "twenty"), (42, "forty-two"), (100, "one hundred"),
    (105, "one hundred and five"), (1_000, "one thousand"), (1_050, "one thousand and fifty"),
    (1_500, "one thousand, five hundred"), (20_000, "twenty thousand"), (150_000, "one hundred and fifty thousand"),
    (842_300, "eight hundred and forty-two thousand, three hundred"), (1_000_000, "one million"),
    (5_000_010, "five million and ten"), (2_000_000_000, "two billion"),
])
def test_words(n, text):
    assert amounts.words(n) == text


def test_spoken_and_shown():
    assert amounts.spoken(2_000_000) == "twenty thousand naira"
    assert amounts.spoken(550) == "five naira and fifty kobo"
    assert amounts.sentence(2_000_000) == "Twenty thousand naira."
    assert amounts.naira(84_230_000) == "₦842,300"
    assert amounts.naira(2_000_050) == "₦20,000.50"
    assert amounts.digit_words("4471") == "four, four, seven, one"


@pytest.mark.parametrize("text,kobo", [
    ("send twenty thousand to Tolu", 2_000_000),
    ("send 20000 to Tolu", 2_000_000),
    ("send 20,000 to Tolu", 2_000_000),
    ("₦20k", 2_000_000),
    ("N20,000", 2_000_000),
    ("20 thousand naira", 2_000_000),
    ("twenty thousand five hundred", 2_050_000),
    ("one hundred and fifty thousand", 15_000_000),
    ("1.5 million", 150_000_000),
    ("a thousand", 100_000),
    ("forty-two", 4_200),
    ("2 million 500 thousand", 250_000_000),
    ("send 5k give Mama", 500_000),
    ("send money to Tolu", None),
    ("send ACCOUNT_1 at GTBank", None),
    ("hello", None),
    ("zero", None),
])
def test_parse_kobo(text, kobo):
    assert amounts.parse_kobo(text) == kobo


def test_parse_stops_at_the_first_amount():
    assert amounts.parse_kobo("send 20000 to Tolu 5000 times") == 2_000_000
    assert amounts.parse_kobo("send 5000 to ACCOUNT_1 at GTBank") == 500_000


def test_pin_block_placeholder():
    assert unseal_pin_block("demo-7924") == "2075"
    assert unseal_pin_block("demo-79") is None
    assert unseal_pin_block("7924") is None
    assert unseal_pin_block("demo-abcd") is None


def test_generated_names_are_stable_and_nip_shaped():
    a = generated_name("058", "0123456789")
    assert a == generated_name("058", "0123456789") and a == a.upper() and len(a.split()) == 2
    assert generated_name("044", "0123456789") != a or generated_name("058", "0123456788") != a
