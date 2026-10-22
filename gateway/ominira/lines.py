"""Every line a customer hears. English and Pidgin are reviewed copy: never machine-translate."""

from ominira import amounts

LANGS = ("en", "pcm", "yo", "ha", "ig")

# Yorùbá, Hausa and Igbo lines are unreviewed drafts, as in the app, until native speakers check them.
_COPY: dict[str, dict[str, str]] = {
    # Outcomes
    "sent": {
        "en": "Sent. {nick} has it.",
        "pcm": "E don go. {nick} don get am.",
        "yo": "Ó ti lọ. {nick} ti gbà á.",
        "ha": "An aika. {nick} ya samu.",
        "ig": "Ezigala ya. {nick} enwetala ya.",
    },
    "approved_sent": {
        "en": "{approver} approved. Sent. {nick} has it.",
        "pcm": "{approver} don gree. E don go. {nick} don get am.",
        "yo": "{approver} ti fọwọ́ sí i. Ó ti lọ. {nick} ti gbà á.",
        "ha": "{approver} ya amince. An aika. {nick} ya samu.",
        "ig": "{approver} kwadoro ya. Ezigala ya. {nick} enwetala ya.",
    },
    "card_blocked": {
        "en": "Card blocked. A new one is on its way.",
        "pcm": "I don block the card. New one dey come.",
        "yo": "A ti dí káàdì náà. Tuntun ń bọ̀.",
        "ha": "An toshe katin. Sabo yana kan hanya.",
        "ig": "Egbochiela kaadị ahụ. Nke ọhụrụ na-abịa.",
    },
    "cancelled": {
        "en": "Cancelled. Nothing was sent.",
        "pcm": "I don cancel am. Nothing comot.",
        "yo": "A ti fagi lé e. Kò sí owó tó lọ.",
        "ha": "An soke. Ba a aika komai ba.",
        "ig": "Akagbuola ya. Ọ dịghị ihe e zigara.",
    },
    "already_sent": {
        "en": "That was already sent. Nothing more was sent.",
        "pcm": "E don go before. Nothing again comot.",
        "yo": "A ti fi ránṣẹ́ tẹ́lẹ̀. Kò sí owó mìíràn tó lọ.",
        "ha": "An riga an aika. Ba a ƙara aika komai ba.",
        "ig": "Ezigala ya. Ọ dịghị ihe ọzọ e zigara.",
    },
    "processing": {
        "en": "Your transfer is on its way. I will tell you when it lands. Do not send it again.",
        "pcm": "Your transfer dey road. I go tell you when e land. No send am again.",
        "yo": "Owó tí ẹ fi ránṣẹ́ wà lójú ọ̀nà. Màá sọ fún yín nígbà tí ó bá dé. Ẹ má tún fi ránṣẹ́ mọ́.",
        "ha": "Kuɗin da kuka aika suna kan hanya. Zan gaya muku idan sun isa. Kada ku sake aikawa.",
        "ig": "Ego i zigara nọ n'ụzọ. Aga m agwa gị mgbe ọ ruru. Ezigala ya ọzọ.",
    },
    "failed": {
        "en": "That did not go through. Nothing was sent.",
        "pcm": "E no go. Nothing comot.",
        "yo": "Kò lọ. Kò sí owó tó lọ.",
        "ha": "Bai tafi ba. Ba a aika komai ba.",
        "ig": "Ọ gaghị. Ọ dịghị ihe e zigara.",
    },
    "insufficient_funds": {
        "en": "That is more than you have. Nothing was sent.",
        "pcm": "E pass the money wey you get. Nothing comot.",
        "yo": "Ó ju owó tí ẹ ní lọ. Kò sí owó tó lọ.",
        "ha": "Ya fi kuɗin da kuke da shi yawa. Ba a aika komai ba.",
        "ig": "Ọ karịrị ego ị nwere. Ọ dịghị ihe e zigara.",
    },
    "bank_unavailable": {
        "en": "{bank} is not answering right now. Nothing was sent. Shall I try again in a minute?",
        "pcm": "{bank} no dey answer now. Nothing comot. Make I try again small time?",
        "yo": "{bank} kò dáhùn báyìí. Kò sí owó tó lọ. Ṣé kí n tún gbìyànjú ní ìṣẹ́jú kan sí i?",
        "ha": "{bank} ba ta amsa yanzu. Ba a aika komai ba. In sake gwadawa bayan minti ɗaya?",
        "ig": "{bank} anaghị aza ugbu a. Ọ dịghị ihe e zigara. Ka m nwaa ọzọ n'otu nkeji?",
    },
    "not_found": {
        "en": "I could not find that request. Nothing was sent.",
        "pcm": "I no see that request. Nothing comot.",
        "yo": "Mi ò rí ìbéèrè yẹn. Kò sí owó tó lọ.",
        "ha": "Ban sami wannan buƙata ba. Ba a aika komai ba.",
        "ig": "Achọtaghị m arịrịọ ahụ. Ọ dịghị ihe e zigara.",
    },
    # Scam pause
    "pause": {
        "en": "Is someone on the phone telling you to send this? This is a new person and a large amount. "
              "Nothing has been sent. Say stop, or say carry on.",
        "pcm": "Na person for phone dey tell you make you send this money? This na new person and big money. "
               "Nothing don comot. Talk stop, or talk carry on.",
        "yo": "Ṣé ẹnìkan lórí fóònù ló ń sọ fún yín pé kí ẹ fi èyí ránṣẹ́? Ẹni tuntun ni èyí, owó náà sì pọ̀. "
              "Kò sí owó tó tíì lọ. Ẹ sọ dúró, tàbí ẹ sọ tẹ̀síwájú.",
        "ha": "Akwai wani a waya da ke gaya muku ku aika wannan? Wannan sabon mutum ne kuma kuɗi masu yawa. "
              "Ba a aika komai ba tukuna. Ku ce tsaya, ko ku ce ci gaba.",
        "ig": "Ọ dị onye nọ na ekwentị na-agwa gị ka i ziga nke a? Onye ọhụrụ ka nke a bụ, ego ahụ dịkwa ukwuu. "
              "Ọ dịbeghị ihe e zigara. Kwuo kwụsị, ma ọ bụ kwuo gaa n'ihu.",
    },
    "pause_flagged": {
        "en": "You stopped a payment to this person after a phone call before. Nothing has been sent. "
              "Say stop, or say carry on.",
        "pcm": "You don stop payment to this person after phone call before. Nothing don comot. "
               "Talk stop, or talk carry on.",
        "yo": "Ẹ ti dá owó sí ẹni yìí dúró rí lẹ́yìn ìpè kan. Kò sí owó tó tíì lọ. Ẹ sọ dúró, tàbí ẹ sọ tẹ̀síwájú.",
        "ha": "Kun taɓa dakatar da aika kuɗi ga wannan mutumin bayan wani kira. Ba a aika komai ba tukuna. "
              "Ku ce tsaya, ko ku ce ci gaba.",
        "ig": "Ị kwụsịrị nzipu nye onye a mgbe oku gasịrị na mbụ. Ọ dịbeghị ihe e zigara. "
              "Kwuo kwụsị, ma ọ bụ kwuo gaa n'ihu.",
    },
    "pause_stopped": {
        "en": "Stopped. Your money is safe. Nothing was sent.",
        "pcm": "I don stop am. Your money dey safe. Nothing comot.",
        "yo": "Mo ti dá a dúró. Owó yín wà láìléwu. Kò sí owó tó lọ.",
        "ha": "An dakatar. Kuɗinku suna lafiya. Ba a aika komai ba.",
        "ig": "Akwụsịla m ya. Ego gị dị mma. Ọ dịghị ihe e zigara.",
    },
    "pause_continue": {
        "en": "Okay. Pulse your PIN to carry on.",
        "pcm": "Okay. Pulse your PIN make we continue.",
        "yo": "Ó dáa. Ẹ pulse PIN yín láti tẹ̀síwájú.",
        "ha": "To. Ku yi pulse PIN don ci gaba.",
        "ig": "Ọ dị mma. Pulse PIN gị ka anyị gaa n'ihu.",
    },
    "pause_continue_call": {
        "en": "Zenith will never call you to move money. If anyone on this call told you to do this, hang up now. "
              "Okay. Pulse your PIN to carry on.",
        "pcm": "Zenith no go ever call you make you move money. If anybody for this call tell you make you do am, "
               "cut the call now. Okay. Pulse your PIN make we continue.",
        "yo": "Zenith kò ní pè yín láé pé kí ẹ gbé owó. Tí ẹnikẹ́ni lórí ìpè yìí bá sọ fún yín pé kí ẹ ṣe èyí, "
              "ẹ gé ìpè náà báyìí. Ó dáa. Ẹ pulse PIN yín láti tẹ̀síwájú.",
        "ha": "Zenith ba za ta taɓa kiran ku don ku tura kuɗi ba. Idan wani a wannan kiran ya ce ku yi haka, "
              "ku katse kiran yanzu. To. Ku yi pulse PIN don ci gaba.",
        "ig": "Zenith agaghị akpọ gị ka i bufee ego. Ọ bụrụ na onye ọ bụla nọ na oku a gwara gị ime nke a, "
              "kwụsị oku ahụ ugbu a. Ọ dị mma. Pulse PIN gị ka anyị gaa n'ihu.",
    },
    "payee_flagged": {
        "en": "You stopped a payment to this person after a phone call. Call Zenith before you send them money.",
        "pcm": "You stop payment to this person after phone call before. Call Zenith before you send dem money.",
        "yo": "Ẹ dá owó sí ẹni yìí dúró lẹ́yìn ìpè kan. Ẹ pe Zenith kí ẹ tó fi owó ránṣẹ́ sí i.",
        "ha": "Kun dakatar da aika kuɗi ga wannan mutumin bayan wani kira. Ku kira Zenith kafin ku aika masa kuɗi.",
        "ig": "Ị kwụsịrị nzipu nye onye a mgbe oku gasịrị. Kpọọ Zenith tupu i ziga ya ego.",
    },
    # Co-approver
    "approval_wait": {
        "en": "{approver} checks transfers above {threshold}. I have asked {approver}. Nothing moves until {approver} says yes.",
        "pcm": "{approver} dey check transfer wey pass {threshold}. I don ask {approver}. Nothing go move until {approver} talk yes.",
        "yo": "{approver} ló ń yẹ owó tó ju {threshold} lọ wò. Mo ti bi {approver}. "
              "Kò sí ohun tí yóò lọ títí {approver} yóò fi sọ bẹ́ẹ̀ni.",
        "ha": "{approver} ne ke duba aikawa sama da {threshold}. Na tambayi {approver}. "
              "Babu abin da zai motsa har sai {approver} ya ce eh.",
        "ig": "{approver} na-elele nzipu karịrị {threshold}. Ajụọla m {approver}. "
              "Ọ dịghị ihe ga-aga ruo mgbe {approver} kwuru ee.",
    },
    "approval_still_waiting": {
        "en": "Still waiting for {approver}. Nothing has moved.",
        "pcm": "We still dey wait for {approver}. Nothing don move.",
        "yo": "À ṣì ń dúró de {approver}. Kò sí ohun tó tíì lọ.",
        "ha": "Har yanzu ana jiran {approver}. Babu abin da ya motsa.",
        "ig": "Anyị ka na-eche {approver}. Ọ dịbeghị ihe gara.",
    },
    "approval_expired": {
        "en": "{approver} did not answer in time, so nothing was sent. You can start again.",
        "pcm": "{approver} no answer on time, so nothing comot. You fit start am again.",
        "yo": "{approver} kò dáhùn lásìkò, nítorí náà kò sí owó tó lọ. Ẹ lè tún bẹ̀rẹ̀.",
        "ha": "{approver} bai amsa a kan lokaci ba, don haka ba a aika komai ba. Kuna iya sake farawa.",
        "ig": "{approver} azaghị n'oge, ya mere ọ dịghị ihe e zigara. Ị nwere ike ịmalite ọzọ.",
    },
    "approval_declined": {
        "en": "{approver} said no. Nothing was sent.",
        "pcm": "{approver} talk no. Nothing comot.",
        "yo": "{approver} sọ pé rárá. Kò sí owó tó lọ.",
        "ha": "{approver} ya ce a'a. Ba a aika komai ba.",
        "ig": "{approver} kwuru mba. Ọ dịghị ihe e zigara.",
    },
    # PIN
    "pin_incorrect": {
        "en": "That PIN did not match. Feel the count again. You have {tries} tries left.",
        "pcm": "That PIN no match. Feel the count again. You get {tries} try remain.",
        "yo": "PIN yẹn kò bá a mu. Ẹ tún fi ara mọ ìkà náà. Ẹ ní àǹfààní {tries} tó kù.",
        "ha": "Wannan PIN bai dace ba. Ku sake jin ƙirgan. Kuna da sauran damar {tries}.",
        "ig": "PIN ahụ ekwekọghị. Nwee mmetụta ọgụgụ ọzọ. Ị fọdụrụ ohere {tries}.",
    },
    # Singular: "one tries left" read badly.
    "pin_incorrect_one": {
        "en": "That PIN did not match. Feel the count again. You have one try left.",
        "pcm": "That PIN no match. Feel the count again. You get one try remain.",
        "yo": "PIN yẹn kò bá a mu. Ẹ tún fi ara mọ ìkà náà. Ẹ ní àǹfààní kan ṣoṣo tó kù.",
        "ha": "Wannan PIN bai dace ba. Ku sake jin ƙirgan. Kuna da sauran dama ɗaya.",
        "ig": "PIN ahụ ekwekọghị. Nwee mmetụta ọgụgụ ọzọ. Ị fọdụrụ otu ohere.",
    },
    "pin_locked": {
        "en": "Your Pulse PIN is locked after three wrong tries. Nothing was sent. Zenith will call you to unlock it.",
        "pcm": "Your Pulse PIN don lock because of three wrong tries. Nothing comot. Zenith go call you to open am.",
        "yo": "Pulse PIN yín ti tì pa lẹ́yìn ìgbìyànjú mẹ́ta tí kò tọ̀nà. Kò sí owó tó lọ. Zenith yóò pè yín láti ṣí i.",
        "ha": "An kulle Pulse PIN ɗinku bayan kuskure sau uku. Ba a aika komai ba. Zenith za ta kira ku don buɗe shi.",
        "ig": "Akpọchiela Pulse PIN gị mgbe ịnwara ugboro atọ na-ezighị ezi. Ọ dịghị ihe e zigara. "
              "Zenith ga-akpọ gị ka emepee ya.",
    },
    "unlocked": {"en": "Welcome back.", "pcm": "Welcome back.", "yo": "Ẹ káàbọ̀ padà.", "ha": "Barka da dawowa.",
                 "ig": "Nnọọ ọzọ."},
    "pin_spoken": {
        "en": "Never say your PIN out loud, not even to me. I have not done anything. If someone may have heard it, call Zenith to change it.",
        "pcm": "No ever talk your PIN for mouth, even give me. I never do anything. If person fit don hear am, call Zenith make dem change am.",
        "yo": "Ẹ má ṣe sọ PIN yín sókè láé, kódà fún mi. Mi ò ṣe nǹkan kan. Tí ẹnìkan bá lè ti gbọ́ ọ, "
              "ẹ pe Zenith láti yí i padà.",
        "ha": "Kada ku taɓa faɗin PIN ɗinku da ƙarfi, ko da a gare ni. Ban yi komai ba. Idan wani ya iya jin sa, "
              "ku kira Zenith don canza shi.",
        "ig": "Ekwula PIN gị n'olu dị elu, ọbụlagodi nye m. Emeghị m ihe ọ bụla. Ọ bụrụ na mmadụ nwere ike ịnụ ya, "
              "kpọọ Zenith ka ha gbanwee ya.",
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
        "yo": "Pulse PIN yín ni ẹ ó fi ara mọ̀, kí ẹnikẹ́ni má baà rí i. Ẹ tẹ àárín ojú fóònù láti bẹ̀rẹ̀. "
              "Fóònù yóò máa ka pẹ̀lú ìgbọ̀n jẹ́jẹ́, ìgbọ̀n kan fún nọ́ńbà kọ̀ọ̀kan, yóò sì máa bẹ̀rẹ̀ ní nọ́ńbà ọ̀tọ̀ ní gbogbo ìgbà. "
              "Òdo ni ìgbọ̀n méjì tó lágbára, lẹ́yìn rẹ̀ ni ọ̀kan, lẹ́yìn náà méjì, bẹ́ẹ̀ bẹ́ẹ̀ lọ. "
              "Nígbà tí ìkà bá dé nọ́ńbà yín, ẹ tẹ̀ ẹ́. Ẹ ṣe bẹ́ẹ̀ nígbà mẹ́rin.",
        "ha": "Ana shigar da Pulse PIN ɗinku ta hanyar ji, don haka babu wanda zai iya ganinsa. Ku taɓa tsakiyar allon don farawa. "
              "Wayar tana ƙirga da girgiza a hankali, girgiza ɗaya ga kowace lamba, kuma tana farawa daga lamba daban kowane lokaci. "
              "Sifili girgiza biyu ce mai ƙarfi, bayanta sai ɗaya, sannan biyu, da sauransu. "
              "Idan ƙirgan ya kai lambarku, ku taɓa. Ku yi haka sau huɗu.",
        "ig": "A na-etinye Pulse PIN gị site na mmetụta, ya mere ọ dịghị onye ga-ahụ ya. Pịa etiti ihuenyo ka ịmalite. "
              "Ekwentị na-agụ site na mkpọtụ dị nro, otu mkpọtụ maka nọmba ọ bụla, ọ na-amalitekwa na nọmba dị iche oge ọ bụla. "
              "Efu bụ mkpọtụ abụọ siri ike, mgbe ahụ otu, mgbe ahụ abụọ, wee gaa n'ihu. "
              "Mgbe ọgụgụ ruru nọmba gị, pịa. Mee nke ahụ ugboro anọ.",
    },
    # Gate refusals
    "draft_expired": {
        "en": "That request is more than ten minutes old, so I did not send it. Shall I start it again?",
        "pcm": "That request don pass ten minutes, so I no send am. Make I start am again?",
        "yo": "Ìbéèrè yẹn ti ju ìṣẹ́jú mẹ́wàá lọ, nítorí náà mi ò fi ránṣẹ́. Ṣé kí n tún un bẹ̀rẹ̀?",
        "ha": "Wannan buƙata ta wuce minti goma, don haka ban aika ta ba. In sake farawa?",
        "ig": "Arịrịọ ahụ agafeela nkeji iri, ya mere eziteghị m ya. Ka m malite ya ọzọ?",
    },
    "readback_mismatch": {
        "en": "Something changed since I read it to you. Listen again.",
        "pcm": "Something don change since I read am give you. Listen again.",
        "yo": "Nǹkan kan ti yí padà láti ìgbà tí mo kà á fún yín. Ẹ tún fetí sílẹ̀.",
        "ha": "Wani abu ya canza tun da na karanta muku. Ku sake saurara.",
        "ig": "Ihe gbanwere kemgbe m gụọrọ gị ya. Gee ntị ọzọ.",
    },
    "limit_daily": {
        "en": "That is over your limit for today. You can send {remaining} more today. Nothing was sent.",
        "pcm": "E pass your limit for today. You fit still send {remaining} today. Nothing comot.",
        "yo": "Ó ju iye tí ẹ lè fi ránṣẹ́ lónìí lọ. Ẹ ṣì lè fi {remaining} ránṣẹ́ lónìí. Kò sí owó tó lọ.",
        "ha": "Ya wuce iyakar ku ta yau. Kuna iya ƙara aika {remaining} yau. Ba a aika komai ba.",
        "ig": "Ọ karịrị oke gị maka taa. Ị ka nwere ike iziga {remaining} taa. Ọ dịghị ihe e zigara.",
    },
    "limit_single": {
        "en": "That is more than you can send at once. The most is {max}. Nothing was sent.",
        "pcm": "E pass wetin you fit send one time. The highest na {max}. Nothing comot.",
        "yo": "Ó ju ohun tí ẹ lè fi ránṣẹ́ lẹ́ẹ̀kan lọ. Èyí tó pọ̀ jù ni {max}. Kò sí owó tó lọ.",
        "ha": "Ya fi abin da za ku iya aikawa a lokaci ɗaya yawa. Mafi yawa shi ne {max}. Ba a aika komai ba.",
        "ig": "Ọ karịrị ihe ị nwere ike iziga n'otu oge. Nke kachasị bụ {max}. Ọ dịghị ihe e zigara.",
    },
    "only_customer": {
        "en": "Only you can do that, with your Pulse PIN.",
        "pcm": "Na only you fit do am, with your Pulse PIN.",
        "yo": "Ẹ̀yin nìkan ló lè ṣe èyí, pẹ̀lú Pulse PIN yín.",
        "ha": "Ku kaɗai ne za ku iya yin haka, da Pulse PIN ɗinku.",
        "ig": "Naanị gị nwere ike ime nke a, site na Pulse PIN gị.",
    },
    # Accounts
    "invalid_account": {
        "en": "I could not find that account. Check the number and the bank.",
        "pcm": "I no see that account. Check the number and the bank.",
        "yo": "Mi ò rí àkáǹtì yẹn. Ẹ yẹ nọ́ńbà àti báǹkì náà wò.",
        "ha": "Ban sami wannan asusun ba. Ku duba lambar da bankin.",
        "ig": "Achọtaghị m akaụntụ ahụ. Lelee nọmba na ụlọ akụ ahụ.",
    },
    "dormant": {
        "en": "That account is not active, so it cannot receive money.",
        "pcm": "That account no dey active, e no fit collect money.",
        "yo": "Àkáǹtì yẹn kò ṣiṣẹ́ mọ́, nítorí náà kò lè gba owó.",
        "ha": "Wannan asusun ba ya aiki, don haka ba zai iya karɓar kuɗi ba.",
        "ig": "Akaụntụ ahụ anaghị arụ ọrụ, ya mere ọ pụghị ịnata ego.",
    },
    "balance": {
        "en": "You have {amount} in your current account.",
        "pcm": "You get {amount} for your current account.",
        "yo": "Ẹ ní {amount} nínú àkáǹtì yín.",
        "ha": "Kuna da {amount} a asusunku.",
        "ig": "Ị nwere {amount} na akaụntụ gị.",
    },
    # Onboarding
    "bvn_ok": {
        "en": "Your BVN matches your card and your face.",
        "pcm": "Your BVN match your card and your face.",
        "yo": "BVN yín bá káàdì àti ojú yín mu.",
        "ha": "BVN ɗinku ya dace da katinku da fuskarku.",
        "ig": "BVN gị kwekọrọ na kaadị gị na ihu gị.",
    },
    "bvn_found": {
        "en": "Zenith found your BVN with NIBSS. It matches your card and your face.",
        "pcm": "Zenith don find your BVN with NIBSS. E match your card and your face.",
        "yo": "Zenith ti rí BVN yín lọ́dọ̀ NIBSS. Ó bá káàdì àti ojú yín mu.",
        "ha": "Zenith ta sami BVN ɗinku a NIBSS. Ya dace da katinku da fuskarku.",
        "ig": "Zenith achọtala BVN gị na NIBSS. Ọ kwekọrọ na kaadị gị na ihu gị.",
    },
    "pin_set": {
        "en": "Your Pulse PIN is set. Nobody saw it, not even me.",
        "pcm": "Your Pulse PIN don set. Nobody see am, even me.",
        "yo": "A ti ṣètò Pulse PIN yín. Kò sí ẹni tó rí i, kódà èmi.",
        "ha": "An saita Pulse PIN ɗinku. Babu wanda ya gan shi, ko ni ma.",
        "ig": "Edobela Pulse PIN gị. Ọ dịghị onye hụrụ ya, ọbụlagodi m.",
    },
    "pin_weak": {
        "en": "That PIN is too easy to guess. Choose another.",
        "pcm": "That PIN too easy to guess. Choose another one.",
        "yo": "PIN yẹn rọrùn jù láti mọ̀. Ẹ yan òmíràn.",
        "ha": "Wannan PIN yana da sauƙin ganewa. Ku zaɓi wani.",
        "ig": "PIN ahụ dị mfe ịkọ. Họrọ ọzọ.",
    },
    "bvn_mismatch": {
        "en": "That BVN belongs to someone else. Say your eleven digits again.",
        "pcm": "That BVN na another person own. Talk your eleven numbers again.",
        "yo": "BVN yẹn jẹ́ ti ẹlòmíràn. Ẹ tún sọ nọ́ńbà mọ́kànlá yín.",
        "ha": "Wannan BVN na wani ne. Ku sake faɗin lambobinku goma sha ɗaya.",
        "ig": "BVN ahụ bụ nke onye ọzọ. Kwuo nọmba iri na otu gị ọzọ.",
    },
    # Speech
    "speech_unavailable": {
        "en": "I can't listen or talk over the network right now. You can tap your choice instead.",
        "pcm": "Network no gree me hear you now. You fit tap wetin you want instead.",
        "yo": "Mi ò lè gbọ́ tàbí sọ̀rọ̀ lórí ẹ̀rọ ayélujára báyìí. Ẹ lè tẹ ohun tí ẹ fẹ́ dípò.",
        "ha": "Ba zan iya ji ko yin magana ta intanet yanzu ba. Kuna iya taɓa abin da kuke so maimakon haka.",
        "ig": "Enweghị m ike ịnụ ma ọ bụ ikwu okwu site na ịntanetị ugbu a. Ị nwere ike ịpị ihe ị chọrọ kama.",
    },
    "speech_not_heard": {
        "en": "Sorry, I didn't hear anything. Say it again, or tap your choice.",
        "pcm": "Sorry, I no hear anything. Talk am again, or tap wetin you want.",
        "yo": "Ẹ má bínú, mi ò gbọ́ nǹkan kan. Ẹ tún un sọ, tàbí ẹ tẹ ohun tí ẹ fẹ́.",
        "ha": "Yi haƙuri, ban ji komai ba. Ku sake faɗa, ko ku taɓa abin da kuke so.",
        "ig": "Ndo, anụghị m ihe ọ bụla. Kwuo ya ọzọ, ma ọ bụ pịa ihe ị chọrọ.",
    },
    # Assistant, once a draft exists
    "drafted_transfer": {
        "en": "I found {name} at {bank}.{new} Listen, then pulse your PIN to send.",
        "pcm": "I don find {name} for {bank}.{new} Listen well, then pulse your PIN make e go.",
        "yo": "Mo rí {name} ní {bank}.{new} Ẹ fetí sílẹ̀, lẹ́yìn náà ẹ pulse PIN yín láti fi ránṣẹ́.",
        "ha": "Na sami {name} a {bank}.{new} Ku saurara, sannan ku yi pulse PIN don aikawa.",
        "ig": "Achọtara m {name} na {bank}.{new} Gee ntị, mgbe ahụ pulse PIN gị ka i ziga.",
    },
    "drafted_new_payee": {
        "en": " You have not paid them before.",
        "pcm": " You never pay dem before.",
        "yo": " Ẹ kò tíì fi owó ránṣẹ́ sí wọn rí.",
        "ha": " Ba ku taɓa biyan su ba.",
        "ig": " Ị kwụbeghị ha ụgwọ mbụ.",
    },
    "drafted_card_block": {
        "en": "Listen, then pulse your PIN to block your card.",
        "pcm": "Listen well, then pulse your PIN make I block the card.",
        "yo": "Ẹ fetí sílẹ̀, lẹ́yìn náà ẹ pulse PIN yín láti dí káàdì yín.",
        "ha": "Ku saurara, sannan ku yi pulse PIN don toshe katinku.",
        "ig": "Gee ntị, mgbe ahụ pulse PIN gị ka egbochie kaadị gị.",
    },
    # Readback pieces
    "rb_to": {"en": "To {name}.", "pcm": "To {name}.", "yo": "Sí {name}.", "ha": "Zuwa ga {name}.", "ig": "Nye {name}."},
    "rb_bank": {
        "en": "{bank}, ending {digits}.",
        "pcm": "{bank}, wey end with {digits}.",
        "yo": "{bank}, tí ó parí sí {digits}.",
        "ha": "{bank}, mai ƙarewa da {digits}.",
        "ig": "{bank}, nke na-ejedebe na {digits}.",
    },
    "rb_pulse_send": {
        "en": "Pulse your PIN to send.",
        "pcm": "Pulse your PIN make e go.",
        "yo": "Ẹ pulse PIN yín láti fi ránṣẹ́.",
        "ha": "Ku yi pulse PIN don aikawa.",
        "ig": "Pulse PIN gị ka i ziga.",
    },
    "rb_block": {
        "en": "Block your card ending {digits}.",
        "pcm": "Block your card wey end with {digits}.",
        "yo": "Dí káàdì yín tí ó parí sí {digits}.",
        "ha": "Toshe katinku mai ƙarewa da {digits}.",
        "ig": "Gbochie kaadị gị nke na-ejedebe na {digits}.",
    },
    "rb_block_effect": {
        "en": "It stops working at once. A new card reaches you in about five working days.",
        "pcm": "E go stop to work sharp sharp. New card go reach you for like five working days.",
        "yo": "Yóò dẹ́kun iṣẹ́ lẹ́sẹ̀kẹsẹ̀. Káàdì tuntun yóò dé ọ̀dọ̀ yín ní nǹkan bí ọjọ́ iṣẹ́ márùn-ún.",
        "ha": "Zai daina aiki nan take. Sabon kati zai iso muku cikin kusan kwanakin aiki biyar.",
        "ig": "Ọ ga-akwụsị ịrụ ọrụ ozugbo. Kaadị ọhụrụ ga-eru gị n'ihe dị ka ụbọchị ọrụ ise.",
    },
    "rb_pulse_block": {
        "en": "Pulse your PIN to block it.",
        "pcm": "Pulse your PIN make I block am.",
        "yo": "Ẹ pulse PIN yín láti dí i.",
        "ha": "Ku yi pulse PIN don toshe shi.",
        "ig": "Pulse PIN gị ka egbochie ya.",
    },
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
    "phone_stranger": {
        "en": "I can't open that account on this call. Please call from your registered phone number, or use the app.",
        "pcm": "I no fit open that account for this call. Abeg call from the phone number wey you register, or use "
               "the app.",
        "yo": "Mi ò lè ṣí àkáǹtì yẹn lórí ìpè yìí. Ẹ jọ̀ọ́ ẹ pè láti nọ́ńbà fóònù tí ẹ forúkọ sílẹ̀, tàbí ẹ lo áàpù.",
        "ha": "Ba zan iya buɗe wannan asusun a wannan kiran ba. Don Allah ku kira daga lambar wayar da kuka yi "
              "rajista, ko ku yi amfani da manhajar.",
        "ig": "Enweghị m ike imeghe akaụntụ ahụ na oku a. Biko kpọọ site na nọmba ekwentị i debanyere, ma ọ bụ jiri "
              "ngwa ahụ.",
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
    # The assistant's questions and answers
    "ask_who": {
        "en": "Who are you sending to? Say a name, like {names}.",
        "pcm": "Who you wan send am give? Talk name, like {names}.",
        "yo": "Ta ni ẹ fẹ́ fi ránṣẹ́ sí? Ẹ sọ orúkọ kan, bíi {names}.",
        "ha": "Wa za ku aika wa? Ku faɗi suna, kamar {names}.",
        "ig": "Onye ka ị na-eziga? Kwuo aha, dịka {names}.",
    },
    "or": {"en": "or", "pcm": "or", "yo": "tàbí", "ha": "ko", "ig": "ma ọ bụ"},
    "ask_amount": {
        "en": "How much should I send to {nick}?",
        "pcm": "How much I go send give {nick}?",
        "yo": "Èló ni kí n fi ránṣẹ́ sí {nick}?",
        "ha": "Nawa zan aika wa {nick}?",
        "ig": "Ego ole ka m ga-eziga {nick}?",
    },
    "ask_bank": {
        "en": "Which bank is that account with?",
        "pcm": "Which bank that account dey?",
        "yo": "Báǹkì wo ni àkáǹtì yẹn wà?",
        "ha": "A wane banki ne wannan asusun yake?",
        "ig": "Ụlọ akụ ole ka akaụntụ ahụ dị?",
    },
    "help": {
        "en": "I can send money, read your balance, tell you what came in, or block your card. What would you like?",
        "pcm": "I fit send money, read your balance, tell you wetin enter, or block your card. Wetin you want?",
        "yo": "Mo lè fi owó ránṣẹ́, ka owó inú àkáǹtì yín, sọ ohun tó wọlé fún yín, tàbí dí káàdì yín. Kí ni ẹ fẹ́?",
        "ha": "Zan iya aika kuɗi, karanta kuɗin asusunku, gaya muku abin da ya shigo, ko toshe katinku. Me kuke so?",
        "ig": "Enwere m ike iziga ego, gụọ ego dị na akaụntụ gị, gwa gị ihe batara, ma ọ bụ gbochie kaadị gị. "
              "Gịnị ka ị chọrọ?",
    },
    "came_in_none": {
        "en": "Nothing came in this week.",
        "pcm": "Nothing enter this week.",
        "yo": "Kò sí owó tó wọlé lọ́sẹ̀ yìí.",
        "ha": "Babu abin da ya shigo wannan mako.",
        "ig": "Ọ dịghị ihe batara n'izu a.",
    },
    "came_in": {
        "en": "{amount} came in this week, from {count} {people}.",
        "pcm": "{amount} enter this week, from {count} {people}.",
        "yo": "{amount} ló wọlé lọ́sẹ̀ yìí, láti ọ̀dọ̀ {people} {count}.",
        "ha": "{amount} ya shigo wannan mako, daga {people} {count}.",
        "ig": "{amount} batara n'izu a, site n'aka {people} {count}.",
    },
    "person": {"en": "person", "pcm": "person", "yo": "ènìyàn", "ha": "mutum", "ig": "mmadụ"},
    "people": {"en": "people", "pcm": "people", "yo": "ènìyàn", "ha": "mutane", "ig": "mmadụ"},
    "lookup_found": {
        "en": "{name}, {bank}, ending {digits}.",
        "pcm": "{name}, {bank}, wey end with {digits}.",
        "yo": "{name}, {bank}, tí ó parí sí {digits}.",
        "ha": "{name}, {bank}, mai ƙarewa da {digits}.",
        "ig": "{name}, {bank}, nke na-ejedebe na {digits}.",
    },
    "no_card": {
        "en": "I could not find a card to block.",
        "pcm": "I no see any card to block.",
        "yo": "Mi ò rí káàdì kankan láti dí.",
        "ha": "Ban sami katin da zan toshe ba.",
        "ig": "Achọtaghị m kaadị m ga-egbochi.",
    },
    "unknown_bank": {
        "en": "I do not know a bank called {bank}.",
        "pcm": "I no know any bank wey dem dey call {bank}.",
        "yo": "Mi ò mọ báǹkì kan tí wọ́n ń pè ní {bank}.",
        "ha": "Ban san wani banki mai suna {bank} ba.",
        "ig": "Amaghị m ụlọ akụ a na-akpọ {bank}.",
    },
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
    return say(lang, "pin_incorrect", tries=amounts.words(tries_left, lang))
