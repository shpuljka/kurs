package io.github.shpuljka.kurs.digest

import io.github.shpuljka.kurs.data.CurrencyPair
import io.github.shpuljka.kurs.data.Rates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class DigestBuilderTest {
    private val ru = Locale.forLanguageTag("ru-RU")
    private fun rates(day: Int, vararg pairs: Pair<String, String>) =
        Rates(LocalDate.of(2026, 10, day), pairs.associate { it.first to BigDecimal(it.second) })

    private val today = rates(24, "USD" to "1", "RUB" to "81.30", "EUR" to "0.9", "JPY" to "150")
    private val yesterday = rates(23, "USD" to "1", "RUB" to "81", "EUR" to "0.9")
    private val usdRub = CurrencyPair("USD", "RUB")

    private fun plain(s: String) = s.replace(' ', ' ').replace(' ', ' ')

    @Test fun `cross rate and change against yesterday`() {
        val line = DigestBuilder.build(listOf(usdRub), today, yesterday).lines.single()
        assertEquals(0, BigDecimal("81.30").compareTo(line.rate))
        assertEquals(0, BigDecimal("0.0037037").compareTo(line.change!!.setScale(7, java.math.RoundingMode.HALF_UP)))
    }

    @Test fun `cross rate between two non-USD currencies`() {
        val line = DigestBuilder.build(listOf(CurrencyPair("EUR", "RUB")), today, yesterday).lines.single()
        assertEquals("90,33", DigestBuilder.formatRate(line.rate, ru))
    }

    @Test fun `no yesterday rates means no change`() =
        assertNull(DigestBuilder.build(listOf(usdRub), today, null).lines.single().change)

    @Test fun `pair missing from rates is skipped`() =
        assertTrue(DigestBuilder.build(listOf(CurrencyPair("USD", "XYZ")), today, yesterday).lines.isEmpty())

    @Test fun `digest date is the rates date`() =
        assertEquals(LocalDate.of(2026, 10, 24), DigestBuilder.build(listOf(usdRub), today, yesterday).date)

    @Test fun `small rates keep significant digits`() =
        assertEquals("0,006667", DigestBuilder.formatRate(BigDecimal.ONE.divide(BigDecimal(150), java.math.MathContext.DECIMAL64), ru))

    @Test fun `change up, down and flat`() {
        assertEquals("▲ 0,4 %", plain(DigestBuilder.formatChange(BigDecimal("0.0037"), ru)))
        assertEquals("▼ 1,2 %", plain(DigestBuilder.formatChange(BigDecimal("-0.0121"), ru)))
        assertEquals("= 0 %", plain(DigestBuilder.formatChange(BigDecimal("0.00001"), ru)))
    }

    @Test fun `full line`() {
        val line = DigestBuilder.build(listOf(usdRub), today, yesterday).lines.single()
        assertEquals("USD → RUB 81,30  ▲ 0,4 %", plain(DigestBuilder.formatLine(line, ru)))
    }

    private val zone = ZoneId.of("Europe/Moscow")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 10, 24, h, m, 0, 0, zone)

    @Test fun `delay to later today`() =
        assertEquals(Duration.ofHours(2), DigestBuilder.delayUntil(at(7, 0), LocalTime.of(9, 0)))

    @Test fun `delay to tomorrow when the time has passed`() =
        assertEquals(Duration.ofHours(23), DigestBuilder.delayUntil(at(10, 0), LocalTime.of(9, 0)))

    @Test fun `exactly at the time schedules tomorrow`() =
        assertEquals(Duration.ofDays(1), DigestBuilder.delayUntil(at(9, 0), LocalTime.of(9, 0)))
}
