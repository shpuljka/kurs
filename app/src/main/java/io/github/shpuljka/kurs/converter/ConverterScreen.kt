package io.github.shpuljka.kurs.converter

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
fun ConverterRoute(onSettings: () -> Unit, onScan: () -> Unit, viewModel: ConverterViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Row index whose currency is being changed, ADD_ROW when adding, null when the picker is closed.
    var pickerFor by rememberSaveable { mutableStateOf<Int?>(null) }
    ConverterScreen(
        state = state,
        onKey = viewModel::press,
        onSelect = viewModel::select,
        onCursor = viewModel::moveCursor,
        onSelectAll = viewModel::selectAll,
        onKeypadCollapsedChange = viewModel::setKeypadCollapsed,
        onRemove = viewModel::remove,
        onChipClick = { pickerFor = it },
        onAdd = { pickerFor = ADD_ROW },
        onRefresh = viewModel::refreshAndWait,
        onSettings = onSettings,
        onScan = onScan,
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
    onCursor: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onKeypadCollapsedChange: (Boolean) -> Unit,
    onRemove: (Int) -> Unit,
    onChipClick: (Int) -> Unit,
    onAdd: () -> Unit,
    onRefresh: suspend () -> Unit,
    onSettings: () -> Unit,
    onScan: () -> Unit,
) {
    val c = KursTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { AmountFormat(locale) }
    val model = state.model
    val values = model.values(state.rates)
    // Same instance while the expression is unchanged, so the cursor's effects and tap detector don't restart.
    val editable = remember(format, model.expr) { format.editable(model.expr) }
    val noValue = stringResource(R.string.no_value)
    val pullState = rememberPullToRefreshState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding()
            .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val scope = rememberCoroutineScope()
        // Spinner only for a pull; the automatic refresh on launch shows in the rates line.
        var pulling by remember { mutableStateOf(false) }
        val refresh: () -> Unit = {
            scope.launch {
                pulling = true
                try { onRefresh() } finally { pulling = false }
            }
        }
        Header(state, locale, onRefresh = refresh, onScan = onScan, onSettings = onSettings)

        // Pull down on the rows to refresh rates (spec.md §1.4).
        PullToRefreshBox(
            isRefreshing = pulling,
            onRefresh = refresh,
            modifier = Modifier.weight(1f),
            state = pullState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = pulling,
                    containerColor = c.surface,
                    color = c.accent,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(2.dp),
            ) {
                itemsIndexed(model.codes, key = { _, code -> code }) { index, code ->
                    val active = index == model.active
                    val value = values[index]
                    val amount = if (value == null) noValue else format.amount(value, Currencies.fractionDigits(code))
                    val input = if (active) AmountInput(editable, model.selStart, model.selEnd) else null
                    SwipeToRemove(enabled = model.canRemove, onRemove = { onRemove(index) }, modifier = Modifier.animateItem()) {
                        CurrencyRow(
                            code = code,
                            amount = amount,
                            input = input,
                            showsExpression = Calculator.hasOperation(model.expr),
                            onChipClick = { onChipClick(index) },
                            onSelect = { onSelect(index) },
                            onCursor = onCursor,
                            onSelectAll = onSelectAll,
                            // Nothing to type with while the keypad is folded.
                            showCursor = state.keypadCollapsed != true,
                        )
                    }
                }
                if (model.canAdd) {
                    item(key = "add") {
                        AddButton(onAdd, Modifier.animateItem())
                    }
                }
            }
        }

        Keypad(onKey = onKey, collapsed = state.keypadCollapsed, onCollapsedChange = onKeypadCollapsedChange)
    }
}

@Composable
private fun Header(state: ConverterUiState, locale: Locale, onRefresh: () -> Unit, onScan: () -> Unit, onSettings: () -> Unit) {
    val c = KursTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.converter_title), style = MaterialTheme.typography.headlineSmall, color = c.fg)
            // Country badge and rates date share the second line; a long date ("… · offline") wraps under the badge.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.country != null) {
                    val name = remember(state.country, locale) { Locale.Builder().setRegion(state.country).build().getDisplayCountry(locale) }
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.accentText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(KursShapes.pill)
                            .background(c.tag)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                RatesLine(state, locale, onRefresh)
            }
        }
        HeaderButton(R.drawable.ic_camera, stringResource(R.string.scan_open), onScan)
        HeaderButton(R.drawable.ic_menu, stringResource(R.string.settings), onSettings, Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun HeaderButton(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = KursTheme.colors
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .shadow(6.dp, KursShapes.pill, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(KursShapes.pill)
            .background(c.surface),
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = c.fg)
    }
}

@Composable
private fun SwipeToRemove(enabled: Boolean, onRemove: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val c = KursTheme.colors
    val dismiss = rememberSwipeToDismissBoxState()
    val remove by rememberUpdatedState(onRemove)
    LaunchedEffect(dismiss) {
        // The state is saved per row key (the currency code). A currency removed and later added
        // back restores "dismissed" — reset it instead of removing the row again.
        if (dismiss.currentValue != SwipeToDismissBoxValue.Settled) dismiss.snapTo(SwipeToDismissBoxValue.Settled)
        snapshotFlow { dismiss.currentValue }
            .filter { it == SwipeToDismissBoxValue.EndToStart }
            .collect { remove() }
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

/** Rates date under the title, spec.md §1.4. Refreshing is a pull on the rows; screen readers get it as an action here. */
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
    val refreshLabel = stringResource(R.string.refresh_rates)
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = c.muted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .semantics { customActions = listOf(CustomAccessibilityAction(refreshLabel) { onRefresh(); true }) },
    )
}

private fun formatDate(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale))
