"""Short spoken answers on the phone line, in the five languages, as the app hears them (see the app's Answers.kt).

A clear no beats a yes. Yorùbá, Hausa and Igbo words are unreviewed drafts, matched without their tone marks."""

import difflib
import re
import unicodedata

_YES = {"yes", "yeah", "yea", "yep", "yup", "ok", "okay", "sure", "correct", "right", "alright", "send", "allow",
        "agree", "oya", "ehen", "beeni", "bee", "eh", "toh", "naam", "ee", "ehh"}
_YES_PHRASES = ("go ahead", "go on", "na so", "e correct", "send it", "mo gba", "na yarda", "ekwere m")
_NO = {"no", "nope", "nah", "not", "dont", "wrong", "cancel", "stop", "rara", "beeko", "aa", "mba", "kwusi",
       "tsaya", "duro"}
_NO_PHRASES = ("no o", "abeg no", "mi o gba", "ban yarda", "ekweghi m")
_BYE = {"bye", "goodbye", "nothing", "dabo", "kaodi"}
_BYE_PHRASES = ("that is all", "that's all", "no thank you", "bye bye", "o dabo", "sai an jima", "ka o di",
                "nothing else")
_LANGUAGES = {
    "en": ("english", "oyinbo"),
    "pcm": ("pidgin", "pigin", "broken"),
    "yo": ("yoruba", "yorouba"),
    "ha": ("hausa", "housa", "awusa"),
    "ig": ("igbo", "ibo", "egbo"),
}
_DIGITS = {"zero": "0", "oh": "0", "o": "0", "nought": "0", "one": "1", "two": "2", "to": "2", "too": "2",
           "three": "3", "four": "4", "for": "4", "five": "5", "six": "6", "seven": "7", "eight": "8", "nine": "9",
           # What a recogniser writes for digits said down a phone line.
           "won": "1", "wan": "1", "tu": "2", "tree": "3", "free": "3", "fo": "4", "fore": "4", "fife": "5",
           "fiv": "5", "sicks": "6", "siks": "6", "sevin": "7", "seben": "7", "ate": "8", "eit": "8", "nain": "9",
           "nayn": "9", "niner": "9"}
_DIGIT_WORDS = ("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")


def _digit_word(w: str) -> str | None:
    """One word as digits, forgiving a recogniser: "oight" is "oh eight", "sevn" is "seven"."""
    if w in _DIGITS:
        return _DIGITS[w]
    # "oh" run into the next digit first: "oight" is closer to "eight" than to anything, but means 0 8.
    for lead in ("oh", "o"):
        rest = w[len(lead):]
        if w.startswith(lead) and len(rest) >= 2:
            if rest in _DIGITS:
                return "0" + _DIGITS[rest]
            close = difflib.get_close_matches(rest, _DIGIT_WORDS, n=1, cutoff=0.75)
            if close:
                return "0" + _DIGITS[close[0]]
    close = difflib.get_close_matches(w, _DIGIT_WORDS, n=1, cutoff=0.75)
    return _DIGITS[close[0]] if close else None


def plain(text: str) -> str:
    bare = unicodedata.normalize("NFD", text.lower())
    bare = "".join(ch for ch in bare if unicodedata.category(ch) != "Mn")
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9' ]", " ", bare.replace("’", "'"))).strip()


def _words(text: str) -> list[str]:
    return plain(text).replace("'", "").split()


def _has(text: str, phrases) -> bool:
    padded = f" {plain(text)} "
    return any(f" {p} " in padded for p in phrases)


def is_no(text: str) -> bool:
    return any(w in _NO for w in _words(text)) or _has(text, _NO_PHRASES)


def is_yes(text: str) -> bool:
    return not is_no(text) and (any(w in _YES for w in _words(text)) or _has(text, _YES_PHRASES))


def is_stop(text: str) -> bool:
    """To "is someone telling you to do this?": yes, or a plain stop, stops it."""
    return any(w in {"stop", "cancel", "duro", "tsaya", "kwusi"} for w in _words(text)) or is_yes(text)


def is_bye(text: str) -> bool:
    return any(w in _BYE for w in _words(text)) or _has(text, _BYE_PHRASES)


def language(text: str) -> str | None:
    said = plain(text)
    for code, names in _LANGUAGES.items():
        if any(re.search(rf"\b{n}\b", said) for n in names):
            return code
    return None


def digits(text: str) -> str:
    """The digits in what was said, written or spoken in English: "two oh eight, 1 2 3" -> "208123"."""
    out = []
    for w in re.findall(r"[a-z]+|\d+", plain(text)):
        if w.isdigit():
            out.append(w)
        elif (d := _digit_word(w)) is not None:
            out.append(d)
    return "".join(out)
