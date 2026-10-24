package io.github.shpuljka.kurs.converter

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.shpuljka.kurs.R
import io.github.shpuljka.kurs.calc.AmountFormat
import io.github.shpuljka.kurs.calc.Calculator
import io.github.shpuljka.kurs.data.Currencies
import io.github.shpuljka.kurs.data.CurrencyCatalog
import io.github.shpuljka.kurs.picker.CurrencyPicker
import io.github.shpuljka.kurs.ui.theme.KursShapes
import io.github.shpuljka.kurs.ui.theme.KursTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ConverterRoute(onSettings: () -> Unit, viewModel: ConverterViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Row index whose currency is being changed, ADD_ROW when adding, null when the picker is closed.
    var pickerFor by rememberSaveable { mutableStateOf<Int?>(null) }
    ConverterScreen(
        state = state,
        onKey = viewModel::press,
        onSelect = viewModel::select,
        onRemove = viewModel::remove,
        onChipClick = { pickerFor = it },
        onAdd = { pickerFor = ADD_ROW },
        onRefresh = viewModel::refresh,
        onSettings = onSettings,
    )
    pickerFor?.let { target ->
        val adding = target == ADD_ROW
        val locale = LocalConfiguration.current.locales[0]
        val onScreen = state.model.codes
        val codes = state.rates?.perUsd?.keys ?: (Currencies.POPULAR + onScreen).toSet()
        val currencies = remember(codes, locale, onScreen, adding) {
            // When adding, currencies already on screen are left out; when changing a row, picking one swaps rows.
            CurrencyCatalog.list(codes, locale).filter { !adding || it.code !in onScreen }
        }
        CurrencyPicker(
            currencies = currencies,
            recent = state.recent,
            selected = if (adding) null else onScreen.getOrNull(target),
            onPick = { code ->
                viewModel.pick(target.takeUnless { adding }, code)
                pickerFor = null
            },
            onDismiss = { pickerFor = null },
        )
    }
}

private const val ADD_ROW = -1

@Composable
fun ConverterScreen(
    state: ConverterUiState,
    onKey: (io.github.shpuljka.kurs.calc.Key) -> Unit,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onChipClick: (Int) -> Unit,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    val c = KursTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { AmountFormat(locale) }
    val model = state.model
    val values = model.values(state.rates)
    val noValue = stringResource(R.string.no_value)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding()
            .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(country = state.country, locale = locale, onSettings = onSettings)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(2.dp),
        ) {
            itemsIndexed(model.codes, key = { _, code -> code }) { index, code ->
                val active = index == model.active
                val value = values[index]
                val amount = when {
                    active && !Calculator.hasOperation(model.expr) -> format.typed(model.expr)
                    value == null -> noValue
                    else -> format.amount(value, Currencies.fractionDigits(code))
                }
                val expression = if (active && Calculator.hasOperation(model.expr)) format.expression(model.expr) else null
                SwipeToRemove(enabled = model.canRemove, onRemove = { onRemove(index) }, modifier = Modifier.animateItem()) {
                    CurrencyRow(
                        code = code,
                        amount = amount,
                        expression = expression,
                        active = active,
                        onChipClick = { onChipClick(index) },
                        onSelect = { onSelect(index) },
                    )
                }
            }
            if (model.canAdd) {
                item(key = "add") {
                    AddButton(onAdd, Modifier.animateItem())
                }
            }
        }

        RatesLine(state, locale, onRefresh)
        Keypad(onKey = onKey)
    }
}

@Composable
private fun Header(country: String?, locale: Locale, onSettings: () -> Unit) {
    val c = KursTheme.colors
    Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.converter_title), style = MaterialTheme.typography.headlineSmall, color = c.fg)
        if (country != null) {
            val name = remember(country, locale) { Locale.Builder().setRegion(country).build().getDisplayCountry(locale) }
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                color = c.accentText,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .clip(KursShapes.pill)
                    .background(c.tag)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Box(Modifier.weight(1f))
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .size(48.dp)
                .shadow(6.dp, KursShapes.pill, ambientColor = c.shadow, spotColor = c.shadow)
                .clip(KursShapes.pill)
                .background(c.surface),
        ) {
            Icon(painterResource(R.drawable.ic_menu), contentDescription = stringResource(R.string.settings), tint = c.fg)
        }
    }
}

@Composable
private fun SwipeToRemove(enabled: Boolean, onRemove: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val c = KursTheme.colors
    val dismiss = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismiss.currentValue) {
        if (dismiss.currentValue == SwipeToDismissBoxValue.EndToStart) onRemove()
    }
    SwipeToDismissBox(
        state = dismiss,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = enabled,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().clip(KursShapes.row).background(c.tag).padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.remove_currency), tint = c.accentText)
            }
        },
    ) { content() }
}

@Composable
private fun AddButton(onAdd: () -> Unit, modifier: Modifier) {
    val c = KursTheme.colors
    TextButton(
        onClick = onAdd,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(KursShapes.pill)
            .background(c.tag),
    ) {
        Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = c.accentText, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.add_currency),
            style = MaterialTheme.typography.labelLarge,
            color = c.accentText,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun RatesLine(state: ConverterUiState, locale: Locale, onRefresh: () -> Unit) {
    val c = KursTheme.colors
    val date = state.rates?.date?.let { formatDate(it, locale) }
    val text = when (state.status) {
        RatesStatus.Loading -> stringResource(R.string.rates_loading)
        RatesStatus.Unavailable -> stringResource(R.string.rates_unavailable)
        RatesStatus.Offline -> stringResource(R.string.rates_as_of, date.orEmpty()) + " · " + stringResource(R.string.rates_offline)
        RatesStatus.Fresh, RatesStatus.Refreshing -> stringResource(R.string.rates_as_of, date.orEmpty())
    }
    val busy = state.status == RatesStatus.Loading || state.status == RatesStatus.Refreshing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.weight(1f))
        IconButton(
            onClick = onRefresh,
            enabled = !busy,
            modifier = Modifier.size(48.dp),
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(KursShapes.pill).background(c.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_refresh),
                    contentDescription = stringResource(R.string.refresh_rates),
                    tint = if (busy) c.muted else c.accentText,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun formatDate(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale))
