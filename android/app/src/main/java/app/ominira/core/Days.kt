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

    fun label(iso: String, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone), lang: Lang = Lang.En): String =
        name(iso, zone, today, short, lang)

    fun spoken(iso: String, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone), lang: Lang = Lang.En): String =
        name(iso, zone, today, long, lang)

    // Pidgin reads dates the English way; yo, ha and ig take their own month and day names.
    private fun locale(lang: Lang): Locale = if (lang == Lang.En || lang == Lang.Pcm) Locale.UK else Locale.forLanguageTag(lang.code)

    fun bornOn(isoDate: String, lang: Lang = Lang.En): String =
        runCatching { DateTimeFormatter.ofPattern("d MMMM yyyy", locale(lang)).format(LocalDate.parse(isoDate)) }.getOrDefault(isoDate)

    private fun name(iso: String, zone: ZoneId, today: LocalDate, format: DateTimeFormatter, lang: Lang): String {
        val day = parse(iso)?.atZone(zone)?.toLocalDate() ?: return ""
        val words = Words.of(lang)
        return when (day) {
            today -> words.today
            today.minusDays(1) -> words.yesterday
            else -> format.withLocale(locale(lang)).format(day)
        }
    }
}
