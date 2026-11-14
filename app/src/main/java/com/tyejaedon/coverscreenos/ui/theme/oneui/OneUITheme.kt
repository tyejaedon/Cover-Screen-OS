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
    val gridBase: Dp = 8.dp,
    val gridMicro: Dp = 4.dp,
    val screenPaddingCompact: Dp = 16.dp,
    val screenPaddingExpanded: Dp = 24.dp,
    val cardCornerRadius: Dp = 24.dp,
    val cardCornerRadiusLarge: Dp = 26.dp,
    val sheetCornerRadius: Dp = 28.dp,
    val pillRadius: Dp = 999.dp,
    val maxCardWidth: Dp = 680.dp,
    val floatingNavHeight: Dp = 60.dp,
    val nowPillHeight: Dp = 44.dp,
    val sliderHeight: Dp = 24.dp,
    val encircledIconSize: Dp = 40.dp,
    val cardSpacing: Dp = 16.dp,
    val internalDividerIndent: Dp = 16.dp
)

/**
 * Samsung One UI 8 Geometry and Shape Tokens.
 */
@Immutable
data class OneUIShapes(
    val card: Shape = RoundedCornerShape(24.dp),
    val cardLarge: Shape = RoundedCornerShape(26.dp),
    val bottomSheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val pill: Shape = CircleShape,
    val encircledIcon: Shape = CircleShape
)

/**
 * Samsung One UI 8 Typography Scale.
 * Clean humanist sans-serif scale emphasizing spatial hierarchy and viewing/interaction distinction.
 */
@Immutable
data class OneUITypography(
    val displayLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 34.sp,
        fontWeight = FontWeight.Light,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    val titleLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    val labelMedium: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    val bodyLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    )
)

val LocalOneUIColors = staticCompositionLocalOf { OneUILightColors }
val LocalOneUIDimensions = staticCompositionLocalOf { OneUIDimensions() }
val LocalOneUIShapes = staticCompositionLocalOf { OneUIShapes() }
val LocalOneUITypography = staticCompositionLocalOf { OneUITypography() }

/**
 * Accessor object for One UI 8 design tokens.
 */
object OneUITheme {
    val colors: OneUIColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOneUIColors.current

    val dimensions: OneUIDimensions
        @Composable
        @ReadOnlyComposable
        get() = LocalOneUIDimensions.current

    val shapes: OneUIShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalOneUIShapes.current

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
    val context = LocalContext.current
    val dynamicAccent = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dynScheme.primary
    } else {
        null
    }

    val baseColors = if (darkTheme) OneUIDarkColors else OneUILightColors
    val effectiveColors = if (dynamicAccent != null) {
        baseColors.copy(accent = dynamicAccent)
    } else {
        baseColors
    }

    // Material 3 bridge: maps One UI high-contrast surfaces into M3 ColorScheme
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
