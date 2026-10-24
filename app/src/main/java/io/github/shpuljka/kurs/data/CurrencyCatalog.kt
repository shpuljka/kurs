package io.github.shpuljka.kurs.data

import java.util.Currency
import java.util.Locale

data class CurrencyInfo(val code: String, val name: String, private val searchText: String) {
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        return q.isEmpty() || q in searchText
    }
}

/** The list behind the currency picker, spec.md §2. */
object CurrencyCatalog {
    /**
     * Currencies some country uses today. Rate sources also list withdrawn ones (ITL, TRL),
     * metals and funds (XAU, XDR); none of those is any country's current currency.
     */
    private val CURRENT: Set<String> by lazy {
        Locale.getISOCountries().mapNotNullTo(HashSet()) { region ->
            runCatching { Currency.getInstance(Locale.Builder().setRegion(region).build())?.currencyCode }.getOrNull()
        }
    }

    /**
     * Currencies matching [query], best first: exact code, then code prefix, then the rest
     * (so "rub" puts RUB before Aruba's AWG). Order within a group is kept.
     */
    fun search(currencies: List<CurrencyInfo>, query: String): List<CurrencyInfo> {
        val q = query.trim().uppercase()
        return currencies.filter { it.matches(query) }.sortedBy {
            when {
                q.isEmpty() -> 0
                it.code == q -> 0
                it.code.startsWith(q) -> 1
                else -> 2
            }
        }
    }

    /** [codes] that are current currencies, sorted by code, with names in [locale]. */
    fun list(codes: Collection<String>, locale: Locale): List<CurrencyInfo> =
        codes.asSequence()
            .filter { it in CURRENT }
            .mapNotNull { code -> runCatching { Currency.getInstance(code) }.getOrNull() }
            .map { info(it, locale) }
            .sortedBy { it.code }
            .toList()

    private fun info(currency: Currency, locale: Locale): CurrencyInfo {
        val code = currency.currencyCode
        val name = currency.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
        // Issuer country (first two letters of the code) helps searches like "Турция" → TRY.
        val country = Locale.Builder().setRegion(code.take(2)).build()
        val search = listOf(
            code,
            name,
            currency.getDisplayName(Locale.ENGLISH),
            country.getDisplayCountry(locale),
            country.getDisplayCountry(Locale.ENGLISH),
        ).joinToString("\n").lowercase()
        return CurrencyInfo(code, name, search)
    }
}
