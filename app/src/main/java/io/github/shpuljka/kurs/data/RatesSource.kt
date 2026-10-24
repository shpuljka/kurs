package io.github.shpuljka.kurs.data

import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency

/** One remote provider of USD-based rates, spec.md §4. */
interface RatesSource {
    fun fetch(): Rates
}

/** Parsers are separate from HTTP so they can be unit-tested. */
object RatesParser {
    /** ISO 4217 codes only: fawazahmed0 also lists crypto, which is out of scope for v1. */
    private val iso: Set<String> by lazy { Currency.getAvailableCurrencies().mapTo(HashSet()) { it.currencyCode } }

    /** `{"date":"2026-10-23","usd":{"eur":0.89,...}}` */
    fun fawazahmed(json: String): Rates {
        val root = JSONObject(json)
        val usd = root.getJSONObject("usd")
        val rates = usd.keys().asSequence()
            .map { it.uppercase() to usd.get(it).toString() }
            .filter { (code, _) -> code in iso }
            .associate { (code, value) -> code to BigDecimal(value) }
        return Rates(LocalDate.parse(root.getString("date")), rates + ("USD" to BigDecimal.ONE))
    }

    /** `{"result":"success","time_last_update_unix":1792800151,"rates":{"USD":1,...}}` */
    fun openErApi(json: String): Rates {
        val root = JSONObject(json)
        check(root.optString("result") == "success") { "open.er-api: ${root.optString("error-type")}" }
        val obj = root.getJSONObject("rates")
        val rates = obj.keys().asSequence().associateWith { BigDecimal(obj.get(it).toString()) }
        val date = Instant.ofEpochSecond(root.getLong("time_last_update_unix")).atZone(ZoneOffset.UTC).toLocalDate()
        return Rates(date, rates)
    }
}

private class HttpSource(private val url: String, private val parse: (String) -> Rates) : RatesSource {
    override fun fetch(): Rates {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "$url: HTTP ${connection.responseCode}" }
            return parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}

/** fawazahmed0 rates as of [date] (for the digest's day-over-day change); open.er-api has no history. */
fun historySources(date: LocalDate): List<RatesSource> = listOf(
    HttpSource("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$date/v1/currencies/usd.min.json", RatesParser::fawazahmed),
    HttpSource("https://$date.currency-api.pages.dev/v1/currencies/usd.min.json", RatesParser::fawazahmed),
)

/** Primary, its mirror, then the fallback provider. */
val DEFAULT_SOURCES: List<RatesSource> = listOf(
    HttpSource("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.min.json", RatesParser::fawazahmed),
    HttpSource("https://latest.currency-api.pages.dev/v1/currencies/usd.min.json", RatesParser::fawazahmed),
    HttpSource("https://open.er-api.com/v6/latest/USD", RatesParser::openErApi),
)
