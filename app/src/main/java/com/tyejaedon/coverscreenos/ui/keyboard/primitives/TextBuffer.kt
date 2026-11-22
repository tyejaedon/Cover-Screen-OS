package com.tyejaedon.coverscreenos.ui.keyboard.primitives

/**
 * Immutable text-editing state suitable for both IME (`InputConnection`) and
 * overlay keypad paths. The three concurrent regions:
 *
 * - [text] — the current buffer.
 * - [selection] — the caret / selection expressed as half-open cursor boundaries
 *   `first..last` in `0..text.length` (matching `String.substring(first, last)`).
 *   `first == last` means a collapsed caret at that position; `first < last` means
 *   an active range selection.
 * - [composing] — optional composing / soft-editable region using the same
 *   boundary semantics as [selection] (parallel to Android `InputConnection`
 *   composing spans). `null` when no composition is in progress.
 *
 * All mutator methods return a new [TextBuffer]; instances are safe to hold
 * as Compose state.
 *
 * @property text Full backing text string currently in the buffer.
 * @property selection Half-open caret/selection range (`first == last` is a caret; `first < last` is a selection).
 * @property composing Optional soft-editable composing span (`null` when no composition is active).
 */
data class TextBuffer(
    val text: String = "",
    val selection: IntRange = 0..0,
    val composing: IntRange? = null
) {

    init {
        // Enforce valid half-open bounds [0, text.length] for selection and composing spans.
        require(selection.first in 0..text.length && selection.last in 0..text.length) {
            "selection $selection out of bounds for text length ${text.length}"
        }
        require(selection.first <= selection.last) {
            "selection range must be non-decreasing: $selection"
        }
        composing?.let {
            require(it.first in 0..text.length && it.last in 0..text.length) {
                "composing $it out of bounds for text length ${text.length}"
            }
            require(it.first <= it.last) {
                "composing range must be non-decreasing: $it"
            }
        }
    }

    /** True when [selection] spans one or more characters (not a collapsed zero-length caret). */
    val hasSelection: Boolean get() = selection.first < selection.last

    /** True when a composing region is active. */
    val isComposing: Boolean get() = composing != null

    /** The substring currently under the composing region, or `""` if none. */
    val composingText: String
        get() = composing?.let { text.substring(it.first, it.last) }.orEmpty()

    /**
     * Replaces the current [selection] with [insert] and returns the result.
     * The new caret is placed immediately after the inserted text. Any active
     * [composing] region is shifted if it lies after the edit, preserved if before
     * it, or cleared if the edit overlaps it.
     *
     * Passing an empty [insert] performs a delete of the current selection.
     */
    fun applyDelta(insert: String): TextBuffer {
        val start = selection.first
        val end = selection.last
        val newText = text.substring(0, start) + insert + text.substring(end)
        val caret = start + insert.length
        val newComposing = composing?.let { adjustRange(it, start, end, insert.length) }
        return TextBuffer(
            text = newText,
            selection = caret..caret,
            composing = newComposing
        )
    }

    /**
     * Deletes [count] characters before the caret (backspace). If a range
     * selection is active, deletes the selection and ignores [count].
     */
    fun backspace(count: Int = 1): TextBuffer {
        require(count >= 0) { "count must be >= 0" }
        // Deleting an active range selection collapses it without deleting extra characters.
        if (hasSelection) return applyDelta("")
        if (count == 0) return this
        val caret = selection.first
        val newStart = (caret - count).coerceAtLeast(0)
        val removed = caret - newStart
        if (removed == 0) return this
        val newText = text.substring(0, newStart) + text.substring(caret)
        val newComposing = composing?.let { adjustRange(it, newStart, caret, 0) }
        return TextBuffer(
            text = newText,
            selection = newStart..newStart,
            composing = newComposing
        )
    }

    /**
     * Replaces the current composing region with [newComposing] (mirrors
     * `InputConnection.setComposingText`). If there is no active composing region,
     * one is started at the current caret / selection. The composing region and caret
     * both move to enclose the newly inserted text: composing =
     * `start..(start + newComposing.length)`, selection =
     * `(start + newComposing.length)..(start + newComposing.length)`.
     */
    fun replaceComposing(newComposing: String): TextBuffer {
        val start = composing?.first ?: selection.first
        val end = composing?.last ?: selection.last
        val newText = text.substring(0, start) + newComposing + text.substring(end)
        val composeEnd = start + newComposing.length
        // Empty replacement ends composition cleanly instead of retaining a 0-length span.
        val newComposingRange = if (newComposing.isEmpty()) null else start..composeEnd
        return TextBuffer(
            text = newText,
            selection = composeEnd..composeEnd,
            composing = newComposingRange
        )
    }

    /**
     * Commits [committed] into the buffer. If a composing region is active
     * it is replaced (mirrors `InputConnection.commitText`); otherwise the
     * commit is a plain insertion at the current selection. Composing is
     * always cleared after commit. The caret is placed at the end of the
     * committed text.
     */
    fun commit(committed: String): TextBuffer {
        val start = composing?.first ?: selection.first
        val end = composing?.last ?: selection.last
        val newText = text.substring(0, start) + committed + text.substring(end)
        val caret = start + committed.length
        return TextBuffer(
            text = newText,
            selection = caret..caret,
            composing = null
        )
    }

    /**
     * Commits a prediction over the composing region, selected text, or the
     * letter word immediately before the caret. Also inserts at the caret when
     * no word is being composed (next-word suggestions).
     */
    fun commitCurrentWord(candidate: String): TextBuffer {
        if (isComposing || hasSelection) return commit(candidate)
        val caret = selection.first
        val prefix = text.substring(0, caret)
        // indexOfLast returns -1 when all preceding characters are letters, yielding start = 0.
        val start = prefix.indexOfLast { !it.isLetter() } + 1
        return withSelection(start, caret).commit(candidate)
    }

    /**
     * Moves the caret to [position]. [position] is clamped into
     * `0..text.length`. Clears any active selection but preserves composing.
     */
    fun withCaret(position: Int): TextBuffer {
        val p = position.coerceIn(0, text.length)
        return copy(selection = p..p)
    }

    /**
     * Applies a range selection `start..end`. Both bounds are clamped and
     * reordered as needed.
     */
    fun withSelection(start: Int, end: Int): TextBuffer {
        val lo = minOf(start, end).coerceIn(0, text.length)
        val hi = maxOf(start, end).coerceIn(0, text.length)
        return copy(selection = lo..hi)
    }

    /**
     * Re-indexes [range] after replacing `[editStart, editEnd)` with [insertLength] characters,
     * returning `null` if the edit overlaps [range].
     */
    private fun adjustRange(
        range: IntRange,
        editStart: Int,
        editEnd: Int,
        insertLength: Int
    ): IntRange? {
        val deletedLength = editEnd - editStart
        val delta = insertLength - deletedLength
        val fullyBefore = range.last <= editStart
        val fullyAfter = range.first >= editEnd
        return when {
            fullyBefore -> range
            fullyAfter -> (range.first + delta)..(range.last + delta)
            // Edit overlaps composing — safest to drop the composing region.
            else -> null
        }
    }

    /** Default empty [TextBuffer] instance. */
    companion object {
        /** Empty buffer with caret at index 0 and no active composing span. */
        val Empty = TextBuffer()
    }
}
