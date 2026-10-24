package io.github.shpuljka.kurs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.shpuljka.kurs.converter.ConverterRoute
import io.github.shpuljka.kurs.ui.theme.KursTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KursTheme {
                ConverterRoute()
            }
        }
    }
}
