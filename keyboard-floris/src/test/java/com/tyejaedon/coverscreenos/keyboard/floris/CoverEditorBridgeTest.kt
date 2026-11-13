package com.tyejaedon.coverscreenos.keyboard.floris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverEditorBridgeTest {

    private class FakeOverlayCallbacks : CoverOverlayInputCallbacks {
        var buffer: String = ""
        var cursor: Int = 0
        var doneCalled: Boolean = false
        var clearCalled: Boolean = false
        var lastCommittedCandidate: String? = null

        override fun onAppend(text: String) {
            buffer = buffer.substring(0, cursor) + text + buffer.substring(cursor)
            cursor += text.length
        }

        override fun onDeleteBackward() {
            if (cursor > 0) {
                buffer = buffer.substring(0, cursor - 1) + buffer.substring(cursor)
                cursor--
            }
        }

        override fun onDeletePreviousWord() {
            if (cursor > 0) {
                val before = buffer.substring(0, cursor)
                val wordLen = Regex("\\S+\\s*$").find(before)?.value?.length ?: 1
                buffer = buffer.substring(0, cursor - wordLen) + buffer.substring(cursor)
                cursor -= wordLen
            }
        }

        override fun onMoveCursor(delta: Int) {
            cursor = (cursor + delta).coerceIn(0, buffer.length)
        }

        override fun onCommitCandidate(candidate: String) {
            lastCommittedCandidate = candidate
            buffer += candidate
            cursor = buffer.length
        }

        override fun onClear() {
            clearCalled = true
            buffer = ""
            cursor = 0
        }

        override fun onDone() {
            doneCalled = true
        }

        override fun getTextBeforeCursor(maxChars: Int): String {
            val start = (cursor - maxChars).coerceAtLeast(0)
            return buffer.substring(start, cursor)
        }

        override fun getTextAfterCursor(maxChars: Int): String {
            val end = (cursor + maxChars).coerceAtMost(buffer.length)
            return buffer.substring(cursor, end)
        }

        override fun getFullText(): String = buffer

        override fun getCursorPosition(): Int = cursor

        override fun setCursorPosition(newPos: Int) {
            cursor = newPos.coerceIn(0, buffer.length)
        }

        override fun replaceText(range: IntRange, text: String, newCursor: Int) {
            val safeStart = range.first.coerceIn(0, buffer.length)
            val safeEnd = (range.last + 1).coerceIn(safeStart, buffer.length)
            buffer = buffer.substring(0, safeStart) + text + buffer.substring(safeEnd)
            cursor = newCursor.coerceIn(0, buffer.length)
        }
    }

    @Test
    fun testInsertAndSurroundingText() {
        val callbacks = FakeOverlayCallbacks()
        val bridge = CoverEditorBridge(overlayCallbacks = callbacks)

        bridge.insertText("hello")
        assertEquals("hello", callbacks.buffer)
        assertEquals(5, callbacks.cursor)

        val surrounding = bridge.getSurroundingText(10, 10)
        assertEquals("hello", surrounding.textBefore)
        assertEquals("", surrounding.textAfter)
    }

    @Test
    fun testDeleteBackward() {
        val callbacks = FakeOverlayCallbacks()
        val bridge = CoverEditorBridge(overlayCallbacks = callbacks)

        bridge.insertText("test")
        bridge.deleteSurroundingText(1, 0)
        assertEquals("tes", callbacks.buffer)
        assertEquals(3, callbacks.cursor)
    }

    @Test
    fun testDeletePreviousWord() {
        val callbacks = FakeOverlayCallbacks()
        val bridge = CoverEditorBridge(overlayCallbacks = callbacks)

        bridge.insertText("hello world")
        bridge.deletePreviousWord()
        assertEquals("hello ", callbacks.buffer)
    }

    @Test
    fun testMoveCursor() {
        val callbacks = FakeOverlayCallbacks()
        val bridge = CoverEditorBridge(overlayCallbacks = callbacks)

        bridge.insertText("abcdef")
        bridge.moveCursor(-3)
        assertEquals(3, callbacks.cursor)

        val surrounding = bridge.getSurroundingText(10, 10)
        assertEquals("abc", surrounding.textBefore)
        assertEquals("def", surrounding.textAfter)
    }

    @Test
    fun testCommitCandidate() {
        val callbacks = FakeOverlayCallbacks()
        val bridge = CoverEditorBridge(overlayCallbacks = callbacks)

        bridge.commitCandidate("android", trailingSpace = true)
        assertEquals("android ", callbacks.buffer)
        assertEquals("android ", callbacks.lastCommittedCandidate)
    }

    @Test
    fun testFinishAndClear() {
        val callbacks = FakeOverlayCallbacks()
        val bridge = CoverEditorBridge(overlayCallbacks = callbacks)

        bridge.insertText("some text")
        bridge.finishInput()
        assertTrue(callbacks.doneCalled)

        bridge.clearAll()
        assertTrue(callbacks.clearCalled)
        assertEquals("", callbacks.buffer)
    }
}
