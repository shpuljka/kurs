package io.github.shpuljka.kurs.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

/** Fetches rates from the first working source and keeps the last success on disk. */
class RatesRepository(
    private val cacheFile: File,
    private val sources: List<RatesSource> = DEFAULT_SOURCES,
) {
    suspend fun cached(): Rates? = withContext(Dispatchers.IO) {
        runCatching { decode(cacheFile.readText()) }.getOrNull()
    }

    /** Returns fresh rates, or null if every source failed. */
    suspend fun refresh(): Rates? = withContext(Dispatchers.IO) {
        val rates = sources.firstNotNullOfOrNull { runCatching { it.fetch() }.getOrNull() } ?: return@withContext null
        runCatching { cacheFile.writeText(encode(rates)) }
        rates
    }

    companion object {
        fun encode(rates: Rates): String = JSONObject()
            .put("date", rates.date.toString())
            .put("rates", JSONObject(rates.perUsd.mapValues { it.value.toPlainString() }))
            .toString()

        fun decode(json: String): Rates {
            val root = JSONObject(json)
            val obj = root.getJSONObject("rates")
            return Rates(
                LocalDate.parse(root.getString("date")),
                obj.keys().asSequence().associateWith { BigDecimal(obj.getString(it)) },
            )
        }
    }
}
