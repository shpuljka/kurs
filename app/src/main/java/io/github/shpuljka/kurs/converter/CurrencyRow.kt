package io.github.shpuljka.kurs.converter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import io.github.shpuljka.kurs.data.Currencies
import io.github.shpuljka.kurs.ui.theme.AmountTextStyle
import io.github.shpuljka.kurs.ui.theme.KursShapes
import io.github.shpuljka.kurs.ui.theme.KursTheme
import io.github.shpuljka.kurs.ui.theme.Nunito

/** One currency row: picker chip on the start side, amount on the end side. spec.md §1.1, §8.3. */
@Composable
fun CurrencyRow(
    code: String,
    amount: String,
    expression: String?,
    active: Boolean,
    onChipClick: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KursTheme.colors
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
            Flag(code)
            Text(code, style = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = c.fg))
            Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, tint = c.fg, modifier = Modifier.size(14.dp))
        }

        val typeDescription = stringResource(R.string.type_into, code)
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onSelect)
                .semantics { contentDescription = typeDescription },
            horizontalAlignment = Alignment.End,
        ) {
            if (expression != null) {
                Text(
                    expression,
                    maxLines = 1,
                    style = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.accentText),
                )
            }
            BasicText(
                amount,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 28.sp),
                style = AmountTextStyle.copy(color = c.fg, textAlign = TextAlign.End),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Interim emoji flag in a circle; replaced by circle-flags later (spec.md §8.3). */
@Composable
private fun Flag(code: String) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(KursTheme.colors.pad)
            .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(Currencies.flagEmoji(code), fontSize = 20.sp)
    }
}
