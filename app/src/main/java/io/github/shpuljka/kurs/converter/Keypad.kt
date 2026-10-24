package io.github.shpuljka.kurs.converter

import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shpuljka.kurs.R
import io.github.shpuljka.kurs.calc.Key
import io.github.shpuljka.kurs.ui.theme.KursShapes
import io.github.shpuljka.kurs.ui.theme.KursTheme
import io.github.shpuljka.kurs.ui.theme.Nunito
import java.text.DecimalFormatSymbols

private enum class Kind { Num, Fn, Op, Eq }

private data class KeySpec(
    val key: Key,
    val kind: Kind,
    val label: String,
    @StringRes val description: Int? = null,
    val span: Int = 1,
)

/** 4 × 5 keypad, spec.md §1.3. */
@Composable
fun Keypad(onKey: (Key) -> Unit, modifier: Modifier = Modifier) {
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KursShapes.keypad)
            .background(c.pad)
            .padding(vertical = 12.dp, horizontal = 7.dp),
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
