package io.github.drjkn7.shoestep.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drjkn7.shoestep.settings.SettingsStore
import io.github.drjkn7.shoestep.settings.ThemeMode

private val LocalSilverColors = staticCompositionLocalOf { LightSilverColors }
private val LocalSilverType = staticCompositionLocalOf { DefaultSilverType }

/** The Silver Matte tokens for the current theme: `SilverTheme.colors.card`, `SilverTheme.type.section`. */
object SilverTheme {
    val colors: SilverColors
        @Composable @ReadOnlyComposable get() = LocalSilverColors.current
    val type: SilverType
        @Composable @ReadOnlyComposable get() = LocalSilverType.current
    val shapes = SilverShapes
    val spacing = SilverSpacing
}

/**
 * The app theme. The look comes from the Silver Matte tokens; Material's colour scheme is filled from
 * the same tokens so Material components used underneath match. Dynamic colour (from the wallpaper)
 * is deliberately off.
 */
@Composable
fun ShoeStepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) NightSilverColors else LightSilverColors
    val type = DefaultSilverType
    CompositionLocalProvider(LocalSilverColors provides colors, LocalSilverType provides type) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = type.toMaterial(),
            shapes = SilverMaterialShapes,
            content = content,
        )
    }
}

/**
 * [ShoeStepTheme] following the user's choice in Settings → Appearance. A change applies straight away,
 * without a restart. Until the setting has been read (a moment at launch) it follows the phone.
 */
@Composable
fun ShoeStepTheme(settings: SettingsStore, content: @Composable () -> Unit) {
    val mode by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    ShoeStepTheme(darkTheme = mode.isDark(isSystemInDarkTheme()), content = content)
}

/**
 * Material roles from the tokens. "primary" is the accent ink rather than the gradient's purple,
 * because Material also uses it for text (text buttons, links), and only the ink is readable on
 * cards in both themes.
 */
private fun SilverColors.toMaterial(): ColorScheme {
    val base = if (isNight) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accentInk,
        onPrimary = if (isNight) LightSilverColors.textPrimary else textOnAccent,
        primaryContainer = inset,
        onPrimaryContainer = textPrimary,
        secondary = accentInk,
        onSecondary = if (isNight) LightSilverColors.textPrimary else textOnAccent,
        secondaryContainer = inset,
        onSecondaryContainer = textPrimary,
        tertiary = accentInk,
        background = backdrop.first().second,
        onBackground = textPrimary,
        surface = card,
        onSurface = textPrimary,
        surfaceVariant = inset,
        onSurfaceVariant = textMuted,
        surfaceTint = card,
        surfaceBright = card,
        surfaceDim = card,
        surfaceContainerLowest = card,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = dialog.first(),
        surfaceContainerHighest = dialog.first(),
        inverseSurface = textPrimary,
        inverseOnSurface = card,
        outline = textMuted,
        outlineVariant = divider,
        error = danger,
        onError = if (isNight) LightSilverColors.textPrimary else textOnAccent,
        errorContainer = inset,
        onErrorContainer = danger,
        scrim = scrim,
    )
}
