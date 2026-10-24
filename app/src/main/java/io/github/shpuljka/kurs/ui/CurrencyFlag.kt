package io.github.shpuljka.kurs.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shpuljka.kurs.ui.theme.KursTheme
import io.github.shpuljka.kurs.ui.theme.Nunito

/** Round issuer flag with a white rim, spec.md §8.3. Currencies without one show their code. */
@Composable
fun CurrencyFlag(code: String, size: Dp, modifier: Modifier = Modifier) {
    val c = KursTheme.colors
    val shape = Modifier
        .size(size)
        .clip(CircleShape)
    val rim = Modifier.border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
    val flag = CURRENCY_FLAGS[code]
    if (flag != null) {
        Box(modifier.then(shape)) {
            Image(painterResource(flag), contentDescription = null, modifier = Modifier.size(size))
            Box(Modifier.size(size).then(rim))
        }
    } else {
        Box(modifier.then(shape).background(c.tag).then(rim), contentAlignment = Alignment.Center) {
            Text(
                code,
                style = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.3f).sp, color = c.accentText),
            )
        }
    }
}
