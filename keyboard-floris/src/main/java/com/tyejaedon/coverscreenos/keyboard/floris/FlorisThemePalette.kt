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
         * High-contrast AMOLED Cover palette tuned for outdoor legibility and OLED power savings
         * on small secondary displays (`#141416` surface, `#1F1F24` keys, `#2A2A30` modifiers,
         * `#10B981` accent action keys).
         */
        val AmoledCover = FlorisThemePalette(
            mode = FlorisThemeMode.AMOLED_COVER,
            keyboardSurface = Color(0xFF141416),
            keyBackground = Color(0xFF1F1F24),
            keyForeground = Color(0xFFF4F4F6),
            keySecondaryForeground = Color(0xFF9CA3AF),
            functionalKeyBackground = Color(0xFF2A2A30),
            functionalKeyForeground = Color(0xFFE5E7EB),
            shiftActiveBackground = Color(0xFF3B82F6),
            shiftActiveForeground = Color(0xFFFFFFFF),
            shiftLockedBorder = Color(0xFF10B981),
            actionKeyBackground = Color(0xFF10B981),
            actionKeyForeground = Color(0xFF06281E),
            destructiveKeyForeground = Color(0xFFFF7043),
            smartbarBackground = Color(0xFF18181C),
            candidateChipBackground = Color(0xFF232329),
            candidateChipForeground = Color(0xFFE5E7EB),
            candidatePrimaryBackground = Color(0xFF10B981),
            candidatePrimaryForeground = Color(0xFF06281E),
            popupBubbleBackground = Color(0xFF2A2A32),
            popupBubbleForeground = Color(0xFFFFFFFF),
            popupBubbleBorder = Color(0xFF3F3F46),
            popupSelectedBackground = Color(0xFF10B981),
            popupSelectedForeground = Color(0xFF06281E),
            glideTrailPrimary = Color(0xFF10B981),
            glideTrailGlow = Color(0x6610B981)
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
