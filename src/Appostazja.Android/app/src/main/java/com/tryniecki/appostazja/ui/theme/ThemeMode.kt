package com.tryniecki.appostazja.ui.theme

import androidx.annotation.StringRes
import com.tryniecki.appostazja.R

enum class ThemeMode(@StringRes val labelResId: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
}
