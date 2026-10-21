package app.ominira.core

import java.text.Normalizer

// A clear no always beats a yes: "no, I'd rather read" is a no.
object Answers {

    private val yesWords = setOf(
        // English
        "yes", "yeah", "yea", "yep", "yup", "ok", "okay", "sure", "correct", "right", "alright", "please", "aye",
        // Pidgin
        "oya", "ehen", "ehn",
        // Yorùbá
        "beeni", "bee", "daadaa",
        // Hausa ("to" left out: it is usually the English word)
        "eh", "toh", "na'am", "naam",
        // Igbo
        "ee", "ehh",
    )
    private val yesPhrases = listOf("na so", "e correct", "go on", "go ahead", "that is right", "that's right", "talk me through")

    private val noWords = setOf(
        // English
        "no", "nope", "nah", "not", "dont", "don't", "wrong", "rather",
        // Pidgin
        "abeg no",
        // Yorùbá
        "rara", "beeko",
        // Hausa
        "a'a", "aa",
        // Igbo
        "mba",
    )
    private val noPhrases = listOf("read it myself", "read and tap", "i'll read", "i will read", "by myself", "no o")

    private val startWords = setOf("start", "ready", "begin", "go", "now", "proceed", "continue", "oya")
    private val againWords = setOf("again", "repeat", "what", "pardon", "sorry", "huh")
    private val stopWords = setOf("stop", "cancel", "wait", "hold", "halt", "abeg")
    private val carryOnPhrases = listOf("carry on", "continue", "go on", "go ahead", "chose", "choose", "myself", "my own")

    private val digitNames = mapOf(
        "zero" to '0', "oh" to '0', "nought" to '0', "nil" to '0',
        "one" to '1', "won" to '1',
        "two" to '2', "to" to '2', "too" to '2',
        "three" to '3', "tree" to '3',
        "four" to '4', "for" to '4', "fo" to '4',
        "five" to '5',
        "six" to '6',
        "seven" to '7',
        "eight" to '8', "ate" to '8',
        "nine" to '9',
    )

    // Pidgin first, so "broken English" means Pidgin.
    private val languageNames = listOf(
        Lang.Pcm to setOf("pidgin", "pijin", "pigin", "pidgen", "pidging", "pigeon", "broken", "naija"),
        Lang.Yo to setOf("yoruba", "yorba", "yuroba", "yorouba", "yarbanci"),
        Lang.Ha to setOf("hausa", "housa", "ausa", "hawsa", "hausance"),
        Lang.Ig to setOf("igbo", "ibo", "eegbo", "igbos", "inyamuranci", "igbonci"),
        Lang.En to setOf("english", "inglish", "oyinbo", "turanci", "bekee", "oyibo", "geesi", "gesi"),
    )

    fun normalise(text: String): String {
        val bare = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return bare.replace('’', '\'').replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun words(text: String): List<String> = normalise(text).split(' ').filter { it.isNotEmpty() }

    private fun hasPhrase(text: String, phrases: Collection<String>): Boolean {
        val padded = " " + normalise(text) + " "
        return phrases.any { padded.contains(" $it ") }
    }

    fun isNo(text: String): Boolean = words(text).any { it in noWords } || hasPhrase(text, noPhrases + noWords.filter { ' ' in it })

    fun isYes(text: String): Boolean {
        if (isNo(text)) return false
        return words(text).any { it in yesWords } || hasPhrase(text, yesPhrases)
    }

    fun isStart(text: String): Boolean = !isNo(text) && (isYes(text) || words(text).any { it in startWords })

    fun isAgain(text: String): Boolean = words(text).any { it in againWords } || hasPhrase(text, listOf("say it again", "one more time"))

    fun mentions(text: String, vararg options: String): Boolean {
        val said = words(text)
        return options.any { it in said }
    }

    // A plain no means carry on ("no, I chose this myself").
    fun isCarryOn(text: String): Boolean {
        val said = words(text)
        if (said.any { it in stopWords }) return false
        return hasPhrase(text, carryOnPhrases) || isNo(text)
    }

    // A yes means stop ("yes, someone told me to").
    fun isStop(text: String): Boolean {
        if (words(text).any { it in stopWords }) return true
        return isYes(text) && !isCarryOn(text)
    }

    fun language(text: String): Lang? {
        val said = words(text).toSet()
        return languageNames.firstOrNull { (_, names) -> names.any { it in said } }?.first
    }

    fun digits(text: String): String {
        val out = StringBuilder()
        var times = 1
        for (word in words(text)) {
            when (word) {
                "double" -> { times = 2; continue }
                "triple" -> { times = 3; continue }
            }
            val named = digitNames[word]
            val run = when {
                named != null -> named.toString()
                word.all { it in '0'..'9' } -> word
                else -> ""
            }
            if (run.isNotEmpty()) {
                repeat(times - 1) { out.append(run.first()) }
                out.append(run)
            }
            times = 1
        }
        return out.toString()
    }

    fun soundsLikePin(text: String): Boolean = mentions(text, "pin", "password") && digits(text).length >= 4
}
