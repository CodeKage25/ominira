package app.ominira.core

import kotlin.math.roundToLong

/**
 * Reads an amount out of what someone said or typed, the way speech recognisers write it:
 * "20000", "20,000", "₦20,000", "N20,000", "20k", "1.5m", "20 thousand", "twenty thousand",
 * "one million two hundred thousand", "twenty thousand five hundred naira".
 *
 * Reading starts at the first number word and stops at the first ordinary word after it, so in
 * "send 20,000 to Tolu" nothing after "to" is read. Returns kobo, or null when there is no amount.
 */
object SpokenAmount {

    private val small = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40,
        "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )
    private val multipliers = mapOf("thousand" to 1_000.0, "k" to 1_000.0, "million" to 1_000_000.0, "m" to 1_000_000.0)
    private val fillers = setOf("and", "naira", "a")

    /** A figure with an optional currency mark in front and an optional k or m behind. */
    private val figure = Regex("""^(?:₦|n)?(\d+(?:\.\d+)?)(k|m)?$""")

    fun parseKobo(text: String): Long? {
        var prepared = text.lowercase()
        // "1,000,000" and "20,000": drop the grouping commas, however many there are.
        while (true) {
            val next = prepared.replace(Regex("""(\d),(\d{3})(?!\d)"""), "$1$2")
            if (next == prepared) break
            prepared = next
        }
        prepared = prepared.replace(Regex("""[^a-z0-9₦.\s-]"""), " ")

        var finished = 0.0 // groups already closed by thousand or million
        var group = 0.0 // the group being built
        var started = false

        for (raw in prepared.split(Regex("""[\s-]+"""))) {
            val word = raw.trim('.')
            if (word.isEmpty()) continue
            val match = figure.find(word)
            when {
                match != null -> {
                    val value = match.groupValues[1].toDouble()
                    val scale = multipliers[match.groupValues[2]]
                    if (scale != null) finished += value * scale else group += value
                    started = true
                }
                word in small -> {
                    group += small.getValue(word)
                    started = true
                }
                word == "hundred" -> {
                    group = (if (group == 0.0) 1.0 else group) * 100
                    started = true
                }
                // A lone "k" or "m" only counts after a number ("20 k"), never in "I'm".
                word in multipliers && (word.length > 1 || started) -> {
                    finished += (if (group == 0.0) 1.0 else group) * multipliers.getValue(word)
                    group = 0.0
                    started = true
                }
                word in fillers -> Unit
                started -> break
            }
        }
        val naira = finished + group
        if (!started || naira <= 0.0) return null
        return (naira * 100).roundToLong()
    }
}
