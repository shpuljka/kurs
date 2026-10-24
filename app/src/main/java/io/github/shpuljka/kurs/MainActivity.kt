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
import io.github.shpuljka.kurs.settings.SettingsRoute
import io.github.shpuljka.kurs.ui.theme.KursTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Theme and language changes recreate the activity; the open screen survives via rememberSaveable.
            var settingsOpen by rememberSaveable { mutableStateOf(false) }
            KursTheme {
                Crossfade(settingsOpen, label = "screen") { settings ->
                    if (settings) {
                        BackHandler { settingsOpen = false }
                        SettingsRoute(onBack = { settingsOpen = false })
                    } else {
                        ConverterRoute(onSettings = { settingsOpen = true })
                    }
                }
            }
        }
    }
}
