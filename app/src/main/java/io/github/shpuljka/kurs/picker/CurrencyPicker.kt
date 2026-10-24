package io.github.shpuljka.kurs.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shpuljka.kurs.R
import io.github.shpuljka.kurs.data.CurrencyInfo
import io.github.shpuljka.kurs.ui.CurrencyFlag
import io.github.shpuljka.kurs.ui.theme.KursShapes
import io.github.shpuljka.kurs.ui.theme.KursTheme
import io.github.shpuljka.kurs.ui.theme.Nunito
import kotlinx.coroutines.launch

/**
 * Bottom sheet for choosing a currency, spec.md §2.
 * [selected] is the currency of the row being changed (null when adding a row).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPicker(
    currencies: List<CurrencyInfo>,
    recent: List<String>,
    selected: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.picker_title),
) {
    val c = KursTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: (String?) -> Unit = { code ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (code != null) onPick(code) else onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = KursShapes.sheet,
        containerColor = c.bg,
        contentColor = c.fg,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .size(width = 44.dp, height = 6.dp)
                    .clip(KursShapes.pill)
                    .background(c.tag),
            )
        },
    ) {
        PickerContent(title, currencies, recent, selected, onPick = { close(it) }, onClose = { close(null) })
    }
}

@Composable
private fun PickerContent(
    title: String,
    currencies: List<CurrencyInfo>,
    recent: List<String>,
    selected: String?,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    val c = KursTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    var crypto by rememberSaveable { mutableStateOf(false) }
    val filtered = remember(currencies, query) { currencies.filter { it.matches(query) } }
    val available = remember(currencies) { currencies.mapTo(HashSet()) { it.code } }
    val recentShown = recent.filter { it in available }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .imePadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(c.surface),
            ) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close), tint = c.fg)
            }
        }

        SearchField(query, onChange = { query = it })
        Tabs(crypto, onChange = { crypto = it })

        if (crypto) {
            CryptoPlaceholder()
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 2.dp, end = 2.dp, bottom = 24.dp),
            ) {
                if (query.isBlank() && recentShown.isNotEmpty()) {
                    item(key = "recent") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionLabel(stringResource(R.string.recent))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                recentShown.forEach { RecentPill(it, it == selected, onPick) }
                            }
                            SectionLabel(stringResource(R.string.all_currencies))
                        }
                    }
                }
                items(filtered, key = { it.code }) { CurrencyItem(it, it.code == selected, onPick) }
                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.nothing_found),
                            style = MaterialTheme.typography.labelLarge,
                            color = c.muted,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val c = KursTheme.colors
    val description = stringResource(R.string.search_currency)
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = c.fg),
        cursorBrush = SolidColor(c.accent),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        decorationBox = { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(6.dp, KursShapes.pill, ambientColor = c.shadow, spotColor = c.shadow)
                    .clip(KursShapes.pill)
                    .background(c.surface)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = c.muted)
                    }
                    field()
                }
            }
        },
    )
}

@Composable
private fun Tabs(crypto: Boolean, onChange: (Boolean) -> Unit) {
    val c = KursTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KursShapes.pill)
            .background(c.pad)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(false to R.string.tab_fiat, true to R.string.tab_crypto).forEach { (isCrypto, label) ->
            val on = crypto == isCrypto
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .then(if (on) Modifier.shadow(4.dp, KursShapes.pill, ambientColor = c.shadow, spotColor = c.shadow) else Modifier)
                    .clip(KursShapes.pill)
                    .background(if (on) c.surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onChange(isCrypto) }
                    .semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = if (on) c.accentText else c.muted)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = KursTheme.colors.muted,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

@Composable
private fun RecentPill(code: String, selected: Boolean, onPick: (String) -> Unit) {
    val c = KursTheme.colors
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(KursShapes.pill)
            .background(if (selected) c.accent else c.surface)
            .clickable(role = Role.Button) { onPick(code) }
            .padding(start = 6.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CurrencyFlag(code, 32.dp)
        Text(code, style = MaterialTheme.typography.labelLarge, color = if (selected) c.onAccent else c.fg)
    }
}

@Composable
private fun CurrencyItem(info: CurrencyInfo, selected: Boolean, onPick: (String) -> Unit) {
    val c = KursTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (selected) Modifier else Modifier.shadow(4.dp, KursShapes.row, ambientColor = c.shadow, spotColor = c.shadow))
            .clip(KursShapes.row)
            .background(if (selected) c.active else c.surface)
            .then(if (selected) Modifier.border(2.dp, c.accent, KursShapes.row) else Modifier)
            .clickable(role = Role.Button) { onPick(info.code) }
            .semantics { this.selected = selected }
            .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CurrencyFlag(info.code, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(info.code, style = MaterialTheme.typography.titleMedium, color = c.fg)
            Text(info.name, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        if (selected) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(c.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.selected), tint = c.onAccent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** v1 placeholder: crypto arrives with its own rates source, spec.md §10. */
@Composable
private fun CryptoPlaceholder() {
    val c = KursTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.crypto_soon_note),
            style = MaterialTheme.typography.labelLarge,
            color = c.accentText,
            modifier = Modifier
                .fillMaxWidth()
                .clip(KursShapes.row)
                .background(c.tag)
                .padding(18.dp),
        )
        listOf(
            Triple("BTC", "Bitcoin", "₿" to Color(0xFFC26A00)),
            Triple("ETH", "Ethereum", "Ξ" to Color(0xFF4A63D1)),
            Triple("USDT", "Tether", "₮" to Color(0xFF1E7F60)),
        ).forEach { (code, name, icon) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .alpha(0.6f)
                    .clip(KursShapes.row)
                    .background(c.surface)
                    .padding(start = 10.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(icon.second), contentAlignment = Alignment.Center) {
                    Text(icon.first, style = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White))
                }
                Column(Modifier.weight(1f)) {
                    Text(code, style = MaterialTheme.typography.titleMedium, color = c.fg)
                    Text(name, style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Text(stringResource(R.string.soon), style = MaterialTheme.typography.labelMedium, color = c.muted)
            }
        }
    }
}
