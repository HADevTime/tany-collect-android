package ma.tany.core.designsystem

import ma.tany.core.designsystem.format.TanyFormatters
import ma.tany.core.designsystem.format.TanyLanguage
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.model.common.MoneyAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone

class TanyFormattersTest {
    private val fr = TanyFormatters(TanyLanguage.FR)
    private val en = TanyFormatters(TanyLanguage.EN)
    private val ar = TanyFormatters(TanyLanguage.AR)

    @Test
    fun moneyUsesDhAndDirhamWithWesternDigits() {
        assertEquals("150 DH", en.money(MoneyAmount(15000)))
        assertTrue(fr.money(MoneyAmount(4950)).matches(Regex("49,50\\s?DH")))
        val arabic = ar.money(MoneyAmount(15000))
        assertTrue(arabic, arabic.startsWith("150") && arabic.endsWith("درهم"))
        assertFalse("no Arabic-Indic digits", arabic.any { it in '٠'..'٩' })
    }

    @Test
    fun businessTimesIgnoreThePhoneTimeZone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            // Casablanca offset comes from tzdb (no hard-coded offset). 2026-10-05T09:00Z is shown as Casablanca time.
            val instant = Instant.parse("2026-10-05T09:00:00.000Z")
            val expected = instant.atZone(java.time.ZoneId.of("Africa/Casablanca")).toLocalTime().toString()
            assertEquals(expected, en.businessTime(instant))
            assertTrue(en.businessWindow(instant, Instant.parse("2026-10-05T11:00:00.000Z")).contains("–"))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun usagePeriodRanges() {
        assertEquals("5–7 Oct", en.usagePeriod(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7)))
        assertEquals("5 Oct", en.usagePeriod(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun languageTags() {
        assertEquals(TanyLanguage.AR, TanyLanguage.fromTag("ar-MA"))
        assertEquals(TanyLanguage.FR, TanyLanguage.fromTag("de"))
        assertEquals("${Char(0x2066)}TNY-1003${Char(0x2069)}", ltrIsolated("TNY-1003"))
    }

    @Test
    fun longDayAndTimeRangeForPremiumHeaders() {
        assertEquals("Lundi 5 octobre", fr.businessLongDay(LocalDate.of(2026, 10, 5)))
        assertEquals("Monday 5 October", en.businessLongDay(LocalDate.of(2026, 10, 5)))
        val start = Instant.parse("2026-10-05T08:00:00Z")
        val end = Instant.parse("2026-10-05T10:00:00Z")
        val range = en.businessTimeRange(start, end)
        assertEquals("${en.businessTime(start)}–${en.businessTime(end)}", range)
        assertEquals("09:05", en.clock(java.time.LocalTime.of(9, 5)))
    }
}
