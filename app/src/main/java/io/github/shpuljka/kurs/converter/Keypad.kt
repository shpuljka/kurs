package io.github.shpuljka.kurs.converter

import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shpuljka.kurs.R
import io.github.shpuljka.kurs.calc.Key
import io.github.shpuljka.kurs.ui.theme.KursShapes
import io.github.shpuljka.kurs.ui.theme.KursTheme
import io.github.shpuljka.kurs.ui.theme.Nunito
import java.text.DecimalFormatSymbols
import kotlin.math.roundToInt

private enum class Kind { Num, Fn, Op, Eq }

private data class KeySpec(
    val key: Key,
    val kind: Kind,
    val label: String,
    @StringRes val description: Int? = null,
    val span: Int = 1,
)

private enum class Fold { Open, Folded }

private val HANDLE_HEIGHT = 22.dp

/**
 * 4 × 5 keypad under a handle, spec.md §1.3. Dragging the handle folds the keys away below the
 * screen edge, following the finger; a tap on it toggles. [collapsed] is null until restored.
 */
@Composable
fun Keypad(onKey: (Key) -> Unit, collapsed: Boolean?, onCollapsedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator.toString()
    val rows = listOf(
        listOf(
            KeySpec(Key.Clear, Kind.Fn, "C", R.string.key_clear),
            KeySpec(Key.Percent, Kind.Fn, "%", R.string.key_percent),
            KeySpec(Key.Backspace, Kind.Fn, "", R.string.key_backspace),
            KeySpec(Key.Op('÷'), Kind.Op, "÷", R.string.key_divide),
        ),
        digits("789") + KeySpec(Key.Op('×'), Kind.Op, "×", R.string.key_multiply),
        digits("456") + KeySpec(Key.Op('-'), Kind.Op, "−", R.string.key_minus),
        digits("123") + KeySpec(Key.Op('+'), Kind.Op, "+", R.string.key_plus),
        listOf(
            KeySpec(Key.Digit('0'), Kind.Num, "0", span = 2),
            KeySpec(Key.Decimal, Kind.Num, separator, R.string.key_decimal),
            KeySpec(Key.Equals, Kind.Eq, "=", R.string.key_equals),
        ),
    )
    val c = KursTheme.colors
    val fold = remember { AnchoredDraggableState(Fold.Open) }
    val change by rememberUpdatedState(onCollapsedChange)
    // The restored state is snapped to; later changes (a tap on an amount, the scanner) animate.
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(collapsed) {
        val target = when (collapsed) {
            null -> return@LaunchedEffect
            true -> Fold.Folded
            false -> Fold.Open
        }
        if (!restored) {
            restored = true
            fold.snapTo(target)
        } else if (fold.settledValue != target) {
            fold.animateTo(target)
        }
    }
    LaunchedEffect(fold) {
        snapshotFlow { fold.settledValue }.collect { if (restored) change(it == Fold.Folded) }
    }
    val folded = fold.settledValue == Fold.Folded
    val handleLabel = stringResource(if (folded) R.string.keypad_expand else R.string.keypad_collapse)
    val handleColor = c.muted.copy(alpha = 0.45f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Clipped to the shrunk height, so the keys slide away under the bottom edge.
            .clip(KursShapes.keypad)
            .background(c.pad)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val handle = HANDLE_HEIGHT.toPx()
                val anchors = DraggableAnchors {
                    Fold.Open at 0f
                    Fold.Folded at (placeable.height - handle).coerceAtLeast(0f)
                }
                if (fold.anchors != anchors) fold.updateAnchors(anchors)
                val offset = fold.offset.takeUnless { it.isNaN() } ?: 0f
                layout(placeable.width, (placeable.height - offset).roundToInt()) { placeable.place(0, 0) }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HANDLE_HEIGHT)
                .anchoredDraggable(
                    fold,
                    reverseDirection = false,
                    orientation = Orientation.Vertical,
                    flingBehavior = AnchoredDraggableDefaults.flingBehavior(fold, positionalThreshold = { it / 3 }),
                )
                .clickable(role = Role.Button, onClickLabel = handleLabel, indication = null, interactionSource = null) { change(!folded) }
                .semantics { contentDescription = handleLabel }
                .drawBehind {
                    // Two bars a third of the width wide, one above the other.
                    val width = size.width / 3
                    val bar = 2.5.dp.toPx()
                    val gap = 3.5.dp.toPx()
                    val left = (size.width - width) / 2
                    val top = (size.height - 2 * bar - gap) / 2
                    listOf(top, top + bar + gap).forEach { y ->
                        drawRoundRect(handleColor, Offset(left, y), Size(width, bar), CornerRadius(bar / 2))
                    }
                },
        )
        Column(
            modifier = Modifier
                .padding(start = 7.dp, end = 7.dp, bottom = 12.dp)
                // Folded keys are out of sight: keep TalkBack off them too.
                .then(if (folded) Modifier.clearAndSetSemantics {} else Modifier),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rows.forEach { row ->
                // Gaps come from per-key padding, so a 2-wide key lines up with two keys plus their gap.
                Row {
                    row.forEach { spec -> KeyButton(spec, onKey, Modifier.weight(spec.span.toFloat())) }
                }
            }
        }
    }
}

private fun digits(chars: String) = chars.map { KeySpec(Key.Digit(it), Kind.Num, it.toString()) }

@Composable
private fun KeyButton(spec: KeySpec, onKey: (Key) -> Unit, modifier: Modifier) {
    val c = KursTheme.colors
    val view = LocalView.current
    val (bg, fg) = when (spec.kind) {
        Kind.Num -> c.surface to c.fg
        Kind.Fn -> c.keyFn to c.muted
        Kind.Op -> c.keyOp to c.accentText
        Kind.Eq -> c.accent to c.onAccent
    }
    val description = spec.description?.let { stringResource(it) }
    Box(
        modifier = modifier
            .padding(horizontal = 5.dp)
            .height(54.dp)
            .clip(KursShapes.pill)
            .background(bg)
            .clickable(role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onKey(spec.key)
            }
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (spec.key == Key.Backspace) {
            Icon(painterResource(R.drawable.ic_backspace), contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
        } else {
            Text(spec.label, style = keyText(fg))
        }
    }
}

private fun keyText(color: Color) = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = color)
