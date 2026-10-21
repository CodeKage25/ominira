package app.ominira.core

/**
 * The very first screen speaks one line per language, each in that language, in this order.
 * Draft wording: Yorùbá, Hausa and Igbo need a native speaker's check (and so does the Pidgin).
 * The bundled clips in assets/audio/lang_<code>.mp3 must say exactly these words.
 */
object LanguagePrompts {

    /** The order the lines are spoken and the bands are drawn. */
    val order: List<Lang> = listOf(Lang.En, Lang.Pcm, Lang.Yo, Lang.Ha, Lang.Ig)

    fun line(lang: Lang): String = when (lang) {
        Lang.En -> "For English, say English, or tap English."
        Lang.Pcm -> "For Pidgin, talk Pidgin, or touch Pidgin."
        Lang.Yo -> "Fún Yorùbá, sọ Yorùbá, tàbí tẹ Yorùbá."
        Lang.Ha -> "Don Hausa, ka ce Hausa, ko ka taɓa Hausa."
        Lang.Ig -> "Maka Igbo, kwuo Igbo, ma ọ bụ pịa Igbo."
    }

    /** Said in the chosen language once a language is picked. */
    fun chosen(lang: Lang): String = when (lang) {
        Lang.En -> "English. Thank you."
        Lang.Pcm -> "Pidgin. Correct."
        Lang.Yo -> "Yorùbá ni. Ẹ ṣé."
        Lang.Ha -> "Hausa ke nan. Na gode."
        Lang.Ig -> "Igbo. Daalụ."
    }

    /** The bundled clip for [lang], relative to the assets folder. */
    fun clipName(lang: Lang): String = "lang_${lang.code}.mp3"
}

/**
 * Everything onboarding says or shows after the language is chosen, in that language.
 *
 * English is the reviewed product copy. Pidgin follows the reference wording where there was one.
 * NATIVE-SPEAKER REVIEW NEEDED: the Pidgin, Yorùbá, Hausa and Igbo lines below are drafts written
 * during the hackathon and have not been checked. Hausa asks for "eh" rather than "i" for yes,
 * because the answer parser cannot tell "i" from the English word "I".
 */
class OnboardingCopy(
    val kicker: String,
    val welcome: String,
    val canTalk: String,
    val askTalk: String,
    val heading: String,
    val hint: String,
    val hintReader: String,
    val readAndTap: String,
    val bigTap: String,
    val tapToBegin: String,
    val stopListening: String,
    val notCaught: String,
    val sayYes: String,
    val willWait: String,
    val waysHeading: String,
    val waysHint: String,
    val pickOne: String,
    private val continueWithOne: (String) -> String,
    private val continueWithMany: (Int) -> String,
    private val modeNames: Map<Mode, Pair<String, String>>,
) {
    fun continueWith(name: String): String = continueWithOne(name)
    fun continueWith(count: Int): String = continueWithMany(count)
    fun modeName(mode: Mode): String = modeNames.getValue(mode).first
    fun modeFor(mode: Mode): String = modeNames.getValue(mode).second

    companion object {
        fun of(lang: Lang): OnboardingCopy = when (lang) {
            Lang.En -> english
            Lang.Pcm -> pidgin
            Lang.Yo -> yoruba
            Lang.Ha -> hausa
            Lang.Ig -> igbo
        }

        private val english = OnboardingCopy(
            kicker = "Welcome",
            welcome = "Welcome to Òmìnira.",
            canTalk = "I can talk you through everything, so you never need to see the screen.",
            askTalk = "Would you like that? Say yes, or tap anywhere on the screen.",
            heading = "Shall I talk you through it?",
            hint = "Say yes, or tap anywhere on the screen. I will ask every question out loud and listen for your answer.",
            hintReader = "Double tap anywhere and Òmìnira will guide you by voice, or choose read and tap.",
            readAndTap = "Read and tap instead",
            bigTap = "Tap anywhere and I will talk you through it",
            tapToBegin = "Tap anywhere on the screen to begin.",
            stopListening = "I will stop listening. Tap anywhere to talk, or choose read and tap.",
            notCaught = "Sorry, I did not catch that.",
            sayYes = "Say yes, or tap anywhere on the screen.",
            willWait = "I will wait. Tap anywhere on the screen when you are ready.",
            waysHeading = "How would you like to use Òmìnira?",
            waysHint = "Pick one or more. You can change this any time.",
            pickOne = "Pick at least one",
            continueWithOne = { "Continue with $it" },
            continueWithMany = { "Continue with $it ways" },
            modeNames = mapOf(
                Mode.Voice to ("Voice" to "Blind and low vision"),
                Mode.Sign to ("Sign and captions" to "Deaf and hard of hearing"),
                Mode.Switch to ("Switch" to "Limited hand movement"),
                Mode.Plain to ("Plain" to "Bigger, one step at a time"),
            ),
        )

        // Draft, for native-speaker review.
        private val pidgin = OnboardingCopy(
            kicker = "Welcome",
            welcome = "Welcome to Òmìnira.",
            canTalk = "I fit talk you through everything, so you no need to see the screen.",
            askTalk = "You want am like that? Talk yes, or touch anywhere for the screen.",
            heading = "Make I talk you through am?",
            hint = "Talk yes, or touch anywhere for the screen. I go ask every question for mouth, and hear wetin you talk.",
            hintReader = "Double tap anywhere and Òmìnira go guide you with voice, or choose read and touch.",
            readAndTap = "Make I read and touch by myself",
            bigTap = "Touch anywhere and I go talk you through am",
            tapToBegin = "Touch anywhere for the screen make we start.",
            stopListening = "I go stop to listen now. Touch anywhere to talk, or choose read and touch.",
            notCaught = "Sorry, I no hear am well.",
            sayYes = "Talk yes, or touch anywhere for the screen.",
            willWait = "I go wait. Touch anywhere for the screen when you ready.",
            waysHeading = "How you wan take use Òmìnira?",
            waysHint = "Pick one or more. You fit change am anytime.",
            pickOne = "Pick at least one",
            continueWithOne = { "Continue with $it" },
            continueWithMany = { "Continue with $it ways" },
            modeNames = mapOf(
                Mode.Voice to ("Voice" to "For people wey no see, or no see well"),
                Mode.Sign to ("Sign and captions" to "For people wey no hear, or no hear well"),
                Mode.Switch to ("Switch" to "Hand no dey move well"),
                Mode.Plain to ("Plain" to "Big, one step at a time"),
            ),
        )

        // Draft, for native-speaker review.
        private val yoruba = OnboardingCopy(
            kicker = "Ẹ káàbọ̀",
            welcome = "Ẹ káàbọ̀ sí Òmìnira.",
            canTalk = "Mo lè fi ohùn tọ́ yín sọ́nà nínú ohun gbogbo, kí ẹ má baà nílò láti wo ojú fóònù.",
            askTalk = "Ṣé ẹ fẹ́ bẹ́ẹ̀? Ẹ sọ bẹ́ẹ̀ni, tàbí ẹ tẹ ibikíbi lójú fóònù.",
            heading = "Ṣé kí n fi ohùn tọ́ yín sọ́nà?",
            hint = "Ẹ sọ bẹ́ẹ̀ni, tàbí ẹ tẹ ibikíbi lójú fóònù. Màá béèrè gbogbo ìbéèrè sókè, màá sì gbọ́ ìdáhùn yín.",
            hintReader = "Ẹ tẹ ibikíbi lẹ́ẹ̀mejì, Òmìnira yóò fi ohùn tọ́ yín sọ́nà, tàbí ẹ yan kíkà àti títẹ̀.",
            readAndTap = "Màá kà á, màá sì tẹ̀ ẹ́ fúnra mi",
            bigTap = "Ẹ tẹ ibikíbi, màá sì fi ohùn tọ́ yín sọ́nà",
            tapToBegin = "Ẹ tẹ ibikíbi lójú fóònù láti bẹ̀rẹ̀.",
            stopListening = "Mi ò ní gbọ́ mọ́. Ẹ tẹ ibikíbi láti sọ̀rọ̀, tàbí ẹ yan kíkà àti títẹ̀.",
            notCaught = "Ẹ má bínú, kò yé mi.",
            sayYes = "Ẹ sọ bẹ́ẹ̀ni, tàbí ẹ tẹ ibikíbi lójú fóònù.",
            willWait = "Màá dúró. Ẹ tẹ ibikíbi lójú fóònù nígbà tí ẹ bá ṣetán.",
            waysHeading = "Báwo ni ẹ ṣe fẹ́ lo Òmìnira?",
            waysHint = "Ẹ yan ọ̀kan tàbí jù bẹ́ẹ̀ lọ. Ẹ lè yí i padà nígbàkúgbà.",
            pickOne = "Ẹ yan ọ̀kan, ó kéré tán",
            continueWithOne = { "Ẹ tẹ̀síwájú pẹ̀lú $it" },
            continueWithMany = { "Ẹ tẹ̀síwájú pẹ̀lú ọ̀nà $it" },
            modeNames = mapOf(
                Mode.Voice to ("Ohùn" to "Afọ́jú àti àwọn tí kò ríran dáadáa"),
                Mode.Sign to ("Èdè àwọn adití àti àkọlé" to "Adití àti àwọn tí kò gbọ́ràn dáadáa"),
                Mode.Switch to ("Bọ́tìnnì kan" to "Ọwọ́ tí kò rìn dáadáa"),
                Mode.Plain to ("Rírọrùn" to "Ó tóbi, ìgbésẹ̀ kan lẹ́ẹ̀kan"),
            ),
        )

        // Draft, for native-speaker review.
        private val hausa = OnboardingCopy(
            kicker = "Barka",
            welcome = "Barka da zuwa Òmìnira.",
            canTalk = "Zan iya yi muku jagora da murya a komai, don kada ku buƙaci ganin allon.",
            askTalk = "Kuna so haka? Ku ce eh, ko ku taɓa ko'ina a allon.",
            heading = "In yi muku jagora da murya?",
            hint = "Ku ce eh, ko ku taɓa ko'ina a allon. Zan yi kowace tambaya da murya, in saurari amsarku.",
            hintReader = "Ku taɓa ko'ina sau biyu, Òmìnira zai yi muku jagora da murya, ko ku zaɓi karanta da taɓawa.",
            readAndTap = "Zan karanta in taɓa da kaina",
            bigTap = "Ku taɓa ko'ina, zan yi muku jagora",
            tapToBegin = "Ku taɓa ko'ina a allon don farawa.",
            stopListening = "Zan daina saurare. Ku taɓa ko'ina don magana, ko ku zaɓi karanta da taɓawa.",
            notCaught = "Yi haƙuri, ban ji ba.",
            sayYes = "Ku ce eh, ko ku taɓa ko'ina a allon.",
            willWait = "Zan jira. Ku taɓa ko'ina a allon idan kun shirya.",
            waysHeading = "Ta yaya kuke so ku yi amfani da Òmìnira?",
            waysHint = "Ku zaɓi ɗaya ko fiye. Kuna iya canza wannan kowane lokaci.",
            pickOne = "Ku zaɓi aƙalla ɗaya",
            continueWithOne = { "Ci gaba da $it" },
            continueWithMany = { "Ci gaba da hanyoyi $it" },
            modeNames = mapOf(
                Mode.Voice to ("Murya" to "Makafi da masu raunin gani"),
                Mode.Sign to ("Yaren kurame da rubutu" to "Kurame da masu raunin ji"),
                Mode.Switch to ("Maɓalli" to "Ƙarancin motsin hannu"),
                Mode.Plain to ("Mai sauƙi" to "Babba, mataki ɗaya a lokaci ɗaya"),
            ),
        )

        // Draft, for native-speaker review.
        private val igbo = OnboardingCopy(
            kicker = "Nnọọ",
            welcome = "Nnọọ na Òmìnira.",
            canTalk = "Enwere m ike iji olu duzie gị n'ihe niile, ka ị ghara ịchọ ịhụ ihuenyo.",
            askTalk = "Ị chọrọ nke ahụ? Kwuo ee, ma ọ bụ pịa ebe ọ bụla na ihuenyo.",
            heading = "Ka m jiri olu duzie gị?",
            hint = "Kwuo ee, ma ọ bụ pịa ebe ọ bụla na ihuenyo. M ga-ajụ ajụjụ ọ bụla n'olu, ma gee ntị na azịza gị.",
            hintReader = "Pịa ebe ọ bụla ugboro abụọ, Òmìnira ga-eji olu duzie gị, ma ọ bụ họrọ gụọ ma pịa.",
            readAndTap = "Aga m agụ ma pịa n'onwe m",
            bigTap = "Pịa ebe ọ bụla, m ga-eji olu duzie gị",
            tapToBegin = "Pịa ebe ọ bụla na ihuenyo ka ịmalite.",
            stopListening = "Agaghị m ege ntị ọzọ. Pịa ebe ọ bụla ka ikwu okwu, ma ọ bụ họrọ gụọ ma pịa.",
            notCaught = "Ndo, anụghị m nke ọma.",
            sayYes = "Kwuo ee, ma ọ bụ pịa ebe ọ bụla na ihuenyo.",
            willWait = "Aga m eche. Pịa ebe ọ bụla na ihuenyo mgbe ị dị njikere.",
            waysHeading = "Kedu ka ị chọrọ isi jiri Òmìnira?",
            waysHint = "Họrọ otu ma ọ bụ karịa. Ị nwere ike ịgbanwe nke a mgbe ọ bụla.",
            pickOne = "Họrọ opekata mpe otu",
            continueWithOne = { "Gaa n'ihu na $it" },
            continueWithMany = { "Gaa n'ihu na ụzọ $it" },
            modeNames = mapOf(
                Mode.Voice to ("Olu" to "Ndị ìsì na ndị anya adịghị ike"),
                Mode.Sign to ("Asụsụ aka na ederede" to "Ndị ntị chiri na ndị anaghị anụ nke ọma"),
                Mode.Switch to ("Otu bọtịnụ" to "Aka anaghị emegharị nke ọma"),
                Mode.Plain to ("Dị mfe" to "Buru ibu, otu nzọụkwụ n'otu oge"),
            ),
        )
    }
}
