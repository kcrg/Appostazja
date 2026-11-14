package com.tryniecki.appostazja

import android.app.Application
import android.os.Process

class AppostazjaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // androidx.pdf renders documents in an isolated sandbox process that also creates this class; it has no
        // access to SharedPreferences or the UI, so app setup only runs in the main process.
        if (Process.isIsolated()) return
        com.tryniecki.appostazja.ui.theme.ThemePreferences(this).applyPlatformNightMode()
    }
}
