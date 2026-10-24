package io.github.shpuljka.kurs.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.shpuljka.kurs.R
import io.github.shpuljka.kurs.data.CurrencyCatalog
import io.github.shpuljka.kurs.data.CurrencyPair
import io.github.shpuljka.kurs.data.DigestSettings
import io.github.shpuljka.kurs.digest.Digest
import io.github.shpuljka.kurs.digest.DigestBuilder
import io.github.shpuljka.kurs.digest.DigestNotifier
import io.github.shpuljka.kurs.picker.CurrencyPicker
import io.github.shpuljka.kurs.ui.theme.KursShapes
import io.github.shpuljka.kurs.ui.theme.KursTheme
import io.github.shpuljka.kurs.ui.theme.ThemeMode
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** App languages, spec.md §7: BCP 47 tag to the language's own name. */
private val LANGUAGES = listOf(
    "en" to "English",
    "ru" to "Русский",
    "es" to "Español",
    "pt" to "Português",
    "de" to "Deutsch",
    "fr" to "Français",
    "it" to "Italiano",
    "tr" to "Türkçe",
    "zh-CN" to "中文（简体）",
    "ja" to "日本語",
    "ko" to "한국어",
    "hi" to "हिन्दी",
)

@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.setDigestEnabled(true)
    }
    SettingsScreen(
        state = state,
        notificationsBlocked = permissionDenied || (state.digest.enabled && !DigestNotifier.canNotify(context)),
        onBack = onBack,
        onTheme = viewModel::setTheme,
        onLanguage = viewModel::setLanguage,
        onDigestEnabled = { enabled ->
            val needsPermission = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.setDigestEnabled(enabled)
        },
        onTime = viewModel::setDigestTime,
        onAddPair = viewModel::addPair,
        onRemovePair = viewModel::removePair,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    notificationsBlocked: Boolean,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onLanguage: (String) -> Unit,
    onDigestEnabled: (Boolean) -> Unit,
    onTime: (LocalTime) -> Unit,
    onAddPair: (CurrencyPair) -> Unit,
    onRemovePair: (CurrencyPair) -> Unit,
) {
    val c = KursTheme.colors
    var languageDialog by rememberSaveable { mutableStateOf(false) }
    var timeDialog by rememberSaveable { mutableStateOf(false) }
    // Adding a pair: null = closed, "" = picking the base, a code = picking the quote for it.
    var pairFrom by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .shadow(6.dp, CircleShape, ambientColor = c.shadow, spotColor = c.shadow)
                    .clip(CircleShape)
                    .background(c.surface),
            ) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back), tint = c.fg)
            }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall, color = c.fg)
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium, color = c.fg)
                ThemeSelector(state.theme, onTheme)
            }

            val languageName = LANGUAGES.firstOrNull { it.first == state.language }?.second ?: stringResource(R.string.language_system)
            ValueRow(stringResource(R.string.language), languageName, onClick = { languageDialog = true })

            DigestCard(
                digest = state.digest,
                preview = state.preview,
                blocked = notificationsBlocked,
                onEnabled = onDigestEnabled,
                onTimeClick = { timeDialog = true },
                onAddPair = { pairFrom = "" },
                onRemovePair = onRemovePair,
            )

            SoonRow(stringResource(R.string.rate_alert))
            InfoRow(stringResource(R.string.rates_update), stringResource(R.string.rates_update_on_launch))
        }
    }

    if (languageDialog) {
        LanguageDialog(state.language, onPick = { onLanguage(it); languageDialog = false }, onDismiss = { languageDialog = false })
    }
    if (timeDialog) {
        TimeDialog(state.digest.time, onPick = { onTime(it); timeDialog = false }, onDismiss = { timeDialog = false })
    }
    pairFrom?.let { from ->
        val locale = LocalConfiguration.current.locales[0]
        val currencies = remember(state.codes, locale, from) {
            CurrencyCatalog.list(state.codes, locale).filter { it.code != from }
        }
        CurrencyPicker(
            currencies = currencies,
            recent = emptyList(),
            selected = null,
            title = stringResource(if (from.isEmpty()) R.string.pick_base else R.string.pick_quote),
            onPick = { code ->
                if (from.isEmpty()) {
                    pairFrom = code
                } else {
                    onAddPair(CurrencyPair(from, code))
                    pairFrom = null
                }
            },
            onDismiss = { pairFrom = null },
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val c = KursTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, KursShapes.card, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(KursShapes.card)
            .background(c.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun ThemeSelector(theme: ThemeMode, onTheme: (ThemeMode) -> Unit) {
    val c = KursTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KursShapes.pill)
            .background(c.pad)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            ThemeMode.System to R.string.theme_system,
            ThemeMode.Light to R.string.theme_light,
            ThemeMode.Dark to R.string.theme_dark,
        ).forEach { (mode, label) ->
            val on = mode == theme
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .then(if (on) Modifier.shadow(4.dp, KursShapes.pill, ambientColor = c.shadow, spotColor = c.shadow) else Modifier)
                    .clip(KursShapes.pill)
                    .background(if (on) c.surface else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton) { onTheme(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = if (on) c.accentText else c.muted)
            }
        }
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    val c = KursTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .shadow(6.dp, KursShapes.card, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(KursShapes.card)
            .background(c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.fg, modifier = Modifier.weight(1f))
        Pill(value)
    }
}

@Composable
private fun Pill(text: String, onClick: (() -> Unit)? = null) {
    val c = KursTheme.colors
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = c.accentText,
        modifier = Modifier
            .clip(KursShapes.pill)
            .background(c.tag)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DigestCard(
    digest: DigestSettings,
    preview: Digest?,
    blocked: Boolean,
    onEnabled: (Boolean) -> Unit,
    onTimeClick: () -> Unit,
    onAddPair: () -> Unit,
    onRemovePair: (CurrencyPair) -> Unit,
) {
    val c = KursTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    Card {
        Row(
            modifier = Modifier.toggleable(value = digest.enabled, role = Role.Switch, onValueChange = onEnabled),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.digest_title), style = MaterialTheme.typography.titleMedium, color = c.fg)
                Text(stringResource(R.string.digest_subtitle), style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            SoftSwitch(digest.enabled)
        }
        if (blocked) {
            Text(stringResource(R.string.notifications_blocked), style = MaterialTheme.typography.bodySmall, color = c.down)
        }
        if (digest.enabled) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(formatTime(digest.time, locale), onClick = onTimeClick)
                digest.pairs.forEach { pair ->
                    val remove = stringResource(R.string.remove_pair, pair.toString())
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(KursShapes.pill)
                            .background(c.pad)
                            .clickable(role = Role.Button, onClickLabel = remove) { onRemovePair(pair) }
                            .padding(start = 16.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(pair.toString(), style = MaterialTheme.typography.labelLarge, color = c.fg)
                        Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = c.muted, modifier = Modifier.size(14.dp))
                    }
                }
                if (digest.pairs.size < DigestSettings.MAX_PAIRS) {
                    IconButton(
                        onClick = onAddPair,
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(c.pad),
                    ) {
                        Icon(painterResource(R.drawable.ic_plus), contentDescription = stringResource(R.string.add_pair), tint = c.accentText, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (preview != null && preview.lines.isNotEmpty()) {
                DigestPreview(preview, digest.time, locale)
            }
        }
    }
}

@Composable
private fun DigestPreview(preview: Digest, time: LocalTime, locale: Locale) {
    val c = KursTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KursShapes.row)
            .background(c.bg)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(KursShapes.row).background(c.accent), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_notification), contentDescription = null, tint = c.onAccent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.digest_preview, stringResource(R.string.app_name), formatTime(time, locale)),
                style = MaterialTheme.typography.labelSmall,
                color = c.muted,
            )
            preview.lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${line.pair} ${DigestBuilder.formatRate(line.rate, locale)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = c.fg,
                        modifier = Modifier.weight(1f),
                    )
                    line.change?.let { change ->
                        Text(
                            DigestBuilder.formatChange(change, locale),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (change.signum() < 0) c.down else c.up,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoftSwitch(on: Boolean) {
    val c = KursTheme.colors
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 34.dp)
            .clip(KursShapes.pill)
            .background(if (on) c.accent else c.muted.copy(alpha = 0.35f))
            .padding(4.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(26.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun SoonRow(title: String) {
    val c = KursTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .drawBehind {
                drawRoundRect(
                    color = c.tag,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    cornerRadius = CornerRadius(28.dp.toPx()),
                )
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.muted, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.soon), style = MaterialTheme.typography.labelMedium, color = c.muted)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    val c = KursTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .shadow(6.dp, KursShapes.card, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(KursShapes.card)
            .background(c.surface)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.fg, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
}

@Composable
private fun LanguageDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = KursTheme.colors
    val options = listOf("" to stringResource(R.string.language_system)) + LANGUAGES
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(stringResource(R.string.language), color = c.fg) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (tag, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = tag == current, role = Role.RadioButton) { onPick(tag) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = tag == current,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = c.muted),
                        )
                        Text(name, style = MaterialTheme.typography.bodyLarge, color = c.fg, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.accentText) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(time: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val c = KursTheme.colors
    val context = LocalContext.current
    val picker = rememberTimePickerState(time.hour, time.minute, is24Hour = DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(stringResource(R.string.digest_time), color = c.fg) },
        text = {
            TimePicker(
                state = picker,
                colors = TimePickerDefaults.colors(
                    clockDialColor = c.pad,
                    selectorColor = c.accent,
                    timeSelectorSelectedContainerColor = c.active,
                    timeSelectorUnselectedContainerColor = c.pad,
                    timeSelectorSelectedContentColor = c.fg,
                    timeSelectorUnselectedContentColor = c.fg,
                    periodSelectorSelectedContainerColor = c.active,
                    periodSelectorBorderColor = c.tag,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(picker.hour, picker.minute)) }) {
                Text(stringResource(R.string.ok), color = c.accentText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.accentText) } },
    )
}

private fun formatTime(time: LocalTime, locale: Locale): String =
    time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
