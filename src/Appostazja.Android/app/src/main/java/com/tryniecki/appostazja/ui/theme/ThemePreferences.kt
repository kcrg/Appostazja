package com.tryniecki.appostazja.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build

class ThemePreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var themeMode: ThemeMode
        get() {
            val storedValue = preferences.getString(KEY_THEME_MODE, null)
            return ThemeMode.entries.firstOrNull { it.name == storedValue } ?: ThemeMode.SYSTEM
        }
        set(value) {
            preferences.edit()
                .putString(KEY_THEME_MODE, value.name)
                .apply()
            applyPlatformNightMode(value)
        }

    fun applyPlatformNightMode() {
        applyPlatformNightMode(themeMode)
    }

    private fun applyPlatformNightMode(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val uiModeManager = appContext.getSystemService(UiModeManager::class.java)
        uiModeManager.setApplicationNightMode(
            when (mode) {
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            },
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "appostazja_preferences"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
