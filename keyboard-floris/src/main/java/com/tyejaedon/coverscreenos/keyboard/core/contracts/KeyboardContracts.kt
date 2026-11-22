package com.tyejaedon.coverscreenos.keyboard.core.contracts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Layout modes supported by the CoverOS keyboard engine.
 */
enum class LayoutMode {
    /** Compact letter-by-letter QWERTY keyboard for cover-screen typing and glide input. */
    QWERTY_COMPACT,
    /** Classic 3x4 phone keypad with T9 predictive or multi-tap letter entry. */
    T9_PREDICTIVE,
    /** Primary punctuation and digit layer (`?123`). */
    SYMBOLS_PRIMARY,
    /** Secondary/extended math, currency, and rare symbols layer (`=/<`). */
    SYMBOLS_SECONDARY,
    /** Dedicated 10-digit keypad for PINs, phone numbers, and numeric forms. */
    NUMBER_PAD
}

/**
 * Shift state for alphabetic keyboard layouts.
 */
enum class ShiftState {
    /** All letters emit lowercase characters. */
    LOWERCASE,
    /** Next typed letter is uppercase, then automatically drops back to [LOWERCASE]. */
    SINGLE_SHIFT,
    /** Locked uppercase until tapped again. */
    CAPS_LOCK
}

/**
 * Structural model representing a single key on the keyboard surface.
 */
data class KeyModel(
    /** Stable key identifier / code point (positive for Unicode chars, negative for action keys). */
    val id: Int,
    /** Main label drawn in the center of the keycap (e.g. `"a"`, `"2"`, `"Space"`). */
    val primaryLabel: String,
    /** Optional small corner hint label (e.g. number popup `"1"` on `"q"` or `"abc"` on `"2"`). */
    val secondaryLabel: String? = null,
    /** Letters mapped to this key in T9 mode (e.g. `"abc"` for key `'2'`). */
    val t9Chars: String = "",
    /** Hit-test rectangle in keyboard surface pixel coordinates for touch and swipe matching. */
    val bounds: Rect = Rect.Zero,
    /** Center coordinate of [bounds], used by glide/DTW path matching and spatial proximity scoring. */
    val centroid: Offset = Offset.Zero,
    /** True for non-character keys (Shift, Backspace, Enter, mode switches) styled differently from letter keys. */
    val isFunctional: Boolean = false
)

/**
 * A word prediction candidate produced by the NLP engine.
 */
data class CandidateWord(
    /** Suggested word text ready to insert or replace the composing word. */
    val word: String,
    /** Composite ranking score combining frequency and spatial/edit proximity (higher = better). */
    val score: Float,
    /** True when confident enough to auto-commit on space/punctuation or highlight as primary choice. */
    val isAutocorrectDefault: Boolean = false
)

/**
 * User input actions dispatched from touch, gesture, and hardware handlers.
 */
sealed interface InputAction {
    /** Single key press with the tapped [key] and exact finger [touchOffset] for adaptive hitbox logging. */
    data class KeyTap(val key: KeyModel, val touchOffset: Offset) : InputAction
    /** Long-press selection picking [selectedPopupChar] from [key]'s accent/digit popup strip. */
    data class LongPressSelect(val key: KeyModel, val selectedPopupChar: String) : InputAction
    /** Completed swipe/glide gesture carrying raw [sampledPoints] along the user's finger path. */
    data class GlideComplete(val sampledPoints: List<Offset>) : InputAction
    /** Horizontal slide along the spacebar shifting the cursor left/right by [charDelta] characters. */
    data class SpacebarCursorDelta(val charDelta: Int) : InputAction
    /** Swipe-left gesture from backspace previewing or committing [wordsToDelete] whole-word deletions. */
    data class GestureDeleteUpdate(val wordsToDelete: Int, val isFinalCommit: Boolean) : InputAction
    /** Request to switch the keyboard surface to [targetMode]. */
    data class ModeSwitch(val targetMode: LayoutMode) : InputAction
}
