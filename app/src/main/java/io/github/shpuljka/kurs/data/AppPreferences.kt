package io.github.shpuljka.kurs.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.shpuljka.kurs.converter.ConverterModel
import io.github.shpuljka.kurs.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

/** "USD → RUB" in the daily digest. */
data class CurrencyPair(val from: String, val to: String) {
    override fun toString() = "$from → $to"
}

/** Daily digest settings, spec.md §6.3. */
data class DigestSettings(
    val enabled: Boolean = false,
    val time: LocalTime = DEFAULT_TIME,
    val pairs: List<CurrencyPair> = emptyList(),
) {
    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)
        const val MAX_PAIRS = 5

        /** Rows 2 and 3 into the local currency, e.g. USD → RUB, EUR → RUB. */
        fun defaultPairs(codes: List<String>): List<CurrencyPair> =
            codes.drop(1).take(2).map { CurrencyPair(it, codes.first()) }
    }
}

data class SavedConverter(val model: ConverterModel, val recent: List<String>, val lastLocal: String?)

/** Compact string forms for preferences; pure so they can be unit-tested. */
object PrefsCodec {
    fun codes(list: List<String>) = list.joinToString(",")
    fun codes(text: String?) = text?.split(',')?.filter { it.matches(CODE) }.orEmpty()

    fun pairs(list: List<CurrencyPair>) = list.joinToString(",") { "${it.from}:${it.to}" }
    fun pairs(text: String?): List<CurrencyPair> = text?.split(',')?.mapNotNull { part ->
        val (from, to) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        if (from.matches(CODE) && to.matches(CODE)) CurrencyPair(from, to) else null
    }.orEmpty()

    private val CODE = Regex("[A-Z]{3}")
}

private val Context.dataStore by preferencesDataStore(name = "kurs")

class AppPreferences(context: Context) {
    private val store = context.applicationContext.dataStore

    val theme: Flow<ThemeMode> = store.data.map { prefs ->
        prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System
    }

    /** Null until the user has touched the digest settings (pairs then default to the rows). */
    val digest: Flow<DigestSettings?> = store.data.map { prefs ->
        if (prefs[DIGEST_PAIRS] == null && prefs[DIGEST_ENABLED] == null) return@map null
        DigestSettings(
            enabled = prefs[DIGEST_ENABLED] ?: false,
            time = prefs[DIGEST_MINUTES]?.let { LocalTime.of(it / 60, it % 60) } ?: DigestSettings.DEFAULT_TIME,
            pairs = PrefsCodec.pairs(prefs[DIGEST_PAIRS]),
        )
    }

    suspend fun converter(): SavedConverter? {
        val prefs = store.data.first()
        val codes = PrefsCodec.codes(prefs[ROWS]).distinct()
        if (codes.size < ConverterModel.MIN_ROWS) return null
        val active = (prefs[ACTIVE] ?: 0).coerceIn(codes.indices)
        val model = ConverterModel(codes.take(ConverterModel.MAX_ROWS), active, prefs[EXPR] ?: ConverterModel.START_AMOUNT)
        return SavedConverter(model, PrefsCodec.codes(prefs[RECENT]), prefs[LAST_LOCAL])
    }

    suspend fun saveConverter(saved: SavedConverter) {
        store.edit { prefs ->
            prefs[ROWS] = PrefsCodec.codes(saved.model.codes)
            prefs[ACTIVE] = saved.model.active
            prefs[EXPR] = saved.model.expr
            prefs[RECENT] = PrefsCodec.codes(saved.recent)
            saved.lastLocal?.let { prefs[LAST_LOCAL] = it }
        }
    }

    /**
     * App language tag ("" = system). AppCompat owns the real setting; this copy lets background
     * work localize notifications on Android 10–12, where AppCompat only localizes activities.
     */
    val language: Flow<String> = store.data.map { it[LANGUAGE].orEmpty() }

    suspend fun setLanguage(tag: String) {
        store.edit { it[LANGUAGE] = tag }
    }

    /** Keypad folded down to its handle, spec.md §1.3. */
    suspend fun keypadCollapsed(): Boolean = store.data.first()[KEYPAD_COLLAPSED] ?: false

    suspend fun setKeypadCollapsed(collapsed: Boolean) {
        store.edit { it[KEYPAD_COLLAPSED] = collapsed }
    }

    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    suspend fun setDigest(digest: DigestSettings) {
        store.edit { prefs ->
            prefs[DIGEST_ENABLED] = digest.enabled
            prefs[DIGEST_MINUTES] = digest.time.hour * 60 + digest.time.minute
            prefs[DIGEST_PAIRS] = PrefsCodec.pairs(digest.pairs)
        }
    }

    private companion object {
        val ROWS = stringPreferencesKey("rows")
        val ACTIVE = intPreferencesKey("active")
        val EXPR = stringPreferencesKey("expr")
        val RECENT = stringPreferencesKey("recent")
        val LAST_LOCAL = stringPreferencesKey("last_local")
        val THEME = stringPreferencesKey("theme")
        val KEYPAD_COLLAPSED = booleanPreferencesKey("keypad_collapsed")
        val LANGUAGE = stringPreferencesKey("language")
        val DIGEST_ENABLED = booleanPreferencesKey("digest_enabled")
        val DIGEST_MINUTES = intPreferencesKey("digest_minutes")
        val DIGEST_PAIRS = stringPreferencesKey("digest_pairs")
    }
}
