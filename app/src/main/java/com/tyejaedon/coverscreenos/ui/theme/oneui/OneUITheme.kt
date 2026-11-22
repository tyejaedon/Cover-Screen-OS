package com.tyejaedon.coverscreenos.ui.theme.oneui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Samsung One UI 8 (Android 16) Color Tokens.
 *
 * Implements high-contrast tonal layering rather than stock Material 3 tints:
 * - Light Mode: Recessed cool light gray canvas (#F2F2F7) with pure white elevated card containers (#FFFFFF).
 * - Dark Mode: True OLED pure black (#000000) canvas with elevated dark charcoal cards (#17171A).
 * - Accent: Default Samsung Blue (#0381FE) with dynamic palette extraction on Android 12+.
 */
@Immutable
data class OneUIColors(
    val canvasBackground: Color,
    val cardSurface: Color,
    val cardSurfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val hairlineBorder: Color,
    val isDark: Boolean
)

/** Holds the default light-mode One UI palette pairing a cool `#F2F2F7` canvas with `#FFFFFF` cards. */
val OneUILightColors = OneUIColors(
    canvasBackground = Color(0xFFF2F2F7),
    cardSurface = Color(0xFFFFFFFF),
    cardSurfaceVariant = Color(0xFFE5E5EA),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0xFF6E6E73),
    accent = Color(0xFF0381FE),
    hairlineBorder = Color(0x1F000000),
    isDark = false
)

/** Holds the default OLED dark-mode One UI palette pairing true black (`#000000`) with charcoal (`#17171A`) cards. */
val OneUIDarkColors = OneUIColors(
    canvasBackground = Color(0xFF000000),
    cardSurface = Color(0xFF17171A),
    cardSurfaceVariant = Color(0xFF2C2C2E),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8E8E93),
    accent = Color(0xFF0381FE),
    hairlineBorder = Color(0x1FFFFFFF),
    isDark = true
)

/**
 * Samsung One UI 8 Spacing and Dimension Tokens.
 * Strict 8dp spatial grid with 4dp micro-grid.
 */
@Immutable
data class OneUIDimensions(
    /** Primary 8dp spatial grid step used across screen margins and section gaps. */
    val gridBase: Dp = 8.dp,
    /** Half-step 4dp micro-grid unit used for tight icon/label alignment. */
    val gridMicro: Dp = 4.dp,
    /** Horizontal screen margin (`16.dp`) on standard phone-sized displays. */
    val screenPaddingCompact: Dp = 16.dp,
    /** Wider horizontal screen margin (`24.dp`) on unfolded/tablet layouts (`>= 600dp`). */
    val screenPaddingExpanded: Dp = 24.dp,
    /** Standard `24.dp` rounded corner radius shared by One UI card groups. */
    val cardCornerRadius: Dp = 24.dp,
    /** Slightly rounder `26.dp` corner radius for featured or hero cards. */
    val cardCornerRadiusLarge: Dp = 26.dp,
    /** `28.dp` top corner radius applied to modal bottom sheets. */
    val sheetCornerRadius: Dp = 28.dp,
    /** Extra-large `999.dp` radius guaranteeing fully rounded stadium pills regardless of height. */
    val pillRadius: Dp = 999.dp,
    /** Maximum card column width (`680.dp`) so content stays centered and readable on large screens. */
    val maxCardWidth: Dp = 680.dp,
    /** Standard `60.dp` height of [OneUIFloatingNavBar][com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUIFloatingNavBar] on tall displays. */
    val floatingNavHeight: Dp = 60.dp,
    /** Standard `44.dp` height of the bottom-anchored [OneUINowPill][com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUINowPill]. */
    val nowPillHeight: Dp = 44.dp,
    /** Standard `24.dp` track thickness for [OneUIPillSlider][com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUIPillSlider]. */
    val sliderHeight: Dp = 24.dp,
    /** Standard `40.dp` diameter for circular [OneUIEncircledActionIcon][com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUIEncircledActionIcon] buttons. */
    val encircledIconSize: Dp = 40.dp,
    /** Vertical gap (`16.dp`) separating stacked [OneUICardGroup][com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUICardGroup] containers. */
    val cardSpacing: Dp = 16.dp,
    /** Left/right inset (`16.dp`) for [OneUIInternalDivider][com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUIInternalDivider] inside cards. */
    val internalDividerIndent: Dp = 16.dp
)

/**
 * Samsung One UI 8 Geometry and Shape Tokens.
 */
@Immutable
data class OneUIShapes(
    /** Holds the standard `24.dp` rounded-rectangle shape for grouped cards. */
    val card: Shape = RoundedCornerShape(24.dp),
    /** Holds the `26.dp` rounded-rectangle shape for larger hero cards. */
    val cardLarge: Shape = RoundedCornerShape(26.dp),
    /** Holds the top-rounded `28.dp` shape with flat bottom edges for anchored bottom sheets. */
    val bottomSheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    /** Holds [CircleShape] so pill bars and status badges automatically take full stadium ends. */
    val pill: Shape = CircleShape,
    /** Holds [CircleShape] for circular 40dp action icon containers. */
    val encircledIcon: Shape = CircleShape
)

/**
 * Samsung One UI 8 Typography Scale.
 * Clean humanist sans-serif scale emphasizing spatial hierarchy and viewing/interaction distinction.
 */
@Immutable
data class OneUITypography(
    /** Holds the `34.sp` light-weight expanded header title shown in the One UI Viewing Area. */
    val displayLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 34.sp,
        fontWeight = FontWeight.Light,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    /** Holds the `20.sp` semi-bold collapsed toolbar title shown once the header scrolls up. */
    val titleLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    /** Holds the `14.sp` semi-bold section label style rendered outside card headers. */
    val labelMedium: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    /** Holds the `17.sp` medium-weight primary body style for card setting titles and list rows. */
    val bodyLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    /** Holds the `13.sp` regular-weight supporting text style for descriptions, subtitles, and pill labels. */
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    )
)

/**
 * Holds the invisible theme channel (`staticCompositionLocalOf`) carrying the active [OneUIColors]
 * palette down to any nested UI widget without needing explicit parameter passing.
 * Uses `staticCompositionLocalOf` because theme palettes rarely change, avoiding per-widget tracking overhead.
 */
val LocalOneUIColors = staticCompositionLocalOf { OneUILightColors }

/** Holds the theme channel carrying [OneUIDimensions] spacing and component metrics down the UI tree. */
val LocalOneUIDimensions = staticCompositionLocalOf { OneUIDimensions() }

/** Holds the theme channel carrying [OneUIShapes] continuous corner and pill shapes down the UI tree. */
val LocalOneUIShapes = staticCompositionLocalOf { OneUIShapes() }

/** Holds the theme channel carrying [OneUITypography] text styles down the UI tree. */
val LocalOneUITypography = staticCompositionLocalOf { OneUITypography() }

/**
 * Accessor object for One UI 8 design tokens.
 */
object OneUITheme {
    /** Current [OneUIColors] resolved via `@ReadOnlyComposable` (reads the channel without emitting UI nodes). */
    val colors: OneUIColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOneUIColors.current

    /** Current [OneUIDimensions] resolved via `@ReadOnlyComposable` from [LocalOneUIDimensions]. */
    val dimensions: OneUIDimensions
        @Composable
        @ReadOnlyComposable
        get() = LocalOneUIDimensions.current

    /** Current [OneUIShapes] resolved via `@ReadOnlyComposable` from [LocalOneUIShapes]. */
    val shapes: OneUIShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalOneUIShapes.current

    /** Current [OneUITypography] resolved via `@ReadOnlyComposable` from [LocalOneUITypography]. */
    val typography: OneUITypography
        @Composable
        @ReadOnlyComposable
        get() = LocalOneUITypography.current
}

/**
 * One UI 8 Theme Provider Composable.
 *
 * Configures One UI design tokens and establishes a bridged Material 3 color scheme
 * so any embedded M3 widgets also respect One UI's OLED dark / cool gray light palette.
 * On Android 12+ (API 31+, [Build.VERSION_CODES.S]) when [dynamicColor] is enabled,
 * Material You's [dynamicDarkColorScheme] / [dynamicLightColorScheme] supplies the primary
 * wallpaper-derived accent while keeping One UI's crisp black/cool-gray surfaces intact.
 */
@Composable
fun OneUITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    dimensions: OneUIDimensions = OneUIDimensions(),
    shapes: OneUIShapes = OneUIShapes(),
    typography: OneUITypography = OneUITypography(),
    content: @Composable () -> Unit
) {
    // Android Context required on Android 12+ to ask the OS for wallpaper-derived Material You colors.
    val context = LocalContext.current
    // Wallpaper-extracted accent color (null on Android < 12 or when dynamicColor is false) so One UI adapts to the user's wallpaper.
    val dynamicAccent = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dynScheme.primary
    } else {
        null
    }

    // Default One UI palette (pure-black OLED dark or cool-gray light) before applying any dynamic accent.
    val baseColors = if (darkTheme) OneUIDarkColors else OneUILightColors
    // Final One UI color palette with the wallpaper accent swapped in when available.
    val effectiveColors = if (dynamicAccent != null) {
        baseColors.copy(accent = dynamicAccent)
    } else {
        baseColors
    }

    // Material 3 bridge: holds an M3 ColorScheme populated from effectiveColors so stock Material widgets match One UI.
    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = effectiveColors.accent,
            onPrimary = Color.White,
            primaryContainer = effectiveColors.cardSurfaceVariant,
            onPrimaryContainer = effectiveColors.textPrimary,
            surface = effectiveColors.cardSurface,
            onSurface = effectiveColors.textPrimary,
            surfaceVariant = effectiveColors.cardSurfaceVariant,
            onSurfaceVariant = effectiveColors.textSecondary,
            background = effectiveColors.canvasBackground,
            onBackground = effectiveColors.textPrimary,
            outline = effectiveColors.hairlineBorder
        )
    } else {
        lightColorScheme(
            primary = effectiveColors.accent,
            onPrimary = Color.White,
            primaryContainer = effectiveColors.cardSurfaceVariant,
            onPrimaryContainer = effectiveColors.textPrimary,
            surface = effectiveColors.cardSurface,
            onSurface = effectiveColors.textPrimary,
            surfaceVariant = effectiveColors.cardSurfaceVariant,
            onSurfaceVariant = effectiveColors.textSecondary,
            background = effectiveColors.canvasBackground,
            onBackground = effectiveColors.textPrimary,
            outline = effectiveColors.hairlineBorder
        )
    }

    // Bind One UI token locals down the composition tree, then wrap MaterialTheme for M3 compatibility.
    CompositionLocalProvider(
        LocalOneUIColors provides effectiveColors,
        LocalOneUIDimensions provides dimensions,
        LocalOneUIShapes provides shapes,
        LocalOneUITypography provides typography
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}