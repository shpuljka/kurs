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

    private fun repository(vararg sources: RatesSource, fallback: List<RatesSource> = emptyList()) =
        RatesRepository(folder.root.resolve("rates.json"), sources.toList(), fallback) { today }

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

    @Test fun `the fallback isn't asked when a mirror answered, even with yesterday's rates`() = runBlocking {
        val fallback = Source(rates(today, "81"))
        assertEquals(yesterday, repository(Source(rates(yesterday)), fallback = listOf(fallback)).refresh()?.date)
        assertEquals(0, fallback.calls)
    }

    @Test fun `the fallback answers when no mirror did`() = runBlocking {
        val result = repository(Source(null), Source(null), fallback = listOf(Source(rates(today, "81")))).refresh()
        assertEquals(BigDecimal("81"), result?.perUsd?.get("RUB"))
    }

    @Test fun `a failing mirror between a stale and a fresh one is skipped`() = runBlocking {
        assertEquals(today, repository(Source(rates(yesterday)), Source(null), Source(rates(today))).refresh()?.date)
    }

    @Test fun `rates of the same date replace the cached ones`() = runBlocking {
        repository(Source(rates(today))).refresh()
        repository(Source(rates(today, "81"))).refresh()
        assertEquals(BigDecimal("81"), repository().cached()?.perUsd?.get("RUB"))
    }

    @Test fun `a corrupt cache doesn't block fresh rates`() = runBlocking {
        folder.root.resolve("rates.json").writeText("{not json")
        assertEquals(today, repository(Source(rates(today))).refresh()?.date)
        assertEquals(today, repository().cached()?.date)
    }

    @Test fun `every source failed`() = runBlocking {
        assertNull(repository(Source(null), Source(null), fallback = listOf(Source(null))).refresh())
    }
}
