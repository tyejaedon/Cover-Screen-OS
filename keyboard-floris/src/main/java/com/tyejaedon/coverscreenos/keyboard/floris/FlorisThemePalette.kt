package com.tyejaedon.coverscreenos.keyboard.floris

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Visual color theme modes supported by `:keyboard-floris`.
 */
enum class FlorisThemeMode {
    /** High-contrast pitch-dark theme tailored for OLED cover displays. */
    AMOLED_COVER,
    /** Dynamic palette derived from the active Material 3 [ColorScheme]. */
    MATERIAL_DYNAMIC
}

/**
 * FlorisBoard Snygg-inspired semantic theme tokens for `:keyboard-floris`.
 * Provides high-contrast AMOLED cover tokens (`#111215` surface, `#272A30` character keys,
 * `#1B1D22` functional keys, `#3E7BFA` cobalt action keys) alongside Material3 dynamic tokens.
 *
 * @property mode Theme source ([FlorisThemeMode.AMOLED_COVER] or [FlorisThemeMode.MATERIAL_DYNAMIC]).
 * @property keyboardSurface Backdrop color behind all key rows.
 * @property keyBackground Default fill color for letter and digit keycaps.
 * @property keyForeground Primary label/icon color on character keys.
 * @property keySecondaryForeground Muted label color for T9 sub-letters, spacebar label, and secondary hints.
 * @property functionalKeyBackground Fill color for modifier keys (Backspace, Symbols, Mode toggle).
 * @property functionalKeyForeground Label/icon color for functional modifier keys.
 * @property shiftActiveBackground Highlight fill when Shift or Caps Lock is active.
 * @property shiftActiveForeground Icon color when Shift or Caps Lock is active.
 * @property shiftLockedBorder Outline border added around the Shift key while Caps Lock is locked.
 * @property actionKeyBackground Accent fill for primary IME action keys (Enter, Send, Search, Done).
 * @property actionKeyForeground Icon/text color on the primary action key.
 * @property destructiveKeyForeground Alert tint for destructive keys such as Clear.
 * @property smartbarBackground Backdrop behind the top suggestion/tool bar.
 * @property candidateChipBackground Fill behind non-primary suggestion chips.
 * @property candidateChipForeground Text color for secondary suggestion chips.
 * @property candidatePrimaryBackground Highlighted fill behind the top/selected prediction chip.
 * @property candidatePrimaryForeground Text color for the top/selected prediction chip.
 * @property popupBubbleBackground Surface color for floating key previews and extended accent menus.
 * @property popupBubbleForeground Text color inside floating key previews and extended accent menus.
 * @property popupBubbleBorder Border stroke color available for popup containers.
 * @property popupSelectedBackground Fill for an active option inside extended popups.
 * @property popupSelectedForeground Text color for an active option inside extended popups.
 * @property glideTrailPrimary Crisp inner stroke color of the swipe-typing trail.
 * @property glideTrailGlow Soft outer halo stroke color behind the swipe-typing trail.
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
    /** Built-in theme presets and Material 3 palette adapters. */
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
