package io.github.shpuljka.kurs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** User-selectable theme, spec.md §6.1. */
enum class ThemeMode { System, Light, Dark }

@Composable
fun KursTheme(
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (dark) DarkKursColors else LightKursColors
    // Material components still read colorScheme, so map our tokens onto it.
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.active,
        onPrimaryContainer = colors.fg,
        background = colors.bg,
        onBackground = colors.fg,
        surface = colors.surface,
        onSurface = colors.fg,
        onSurfaceVariant = colors.muted,
        surfaceVariant = colors.pad,
    )
    CompositionLocalProvider(LocalKursColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = KursTypography, content = content)
    }
}

object KursTheme {
    val colors: KursColors
        @Composable @ReadOnlyComposable
        get() = LocalKursColors.current
}
