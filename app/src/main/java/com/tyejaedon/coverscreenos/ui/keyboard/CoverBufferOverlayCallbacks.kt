package com.tyejaedon.coverscreenos.ui.keyboard

import com.tyejaedon.coverscreenos.keyboard.floris.CoverOverlayInputCallbacks

/**
 * Shared [CoverOverlayInputCallbacks] implementation for local Compose text-buffer hosts
 * such as launcher search and QA previews.
 */
internal class CoverBufferOverlayCallbacks(
    private val getText: () -> String,
    private val setText: (String) -> Unit,
    private val onCharTyped: ((Char) -> Unit)? = null,
    private val onBackspace: (() -> Unit)? = null,
    private val onClearAction: (() -> Unit)? = null,
    private val onDoneAction: () -> Unit = {}
) : CoverOverlayInputCallbacks {

    override fun onAppend(text: String) {
        if (text.length == 1 && onCharTyped != null) {
            onCharTyped.invoke(text[0])
        } else {
            setText(getText() + text)
        }
    }

    override fun onDeleteBackward() {
        if (onBackspace != null) {
            onBackspace.invoke()
        } else {
            val current = getText()
            if (current.isNotEmpty()) {
                setText(current.dropLast(1))
            }
        }
    }

    override fun onDeletePreviousWord() {
        val current = getText()
        val trimmed = current.trimEnd()
        val lastSpace = trimmed.lastIndexOf(' ')
        val updated = if (lastSpace >= 0) trimmed.substring(0, lastSpace + 1) else ""
        setText(updated)
    }

    override fun onMoveCursor(delta: Int) = Unit

    override fun onCommitCandidate(candidate: String) {
        val current = getText()
        val lastWord = current.takeLastWhile { it.isLetterOrDigit() }
        val updated = if (lastWord.isNotEmpty()) {
            current.dropLast(lastWord.length) + candidate + " "
        } else if (current.isNotEmpty() && !current.endsWith(" ")) {
            "$current $candidate "
        } else {
            "$current$candidate "
        }
        setText(updated)
    }

    override fun onClear() {
        if (onClearAction != null) {
            onClearAction.invoke()
        } else {
            setText("")
        }
    }

    override fun onDone() {
        onDoneAction()
    }

    override fun getTextBeforeCursor(maxChars: Int): String = getText().takeLast(maxChars)

    override fun getTextAfterCursor(maxChars: Int): String = ""

    override fun getFullText(): String = getText()

    override fun getCursorPosition(): Int = getText().length

    override fun setCursorPosition(newPos: Int) = Unit

    override fun replaceText(range: IntRange, text: String, newCursor: Int) {
        val current = getText()
        val safeStart = range.first.coerceIn(0, current.length)
        val safeEnd = (range.last + 1).coerceIn(safeStart, current.length)
        setText(current.replaceRange(safeStart, safeEnd, text))
    }
}
