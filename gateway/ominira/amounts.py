"""Naira as words, and words as naira, in English and in Yorùbá, Hausa and Igbo."""

import re
import unicodedata

LOCAL = ("yo", "ha", "ig")

_ONES = ("zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen "
         "fifteen sixteen seventeen eighteen nineteen").split()
_TENS = {2: "twenty", 3: "thirty", 4: "forty", 5: "fifty", 6: "sixty", 7: "seventy", 8: "eighty", 9: "ninety"}
_SCALES = ((1_000_000_000, "billion"), (1_000_000, "million"), (1_000, "thousand"))

# Yorùbá, Hausa and Igbo number words are unreviewed drafts until native speakers check them.
_YO_ONES = "òdo kan méjì mẹ́ta mẹ́rin márùn-ún mẹ́fà méje mẹ́jọ mẹ́sàn-án mẹ́wàá".split()
_YO_TENS = {
    20: "ogún", 30: "ọgbọ̀n", 40: "ogójì", 50: "àádọ́ta", 60: "ọgọ́ta", 70: "àádọ́rin", 80: "ọgọ́rin",
    90: "àádọ́rùn-ún", 100: "ọgọ́rùn-ún", 110: "àádọ́fà", 120: "ọgọ́fà", 130: "àádóje", 140: "ogóje",
    150: "àádọ́jọ", 160: "ọgọ́jọ", 170: "àádọ́sàn-án", 180: "ọgọ́sàn-án", 190: "àádọ́wàá", 200: "igba",
}
# The same tens after lé (plus) or dín (less): 21 is mọ́kànlélógún, 29 is mọ́kàndínlọ́gbọ̀n.
_YO_AFTER = {
    20: "lógún", 30: "lọ́gbọ̀n", 40: "lógójì", 50: "láàádọ́ta", 60: "lọ́gọ́ta", 70: "láàádọ́rin", 80: "lọ́gọ́rin",
    90: "láàádọ́rùn-ún", 100: "lọ́gọ́rùn-ún", 110: "láàádọ́fà", 120: "lọ́gọ́fà", 130: "láàádóje", 140: "lógóje",
    150: "láàádọ́jọ", 160: "lọ́gọ́jọ", 170: "láàádọ́sàn-án", 180: "lọ́gọ́sàn-án", 190: "láàádọ́wàá", 200: "nígba",
}
_YO_PREFIX = {1: "mọ́kàn", 2: "méjì", 3: "mẹ́tà", 4: "mẹ́rìn", 5: "márùn"}
_YO_HUNDREDS = {200: "igba", 300: "ọ̀ọ́dúnrún", 400: "irinwó", 500: "ẹ̀ẹ́dẹ́gbẹ̀ta", 600: "ẹgbẹ̀ta",
                700: "ẹ̀ẹ́dẹ́gbẹ̀rin", 800: "ẹgbẹ̀rin", 900: "ẹ̀ẹ́dẹ́gbẹ̀rún"}
_YO_DIGITS = "òdo ọ̀kan méjì mẹ́ta mẹ́rin márùn-ún mẹ́fà méje mẹ́jọ mẹ́sàn-án".split()

_HA_ONES = "sifili ɗaya biyu uku huɗu biyar shida bakwai takwas tara goma".split()
_HA_TENS = {2: "ashirin", 3: "talatin", 4: "arba'in", 5: "hamsin", 6: "sittin", 7: "saba'in", 8: "tamanin",
            9: "casa'in"}

_IG_ONES = "efu otu abụọ atọ anọ ise isii asaa asatọ itoolu".split()


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


def _yo_below_thousand(n: int) -> str:
    if n <= 10:
        return _YO_ONES[n]
    if n < 15:
        return _YO_PREFIX[n - 10] + "lá"
    if n <= 200:
        if n % 10 == 0:
            return _YO_TENS[n]
        tens, ones = n // 10 * 10, n % 10
        if ones <= 4:
            return f"{_YO_PREFIX[ones]}lé{_YO_AFTER[tens]}"
        return f"{_YO_PREFIX[10 - ones]}dín{_YO_AFTER[tens + 10]}"
    hundreds = n // 100 * 100
    rest = n - hundreds
    return _YO_HUNDREDS[hundreds] + (f" ó lé {_yo_below_thousand(rest)}" if rest else "")


def _ha_below_thousand(n: int) -> str:
    hundreds, rest = divmod(n, 100)
    parts = []
    if hundreds:
        parts.append("ɗari" if hundreds == 1 else f"ɗari {_HA_ONES[hundreds]}")
    if rest:
        tens, ones = divmod(rest, 10)
        if rest <= 10:
            parts.append(_HA_ONES[rest])
        elif tens == 1:
            parts.append(f"goma sha {_HA_ONES[ones]}")
        else:
            parts.append(_HA_TENS[tens] + (f" da {_HA_ONES[ones]}" if ones else ""))
    return " da ".join(parts)


def _ig_below_thousand(n: int) -> str:
    hundreds, rest = divmod(n, 100)
    parts = []
    if hundreds:
        parts.append("narị" if hundreds == 1 else f"narị {_IG_ONES[hundreds]}")
    if rest:
        tens, ones = divmod(rest, 10)
        if tens:
            parts.append(("iri" if tens == 1 else f"iri {_IG_ONES[tens]}") + (f" na {_IG_ONES[ones]}" if ones else ""))
        else:
            parts.append(_IG_ONES[ones])
    return " na ".join(parts)


# Per language: words below a thousand, (billion, million, thousand), the join between those, zero, naira, kobo.
_LOCAL_WORDS = {
    "yo": (_yo_below_thousand, ("bílíọ̀nù", "mílíọ̀nù", "ẹgbẹ̀rún"), ", ó lé ", "òdo", "náírà", "àti kọ́bọ̀"),
    "ha": (_ha_below_thousand, ("biliyan", "miliyan", "dubu"), ", da ", "sifili", "naira", "da kobo"),
    "ig": (_ig_below_thousand, ("ijeri", "nde", "puku"), ", na ", "efu", "naịra", "na kobo"),
}


def _local_words(n: int, lang: str) -> str:
    below, scales, join, zero, _, _ = _LOCAL_WORDS[lang]
    if n == 0:
        return zero
    parts = []
    for (size, _), name in zip(_SCALES, scales):
        head, n = divmod(n, size)
        if not head:
            continue
        count = _local_words(head, lang)
        if head == 1 and lang == "ha" and size == 1_000:
            parts.append(name)  # dubu, not dubu ɗaya
        elif head == 1 and lang == "ig":
            parts.append(f"otu {name}")
        else:
            parts.append(f"{name} {count}")
    if n:
        parts.append(below(n))
    return join.join(parts)


def words(n: int, lang: str = "en") -> str:
    """English is British style: 'one hundred and five', 'two thousand, three hundred', 'one million and ten'."""
    if n < 0:
        return "minus " + words(-n, lang)
    if lang in LOCAL:
        return _local_words(n, lang)
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


def spoken(kobo: int, lang: str = "en") -> str:
    """'twenty thousand naira', 'naira dubu ashirin', or 'five naira and fifty kobo'."""
    naira, k = divmod(kobo, 100)
    if lang in LOCAL:
        *_, currency, and_kobo = _LOCAL_WORDS[lang]
        text = f"{currency} {words(naira, lang)}"
        return text + (f" {and_kobo} {words(k, lang)}" if k else "")
    text = f"{words(naira)} naira"
    if k:
        text += f" and {words(k)} kobo"
    return text


def sentence(kobo: int, lang: str = "en") -> str:
    s = spoken(kobo, lang)
    return s[0].upper() + s[1:] + "."


def naira(kobo: int) -> str:
    """'₦20,000', or '₦20,000.50' when there are kobo."""
    whole, k = divmod(kobo, 100)
    return f"₦{whole:,}" + (f".{k:02d}" if k else "")


def digit_words(digits: str, lang: str = "en") -> str:
    """'4471' reads 'four, four, seven, one'."""
    names = {"yo": _YO_DIGITS, "ha": _HA_ONES, "ig": _IG_ONES}.get(lang, _ONES)
    return ", ".join(names[int(d)] for d in digits if d.isdigit())


# ---- parsing --------------------------------------------------------------------------------

_SMALL = {w: i for i, w in enumerate(_ONES)} | {w: t * 10 for t, w in _TENS.items()}
_MULTIPLIERS = {"thousand": 1_000, "k": 1_000, "million": 1_000_000, "m": 1_000_000, "billion": 1_000_000_000}
_FILLER = {"and", "naira", "a", "of"}
_NUMBER = re.compile(r"^(\d+(?:\.\d+)?)(k|m)?$")
_LABELS = re.compile(r"ACCOUNT_\d+|\[(?:BVN|PIN)\]", re.IGNORECASE)
_HOOKED = str.maketrans({"ɗ": "d", "ƙ": "k", "ɓ": "b", "ƴ": "y", "'": "", "’": ""})


def fold(text: str) -> str:
    """Lower case without tone marks or hooked letters, as speech to text often writes it: 'ẹgbẹ̀rún' -> 'egberun'."""
    bare = unicodedata.normalize("NFD", text.lower().translate(_HOOKED))
    bare = "".join(ch for ch in bare if not unicodedata.combining(ch))
    return re.sub(r"(?<=[a-z])-(?=(?:un|an)\b)", "", bare)  # márùn-ún is one word


def _tokens(text: str) -> list[str]:
    text = _LABELS.sub(" | ", text.lower())
    text = re.sub(r"(?<=\d),(?=\d{3}\b)", "", text)  # 20,000 -> 20000
    text = re.sub(r"[₦]|(?<![a-z])n(?=\d)", " ", text)  # ₦20k, N20k
    text = fold(text).replace("-", " ")
    return re.findall(r"\d+(?:\.\d+)?[km]?\b|[a-z]+|[|,]", text)


def _english(tokens: list[str]) -> float | None:
    total = 0.0
    group = 0.0
    seen = False
    for tok in tokens:
        if tok == ",":
            continue
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
    return value if seen and value > 0 else None


def _vocab(lang: str) -> tuple[dict[str, int], dict[str, int], set[str]]:
    """(numbers, multipliers that take a count after them, joining words), all folded."""
    if lang == "yo":
        units = {fold(_yo_below_thousand(n)): n for n in range(0, 201)}
        units |= {fold(w): n for n, w in _YO_HUNDREDS.items()} | {fold(w): n for n, w in enumerate(_YO_DIGITS)}
        # Counting forms and common spellings without the nasal echo.
        units |= {"ookan": 1, "eji": 2, "eta": 3, "erin": 4, "arun": 5, "efa": 6, "eje": 7, "ejo": 8, "esan": 9,
                  "ewa": 10, "marun": 5, "mesan": 9, "mewa": 10, "aadorun": 90, "ogorun": 100, "meedogun": 15,
                  "meedogbon": 25}
        mults = {"egberun": 1_000, "egbaa": 2_000, "milionu": 1_000_000, "milioonu": 1_000_000,
                 "bilionu": 1_000_000_000}
        return units, mults, {"o", "le", "lona", "ati"}
    if lang == "ha":
        units = {fold(w): n for n, w in enumerate(_HA_ONES)} | {fold(w): t * 10 for t, w in _HA_TENS.items()}
        mults = {"dari": 100, "dubu": 1_000, "miliyan": 1_000_000, "biliyan": 1_000_000_000}
        return units, mults, {"da", "sha"}
    units = {fold(w): n for n, w in enumerate(_IG_ONES)}
    mults = {"iri": 10, "nari": 100, "puku": 1_000, "nde": 1_000_000, "ijeri": 1_000_000_000}
    return units, mults, {"na"}


_VOCAB = {lang: _vocab(lang) for lang in LOCAL}


def _place(value: float) -> float:
    return 10 ** (len(str(int(value))) - 1) if value >= 1 else 1


def _term(tokens: list[str], i: int, vocab) -> tuple[float, float, int] | None:
    """(value, place, next index) of one number at i: '20k', 'ogún', or 'dubu ashirin'."""
    units, mults, _ = vocab
    if i >= len(tokens):
        return None
    tok = tokens[i]
    num = _NUMBER.match(tok)
    if tok in mults:
        size = mults[tok]
        count, after = _number(tokens, i + 1, vocab, cap=min(size, 1_000))
        return size * (count or 1), size, after
    if num:
        value = float(num.group(1)) * _MULTIPLIERS.get(num.group(2) or "", 1)
    elif tok in units:
        value = units[tok]
    else:
        return None
    after = i + 1
    if after < len(tokens) and mults.get(tokens[after], 0) > value:
        return value * mults[tokens[after]], mults[tokens[after]], after + 1  # "otu puku": the count first
    return value, _place(value), after


def _number(tokens: list[str], i: int, vocab, cap: int | None = None) -> tuple[float, int]:
    """Terms joined by 'da', 'na' or 'ó lé', each smaller than the one before. A count stops at a comma."""
    joins = vocab[2]
    total, last = 0.0, None
    while i < len(tokens):
        j = i
        if total:
            if cap and tokens[j] == ",":
                break
            while j < len(tokens) and (tokens[j] in joins or tokens[j] == ","):
                j += 1
        term = _term(tokens, j, vocab)
        if term is None:
            break
        value, place, after = term
        if (last is not None and place >= last) or (cap and total + value >= cap):
            break
        total, last, i = total + value, place, after
    return total, i


def _local(tokens: list[str], lang: str) -> float | None:
    units, mults, _ = _VOCAB[lang]
    for i, tok in enumerate(tokens):
        # "owó kan" is "some money", not one naira.
        if lang == "yo" and tok == "kan" and i and tokens[i - 1] == "owo":
            continue
        if _NUMBER.match(tok) or tok in units or tok in mults:
            value, _ = _number(tokens, i, _VOCAB[lang])
            return value or None
    return None


def parse_kobo(text: str, lang: str = "en") -> int | None:
    """The first amount in `text`, in kobo, or None. In Yorùbá, Hausa or Igbo, English words still count."""
    tokens = _tokens(text)
    value = _local(tokens, lang) if lang in LOCAL else None
    if value is None:
        value = _english(tokens)
    return round(value * 100) if value else None
