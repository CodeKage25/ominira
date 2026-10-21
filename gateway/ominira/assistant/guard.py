"""Checks in code before any model sees a turn: a spoken PIN, and number masking."""

import re

_DIGIT_WORDS = {"zero": "0", "oh": "0", "one": "1", "two": "2", "three": "3", "four": "4", "five": "5",
                "six": "6", "seven": "7", "eight": "8", "nine": "9"}
_DW = r"(?:zero|oh|one|two|three|four|five|six|seven|eight|nine)"
_DIGIT_WORD_RUN = re.compile(rf"\b{_DW}(?:[\s,\-]+{_DW}){{3,}}\b", re.IGNORECASE)

_SEP = r"[ .,/\-]"
_GROUP = re.compile(rf"(?<!\d)\d+(?:{_SEP}\d+)*(?!\d)")

_PIN_WORDS = re.compile(
    r"\b(?:pin|passcode|pass\s+code|password|secret\s+code|code|"
    r"n[oọ]́?mb[aà]̀?\s+[aà]̀?[sṣ]̣?[ií]́?r[ií]́?|"  # Yorùbá: nọ́mbà àṣírí
    r"lamba[r]?\s+sirri|"  # Hausa: lambar sirri
    r"n[oọ]mba\s+nzuzo)\b",  # Igbo: nọmba nzuzo
    re.IGNORECASE,
)
_PIN_WINDOW = 30  # characters between the PIN word and the digits, either side
_AMOUNT_BEFORE = {"send", "sen", "transfer", "pay", "give", "dash", "of", "₦", "n", "ngn"}
_AMOUNT_AFTER = {"naira", "k", "thousand", "million", "kobo", "ngn", "bucks"}


def _words_to_digits(match: re.Match) -> str:
    return "".join(_DIGIT_WORDS[w.lower()] for w in re.findall(r"[a-zA-Z]+", match.group(0)))


def _normalise(text: str) -> str:
    """'two zero seven five' -> '2075'. Runs of four or more digit words only."""
    return _DIGIT_WORD_RUN.sub(_words_to_digits, text)


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
        if len(pieces) > 1 and all(e - s <= 2 for s, e in pieces):
            digits = sum(e - s for s, e in pieces)
            if 4 <= digits <= 6:
                spans.append((group.start(), group.end()))
            continue
        spans.extend((s, e) for s, e in pieces if 4 <= e - s <= 6)
    return [(s, e) for s, e in spans if not _looks_like_amount(text, s, e)]


def _pin_spans(normalised: str) -> list[tuple[int, int]]:
    words = [(m.start(), m.end()) for m in _PIN_WORDS.finditer(normalised)]
    if not words:
        return []
    found = []
    for s, e in _pin_candidates(normalised):
        if any((s - we if s >= we else ws - e) <= _PIN_WINDOW for ws, we in words):
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
    parts = re.split(f"({_SEP})", group)
    pieces, seps = parts[0::2], parts[1::2]
    out: list[str] = []
    i = 0
    while i < len(pieces):
        run = pieces[i]
        if len(run) >= 10:
            out.append(_classify(run, accounts))
            i += 1
        else:
            best = None
            if len(run) <= 4:
                # Short pieces side by side may be one number said or written in chunks.
                total, j = 0, i
                while j < len(pieces) and len(pieces[j]) <= 4 and total + len(pieces[j]) <= 19:
                    total += len(pieces[j])
                    j += 1
                    if total >= 10 and j - i >= 2:
                        best = j
            if best is not None:
                out.append(_classify("".join(pieces[i:best]), accounts))
                i = best
            else:
                out.append(run)
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
