package com.tyejaedon.coverscreenos.keyboard.floris

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import org.k3lp.runtime.K3Editor
import org.k3lp.runtime.K3SurroundingText
import org.k3lp.runtime.K3TextRange
import java.lang.ref.WeakReference

/**
 * Callbacks supplied by cover overlay hosts (such as CoverInputSessionManager or
 * local launcher search) where text is managed in a local buffer rather than a direct InputConnection.
 */
interface CoverOverlayInputCallbacks {
    fun onAppend(text: String)
    fun onDeleteBackward()
    fun onDeletePreviousWord()
    fun onMoveCursor(delta: Int)
    fun onCommitCandidate(candidate: String)
    fun onClear()
    fun onDone()
    fun getTextBeforeCursor(maxChars: Int = 100): String
    fun getTextAfterCursor(maxChars: Int = 100): String
    fun getFullText(): String
    fun getCursorPosition(): Int
    fun setCursorPosition(newPos: Int)
    fun replaceText(range: IntRange, text: String, newCursor: Int)
}

/**
 * Unified editor bridge implementing FlorisBoard's [K3Editor].
 *
 * Multiplexes text editing operations between:
 * 1. An Android [InputConnection] (for system IME and API 33+ `flagInputMethodEditor` sessions).
 * 2. An overlay callback interface [CoverOverlayInputCallbacks] (for accessibility node text manipulation
 *    and local launcher search).
 */
class CoverEditorBridge(
    private val overlayCallbacks: CoverOverlayInputCallbacks? = null,
    private val inputConnectionRef: WeakReference<InputConnection>? = null,
    private val inputConnectionProvider: (() -> InputConnection?)? = null,
    private val editorActionHandler: (() -> Unit)? = null
) : K3Editor {

    private fun currentInputConnection(): InputConnection? =
        inputConnectionProvider?.invoke() ?: inputConnectionRef?.get()

    override fun replaceText(
        range: IntRange,
        text: String,
        newSelection: K3TextRange,
        newComposition: K3TextRange?
    ) {
        val ic = currentInputConnection()
        if (ic != null) {
            ic.beginBatchEdit()
            ic.setComposingRegion(range.first, range.last + 1)
            ic.commitText(text, 1)
            ic.setSelection(newSelection.start, newSelection.end)
            if (newComposition != null) {
                ic.setComposingRegion(newComposition.start, newComposition.end)
            } else {
                ic.finishComposingText()
            }
            ic.endBatchEdit()
        } else {
            overlayCallbacks?.replaceText(range, text, newSelection.start)
        }
    }

    override fun setComposition(newComposition: K3TextRange?) {
        val ic = currentInputConnection()
        if (ic != null) {
            if (newComposition != null) {
                ic.setComposingRegion(newComposition.start, newComposition.end)
            } else {
                ic.finishComposingText()
            }
        }
    }

    fun getSurroundingText(charsBefore: Int = 100, charsAfter: Int = 100): K3SurroundingText {
        val ic = currentInputConnection()
        if (ic != null) {
            val before = ic.getTextBeforeCursor(charsBefore, 0)?.toString() ?: ""
            val selected = ic.getSelectedText(0)?.toString() ?: ""
            val after = ic.getTextAfterCursor(charsAfter, 0)?.toString() ?: ""
            return K3SurroundingText(
                textBefore = before,
                textSelected = selected,
                textAfter = after
            )
        }

        val before = overlayCallbacks?.getTextBeforeCursor(charsBefore) ?: ""
        val after = overlayCallbacks?.getTextAfterCursor(charsAfter) ?: ""
        return K3SurroundingText(
            textBefore = before,
            textSelected = "",
            textAfter = after
        )
    }

    fun insertText(text: String) {
        val ic = currentInputConnection()
        if (ic != null) {
            ic.commitText(text, 1)
        } else {
            overlayCallbacks?.onAppend(text)
        }
    }

    fun deleteSurroundingText(charsBefore: Int, charsAfter: Int = 0) {
        val ic = currentInputConnection()
        if (ic != null) {
            ic.deleteSurroundingText(charsBefore, charsAfter)
        } else {
            repeat(charsBefore) {
                overlayCallbacks?.onDeleteBackward()
            }
        }
    }

    fun deletePreviousWord() {
        val ic = currentInputConnection()
        if (ic != null) {
            val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
            val wordLen = Regex("\\S+\\s*$").find(before)?.value?.length ?: 1
            ic.deleteSurroundingText(wordLen, 0)
        } else {
            overlayCallbacks?.onDeletePreviousWord()
        }
    }

    fun moveCursor(delta: Int) {
        val ic = currentInputConnection()
        if (ic != null) {
            val surrounding = getSurroundingText(charsBefore = 100, charsAfter = 100)
            val currentPos = surrounding.textBefore.length
            val newPos = (currentPos + delta).coerceIn(0, currentPos + surrounding.textAfter.length)
            ic.setSelection(newPos, newPos)
        } else {
            overlayCallbacks?.onMoveCursor(delta)
        }
    }

    fun commitCandidate(candidate: String, trailingSpace: Boolean = true) {
        val toCommit = if (trailingSpace) "$candidate " else candidate
        val ic = currentInputConnection()
        if (ic != null) {
            val surrounding = getSurroundingText(50, 0)
            val word = surrounding.textBefore.takeLastWhile { it.isLetterOrDigit() }
            if (word.isNotEmpty()) {
                ic.deleteSurroundingText(word.length, 0)
            }
            ic.commitText(toCommit, 1)
        } else {
            overlayCallbacks?.onCommitCandidate(toCommit)
        }
    }

    fun finishInput() {
        if (editorActionHandler != null) {
            editorActionHandler.invoke()
            return
        }
        val ic = currentInputConnection()
        if (ic != null) {
            ic.finishComposingText()
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        } else {
            overlayCallbacks?.onDone()
        }
    }

    fun clearAll() {
        val ic = currentInputConnection()
        if (ic != null) {
            val surrounding = getSurroundingText(500, 500)
            ic.deleteSurroundingText(surrounding.textBefore.length, surrounding.textAfter.length)
        } else {
            overlayCallbacks?.onClear()
        }
    }
}
