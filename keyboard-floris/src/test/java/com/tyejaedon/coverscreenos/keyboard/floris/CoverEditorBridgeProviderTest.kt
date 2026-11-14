package com.tyejaedon.coverscreenos.keyboard.floris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverEditorBridgeProviderTest {

    @Test
    fun `custom editorActionHandler is invoked on finishInput`() {
        var actionExecuted = false
        val bridge = CoverEditorBridge(
            editorActionHandler = { actionExecuted = true }
        )

        bridge.finishInput()
        assertTrue("Expected editorActionHandler to be invoked", actionExecuted)
    }

    @Test
    fun `overlayCallbacks integration works with full protocol`() {
        var committedCandidate = ""
        val callbacks = object : CoverOverlayInputCallbacks {
            var buffer = ""
            var cursor = 0

            override fun onAppend(text: String) { buffer += text; cursor += text.length }
            override fun onDeleteBackward() { if (buffer.isNotEmpty()) buffer = buffer.dropLast(1) }
            override fun onDeletePreviousWord() { buffer = "" }
            override fun onMoveCursor(delta: Int) { cursor += delta }
            override fun onCommitCandidate(candidate: String) { committedCandidate = candidate }
            override fun onClear() { buffer = "" }
            override fun onDone() {}
            override fun getTextBeforeCursor(maxChars: Int): String = buffer
            override fun getTextAfterCursor(maxChars: Int): String = ""
            override fun getFullText(): String = buffer
            override fun getCursorPosition(): Int = cursor
            override fun setCursorPosition(newPos: Int) { cursor = newPos }
            override fun replaceText(range: IntRange, text: String, newCursor: Int) {
                buffer = text
                cursor = newCursor
            }
        }

        val bridge = CoverEditorBridge(overlayCallbacks = callbacks)
        bridge.insertText("hey")
        assertEquals("hey", callbacks.buffer)

        bridge.commitCandidate("hello", trailingSpace = true)
        assertEquals("hello ", committedCandidate)
    }
}
