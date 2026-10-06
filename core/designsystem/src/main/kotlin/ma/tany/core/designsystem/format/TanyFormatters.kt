package ma.tany.core.designsystem.format

import ma.tany.core.model.common.BusinessTime
import ma.tany.core.model.common.MoneyAmount
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** TANY app languages. Darija is not an app locale; Arabic = Modern Standard Arabic. */
enum class TanyLanguage(val tag: String) {
    FR("fr"),
    EN("en"),
    AR("ar"),
    ;

    /** Western digits 0-9 in all three languages (TANY glossary: « chiffres 0-9 dans les trois langues »). */
    val locale: Locale get() = Locale.forLanguageTag("$tag-MA-u-nu-latn")

    companion object {
        fun fromTag(tag: String?): TanyLanguage {
            val primary = tag?.substringBefore('-')?.substringBefore('_')?.lowercase()
            return entries.firstOrNull { it.tag == primary } ?: FR
        }
    }
}

/**
 * Presentation formatters (category E: structured values are formatted, never translated).
 * - Money: server amounts as-is — « 150 DH » (FR/EN), « 150 درهم » (AR); decimals only when present.
 * - Business schedule: ALWAYS rendered in Africa/Casablanca, never the phone time zone.
 */
class TanyFormatters(val language: TanyLanguage) {
    private val locale = language.locale

    fun money(amount: MoneyAmount): String {
        val format = NumberFormat.getNumberInstance(locale).apply {
            val hasCents = amount.centimes % 100 != 0L
            minimumFractionDigits = if (hasCents) 2 else 0
            maximumFractionDigits = 2
            isGroupingUsed = true
        }
        val number = format.format(amount.major)
        return when (language) {
            TanyLanguage.AR -> "$number درهم"
            else -> "$number DH"
        }
    }

    /** « 49,50 DH / jour » style daily price. */
    fun moneyPerDay(amount: MoneyAmount, perDayLabel: String): String = "${money(amount)} $perDayLabel"

    /** Server clock time (opening hours, already Africa/Casablanca) as `HH:mm`. */
    fun clock(time: LocalTime): String = TIME.format(time)

    /** Business clock time (`HH:mm`, 24 h) in Africa/Casablanca. */
    fun businessTime(instant: Instant): String = TIME.format(BusinessTime.at(instant))

    /** « lun. 5 oct. » — civil business day. */
    fun businessDay(date: LocalDate): String = DateTimeFormatter.ofPattern("EEE d MMM", locale).format(date)

    /** « Lundi 5 octobre » — header date of a civil business day (capitalized for the locale). */
    fun businessLongDay(date: LocalDate): String =
        DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(date).replaceFirstChar { it.titlecase(locale) }

    /** « 09:00–11:00 » — same-day window clock times in Africa/Casablanca (falls back to [businessWindow]). */
    fun businessTimeRange(start: Instant, end: Instant): String {
        val s = BusinessTime.at(start)
        val e = BusinessTime.at(end)
        return if (s.toLocalDate() == e.toLocalDate()) "${TIME.format(s)}–${TIME.format(e)}" else businessWindow(start, end)
    }

    /** « lun. 5 oct. · 09:00 » — instant shown in Africa/Casablanca. */
    fun businessDayTime(instant: Instant): String {
        val zoned = BusinessTime.at(instant)
        return "${businessDay(zoned.toLocalDate())} · ${TIME.format(zoned)}"
    }

    /** Pickup / return window « lun. 5 oct. · 09:00–11:00 » (both ends in Africa/Casablanca). */
    fun businessWindow(start: Instant, end: Instant): String {
        val s = BusinessTime.at(start)
        val e = BusinessTime.at(end)
        return if (s.toLocalDate() == e.toLocalDate()) {
            "${businessDay(s.toLocalDate())} · ${TIME.format(s)}–${TIME.format(e)}"
        } else {
            "${businessDayTime(start)} – ${businessDayTime(end)}"
        }
    }

    /** « 5–7 oct. » / « 30 sept. – 2 oct. » usage period (server-provided dates). */
    fun usagePeriod(start: LocalDate, end: LocalDate): String {
        val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
        return when {
            start == end -> dayMonth.format(start)
            start.month == end.month && start.year == end.year -> "${start.dayOfMonth}–${dayMonth.format(end)}"
            else -> "${dayMonth.format(start)} – ${dayMonth.format(end)}"
        }
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

/** Unicode LEFT-TO-RIGHT ISOLATE / POP DIRECTIONAL ISOLATE, built from code points (no invisible character in source). */
private val LRI: Char = Char(0x2066)
private val PDI: Char = Char(0x2069)

/** Isolates an identifier (TNY-1003, PSF001, phone) as LTR inside RTL text (Unicode LRI … PDI). */
fun ltrIsolated(text: String): String = "$LRI$text$PDI"
