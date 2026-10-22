"""Checks in code before any model sees a turn: a spoken PIN, and number masking."""

import re

_DIGIT_WORDS = {"zero": "0", "oh": "0", "one": "1", "two": "2", "three": "3", "four": "4", "five": "5",
                "six": "6", "seven": "7", "eight": "8", "nine": "9"}
_DW = r"(?:zero|oh|one|two|three|four|five|six|seven|eight|nine)"
_DIGIT_WORD_RUN = re.compile(rf"\b{_DW}(?:[\s,\-]+{_DW}){{3,}}\b", re.IGNORECASE)

_TENS = {"twenty": "2", "thirty": "3", "forty": "4", "fifty": "5", "sixty": "6", "seventy": "7", "eighty": "8",
         "ninety": "9"}
_TEENS = {"ten": "10", "eleven": "11", "twelve": "12", "thirteen": "13", "fourteen": "14", "fifteen": "15",
          "sixteen": "16", "seventeen": "17", "eighteen": "18", "nineteen": "19"}
_UNIT = r"(?:one|two|three|four|five|six|seven|eight|nine)"
_PAIR = rf"(?:(?:{'|'.join(_TENS)})(?:[\s\-]+{_UNIT})?|{'|'.join(_TEENS)}|oh\s+{_UNIT})"
# "twenty seventy five" -> 2075: a PIN said as two or three two-digit numbers.
_PAIR_RUN = re.compile(rf"\b{_PAIR}(?:\s+{_PAIR}){{1,2}}\b", re.IGNORECASE)

_SEP = r"[ .,/\-]"
_GROUP = re.compile(rf"(?<!\d)\d+(?:{_SEP}\d+)*(?!\d)")
_THOUSANDS = re.compile(r"\d{1,3}(?:,\d{3})+(?:\.\d+)?")

_NOT_LETTER_BEFORE, _NOT_LETTER_AFTER = r"(?<![^\W\d_])", r"(?![^\W\d_])"
_PIN_WORD = (
    r"(?:pin|p\.\s?i\.\s?n\.?|passcode|pass\s+code|password|secret\s+code|code|"
    r"n[oọ]́?mb[aà]̀?\s+[aà]̀?[sṣ]̣?[ií]́?r[ií]́?|"  # Yorùbá: nọ́mbà àṣírí
    r"(?:lamba|kalma)r?\s+sirri(?:na|nki|nku|nsa|nta|nsu|nmu)?|"  # Hausa: lambar sirri, kalmar sirri
    r"n[oọ]mba\s+nzuzo|okwuntughe)"  # Igbo: nọmba nzuzo, okwuntughe
)
_PIN_WORDS = re.compile(_NOT_LETTER_BEFORE + _PIN_WORD + _NOT_LETTER_AFTER, re.IGNORECASE)
# "pin 2075", "PIN2075", "pin of 2075": the PIN word right before the digits wins over any amount reading.
_PIN_RIGHT_BEFORE = re.compile(_NOT_LETTER_BEFORE + _PIN_WORD + r"[\s:=]*(?:(?:is|of|na)\s+)?$", re.IGNORECASE)
# Digits before the PIN word only count when tied to it: "2075 is my PIN", "2075 ni pin mi".
_TIED = re.compile(_NOT_LETTER_BEFORE + r"(?:is|was|na|be|ni|ce|bụ)" + _NOT_LETTER_AFTER, re.IGNORECASE)
_SENTENCE_END = re.compile(r"[.?!](?:\s|$)")
_PIN_WINDOW = 40  # characters between the PIN word and the digits, either side
_AMOUNT_BEFORE = {"send", "sen", "transfer", "pay", "give", "dash", "of", "₦", "n", "ngn"}
_AMOUNT_AFTER = {"naira", "k", "thousand", "million", "kobo", "ngn", "bucks"}


def _words_to_digits(match: re.Match) -> str:
    return "".join(_DIGIT_WORDS[w.lower()] for w in re.findall(r"[a-zA-Z]+", match.group(0)))


def _pairs_to_digits(match: re.Match) -> str:
    out = ""
    for m in re.finditer(rf"[a-zA-Z]+(?:[\s\-]+{_UNIT}\b)?", match.group(0), re.IGNORECASE):
        words = re.findall(r"[a-zA-Z]+", m.group(0).lower())
        if words[0] in _TENS:
            out += _TENS[words[0]] + (_DIGIT_WORDS[words[1]] if len(words) > 1 else "0")
        elif words[0] in _TEENS:
            out += _TEENS[words[0]]
        else:
            out += "0" + _DIGIT_WORDS[words[1]]
    return out


def _normalise(text: str) -> str:
    """'two zero seven five' and 'twenty seventy five' -> '2075'."""
    return _PAIR_RUN.sub(_pairs_to_digits, _DIGIT_WORD_RUN.sub(_words_to_digits, text))


def _looks_like_amount(text: str, start: int, end: int) -> bool:
    before = text[:start]
    if before.endswith(("₦", "N", "n")) and (len(before) < 2 or not before[-2].isalpha()):
        return True
    prev = re.search(r"([^\s\d]+)\s*$", before)
    if prev and prev.group(1).lower().strip(".,:;!?") in _AMOUNT_BEFORE:
        return True
    after = text[end:]
    if re.match(r"[kKmM]\b", after):
        return True
    nxt = re.match(r"\s*([^\s\d]+)", after)
    return bool(nxt and nxt.group(1).lower().strip(".,:;!?") in _AMOUNT_AFTER)


def _pin_candidates(text: str) -> list[tuple[int, int]]:
    spans = []
    for group in _GROUP.finditer(text):
        pieces = [(m.start() + group.start(), m.end() + group.start()) for m in re.finditer(r"\d+", group.group(0))]
        if len(pieces) > 1 and 4 <= sum(e - s for s, e in pieces) <= 6:
            spans.append((group.start(), group.end()))
            continue
        spans.extend((s, e) for s, e in pieces if 4 <= e - s <= 6)
    return [(s, e) for s, e in spans
            if _PIN_RIGHT_BEFORE.search(text[:s]) or not _looks_like_amount(text, s, e)]


def _near(text: str, s: int, e: int, ws: int, we: int) -> bool:
    gap = text[we:s] if s >= we else text[e:ws]
    if len(gap) > _PIN_WINDOW or _SENTENCE_END.search(gap):
        return False
    return s >= we or bool(_TIED.search(gap))


def _pin_spans(normalised: str) -> list[tuple[int, int]]:
    words = [(m.start(), m.end()) for m in _PIN_WORDS.finditer(normalised)]
    if not words:
        return []
    found = []
    for s, e in _pin_candidates(normalised):
        if any(_near(normalised, s, e, ws, we) for ws, we in words):
            found.append((s, e))
    return found


def spoken_pin(text: str) -> bool:
    return bool(_pin_spans(_normalise(text)))


def _classify(run: str, accounts: dict[str, str]) -> str:
    if len(run) == 10:
        label = next((k for k, v in accounts.items() if v == run), None)
        if label is None:
            taken = [int(k.split("_")[1]) for k in accounts if k.startswith("ACCOUNT_")]
            label = f"ACCOUNT_{max(taken, default=0) + 1}"
            accounts[label] = run
        return label
    if len(run) == 11:
        return "[BVN]"
    if 12 <= len(run) <= 19:
        return "[CARD]"
    return "[NUMBER]"


def _mask_group(group: str, accounts: dict[str, str]) -> str:
    if _THOUSANDS.fullmatch(group):
        return group  # 20,000 or 1,500,000.50: an amount
    parts = re.split(f"({_SEP})", group)
    pieces, seps = parts[0::2], parts[1::2]
    out: list[str] = []
    i = 0
    while i < len(pieces):
        total = len(pieces[i])
        best = i + 1 if total >= 10 else None
        if best is None:
            # Pieces side by side, of any size, may be one number said or written in chunks.
            for j in range(i + 1, len(pieces)):
                total += len(pieces[j])
                if len(pieces[j]) >= 10 or total > 19:
                    break
                if total >= 10:
                    best = j + 1
        if best is not None:
            out.append(_classify("".join(pieces[i:best]), accounts))
            i = best
        else:
            out.append(pieces[i])
            i += 1
        if i < len(pieces):
            out.append(seps[i - 1])
    return "".join(out)


def mask(text: str, known: dict[str, str] | None = None) -> tuple[str, dict[str, str]]:
    """(masked text, labels). A number in `known` keeps its label; new ones get the next."""
    accounts = dict(known or {})
    text = _normalise(text)
    for s, e in reversed(_pin_spans(text)):
        text = text[:s] + "[PIN]" + text[e:]

    def swap(m: re.Match) -> str:
        masked = _mask_group(m.group(0), accounts)
        glued = m.start() > 0 and text[m.start() - 1].isalpha() and masked != m.group(0)
        return (" " if glued else "") + masked

    return _GROUP.sub(swap, text), accounts
