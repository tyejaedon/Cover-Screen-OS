package com.tyejaedon.coverscreenos.keyboard.floris

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 *
 * Publishes [mutationRevision] on every text or selection mutation so hosts (like `CoverT9InputMethodService`)
 * can reactively recompute NLP candidates and auto-capitalization state on every keystroke.
 *
 * @property overlayCallbacks Fallback text-buffer callbacks when no live Android [InputConnection] is attached.
 * @property inputConnectionRef Weakly held [InputConnection] so detached editor sessions can be garbage-collected.
 * @property inputConnectionProvider Dynamic getter returning the currently active [InputConnection] on each call.
 * @property editorActionHandler Optional override invoked instead of sending an Enter key event on Done/Send.
 */
class CoverEditorBridge(
    private val overlayCallbacks: CoverOverlayInputCallbacks? = null,
    private val inputConnectionRef: WeakReference<InputConnection>? = null,
    private val inputConnectionProvider: (() -> InputConnection?)? = null,
    private val editorActionHandler: (() -> Unit)? = null
) : K3Editor {

    // Internal mutable counter incremented whenever text, composition, or cursor position changes.
    private val _mutationRevision = MutableStateFlow(0L)

    /** Read-only stream of edit counters; hosts observe this to refresh predictions after each keystroke. */
    val mutationRevision: StateFlow<Long> = _mutationRevision.asStateFlow()

    /** Bumps [mutationRevision] when an outside caller modifies text directly without going through this bridge. */
    fun notifyExternalMutation() {
        _mutationRevision.value += 1L
    }

    // Prefer the dynamic provider (live IME session) and fall back to the weak static reference.
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
            // Batch all region/commit/selection changes so the host app treats them as one atomic edit.
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
        notifyExternalMutation()
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
        notifyExternalMutation()
    }

    /** Reads up to [charsBefore]/[charsAfter] characters around the caret from the active editor or overlay buffer. */
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

    /** Inserts [text] at the cursor in either the attached [InputConnection] or overlay callbacks. */
    fun insertText(text: String) {
        val ic = currentInputConnection()
        if (ic != null) {
            ic.commitText(text, 1)
        } else {
            overlayCallbacks?.onAppend(text)
        }
        notifyExternalMutation()
    }

    /** Swaps the character immediately before the cursor with [newChar] (used for T9 multi-tap cycling). */
    fun replacePreviousChar(newChar: Char) {
        val ic = currentInputConnection()
        if (ic != null) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText(newChar.toString(), 1)
            ic.endBatchEdit()
        } else {
            overlayCallbacks?.onDeleteBackward()
            overlayCallbacks?.onAppend(newChar.toString())
        }
        notifyExternalMutation()
    }

    /** Deletes [charsBefore] characters before and [charsAfter] characters after the cursor. */
    fun deleteSurroundingText(charsBefore: Int, charsAfter: Int = 0) {
        val ic = currentInputConnection()
        if (ic != null) {
            ic.deleteSurroundingText(charsBefore, charsAfter)
        } else {
            repeat(charsBefore) {
                overlayCallbacks?.onDeleteBackward()
            }
        }
        notifyExternalMutation()
    }

    /** Removes the whole word (plus trailing whitespace) immediately before the cursor. */
    fun deletePreviousWord() {
        val ic = currentInputConnection()
        if (ic != null) {
            val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
            // Match the last contiguous non-space token and any trailing spaces after it.
            val wordLen = Regex("\\S+\\s*$").find(before)?.value?.length ?: 1
            ic.deleteSurroundingText(wordLen, 0)
        } else {
            overlayCallbacks?.onDeletePreviousWord()
        }
        notifyExternalMutation()
    }

    /** Shifts the caret left (`delta < 0`) or right (`delta > 0`) for spacebar cursor scrubbing. */
    fun moveCursor(delta: Int) {
        val ic = currentInputConnection()
        if (ic != null) {
            val surrounding = getSurroundingText(charsBefore = 100, charsAfter = 100)
            // Compute caret offset within the fetched window and clamp to valid window bounds.
            val currentPos = surrounding.textBefore.length
            val newPos = (currentPos + delta).coerceIn(0, currentPos + surrounding.textAfter.length)
            ic.setSelection(newPos, newPos)
        } else {
            overlayCallbacks?.onMoveCursor(delta)
        }
        notifyExternalMutation()
    }

    /** Replaces the partial word before the cursor with [candidate], optionally appending a space. */
    fun commitCandidate(candidate: String, trailingSpace: Boolean = true) {
        val toCommit = if (trailingSpace) "$candidate " else candidate
        val ic = currentInputConnection()
        if (ic != null) {
            val surrounding = getSurroundingText(50, 0)
            // Find only the active alphanumeric word prefix so punctuation stays untouched.
            val word = surrounding.textBefore.takeLastWhile { it.isLetterOrDigit() }
            if (word.isNotEmpty()) {
                ic.deleteSurroundingText(word.length, 0)
            }
            ic.commitText(toCommit, 1)
        } else {
            overlayCallbacks?.onCommitCandidate(toCommit)
        }
        notifyExternalMutation()
    }

    /** Submits the current field via [editorActionHandler], an Enter key event, or overlay `onDone()`. */
    fun finishInput() {
        if (editorActionHandler != null) {
            editorActionHandler.invoke()
            notifyExternalMutation()
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
        notifyExternalMutation()
    }

    /** Clears up to 500 characters before and after the cursor across the active editor. */
    fun clearAll() {
        val ic = currentInputConnection()
        if (ic != null) {
            val surrounding = getSurroundingText(500, 500)
            ic.deleteSurroundingText(surrounding.textBefore.length, surrounding.textAfter.length)
        } else {
            overlayCallbacks?.onClear()
        }
        notifyExternalMutation()
    }
}
