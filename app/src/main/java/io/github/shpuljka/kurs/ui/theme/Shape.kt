package io.github.shpuljka.kurs.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Corner radii per spec.md §8.3. */
object KursShapes {
    val row = RoundedCornerShape(26.dp)
    val pill = RoundedCornerShape(percent = 50)
    val keypad = RoundedCornerShape(30.dp)
    val sheet = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    val card = RoundedCornerShape(28.dp)
}
