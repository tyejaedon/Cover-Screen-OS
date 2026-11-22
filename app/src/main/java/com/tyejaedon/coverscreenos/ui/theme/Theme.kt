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
import com.tyejaedon.coverscreenos.ui.theme.oneui.LocalOneUIColors
import com.tyejaedon.coverscreenos.ui.theme.oneui.LocalOneUIDimensions
import com.tyejaedon.coverscreenos.ui.theme.oneui.LocalOneUIShapes
import com.tyejaedon.coverscreenos.ui.theme.oneui.LocalOneUITypography
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUIDarkColors
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUIDimensions
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUILightColors
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUIShapes
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUITypography

// Holds the baseline dark Material 3 palette (true-black OLED backdrop + Galaxy indigo accents)
// used whenever dark mode is active and before any user accent override is applied.
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

// Holds the baseline light Material 3 palette pairing a cool off-white canvas with crisp white cards.
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
// staticCompositionLocalOf acts like a broadcast channel down the UI tree for rare settings changes
// without paying snapshot-tracking overhead on every single widget read.
val LocalCoverPanelCornerRadius = staticCompositionLocalOf { DEFAULT_PANEL_CORNER_RADIUS_DP.dp }

/**
 * Holds the active overlay accent color broadcast down the UI tree via [staticCompositionLocalOf].
 * Controls and chips read `.current` so they automatically match the user's chosen [AccentColor].
 */
val LocalCoverOverlayAccent = staticCompositionLocalOf { CoverOSPrimary }

/**
 * Holds the readable foreground text/icon color paired with [LocalCoverOverlayAccent] so labels
 * placed on solid accent buttons never become unreadable.
 */
val LocalCoverOverlayOnAccent = staticCompositionLocalOf { Color.Black }

/**
 * Holds an optional accent-tinted border color for overlay cards when a custom accent is chosen;
 * stays `null` on the default theme so cards keep their neutral outline border.
 */
val LocalCoverOverlayPanelBorder = staticCompositionLocalOf<Color?> { null }

/**
 * True when the user has explicitly picked a non-default accent color in settings. One UI's
 * "one signal color per page" pillar (see `ui/theme/oneui`) only applies its own per-page
 * accents when this is false — an explicit user choice always wins everywhere.
 */
val LocalCoverAccentIsCustomized = staticCompositionLocalOf { false }

/**
 * Builds the Material 3 [ColorScheme][androidx.compose.material3.ColorScheme] for the requested
 * [accent] and [dark] mode by swapping only the primary accent tokens onto the base canvas.
 */
internal fun coverAccentScheme(accent: AccentColor, dark: Boolean): androidx.compose.material3.ColorScheme {
    // Base light/dark canvas colors that remain untouched regardless of accent choice.
    val base = if (dark) CoverOSDarkColorScheme else CoverOSLightColorScheme
    if (accent == AccentColor.DEFAULT) return base
    // Four coordinated colors for the chosen accent: main color, soft card fill, and their two readable text colors.
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

/**
 * Root Compose theme for Cover-Screen-OS.
 *
 * Resolves [themePreference] (following [isSystemInDarkTheme] when set to [ThemePreference.SYSTEM]),
 * builds the Material 3 color scheme for [accentColor], and binds both One UI design tokens and
 * cover-overlay customization tokens into the tree via [CompositionLocalProvider] before rendering
 * [MaterialTheme].
 */
@Composable
fun CoverOSTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    accentColor: AccentColor = AccentColor.DEFAULT,
    panelCornerRadiusDp: Float = DEFAULT_PANEL_CORNER_RADIUS_DP,
    content: @Composable () -> Unit
) {
    // Final dark-mode switch resolved from user preference (System, Light, or Dark).
    val useDarkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    // Complete Material 3 color scheme with any custom user accent baked in.
    val scheme = coverAccentScheme(accentColor, useDarkTheme)
    // Baseline One UI surface/text palette matching the dark or light choice.
    val oneUiBaseColors = if (useDarkTheme) OneUIDarkColors else OneUILightColors
    // Updated One UI palette carrying the custom accent color so One UI widgets stay visually unified with MaterialTheme.
    val effectiveOneUiColors = if (accentColor != AccentColor.DEFAULT) {
        oneUiBaseColors.copy(accent = scheme.primary)
    } else {
        oneUiBaseColors
    }

    // Broadcast all One UI and cover-overlay theme values down to every composable inside `content`.
    CompositionLocalProvider(
        LocalOneUIColors provides effectiveOneUiColors,
        LocalOneUIDimensions provides OneUIDimensions(),
        LocalOneUIShapes provides OneUIShapes(),
        LocalOneUITypography provides OneUITypography(),
        LocalCoverPanelCornerRadius provides normalizePanelCornerRadius(panelCornerRadiusDp).dp,
        LocalCoverOverlayAccent provides if (accentColor == AccentColor.DEFAULT) CoverOSPrimary else scheme.primary,
        LocalCoverOverlayOnAccent provides if (accentColor == AccentColor.DEFAULT) Color.Black else scheme.onPrimary,
        LocalCoverOverlayPanelBorder provides
            if (accentColor == AccentColor.DEFAULT) null else scheme.primary.copy(alpha = 0.45f),
        LocalCoverAccentIsCustomized provides (accentColor != AccentColor.DEFAULT)
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = CoverOSTypography,
            content = content
        )
    }
}

/**
 * Compatibility alias delegating directly to [CoverOSTheme] for older components that
 * still reference `CoverScreenOSTheme`.
 */
// Alias maintained for backward compatibility with existing components
@Composable
fun CoverScreenOSTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) = CoverOSTheme(themePreference = themePreference, content = content)