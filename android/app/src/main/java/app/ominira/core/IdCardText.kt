package app.ominira.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// One line of text the camera read, and where it sits on the card, in pixels.
data class CardLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

// Reads a NIN slip or ID card from the lines the camera saw. Only the NIN's last four digits are kept.
object IdCardText {
    private val nin = Regex("""(?<!\d)(\d[ \-]?){10}\d(?!\d)""")
    private val months = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    private val dayMonthName = Regex("""\b(\d{1,2})[ \-/.]+(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[A-Z]*[ \-/.,]+(\d{4})\b""")
    private val dayMonthNumber = Regex("""\b(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4})\b""")
    private val iso = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val surnameLabel = Regex("""\b(SURNAME|LAST NAME)\b""")
    private val givenLabel = Regex("""\b(FIRST NAME|GIVEN NAMES?|FORENAMES?)\b""")
    // Any label on these cards: never a name, even when it sits under one.
    private val anyLabel = Regex("""\b(SURNAME|NOM|GIVEN|PRENOMS?|FIRST NAME|MIDDLE|DATE|BIRTH|SEX|SEXE|ISSUE|NIN|NATIONAL|REPUBLIC|NIGERIA|NGA|SLIP)\b""")

    fun ninLast4(text: String): String? = nin.find(text)?.value?.filter { it.isDigit() }?.takeLast(4)

    // The date of birth is the earliest date on the card: issue and expiry dates come after it.
    fun bornOn(text: String): String? {
        val up = text.uppercase(Locale.ROOT)
        val dates = dayMonthName.findAll(up).mapNotNull { m ->
            runCatching { LocalDate.of(m.groupValues[3].toInt(), months.indexOf(m.groupValues[2]) + 1, m.groupValues[1].toInt()) }.getOrNull()
        } + iso.findAll(up).mapNotNull { m ->
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
        } + dayMonthNumber.findAll(up).mapNotNull { m ->
            runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        }
        return dates.filter { it.isBefore(LocalDate.now()) }.minOrNull()?.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    // The value is after the label on its line ("Surname: ADEYEMI"), else the nearest line just below it,
    // in the same column. The camera's lines come in no fixed order, so position decides, not order.
    private fun valueOf(lines: List<CardLine>, label: Regex): CardLine? {
        for (line in lines) {
            val m = label.find(line.text.uppercase(Locale.ROOT)) ?: continue
            val raw = line.text.substring(m.range.last + 1)
            // "SURNAME/NOM": after a slash comes the label in French, not the name.
            val rest = if (raw.trimStart().startsWith("/")) "" else raw.trim(' ', ':', '-', '.').trim()
            if (rest.any { it.isLetter() }) return line.copy(text = rest)
            val below = lines
                .filter { it !== line }
                .filter { it.top >= line.top + line.height / 2 && it.top - line.bottom <= line.height * 3 }
                .filter { it.left < line.right && it.right > line.left }
                .minByOrNull { it.top }
            if (below != null) return below
        }
        return null
    }

    private fun name(line: CardLine?): String? {
        val text = line?.text?.trim() ?: return null
        if (!text.any { it.isLetter() } || anyLabel.containsMatchIn(text.uppercase(Locale.ROOT))) return null
        return text.split(' ', '-', ',', '.').filter { it.isNotBlank() }.joinToString(" ") { part ->
            part.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }

    // Null unless the name, the NIN and the date of birth are all there: half a card is not read back.
    fun read(lines: List<CardLine>): IdDetails? {
        val all = lines.joinToString("\n") { it.text }
        val surname = name(valueOf(lines, surnameLabel)) ?: return null
        val given = name(valueOf(lines, givenLabel)) ?: return null
        val last4 = ninLast4(all) ?: return null
        val born = bornOn(all) ?: return null
        return IdDetails("$given $surname", last4, born)
    }
}
