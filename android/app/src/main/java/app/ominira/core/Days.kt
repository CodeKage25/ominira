package app.ominira.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object Days {
    private val short: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
    private val long: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)

    fun parse(iso: String): Instant? {
        if (iso.isBlank()) return null
        return runCatching { Instant.parse(iso) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(iso.removeSuffix("Z")).toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    fun label(iso: String, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String =
        name(iso, zone, today, short)

    fun spoken(iso: String, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String =
        name(iso, zone, today, long)

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
