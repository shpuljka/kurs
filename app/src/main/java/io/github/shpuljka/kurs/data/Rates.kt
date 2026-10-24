package io.github.shpuljka.kurs.data

import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.util.Currency

/** Exchange rates as units of each currency per 1 USD. */
data class Rates(val date: LocalDate, val perUsd: Map<String, BigDecimal>) {

    fun has(code: String): Boolean = code in perUsd

    fun convert(amount: BigDecimal, from: String, to: String): BigDecimal? {
        val fromRate = perUsd[from]?.takeIf { it.signum() > 0 } ?: return null
        val toRate = perUsd[to]?.takeIf { it.signum() > 0 } ?: return null
        return amount.divide(fromRate, MathContext.DECIMAL64).multiply(toRate, MathContext.DECIMAL64)
    }
}

object Currencies {
    /** Defaults after the local currency, spec.md §1.1. */
    val POPULAR = listOf("USD", "EUR", "GBP", "CNY", "TRY", "JPY", "KZT", "AED", "THB", "GEL")

    /** ISO 4217 minor units; 2 when unknown. */
    fun fractionDigits(code: String): Int =
        runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrNull()?.takeIf { it >= 0 } ?: 2
}
