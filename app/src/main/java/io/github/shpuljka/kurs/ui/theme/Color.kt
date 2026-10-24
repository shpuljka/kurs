package io.github.shpuljka.kurs.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Design tokens of the «Мягкий» style, see spec.md §8.2. */
@Immutable
data class KursColors(
    val bg: Color,
    val surface: Color,
    val fg: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val active: Color,
    val tag: Color,
    val pad: Color,
    val keyFn: Color,
    val keyOp: Color,
    val shadow: Color,
    val up: Color,
    val down: Color,
    val isDark: Boolean,
)

val LightKursColors = KursColors(
    bg = Color(0xFFF4F1FB),
    surface = Color(0xFFFFFFFF),
    fg = Color(0xFF24203A),
    muted = Color(0xFF5F5A78),
    accent = Color(0xFF5B4BD6),
    onAccent = Color(0xFFFFFFFF),
    accentText = Color(0xFF4A3BC4),
    active = Color(0xFFE3DCFF),
    tag = Color(0xFFE8E3FF),
    pad = Color(0xFFEAE5F7),
    keyFn = Color(0xFFF6F3FF),
    keyOp = Color(0xFFDCD4FF),
    shadow = Color(0x1A5B4BD6),
    up = Color(0xFF2F62C9),
    down = Color(0xFFB04F12),
    isDark = false,
)

val DarkKursColors = KursColors(
    bg = Color(0xFF17151F),
    surface = Color(0xFF221F2D),
    fg = Color(0xFFEDEAF7),
    muted = Color(0xFFABA6C2),
    accent = Color(0xFFB3A8FF),
    onAccent = Color(0xFF17151F),
    accentText = Color(0xFFC3BAFF),
    active = Color(0xFF352D59),
    tag = Color(0xFF2F2946),
    pad = Color(0xFF1E1B28),
    keyFn = Color(0xFF24212F),
    keyOp = Color(0xFF3A3260),
    shadow = Color(0x4D000000),
    up = Color(0xFF93B6FF),
    down = Color(0xFFFFB07A),
    isDark = true,
)

val LocalKursColors = staticCompositionLocalOf { LightKursColors }
