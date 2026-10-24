package io.github.shpuljka.kurs.digest

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.shpuljka.kurs.data.AppPreferences
import io.github.shpuljka.kurs.data.CurrencyPair
import io.github.shpuljka.kurs.data.DigestSettings
import io.github.shpuljka.kurs.data.RatesRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Fetches today's and yesterday's rates and builds the digest; shared by the worker and the settings preview. */
class DigestRepository(context: Context) {
    private val rates = RatesRepository(File(context.filesDir, "rates.json"))

    suspend fun load(pairs: List<CurrencyPair>): Digest? {
        val today = rates.refresh() ?: rates.cached() ?: return null
        return DigestBuilder.build(pairs, today, rates.history(today.date.minusDays(1)))
    }
}

/** Settings as stored, or the defaults derived from the converter rows. */
suspend fun AppPreferences.digestOrDefault(): DigestSettings {
    digest.first()?.let { return it }
    val codes = converter()?.model?.codes.orEmpty()
    return DigestSettings(pairs = DigestSettings.defaultPairs(codes))
}

/**
 * One run per day as a chain of one-time works: each run schedules the next for the chosen time,
 * so the schedule doesn't drift the way a 24 h periodic work would. Time is approximate (spec §6.3).
 */
object DigestScheduler {
    private const val WORK = "daily_digest"

    fun apply(context: Context, settings: DigestSettings) {
        val work = WorkManager.getInstance(context)
        if (settings.enabled) {
            work.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request(settings.time))
        } else {
            work.cancelUniqueWork(WORK)
        }
    }

    /** Called from the running worker: queue tomorrow's run after the current one finishes. */
    internal fun scheduleNext(context: Context, time: LocalTime) {
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request(time))
    }

    private fun request(time: LocalTime) = OneTimeWorkRequestBuilder<DigestWorker>()
        .setInitialDelay(DigestBuilder.delayUntil(ZonedDateTime.now(), time).toMillis(), TimeUnit.MILLISECONDS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
}

class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = AppPreferences(applicationContext).digestOrDefault()
        if (!settings.enabled) return Result.success()
        val digest = DigestRepository(applicationContext).load(settings.pairs)
        if (digest == null && runAttemptCount < MAX_ATTEMPTS) return Result.retry()
        digest?.let { DigestNotifier.show(applicationContext, it) }
        DigestScheduler.scheduleNext(applicationContext, settings.time)
        return Result.success()
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
