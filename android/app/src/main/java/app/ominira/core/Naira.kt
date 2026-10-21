package app.ominira.core

/**
 * Money on screen and out loud. Amounts arrive in kobo; customers see and hear whole naira:
 * "₦20,000" and "twenty thousand naira". English words only: lines in other languages come
 * from the Gateway's `say`.
 */
object Naira {

    private val ones = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
    private val scales = listOf(
        1_000_000_000L to "billion",
        1_000_000L to "million",
        1_000L to "thousand",
    )

    /** "₦20,000". No decimals; kobo are dropped. */
    fun format(kobo: Long): String {
        val naira = kobo / 100
        val sign = if (naira < 0) "-" else ""
        return sign + "₦" + group(kotlin.math.abs(naira))
    }

    /** "842,300" */
    fun group(n: Long): String {
        val digits = n.toString()
        val out = StringBuilder()
        digits.forEachIndexed { i, ch ->
            if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
            out.append(ch)
        }
        return out.toString()
    }

    /** "twenty thousand naira" */
    fun spoken(kobo: Long): String = words(kobo / 100) + " naira"

    /** The same, with a capital first letter, to start a sentence. */
    fun spokenSentence(kobo: Long): String = spoken(kobo).replaceFirstChar { it.uppercaseChar() }

    /**
     * British English number words, the way Nigerian banks read them:
     * 842300 is "eight hundred and forty-two thousand, three hundred".
     */
    fun words(n: Long): String {
        if (n < 0) return "minus " + words(-n)
        if (n == 0L) return ones[0]
        val parts = mutableListOf<String>()
        var rest = n
        for ((size, name) in scales) {
            if (rest >= size) {
                parts += below1000(rest / size) + " " + name
                rest %= size
            }
        }
        if (rest > 0) parts += if (parts.isNotEmpty() && rest < 100) "and " + below100(rest) else below1000(rest)
        val out = StringBuilder(parts.first())
        for (part in parts.drop(1)) {
            out.append(if (part.startsWith("and ")) " " else ", ").append(part)
        }
        return out.toString()
    }

    private fun below1000(n: Long): String {
        val hundreds = n / 100
        val rest = n % 100
        return when {
            hundreds == 0L -> below100(rest)
            rest == 0L -> ones[hundreds.toInt()] + " hundred"
            else -> ones[hundreds.toInt()] + " hundred and " + below100(rest)
        }
    }

    private fun below100(n: Long): String {
        if (n < 20) return ones[n.toInt()]
        val t = tens[(n / 10).toInt()]
        val u = n % 10
        return if (u == 0L) t else t + "-" + ones[u.toInt()]
    }

    /** Digits read one at a time: "4471" is "four, four, seven, one". Anything else is skipped. */
    fun digitWords(digits: String): String =
        digits.filter { it in '0'..'9' }.map { ones[it - '0'] }.joinToString(", ")
}
