"""Every line a customer hears, keyed by event, in their language.

English and Pidgin are reviewed product copy: change them only with the product team. Yorùbá,
Hausa and Igbo fall back to English until a native speaker has written and checked them. Add them
as new entries, never by machine translation.

A few lines are English only so far (marked below). Pidgin customers hear English for those
until the copy is written and reviewed.
"""

from ominira import amounts

LANGS = ("en", "pcm", "yo", "ha", "ig")

_COPY: dict[str, dict[str, str]] = {
    # Outcomes
    "sent": {"en": "Sent. {nick} has it.", "pcm": "E don go. {nick} don get am."},
    "approved_sent": {
        "en": "{approver} approved. Sent. {nick} has it.",
        "pcm": "{approver} don gree. E don go. {nick} don get am.",
    },
    "card_blocked": {"en": "Card blocked. A new one is on its way.", "pcm": "I don block the card. New one dey come."},
    "cancelled": {"en": "Cancelled. Nothing was sent.", "pcm": "I don cancel am. Nothing comot."},
    "already_sent": {"en": "That was already sent. Nothing more was sent.", "pcm": "E don go before. Nothing again comot."},
    "processing": {
        "en": "Your transfer is on its way. I will tell you when it lands. Do not send it again.",
        "pcm": "Your transfer dey road. I go tell you when e land. No send am again.",
    },
    "failed": {"en": "That did not go through. Nothing was sent.", "pcm": "E no go. Nothing comot."},
    "insufficient_funds": {"en": "That is more than you have. Nothing was sent.", "pcm": "E pass the money wey you get. Nothing comot."},
    "bank_unavailable": {
        "en": "{bank} is not answering right now. Nothing was sent. Shall I try again in a minute?",
        "pcm": "{bank} no dey answer now. Nothing comot. Make I try again small time?",
    },
    "not_found": {"en": "I could not find that request. Nothing was sent.", "pcm": "I no see that request. Nothing comot."},
    # Scam pause
    "pause": {
        "en": "Is someone on the phone telling you to send this? This is a new person and a large amount. "
              "Nothing has been sent. Say stop, or say carry on.",
        "pcm": "Na person for phone dey tell you make you send this money? This na new person and big money. "
               "Nothing don comot. Talk stop, or talk carry on.",
    },
    "pause_flagged": {
        "en": "You stopped a payment to this person after a phone call before. Nothing has been sent. "
              "Say stop, or say carry on.",
        "pcm": "You don stop payment to this person after phone call before. Nothing don comot. "
               "Talk stop, or talk carry on.",
    },
    "pause_stopped": {"en": "Stopped. Your money is safe. Nothing was sent.", "pcm": "I don stop am. Your money dey safe. Nothing comot."},
    "pause_continue": {"en": "Okay. Pulse your PIN to carry on.", "pcm": "Okay. Pulse your PIN make we continue."},
    "pause_continue_call": {
        "en": "Zenith will never call you to move money. If anyone on this call told you to do this, hang up now. "
              "Okay. Pulse your PIN to carry on.",
        "pcm": "Zenith no go ever call you make you move money. If anybody for this call tell you make you do am, "
               "cut the call now. Okay. Pulse your PIN make we continue.",
    },
    "payee_flagged": {
        "en": "You stopped a payment to this person after a phone call. Call Zenith before you send them money.",
        "pcm": "You stop payment to this person after phone call before. Call Zenith before you send dem money.",
    },
    # Co-approver
    "approval_wait": {
        "en": "{approver} checks transfers above {threshold}. I have asked {approver}. Nothing moves until {approver} says yes.",
        "pcm": "{approver} dey check transfer wey pass {threshold}. I don ask {approver}. Nothing go move until {approver} talk yes.",
    },
    "approval_still_waiting": {"en": "Still waiting for {approver}. Nothing has moved.", "pcm": "We still dey wait for {approver}. Nothing don move."},
    "approval_expired": {
        "en": "{approver} did not answer in time, so nothing was sent. You can start again.",
        "pcm": "{approver} no answer on time, so nothing comot. You fit start am again.",
    },
    "approval_declined": {"en": "{approver} said no. Nothing was sent.", "pcm": "{approver} talk no. Nothing comot."},
    # PIN
    "pin_incorrect": {
        "en": "That PIN did not match. Feel the count again. You have {tries} tries left.",
        "pcm": "That PIN no match. Feel the count again. You get {tries} try remain.",
    },
    # "one tries left" read badly; this is the same line with the singular.
    "pin_incorrect_one": {
        "en": "That PIN did not match. Feel the count again. You have one try left.",
        "pcm": "That PIN no match. Feel the count again. You get one try remain.",
    },
    "pin_locked": {
        "en": "Your Pulse PIN is locked after three wrong tries. Nothing was sent. Zenith will call you to unlock it.",
        "pcm": "Your Pulse PIN don lock because of three wrong tries. Nothing comot. Zenith go call you to open am.",
    },
    "unlocked": {"en": "Welcome back.", "pcm": "Welcome back."},
    "pin_spoken": {
        "en": "Never say your PIN out loud, not even to me. I have not done anything. If someone may have heard it, call Zenith to change it.",
        "pcm": "No ever talk your PIN for mouth, even give me. I never do anything. If person fit don hear am, call Zenith make dem change am.",
    },
    "pulse_pin_explain": {
        "en": "Your Pulse PIN is entered by feel, so nobody can see it. Tap the middle of the screen to start. "
              "The phone counts with soft buzzes, one buzz for each number, and it starts at a different number every time. "
              "Zero is a strong double buzz, so after it comes one, then two, and so on. "
              "When the count reaches your digit, tap. Do that four times.",
        "pcm": "Your Pulse PIN na by feeling, so nobody fit see am. Tap the middle of the screen make e start. "
               "The phone go dey count with small buzz, one buzz for each number, and e dey start from different number every time. "
               "Zero na strong double buzz, so after am na one, then two, like that. "
               "When the count reach your number, tap. Do am four times.",
    },
    # Gate refusals
    "draft_expired": {
        "en": "That request is more than ten minutes old, so I did not send it. Shall I start it again?",
        "pcm": "That request don pass ten minutes, so I no send am. Make I start am again?",
    },
    "readback_mismatch": {"en": "Something changed since I read it to you. Listen again.", "pcm": "Something don change since I read am give you. Listen again."},
    "limit_daily": {
        "en": "That is over your limit for today. You can send {remaining} more today. Nothing was sent.",
        "pcm": "E pass your limit for today. You fit still send {remaining} today. Nothing comot.",
    },
    "limit_single": {
        "en": "That is more than you can send at once. The most is {max}. Nothing was sent.",
        "pcm": "E pass wetin you fit send one time. The highest na {max}. Nothing comot.",
    },
    "only_customer": {"en": "Only you can do that, with your Pulse PIN.", "pcm": "Na only you fit do am, with your Pulse PIN."},
    # Accounts
    "invalid_account": {"en": "I could not find that account. Check the number and the bank.", "pcm": "I no see that account. Check the number and the bank."},
    "dormant": {"en": "That account is not active, so it cannot receive money.", "pcm": "That account no dey active, e no fit collect money."},
    "balance": {"en": "You have {amount} in your current account.", "pcm": "You get {amount} for your current account."},
    # Onboarding
    "bvn_ok": {"en": "Your BVN matches your card and your face.", "pcm": "Your BVN match your card and your face."},
    "bvn_mismatch": {"en": "That BVN belongs to someone else. Say your eleven digits again.", "pcm": "That BVN na another person own. Talk your eleven numbers again."},
    # Speech
    "speech_unavailable": {
        "en": "I can't listen or talk over the network right now. You can tap your choice instead.",
        "pcm": "Network no gree me hear you now. You fit tap wetin you want instead.",
    },
    "speech_not_heard": {
        "en": "Sorry, I didn't hear anything. Say it again, or tap your choice.",
        "pcm": "Sorry, I no hear anything. Talk am again, or tap wetin you want.",
    },
    # Assistant, once a draft exists
    "drafted_transfer": {
        "en": "I found {name} at {bank}.{new} Listen, then pulse your PIN to send.",
        "pcm": "I don find {name} for {bank}.{new} Listen well, then pulse your PIN make e go.",
    },
    "drafted_new_payee": {"en": " You have not paid them before.", "pcm": " You never pay dem before."},
    "drafted_card_block": {"en": "Listen, then pulse your PIN to block your card.", "pcm": "Listen well, then pulse your PIN make I block the card."},
    # Readback pieces
    "rb_to": {"en": "To {name}.", "pcm": "To {name}."},
    "rb_bank": {"en": "{bank}, ending {digits}.", "pcm": "{bank}, wey end with {digits}."},
    "rb_pulse_send": {"en": "Pulse your PIN to send.", "pcm": "Pulse your PIN make e go."},
    "rb_block": {"en": "Block your card ending {digits}.", "pcm": "Block your card wey end with {digits}."},
    "rb_block_effect": {
        "en": "It stops working at once. A new card reaches you in about five working days.",
        "pcm": "E go stop to work sharp sharp. New card go reach you for like five working days.",
    },
    "rb_pulse_block": {"en": "Pulse your PIN to block it.", "pcm": "Pulse your PIN make I block am."},
    # English only so far: waiting for Pidgin copy.
    "ask_who": {"en": "Who are you sending to? Say a name, like {names}."},
    "ask_amount": {"en": "How much should I send to {nick}?"},
    "ask_bank": {"en": "Which bank is that account with?"},
    "help": {"en": "I can send money, read your balance, tell you what came in, or block your card. What would you like?"},
    "came_in_none": {"en": "Nothing came in this week."},
    "came_in": {"en": "{amount} came in this week, from {count} {people}."},
    "lookup_found": {"en": "{name}, {bank}, ending {digits}."},
    "no_card": {"en": "I could not find a card to block."},
    "unknown_bank": {"en": "I do not know a bank called {bank}."},
}


def say(lang: str, key: str, **values) -> str:
    row = _COPY[key]
    return row.get(lang, row["en"]).format(**values)


def has(key: str) -> bool:
    return key in _COPY


def keys() -> list[str]:
    return list(_COPY)


def pin_wrong(lang: str, tries_left: int) -> str:
    if tries_left <= 0:
        return say(lang, "pin_locked")
    if tries_left == 1:
        return say(lang, "pin_incorrect_one")
    return say(lang, "pin_incorrect", tries=amounts.words(tries_left))
