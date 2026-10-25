package io.github.shpuljka.kurs

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.shpuljka.kurs.converter.ConverterRoute
import io.github.shpuljka.kurs.scan.ScannerRoute
import io.github.shpuljka.kurs.settings.SettingsRoute
import io.github.shpuljka.kurs.ui.theme.KursTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Theme and language changes recreate the activity; the open screen survives via rememberSaveable.
            var screen by rememberSaveable { mutableStateOf(Screen.Converter) }
            KursTheme {
                Crossfade(screen, label = "screen") { current ->
                    when (current) {
                        Screen.Converter -> ConverterRoute(
                            onSettings = { screen = Screen.Settings },
                            onScan = { screen = Screen.Scanner },
                        )
                        Screen.Settings -> {
                            BackHandler { screen = Screen.Converter }
                            SettingsRoute(onBack = { screen = Screen.Converter })
                        }
                        Screen.Scanner -> {
                            BackHandler { screen = Screen.Converter }
                            ScannerRoute(onClose = { screen = Screen.Converter })
                        }
                    }
                }
            }
        }
    }
}

private enum class Screen { Converter, Settings, Scanner }
