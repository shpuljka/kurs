package io.github.shpuljka.kurs.converter

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import io.github.shpuljka.kurs.calc.Formatted
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shpuljka.kurs.R
import io.github.shpuljka.kurs.ui.CurrencyFlag
import io.github.shpuljka.kurs.ui.theme.AmountTextStyle
import io.github.shpuljka.kurs.ui.theme.KursShapes
import io.github.shpuljka.kurs.ui.theme.KursTheme
import io.github.shpuljka.kurs.ui.theme.Nunito

/** The active row's expression with its cursor/selection; offsets are into the raw expression. */
class AmountInput(val formatted: Formatted, val selStart: Int, val selEnd: Int) {
    val length: Int get() = formatted.rawToDisplay.size - 1
}

/**
 * One currency row: picker chip on the start side, amount on the end side. spec.md §1.1, §8.3.
 * The active row gets [input]: shown big while it is a plain number, or as the small line above
 * the result once it has an operation. Tapping it places the cursor, double tap selects all.
 */
@Composable
fun CurrencyRow(
    code: String,
    amount: String,
    input: AmountInput?,
    showsExpression: Boolean,
    onChipClick: () -> Unit,
    onSelect: () -> Unit,
    onCursor: (Int) -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
    showCursor: Boolean = true,
) {
    val c = KursTheme.colors
    val active = input != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .then(if (active) Modifier else Modifier.shadow(6.dp, KursShapes.row, ambientColor = c.shadow, spotColor = c.shadow))
            .clip(KursShapes.row)
            .background(if (active) c.active else c.surface)
            .then(if (active) Modifier.border(2.dp, c.accent, KursShapes.row) else Modifier)
            .padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val chipDescription = stringResource(R.string.change_currency, code)
        Row(
            modifier = Modifier
                .heightIn(min = 50.dp)
                .clip(KursShapes.pill)
                .background(if (active) c.surface else c.bg)
                .clickable(role = Role.Button, onClick = onChipClick)
                .semantics { contentDescription = chipDescription }
                .padding(start = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CurrencyFlag(code, 34.dp)
            Text(code, style = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = c.fg))
            Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, tint = c.fg, modifier = Modifier.size(14.dp))
        }

        val typeDescription = stringResource(R.string.type_into, code)
        val amountStyle = AmountTextStyle.copy(color = c.fg, textAlign = TextAlign.End)
        val amountSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 28.sp)
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (input == null) {
                        Modifier.clickable(role = Role.Button, onClick = onSelect).semantics { contentDescription = typeDescription }
                    } else {
                        // Taps beside the text put the cursor at the end.
                        Modifier.clickable(indication = null, interactionSource = null) { onCursor(input.length) }
                    },
                ),
            horizontalAlignment = Alignment.End,
        ) {
            when {
                input == null -> BasicText(amount, maxLines = 1, autoSize = amountSize, style = amountStyle, modifier = Modifier.fillMaxWidth())
                showsExpression -> {
                    EditableAmount(
                        input,
                        TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.accentText, textAlign = TextAlign.End),
                        autoSize = null,
                        onCursor = onCursor,
                        onSelectAll = onSelectAll,
                        showCursor = showCursor,
                    )
                    BasicText(amount, maxLines = 1, autoSize = amountSize, style = amountStyle, modifier = Modifier.fillMaxWidth())
                }
                else -> EditableAmount(input, amountStyle, amountSize, onCursor, onSelectAll, showCursor)
            }
        }
    }
}

/** Expression text with a blinking cursor or a highlighted selection, drawn from its text layout. */
@Composable
private fun EditableAmount(
    input: AmountInput,
    style: TextStyle,
    autoSize: TextAutoSize?,
    onCursor: (Int) -> Unit,
    onSelectAll: () -> Unit,
    /** False while the keypad is folded: neither the cursor nor the selection is drawn. */
    showCursor: Boolean = true,
) {
    val c = KursTheme.colors
    val f = input.formatted
    val empty = f.text.isEmpty()
    val text = if (empty) "0" else f.text
    val start = if (empty) 0 else f.rawToDisplay[input.selStart.coerceIn(0, input.length)]
    val end = if (empty) 1 else f.rawToDisplay[input.selEnd.coerceIn(0, input.length)]
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // Solid right after each edit or cursor move, then blinking, like a regular text field.
    val blink = remember { Animatable(1f) }
    LaunchedEffect(f, input.selStart, input.selEnd) {
        while (true) {
            blink.snapTo(1f)
            delay(500)
            blink.snapTo(0f)
            delay(500)
        }
    }
    BasicText(
        text,
        maxLines = 1,
        autoSize = autoSize,
        style = if (empty) style.copy(color = style.color.copy(alpha = 0.4f)) else style,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(f) {
                detectTapGestures(
                    onTap = { pos -> layout?.let { onCursor(if (empty) 0 else f.displayToRaw[it.getOffsetForPosition(pos)]) } },
                    onDoubleTap = { onSelectAll() },
                )
            }
            .drawWithContent {
                val l = layout
                if (l != null && showCursor && start != end) {
                    drawPath(l.getPathForRange(minOf(start, end), maxOf(start, end)), c.accent.copy(alpha = 0.28f))
                }
                drawContent()
                if (l != null && showCursor && start == end) {
                    val r = l.getCursorRect(start)
                    val w = 2.dp.toPx()
                    drawRect(c.accent.copy(alpha = blink.value), topLeft = Offset(r.left - w / 2, r.top), size = Size(w, r.height))
                }
            },
    )
}
