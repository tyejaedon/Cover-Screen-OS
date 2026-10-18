package com.coveros.keyboard.core.contracts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Layout modes supported by the CoverOS keyboard engine.
 */
enum class LayoutMode {
    QWERTY_COMPACT,
    T9_PREDICTIVE,
    SYMBOLS_PRIMARY,
    SYMBOLS_SECONDARY,
    NUMBER_PAD
}

/**
 * Shift state for alphabetic keyboard layouts.
 */
enum class ShiftState {
    LOWERCASE,
    SINGLE_SHIFT,
    CAPS_LOCK
}

/**
 * Structural model representing a single key on the keyboard surface.
 */
data class KeyModel(
    val id: Int,
    val primaryLabel: String,
    val secondaryLabel: String? = null,
    val t9Chars: String = "",
    val bounds: Rect = Rect.Zero,
    val centroid: Offset = Offset.Zero,
    val isFunctional: Boolean = false
)

/**
 * A word prediction candidate produced by the NLP engine.
 */
data class CandidateWord(
    val word: String,
    val score: Float,
    val isAutocorrectDefault: Boolean = false
)

/**
 * User input actions dispatched from touch, gesture, and hardware handlers.
 */
sealed interface InputAction {
    data class KeyTap(val key: KeyModel, val touchOffset: Offset) : InputAction
    data class LongPressSelect(val key: KeyModel, val selectedPopupChar: String) : InputAction
    data class GlideComplete(val sampledPoints: List<Offset>) : InputAction
    data class SpacebarCursorDelta(val charDelta: Int) : InputAction
    data class GestureDeleteUpdate(val wordsToDelete: Int, val isFinalCommit: Boolean) : InputAction
    data class ModeSwitch(val targetMode: LayoutMode) : InputAction
}
