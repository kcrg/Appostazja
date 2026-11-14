package com.tryniecki.appostazja.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

/**
 * Runs [content] with the standard (non-bouncy) motion scheme. Bottom sheets open with the theme's default
 * spatial spring; the expressive one overshoots visibly on a tall sheet, so sheets use this instead.
 */
@Composable
internal fun StandardMotion(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        motionScheme = MotionScheme.standard(),
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}
