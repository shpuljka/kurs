package io.github.shpuljka.kurs.digest

import io.github.shpuljka.kurs.data.CurrencyPair
import io.github.shpuljka.kurs.data.Rates
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.Locale

/** One line of the daily push: `USD → RUB 81,30 ▲ 0,4 %`. [change] is a fraction (0.004 = 0.4 %). */
data class DigestLine(val pair: CurrencyPair, val rate: BigDecimal, val change: BigDecimal?)

data class Digest(val date: LocalDate, val lines: List<DigestLine>)

/** Pure logic of the daily digest, spec.md §6.3. */
object DigestBuilder {
    private val MC = MathContext.DECIMAL64

    fun build(pairs: List<CurrencyPair>, today: Rates, yesterday: Rates?): Digest = Digest(
        today.date,
        pairs.mapNotNull { pair ->
            val rate = today.convert(BigDecimal.ONE, pair.from, pair.to) ?: return@mapNotNull null
            val before = yesterday?.convert(BigDecimal.ONE, pair.from, pair.to)
            val change = before?.takeIf { it.signum() != 0 }?.let { rate.subtract(it, MC).divide(it, MC) }
            DigestLine(pair, rate, change)
        },
    )

    /** Rates ≥ 1 with 2 decimals; small ones (JPY → USD) with 4 significant digits. */
    fun formatRate(rate: BigDecimal, locale: Locale): String {
        val digits = if (rate >= BigDecimal.ONE) 2 else maxOf(2, 4 - rate.precision() + rate.scale())
        return NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            roundingMode = RoundingMode.HALF_UP
        }.format(rate)
    }

    /** `▲ 0,4 %`, `▼ 1,2 %`, or `= 0 %` when it rounds to zero. */
    fun formatChange(change: BigDecimal, locale: Locale): String {
        val rounded = change.setScale(3, RoundingMode.HALF_UP)
        val percent = NumberFormat.getPercentInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        return when (rounded.signum()) {
            1 -> "▲ " + percent.format(rounded)
            -1 -> "▼ " + percent.format(rounded.negate())
            else -> "= " + NumberFormat.getPercentInstance(locale).format(0)
        }
    }

    fun formatLine(line: DigestLine, locale: Locale): String =
        listOfNotNull("${line.pair} ${formatRate(line.rate, locale)}", line.change?.let { formatChange(it, locale) })
            .joinToString("  ")

    /** Time until the next [time] strictly after [now] (today if still ahead, else tomorrow). */
    fun delayUntil(now: ZonedDateTime, time: LocalTime): Duration {
        var next = now.with(time).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
