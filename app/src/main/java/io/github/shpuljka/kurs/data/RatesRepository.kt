package io.github.shpuljka.kurs.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

/** Fetches rates from the first working source and keeps the last success on disk. */
class RatesRepository(
    private val cacheFile: File,
    private val sources: List<RatesSource> = DEFAULT_SOURCES,
    private val today: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) },
) {
    suspend fun cached(): Rates? = withContext(Dispatchers.IO) {
        runCatching { decode(cacheFile.readText()) }.getOrNull()
    }

    /**
     * Returns fresh rates, or null if every source failed. A CDN may serve yesterday's file for hours,
     * so when the first answer isn't dated today the other sources are asked too and the newest wins.
     * Rates older than the cached ones never replace them.
     */
    suspend fun refresh(): Rates? = withContext(Dispatchers.IO) {
        var newest: Rates? = null
        for (source in sources) {
            val rates = runCatching { source.fetch() }.getOrNull() ?: continue
            if (newest == null || rates.date > newest.date) newest = rates
            if (newest.date >= today()) break
        }
        if (newest == null) return@withContext null
        val cached = runCatching { decode(cacheFile.readText()) }.getOrNull()
        if (cached != null && cached.date > newest.date) return@withContext cached
        runCatching { cacheFile.writeText(encode(newest)) }
        newest
    }

    /** Rates as they were on [date], or null if unavailable. Not cached. */
    suspend fun history(date: LocalDate): Rates? = withContext(Dispatchers.IO) {
        historySources(date).firstNotNullOfOrNull { runCatching { it.fetch() }.getOrNull() }
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
