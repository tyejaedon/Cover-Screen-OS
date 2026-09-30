package com.tyejaedon.coverscreenos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.datastore.AccentColor
import com.tyejaedon.coverscreenos.datastore.DEFAULT_PANEL_CORNER_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.ThemePreference
import com.tyejaedon.coverscreenos.datastore.normalizePanelCornerRadius

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

/** Overlay panels use this token; outside the launcher theme it retains the original 16dp shape. */
val LocalCoverPanelCornerRadius = staticCompositionLocalOf { DEFAULT_PANEL_CORNER_RADIUS_DP.dp }
val LocalCoverOverlayAccent = staticCompositionLocalOf { CoverOSPrimary }
val LocalCoverOverlayOnAccent = staticCompositionLocalOf { Color.Black }
val LocalCoverOverlayPanelBorder = staticCompositionLocalOf<Color?> { null }

internal fun coverAccentScheme(accent: AccentColor, dark: Boolean): androidx.compose.material3.ColorScheme {
    val base = if (dark) CoverOSDarkColorScheme else CoverOSLightColorScheme
    if (accent == AccentColor.DEFAULT) return base
    val (primary, container, onPrimary, onContainer) = when (accent) {
        AccentColor.MINT -> if (dark) listOf(CoverOSSecondary, CoverOSSecondaryContainer, CoverOSOnSecondary, Color(0xFFCBFFEA))
            else listOf(CoverOSLightSecondary, CoverOSLightSecondaryContainer, CoverOSLightOnSecondary, CoverOSLightOnSecondaryContainer)
        AccentColor.AMBER -> if (dark) listOf(CoverOSTertiary, CoverOSTertiaryContainer, CoverOSOnTertiary, Color(0xFFFFE1B9))
            else listOf(CoverOSLightTertiary, CoverOSLightTertiaryContainer, CoverOSLightOnTertiary, CoverOSLightOnTertiaryContainer)
        AccentColor.ROSE -> if (dark) listOf(Color(0xFFFFA8C5), Color(0xFF54253A), Color(0xFF3A1024), Color(0xFFFFD9E6))
            else listOf(Color(0xFFAD365F), Color(0xFFFFD9E6), Color.White, Color(0xFF481327))
        AccentColor.DEFAULT -> error("Default accent is handled above")
    }
    return base.copy(
        primary = primary, primaryContainer = container, onPrimary = onPrimary, onPrimaryContainer = onContainer
    )
}

@Composable
fun CoverOSTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    accentColor: AccentColor = AccentColor.DEFAULT,
    panelCornerRadiusDp: Float = DEFAULT_PANEL_CORNER_RADIUS_DP,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val scheme = coverAccentScheme(accentColor, useDarkTheme)
    CompositionLocalProvider(
        LocalCoverPanelCornerRadius provides normalizePanelCornerRadius(panelCornerRadiusDp).dp,
        LocalCoverOverlayAccent provides if (accentColor == AccentColor.DEFAULT) CoverOSPrimary else scheme.primary,
        LocalCoverOverlayOnAccent provides if (accentColor == AccentColor.DEFAULT) Color.Black else scheme.onPrimary,
        LocalCoverOverlayPanelBorder provides
            if (accentColor == AccentColor.DEFAULT) null else scheme.primary.copy(alpha = 0.45f)
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = CoverOSTypography,
            content = content
        )
    }
}

// Alias maintained for backward compatibility with existing components
@Composable
fun CoverScreenOSTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) = CoverOSTheme(themePreference = themePreference, content = content)