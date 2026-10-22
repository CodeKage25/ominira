"""Every line a customer hears. Reviewed copy: never machine-translate."""

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
    # Singular: "one tries left" read badly.
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
    "bvn_found": {"en": "Zenith found your BVN with NIBSS. It matches your card and your face."},
    "pin_set": {"en": "Your Pulse PIN is set. Nobody saw it, not even me."},
    "pin_weak": {"en": "That PIN is too easy to guess. Choose another."},
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
    # The phone line. Each menu line is said in its own language. Yorùbá, Hausa and Igbo are unreviewed
    # drafts, as in the app, until native speakers check them.
    "phone_menu_en": {"en": "For English, press 1."},
    "phone_menu_pcm": {"en": "For Pidgin, press 2.", "pcm": "For Pidgin, press 2."},
    "phone_menu_yo": {"en": "Fún Yorùbá, ẹ tẹ mẹ́ta.", "yo": "Fún Yorùbá, ẹ tẹ mẹ́ta."},
    "phone_menu_ha": {"en": "Don Hausa, ku danna huɗu.", "ha": "Don Hausa, ku danna huɗu."},
    "phone_menu_ig": {"en": "Maka Igbo, pịa ise.", "ig": "Maka Igbo, pịa ise."},
    "phone_account": {
        "en": "Please enter your ten-digit Zenith account number.",
        "pcm": "Abeg enter your ten-digit Zenith account number.",
        "yo": "Ẹ jọ̀ọ́ ẹ tẹ nọ́ńbà àkáǹtì Zenith yín oní-nọ́ńbà mẹ́wàá.",
        "ha": "Don Allah ku shigar da lambar asusunku ta Zenith mai lambobi goma.",
        "ig": "Biko tinye nọmba akaụntụ Zenith gị nwere nọmba iri.",
    },
    "phone_no_account": {
        "en": "I could not find that account. Please try again.",
        "pcm": "I no see that account. Abeg try again.",
        "yo": "Mi ò rí àkáǹtì yẹn. Ẹ jọ̀ọ́ ẹ tún gbìyànjú.",
        "ha": "Ban sami wannan asusun ba. Don Allah ku sake gwadawa.",
        "ig": "Achọtaghị m akaụntụ ahụ. Biko nwaa ọzọ.",
    },
    "phone_hello": {
        "en": "Hello {first}. What would you like to do? You can say: send money to Tolu, or what is my balance.",
        "pcm": "How far {first}. Wetin you wan do? You fit talk: send money give Tolu, or how much I get.",
        "yo": "Ẹ n lẹ {first}. Kí ni ẹ fẹ́ ṣe? Ẹ lè sọ pé: fi owó ránṣẹ́ sí Tolu, tàbí èló ni mo ní.",
        "ha": "Sannu {first}. Me kuke so ku yi? Kuna iya cewa: aika kuɗi ga Tolu, ko nawa nake da shi.",
        "ig": "Nnọọ {first}. Gịnị ka ị chọrọ ime? Ị nwere ike ikwu: ziga Tolu ego, ma ọ bụ ego ole ka m nwere.",
    },
    "phone_record_hint": {
        "en": "Speak after the beep, then press hash.",
        "pcm": "Talk after the beep, then press hash.",
        "yo": "Ẹ sọ̀rọ̀ lẹ́yìn ìró náà, lẹ́yìn náà ẹ tẹ hash.",
        "ha": "Ku yi magana bayan ƙarar, sannan ku danna hash.",
        "ig": "Kwuo okwu mgbe ụda ahụ gasịrị, wee pịa hash.",
    },
    "phone_not_heard": {
        "en": "Sorry, I didn't hear that. Please say it again.",
        "pcm": "Sorry, I no hear am. Abeg talk am again.",
        "yo": "Ẹ má bínú, kò yé mi. Ẹ jọ̀ọ́ ẹ tún un sọ.",
        "ha": "Yi haƙuri, ban ji ba. Don Allah ku sake faɗa.",
        "ig": "Ndo, anụghị m ya. Biko kwuo ya ọzọ.",
    },
    "phone_confirm": {
        "en": "Press 1 to send it, or 2 to cancel.",
        "pcm": "Press 1 make e go, or 2 make we cancel.",
        "yo": "Ẹ tẹ ọ̀kan láti fi ránṣẹ́, tàbí méjì láti fagi lé e.",
        "ha": "Ku danna ɗaya don aikawa, ko biyu don sokewa.",
        "ig": "Pịa otu ka i ziga ya, ma ọ bụ abụọ ka ịkagbu ya.",
    },
    "phone_pin": {
        "en": "Enter your four-digit PIN on the keypad, then press hash.",
        "pcm": "Enter your four-number PIN for keypad, then press hash.",
        "yo": "Ẹ tẹ nọ́ńbà àṣírí oní-nọ́ńbà mẹ́rin yín, lẹ́yìn náà ẹ tẹ hash.",
        "ha": "Ku shigar da lambar sirrinku mai lambobi huɗu, sannan ku danna hash.",
        "ig": "Tinye nọmba nzuzo gị nwere nọmba anọ, wee pịa hash.",
    },
    "phone_pause_keys": {
        "en": "Press 1 to stop it. Press 2 if you chose this yourself.",
        "pcm": "Press 1 make we stop am. Press 2 if na you choose am by yourself.",
        "yo": "Ẹ tẹ ọ̀kan láti dá a dúró. Ẹ tẹ méjì tí ẹ̀yin fúnra yín ló yàn án.",
        "ha": "Ku danna ɗaya don dakatar da shi. Ku danna biyu idan ku da kanku kuka zaɓa.",
        "ig": "Pịa otu ka ịkwụsị ya. Pịa abụọ ma ọ bụ gị n'onwe gị họọrọ ya.",
    },
    "phone_more": {
        "en": "Anything else? Speak now, or hang up.",
        "pcm": "Anything again? Talk now, or cut the call.",
        "yo": "Ṣé nǹkan mìíràn wà? Ẹ sọ̀rọ̀ báyìí, tàbí ẹ gé ìpè.",
        "ha": "Akwai wani abu kuma? Ku yi magana yanzu, ko ku katse kiran.",
        "ig": "Ọ dị ihe ọzọ? Kwuo okwu ugbu a, ma ọ bụ kwụsị oku.",
    },
    "phone_bye": {
        "en": "Thank you for banking with Zenith. Goodbye.",
        "pcm": "Thank you as you bank with Zenith. Bye bye.",
        "yo": "Ẹ ṣé tí ẹ bá Zenith ṣe ìfowópamọ́. Ó dàbọ̀.",
        "ha": "Na gode da kuka yi banki da Zenith. Sai an jima.",
        "ig": "Daalụ maka iji Zenith. Ka ọ dị.",
    },
    # The live call: everything by voice, except the Pulse PIN's four key presses.
    "phone_v_welcome": {
        "en": "Welcome to Zenith, with Òmìnira. Zenith will never call you. If someone told you to call, hang up now.",
        "pcm": "Welcome to Zenith, with Òmìnira. Zenith no go ever call you. If person tell you make you call, cut the call now.",
        "yo": "Ẹ kú àbọ̀ sí Zenith, pẹ̀lú Òmìnira. Zenith kò ní pè yín láé. Tí ẹnìkan bá ní kí ẹ pè, ẹ gé ìpè báyìí.",
        "ha": "Barka da zuwa Zenith, tare da Òmìnira. Zenith ba za ta taɓa kiran ku ba. Idan wani ya ce ku kira, ku katse yanzu.",
        "ig": "Nnọọ na Zenith, ya na Òmìnira. Zenith agaghị akpọ gị ma ọlị. Ọ bụrụ na mmadụ gwara gị kpọọ, kwụsị oku ugbu a.",
    },
    "phone_v_menu_en": {"en": "For English, say English."},
    "phone_v_menu_pcm": {"en": "For Pidgin, say Pidgin.", "pcm": "For Pidgin, talk Pidgin."},
    "phone_v_menu_yo": {"en": "Fún Yorùbá, ẹ sọ Yorùbá.", "yo": "Fún Yorùbá, ẹ sọ Yorùbá."},
    "phone_v_menu_ha": {"en": "Don Hausa, ku ce Hausa.", "ha": "Don Hausa, ku ce Hausa."},
    "phone_v_menu_ig": {"en": "Maka Igbo, kwuo Igbo.", "ig": "Maka Igbo, kwuo Igbo."},
    "phone_v_account": {
        "en": "Please say your ten-digit Zenith account number.",
        "pcm": "Abeg talk your ten-digit Zenith account number.",
        "yo": "Ẹ jọ̀ọ́ ẹ sọ nọ́ńbà àkáǹtì Zenith yín oní-nọ́ńbà mẹ́wàá.",
        "ha": "Don Allah ku faɗi lambar asusunku ta Zenith mai lambobi goma.",
        "ig": "Biko kwuo nọmba akaụntụ Zenith gị nwere nọmba iri.",
    },
    "phone_v_hi": {"en": "Hello {first}.", "pcm": "How far {first}.", "yo": "Ẹ n lẹ {first}.", "ha": "Sannu {first}.",
                   "ig": "Nnọọ {first}."},
    "phone_v_what": {
        "en": "What would you like to do? You can say: send money to Tolu, or what is my balance.",
        "pcm": "Wetin you wan do? You fit talk: send money give Tolu, or how much I get.",
        "yo": "Kí ni ẹ fẹ́ ṣe? Ẹ lè sọ pé: fi owó ránṣẹ́ sí Tolu, tàbí èló ni mo ní.",
        "ha": "Me kuke so ku yi? Kuna iya cewa: aika kuɗi ga Tolu, ko nawa nake da shi.",
        "ig": "Gịnị ka ị chọrọ ime? Ị nwere ike ikwu: ziga Tolu ego, ma ọ bụ ego ole ka m nwere.",
    },
    "phone_v_confirm": {
        "en": "Say yes to send it, or no to stop.",
        "pcm": "Talk yes make e go, or no make we stop.",
        "yo": "Ẹ sọ bẹ́ẹ̀ni láti fi ránṣẹ́, tàbí rárá láti dá a dúró.",
        "ha": "Ku ce eh don aikawa, ko a'a don dakatarwa.",
        "ig": "Kwuo ee ka i ziga ya, ma ọ bụ mba ka ịkwụsị ya.",
    },
    "phone_v_pin": {
        "en": "Now your Pulse PIN. I will count with ticks, from a different number each time. The high double tick "
              "is zero. When the count reaches your digit, press any key. Do that four times.",
        "pcm": "Now your Pulse PIN. I go count with tick, from different number every time. The high double tick na "
               "zero. When the count reach your number, press any key. Do am four times.",
        "yo": "Báyìí, Pulse PIN yín. Màá ka pẹ̀lú ìró kékeré, láti nọ́ńbà ọ̀tọ̀ ní gbogbo ìgbà. Ìró gíga méjì ni òdo. "
              "Nígbà tí ìkà bá dé nọ́ńbà yín, ẹ tẹ bọ́tìnnì èyíkéyìí. Ẹ ṣe bẹ́ẹ̀ nígbà mẹ́rin.",
        "ha": "Yanzu Pulse PIN ɗinku. Zan ƙirga da ƙara, ina farawa daga lamba daban kowane lokaci. Ƙara biyu mai "
              "tsayi ita ce sifili. Idan ƙirgan ya kai lambarku, ku danna kowane maɓalli. Ku yi haka sau huɗu.",
        "ig": "Ugbu a Pulse PIN gị. Aga m agụ site na ụda, na-amalite na nọmba dị iche oge ọ bụla. Ụda abụọ dị elu bụ "
              "efu. Mgbe ọgụgụ ruru nọmba gị, pịa bọtịnụ ọ bụla. Mee nke ahụ ugboro anọ.",
    },
    "phone_v_pin_short": {
        "en": "Your Pulse PIN. Press any key at your digit.",
        "pcm": "Your Pulse PIN. Press any key for your number.",
        "yo": "Pulse PIN yín. Ẹ tẹ bọ́tìnnì èyíkéyìí ní nọ́ńbà yín.",
        "ha": "Pulse PIN ɗinku. Ku danna kowane maɓalli a lambarku.",
        "ig": "Pulse PIN gị. Pịa bọtịnụ ọ bụla na nọmba gị.",
    },
    "phone_v_pause": {
        "en": "Say yes to stop it. Say no if you chose this yourself.",
        "pcm": "Talk yes make we stop am. Talk no if na you choose am by yourself.",
        "yo": "Ẹ sọ bẹ́ẹ̀ni láti dá a dúró. Ẹ sọ rárá tí ẹ̀yin fúnra yín ló yàn án.",
        "ha": "Ku ce eh don dakatar da shi. Ku ce a'a idan ku da kanku kuka zaɓa.",
        "ig": "Kwuo ee ka ịkwụsị ya. Kwuo mba ma ọ bụ gị n'onwe gị họọrọ ya.",
    },
    "phone_v_more": {
        "en": "Anything else? Speak now, or say goodbye.",
        "pcm": "Anything again? Talk now, or talk bye bye.",
        "yo": "Ṣé nǹkan mìíràn wà? Ẹ sọ̀rọ̀ báyìí, tàbí ẹ sọ ó dàbọ̀.",
        "ha": "Akwai wani abu kuma? Ku yi magana yanzu, ko ku ce sai an jima.",
        "ig": "Ọ dị ihe ọzọ? Kwuo okwu ugbu a, ma ọ bụ kwuo ka ọ dị.",
    },
    "phone_v_cancelled": {
        "en": "Stopped. Nothing was sent.",
        "pcm": "I don stop am. Nothing go.",
        "yo": "Mo ti dá a dúró. Kò sí ohun tí ó lọ.",
        "ha": "An dakatar. Ba a aika komai ba.",
        "ig": "Akwụsịla m ya. Ọ dịghị ihe e zigara.",
    },
    "phone_v_still_there": {
        "en": "Are you still there?",
        "pcm": "You still dey there?",
        "yo": "Ṣé ẹ ṣì wà níbẹ̀?",
        "ha": "Kuna nan har yanzu?",
        "ig": "Ị ka nọ ebe ahụ?",
    },
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
