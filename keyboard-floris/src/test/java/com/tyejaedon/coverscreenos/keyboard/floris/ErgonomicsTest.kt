package com.tyejaedon.coverscreenos.keyboard.floris

import androidx.compose.ui.geometry.Offset
import com.tyejaedon.coverscreenos.keyboard.nlp.DigramModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErgonomicsTest {
    @Test
    fun contractionsExpand() {
        assertEquals("don't", FlorisTextInputManager.expandContraction("dont"))
        assertEquals("Don't", FlorisTextInputManager.expandContraction("Dont"))
        assertEquals("I'm", FlorisTextInputManager.expandContraction("im"))
        assertNull(FlorisTextInputManager.expandContraction("hello"))
    }

    @Test
    fun digramBiasesBorderTouch() {
        // Midway between g (4.5,1) and h (5.5,1): after 't' prefers 'h'
        val touch = Offset(5.0f, 1.0f)
        assertEquals('h', DigramModel.resolveNearestKey(touch, 't'))
    }

    @Test
    fun doubleSpaceInsertsPeriodAndAutoCaps() {
        var buffer = "hello"
        val callbacks = object : CoverOverlayInputCallbacks {
            override fun onAppend(text: String) { buffer += text }
            override fun onDeleteBackward() { if (buffer.isNotEmpty()) buffer = buffer.dropLast(1) }
            override fun onDeletePreviousWord() {}
            override fun onMoveCursor(delta: Int) {}
            override fun onCommitCandidate(candidate: String) {
                val wordLen = buffer.takeLastWhile { it.isLetter() }.length
                buffer = buffer.dropLast(wordLen) + candidate + " "
            }
            override fun onClear() { buffer = "" }
            override fun onDone() {}
            override fun getTextBeforeCursor(maxChars: Int) = buffer
            override fun getTextAfterCursor(maxChars: Int) = ""
            override fun getFullText() = buffer
            override fun getCursorPosition() = buffer.length
            override fun setCursorPosition(newPos: Int) {}
            override fun replaceText(range: IntRange, text: String, newCursor: Int) {
                buffer = buffer.replaceRange(range, text)
            }
        }
        val bridge = CoverEditorBridge(overlayCallbacks = callbacks)
        val manager = FlorisTextInputManager(bridge = bridge, initialMode = CoverLayoutMode.QWERTY)

        manager.handleSpace(autocorrectEnabled = false, nowMs = 1000L)
        assertEquals("hello ", buffer)

        manager.handleSpace(autocorrectEnabled = false, nowMs = 1200L)
        assertEquals("hello. ", buffer)
        assertEquals(CoverShiftState.SINGLE_SHIFT, manager.shiftState)
    }
}
