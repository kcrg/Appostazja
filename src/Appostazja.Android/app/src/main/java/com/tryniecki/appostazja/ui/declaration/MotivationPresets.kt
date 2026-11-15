package com.tryniecki.appostazja.ui.declaration

import androidx.annotation.StringRes
import com.tryniecki.appostazja.R

/** Ready-made reasons for the motivation field: a short chip label and the sentence it writes. */
internal enum class MotivationPreset(@StringRes val label: Int, @StringRes val text: Int) {
    NoFaith(R.string.motivation_preset_no_faith_label, R.string.motivation_preset_no_faith),
    Teaching(R.string.motivation_preset_teaching_label, R.string.motivation_preset_teaching),
    Institution(R.string.motivation_preset_institution_label, R.string.motivation_preset_institution),
}

internal const val MOTIVATION_MAX_LENGTH = 2_000

/**
 * The motivation after choosing [preset] among [presets]: it replaces an empty field or another ready-made
 * reason, and is added as a new paragraph after the person's own words, which are never thrown away. Choosing
 * a reason the text already contains changes nothing.
 */
internal fun withMotivationPreset(current: String, preset: String, presets: Collection<String>): String {
    val text = current.trim()
    return when {
        text.isEmpty() || text in presets -> preset
        preset in text -> current
        else -> "$text\n\n$preset"
    }
}
