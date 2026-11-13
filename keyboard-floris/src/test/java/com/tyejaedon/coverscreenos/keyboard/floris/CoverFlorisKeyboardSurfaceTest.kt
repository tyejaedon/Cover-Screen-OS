package com.tyejaedon.coverscreenos.keyboard.floris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverFlorisKeyboardSurfaceTest {

    private class TestOverlayCallbacks : CoverOverlayInputCallbacks {
        var buffer: String = ""
        var cursor: Int = 0
        var doneCalled: Boolean = false
        var lastCommitted: String? = null

        override fun onAppend(text: String) {
            buffer += text
            cursor += text.length
        }

        override fun onDeleteBackward() {
            if (buffer.isNotEmpty()) {
                buffer = buffer.dropLast(1)
                cursor = buffer.length
            }
        }

        override fun onDeletePreviousWord() {
            buffer = buffer.trimEnd().substringBeforeLast(" ", "")
            cursor = buffer.length
        }

        override fun onMoveCursor(delta: Int) {
            cursor = (cursor + delta).coerceIn(0, buffer.length)
        }

        override fun onCommitCandidate(candidate: String) {
            lastCommitted = candidate
            buffer += candidate
            cursor = buffer.length
        }

        override fun onClear() {
            buffer = ""
            cursor = 0
        }

        override fun onDone() {
            doneCalled = true
        }

        override fun getTextBeforeCursor(maxChars: Int): String = buffer

        override fun getTextAfterCursor(maxChars: Int): String = ""

        override fun getFullText(): String = buffer

        override fun getCursorPosition(): Int = cursor

        override fun setCursorPosition(newPos: Int) {
            cursor = newPos
        }

        override fun replaceText(range: IntRange, text: String, newCursor: Int) {
            buffer = text
            cursor = newCursor
        }
    }

    @Test
    fun testKeyActionDispatching() {
        val callbacks = TestOverlayCallbacks()
        val bridge = CoverEditorBridge(overlayCallbacks = callbacks)

        // Simulate typing through layout key actions
        val qwertyRows = CoverLayoutFactory.createQwertyRows(CoverShiftState.LOWERCASE)
        val keyQ = qwertyRows[0][0]
        assertEquals("q", keyQ.primaryLabel)

        if (keyQ.action is CoverKeyAction.InputChar) {
            bridge.insertText(keyQ.action.char.toString())
        }
        assertEquals("q", callbacks.buffer)

        // Simulate space action
        bridge.insertText(" ")
        assertEquals("q ", callbacks.buffer)

        // Simulate backspace action
        bridge.deleteSurroundingText(1, 0)
        assertEquals("q", callbacks.buffer)
    }

    @Test
    fun testCandidateSelectionCommitsWord() {
        val callbacks = TestOverlayCallbacks()
        val bridge = CoverEditorBridge(overlayCallbacks = callbacks)

        bridge.commitCandidate("prediction", trailingSpace = true)
        assertEquals("prediction ", callbacks.buffer)
        assertEquals("prediction ", callbacks.lastCommitted)
    }

    @Test
    fun testModeSwitchToT9AndKeyValidation() {
        val t9Rows = CoverLayoutFactory.createT9Rows()
        assertEquals(4, t9Rows.size)

        // Key 2 = ABC
        val key2 = t9Rows[0][1]
        assertEquals("2", key2.primaryLabel)
        assertEquals("abc", key2.t9Chars)

        // Key 9 = WXYZ
        val key9 = t9Rows[2][2]
        assertEquals("9", key9.primaryLabel)
        assertEquals("wxyz", key9.t9Chars)

        // Key 0 = Space
        val key0 = t9Rows[3][1]
        assertEquals("0", key0.primaryLabel)
        assertEquals(CoverKeyAction.Space, key0.action)
    }

    @Test
    fun testSymbolsRowsGeneration() {
        val symbolRows = CoverLayoutFactory.createSymbolsRows()
        assertEquals(4, symbolRows.size)
        assertEquals("1", symbolRows[0][0].primaryLabel)
        assertEquals("0", symbolRows[0][9].primaryLabel)
        assertEquals("@", symbolRows[1][0].primaryLabel)
    }
}
