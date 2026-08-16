package com.tyejaedon.coverscreenos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.tyejaedon.coverscreenos.datastore.ThemePreference

private val CoverOSDarkColorScheme = darkColorScheme(
    primary = CoverOSPrimary,
    onPrimary = CoverOSOnPrimary,
    primaryContainer = CoverOSPrimaryContainer,
    onPrimaryContainer = CoverOSOnPrimaryContainer,
    secondary = CoverOSSecondary,
    onSecondary = CoverOSOnSecondary,
    secondaryContainer = CoverOSSecondaryContainer,
    onSecondaryContainer = CoverOSOnSecondary,
    tertiary = CoverOSTertiary,
    onTertiary = CoverOSOnTertiary,
    tertiaryContainer = CoverOSTertiaryContainer,
    onTertiaryContainer = CoverOSOnTertiary,
    background = CoverOSDarkBackground,
    surface = CoverOSDarkSurface,
    surfaceVariant = CoverOSDarkSurfaceVariant,
    onBackground = CoverOSDarkOnBackground,
    onSurface = CoverOSDarkOnSurface,
    onSurfaceVariant = CoverOSDarkOnSurfaceVariant,
    outline = CoverOSDarkOutline
)

private val CoverOSLightColorScheme = lightColorScheme(
    primary = CoverOSLightPrimary,
    onPrimary = CoverOSLightOnPrimary,
    primaryContainer = CoverOSLightPrimaryContainer,
    onPrimaryContainer = CoverOSLightOnPrimaryContainer,
    secondary = CoverOSLightSecondary,
    onSecondary = CoverOSLightOnSecondary,
    secondaryContainer = CoverOSLightSecondaryContainer,
    onSecondaryContainer = CoverOSLightOnSecondaryContainer,
    tertiary = CoverOSLightTertiary,
    onTertiary = CoverOSLightOnTertiary,
    tertiaryContainer = CoverOSLightTertiaryContainer,
    onTertiaryContainer = CoverOSLightOnTertiaryContainer,
    background = CoverOSLightBackground,
    surface = CoverOSLightSurface,
    surfaceVariant = CoverOSLightSurfaceVariant,
    onBackground = CoverOSLightOnBackground,
    onSurface = CoverOSLightOnSurface,
    onSurfaceVariant = CoverOSLightOnSurfaceVariant,
    outline = CoverOSLightOutline
)

@Composable
fun CoverOSTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) CoverOSDarkColorScheme else CoverOSLightColorScheme,
        typography = CoverOSTypography,
        content = content
    )
}

// Alias maintained for backward compatibility with existing components
@Composable
fun CoverScreenOSTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) = CoverOSTheme(themePreference = themePreference, content = content)