package app.ominira.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Reads a NIN slip or ID card from the text the camera saw. Only the NIN's last four digits are kept.
object IdCardText {
    private val nin = Regex("""(?<!\d)(\d[ \-]?){10}\d(?!\d)""")
    private val months = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    private val dayMonthName = Regex("""\b(\d{1,2})[ \-/.]+(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[A-Z]*[ \-/.,]+(\d{4})\b""")
    private val dayMonthNumber = Regex("""\b(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4})\b""")
    private val iso = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val surnameLabel = Regex("""\b(SURNAME|LAST NAME)\b""")
    private val givenLabel = Regex("""\b(FIRST NAME|GIVEN NAMES?|FORENAMES?)\b""")

    fun ninLast4(text: String): String? = nin.find(text)?.value?.filter { it.isDigit() }?.takeLast(4)

    fun bornOn(text: String): String? {
        val up = text.uppercase(Locale.ROOT)
        val date = dayMonthName.find(up)?.let { m ->
            runCatching { LocalDate.of(m.groupValues[3].toInt(), months.indexOf(m.groupValues[2]) + 1, m.groupValues[1].toInt()) }.getOrNull()
        } ?: iso.find(up)?.let { m ->
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
        } ?: dayMonthNumber.find(up)?.let { m ->
            runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        }
        return date?.takeIf { it.isBefore(LocalDate.now()) }?.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    // The value is after the label on its line ("Surname: ADEYEMI"), or the next line.
    private fun valueAfter(lines: List<String>, label: Regex): String? {
        for ((i, line) in lines.withIndex()) {
            val m = label.find(line.uppercase(Locale.ROOT)) ?: continue
            val raw = line.substring(m.range.last + 1)
            // "SURNAME/NOM": after a slash comes the label in French, not the name.
            val rest = if (raw.trimStart().startsWith("/")) "" else raw.trim(' ', ':', '-', '.').trim()
            val value = rest.ifBlank { lines.getOrNull(i + 1)?.trim().orEmpty() }
            if (value.any { it.isLetter() } && label.find(value.uppercase(Locale.ROOT)) == null) return value
        }
        return null
    }

    private fun titleCase(name: String): String =
        name.split(' ', '-').filter { it.isNotBlank() }.joinToString(" ") { part ->
            part.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }

    // Null unless the name, the NIN and the date of birth are all there: half a card is not read back.
    fun read(text: String): IdDetails? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val surname = valueAfter(lines, surnameLabel) ?: return null
        val given = valueAfter(lines, givenLabel) ?: return null
        val last4 = ninLast4(text) ?: return null
        val born = bornOn(text) ?: return null
        return IdDetails(titleCase("$given $surname"), last4, born)
    }
}
