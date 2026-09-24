package com.tryniecki.appostazja.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = AppGreenLight,
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005137),
    onPrimaryContainer = Color(0xFF9DF5C7),
    secondary = Color(0xFFB2CCBF),
    onSecondary = Color(0xFF1E352A),
    secondaryContainer = Color(0xFF344C40),
    onSecondaryContainer = Color(0xFFCDE8DA),
    tertiary = Color(0xFFA6CEC3),
    onTertiary = Color(0xFF0E3730),
    tertiaryContainer = Color(0xFF285047),
    onTertiaryContainer = Color(0xFFC1EADF),
    background = AppDarkBackground,
    onBackground = AppDarkText,
    surface = AppDarkSurface,
    onSurface = AppDarkText,
    surfaceVariant = AppDarkSurface2,
    onSurfaceVariant = AppDarkMuted,
    surfaceDim = AppDarkBackground,
    surfaceBright = AppDarkSurface3,
    surfaceContainerLowest = AppDarkBackground,
    surfaceContainerLow = AppDarkSurface,
    surfaceContainer = AppDarkSurface2,
    surfaceContainerHigh = AppDarkSurface3,
    surfaceContainerHighest = Color(0xFF303C36),
    outline = AppDarkOutline,
    outlineVariant = AppDarkSurface3,
)

private val LightColors = lightColorScheme(
    primary = AppGreen,
    onPrimary = Color.White,
    primaryContainer = AppGreenContainer,
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4E6358),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E8DB),
    onSecondaryContainer = Color(0xFF0B2017),
    tertiary = Color(0xFF3D665D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC0ECE0),
    onTertiaryContainer = Color(0xFF00201A),
    background = AppLightBackground,
    onBackground = AppLightText,
    surface = AppLightSurface,
    onSurface = AppLightText,
    surfaceVariant = AppLightSurface2,
    onSurfaceVariant = AppLightMuted,
    surfaceDim = AppLightSurface3,
    surfaceBright = AppLightSurface,
    surfaceContainerLowest = AppLightSurface,
    surfaceContainerLow = AppLightBackground,
    surfaceContainer = AppLightSurface2,
    surfaceContainerHigh = AppLightSurface3,
    surfaceContainerHighest = Color(0xFFDDE4DF),
    outline = AppLightOutline,
    outlineVariant = AppLightSurface3,
)

@Composable
fun AppostazjaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDarkTheme
    }

    val context = LocalContext.current
    val colors = when (themeMode) {
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.SYSTEM -> {
            // Android 12+ follows Material You. Android 10/11 still follows the
            // system light/dark setting, but uses Appostazja's own green palette.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (systemDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else if (systemDarkTheme) {
                DarkColors
            } else {
                LightColors
            }
        }
    }

    val activity = context as? Activity
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !useDarkTheme
                isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        content = content,
    )
}
