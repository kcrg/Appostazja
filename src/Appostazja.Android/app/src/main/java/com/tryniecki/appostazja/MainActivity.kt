package com.tryniecki.appostazja

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tryniecki.appostazja.ui.AppostazjaApp
import com.tryniecki.appostazja.ui.theme.AppostazjaTheme
import com.tryniecki.appostazja.ui.theme.ThemePreferences

class MainActivity : ComponentActivity() {
    private val container by lazy(LazyThreadSafetyMode.NONE) {
        AppContainer(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themePreferences = ThemePreferences(this)

        setContent {
            var themeMode by remember {
                mutableStateOf(themePreferences.themeMode)
            }

            AppostazjaTheme(themeMode = themeMode) {
                AppostazjaApp(
                    container = container,
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themePreferences.themeMode = mode
                        themeMode = mode
                    },
                )
            }
        }
    }
}
