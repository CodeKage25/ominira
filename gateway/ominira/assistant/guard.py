"""Checks that run in code before any model sees a turn.

- A spoken PIN ("my PIN is 2075", "pin na two zero seven five") stops the turn: nothing is
  drafted and no model is called.
- Account numbers (10 digits) become ACCOUNT_1, ACCOUNT_2, ...; BVNs and other 11-digit numbers
  become [BVN]. The real numbers stay in a map that never leaves the Gateway, so the model can
  pass a label to a tool without ever seeing the number. Numbers said as words ("zero one two
  ...") and numbers split by spaces ("2044 714 471") are caught too.
"""

import re

_DIGIT_WORDS = {"zero": "0", "oh": "0", "o": "0", "one": "1", "two": "2", "three": "3", "four": "4",
                "five": "5", "six": "6", "seven": "7", "eight": "8", "nine": "9"}
_DW = r"(?:zero|oh|one|two|three|four|five|six|seven|eight|nine)"

# The words that may sit between "PIN" and the digits: "is", "na", ":", "number", "code"...
_GAP = r"(?:\s|[:=,\-]|\bis\b|\bna\b|\bbe\b|\bdey\b|\bnumber\b|\bcode\b|\bit's\b|\bits\b|\bmy\b|\bsay\b)*"
_PIN_DIGITS = rf"(?:(?<!\d)(?:\d[\s.,\-]?){{3,5}}\d(?!\d)|(?:{_DW}\b[\s,.\-]*){{4,6}})"
_PIN = re.compile(rf"\b(?:pulse\s+)?(?:pin|passcode|password)\b{_GAP}{_PIN_DIGITS}", re.IGNORECASE)

_DIGIT_WORD_RUN = re.compile(rf"\b{_DW}(?:[\s,\-]+{_DW}){{9,}}\b", re.IGNORECASE)
_NUMBER_GROUP = re.compile(r"(?<![\w])\d+(?:[ \-]\d+)*(?![\w])")


def spoken_pin(text: str) -> bool:
    return _PIN.search(text) is not None


def _words_to_digits(match: re.Match) -> str:
    return "".join(_DIGIT_WORDS[w.lower()] for w in re.findall(r"[a-zA-Z]+", match.group(0)))


def _classify(run: str, accounts: dict[str, str]) -> str:
    if len(run) == 10:
        label = next((k for k, v in accounts.items() if v == run), None)
        if label is None:
            label = f"ACCOUNT_{len(accounts) + 1}"
            accounts[label] = run
        return label
    if len(run) == 11:
        return "[BVN]"
    return run


def _mask_group(group: str, accounts: dict[str, str]) -> str:
    runs = re.split(r"[ \-]", group)
    out: list[str] = []
    i = 0
    while i < len(runs):
        run = runs[i]
        if len(run) >= 10:
            out.append(_classify(run, accounts) if len(run) <= 11 else "[NUMBER]")
            i += 1
            continue
        if len(run) <= 4:
            # Short pieces next to each other may be one number said in chunks.
            total, j, best = 0, i, None
            while j < len(runs) and len(runs[j]) <= 4 and total + len(runs[j]) <= 11:
                total += len(runs[j])
                j += 1
                if total in (10, 11):
                    best = j
            if best is not None:
                out.append(_classify("".join(runs[i:best]), accounts))
                i = best
                continue
        out.append(run)
        i += 1
    return " ".join(out)


def mask(text: str) -> tuple[str, dict[str, str]]:
    """Returns the masked text and the label-to-number map for this turn."""
    accounts: dict[str, str] = {}
    text = _PIN.sub("[PIN]", text)
    text = _DIGIT_WORD_RUN.sub(_words_to_digits, text)
    text = _NUMBER_GROUP.sub(lambda m: _mask_group(m.group(0), accounts), text)
    return text, accounts
