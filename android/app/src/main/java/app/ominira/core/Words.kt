package app.ominira.core

// Every screen line after onboarding, in the order en, pcm, yo, ha, ig.
// Pidgin, Yorùbá, Hausa and Igbo are unreviewed drafts: native speakers check them before launch.
class Words private constructor(val lang: Lang) {

    private fun t(en: String, pcm: String, yo: String, ha: String, ig: String): String = when (lang) {
        Lang.En -> en
        Lang.Pcm -> pcm
        Lang.Yo -> yo
        Lang.Ha -> ha
        Lang.Ig -> ig
    }

    private val wordy: Boolean get() = lang == Lang.En || lang == Lang.Pcm

    // English number words read badly inside yo/ha/ig sentences: figures there.
    fun money(kobo: Long): String = if (wordy) Naira.spoken(kobo) else Naira.format(kobo)
    fun moneySentence(kobo: Long): String = if (wordy) Naira.spokenSentence(kobo) else Naira.format(kobo)
    fun digits(d: String): String = if (wordy) Naira.digitWords(d) else d.filter { it in '0'..'9' }.toList().joinToString(" ")
    fun count(n: Long): String = if (wordy) Naira.words(n) else n.toString()

    // Shared
    val back get() = t("Back", "Go back", "Padà sẹ́yìn", "Koma baya", "Laghachi azụ")
    val backToHome get() = t("Back to home", "Go back home", "Padà sí ilé", "Koma gida", "Laghachi n'ụlọ")
    val settings get() = t("Settings", "Settings", "Ètò", "Saituna", "Ntọala")
    val captions get() = t("Captions", "Captions", "Àkọlé", "Rubutu", "Ederede")
    val camera get() = t("Camera", "Camera", "Kámẹ́rà", "Kyamara", "Igwe foto")
    val working get() = t("Working", "I dey work", "Ó ń ṣiṣẹ́", "Ana aiki", "Ọ na-arụ ọrụ")
    val done get() = t("Done", "Done", "Ó parí", "An gama", "Emechaala")
    val tryAgain get() = t("Please try again.", "Abeg try again.", "Ẹ jọ̀ọ́ ẹ tún gbìyànjú.", "Don Allah ku sake gwadawa.", "Biko nwaa ọzọ.")
    val useButtons get() = t(
        "You can also use the buttons on the screen.",
        "You fit also use the buttons for the screen.",
        "Ẹ tún lè lo àwọn bọ́tìnnì lójú fóònù.",
        "Kuna iya amfani da maɓallan da ke kan allon.",
        "Ị nwekwara ike iji bọtịnụ ndị dị na ihuenyo.",
    )
    fun stepOf(step: Int, of: Int) = t("Step $step of $of", "Step $step of $of", "Ìgbésẹ̀ $step nínú $of", "Mataki na $step cikin $of", "Nzọụkwụ $step n'ime $of")
    val today get() = t("Today", "Today", "Òní", "Yau", "Taa")
    val yesterday get() = t("Yesterday", "Yesterday", "Àná", "Jiya", "Ụnyaahụ")

    // Looks
    val paper get() = t("Paper", "Paper", "Ìwé", "Takarda", "Akwụkwọ")
    val night get() = t("Night", "Night", "Òru", "Dare", "Abalị")
    val highContrast get() = t("High contrast", "High contrast", "Ìyàtọ̀ gíga", "Bambanci mai ƙarfi", "Ọdịiche dị elu")

    // Voice orb
    val tapToTalk get() = t("Tap to talk", "Tap make you talk", "Tẹ̀ ẹ́ láti sọ̀rọ̀", "Taɓa don magana", "Pịa ka ị kwuo okwu")
    val listening get() = t("Listening", "I dey listen", "Mo ń gbọ́", "Ina saurare", "Ana m ege ntị")
    val workingItOut get() = t("Working it out", "I dey find am", "Mo ń ṣiṣẹ́ lé e lórí", "Ina dubawa", "Ana m achọpụta ya")
    val speaking get() = t("Speaking", "I dey talk", "Mo ń sọ̀rọ̀", "Ina magana", "Ana m ekwu okwu")
    val talkToOminira get() = t("Talk to Òmìnira", "Talk to Òmìnira", "Bá Òmìnira sọ̀rọ̀", "Yi magana da Òmìnira", "Gwa Òmìnira okwu")
    val stop get() = t("Stop", "Stop", "Dúró", "Tsaya", "Kwụsị")

    // Money views
    val checkBeforeSend get() = t("Check before you send", "Check am before you send", "Ẹ yẹ̀ ẹ́ wò kí ẹ tó fi ránṣẹ́", "Ku duba kafin ku aika", "Lelee ya tupu i ziga ya")
    val ending get() = t("ending", "wey end with", "tí ó parí sí", "mai ƙarewa da", "nke na-ejedebe na")
    val readItToMe get() = t("Read it to me", "Read am give me", "Kà á fún mi", "Karanta mini", "Gụọrọ m ya")
    val currentAccount get() = t("Current account", "Current account", "Àkáǹtì lọ́wọ́lọ́wọ́", "Asusun yau da kullum", "Akaụntụ nkịtị")
    val blocked get() = t("Blocked", "E don block", "Ti dí i", "An toshe", "Egbochiri ya")
    fun cardSummary(last4: String, isBlocked: Boolean) = t(
        "Card ending ${digits(last4)}, " + if (isBlocked) "blocked" else "active",
        "Card wey end with ${digits(last4)}, " + if (isBlocked) "e don block" else "e dey work",
        "Káàdì tí ó parí sí ${digits(last4)}, " + if (isBlocked) "a ti dí i" else "ó ń ṣiṣẹ́",
        "Kati mai ƙarewa da ${digits(last4)}, " + if (isBlocked) "an toshe" else "yana aiki",
        "Kaadị nke na-ejedebe na ${digits(last4)}, " + if (isBlocked) "egbochiri ya" else "ọ na-arụ ọrụ",
    )
    fun txn(incoming: Boolean, amount: String, who: String, day: String): String {
        val whenSaid = if (day.isBlank()) "." else ", " + (if (lang == Lang.En || lang == Lang.Pcm) day.lowercase() else day) + "."
        val line = if (incoming) {
            t("In: $amount from $who", "Enter: $amount from $who", "Ó wọlé: $amount láti ọ̀dọ̀ $who", "Shiga: $amount daga $who", "Ọ batara: $amount site n'aka $who")
        } else {
            t("Out: $amount to $who", "Comot: $amount give $who", "Ó jáde: $amount sí $who", "Fita: $amount zuwa ga $who", "Ọ pụrụ: $amount nye $who")
        }
        return line + whenSaid
    }

    // Sonar selfie
    val sonarSteps: List<String> get() = listOf(
        t("Hold the phone at arm's length, then press start.", "Hold the phone far reach your hand, then press start.", "Ẹ na ọwọ́ di fóònù mú, lẹ́yìn náà ẹ tẹ bẹ̀rẹ̀.", "Ku miƙa hannu ku riƙe wayar, sannan ku danna fara.", "Setịa aka jide ekwentị, mgbe ahụ pịa malite."),
        t("Move the phone up a little.", "Raise the phone small.", "Ẹ gbé fóònù sókè díẹ̀.", "Ku ɗaga wayar kaɗan.", "Bulie ekwentị elu ntakịrị."),
        t("A little to the left.", "Small to your left.", "Díẹ̀ sí apá òsì.", "Kaɗan zuwa hagu.", "Ntakịrị gaa n'aka ekpe."),
        t("Almost there. Keep still.", "E don almost reach. No move.", "Ó fẹ́rẹ̀ tó. Ẹ má mì.", "Kusan ya yi. Ku tsaya cak.", "Ọ fọdụrụ ntakịrị. Kwụrụ jụụ."),
        t("Got it. Now say the four numbers.", "I don see am. Now talk the four numbers.", "Mo ti rí i. Ẹ sọ àwọn nọ́ńbà mẹ́rin náà báyìí.", "Na samu. Yanzu ku faɗi lambobi huɗun.", "Ahụla m ya. Ugbu a kwuo nọmba anọ ahụ."),
        t("Thank you. That is you, and you are here.", "Thank you. Na you be this, and you dey here.", "Ẹ ṣé. Ẹ̀yin ni, ẹ sì wà níbí.", "Na gode. Ku ne, kuma kuna nan.", "Daalụ. Ọ bụ gị, ị nọkwa ebe a."),
    )
    val selfieFailed get() = t(
        "I could not start the selfie. Check the connection and try again.",
        "I no fit start the selfie. Check your network and try again.",
        "Mi ò lè bẹ̀rẹ̀ fọ́tò ojú. Ẹ yẹ ìsopọ̀ wò, kí ẹ sì tún gbìyànjú.",
        "Ban iya fara hoton fuska ba. Ku duba haɗin intanet ku sake gwadawa.",
        "Enweghị m ike ịmalite foto ihu. Lelee njikọ ma nwaa ọzọ.",
    )
    val sayTheseNumbers get() = t("Got it. Now say these numbers.", "I don see am. Now talk these numbers.", "Mo ti rí i. Ẹ sọ àwọn nọ́ńbà wọ̀nyí.", "Na samu. Yanzu ku faɗi waɗannan lambobi.", "Ahụla m ya. Ugbu a kwuo nọmba ndị a.")
    fun sayNumbers(list: String) = t("Say these numbers: $list", "Talk these numbers: $list", "Ẹ sọ àwọn nọ́ńbà wọ̀nyí: $list", "Ku faɗi waɗannan lambobi: $list", "Kwuo nọmba ndị a: $list")
    val notYou get() = t(
        "I could not confirm that was you. Let's try once more.",
        "I no fit confirm say na you. Make we try again.",
        "Mi ò lè fìdí rẹ̀ múlẹ̀ pé ẹ̀yin ni. Ẹ jẹ́ ká tún gbìyànjú lẹ́ẹ̀kan sí i.",
        "Ban iya tabbatar cewa ku ne ba. Mu sake gwadawa sau ɗaya.",
        "Enweghị m ike ikwenye na ọ bụ gị. Ka anyị nwaa ọzọ otu ugboro.",
    )
    val tapToStart get() = t("Tap anywhere on the screen to start.", "Touch anywhere for the screen make we start.", "Ẹ tẹ ibikíbi lójú fóònù láti bẹ̀rẹ̀.", "Ku taɓa ko'ina a allon don farawa.", "Pịa ebe ọ bụla na ihuenyo ka ịmalite.")
    val sayStartOrTap get() = t(
        "Say start when you are ready, or tap anywhere.",
        "Talk start when you ready, or touch anywhere.",
        "Ẹ sọ bẹ̀rẹ̀ nígbà tí ẹ bá ṣetán, tàbí ẹ tẹ ibikíbi.",
        "Ku ce fara idan kun shirya, ko ku taɓa ko'ina.",
        "Kwuo malite mgbe ị dị njikere, ma ọ bụ pịa ebe ọ bụla.",
    )
    val tapWhenReady get() = t(
        "Tap anywhere on the screen when you are ready.",
        "Touch anywhere for the screen when you ready.",
        "Ẹ tẹ ibikíbi lójú fóònù nígbà tí ẹ bá ṣetán.",
        "Ku taɓa ko'ina a allon idan kun shirya.",
        "Pịa ebe ọ bụla na ihuenyo mgbe ị dị njikere.",
    )
    val selfieHeading get() = t("Let's take your selfie by sound.", "Make we snap your selfie with sound.", "Ẹ jẹ́ ká ya fọ́tò ojú yín pẹ̀lú ohùn.", "Mu ɗauki hoton fuskarku da sauti.", "Ka anyị jiri ụda see foto ihu gị.")
    val holdAtArm get() = t(
        "Hold the phone at arm's length, with the screen facing you.",
        "Hold the phone far reach your hand, with the screen facing you.",
        "Ẹ na ọwọ́ di fóònù mú, kí ojú rẹ̀ kọjú sí yín.",
        "Ku miƙa hannu ku riƙe wayar, allon yana fuskantarku.",
        "Setịa aka jide ekwentị, ihuenyo chere gị ihu.",
    )
    val nextIdCard get() = t("Next: your ID card", "Next: your ID card", "Èyí tó kàn: káàdì ìdánimọ̀ yín", "Na gaba: katin shaidarku", "Nke ọzọ: kaadị njirimara gị")
    val startSonar get() = t("Start the sonar", "Start the sonar", "Bẹ̀rẹ̀ sónà", "Fara sonar", "Malite sonar")
    val findingFace get() = t("Finding your face", "I dey find your face", "Mo ń wá ojú yín", "Ina neman fuskarku", "Ana m achọ ihu gị")
    val faceInFrame get() = t("Face in the frame", "Your face don enter the frame", "Ojú yín wà nínú férémù", "Fuskarku tana cikin firam", "Ihu gị dị n'ime okpokolo")
    val sonarKicker get() = t("Sonar selfie", "Sonar selfie", "Fọ́tò ojú pẹ̀lú ohùn", "Hoton fuska da sauti", "Foto ihu site n'ụda")

    // ID by ear
    val cardSteps: List<String> get() = listOf(
        t("Ready when you are.", "I dey ready when you ready.", "Mo ti ṣetán nígbà tí ẹ bá ṣetán.", "Na shirya idan kun shirya.", "Adị m njikere mgbe ị dị njikere."),
        t("Hold your card up a little.", "Raise your card small.", "Ẹ gbé káàdì yín sókè díẹ̀.", "Ku ɗaga katinku kaɗan.", "Bulie kaadị gị elu ntakịrị."),
        t("Tilt left a little.", "Bend am small to left.", "Ẹ tẹ̀ ẹ́ sí òsì díẹ̀.", "Ku karkata shi hagu kaɗan.", "Gbagọọ ya n'aka ekpe ntakịrị."),
        t("Got it.", "I don get am.", "Mo ti rí i.", "Na samu.", "Ahụla m ya."),
    )
    val isThisYou get() = t("Is this you?", "Na you be this?", "Ṣé ẹ̀yin nìyí?", "Ku ne wannan?", "Ọ bụ gị bụ nke a?")
    // spell: "N I N", so a voice says the letters.
    fun ninEnding(last4: String, spell: Boolean = false): String {
        val nin = if (spell) "N I N" else "NIN"
        val d = digits(last4)
        return t("$nin ending $d.", "$nin wey end with $d.", "$nin tí ó parí sí $d.", "$nin mai ƙarewa da $d.", "$nin nke na-ejedebe na $d.")
    }
    fun born(date: String) = t("Born $date.", "Born $date.", "A bí yín ní $date.", "An haife ku a $date.", "A mụrụ gị na $date.")
    val confirmId get() = t(
        "Say yes if that is you, or say again to hear it again.",
        "Talk yes if na you, or talk again make you hear am again.",
        "Ẹ sọ bẹ́ẹ̀ni tí ẹ̀yin ni, tàbí ẹ sọ lẹ́ẹ̀kan sí i láti gbọ́ ọ lẹ́ẹ̀kan sí i.",
        "Ku ce eh idan ku ne, ko ku ce sake don ku sake ji.",
        "Kwuo ee ma ọ bụ gị, ma ọ bụ kwuo ọzọ ka ị nụ ya ọzọ.",
    )
    val nowIdCard get() = t("Now your ID card.", "Now your ID card.", "Báyìí, káàdì ìdánimọ̀ yín.", "Yanzu katin shaidarku.", "Ugbu a, kaadị njirimara gị.")
    val holdNin get() = t(
        "Hold your NIN slip or ID card in front of the back camera.",
        "Hold your NIN slip or ID card for front of the back camera.",
        "Ẹ di ìwé NIN tàbí káàdì ìdánimọ̀ yín mú níwájú kámẹ́rà ẹ̀yìn.",
        "Ku riƙe takardar NIN ko katin shaidarku a gaban kyamarar baya.",
        "Jide akwụkwọ NIN gị ma ọ bụ kaadị njirimara n'ihu igwe foto azụ.",
    )
    val guideThenRead get() = t(
        "I will guide you, then read it back.",
        "I go guide you, then read am back give you.",
        "Màá tọ́ yín sọ́nà, lẹ́yìn náà màá kà á padà fún yín.",
        "Zan yi muku jagora, sannan in karanta muku.",
        "M ga-eduzi gị, mgbe ahụ gụọghachi ya.",
    )
    val alsoTapYes get() = t("You can also tap Yes, that is me.", "You fit also touch Yes, na me.", "Ẹ tún lè tẹ Bẹ́ẹ̀ni, èmi ni.", "Kuna iya taɓa Eh, ni ne.", "Ị nwekwara ike ịpị Ee, ọ bụ m.")
    val cardAgain get() = t("Let's try the card again.", "Make we try the card again.", "Ẹ jẹ́ ká tún gbìyànjú káàdì náà.", "Mu sake gwada katin.", "Ka anyị nwaa kaadị ahụ ọzọ.")
    val cardFailed get() = t(
        "I could not read the card. Check the connection and try again.",
        "I no fit read the card. Check your network and try again.",
        "Mi ò lè ka káàdì náà. Ẹ yẹ ìsopọ̀ wò, kí ẹ sì tún gbìyànjú.",
        "Ban iya karanta katin ba. Ku duba haɗin intanet ku sake gwadawa.",
        "Enweghị m ike ịgụ kaadị ahụ. Lelee njikọ ma nwaa ọzọ.",
    )
    val yesThatsMe get() = t("Yes, that is me", "Yes, na me", "Bẹ́ẹ̀ni, èmi ni", "Eh, ni ne", "Ee, ọ bụ m")
    val readAgain get() = t("Read it to me again", "Read am give me again", "Tún kà á fún mi", "Sake karanta mini", "Gụọrọ m ya ọzọ")
    val startCamera get() = t("Start the camera", "Start the camera", "Bẹ̀rẹ̀ kámẹ́rà", "Fara kyamara", "Malite igwe foto")
    val findingCard get() = t("Finding your card", "I dey find your card", "Mo ń wá káàdì yín", "Ina neman katinku", "Ana m achọ kaadị gị")
    val cardCaptured get() = t("Card captured", "I don snap the card", "Mo ti ya káàdì náà", "An ɗauki katin", "Ejidere kaadị ahụ")
    val idKicker get() = t("ID by ear", "ID by ear", "Ìdánimọ̀ pẹ̀lú etí", "Shaida ta kunne", "Njirimara site na ntị")

    // BVN
    val bvnFailed get() = t(
        "I could not check your BVN. Check the connection and try again.",
        "I no fit check your BVN. Check your network and try again.",
        "Mi ò lè yẹ BVN yín wò. Ẹ yẹ ìsopọ̀ wò, kí ẹ sì tún gbìyànjú.",
        "Ban iya duba BVN ɗinku ba. Ku duba haɗin intanet ku sake gwadawa.",
        "Enweghị m ike ilele BVN gị. Lelee njikọ ma nwaa ọzọ.",
    )
    val accountOpen get() = t("Your account is open.", "Your account don open.", "A ti ṣí àkáǹtì yín.", "An buɗe asusunku.", "Emepere akaụntụ gị.")
    val nextPulse get() = t("Next, your Pulse PIN.", "Next na your Pulse PIN.", "Èyí tó kàn ni Pulse PIN yín.", "Na gaba, Pulse PIN ɗinku.", "Nke ọzọ, Pulse PIN gị.")
    val nextPulseButton get() = t("Next: your Pulse PIN", "Next: your Pulse PIN", "Èyí tó kàn: Pulse PIN yín", "Na gaba: Pulse PIN ɗinku", "Nke ọzọ: Pulse PIN gị")
    val lastCheck get() = t("Last check", "Last check", "Àyẹ̀wò tó kẹ́yìn", "Bincike na ƙarshe", "Nlele ikpeazụ")
    val selfie get() = t("Selfie", "Selfie", "Fọ́tò ojú", "Hoton fuska", "Foto ihu")
    val idCard get() = t("ID card", "ID card", "Káàdì ìdánimọ̀", "Katin shaida", "Kaadị njirimara")
    // BVN by consent: Zenith asks NIBSS, so the customer never says the 11 digits
    val bvnConsentHeading get() = t("Confirm your BVN", "Confirm your BVN", "Ẹ jẹ́rìí BVN yín", "Tabbatar da BVN ɗinku", "Kwado BVN gị")
    val bvnConsent get() = t(
        "Zenith will confirm your BVN with NIBSS, using your NIN and this phone number. You do not need to say it.",
        "Zenith go confirm your BVN with NIBSS, with your NIN and this phone number. You no need talk am.",
        "Zenith yóò jẹ́rìí BVN yín lọ́dọ̀ NIBSS, pẹ̀lú NIN yín àti nọ́ńbà fóònù yìí. Ẹ kò nílò láti sọ ọ́.",
        "Zenith za ta tabbatar da BVN ɗinku a NIBSS, ta amfani da NIN ɗinku da lambar wannan wayar. Ba sai kun faɗa ba.",
        "Zenith ga-akwado BVN gị na NIBSS, site na NIN gị na nọmba ekwentị a. Ịchọghị ikwu ya.",
    )
    val allowYesOrTap get() = t(
        "Say yes, or tap anywhere, to allow it.",
        "Talk yes, or touch anywhere, make you allow am.",
        "Ẹ sọ bẹ́ẹ̀ni, tàbí ẹ tẹ ibikíbi, láti gbà á láàyè.",
        "Ku ce eh, ko ku taɓa ko'ina, don ku ba da izini.",
        "Kwuo ee, ma ọ bụ pịa ebe ọ bụla, ka ịkwado ya.",
    )
    val allowTap get() = t(
        "Tap anywhere to allow it. Touch and hold for not now.",
        "Tap anywhere make you allow am. Press am down small for not now.",
        "Ẹ tẹ ibikíbi láti gbà á láàyè. Ẹ tẹ̀ ẹ́ mọ́lẹ̀ díẹ̀ fún kì í ṣe báyìí.",
        "Ku taɓa ko'ina don ku ba da izini. Ku danna ku riƙe don ba yanzu ba.",
        "Pịa ebe ọ bụla ka ịkwado ya. Pịa ma jide maka ọ bụghị ugbu a.",
    )
    val allowCheck get() = t("Allow Zenith to check", "Make Zenith check am", "Gba Zenith láàyè láti yẹ̀ ẹ́ wò", "Bari Zenith ta duba", "Kwe ka Zenith lelee ya")
    val checkingBvn get() = t("Checking your BVN with NIBSS…", "I dey check your BVN with NIBSS…", "Mo ń yẹ BVN yín wò lọ́dọ̀ NIBSS…", "Ina duba BVN ɗinku a NIBSS…", "Ana m elele BVN gị na NIBSS…")
    val bvnDeclined get() = t(
        "Without it, Zenith cannot open your account. Tap anywhere when you are ready to allow it.",
        "Without am, Zenith no fit open your account. Touch anywhere when you ready to allow am.",
        "Láìsí rẹ̀, Zenith kò lè ṣí àkáǹtì yín. Ẹ tẹ ibikíbi nígbà tí ẹ bá ṣetán láti gbà á láàyè.",
        "Ba tare da shi ba, Zenith ba za ta iya buɗe asusunku ba. Ku taɓa ko'ina idan kun shirya ba da izini.",
        "Na-enweghị ya, Zenith enweghị ike imepe akaụntụ gị. Pịa ebe ọ bụla mgbe ị dị njikere ịkwado ya.",
    )
    val bvnFound get() = t(
        "Zenith found your BVN with NIBSS. It matches your card and your face.",
        "Zenith don find your BVN for NIBSS. E match your card and your face.",
        "Zenith ti rí BVN yín lọ́dọ̀ NIBSS. Ó bá káàdì àti ojú yín mu.",
        "Zenith ta sami BVN ɗinku a NIBSS. Ya dace da katinku da fuskarku.",
        "Zenith achọtala BVN gị na NIBSS. Ọ kwekọrọ na kaadị gị na ihu gị.",
    )

    // Choosing the Pulse PIN
    val choosePinTitle get() = t("Choose your Pulse PIN", "Choose your Pulse PIN", "Ẹ yan Pulse PIN yín", "Zaɓi Pulse PIN ɗinku", "Họrọ Pulse PIN gị")
    val choosePinIntro get() = t(
        "Now choose your Pulse PIN: four digits only you know. Practise first if you like.",
        "Now choose your Pulse PIN: four numbers wey only you sabi. Practise first if you like.",
        "Báyìí, ẹ yan Pulse PIN yín: nọ́ńbà mẹ́rin tí ẹ̀yin nìkan mọ̀. Ẹ lè kọ́kọ́ dánrawò tí ẹ bá fẹ́.",
        "Yanzu ku zaɓi Pulse PIN ɗinku: lambobi huɗu da ku kaɗai kuka sani. Ku gwada tukuna idan kuna so.",
        "Ugbu a họrọ Pulse PIN gị: nọmba anọ naanị gị maara. Mee omume mbụ ma ọ masị gị.",
    )
    val confirmPinTitle get() = t("Pulse it again to check", "Pulse am again make we check", "Ẹ tún pulse rẹ̀ láti yẹ̀ ẹ́ wò", "Ku sake yin pulse don dubawa", "Pulse ya ọzọ ka e lelee")
    val confirmPinSay get() = t("Now pulse the same PIN again.", "Now pulse the same PIN again.", "Báyìí, ẹ tún pulse PIN kan náà.", "Yanzu ku sake yin pulse na PIN ɗin.", "Ugbu a, pulse otu PIN ahụ ọzọ.")
    val pinTooEasy get() = t(
        "That PIN is too easy to guess. Choose another.",
        "That PIN too easy to guess. Choose another one.",
        "PIN yẹn rọrùn jù láti mọ̀. Ẹ yan òmíràn.",
        "Wannan PIN yana da sauƙin ganewa. Ku zaɓi wani.",
        "PIN ahụ dị mfe ịkọ. Họrọ ọzọ.",
    )
    val pinIsBirthday get() = t(
        "That PIN is your birthday, from your ID. Choose another.",
        "That PIN na your birthday, from your ID. Choose another one.",
        "PIN yẹn ni ọjọ́ ìbí yín, láti inú káàdì ìdánimọ̀ yín. Ẹ yan òmíràn.",
        "Wannan PIN ranar haihuwarku ce, daga katin shaidarku. Ku zaɓi wani.",
        "PIN ahụ bụ ụbọchị ọmụmụ gị, site na kaadị njirimara gị. Họrọ ọzọ.",
    )
    val pinMismatch get() = t("Those were different. Let's start again.", "Dem no be the same. Make we start again.", "Wọ́n yàtọ̀ síra. Ẹ jẹ́ ká tún bẹ̀rẹ̀.", "Sun bambanta. Mu sake farawa.", "Ha dị iche. Ka anyị malite ọzọ.")
    val pinSet get() = t(
        "Your Pulse PIN is set. Nobody saw it, not even me.",
        "Your Pulse PIN don set. Nobody see am, even me.",
        "A ti ṣètò Pulse PIN yín. Kò sí ẹni tó rí i, àní èmi pàápàá.",
        "An saita Pulse PIN ɗinku. Babu wanda ya gan shi, har ni ma.",
        "Edozila Pulse PIN gị. Ọ dịghị onye hụrụ ya, ọbụna m.",
    )
    val pinSetFailed get() = t(
        "I could not save your PIN just now. Please try again.",
        "I no fit save your PIN now. Abeg try again.",
        "Mi ò lè fi PIN yín pamọ́ báyìí. Ẹ jọ̀ọ́ ẹ tún gbìyànjú.",
        "Ban iya ajiye PIN ɗinku yanzu ba. Don Allah ku sake gwadawa.",
        "Enweghị m ike ichekwa PIN gị ugbu a. Biko nwaa ọzọ.",
    )
    val bvnMatches get() = t("Your BVN matches your card and your face.", "Your BVN match your card and your face.", "BVN yín bá káàdì àti ojú yín mu.", "BVN ɗinku ya dace da katinku da fuskarku.", "BVN gị kwekọrọ na kaadị gị na ihu gị.")
    val bvnNoMatch get() = t(
        "That BVN does not match your card. Please check it and try again.",
        "That BVN no match your card. Abeg check am and try again.",
        "BVN yẹn kò bá káàdì yín mu. Ẹ jọ̀ọ́ ẹ yẹ̀ ẹ́ wò kí ẹ tún gbìyànjú.",
        "Wannan BVN bai dace da katinku ba. Don Allah ku duba ku sake gwadawa.",
        "BVN ahụ ekwekọghị na kaadị gị. Biko lelee ya ma nwaa ọzọ.",
    )

    // Unlock
    fun yourPulsePin(name: String) = t("Your Pulse PIN, $name.", "Your Pulse PIN, $name.", "Pulse PIN yín, $name.", "Pulse PIN ɗinku, $name.", "Pulse PIN gị, $name.")
    val unlocked get() = t("Unlocked. Nobody saw a thing.", "E don open. Nobody see anything.", "Ó ti ṣí. Kò sí ẹni tó rí nǹkankan.", "An buɗe. Babu wanda ya ga komai.", "Emepela ya. Ọ dịghị onye hụrụ ihe ọ bụla.")
    val goToMyHome get() = t("Go to my home", "Carry me go my home", "Lọ sí ojú-ìwé ilé mi", "Je zuwa shafin gida na", "Gaa n'ihu ụlọ m")
    val pinCheckFailed get() = t(
        "I could not check your PIN just now. Please try again.",
        "I no fit check your PIN now. Abeg try again.",
        "Mi ò lè yẹ PIN yín wò báyìí. Ẹ jọ̀ọ́ ẹ tún gbìyànjú.",
        "Ban iya duba PIN ɗinku yanzu ba. Don Allah ku sake gwadawa.",
        "Enweghị m ike ilele PIN gị ugbu a. Biko nwaa ọzọ.",
    )

    // Homes
    fun greeting(name: String, hour: Int) = Lines.greeting(lang, name, hour)
    val whatToDo get() = Lines.whatToDo(lang)
    val couldNotDo get() = t(
        "Sorry, I could not do that just now. Please try again.",
        "Sorry, I no fit do am now. Abeg try again.",
        "Ẹ má bínú, mi ò lè ṣe é báyìí. Ẹ jọ̀ọ́ ẹ tún gbìyànjú.",
        "Yi haƙuri, ban iya yin hakan yanzu ba. Don Allah ku sake gwadawa.",
        "Ndo, enweghị m ike ime ya ugbu a. Biko nwaa ọzọ.",
    )
    val anythingElse get() = t("Anything else?", "Anything again?", "Ṣé nǹkan mìíràn wà?", "Akwai wani abu kuma?", "Ọ dị ihe ọzọ?")
    val homeTitle get() = t("Home.", "Home.", "Ilé.", "Gida.", "Ụlọ.")
    fun balanceIs(kobo: Long?) = if (kobo == null) {
        t("Your balance is still loading.", "Your balance still dey load.", "Owó inú àkáǹtì yín ṣì ń bọ̀.", "Ana ci gaba da loda kuɗin asusunku.", "Ego dị na akaụntụ gị ka na-ebu.")
    } else {
        val a = money(kobo)
        t("Your balance is $a.", "Your balance na $a.", "Owó inú àkáǹtì yín jẹ́ $a.", "Kuɗin asusunku: $a.", "Ego dị na akaụntụ gị bụ $a.")
    }
    val youCanSay get() = t(
        "You can say: send money to Tolu, what came in this week, or block my card.",
        "You fit talk: send money give Tolu, wetin enter this week, or block my card.",
        "Ẹ lè sọ pé: fi owó ránṣẹ́ sí Tolu, kí ló wọlé lọ́sẹ̀ yìí, tàbí dí káàdì mi.",
        "Kuna iya cewa: aika kuɗi ga Tolu, me ya shigo wannan mako, ko toshe katina.",
        "Ị nwere ike ikwu: ziga Tolu ego, gịnị batara n'izu a, ma ọ bụ gbochie kaadị m.",
    )
    fun youHave(kobo: Long): String {
        val a = money(kobo)
        return t("You have $a.", "You get $a.", "Ẹ ní $a.", "Kuna da $a.", "Ị nwere $a.")
    }
    val youSaid get() = t("You said", "You talk", "Ẹ sọ pé", "Kun ce", "Ị kwuru")
    val ominiraSaid get() = t("Òmìnira said", "Òmìnira talk", "Òmìnira sọ pé", "Òmìnira ya ce", "Òmìnira kwuru")
    val noVoice get() = t("Voice is not available on this phone yet.", "Voice no dey work for this phone yet.", "Ohùn kò tíì ṣiṣẹ́ lórí fóònù yìí.", "Murya ba ta samu a wannan wayar ba tukuna.", "Olu adịbeghị na ekwentị a.")
    val tapRequestBelow get() = t("Tap one of the requests below instead.", "Touch one of the requests wey dey below.", "Ẹ tẹ ọ̀kan nínú àwọn ìbéèrè ìsàlẹ̀ yìí dípò.", "Ku taɓa ɗaya daga cikin buƙatun da ke ƙasa.", "Pịa otu n'ime arịrịọ ndị dị n'okpuru.")
    val trySaying get() = t("Try saying", "Try talk", "Ẹ gbìyànjú láti sọ", "Ku gwada cewa", "Nwaa ikwu")
    val suggestSend get() = t("“Send ₦20,000 to Tolu”", "“Send ₦20,000 give Tolu”", "“Fi ₦20,000 ránṣẹ́ sí Tolu”", "“Aika ₦20,000 ga Tolu”", "“Ziga Tolu ₦20,000”")
    val suggestCameIn get() = t("“What came in this week?”", "“Wetin enter this week?”", "“Kí ló wọlé lọ́sẹ̀ yìí?”", "“Me ya shigo wannan mako?”", "“Gịnị batara n'izu a?”")
    val suggestBlock get() = t("“Block my card”", "“Block my card”", "“Dí káàdì mi”", "“Toshe katina”", "“Gbochie kaadị m”")
    val signHelp get() = t("Sign language help", "Sign language help", "Ìrànlọ́wọ́ èdè àwọn adití", "Taimako da yaren kurame", "Enyemaka asụsụ aka")
    val hearThisScreen get() = t("Hear this screen", "Hear this screen", "Gbọ́ ojú-ìwé yìí", "Ji wannan shafi", "Nụ ihuenyo a")
    val orJustSay get() = t("Or just say it", "Or just talk am", "Tàbí ẹ kàn sọ ọ́", "Ko ku faɗa kawai", "Ma ọ bụ kwuo ya")
    val sendMoney get() = t("Send money", "Send money", "Fi owó ránṣẹ́", "Aika kuɗi", "Ziga ego")
    val howMuchHave get() = t("How much do I have?", "How much I get?", "Èló ni mo ní?", "Nawa nake da shi?", "Ego ole ka m nwere?")
    val myCard get() = t("My card", "My card", "Káàdì mi", "Katina", "Kaadị m")
    val myBalance get() = t("My balance", "My balance", "Owó mi", "Kuɗina", "Ego m")
    val talkToPerson get() = t("Talk to a person", "Talk to person", "Bá ènìyàn sọ̀rọ̀", "Yi magana da mutum", "Gwa mmadụ okwu")
    val callingPerson get() = t(
        "Calling a Zenith person who speaks your language.",
        "I dey call Zenith person wey sabi your language.",
        "Mo ń pe òṣìṣẹ́ Zenith tó ń sọ èdè yín.",
        "Ina kiran ma'aikacin Zenith mai magana da harshenku.",
        "Ana m akpọ onye Zenith na-asụ asụsụ gị.",
    )
    val stayOnLine get() = t("Stay on the line.", "No cut the call.", "Ẹ dúró lórí ìpè.", "Ku tsaya a kan layi.", "Nọrọ na ekwentị.")
    val resumeScanning get() = t("Resume scanning", "Continue scanning", "Tún bẹ̀rẹ̀ ìyíká", "Ci gaba da zagayawa", "Gaa n'ihu na-agagharị")
    val pauseScanning get() = t("Pause scanning", "Pause scanning", "Dá ìyíká dúró", "Dakatar da zagayawa", "Kwụsịtụ ịgagharị")
    val pressSwitch get() = t("Press your switch", "Press your switch", "Tẹ bọ́tìnnì yín", "Danna maɓallinku", "Pịa bọtịnụ gị")
    val scanPaused get() = t("Scanning is paused.", "Scanning don pause.", "Ìyíká ti dúró.", "An dakatar da zagayawa.", "Ịgagharị akwụsịtụla.")
    fun ringMoves(seconds: Double) = t(
        "The ring moves every $seconds seconds. Press when it reaches what you want.",
        "The ring dey move every $seconds seconds. Press when e reach wetin you want.",
        "Òrùka náà ń yí ní gbogbo ìṣẹ́jú-àáyá $seconds. Ẹ tẹ̀ ẹ́ nígbà tí ó bá dé ohun tí ẹ fẹ́.",
        "Zoben yana motsi kowane daƙiƙa $seconds. Ku danna idan ya kai abin da kuke so.",
        "Mgbanaka ahụ na-aga kwa sekọnd $seconds. Pịa mgbe ọ ruru ihe ị chọrọ.",
    )
    val open get() = t("open", "open", "ṣí i", "buɗe", "mepee")
    val chosenPressAgain get() = t("Chosen. Press again to open.", "You don choose am. Press again make e open.", "A ti yàn án. Ẹ tún tẹ̀ ẹ́ láti ṣí i.", "An zaɓa. Ku sake danna don buɗewa.", "Ahọrọla ya. Pịa ọzọ ka imepee ya.")
    val highlighted get() = t("Highlighted", "E dey ringed", "Ó ti tàn", "An haskaka", "Egosiri ya")
    fun youChose(label: String) = t("You chose $label.", "You choose $label.", "Ẹ yan $label.", "Kun zaɓi $label.", "Ị họrọ $label.")
    val pressOrWait get() = t(
        "Press again to open it, or wait to keep scanning.",
        "Press again make e open, or wait make e continue.",
        "Ẹ tún tẹ̀ ẹ́ láti ṣí i, tàbí ẹ dúró kí ó máa yí lọ.",
        "Ku sake danna don buɗe shi, ko ku jira ya ci gaba da zagayawa.",
        "Pịa ọzọ ka imepee ya, ma ọ bụ chere ka ọ gaa n'ihu.",
    )

    // Send money
    val confirmSend get() = t(
        "Say yes to go on to your Pulse PIN, or no to stop.",
        "Talk yes make we go your Pulse PIN, or no make we stop.",
        "Ẹ sọ bẹ́ẹ̀ni láti lọ sí Pulse PIN yín, tàbí rárá láti dúró.",
        "Ku ce eh don ku je Pulse PIN ɗinku, ko a'a don tsayawa.",
        "Kwuo ee ka i gaa na Pulse PIN gị, ma ọ bụ mba ka ịkwụsị.",
    )
    val pauseQuestion get() = t(
        "Is someone on the phone telling you to send this?",
        "Na person for phone dey tell you make you send this?",
        "Ṣé ẹnìkan lórí fóònù ló ń sọ fún yín pé kí ẹ fi èyí ránṣẹ́?",
        "Akwai wani a waya da ke gaya muku ku aika wannan?",
        "Ọ dị onye nọ na ekwentị na-agwa gị ka i ziga nke a?",
    )
    val neverCalls get() = t(
        "Zenith will never call you to move money. If anyone on this call told you to do this, hang up now.",
        "Zenith no go ever call you make you move money. If anybody for this call tell you make you do am, cut the call now.",
        "Zenith kò ní pè yín láé pé kí ẹ gbé owó. Tí ẹnikẹ́ni lórí ìpè yìí bá sọ fún yín pé kí ẹ ṣe èyí, ẹ gé ìpè náà báyìí.",
        "Zenith ba za ta taɓa kiran ku don ku tura kuɗi ba. Idan wani a wannan kiran ya ce ku yi haka, ku katse kiran yanzu.",
        "Zenith agaghị akpọ gị ka i bufee ego. Ọ bụrụ na onye ọ bụla nọ na oku a gwara gị ime nke a, kwụsị oku ahụ ugbu a.",
    )
    val couldNotPrepare get() = t(
        "I could not prepare that. Nothing was sent. Please try again.",
        "I no fit prepare am. Nothing comot. Abeg try again.",
        "Mi ò lè ṣètò rẹ̀. Kò sí owó tó lọ. Ẹ jọ̀ọ́ ẹ tún gbìyànjú.",
        "Ban iya shirya hakan ba. Ba a aika komai ba. Don Allah ku sake gwadawa.",
        "Enweghị m ike ịkwadebe ya. Ọ dịghị ihe e zigara. Biko nwaa ọzọ.",
    )
    val stoppedNothingSent get() = t("Okay, I have stopped. Nothing was sent.", "Okay, I don stop. Nothing comot.", "Ó dáa, mo ti dúró. Kò sí owó tó lọ.", "To, na tsaya. Ba a aika komai ba.", "Ọ dị mma, akwụsịla m. Ọ dịghị ihe e zigara.")
    val cannotReachBank get() = t(
        "I cannot reach the bank right now. Nothing was sent.",
        "I no fit reach the bank now. Nothing comot.",
        "Mi ò lè dé ọ̀dọ̀ báǹkì báyìí. Kò sí owó tó lọ.",
        "Ba zan iya isa ga banki yanzu ba. Ba a aika komai ba.",
        "Enweghị m ike iru ụlọ akụ ugbu a. Ọ dịghị ihe e zigara.",
    )
    val cancelFailed get() = t(
        "I could not reach the bank to cancel it. It is still waiting. Try again.",
        "I no fit reach the bank to cancel am. E still dey wait. Try again.",
        "Mi ò lè dé ọ̀dọ̀ báǹkì láti fagi lé e. Ó ṣì ń dúró. Ẹ tún gbìyànjú.",
        "Ban iya isa ga banki don soke shi ba. Har yanzu yana jira. Ku sake gwadawa.",
        "Enweghị m ike iru ụlọ akụ ka m kagbuo ya. Ọ ka na-eche. Nwaa ọzọ.",
    )
    val sayStopOrCarryOn get() = t("Say stop, or say carry on.", "Talk stop, or talk carry on.", "Ẹ sọ dúró, tàbí ẹ sọ tẹ̀síwájú.", "Ku ce tsaya, ko ku ce ci gaba.", "Kwuo kwụsị, ma ọ bụ kwuo gaa n'ihu.")
    val whoAndHowMuch get() = t(
        "Who would you like to send money to, and how much?",
        "Who you wan send money give, and how much?",
        "Ta ni ẹ fẹ́ fi owó ránṣẹ́ sí, èló sì ni?",
        "Wa kuke so ku aika wa kuɗi, kuma nawa?",
        "Onye ka ị chọrọ iziga ego, ego ole kwa?",
    )
    val newAndLarge get() = t(
        "This is a new person and a large amount. Nothing has been sent.",
        "This na new person and big money. Nothing don comot.",
        "Ẹni tuntun ni èyí, owó náà sì pọ̀. Kò sí owó tó tíì lọ.",
        "Wannan sabon mutum ne kuma kuɗi masu yawa. Ba a aika komai ba tukuna.",
        "Onye ọhụrụ ka nke a bụ, ego ahụ dịkwa ukwuu. Ọ dịbeghị ihe e zigara.",
    )
    val newAndLargeTakeTime get() = t(
        "This is a new person and a large amount. Take your time. Nothing has been sent.",
        "This na new person and big money. Take your time. Nothing don comot.",
        "Ẹni tuntun ni èyí, owó náà sì pọ̀. Ẹ fara balẹ̀. Kò sí owó tó tíì lọ.",
        "Wannan sabon mutum ne kuma kuɗi masu yawa. Ku dau lokacinku. Ba a aika komai ba tukuna.",
        "Onye ọhụrụ ka nke a bụ, ego ahụ dịkwa ukwuu. Were nwayọọ. Ọ dịbeghị ihe e zigara.",
    )
    val homeOrSendMore get() = t(
        "Say home to go back, or say send to send more.",
        "Talk home make you go back, or talk send make you send more.",
        "Ẹ sọ ilé láti padà, tàbí ẹ sọ fi ránṣẹ́ láti fi òmíràn ránṣẹ́.",
        "Ku ce gida don komawa, ko ku ce aika don ƙara aikawa.",
        "Kwuo ụlọ ka ịlaghachi, ma ọ bụ kwuo ziga ka i ziga ọzọ.",
    )
    fun pulseToSend(amount: String) = t("Pulse your PIN to send $amount", "Pulse your PIN make $amount go", "Ẹ pulse PIN yín láti fi $amount ránṣẹ́", "Ku yi pulse PIN don aika $amount", "Pulse PIN gị ka i ziga $amount")
    val pulseYourPin get() = t("Pulse your PIN", "Pulse your PIN", "Ẹ pulse PIN yín", "Ku yi pulse PIN ɗinku", "Pulse PIN gị")
    val backToReadback get() = t("Back to the readback", "Go back to the readback", "Padà sí àkàsílẹ̀", "Koma ga karatun", "Laghachi na ngụghachi")
    val lockedNothingSent get() = t("Your Pulse PIN is locked. Nothing was sent.", "Your Pulse PIN don lock. Nothing comot.", "Pulse PIN yín ti tì pa. Kò sí owó tó lọ.", "An kulle Pulse PIN ɗinku. Ba a aika komai ba.", "Akpọchiela Pulse PIN gị. Ọ dịghị ihe e zigara.")
    val onACall get() = t("On a call", "You dey call", "Ẹ wà lórí ìpè", "Kuna kan waya", "Ị nọ na oku")
    fun pulseButtonSend(amount: String) = t("Pulse PIN to send $amount", "Pulse PIN make $amount go", "Pulse PIN láti fi $amount ránṣẹ́", "Pulse PIN don aika $amount", "Pulse PIN ka i ziga $amount")
    val somethingWrong get() = t("Something is wrong", "Something no correct", "Nǹkan kò tọ̀nà", "Akwai matsala", "Ihe adịghị mma")
    val cancelTransfer get() = t("Cancel this transfer", "Cancel this transfer", "Fagi lé owó yìí", "Soke wannan aikawa", "Kagbuo nzipu a")
    val sayIt get() = t("Say it", "Talk am", "Sọ ọ́", "Faɗa", "Kwuo ya")
    val workingOut get() = t("Working it out…", "I dey find am…", "Mo ń ṣiṣẹ́ lé e lórí…", "Ina dubawa…", "Ana m achọpụta ya…")
    val orChoosePerson get() = t("Or choose a person", "Or choose person", "Tàbí ẹ yan ẹnìkan", "Ko ku zaɓi mutum", "Ma ọ bụ họrọ mmadụ")
    val newToYou get() = t("New to you.", "You never send am before.", "Ẹ kò tíì fi ránṣẹ́ sí i rí.", "Sabo gare ku.", "Ọ bụ onye ọhụrụ nye gị.")
    fun howMuchTo(nick: String) = t("How much to $nick, in naira?", "How much give $nick, for naira?", "Èló ni sí $nick, ní náírà?", "Nawa za a aika ga $nick, a naira?", "Ego ole nye $nick, na naịra?")
    val checkBeforeSending get() = t("Check it before sending", "Check am before you send", "Yẹ̀ ẹ́ wò kí ẹ tó fi ránṣẹ́", "Duba kafin aikawa", "Lelee ya tupu iziga")
    fun sendTo(amount: String, nick: String) = t("Send $amount to $nick", "Send $amount give $nick", "Fi $amount ránṣẹ́ sí $nick", "Aika $amount ga $nick", "Ziga $nick $amount")
    fun amountTo(amount: String, name: String) = t("$amount to $name", "$amount give $name", "$amount sí $name", "$amount ga $name", "$amount nye $name")
    val yourApprover get() = t("your approver", "your approver", "ẹni tó ń fọwọ́ sí i", "mai amincewa", "onye na-akwado")
    fun waitingFor(who: String) = t("Waiting for $who", "We dey wait for $who", "À ń dúró de $who", "Ana jiran $who", "Anyị na-eche $who")
    fun approverRole(who: String) = t(
        "$who approves transfers above ₦50,000, and sees who and how much, never your PIN.",
        "$who dey approve transfer wey pass ₦50,000. E dey see who and how much, but e no go ever see your PIN.",
        "$who ló ń fọwọ́ sí owó tó ju ₦50,000 lọ. Ó ń rí ẹni àti iye, kò ní rí PIN yín láé.",
        "$who ne ke amince da aikawa sama da ₦50,000. Yana ganin wa da nawa, amma ba ya ganin PIN ɗinku.",
        "$who na-akwado nzipu karịrị ₦50,000. Ọ na-ahụ onye na ego ole, ma ọ dịghị mgbe ọ ga-ahụ PIN gị.",
    )
    fun youConfirmed(amount: String, name: String) = t("You confirmed $amount to $name", "You confirm $amount give $name", "Ẹ fọwọ́ sí $amount sí $name", "Kun tabbatar da $amount ga $name", "Ị kwadoro $amount nye $name")
    val justNowWithPin get() = t("Just now, with your Pulse PIN", "Just now, with your Pulse PIN", "Ní báyìí, pẹ̀lú Pulse PIN yín", "Yanzu nan, da Pulse PIN ɗinku", "Ugbu a, site na Pulse PIN gị")
    fun isChecking(who: String) = t("$who is checking", "$who dey check am", "$who ń yẹ̀ ẹ́ wò", "$who yana dubawa", "$who na-elele ya")
    fun sentToPhone(who: String) = t("Sent to $who's phone", "E don reach $who phone", "A ti fi ránṣẹ́ sí fóònù $who", "An aika zuwa wayar $who", "Ezigara ya na ekwentị $who")
    val moneyIsSent get() = t("Money is sent", "Money go comot", "A ó fi owó ránṣẹ́", "Za a aika kuɗi", "A ga-eziga ego")
    fun assoonAsApproves(who: String) = t("As soon as $who approves", "As soon as $who gree", "Ní kété tí $who bá fọwọ́ sí i", "Da zarar $who ya amince", "Ozugbo $who kwadoro ya")
    val stoppedSafe get() = t("Stopped. Your money is safe.", "I don stop am. Your money dey safe.", "Mo ti dá a dúró. Owó yín wà láìléwu.", "An tsayar. Kuɗinku suna lafiya.", "Akwụsịla ya. Ego gị dị nchebe.")
    val nobodyAsksToMove get() = t(
        "Nobody from Zenith will ever ask you to move money to keep it safe.",
        "Nobody from Zenith go ever ask you make you move money to keep am safe.",
        "Kò sí ẹnikẹ́ni láti Zenith tí yóò ní kí ẹ gbé owó láti dáàbò bò ó.",
        "Babu wani daga Zenith da zai taɓa ce ku tura kuɗi don kare su.",
        "Ọ dịghị onye si Zenith ga-agwa gị ka i bufee ego iji chekwaa ya.",
    )
    val talkToZenith get() = t("Talk to Zenith now", "Talk to Zenith now", "Bá Zenith sọ̀rọ̀ báyìí", "Yi magana da Zenith yanzu", "Gwa Zenith okwu ugbu a")
    val dontSendAgain get() = t(
        "Do not send it again. Your balance will show what happened.",
        "No send am again. Your balance go show wetin happen.",
        "Ẹ má tún fi ránṣẹ́ mọ́. Owó inú àkáǹtì yín yóò fi ohun tó ṣẹlẹ̀ hàn.",
        "Kada ku sake aikawa. Kuɗin asusunku zai nuna abin da ya faru.",
        "Ezigala ya ọzọ. Ego dị na akaụntụ gị ga-egosi ihe mere.",
    )
    val checkMyBalance get() = t("Check my balance", "Check my balance", "Yẹ owó mi wò", "Duba kuɗina", "Lelee ego m")
    val scamCheck get() = t("Scam check", "Scam check", "Àyẹ̀wò jìbìtì", "Binciken zamba", "Nlele aghụghọ")
    val yesStopPayment get() = t("Yes, stop this payment", "Yes, stop this payment", "Bẹ́ẹ̀ni, dá owó yìí dúró", "Eh, dakatar da wannan biyan", "Ee, kwụsị ịkwụ ụgwọ a")
    val noChoseMyself get() = t("No, I chose this myself", "No, na me choose am by myself", "Rárá, èmi fúnra mi ni mo yàn án", "A'a, ni da kaina na zaɓa", "Mba, ọ bụ m n'onwe m họọrọ ya")

    // Card
    val confirmBlock get() = t(
        "Say yes to go on to your Pulse PIN, or no to keep your card.",
        "Talk yes make we go your Pulse PIN, or no make you keep your card.",
        "Ẹ sọ bẹ́ẹ̀ni láti lọ sí Pulse PIN yín, tàbí rárá láti pa káàdì yín mọ́.",
        "Ku ce eh don ku je Pulse PIN ɗinku, ko a'a don ku ajiye katinku.",
        "Kwuo ee ka i gaa na Pulse PIN gị, ma ọ bụ mba ka i debe kaadị gị.",
    )
    val whereSendCard get() = t("Where should we send the new card?", "Where we go send the new card?", "Ibo ni ká fi káàdì tuntun ránṣẹ́ sí?", "Ina za mu aika sabon katin?", "Ebee ka anyị ga-eziga kaadị ọhụrụ?")
    val sayHomeOrBranch get() = t("Say home, or say branch.", "Talk home, or talk branch.", "Ẹ sọ ilé, tàbí ẹ sọ ẹ̀ka.", "Ku ce gida, ko ku ce reshe.", "Kwuo ụlọ, ma ọ bụ kwuo alaka.")
    val cardToHome get() = t("Your new card will go to your home address.", "Your new card go come your house address.", "Káàdì tuntun yín yóò lọ sí àdírẹ́sì ilé yín.", "Sabon katinku zai je adireshin gidanku.", "Kaadị ọhụrụ gị ga-aga n'adreesị ụlọ gị.")
    val cardToBranch get() = t("Your new card will wait for you at a Zenith branch.", "Your new card go wait for you for Zenith branch.", "Káàdì tuntun yín yóò dúró dè yín ní ẹ̀ka Zenith.", "Sabon katinku zai jira ku a reshen Zenith.", "Kaadị ọhụrụ gị ga-eche gị n'alaka Zenith.")
    val tapWhereThenDone get() = t(
        "You can also tap where the card should go, then $done.",
        "You fit also touch where the card go go, then $done.",
        "Ẹ tún lè tẹ ibi tí káàdì náà yóò lọ, lẹ́yìn náà $done.",
        "Kuna iya taɓa inda katin zai je, sannan $done.",
        "Ị nwekwara ike ịpị ebe kaadị ahụ ga-aga, mgbe ahụ $done.",
    )
    val bankUnreachableNoChange get() = t(
        "I could not reach the bank just now. Nothing has changed.",
        "I no fit reach the bank now. Nothing don change.",
        "Mi ò lè dé ọ̀dọ̀ báǹkì báyìí. Kò sí ohun tó yí padà.",
        "Ban iya isa ga banki yanzu ba. Babu abin da ya canza.",
        "Enweghị m ike iru ụlọ akụ ugbu a. Ọ dịghị ihe gbanwere.",
    )
    val pulseToBlockCard get() = t("Pulse your PIN to block the card", "Pulse your PIN make I block the card", "Ẹ pulse PIN yín láti dí káàdì náà", "Ku yi pulse PIN don toshe katin", "Pulse PIN gị ka egbochie kaadị ahụ")
    val lockedCardSame get() = t("Your Pulse PIN is locked. Your card has not changed.", "Your Pulse PIN don lock. Your card never change.", "Pulse PIN yín ti tì pa. Káàdì yín kò yí padà.", "An kulle Pulse PIN ɗinku. Katinku bai canza ba.", "Akpọchiela Pulse PIN gị. Kaadị gị agbanweghị.")
    val pulseToBlock get() = t("Pulse PIN to block", "Pulse PIN make I block am", "Pulse PIN láti dí i", "Pulse PIN don toshewa", "Pulse PIN ka egbochie ya")
    val notNow get() = t("Not now", "No be now", "Kì í ṣe báyìí", "Ba yanzu ba", "Ọ bụghị ugbu a")
    val cardBlockedNew get() = t("Card blocked. A new one is on its way.", "I don block the card. New one dey come.", "A ti dí káàdì náà. Tuntun ń bọ̀.", "An toshe katin. Sabo yana kan hanya.", "Egbochiela kaadị ahụ. Nke ọhụrụ na-abịa.")
    val whereCardGo get() = t("Where should the new card go?", "Where the new card go go?", "Ibo ni káàdì tuntun yóò lọ?", "Ina sabon katin zai je?", "Ebee ka kaadị ọhụrụ ga-aga?")
    val myHomeAddress get() = t("My home address", "My house address", "Àdírẹ́sì ilé mi", "Adireshin gidana", "Adreesị ụlọ m")
    val zenithBranch get() = t("A Zenith branch", "Zenith branch", "Ẹ̀ka Zenith kan", "Reshen Zenith", "Alaka Zenith")
    val pickUpMyself get() = t("I will pick it up myself", "I go carry am by myself", "Màá lọ gbà á fúnra mi", "Zan karɓa da kaina", "M ga-aga were ya n'onwe m")
    val checkBeforeBlock get() = t("Check before you block", "Check am before you block", "Ẹ yẹ̀ ẹ́ wò kí ẹ tó dí i", "Ku duba kafin ku toshe", "Lelee ya tupu i gbochie ya")

    // Person to person
    fun moneyIn(amount: String, who: String) = t(
        "$amount just came in from $who.",
        "$amount just enter from $who.",
        "$amount ṣẹ̀ṣẹ̀ wọlé láti ọ̀dọ̀ $who.",
        "$amount ya shigo yanzu daga $who.",
        "$amount batara ugbu a site n'aka $who.",
    )
    val accountNumber get() = t("Account number", "Account number", "Nọ́ńbà àkáǹtì", "Lambar asusu", "Nọmba akaụntụ")
    fun accountNumberIs(digits: String) = t(
        "Your account number is $digits.",
        "Your account number na $digits.",
        "Nọ́ńbà àkáǹtì yín ni $digits.",
        "Lambar asusunku ita ce $digits.",
        "Nọmba akaụntụ gị bụ $digits.",
    )
    val readAccountNumber get() = t("Read my account number", "Read my account number", "Ka nọ́ńbà àkáǹtì mi", "Karanta lambar asusuna", "Gụọ nọmba akaụntụ m")
    val someoneNew get() = t("Someone new, by account number", "Somebody new, with account number", "Ẹnìkan tuntun, pẹ̀lú nọ́ńbà àkáǹtì", "Sabon mutum, da lambar asusu", "Onye ọhụrụ, site na nọmba akaụntụ")
    val theirAccountNumber get() = t("Their Zenith account number", "Their Zenith account number", "Nọ́ńbà àkáǹtì Zenith wọn", "Lambar asusunsu na Zenith", "Nọmba akaụntụ Zenith ha")
    val howMuchNaira get() = t("How much, in naira?", "How much, for naira?", "Èló, ní náírà?", "Nawa, a naira?", "Ego ole, na naịra?")
    val accountNotFound get() = t(
        "I could not find that account. Check the number. Nothing was sent.",
        "I no see that account. Check the number. Nothing comot.",
        "Mi ò rí àkáǹtì yẹn. Ẹ yẹ nọ́ńbà náà wò. Kò sí owó tó lọ.",
        "Ban sami wannan asusun ba. Ku duba lambar. Ba a aika komai ba.",
        "Achọtaghị m akaụntụ ahụ. Lelee nọmba ahụ. Ọ dịghị ihe e zigara.",
    )

    // Balance
    val accountUnreachable get() = t("I could not reach your account.", "I no fit reach your account.", "Mi ò lè dé àkáǹtì yín.", "Ban iya isa ga asusunku ba.", "Enweghị m ike iru akaụntụ gị.")
    val tryInAMinute get() = t("Please try again in a minute.", "Abeg try again after one minute.", "Ẹ jọ̀ọ́ ẹ tún gbìyànjú ní ìṣẹ́jú kan sí i.", "Don Allah ku sake gwadawa bayan minti ɗaya.", "Biko nwaa ọzọ n'otu nkeji.")
    val yourMoney get() = t("Your money", "Your money", "Owó yín", "Kuɗinku", "Ego gị")
    val readLatest get() = t("Read the latest to me", "Read the latest one give me", "Ka àwọn tuntun fún mi", "Karanta mini na baya-bayan nan", "Gụọrọ m nke ọhụrụ")
    val updatedJustNow get() = t("Updated just now", "E just update", "A ṣẹ̀ṣẹ̀ ṣe àtúnṣe rẹ̀", "An sabunta yanzu nan", "Emelitere ya ugbu a")
    val latestInOut get() = t("Latest money in and out", "Latest money wey enter and comot", "Owó tó wọlé àti tó jáde láìpẹ́", "Kuɗin da suka shigo da fita kwanan nan", "Ego batara na nke pụrụ n'oge na-adịbeghị anya")

    // Settings
    val lookAndSound get() = t("Look and sound", "Look and sound", "Ìrísí àti ohùn", "Kamanni da sauti", "Ọdịdị na ụda")
    val hearSample get() = t("Hear a sample", "Hear sample", "Gbọ́ àpẹẹrẹ", "Ji samfuri", "Nụ ihe atụ")
    val sampleLines: List<String> get() = listOf(
        t("This is how I will sound.", "Na so I go sound.", "Báyìí ni ohùn mi yóò ṣe dún.", "Haka muryata za ta yi.", "Otu a ka m ga-ada."),
        t(
            "Twenty thousand naira to Adebayo Tolulope.",
            "Twenty thousand naira give Adebayo Tolulope.",
            "Ẹgbàá mẹ́wàá náírà sí Adebayo Tolulope.",
            "Naira dubu ashirin ga Adebayo Tolulope.",
            "Puku naịra iri abụọ nye Adebayo Tolulope.",
        ),
    )
    val theme get() = t("Theme", "Theme", "Àwọ̀", "Launi", "Agba")
    val speakingSpeed get() = t("Speaking speed", "How fast I go talk", "Ìyára ọ̀rọ̀ sísọ", "Saurin magana", "Ọsọ okwu")
    val slower get() = t("Slower", "Slow", "Díẹ̀díẹ̀", "A hankali", "Nwayọọ")
    val normal get() = t("Normal", "Normal", "Bí ó ti yẹ", "Daidai", "Nkịtị")
    val faster get() = t("Faster", "Fast", "Kíákíá", "Da sauri", "Ọsọ ọsọ")
    val pulseSpeed get() = t("Pulse PIN speed", "Pulse PIN speed", "Ìyára Pulse PIN", "Saurin Pulse PIN", "Ọsọ Pulse PIN")
    val zeroSoundLabel get() = t("Zero sound on the loudspeaker", "Zero sound for loudspeaker", "Ohùn òdo lórí sípíkà", "Sautin sifili a lasifika", "Ụda efu na ọkà okwu")
    val off get() = t("Off", "Off", "Pa á", "A kashe", "Gbanyụọ")
    val on get() = t("On", "On", "Tàn án", "A kunna", "Gbanye")
    val zeroHint get() = t(
        "Zero always sounds in earphones and while practising. On the loudspeaker, someone nearby could hear zero and count to your digit.",
        "Zero dey always sound for earphone and when you dey practise. For loudspeaker, person wey dey near fit hear zero and count reach your number.",
        "Òdo máa ń dún nígbà gbogbo nínú ẹ̀rọ etí àti nígbà ìdánrawò. Lórí sípíkà, ẹni tó wà nítòsí lè gbọ́ òdo kí ó ka dé nọ́ńbà yín.",
        "Sifili koyaushe yana sauti a cikin belun kunne da lokacin gwaji. A lasifika, wani na kusa zai iya jin sifili ya ƙirga zuwa lambarku.",
        "Efu na-ada mgbe niile na ekweisi ntị na mgbe ị na-eme omume. Na ọkà okwu, onye nọ nso nwere ike ịnụ efu wee gụọ ruo nọmba gị.",
    )
    val scanningSpeed get() = t("Scanning speed", "Scanning speed", "Ìyára ìyíká", "Saurin zagayawa", "Ọsọ ịgagharị")
    val waysIn get() = t("Ways in", "Ways to use am", "Àwọn ọ̀nà ìlò", "Hanyoyin amfani", "Ụzọ iji ya")
    val captionsSub get() = t(
        "Write every spoken line on the screen",
        "Write everything wey I talk for the screen",
        "Kọ gbogbo ọ̀rọ̀ tí mo bá sọ sójú fóònù",
        "Rubuta duk abin da aka faɗa a kan allon",
        "Dee ihe ọ bụla m kwuru na ihuenyo",
    )
    val language get() = t("Language", "Language", "Èdè", "Harshe", "Asụsụ")
    val forTheDemo get() = t("For the demo", "For the demo", "Fún àfihàn", "Don nunawa", "Maka ngosi")
    val pretendCall get() = t("Pretend I am on a phone call", "Pretend say I dey phone call", "Ṣe bí ẹni pé mo wà lórí ìpè", "Yi kamar ina kan waya", "Mee ka à ga-asị na m nọ na oku")
    val pretendCallSub get() = t(
        "Shows the scam pause when sending ₦150,000 to Musa",
        "E go show the scam pause when you send ₦150,000 give Musa",
        "Yóò fi ìdádúró jìbìtì hàn tí ẹ bá fi ₦150,000 ránṣẹ́ sí Musa",
        "Yana nuna dakatarwar zamba idan aka aika ₦150,000 ga Musa",
        "Ọ na-egosi nkwụsịtụ aghụghọ mgbe i ziga Musa ₦150,000",
    )
    val useGateway get() = t("Use the live Gateway", "Use the live Gateway", "Lo Gateway tó wà láàyè", "Yi amfani da Gateway kai tsaye", "Jiri Gateway dị ndụ")
    val gatewayNone get() = t(
        "No Gateway address in this build, so the bank runs inside the app",
        "No Gateway address for this build, so the bank dey run inside the app",
        "Kò sí àdírẹ́sì Gateway nínú ẹ̀dà yìí, nítorí náà báǹkì ń ṣiṣẹ́ nínú áàpù",
        "Babu adireshin Gateway a wannan ginin, don haka banki yana aiki a cikin manhajar",
        "Enweghị adreesị Gateway n'ụdị a, ya mere ụlọ akụ na-arụ ọrụ n'ime ngwa",
    )
    val gatewayOff get() = t(
        "Off: the bank runs inside the app, with no network",
        "Off: the bank dey run inside the app, no network",
        "Ó ti pa: báǹkì ń ṣiṣẹ́ nínú áàpù, láìsí nẹ́tíwọ̀ọ̀kì",
        "A kashe: banki yana aiki a cikin manhajar, ba tare da intanet ba",
        "Agbanyụọla: ụlọ akụ na-arụ ọrụ n'ime ngwa, na-enweghị netwọk",
    )
    val gatewayLost get() = t(
        "Lost the Gateway, so this session runs offline. Start again to retry.",
        "Gateway don lost, so this session dey run offline. Start again make you try again.",
        "Gateway ti sọnù, nítorí náà ìgbà yìí ń ṣiṣẹ́ láìsí íńtánẹ́ẹ̀tì. Ẹ bẹ̀rẹ̀ lẹ́ẹ̀kan sí i láti tún gbìyànjú.",
        "An rasa Gateway, don haka wannan zama yana aiki ba tare da intanet ba. Ku sake farawa don sake gwadawa.",
        "Gateway efuola, ya mere oge a na-arụ ọrụ na-enweghị ịntanetị. Malite ọzọ ka ị nwaa ọzọ.",
    )
    fun gatewayOn(url: String) = t("On: $url", "On: $url", "Ó wà ní títàn: $url", "A kunna: $url", "Agbanyere: $url")
    val startAgain get() = t("Start again from welcome", "Start again from welcome", "Tún bẹ̀rẹ̀ láti ìbẹ̀rẹ̀", "Sake farawa daga farko", "Malite ọzọ site na mmalite")
    val about get() = t(
        "Òmìnira, a Zecathon 6.0 prototype. With the live Gateway it speaks and listens through YarnGPT; otherwise it uses the phone's own speech engines.",
        "Òmìnira, na Zecathon 6.0 prototype. With the live Gateway e dey talk and listen through YarnGPT; if not, e dey use the phone own speech engine.",
        "Òmìnira, àpẹẹrẹ àkọ́kọ́ fún Zecathon 6.0. Pẹ̀lú Gateway tó wà láàyè, ó ń sọ̀rọ̀, ó sì ń gbọ́ nípasẹ̀ YarnGPT; bí bẹ́ẹ̀ kọ́, ó ń lo ẹ̀rọ ohùn fóònù fúnra rẹ̀.",
        "Òmìnira, samfurin farko na Zecathon 6.0. Tare da Gateway kai tsaye yana magana kuma yana saurare ta YarnGPT; in ba haka ba, yana amfani da injin magana na wayar.",
        "Òmìnira, ụdị mbụ maka Zecathon 6.0. Site na Gateway dị ndụ ọ na-ekwu okwu ma na-ege ntị site na YarnGPT; ma ọ bụghị ya, ọ na-eji igwe okwu nke ekwentị.",
    )

    // Sign call
    val funmiSigning get() = t("Funmi is signing", "Funmi dey sign", "Funmi ń fọwọ́ sọ̀rọ̀", "Funmi tana yin alama", "Funmi na-eji aka ekwu okwu")
    val youSigned get() = t("You signed", "You sign", "Ẹ fọwọ́ sọ", "Kun yi alama", "Ị jiri aka kwuo")
    val signScript: List<String> get() = listOf(
        t(
            "I have blocked the old card. Nobody can use it now.",
            "I don block the old card. Nobody fit use am now.",
            "Mo ti dí káàdì àtijọ́. Kò sí ẹni tó lè lò ó báyìí.",
            "Na toshe tsohon katin. Babu wanda zai iya amfani da shi yanzu.",
            "Egbochiela m kaadị ochie ahụ. Ọ dịghị onye nwere ike iji ya ugbu a.",
        ),
        t("Thank you. When will the new one come?", "Thank you. When the new one go come?", "Ẹ ṣé. Ìgbà wo ni tuntun yóò dé?", "Na gode. Yaushe sabon zai zo?", "Daalụ. Olee mgbe nke ọhụrụ ga-abịa?"),
        t(
            "Your new card will arrive on Thursday. Should we send it to your home address?",
            "Your new card go reach on Thursday. Make we send am to your house address?",
            "Káàdì tuntun yín yóò dé ní Ọjọ́bọ̀. Ṣé ká fi ránṣẹ́ sí àdírẹ́sì ilé yín?",
            "Sabon katinku zai iso ranar Alhamis. Mu aika shi zuwa adireshin gidanku?",
            "Kaadị ọhụrụ gị ga-abịa na Tọọzdee. Ka anyị ziga ya n'adreesị ụlọ gị?",
        ),
        t("Yes, my home, please.", "Yes, my house, abeg.", "Bẹ́ẹ̀ni, ilé mi, ẹ jọ̀ọ́.", "Eh, gidana, don Allah.", "Ee, ụlọ m, biko."),
    )
    val signHelpTitle get() = t("Sign help", "Sign help", "Ìrànlọ́wọ́ àmì", "Taimakon alama", "Enyemaka aka")
    fun live(time: String) = t("Live $time", "Live $time", "Láàyè $time", "Kai tsaye $time", "Ndụ $time")
    val callAgain get() = t("Call again", "Call again", "Tún pè", "Sake kira", "Kpọọ ọzọ")
    val endCall get() = t("End call", "End the call", "Parí ìpè", "Kashe kira", "Kwụsị oku")
    val callEnded get() = t("Call with Funmi ended.", "Call with Funmi don end.", "Ìpè pẹ̀lú Funmi ti parí.", "Kiran da Funmi ya ƙare.", "Oku ya na Funmi agwụla.")
    val newCardOnWay get() = t("New card on its way.", "New card dey come.", "Káàdì tuntun ń bọ̀.", "Sabon kati yana kan hanya.", "Kaadị ọhụrụ na-abịa.")
    val arrivesThursday get() = t(
        "It reaches your home address on Thursday. A text copy of this call is in your messages.",
        "E go reach your house address on Thursday. Text copy of this call dey your messages.",
        "Yóò dé àdírẹ́sì ilé yín ní Ọjọ́bọ̀. Ẹ̀dà ìpè yìí ní ọ̀rọ̀ wà nínú àwọn ìfiránṣẹ́ yín.",
        "Zai isa adireshin gidanku ranar Alhamis. Rubutaccen kwafin wannan kira yana cikin saƙonninku.",
        "Ọ ga-eru n'adreesị ụlọ gị na Tọọzdee. Ederede oku a dị na ozi gị.",
    )
    val videoOfFunmi get() = t(
        "Video of Funmi, a Zenith sign language agent",
        "Video of Funmi, Zenith sign language agent",
        "Fídíò Funmi, òṣìṣẹ́ èdè àwọn adití ní Zenith",
        "Bidiyon Funmi, ma'aikaciyar yaren kurame ta Zenith",
        "Vidiyo Funmi, onye ọrụ asụsụ aka nke Zenith",
    )
    val funmiAgent get() = t("Funmi, Zenith sign agent", "Funmi, Zenith sign agent", "Funmi, òṣìṣẹ́ àmì Zenith", "Funmi, ma'aikaciyar alama ta Zenith", "Funmi, onye ọrụ aka Zenith")

    // Pulse PIN
    val howDoesItWork get() = t("How does it work?", "How e dey work?", "Báwo ló ṣe ń ṣiṣẹ́?", "Yaya yake aiki?", "Kedu ka ọ si arụ ọrụ?")
    val donePractising get() = t("Done practising", "I don practise finish", "Mo ti parí ìdánrawò", "Na gama gwaji", "Emechaala m omume")
    val practiseFirst get() = t("Practise first", "Make I practise first", "Ẹ kọ́kọ́ dánrawò", "Gwada tukuna", "Buru ụzọ mee omume")
    val hideCount get() = t("Hide count", "Hide count", "Fi ìkà pamọ́", "Ɓoye ƙirga", "Zoo ọgụgụ")
    val showCount get() = t("Show count", "Show count", "Fi ìkà hàn", "Nuna ƙirga", "Gosi ọgụgụ")
    val checkingWithZenith get() = t("Checking with Zenith…", "I dey check with Zenith…", "Mo ń bá Zenith ṣàyẹ̀wò…", "Ina dubawa da Zenith…", "Ana m elele na Zenith…")
    val demoPin get() = t(
        "Demo PIN 2 0 7 5. On a real phone the screen stays dark and the count is felt, not seen.",
        "Demo PIN 2 0 7 5. For real phone the screen go dark and you go feel the count, you no go see am.",
        "PIN àfihàn 2 0 7 5. Lórí fóònù gidi, ojú rẹ̀ yóò ṣókùnkùn, ẹ ó sì máa fi ara mọ ìkà, ẹ kò ní rí i.",
        "PIN na nunawa 2 0 7 5. A waya ta gaske allon zai yi duhu, kuma za a ji ƙirgan, ba a gani ba.",
        "PIN ngosi 2 0 7 5. Na ekwentị n'ezie, ihuenyo ga-agba ọchịchịrị, a ga-enwe mmetụta ọgụgụ, ọ bụghị ịhụ ya.",
    )
    val practiceRound get() = t("Practice round", "Practice round", "Ìdánrawò", "Zagayen gwaji", "Agba omume")
    fun digitsEntered(n: Int) = t("$n of 4 digits entered", "$n of 4 numbers don enter", "Nọ́ńbà $n nínú 4 ti wọlé", "An shigar da lambobi $n cikin 4", "Etinyela nọmba $n n'ime 4")
    val pinLocked get() = t("Pulse PIN is locked.", "Pulse PIN don lock.", "Pulse PIN ti tì pa.", "An kulle Pulse PIN.", "Akpọchiela Pulse PIN.")
    val tapSpaceToStart get() = t(
        "Tap the space below to start the count.",
        "Touch the space below make the count start.",
        "Ẹ tẹ àyè ìsàlẹ̀ yìí láti bẹ̀rẹ̀ ìkà.",
        "Ku taɓa sararin da ke ƙasa don fara ƙirga.",
        "Pịa ohere dị n'okpuru ka ịmalite ọgụgụ.",
    )
    val practiceHint get() = t(
        "Practice: wait for the strong double buzz (zero), count to three, then tap. Nothing is sent.",
        "Practice: wait for the strong double buzz (zero), count reach three, then tap. Nothing go send.",
        "Ìdánrawò: ẹ dúró de ìgbọ̀n méjì tó lágbára (òdo), ẹ ka dé mẹ́ta, lẹ́yìn náà ẹ tẹ̀ ẹ́. Kò sí ohun tí yóò lọ.",
        "Gwaji: ku jira girgiza biyu mai ƙarfi (sifili), ku ƙirga zuwa uku, sannan ku taɓa. Ba a aika komai.",
        "Omume: chere mkpọtụ abụọ siri ike (efu), gụọ ruo atọ, mgbe ahụ pịa. Ọ dịghị ihe a na-eziga.",
    )
    val earsHint get() = t(
        "Listen in your earphones. Tap at your digit. A higher, longer tick means zero.",
        "Listen for your earphone. Tap for your number. The higher, longer tick na zero.",
        "Ẹ fetí sí ẹ̀rọ etí yín. Ẹ tẹ̀ ẹ́ ní nọ́ńbà yín. Ìró tó ga jù, tó sì gùn jù ni òdo.",
        "Ku saurara a belun kunnenku. Ku taɓa a lambarku. Ƙara mafi tsayi kuma mafi tsawo shine sifili.",
        "Gee ntị na ekweisi ntị gị. Pịa na nọmba gị. Ụda dị elu ma dị ogologo bụ efu.",
    )
    val countHint get() = t(
        "Tap when the count reaches your next digit. A strong double buzz means zero.",
        "Tap when the count reach your next number. Strong double buzz na zero.",
        "Ẹ tẹ̀ ẹ́ nígbà tí ìkà bá dé nọ́ńbà yín tó kàn. Ìgbọ̀n méjì tó lágbára túmọ̀ sí òdo.",
        "Ku taɓa idan ƙirgan ya kai lambarku ta gaba. Girgiza biyu mai ƙarfi na nufin sifili.",
        "Pịa mgbe ọgụgụ ruru nọmba gị ọzọ. Mkpọtụ abụọ siri ike pụtara efu.",
    )
    val feelHint get() = t(
        "Feel the count. Tap at your digit. A strong double buzz means zero.",
        "Feel the count. Tap for your number. Strong double buzz na zero.",
        "Ẹ fi ara mọ ìkà náà. Ẹ tẹ̀ ẹ́ ní nọ́ńbà yín. Ìgbọ̀n méjì tó lágbára túmọ̀ sí òdo.",
        "Ku ji ƙirgan. Ku taɓa a lambarku. Girgiza biyu mai ƙarfi na nufin sifili.",
        "Nwee mmetụta ọgụgụ. Pịa na nọmba gị. Mkpọtụ abụọ siri ike pụtara efu.",
    )
    val takeThisDigit get() = t("take this digit", "take this number", "gba nọ́ńbà yìí", "ɗauki wannan lamba", "were nọmba a")
    val startTheCount get() = t("Start the count", "Start the count", "Bẹ̀rẹ̀ ìkà", "Fara ƙirga", "Malite ọgụgụ")
    val tapAnywhereHere get() = t("Tap anywhere here", "Touch anywhere here", "Ẹ tẹ ibikíbi níbí", "Ku taɓa ko'ina a nan", "Pịa ebe ọ bụla ebe a")
    val pinSpoken get() = t(
        Lines.PIN_SPOKEN,
        "Abeg no ever talk your PIN for mouth. I no keep am. Your Pulse PIN na by feeling, for the screen.",
        "Ẹ jọ̀ọ́ ẹ má sọ PIN yín sókè láé. Mi ò tọ́jú rẹ̀. Pulse PIN yín ni ẹ ó fi ara mọ̀, lójú fóònù.",
        "Don Allah kada ku taɓa faɗin PIN ɗinku da murya. Ban ajiye shi ba. Ana shigar da Pulse PIN ɗinku ta hanyar ji, a kan allon.",
        "Biko, ekwula PIN gị n'olu ọ bụla. Edebeghị m ya. A na-etinye Pulse PIN gị site na mmetụta, na ihuenyo.",
    )

    // Camera directions: one at a time, always about the phone
    fun aim(a: Aim, card: Boolean): String = when (a) {
        Aim.Search -> if (card) {
            t(
                "I cannot see the card yet. Hold it flat, about a hand away from the back camera.",
                "I never see the card. Hold am flat, like one hand away from the back camera.",
                "Mi ò tíì rí káàdì náà. Ẹ di í mú pẹrẹsẹ, nǹkan bí ìbú ọwọ́ kan sí kámẹ́rà ẹ̀yìn.",
                "Ban ga katin ba tukuna. Ku riƙe shi a kwance, kusan tafin hannu ɗaya daga kyamarar baya.",
                "Ahụbeghị m kaadị ahụ. Jide ya n'ala, ihe dị ka obosara aka site na igwe foto azụ.",
            )
        } else {
            t(
                "I cannot see your face yet. Hold the phone at arm's length, screen towards you.",
                "I never see your face. Hold the phone far reach your hand, screen facing you.",
                "Mi ò tíì rí ojú yín. Ẹ na ọwọ́ di fóònù mú, kí ojú rẹ̀ kọjú sí yín.",
                "Ban ga fuskarku ba tukuna. Ku miƙa hannu ku riƙe wayar, allon yana fuskantarku.",
                "Ahụbeghị m ihu gị. Setịa aka jide ekwentị, ihuenyo chere gị ihu.",
            )
        }
        Aim.Dark -> t("It is too dark. Move to more light.", "E too dark. Go where light dey.", "Ó ṣókùnkùn jù. Ẹ lọ síbi tí ìmọ́lẹ̀ wà.", "Ya yi duhu sosai. Ku koma inda akwai haske.", "Ọ gbara ọchịchịrị nke ukwuu. Gaa ebe ìhè dị.")
        Aim.Closer -> t("Bring the phone a little closer.", "Bring the phone come near small.", "Ẹ mú fóònù sún mọ́ díẹ̀.", "Ku kawo wayar kusa kaɗan.", "Bịaruo ekwentị nso ntakịrị.")
        Aim.Farther -> t("Move the phone a little further away.", "Shift the phone go back small.", "Ẹ gbé fóònù jìnnà díẹ̀.", "Ku nisanta wayar kaɗan.", "Wezuga ekwentị ntakịrị.")
        Aim.Left -> t("Move the phone a little to the left.", "Move the phone go left small.", "Ẹ gbé fóònù sí apá òsì díẹ̀.", "Ku matsar da wayar hagu kaɗan.", "Bugharịa ekwentị gaa n'aka ekpe ntakịrị.")
        Aim.Right -> t("Move the phone a little to the right.", "Move the phone go right small.", "Ẹ gbé fóònù sí apá ọ̀tún díẹ̀.", "Ku matsar da wayar dama kaɗan.", "Bugharịa ekwentị gaa n'aka nri ntakịrị.")
        Aim.Up -> t("Move the phone up a little.", "Raise the phone small.", "Ẹ gbé fóònù sókè díẹ̀.", "Ku ɗaga wayar kaɗan.", "Bulie ekwentị elu ntakịrị.")
        Aim.Down -> t("Move the phone down a little.", "Bring the phone down small.", "Ẹ sọ fóònù sílẹ̀ díẹ̀.", "Ku saukar da wayar kaɗan.", "Wedata ekwentị ntakịrị.")
        Aim.Straight -> t("Look straight at the phone.", "Look the phone straight.", "Ẹ wo fóònù tààrà.", "Ku kalli wayar kai tsaye.", "Lee ekwentị anya ozugbo.")
        Aim.Hold -> t("Keep still.", "No move.", "Ẹ má mì.", "Ku tsaya cak.", "Kwụrụ jụụ.")
    }
    val cameraNeeded get() = t(
        "This step needs the camera. Tap anywhere to allow it. If no box appears, allow the camera for Òmìnira in your phone's Settings.",
        "This step need the camera. Touch anywhere make you allow am. If no box show, allow camera for Òmìnira inside your phone Settings.",
        "Ìgbésẹ̀ yìí nílò kámẹ́rà. Ẹ tẹ ibikíbi láti gbà á láàyè. Tí àpótí kankan kò bá yọ, ẹ gba kámẹ́rà láàyè fún Òmìnira nínú Ètò fóònù yín.",
        "Wannan mataki yana buƙatar kyamara. Ku taɓa ko'ina don ku ba da izini. Idan babu akwati da ya bayyana, ku ba Òmìnira izinin kyamara a cikin Saitunan wayarku.",
        "Nzọụkwụ a chọrọ igwe foto. Pịa ebe ọ bụla ka ịkwado ya. Ọ bụrụ na igbe apụtaghị, kwado igwe foto maka Òmìnira na Ntọala ekwentị gị.",
    )
    val enrolmentSlip get() = t(
        "This is an enrolment slip. It has no NIN on it. Please hold up your NIN slip or your ID card instead.",
        "This na enrolment slip. NIN no dey inside. Abeg hold your NIN slip or your ID card instead.",
        "Ìwé ìforúkọsílẹ̀ nìyí. Kò sí NIN lórí rẹ̀. Ẹ jọ̀ọ́ ẹ di ìwé NIN tàbí káàdì ìdánimọ̀ yín mú dípò.",
        "Wannan takardar rajista ce. Babu NIN a kanta. Don Allah ku riƙe takardar NIN ko katin shaidarku maimakon haka.",
        "Nke a bụ akwụkwọ ndebanye aha. Enweghị NIN n'ime ya. Biko jide akwụkwọ NIN gị ma ọ bụ kaadị njirimara gị kama.",
    )
    val cardUnreadable get() = t(
        "I can see the card but cannot read it yet. Hold it flat and still, in good light.",
        "I dey see the card but I never fit read am. Hold am flat, no shake am, where light dey.",
        "Mo rí káàdì náà ṣùgbọ́n mi ò tíì lè kà á. Ẹ di í mú pẹrẹsẹ láìmì, níbi tí ìmọ́lẹ̀ dáa wà.",
        "Ina ganin katin amma ban iya karanta shi ba tukuna. Ku riƙe shi a kwance cak, a wuri mai haske.",
        "Ahụrụ m kaadị ahụ mana enweghị m ike ịgụ ya. Jide ya n'ala jụụ, ebe ìhè dị mma.",
    )
    // allow is the phone's own button label, in the phone's language.
    fun cameraAllow(allow: String) = t(
        "To use the camera, tap $allow, near the bottom of the screen.",
        "To use the camera, touch $allow, for the bottom of the screen.",
        "Láti lo kámẹ́rà, ẹ tẹ $allow, nítòsí ìsàlẹ̀ ojú fóònù.",
        "Don amfani da kyamara, ku taɓa $allow, kusa da ƙasan allon.",
        "Iji igwe foto, pịa $allow, n'akụkụ ala ihuenyo.",
    )

    // Touch answers, when nothing can understand the customer's language
    val touchYesNo get() = t(
        "Tap anywhere for yes. Touch and hold for no.",
        "Tap anywhere for yes. Press am down small for no.",
        "Ẹ tẹ ibikíbi fún bẹ́ẹ̀ni. Ẹ tẹ̀ ẹ́ mọ́lẹ̀ díẹ̀ fún rárá.",
        "Ku taɓa ko'ina don eh. Ku danna ku riƙe don a'a.",
        "Pịa ebe ọ bụla maka ee. Pịa ma jide maka mba.",
    )
    val touchId get() = t(
        "Tap anywhere if that is you. Touch and hold to take the card again.",
        "Tap anywhere if na you. Press am down small make we snap the card again.",
        "Ẹ tẹ ibikíbi tí ẹ̀yin ni. Ẹ tẹ̀ ẹ́ mọ́lẹ̀ díẹ̀ láti tún ya káàdì náà.",
        "Ku taɓa ko'ina idan ku ne. Ku danna ku riƙe don sake ɗaukar katin.",
        "Pịa ebe ọ bụla ma ọ bụ gị. Pịa ma jide ka e see kaadị ahụ ọzọ.",
    )
    val touchToPin get() = t(
        "Tap anywhere to go on to your Pulse PIN.",
        "Tap anywhere make we go your Pulse PIN.",
        "Ẹ tẹ ibikíbi láti lọ sí Pulse PIN yín.",
        "Ku taɓa ko'ina don ku je Pulse PIN ɗinku.",
        "Pịa ebe ọ bụla ka i gaa na Pulse PIN gị.",
    )
    val holdToStop get() = t("Touch and hold to stop.", "Press am down small make we stop.", "Ẹ tẹ̀ ẹ́ mọ́lẹ̀ díẹ̀ láti dúró.", "Ku danna ku riƙe don tsayawa.", "Pịa ma jide ka ịkwụsị.")
    val holdToKeepCard get() = t(
        "Touch and hold to keep your card.",
        "Press am down small make you keep your card.",
        "Ẹ tẹ̀ ẹ́ mọ́lẹ̀ díẹ̀ láti pa káàdì yín mọ́.",
        "Ku danna ku riƙe don ajiye katinku.",
        "Pịa ma jide ka i debe kaadị gị.",
    )
    // The safe answer is the tap: holding is the deliberate choice to carry on.
    val touchPause get() = t(
        "Tap anywhere to stop it. Touch and hold to carry on.",
        "Tap anywhere make we stop am. Press am down small make we continue.",
        "Ẹ tẹ ibikíbi láti dá a dúró. Ẹ tẹ̀ ẹ́ mọ́lẹ̀ díẹ̀ láti tẹ̀síwájú.",
        "Ku taɓa ko'ina don dakatar da shi. Ku danna ku riƙe don ci gaba.",
        "Pịa ebe ọ bụla ka ịkwụsị ya. Pịa ma jide ka ị gaa n'ihu.",
    )
    val touchShip get() = t(
        "Tap anywhere for your home address. Touch and hold for a Zenith branch.",
        "Tap anywhere for your house address. Press am down small for Zenith branch.",
        "Ẹ tẹ ibikíbi fún àdírẹ́sì ilé yín. Ẹ tẹ̀ ẹ́ mọ́lẹ̀ díẹ̀ fún ẹ̀ka Zenith.",
        "Ku taɓa ko'ina don adireshin gidanku. Ku danna ku riƙe don reshen Zenith.",
        "Pịa ebe ọ bụla maka adreesị ụlọ gị. Pịa ma jide maka alaka Zenith.",
    )
    val touchHome get() = t(
        "Tap anywhere to go home. Touch and hold to send more.",
        "Tap anywhere make you go home. Press am down small make you send more.",
        "Ẹ tẹ ibikíbi láti padà sí ilé. Ẹ tẹ̀ ẹ́ mọ́lẹ̀ díẹ̀ láti fi òmíràn ránṣẹ́.",
        "Ku taɓa ko'ina don komawa gida. Ku danna ku riƙe don ƙara aikawa.",
        "Pịa ebe ọ bụla ka ịlaghachi n'ụlọ. Pịa ma jide ka i ziga ọzọ.",
    )

    // Microphone
    val answerOutLoud get() = t("Answer out loud", "Answer with your mouth", "Ẹ dáhùn sókè", "Ku amsa da murya", "Zaa n'olu")
    // allow is the phone's own button label, in the phone's language.
    fun tapAllow(allow: String) = t(
        "To answer by voice, tap $allow, near the bottom of the screen.",
        "To answer with voice, touch $allow, for the bottom of the screen.",
        "Láti fi ohùn dáhùn, ẹ tẹ $allow, nítòsí ìsàlẹ̀ ojú fóònù.",
        "Don amsa da murya, ku taɓa $allow, kusa da ƙasan allon.",
        "Iji olu zaa, pịa $allow, n'akụkụ ala ihuenyo.",
    )

    // Bank inside the app: what the Gateway would say
    fun cardName(last4: String) = t("Card ending $last4", "Card wey end with $last4", "Káàdì tí ó parí sí $last4", "Kati mai ƙarewa da $last4", "Kaadị nke na-ejedebe na $last4")
    val nothingSent get() = t("Nothing was sent.", "Nothing comot.", "Kò sí owó tó lọ.", "Ba a aika komai ba.", "Ọ dịghị ihe e zigara.")
    val welcomeBack get() = t("Welcome back.", "Welcome back.", "Ẹ káàbọ̀ padà.", "Barka da dawowa.", "Nnọọ ọzọ.")
    fun pinWrong(triesLeft: Int): String {
        val tries = when {
            lang == Lang.En && triesLeft == 1 -> "one try"
            lang == Lang.En -> Naira.words(triesLeft.toLong()) + " tries"
            lang == Lang.Pcm -> (if (triesLeft == 1) "one" else Naira.words(triesLeft.toLong())) + " try"
            else -> triesLeft.toString()
        }
        return t(
            "That PIN did not match. Feel the count again. You have $tries left.",
            "That PIN no match. Feel the count again. You get $tries remain.",
            "PIN yẹn kò bá a mu. Ẹ tún fi ara mọ ìkà náà. Ẹ ní àǹfààní $tries tó kù.",
            "Wannan PIN bai dace ba. Ku sake jin ƙirgan. Kuna da sauran damar $tries.",
            "PIN ahụ ekwekọghị. Nwee mmetụta ọgụgụ ọzọ. Ị fọdụrụ ohere $tries.",
        )
    }
    val pinLockedFull get() = t(
        "Your Pulse PIN is locked after three wrong tries. Nothing was sent. Zenith will call you to unlock it.",
        "Your Pulse PIN don lock because of three wrong tries. Nothing comot. Zenith go call you to open am.",
        "Pulse PIN yín ti tì pa lẹ́yìn ìgbìyànjú mẹ́ta tí kò tọ̀nà. Kò sí owó tó lọ. Zenith yóò pè yín láti ṣí i.",
        "An kulle Pulse PIN ɗinku bayan kuskure sau uku. Ba a aika komai ba. Zenith za ta kira ku don buɗe shi.",
        "Akpọchiela Pulse PIN gị mgbe ịnwara ugboro atọ na-ezighị ezi. Ọ dịghị ihe e zigara. Zenith ga-akpọ gị ka emepee ya.",
    )
    fun balanceInAccount(kobo: Long): String {
        val a = money(kobo)
        return t(
            "You have $a in your current account.",
            "You get $a for your current account.",
            "Ẹ ní $a nínú àkáǹtì yín.",
            "Kuna da $a a asusunku.",
            "Ị nwere $a na akaụntụ gị.",
        )
    }
    val nothingCameIn get() = t("Nothing came in this week.", "Nothing enter this week.", "Kò sí owó tó wọlé lọ́sẹ̀ yìí.", "Babu abin da ya shigo wannan mako.", "Ọ dịghị ihe batara n'izu a.")
    fun cameIn(kobo: Long, people: Int): String {
        val total = moneySentence(kobo)
        val from = when {
            lang == Lang.En || lang == Lang.Pcm -> if (people == 1) "one person" else Naira.words(people.toLong()) + " people"
            else -> people.toString()
        }
        return t(
            "$total came in this week, from $from.",
            "$total enter this week, from $from.",
            "$total ló wọlé lọ́sẹ̀ yìí, láti ọ̀dọ̀ ènìyàn $from.",
            "$total ya shigo wannan mako, daga mutane $from.",
            "$total batara n'izu a, site n'aka mmadụ $from.",
        )
    }
    val whoSendingTo get() = t(
        "Who are you sending to? Say a name, like Tolu or Mama.",
        "Who you wan send am give? Talk name, like Tolu or Mama.",
        "Ta ni ẹ fẹ́ fi ránṣẹ́ sí? Ẹ sọ orúkọ kan, bíi Tolu tàbí Mama.",
        "Wa za ku aika wa? Ku faɗi suna, kamar Tolu ko Mama.",
        "Onye ka ị na-eziga? Kwuo aha, dịka Tolu ma ọ bụ Mama.",
    )
    fun howMuchSend(nick: String) = t("How much should I send to $nick?", "How much I go send give $nick?", "Èló ni kí n fi ránṣẹ́ sí $nick?", "Nawa zan aika wa $nick?", "Ego ole ka m ga-eziga $nick?")
    fun foundPayee(name: String, bank: String) = t(
        "I found $name at $bank. Listen, then pulse your PIN to send.",
        "I don find $name for $bank. Listen well, then pulse your PIN make e go.",
        "Mo rí $name ní $bank. Ẹ fetí sílẹ̀, lẹ́yìn náà ẹ pulse PIN yín láti fi ránṣẹ́.",
        "Na sami $name a $bank. Ku saurara, sannan ku yi pulse PIN don aikawa.",
        "Achọtara m $name na $bank. Gee ntị, mgbe ahụ pulse PIN gị ka i ziga.",
    )
    fun canBlockCard(last4: String) = t(
        "I can block your card ending ${digits(last4)}.",
        "I fit block your card wey end with ${digits(last4)}.",
        "Mo lè dí káàdì yín tí ó parí sí ${digits(last4)}.",
        "Zan iya toshe katinku mai ƙarewa da ${digits(last4)}.",
        "Enwere m ike igbochi kaadị gị nke na-ejedebe na ${digits(last4)}.",
    )
    val help get() = t(
        "I can send money, read your balance, tell you what came in, or block your card. What would you like?",
        "I fit send money, read your balance, tell you wetin enter, or block your card. Wetin you want?",
        "Mo lè fi owó ránṣẹ́, ka owó inú àkáǹtì yín, sọ ohun tó wọlé fún yín, tàbí dí káàdì yín. Kí ni ẹ fẹ́?",
        "Zan iya aika kuɗi, karanta kuɗin asusunku, gaya muku abin da ya shigo, ko toshe katinku. Me kuke so?",
        "Enwere m ike iziga ego, gụọ ego dị na akaụntụ gị, gwa gị ihe batara, ma ọ bụ gbochie kaadị gị. Gịnị ka ị chọrọ?",
    )
    val payeeNotFound get() = t("I could not find that person. Nothing was sent.", "I no see that person. Nothing comot.", "Mi ò rí ẹni yẹn. Kò sí owó tó lọ.", "Ban sami wannan mutumin ba. Ba a aika komai ba.", "Achọtaghị m onye ahụ. Ọ dịghị ihe e zigara.")
    val amountNotRight get() = t("That amount is not right. Nothing was sent.", "That amount no correct. Nothing comot.", "Iye owó yẹn kò tọ̀nà. Kò sí owó tó lọ.", "Wannan adadin bai dace ba. Ba a aika komai ba.", "Ego ahụ ezighị ezi. Ọ dịghị ihe e zigara.")
    fun rbTo(name: String) = t("To $name.", "Give $name.", "Sí $name.", "Zuwa ga $name.", "Nye $name.")
    fun rbBank(bank: String, last4: String) = t(
        "$bank, ending ${digits(last4)}.",
        "$bank, wey end with ${digits(last4)}.",
        "$bank, tí ó parí sí ${digits(last4)}.",
        "$bank, mai ƙarewa da ${digits(last4)}.",
        "$bank, nke na-ejedebe na ${digits(last4)}.",
    )
    val rbPulseSend get() = t("Pulse your PIN to send.", "Pulse your PIN make e go.", "Ẹ pulse PIN yín láti fi ránṣẹ́.", "Ku yi pulse PIN don aikawa.", "Pulse PIN gị ka i ziga.")
    val cardNotFound get() = t("I could not find that card. Nothing has changed.", "I no see that card. Nothing don change.", "Mi ò rí káàdì yẹn. Kò sí ohun tó yí padà.", "Ban sami wannan katin ba. Babu abin da ya canza.", "Achọtaghị m kaadị ahụ. Ọ dịghị ihe gbanwere.")
    val alreadyBlocked get() = t(
        "That card is already blocked. A new one is on its way.",
        "That card don block before. New one dey come.",
        "A ti dí káàdì yẹn tẹ́lẹ̀. Tuntun ń bọ̀.",
        "An riga an toshe wannan katin. Sabo yana kan hanya.",
        "Egbochila kaadị ahụ. Nke ọhụrụ na-abịa.",
    )
    fun rbBlock(last4: String) = t(
        "Block your card ending ${digits(last4)}.",
        "Block your card wey end with ${digits(last4)}.",
        "Dí káàdì yín tí ó parí sí ${digits(last4)}.",
        "Toshe katinku mai ƙarewa da ${digits(last4)}.",
        "Gbochie kaadị gị nke na-ejedebe na ${digits(last4)}.",
    )
    val rbBlockEffect get() = t(
        "It stops working at once. A new card reaches you in about five working days.",
        "E go stop to work sharp sharp. New card go reach you for like five working days.",
        "Yóò dẹ́kun iṣẹ́ lẹ́sẹ̀kẹsẹ̀. Káàdì tuntun yóò dé ọ̀dọ̀ yín ní nǹkan bí ọjọ́ iṣẹ́ márùn-ún.",
        "Zai daina aiki nan take. Sabon kati zai iso muku cikin kusan kwanakin aiki biyar.",
        "Ọ ga-akwụsị ịrụ ọrụ ozugbo. Kaadị ọhụrụ ga-eru gị n'ihe dị ka ụbọchị ọrụ ise.",
    )
    val rbPulseBlock get() = t("Pulse your PIN to block it.", "Pulse your PIN make I block am.", "Ẹ pulse PIN yín láti dí i.", "Ku yi pulse PIN don toshe shi.", "Pulse PIN gị ka egbochie ya.")
    val requestNotFound get() = t("I could not find that request. Nothing was sent.", "I no see that request. Nothing comot.", "Mi ò rí ìbéèrè yẹn. Kò sí owó tó lọ.", "Ban sami wannan buƙata ba. Ba a aika komai ba.", "Achọtaghị m arịrịọ ahụ. Ọ dịghị ihe e zigara.")
    val alreadyDone get() = t("That was already done. Nothing more was sent.", "E don happen before. Nothing again comot.", "A ti ṣe é tẹ́lẹ̀. Kò sí owó mìíràn tó lọ.", "An riga an yi hakan. Ba a ƙara aika komai ba.", "E meela ya. Ọ dịghị ihe ọzọ e zigara.")
    val requestClosed get() = t("That request is closed. Nothing was sent.", "That request don close. Nothing comot.", "A ti pa ìbéèrè yẹn. Kò sí owó tó lọ.", "An rufe wannan buƙata. Ba a aika komai ba.", "Emechiela arịrịọ ahụ. Ọ dịghị ihe e zigara.")
    val readbackChanged get() = t(
        "Something changed since I read it to you. Please listen again.",
        "Something don change since I read am give you. Abeg listen again.",
        "Nǹkan kan ti yí padà láti ìgbà tí mo kà á fún yín. Ẹ jọ̀ọ́ ẹ tún fetí sílẹ̀.",
        "Wani abu ya canza tun da na karanta muku. Don Allah ku sake saurara.",
        "Ihe gbanwere kemgbe m gụọrọ gị ya. Biko gee ntị ọzọ.",
    )
    val limitSingle get() = t(
        "That is more than one million naira, the most you can send at once. Nothing was sent.",
        "E pass one million naira, the highest wey you fit send one time. Nothing comot.",
        "Ó ju ₦1,000,000 lọ, èyí tó pọ̀ jù tí ẹ lè fi ránṣẹ́ lẹ́ẹ̀kan. Kò sí owó tó lọ.",
        "Ya fi ₦1,000,000 yawa, mafi yawan da za ku iya aikawa a lokaci ɗaya. Ba a aika komai ba.",
        "Ọ karịrị ₦1,000,000, nke kachasị ị nwere ike iziga n'otu oge. Ọ dịghị ihe e zigara.",
    )
    fun limitDaily(leftKobo: Long): String {
        val a = money(leftKobo)
        return t(
            "You can send $a more today. Nothing was sent.",
            "You fit still send $a today. Nothing comot.",
            "Ẹ ṣì lè fi $a ránṣẹ́ lónìí. Kò sí owó tó lọ.",
            "Kuna iya ƙara aika $a yau. Ba a aika komai ba.",
            "Ị ka nwere ike iziga $a taa. Ọ dịghị ihe e zigara.",
        )
    }
    val moreThanYouHave get() = t("That is more than you have. Nothing was sent.", "E pass the money wey you get. Nothing comot.", "Ó ju owó tí ẹ ní lọ. Kò sí owó tó lọ.", "Ya fi kuɗin da kuke da shi yawa. Ba a aika komai ba.", "Ọ karịrị ego ị nwere. Ọ dịghị ihe e zigara.")
    fun approvalWait(approver: String) = t(
        "$approver checks transfers above fifty thousand naira. I have asked $approver. Nothing moves until $approver says yes.",
        "$approver dey check transfer wey pass fifty thousand naira. I don ask $approver. Nothing go move until $approver talk yes.",
        "$approver ló ń yẹ owó tó ju ₦50,000 lọ wò. Mo ti bi $approver. Kò sí ohun tí yóò lọ títí $approver yóò fi sọ bẹ́ẹ̀ni.",
        "$approver ne ke duba aikawa sama da ₦50,000. Na tambayi $approver. Babu abin da zai motsa har sai $approver ya ce eh.",
        "$approver na-elele nzipu karịrị ₦50,000. Ajụọla m $approver. Ọ dịghị ihe ga-aga ruo mgbe $approver kwuru ee.",
    )
    fun sent(nick: String) = t("Sent. $nick has it.", "E don go. $nick don get am.", "Ó ti lọ. $nick ti gbà á.", "An aika. $nick ya samu.", "Ezigala ya. $nick enwetala ya.")
    fun approved(approver: String) = t("$approver approved.", "$approver don gree.", "$approver ti fọwọ́ sí i.", "$approver ya amince.", "$approver kwadoro ya.")
    val notPaused get() = t("That request is not waiting on a pause.", "That request no dey wait for pause.", "Ìbéèrè yẹn kò dúró de ìdádúró.", "Wannan buƙata ba ta jiran dakatarwa.", "Arịrịọ ahụ anaghị eche nkwụsịtụ.")
    val stoppedSafeNothingSent get() = "$stoppedSafe $nothingSent"
    val pulseAgain get() = t("Okay. Pulse your PIN again to send it.", "Okay. Pulse your PIN again make e go.", "Ó dáa. Ẹ tún pulse PIN yín láti fi ránṣẹ́.", "To. Ku sake yin pulse PIN don aikawa.", "Ọ dị mma. Pulse PIN gị ọzọ ka i ziga ya.")
    val cancelled get() = t("Cancelled. Nothing was sent.", "I don cancel am. Nothing comot.", "A ti fagi lé e. Kò sí owó tó lọ.", "An soke. Ba a aika komai ba.", "Akagbuola ya. Ọ dịghị ihe e zigara.")
    val alreadySent get() = t("That was already sent.", "E don go before.", "A ti fi ránṣẹ́ tẹ́lẹ̀.", "An riga an aika.", "Ezigala ya.")
    val sentShort get() = t("Sent.", "E don go.", "Ó ti lọ.", "An aika.", "Ezigala ya.")
    fun notWaitingFor(approver: String) = t("That request is not waiting for $approver.", "That request no dey wait for $approver.", "Ìbéèrè yẹn kò dúró de $approver.", "Wannan buƙata ba ta jiran $approver.", "Arịrịọ ahụ anaghị eche $approver.")
    val expired get() = t(
        "That request is more than ten minutes old, so I closed it. Nothing was sent.",
        "That request don pass ten minutes, so I close am. Nothing comot.",
        "Ìbéèrè yẹn ti ju ìṣẹ́jú mẹ́wàá lọ, nítorí náà mo ti pa á. Kò sí owó tó lọ.",
        "Wannan buƙata ta wuce minti goma, don haka na rufe ta. Ba a aika komai ba.",
        "Arịrịọ ahụ agafeela nkeji iri, ya mere emechiri m ya. Ọ dịghị ihe e zigara.",
    )
    val onItsWay get() = t(
        "Your transfer is still on its way. Do not send it again. Check your balance in a few minutes.",
        "Your transfer still dey road. No send am again. Check your balance after some minutes.",
        "Owó tí ẹ fi ránṣẹ́ ṣì wà lójú ọ̀nà. Ẹ má tún fi ránṣẹ́ mọ́. Ẹ yẹ owó inú àkáǹtì yín wò ní ìṣẹ́jú díẹ̀.",
        "Kuɗin da kuka aika suna kan hanya. Kada ku sake aikawa. Ku duba kuɗin asusunku bayan 'yan mintuna.",
        "Ego i zigara ka nọ n'ụzọ. Ezigala ya ọzọ. Lelee ego dị na akaụntụ gị n'ime nkeji ole na ole.",
    )
    val pauseFull get() = "$pauseQuestion " + t("Nothing has been sent.", "Nothing don comot.", "Kò sí owó tó tíì lọ.", "Ba a aika komai ba tukuna.", "Ọ dịbeghị ihe e zigara.")

    companion object {
        private val all = Lang.entries.associateWith { Words(it) }
        fun of(lang: Lang): Words = all.getValue(lang)
    }
}
