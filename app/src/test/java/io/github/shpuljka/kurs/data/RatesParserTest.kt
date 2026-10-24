package io.github.shpuljka.kurs.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RatesParserTest {

    @Test fun `fawazahmed keeps ISO currencies, uppercases, drops crypto`() {
        val r = RatesParser.fawazahmed("""{"date":"2026-10-23","usd":{"rub":81.89073893,"eur":0.89342,"btc":1.2245401e-05,"usd":1}}""")
        assertEquals(LocalDate.of(2026, 10, 23), r.date)
        assertEquals(BigDecimal("81.89073893"), r.perUsd["RUB"])
        assertEquals(BigDecimal("0.89342"), r.perUsd["EUR"])
        assertEquals(0, BigDecimal.ONE.compareTo(r.perUsd["USD"]))
        assertFalse(r.has("BTC"))
    }

    @Test fun `open er-api`() {
        val r = RatesParser.openErApi("""{"result":"success","time_last_update_unix":1792800151,"rates":{"USD":1,"RUB":81.296693}}""")
        assertEquals(LocalDate.of(2026, 10, 24), r.date)
        assertEquals(BigDecimal("81.296693"), r.perUsd["RUB"])
    }

    @Test fun `open er-api error is rejected`() {
        assertThrows(IllegalStateException::class.java) {
            RatesParser.openErApi("""{"result":"error","error-type":"unsupported-code"}""")
        }
    }

    @Test fun `cache round trip`() {
        val rates = Rates(LocalDate.of(2026, 10, 23), mapOf("USD" to BigDecimal.ONE, "RUB" to BigDecimal("81.89073893")))
        assertEquals(rates, RatesRepository.decode(RatesRepository.encode(rates)))
    }
}
