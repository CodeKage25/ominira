package app.ominira.core

object Lines {

    fun hello(lang: Lang): String = when (lang) {
        Lang.En -> "Hello. How can I help?"
        Lang.Pcm -> "How far? Wetin you wan do?"
        Lang.Yo -> "Ẹ káàbọ̀. Kí ni kí n ṣe fún yín?"
        Lang.Ha -> "Sannu. Me zan yi muku?"
        Lang.Ig -> "Nnọọ. Gịnị ka m ga-emere gị?"
    }

    fun whichLanguage(lang: Lang): String = when (lang) {
        Lang.En -> "Which language?"
        Lang.Pcm -> "Which language you want?"
        Lang.Yo -> "Èdè wo ni ẹ fẹ́?"
        Lang.Ha -> "Wane harshe kuke so?"
        Lang.Ig -> "Kedu asụsụ ị chọrọ?"
    }

    fun carryOn(lang: Lang): String = when (lang) {
        Lang.En -> "Continue in English"
        Lang.Pcm -> "Make we continue"
        Lang.Yo -> "Ẹ jẹ́ ká tẹ̀síwájú"
        Lang.Ha -> "Mu ci gaba"
        Lang.Ig -> "Ka anyị gaa n’ihu"
    }

    fun greeting(lang: Lang, name: String, hour: Int): String {
        val part = when (hour) {
            in 0..11 -> 0
            in 12..16 -> 1
            else -> 2
        }
        return when (lang) {
            Lang.En, Lang.Pcm -> "Good ${listOf("morning", "afternoon", "evening")[part]}, $name."
            Lang.Yo -> listOf("Ẹ káàárọ̀", "Ẹ káàsán", "Ẹ kúùrọ̀lẹ́")[part] + ", $name."
            Lang.Ha -> listOf("Ina kwana", "Barka da rana", "Barka da yamma")[part] + ", $name."
            Lang.Ig -> listOf("Ụtụtụ ọma", "Ehihie ọma", "Mgbede ọma")[part] + ", $name."
        }
    }

    fun whatToDo(lang: Lang): String = when (lang) {
        Lang.En -> "What do you want to do?"
        Lang.Pcm -> "Wetin you wan do?"
        Lang.Yo -> "Kí ni ẹ fẹ́ ṣe?"
        Lang.Ha -> "Me kuke so ku yi?"
        Lang.Ig -> "Gịnị ka ị chọrọ ime?"
    }

    fun pulseExplain(lang: Lang): String = when (lang) {
        Lang.Pcm -> "Your Pulse PIN na by feeling, so nobody fit see am. Tap the middle of the screen make e start. " +
            "The phone go dey count with small buzz, one buzz for each number, and e dey start from different number every time. " +
            "Zero na strong double buzz, so after am na one, then two, like that. " +
            "When the count reach your number, tap. Do am four times."
        Lang.Yo, Lang.Ha, Lang.Ig -> pulseExplainDraft(lang)
        else -> "Your Pulse PIN is entered by feel, so nobody can see it. Tap the middle of the screen to start. " +
            "The phone counts with soft buzzes, one buzz for each number, and it starts at a different number every time. " +
            "Zero is a strong double buzz, so after it comes one, then two, and so on. " +
            "When the count reaches your digit, tap. Do that four times."
    }

    // Unreviewed drafts.
    private fun pulseExplainDraft(lang: Lang): String = PinTerm.local(lang, when (lang) {
        Lang.Yo -> "Pulse PIN yín ni ẹ ó fi ara mọ̀, kí ẹnikẹ́ni má baà rí i. Ẹ tẹ àárín ojú fóònù láti bẹ̀rẹ̀. " +
            "Fóònù yóò máa ka pẹ̀lú ìgbọ̀n jẹ́jẹ́, ìgbọ̀n kan fún nọ́ńbà kọ̀ọ̀kan, yóò sì máa bẹ̀rẹ̀ ní nọ́ńbà ọ̀tọ̀ ní gbogbo ìgbà. " +
            "Òdo ni ìgbọ̀n méjì tó lágbára, lẹ́yìn rẹ̀ ni ọ̀kan, lẹ́yìn náà méjì, bẹ́ẹ̀ bẹ́ẹ̀ lọ. " +
            "Nígbà tí ìkà bá dé nọ́ńbà yín, ẹ tẹ̀ ẹ́. Ẹ ṣe bẹ́ẹ̀ nígbà mẹ́rin."
        Lang.Ha -> "Ana shigar da Pulse PIN ɗinku ta hanyar ji, don haka babu wanda zai iya ganinsa. Ku taɓa tsakiyar allon don farawa. " +
            "Wayar tana ƙirga da girgiza a hankali, girgiza ɗaya ga kowace lamba, kuma tana farawa daga lamba daban kowane lokaci. " +
            "Sifili girgiza biyu ce mai ƙarfi, bayanta sai ɗaya, sannan biyu, da sauransu. " +
            "Idan ƙirgan ya kai lambarku, ku taɓa. Ku yi haka sau huɗu."
        else -> "A na-etinye Pulse PIN gị site na mmetụta, ya mere ọ dịghị onye ga-ahụ ya. Pịa etiti ihuenyo ka ịmalite. " +
            "Ekwentị na-agụ site na mkpọtụ dị nro, otu mkpọtụ maka nọmba ọ bụla, ọ na-amalitekwa na nọmba dị iche oge ọ bụla. " +
            "Efu bụ mkpọtụ abụọ siri ike, mgbe ahụ otu, mgbe ahụ abụọ, wee gaa n'ihu. " +
            "Mgbe ọgụgụ ruru nọmba gị, pịa. Mee nke ahụ ugboro anọ."
    })

    fun pulseShort(lang: Lang): String = PinTerm.local(lang, when (lang) {
        Lang.Pcm -> "Your Pulse PIN. Tap make e start. Zero na the strong double buzz."
        Lang.Yo -> "Pulse PIN yín. Ẹ tẹ̀ ẹ́ láti bẹ̀rẹ̀. Òdo ni ìgbọ̀n méjì tó lágbára."
        Lang.Ha -> "Pulse PIN ɗinku. Ku taɓa don farawa. Sifili ita ce girgiza biyu mai ƙarfi."
        Lang.Ig -> "Pulse PIN gị. Pịa ka ịmalite. Efu bụ mkpọtụ abụọ siri ike."
        else -> "Your Pulse PIN. Tap to start. Zero is the strong double buzz."
    })

    fun practiceStart(lang: Lang): String = PinTerm.local(lang, when (lang) {
        Lang.Pcm -> "Make we practise. Nothing go send. Tap to start, wait for the strong double buzz, that one na zero, " +
            "then count one, two, three, and tap. Zero go also make small sound so you fit learn am."
        Lang.Yo -> "Ẹ jẹ́ ká dánrawò. Kò sí ohun tí yóò lọ. Ẹ tẹ̀ ẹ́ láti bẹ̀rẹ̀, ẹ dúró de ìgbọ̀n méjì tó lágbára, òdo nìyẹn, " +
            "lẹ́yìn náà ẹ ka ọ̀kan, méjì, mẹ́ta, kí ẹ sì tẹ̀ ẹ́. Òdo yóò tún máa dún nígbà ìdánrawò."
        Lang.Ha -> "Mu yi gwaji. Ba a aika komai. Ku taɓa don farawa, ku jira girgiza biyu mai ƙarfi, wannan sifili ne, " +
            "sannan ku ƙirga ɗaya, biyu, uku, ku taɓa. Sifili kuma yana yin sauti lokacin gwaji."
        Lang.Ig -> "Ka anyị mee omume. Ọ dịghị ihe a na-eziga. Pịa ka ịmalite, chere mkpọtụ abụọ siri ike, nke ahụ bụ efu, " +
            "mgbe ahụ gụọ otu, abụọ, atọ, wee pịa. Efu na-adakwa ụda mgbe ị na-eme omume."
        else -> "Let's practise. Nothing is sent. Tap to start, wait for the strong double buzz, that's zero, " +
            "then count one, two, three, and tap. Zero also makes a sound while you practise."
    })

    fun practiceResult(lang: Lang, digit: Int): String {
        val word = Naira.words(digit.toLong())
        return when {
            digit == 3 && lang == Lang.Pcm -> "Na three be that. You don get am. Tap Done when you ready."
            digit == 3 && lang == Lang.Yo -> "Mẹ́ta nìyẹn. Ẹ ti mọ̀ ọ́n. Ẹ tẹ ${Words.of(lang).donePractising} nígbà tí ẹ bá ṣetán."
            digit == 3 && lang == Lang.Ha -> "Uku ke nan. Kun gane. Ku taɓa ${Words.of(lang).donePractising} idan kun shirya."
            digit == 3 && lang == Lang.Ig -> "Nke ahụ bụ atọ. Ị matala ya. Pịa ${Words.of(lang).donePractising} mgbe ị dị njikere."
            lang == Lang.Yo -> "$digit nìyẹn. Ẹ dúró de ìgbọ̀n méjì tó lágbára, òdo nìyẹn, lẹ́yìn náà ẹ ka ọ̀kan, méjì, mẹ́ta, kí ẹ sì tẹ̀ ẹ́."
            lang == Lang.Ha -> "$digit ke nan. Ku jira girgiza biyu mai ƙarfi, wannan sifili ne, sannan ku ƙirga ɗaya, biyu, uku, ku taɓa."
            lang == Lang.Ig -> "Nke ahụ bụ $digit. Chere mkpọtụ abụọ siri ike, nke ahụ bụ efu, mgbe ahụ gụọ otu, abụọ, atọ, wee pịa."
            digit == 3 -> "That was three. You've got it. Tap Done when you're ready."
            lang == Lang.Pcm -> "Na $word be that. Wait for the strong double buzz, that one na zero, then count one, two, three, and tap."
            else -> "That was $word. Wait for the strong double buzz, that's zero, then count one, two, three, and tap."
        }
    }

    const val PIN_SPOKEN = "Please never say your PIN out loud. I did not keep it. Your Pulse PIN is entered by feel, on the screen."
}
