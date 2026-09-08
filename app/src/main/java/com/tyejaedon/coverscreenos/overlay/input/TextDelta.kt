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
}

/**
 * Result of diffing an old buffer against a new buffer.
 *
 * [replaceStart] and [replaceEnd] are indices into the **old** buffer;
 * [insert] is the substring that should be written at that range.
 * `replaceEnd == replaceStart` with empty [insert] indicates a no-op.
 */
internal data class BufferDelta(
    val old: String,
    val new: String,
    val replaceStart: Int,
    val replaceEnd: Int,
    val insert: String,
    val newCaret: Int
) {
    val isNoOp: Boolean
        get() = replaceStart == replaceEnd && insert.isEmpty()

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

            val maxPrefix = minOf(old.length, new.length)
            var prefix = 0
            while (prefix < maxPrefix && old[prefix] == new[prefix]) prefix++

            val maxSuffix = minOf(old.length - prefix, new.length - prefix)
            var suffix = 0
            while (
                suffix < maxSuffix &&
                old[old.length - 1 - suffix] == new[new.length - 1 - suffix]
            ) suffix++

            val replaceStart = prefix
            val replaceEnd = old.length - suffix
            val insert = new.substring(prefix, new.length - suffix)
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

