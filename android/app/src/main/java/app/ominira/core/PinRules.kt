package app.ominira.core

import java.time.LocalDate

// Refused on the phone before a new PIN is sealed. The bank checks the easy ones again.
object PinRules {
    enum class Weak { Easy, Birthday }

    fun check(pin: List<Int>, bornOn: String?): Weak? {
        if (pin.size != 4) return Weak.Easy
        val steps = pin.zipWithNext { a, b -> b - a }.toSet()
        // One digit four times, a run up or down, or a pair repeated: the same rules as the bank's.
        if (pin.toSet().size == 1 || steps == setOf(1) || steps == setOf(-1) || (pin[0] == pin[2] && pin[1] == pin[3])) {
            return Weak.Easy
        }
        val born = bornOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val typed = pin.joinToString("")
        val day = "%02d".format(born.dayOfMonth)
        val month = "%02d".format(born.monthValue)
        return if (typed == born.year.toString() || typed == day + month || typed == month + day) Weak.Birthday else null
    }
}
