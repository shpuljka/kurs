package io.github.shpuljka.kurs.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.shpuljka.kurs.R

private fun nunito(weight: Int) = Font(
    resId = R.font.nunito,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Nunito variable font; weights 600/700/800 per spec.md §8.1. */
val Nunito = FontFamily(nunito(500), nunito(600), nunito(700), nunito(800))

/** Tabular figures so amounts don't jitter while typing. */
private const val TABULAR = "tnum"

val KursTypography = Typography().let { base ->
    fun TextStyle.n(size: Int, weight: Int) = copy(
        fontFamily = Nunito,
        fontSize = size.sp,
        fontWeight = FontWeight(weight),
        fontFeatureSettings = TABULAR,
    )
    base.copy(
        headlineSmall = base.headlineSmall.n(24, 800),
        titleLarge = base.titleLarge.n(22, 800),
        titleMedium = base.titleMedium.n(16, 800),
        bodyLarge = base.bodyLarge.n(16, 600),
        bodyMedium = base.bodyMedium.n(14, 600),
        bodySmall = base.bodySmall.n(13, 600),
        labelLarge = base.labelLarge.n(15, 800),
        labelMedium = base.labelMedium.n(13, 700),
        labelSmall = base.labelSmall.n(12, 700),
    )
}

/** Amount in a currency row: 28sp / 800. */
val AmountTextStyle = TextStyle(
    fontFamily = Nunito,
    fontSize = 28.sp,
    fontWeight = FontWeight.ExtraBold,
    fontFeatureSettings = TABULAR,
)
