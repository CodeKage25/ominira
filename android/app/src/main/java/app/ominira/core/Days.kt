package app.ominira.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** When something happened, said simply: "Today", "Yesterday", "Wed 14 Oct". */
object Days {
    private val short: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
    private val long: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)

    fun parse(iso: String): Instant? {
        if (iso.isBlank()) return null
        return runCatching { Instant.parse(iso) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(iso.removeSuffix("Z")).toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    /** For the screen. */
    fun label(iso: String, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String =
        name(iso, zone, today, short)

    /** For speech and TalkBack: "Wednesday 14 October". */
    fun spoken(iso: String, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String =
        name(iso, zone, today, long)

    /** A date of birth, "1961-03-14", read as "14 March 1961". Anything else comes back unchanged. */
    fun bornOn(isoDate: String): String =
        runCatching { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK).format(LocalDate.parse(isoDate)) }.getOrDefault(isoDate)

    private fun name(iso: String, zone: ZoneId, today: LocalDate, format: DateTimeFormatter): String {
        val day = parse(iso)?.atZone(zone)?.toLocalDate() ?: return ""
        return when (day) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> format.format(day)
        }
    }
}
