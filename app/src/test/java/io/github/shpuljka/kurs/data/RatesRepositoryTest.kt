package io.github.shpuljka.kurs.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigDecimal
import java.time.LocalDate

class RatesRepositoryTest {

    @get:Rule val folder = TemporaryFolder()

    private val today = LocalDate.of(2026, 10, 26)
    private val yesterday = today.minusDays(1)

    private class Source(private val rates: Rates?) : RatesSource {
        var calls = 0
        override fun fetch(): Rates {
            calls++
            return rates ?: error("offline")
        }
    }

    private fun rates(date: LocalDate, rub: String = "80") = Rates(date, mapOf("USD" to BigDecimal.ONE, "RUB" to BigDecimal(rub)))

    private fun repository(vararg sources: RatesSource) =
        RatesRepository(folder.root.resolve("rates.json"), sources.toList()) { today }

    @Test fun `stops at the first source with today's rates`() = runBlocking {
        val second = Source(rates(today, "81"))
        val result = repository(Source(rates(today)), second).refresh()
        assertEquals(today, result?.date)
        assertEquals(BigDecimal("80"), result?.perUsd?.get("RUB"))
        assertEquals(0, second.calls)
    }

    @Test fun `stale first answer, the next source has today's rates`() = runBlocking {
        val third = Source(rates(today))
        val result = repository(Source(rates(yesterday)), Source(rates(today, "81")), third).refresh()
        assertEquals(today, result?.date)
        assertEquals(BigDecimal("81"), result?.perUsd?.get("RUB"))
        assertEquals(0, third.calls)
    }

    @Test fun `nobody has today's rates, the newest wins and is cached`() = runBlocking {
        val repo = repository(Source(rates(yesterday.minusDays(1))), Source(null), Source(rates(yesterday)))
        assertEquals(yesterday, repo.refresh()?.date)
        assertEquals(yesterday, repo.cached()?.date)
    }

    @Test fun `older rates don't replace the cached ones`() = runBlocking {
        repository(Source(rates(today, "81"))).refresh()
        val result = repository(Source(rates(yesterday))).refresh()
        assertEquals(today, result?.date)
        assertEquals(BigDecimal("81"), result?.perUsd?.get("RUB"))
    }

    @Test fun `every source failed`() = runBlocking {
        assertNull(repository(Source(null), Source(null)).refresh())
    }
}
