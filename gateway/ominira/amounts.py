"""Naira as words, and words as naira."""

import re

_ONES = ("zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen "
         "fifteen sixteen seventeen eighteen nineteen").split()
_TENS = {2: "twenty", 3: "thirty", 4: "forty", 5: "fifty", 6: "sixty", 7: "seventy", 8: "eighty", 9: "ninety"}
_SCALES = ((1_000_000_000, "billion"), (1_000_000, "million"), (1_000, "thousand"))


def _below_thousand(n: int) -> str:
    hundreds, rest = divmod(n, 100)
    parts = []
    if hundreds:
        parts.append(f"{_ONES[hundreds]} hundred")
    if rest:
        if rest < 20:
            tail = _ONES[rest]
        else:
            tens, ones = divmod(rest, 10)
            tail = _TENS[tens] + (f"-{_ONES[ones]}" if ones else "")
        parts.append(tail)
    return " and ".join(parts)


def words(n: int) -> str:
    """British style: 'one hundred and five', 'two thousand, three hundred', 'one million and ten'."""
    if n < 0:
        return "minus " + words(-n)
    if n < 1000:
        return _below_thousand(n) if n else "zero"
    for size, name in _SCALES:
        if n >= size:
            head, rest = divmod(n, size)
            text = f"{words(head)} {name}"
            if rest:
                text += (" and " if rest < 100 else ", ") + words(rest)
            return text
    raise AssertionError("unreachable")


def spoken(kobo: int) -> str:
    """'twenty thousand naira', or 'five naira and fifty kobo'."""
    naira, k = divmod(kobo, 100)
    text = f"{words(naira)} naira"
    if k:
        text += f" and {words(k)} kobo"
    return text


def sentence(kobo: int) -> str:
    s = spoken(kobo)
    return s[0].upper() + s[1:] + "."


def naira(kobo: int) -> str:
    """'₦20,000', or '₦20,000.50' when there are kobo."""
    whole, k = divmod(kobo, 100)
    return f"₦{whole:,}" + (f".{k:02d}" if k else "")


def digit_words(digits: str) -> str:
    """'4471' reads 'four, four, seven, one'."""
    return ", ".join(_ONES[int(d)] for d in digits if d.isdigit())


# ---- parsing --------------------------------------------------------------------------------

_SMALL = {w: i for i, w in enumerate(_ONES)} | {w: t * 10 for t, w in _TENS.items()}
_MULTIPLIERS = {"thousand": 1_000, "k": 1_000, "million": 1_000_000, "m": 1_000_000, "billion": 1_000_000_000}
_FILLER = {"and", "naira", "a", "of"}
_NUMBER = re.compile(r"^(\d+(?:\.\d+)?)(k|m)?$")
_LABELS = re.compile(r"ACCOUNT_\d+|\[(?:BVN|PIN)\]", re.IGNORECASE)


def _tokens(text: str) -> list[str]:
    text = _LABELS.sub(" | ", text.lower())
    text = re.sub(r"(?<=\d),(?=\d{3}\b)", "", text)  # 20,000 -> 20000
    text = re.sub(r"[₦]|(?<![a-z])n(?=\d)", " ", text)  # ₦20k, N20k
    text = text.replace("-", " ")
    return re.findall(r"\d+(?:\.\d+)?[km]?\b|[a-z]+|\|", text)


def parse_kobo(text: str) -> int | None:
    """The first amount in `text`, in kobo, or None. Stops at the next ordinary word."""
    total = 0.0
    group = 0.0
    seen = False
    for tok in _tokens(text):
        num = _NUMBER.match(tok)
        if num:
            if seen and group and not total:
                break  # "20000 5000": two numbers, keep the first
            value = float(num.group(1))
            suffix = num.group(2)
            if suffix:
                total += (group + value) * _MULTIPLIERS[suffix]
                group = 0.0
            else:
                group += value
            seen = True
        elif tok in _SMALL:
            group += _SMALL[tok]
            seen = True
        elif tok == "hundred":
            group = (group or 1.0) * 100
            seen = True
        elif tok in _MULTIPLIERS and (seen or tok not in ("k", "m")):
            total += (group or 1.0) * _MULTIPLIERS[tok]
            group = 0.0
            seen = True
        elif tok in _FILLER:
            continue
        elif seen:
            break
    value = total + group
    if not seen or value <= 0:
        return None
    return round(value * 100)
