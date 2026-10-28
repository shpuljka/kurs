package io.github.shpuljka.kurs.converter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.shpuljka.kurs.calc.Key
import io.github.shpuljka.kurs.data.AppPreferences
import io.github.shpuljka.kurs.data.CountryDetector
import io.github.shpuljka.kurs.data.Rates
import io.github.shpuljka.kurs.data.RatesRepository
import io.github.shpuljka.kurs.data.SavedConverter
import io.github.shpuljka.kurs.scan.Frozen
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal

enum class RatesStatus {
    /** Fetching and nothing cached yet. */
    Loading,
    /** Fetching, showing cached rates meanwhile. */
    Refreshing,
    Fresh,
    /** Last fetch failed; showing cached rates. */
    Offline,
    /** Last fetch failed and there is no cache. */
    Unavailable,
}

data class ConverterUiState(
    val model: ConverterModel,
    val country: String?,
    /** Currency of [country]; a scanned price goes into its row (spec.md §12.5). */
    val local: String? = null,
    val rates: Rates? = null,
    val status: RatesStatus = RatesStatus.Loading,
    /** Most recently picked first; seeded with the rows on screen. */
    val recent: List<String> = model.codes,
    /** Keypad folded down to its handle (spec.md §1.3); null until restored, so the screen doesn't animate on launch. */
    val keypadCollapsed: Boolean? = null,
)

@OptIn(FlowPreview::class)
class ConverterViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = RatesRepository(File(app.filesDir, "rates.json"))
    private val prefs = AppPreferences(app)
    private val country = CountryDetector.country(app)
    private val local: String? = country?.let(CountryDetector::currencyOf)

    private val _state = MutableStateFlow(ConverterUiState(ConverterModel.initial(local ?: "USD"), country, local))
    val state: StateFlow<ConverterUiState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            restore()
            _state
                .map { SavedConverter(it.model, it.recent, local) }
                .distinctUntilChanged()
                .debounce(SAVE_DEBOUNCE_MS)
                .collect { prefs.saveConverter(it) }
        }
        viewModelScope.launch {
            _state.map { it.keypadCollapsed }.filterNotNull().distinctUntilChanged().drop(1).collect { prefs.setKeypadCollapsed(it) }
        }
        viewModelScope.launch {
            val cached = repository.cached()
            if (cached != null) _state.update { it.copy(rates = cached) }
            refresh()
        }
    }

    /** Rows from the last session; a new country's currency moves to the top (spec §3). */
    private suspend fun restore() {
        val collapsed = prefs.keypadCollapsed()
        _state.update { it.copy(keypadCollapsed = collapsed) }
        val saved = prefs.converter() ?: return
        val travelled = local != null && saved.lastLocal != null && saved.lastLocal != local
        val model = if (travelled) saved.model.withLocal(local!!) else saved.model
        _state.update { it.copy(model = model, recent = saved.recent.ifEmpty { model.codes }) }
    }

    /** Spec §4: on every launch and by pulling the currency list down. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        _state.update { it.copy(status = if (it.rates == null) RatesStatus.Loading else RatesStatus.Refreshing) }
        refreshJob = viewModelScope.launch {
            val fresh = repository.refresh()
            _state.update {
                when {
                    fresh != null -> it.copy(rates = fresh, status = RatesStatus.Fresh)
                    it.rates != null -> it.copy(status = RatesStatus.Offline)
                    else -> it.copy(status = RatesStatus.Unavailable)
                }
            }
        }
    }

    /** Pull to refresh: refreshes and returns when done, so the indicator can follow it. */
    suspend fun refreshAndWait() {
        refresh()
        refreshJob?.join()
    }

    fun press(key: Key) = updateModel { it.press(key) }

    fun moveCursor(position: Int) = updateModel(expand = true) { it.moveCursor(position) }

    fun selectAll() = updateModel(expand = true) { it.selectAll() }

    fun select(index: Int) = _state.update { it.withKeypad().copy(model = it.model.select(index, it.rates)) }

    /** The handle was dragged or tapped. */
    fun setKeypadCollapsed(collapsed: Boolean) = _state.update { it.copy(keypadCollapsed = collapsed) }

    /** Gallery photo open when the scanner was last left; shown again on the next open. Memory only. */
    var lastScanPhoto: Frozen? = null

    /** A price from the scanner, spec.md §12.5. */
    fun insertScanned(amount: BigDecimal) = updateModel(expand = true) { it.insertScanned(it.scanTarget(local), amount) }

    fun remove(index: Int) = _state.update { it.copy(model = it.model.remove(index, it.rates)) }

    /** From the currency picker: replace row [index], or add a row when [index] is null. */
    fun pick(index: Int?, code: String) = _state.update {
        it.copy(
            model = if (index == null) it.model.add(code) else it.model.replace(index, code),
            recent = (listOf(code) + it.recent).distinct().take(RECENT_LIMIT),
        )
    }

    private companion object {
        const val RECENT_LIMIT = 6
        const val SAVE_DEBOUNCE_MS = 300L
    }

    /** [expand]: the user is about to type (a tap on an amount, a scanned price), so the keypad unfolds. */
    private inline fun updateModel(expand: Boolean = false, crossinline change: (ConverterModel) -> ConverterModel) =
        _state.update { (if (expand) it.withKeypad() else it).copy(model = change(it.model)) }
}

/** The user is about to type: a folded keypad unfolds; one not restored yet stays as it is. */
internal fun ConverterUiState.withKeypad() = if (keypadCollapsed == true) copy(keypadCollapsed = false) else this
