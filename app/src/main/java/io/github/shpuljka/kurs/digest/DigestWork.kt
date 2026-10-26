package io.github.shpuljka.kurs.digest

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.os.LocaleList
import androidx.work.BackoffPolicy
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Fetches today's and yesterday's rates and builds the digest. */
class DigestRepository(context: Context) {
    private val rates = RatesRepository(File(context.filesDir, "rates.json"))

    /** Null if no source answered and either [useCached] is false or nothing is cached. */
    suspend fun load(pairs: List<CurrencyPair>, useCached: Boolean): Digest? {
        val today = rates.refresh() ?: (if (useCached) rates.cached() else null) ?: return null
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
        .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
        .build()

    /** Retries come 5 and then 10 minutes apart, so the last attempt is ~15 minutes late (spec §6.3). */
    private const val RETRY_DELAY_MINUTES = 5L
}

class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    /**
     * Whatever goes wrong with today's digest, tomorrow's run is still queued: a failed or
     * crashed run would otherwise end the chain until the user touches the digest settings.
     */
    override suspend fun doWork(): Result {
        val prefs = AppPreferences(applicationContext)
        // Without settings we don't know the time; retrying (with backoff) keeps the chain alive.
        val settings = attempt { prefs.digestOrDefault() } ?: return Result.retry()
        if (!settings.enabled) return Result.success()
        // A network hiccup shouldn't turn into a push with cached rates: retry first, fall back to the cache last.
        val lastAttempt = runAttemptCount >= MAX_RETRIES
        val digest = attempt { DigestRepository(applicationContext).load(settings.pairs, useCached = lastAttempt) }
        if (digest == null && !lastAttempt) return Result.retry()
        digest?.let { attempt { DigestNotifier.show(localized(prefs), it) } }
        DigestScheduler.scheduleNext(applicationContext, settings.time)
        return Result.success()
    }

    /** Runs [block], turning failures (not cancellation) into null. */
    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Daily digest step failed", e)
        null
    }

    /**
     * Context in the app's language for notification text. On 13+ the system applies the
     * per-app language (also when changed in system settings); before that only activities get
     * AppCompat's language, so use the copy kept in preferences.
     */
    private suspend fun localized(prefs: AppPreferences): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return applicationContext
        val tag = prefs.language.first()
        if (tag.isEmpty()) return applicationContext
        val config = Configuration(applicationContext.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
        return applicationContext.createConfigurationContext(config)
    }

    private companion object {
        const val TAG = "DigestWorker"
        const val MAX_RETRIES = 2
    }
}
