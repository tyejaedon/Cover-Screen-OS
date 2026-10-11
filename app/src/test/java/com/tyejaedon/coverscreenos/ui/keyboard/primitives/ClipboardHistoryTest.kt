package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Phase H acceptance: clipboard-history ring buffer ordering, max size, and de-dup of
 * identical (not necessarily consecutive) entries.
 */
class ClipboardHistoryTest {

    @Test fun `records are most-recent-first`() {
        val history = ClipboardHistory()
        history.record("first")
        history.record("second")
        history.record("third")
        assertEquals(listOf("third", "second", "first"), history.snippets)
    }

    @Test fun `re-recording an existing snippet moves it to the front without duplicating`() {
        val history = ClipboardHistory()
        history.record("alpha")
        history.record("beta")
        history.record("alpha")
        assertEquals(listOf("alpha", "beta"), history.snippets)
    }

    @Test fun `history never exceeds maxSize, dropping the oldest entry`() {
        val history = ClipboardHistory(maxSize = 3)
        listOf("1", "2", "3", "4", "5").forEach(history::record)
        assertEquals(listOf("5", "4", "3"), history.snippets)
    }

    @Test fun `blank text is ignored`() {
        val history = ClipboardHistory()
        history.record("")
        history.record("   ")
        history.record("real")
        assertEquals(listOf("real"), history.snippets)
    }

    @Test fun `clear removes every entry`() {
        val history = ClipboardHistory()
        history.record("one")
        history.record("two")
        history.clear()
        assertEquals(emptyList<String>(), history.snippets)
    }

    @Test fun `maxSize must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { ClipboardHistory(maxSize = 0) }
        assertThrows(IllegalArgumentException::class.java) { ClipboardHistory(maxSize = -1) }
    }
}
