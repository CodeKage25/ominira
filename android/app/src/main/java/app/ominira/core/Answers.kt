package app.ominira.core

import java.text.Normalizer

/**
 * Understands short spoken answers: yes and no in English, Pidgin, Yorùbá, Hausa and Igbo, the
 * scam-pause answers, a language name, "start", "again", and strings of digits.
 *
 * Tone marks and under-dots are ignored, so "bẹ́ẹ̀ni" and "beeni" are the same word. A clear no
 * always beats a yes: "no, I'd rather read" is a no.
 */
object Answers {

    private val yesWords = setOf(
        // English
        "yes", "yeah", "yea", "yep", "yup", "ok", "okay", "sure", "correct", "right", "alright", "please", "aye",
        // Pidgin
        "oya", "ehen", "ehn",
        // Yorùbá
        "beeni", "bee", "daadaa",
        // Hausa ("to" is left out: it is far more often the English word)
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

    // A language may be named in any of the five: "Turanci" is English in Hausa, "Bekee" in Igbo,
    // "Gẹ̀ẹ́sì" in Yorùbá; "Yarbanci" and "Inyamuranci" are Hausa for Yorùbá and Igbo.
    // Pidgin comes first so "broken English" means Pidgin.
    private val languageNames = listOf(
        Lang.Pcm to setOf("pidgin", "pijin", "pigin", "pidgen", "pidging", "pigeon", "broken", "naija"),
        Lang.Yo to setOf("yoruba", "yorba", "yuroba", "yorouba", "yarbanci"),
        Lang.Ha to setOf("hausa", "housa", "ausa", "hawsa", "hausance"),
        Lang.Ig to setOf("igbo", "ibo", "eegbo", "igbos", "inyamuranci", "igbonci"),
        Lang.En to setOf("english", "inglish", "oyinbo", "turanci", "bekee", "oyibo", "geesi", "gesi"),
    )

    /** Lower case, no tone marks or under-dots, punctuation as spaces (apostrophes kept). */
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

    /** "Start", "I'm ready", or any yes. */
    fun isStart(text: String): Boolean = !isNo(text) && (isYes(text) || words(text).any { it in startWords })

    fun isAgain(text: String): Boolean = words(text).any { it in againWords } || hasPhrase(text, listOf("say it again", "one more time"))

    /** True when any of [options] is one of the words said. */
    fun mentions(text: String, vararg options: String): Boolean {
        val said = words(text)
        return options.any { it in said }
    }

    /**
     * Scam pause, "is someone on the phone telling you to send this?":
     * carry on is "carry on", "continue", "no, I chose this myself", or a plain no.
     */
    fun isCarryOn(text: String): Boolean {
        val said = words(text)
        if (said.any { it in stopWords }) return false
        return hasPhrase(text, carryOnPhrases) || isNo(text)
    }

    /** Scam pause: stop is "stop", "cancel", "hold on", or a yes ("yes, someone told me to"). */
    fun isStop(text: String): Boolean {
        if (words(text).any { it in stopWords }) return true
        return isYes(text) && !isCarryOn(text)
    }

    /** The language named, if any. */
    fun language(text: String): Lang? {
        val said = words(text).toSet()
        return languageNames.firstOrNull { (_, names) -> names.any { it in said } }?.first
    }

    /**
     * Every digit said, in order. "2 2 3 4", "two two three four", "double two three four" and
     * "22-34" all give "2234". Words that are not digits are skipped.
     */
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

    /** True when the words carry what sounds like a PIN: the word PIN and four or more digits. */
    fun soundsLikePin(text: String): Boolean = mentions(text, "pin", "password") && digits(text).length >= 4
}
