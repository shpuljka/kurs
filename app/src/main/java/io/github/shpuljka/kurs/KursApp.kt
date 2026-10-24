package io.github.shpuljka.kurs

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import io.github.shpuljka.kurs.data.AppPreferences
import io.github.shpuljka.kurs.digest.DigestNotifier
import io.github.shpuljka.kurs.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class KursApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Night mode must be set before the first activity inflates; a one-off small DataStore read.
        applyTheme(runBlocking { AppPreferences(this@KursApp).theme.first() })
        DigestNotifier.createChannel(this)
    }

    companion object {
        /** Spec §6.1. AppCompat flips the configuration, so Compose and system UI follow. */
        fun applyTheme(mode: ThemeMode) {
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    ThemeMode.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
                    ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
                },
            )
        }
    }
}
