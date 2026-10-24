package io.github.shpuljka.kurs.converter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.shpuljka.kurs.calc.Key
import io.github.shpuljka.kurs.data.CountryDetector
import io.github.shpuljka.kurs.data.Rates
import io.github.shpuljka.kurs.data.RatesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

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
    val rates: Rates? = null,
    val status: RatesStatus = RatesStatus.Loading,
    /** Most recently picked first; seeded with the rows on screen. */
    val recent: List<String> = model.codes,
)

class ConverterViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = RatesRepository(File(app.filesDir, "rates.json"))

    private val _state = MutableStateFlow(
        CountryDetector.country(app).let { country ->
            val local = country?.let(CountryDetector::currencyOf) ?: "USD"
            ConverterUiState(ConverterModel.initial(local), country)
        },
    )
    val state: StateFlow<ConverterUiState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            val cached = repository.cached()
            if (cached != null) _state.update { it.copy(rates = cached) }
            refresh()
        }
    }

    /** Spec §4: on every launch and by the refresh button. */
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

    fun press(key: Key) = updateModel { it.press(key) }

    fun select(index: Int) = _state.update { it.copy(model = it.model.select(index, it.rates)) }

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
    }

    private inline fun updateModel(crossinline change: (ConverterModel) -> ConverterModel) =
        _state.update { it.copy(model = change(it.model)) }
}
