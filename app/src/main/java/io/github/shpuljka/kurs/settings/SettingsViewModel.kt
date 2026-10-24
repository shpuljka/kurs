package io.github.shpuljka.kurs.settings

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.shpuljka.kurs.KursApp
import io.github.shpuljka.kurs.data.AppPreferences
import io.github.shpuljka.kurs.data.CurrencyPair
import io.github.shpuljka.kurs.data.DigestSettings
import io.github.shpuljka.kurs.data.Currencies
import io.github.shpuljka.kurs.data.Rates
import io.github.shpuljka.kurs.data.RatesRepository
import io.github.shpuljka.kurs.digest.Digest
import io.github.shpuljka.kurs.digest.DigestBuilder
import io.github.shpuljka.kurs.digest.DigestScheduler
import io.github.shpuljka.kurs.digest.digestOrDefault
import io.github.shpuljka.kurs.ui.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime

data class SettingsUiState(
    val theme: ThemeMode = ThemeMode.System,
    /** BCP 47 tag of the app language, empty for the system language. */
    val language: String = "",
    val digest: DigestSettings = DigestSettings(),
    val preview: Digest? = null,
    /** Currencies with rates, for the pair picker (popular ones until rates are known). */
    val codes: Set<String> = Currencies.POPULAR.toSet(),
    /** Stored settings are in; digest edits before that would overwrite them with defaults. */
    val loaded: Boolean = false,
)

class SettingsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = AppPreferences(app)
    private val rates = RatesRepository(File(app.filesDir, "rates.json"))
    private var previewJob: Job? = null
    /** Preview inputs: today's rates (cache first) and yesterday's, fetched at most once per visit. */
    private var today: Rates? = null
    private var yesterday: Rates? = null
    private var yesterdayLoaded = false

    private val _state = MutableStateFlow(SettingsUiState(language = AppCompatDelegate.getApplicationLocales().toLanguageTags()))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            today = rates.cached()
            val theme = prefs.theme.first()
            val digest = prefs.digestOrDefault()
            _state.update { it.copy(theme = theme, digest = digest, codes = codesOf(today), loaded = true) }
            loadPreview()
        }
    }

    fun setTheme(mode: ThemeMode) {
        _state.update { it.copy(theme = mode) }
        viewModelScope.launch { prefs.setTheme(mode) }
        KursApp.applyTheme(mode)
    }

    /** AppCompat stores it (system per-app language on 13+, its own storage before). */
    fun setLanguage(tag: String) {
        _state.update { it.copy(language = tag) }
        viewModelScope.launch { prefs.setLanguage(tag) }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun setDigestEnabled(enabled: Boolean) = updateDigest { it.copy(enabled = enabled) }

    fun setDigestTime(time: LocalTime) = updateDigest { it.copy(time = time) }

    fun addPair(pair: CurrencyPair) = updateDigest {
        if (pair.from == pair.to || pair in it.pairs || it.pairs.size >= DigestSettings.MAX_PAIRS) it
        else it.copy(pairs = it.pairs + pair)
    }

    fun removePair(pair: CurrencyPair) = updateDigest { it.copy(pairs = it.pairs - pair) }

    private fun updateDigest(change: (DigestSettings) -> DigestSettings) {
        if (!_state.value.loaded) return
        val before = _state.value.digest
        val after = change(before)
        if (after == before) return
        _state.update { it.copy(digest = after) }
        viewModelScope.launch { prefs.setDigest(after) }
        DigestScheduler.apply(app, after)
        if (after.pairs != before.pairs || after.enabled != before.enabled) loadPreview()
    }

    /** Only while the digest is on (the preview is hidden otherwise); network only when the cache lacks it. */
    private fun loadPreview() {
        previewJob?.cancel()
        if (!_state.value.digest.enabled) return
        previewJob = viewModelScope.launch {
            val now = today ?: rates.refresh()?.also { fetched ->
                today = fetched
                _state.update { it.copy(codes = codesOf(fetched)) }
            } ?: return@launch
            if (!yesterdayLoaded) {
                yesterday = rates.history(now.date.minusDays(1))
                yesterdayLoaded = true
            }
            _state.update { it.copy(preview = DigestBuilder.build(it.digest.pairs, now, yesterday)) }
        }
    }

    private fun codesOf(rates: Rates?): Set<String> = rates?.perUsd?.keys ?: Currencies.POPULAR.toSet()
}
