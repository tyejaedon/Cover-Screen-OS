package com.tyejaedon.coverscreenos.keyboard.floris

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

enum class FlorisThemeMode {
    AMOLED_COVER,
    MATERIAL_DYNAMIC
}

/**
 * FlorisBoard Snygg-inspired semantic theme tokens for `:keyboard-floris`.
 * Provides high-contrast AMOLED cover tokens (`#141416` surface, `#1F1F24` character keys,
 * `#2A2A30` functional keys, `#10B981` emerald action keys) alongside Material3 dynamic tokens.
 */
data class FlorisThemePalette(
    val mode: FlorisThemeMode = FlorisThemeMode.AMOLED_COVER,
    val keyboardSurface: Color,
    val keyBackground: Color,
    val keyForeground: Color,
    val keySecondaryForeground: Color,
    val functionalKeyBackground: Color,
    val functionalKeyForeground: Color,
    val shiftActiveBackground: Color,
    val shiftActiveForeground: Color,
    val shiftLockedBorder: Color,
    val actionKeyBackground: Color,
    val actionKeyForeground: Color,
    val destructiveKeyForeground: Color,
    val smartbarBackground: Color,
    val candidateChipBackground: Color,
    val candidateChipForeground: Color,
    val candidatePrimaryBackground: Color,
    val candidatePrimaryForeground: Color,
    val popupBubbleBackground: Color,
    val popupBubbleForeground: Color,
    val popupBubbleBorder: Color,
    val popupSelectedBackground: Color,
    val popupSelectedForeground: Color,
    val glideTrailPrimary: Color,
    val glideTrailGlow: Color
) {
    companion object {
        /**
         * High-contrast AMOLED Cover palette inspired by Samsung One UI Keyboard & Gboard dark:
         * deep `#111215` canvas, elevated `#272A30` letter keycaps, recessed `#1B1D22` modifiers,
         * and crisp `#3E7BFA` cobalt action keys.
         */
        val AmoledCover = FlorisThemePalette(
            mode = FlorisThemeMode.AMOLED_COVER,
            keyboardSurface = Color(0xFF111215),
            keyBackground = Color(0xFF272A30),
            keyForeground = Color(0xFFF3F4F6),
            keySecondaryForeground = Color(0xFF8E95A2),
            functionalKeyBackground = Color(0xFF1B1D22),
            functionalKeyForeground = Color(0xFFC8CDD6),
            shiftActiveBackground = Color(0xFF3E7BFA),
            shiftActiveForeground = Color(0xFFFFFFFF),
            shiftLockedBorder = Color(0xFF60A5FA),
            actionKeyBackground = Color(0xFF3E7BFA),
            actionKeyForeground = Color(0xFFFFFFFF),
            destructiveKeyForeground = Color(0xFFF87171),
            smartbarBackground = Color(0xFF111215),
            candidateChipBackground = Color.Transparent,
            candidateChipForeground = Color(0xFFD1D5DB),
            candidatePrimaryBackground = Color(0xFF272A30),
            candidatePrimaryForeground = Color(0xFFFFFFFF),
            popupBubbleBackground = Color(0xFF2F333B),
            popupBubbleForeground = Color(0xFFFFFFFF),
            popupBubbleBorder = Color(0xFF404550),
            popupSelectedBackground = Color(0xFF3E7BFA),
            popupSelectedForeground = Color(0xFFFFFFFF),
            glideTrailPrimary = Color(0xFF3E7BFA),
            glideTrailGlow = Color(0x553E7BFA)
        )

        /**
         * Adapts a Material3 [ColorScheme] into FlorisBoard Snygg semantic tokens.
         */
        fun fromMaterialColorScheme(colorScheme: ColorScheme): FlorisThemePalette = FlorisThemePalette(
            mode = FlorisThemeMode.MATERIAL_DYNAMIC,
            keyboardSurface = colorScheme.surfaceContainerLowest,
            keyBackground = colorScheme.surfaceContainerHigh,
            keyForeground = colorScheme.onSurface,
            keySecondaryForeground = colorScheme.onSurfaceVariant,
            functionalKeyBackground = colorScheme.secondaryContainer,
            functionalKeyForeground = colorScheme.onSecondaryContainer,
            shiftActiveBackground = colorScheme.primary,
            shiftActiveForeground = colorScheme.onPrimary,
            shiftLockedBorder = colorScheme.tertiary,
            actionKeyBackground = colorScheme.primary,
            actionKeyForeground = colorScheme.onPrimary,
            destructiveKeyForeground = Color(0xFFFF7043),
            smartbarBackground = colorScheme.surfaceContainerLow,
            candidateChipBackground = colorScheme.surfaceContainer,
            candidateChipForeground = colorScheme.onSurfaceVariant,
            candidatePrimaryBackground = colorScheme.primaryContainer,
            candidatePrimaryForeground = colorScheme.onPrimaryContainer,
            popupBubbleBackground = colorScheme.inverseSurface,
            popupBubbleForeground = colorScheme.inverseOnSurface,
            popupBubbleBorder = colorScheme.outlineVariant,
            popupSelectedBackground = colorScheme.primary,
            popupSelectedForeground = colorScheme.onPrimary,
            glideTrailPrimary = colorScheme.primary,
            glideTrailGlow = colorScheme.primary.copy(alpha = 0.38f)
        )
    }
}
