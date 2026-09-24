package com.tryniecki.appostazja

import android.app.Application
import com.tryniecki.appostazja.ui.theme.ThemePreferences

class AppostazjaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePreferences(this).applyPlatformNightMode()
    }
}
