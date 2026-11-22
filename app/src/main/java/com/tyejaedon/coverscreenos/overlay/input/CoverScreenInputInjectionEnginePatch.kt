package com.tyejaedon.coverscreenos.overlay.input

/**
 * Computes how many characters precede the caret in the current (or most recent) word so
 * word-level backspace ([TextDelta.Backspace]) can remove both trailing whitespace and the
 * immediately preceding word in a single delta.
 *
 * @param text Substring from the start of the buffer up to the current caret position.
 * @return Number of characters to delete backward from the caret; returns `text.length` when
 *   [text] contains only whitespace or a single word with no preceding space.
 */
internal fun previousWordLength(text: String): Int {
    // Strip trailing spaces first so pressing word-delete right after a space deletes the
    // whitespace plus the previous word rather than leaving the word intact.
    val trimmed = text.trimEnd()
    if (trimmed.isEmpty()) return text.length
    val lastSpace = trimmed.lastIndexOf(' ')
    // If no space remains, delete the entire prefix back to index 0; otherwise delete from
    // just after the last space up to the original (untrimmed) end of `text`.
    return if (lastSpace == -1) text.length else text.length - lastSpace - 1
}
