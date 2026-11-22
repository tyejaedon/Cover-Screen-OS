package com.tyejaedon.coverscreenos.overlay.input

/**
 * Discrete text-editing operations produced by the cover keyboard's keypads.
 *
 * All keypad callers (numeric PIN, T9 multitap, QWERTY, backspace, clear)
 * funnel through [CoverInputSessionManager.applyDelta] as one of these
 * variants. The session manager translates each delta into (old buffer,
 * new buffer, new caret) triples so the injection layer can push a
 * **minimal** edit to the target editor instead of rewriting the whole
 * field on every keystroke.
 */
sealed interface TextDelta {

    /** Insert [text] at the current caret. If a range is selected, the range is replaced. */
    data class Insert(val text: String) : TextDelta

    /**
     * Replace the character immediately preceding the caret with [char].
     * Used by T9 multi-tap cycling. If the caret is at position 0 this
     * degrades to an [Insert].
     */
    data class ReplacePreviousChar(val char: Char) : TextDelta

    /**
     * Delete [count] characters preceding the caret (or the current range
     * selection, if any). No-op when the caret is at 0 and no range is
     * selected.
     */
    data class Backspace(val count: Int = 1) : TextDelta

    /** Empty the entire buffer and place the caret at 0. */
    data object Clear : TextDelta

    /**
     * Commit [text] into the buffer. If a composing region is active it is
     * replaced; otherwise this behaves like [Insert]. Composing is always
     * cleared afterwards.
     */
    data class Commit(val text: String) : TextDelta

    /** Replace the word before the caret (or composing region) with a suggestion. */
    data class CommitCurrentWord(val text: String) : TextDelta
}

/**
 * Result of diffing an old buffer against a new buffer.
 *
 * [replaceStart] and [replaceEnd] are indices into the **old** buffer;
 * [insert] is the substring that should be written at that range.
 * `replaceEnd == replaceStart` with empty [insert] indicates a no-op.
 *
 * This representation bridges the cover keyboard's local [TextBuffer] and the Android
 * injection layer:
 * - Over `InputConnection` (IME path), only `[replaceStart, replaceEnd)` is replaced by [insert].
 * - Over `AccessibilityNodeInfo.ACTION_SET_TEXT` or `ACTION_PASTE` (accessibility fallback paths),
 *   the full [new] buffer is written because accessibility actions lack range-level mutations.
 */
internal data class BufferDelta(
    /** Buffer text before the edit was applied. */
    val old: String,
    /** Buffer text after the edit was applied. */
    val new: String,
    /** Inclusive start index in [old] where characters begin to differ. */
    val replaceStart: Int,
    /** Exclusive end index in [old] of the slice being replaced. */
    val replaceEnd: Int,
    /** Replacement substring taken from [new] to splice into `[replaceStart, replaceEnd)`. */
    val insert: String,
    /** Target cursor index inside [new] once the replacement completes. */
    val newCaret: Int
) {
    /** True when neither characters are removed nor inserted (e.g. a pure caret move). */
    val isNoOp: Boolean
        get() = replaceStart == replaceEnd && insert.isEmpty()

    /** Number of characters removed from [old] at `[replaceStart, replaceEnd)`. */
    val removedLength: Int
        get() = replaceEnd - replaceStart

    companion object {
        /**
         * Compute the minimal replacement span that turns [old] into [new].
         * Uses common-prefix / common-suffix trimming — O(min(old,new)) —
         * so a single-char append to a 40-char buffer produces `insert.length == 1`,
         * `removedLength == 0`.
         */
        fun compute(old: String, new: String, newCaret: Int): BufferDelta {
            // Fast path: identical text produces an empty replacement span at the clamped caret.
            if (old == new) {
                return BufferDelta(
                    old = old,
                    new = new,
                    replaceStart = newCaret.coerceIn(0, new.length),
                    replaceEnd = newCaret.coerceIn(0, new.length),
                    insert = "",
                    newCaret = newCaret.coerceIn(0, new.length)
                )
            }

            // Advance from the left while characters match to find the start of the edited span.
            val maxPrefix = minOf(old.length, new.length) // Upper bound for identical leading characters
            var prefix = 0 // Count of matching characters at the start of `old` and `new`
            while (prefix < maxPrefix && old[prefix] == new[prefix]) prefix++

            // Retreat from the right within the unconsumed tail (never crossing `prefix`, which
            // prevents double-counting characters when inserting/deleting inside repeated chars).
            val maxSuffix = minOf(old.length - prefix, new.length - prefix) // Max trailing chars left after `prefix`
            var suffix = 0 // Count of matching characters at the end of `old` and `new`
            while (
                suffix < maxSuffix &&
                old[old.length - 1 - suffix] == new[new.length - 1 - suffix]
            ) suffix++

            val replaceStart = prefix
            val replaceEnd = old.length - suffix
            val insert = new.substring(prefix, new.length - suffix) // Minimal changed slice from `new`
            return BufferDelta(
                old = old,
                new = new,
                replaceStart = replaceStart,
                replaceEnd = replaceEnd,
                insert = insert,
                newCaret = newCaret.coerceIn(0, new.length)
            )
        }
    }
}

/**
 * Test seam for the injection layer. Production wraps the live
 * `CoverInputAccessibilityService`; unit tests substitute a recording fake
 * so keystroke-level behaviour can be asserted without spinning up the
 * platform accessibility framework.
 */
internal interface TextInjectionTarget {

    /**
     * Apply a delta to the focused editor. Implementations should prefer
     * a minimal edit (e.g. `InputConnection.commitText` for the [BufferDelta.insert]
     * substring only) over a full-buffer rewrite. Returns the channel that
     * ultimately delivered the edit.
     */
    fun applyDelta(delta: BufferDelta): InjectionMethod

    /** Dispatched by [CoverInputSessionManager.commitAndFinish]. */
    fun dispatchDone()
}
