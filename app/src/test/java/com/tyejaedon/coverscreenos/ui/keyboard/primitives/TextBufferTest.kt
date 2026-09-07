package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TextBufferTest {

    // ------------- construction & invariants -------------

    @Test
    fun `empty buffer defaults to empty text and zero caret`() {
        val buf = TextBuffer.Empty
        assertEquals("", buf.text)
        assertEquals(0..0, buf.selection)
        assertNull(buf.composing)
        assertFalse(buf.hasSelection)
        assertFalse(buf.isComposing)
    }

    @Test
    fun `out of bounds selection throws`() {
        try {
            TextBuffer(text = "abc", selection = 0..10)
            fail("expected IAE")
        } catch (_: IllegalArgumentException) { /* ok */ }
    }

    @Test
    fun `inverted selection throws`() {
        try {
            TextBuffer(text = "abc", selection = 2..1)
            fail("expected IAE")
        } catch (_: IllegalArgumentException) { /* ok */ }
    }

    // ------------- applyDelta (insert) -------------

    @Test
    fun `applyDelta inserts at caret and advances selection`() {
        val buf = TextBuffer(text = "hell", selection = 4..4)
        val next = buf.applyDelta("o")
        assertEquals("hello", next.text)
        assertEquals(5..5, next.selection)
        assertNull(next.composing)
    }

    @Test
    fun `applyDelta replaces a range selection with the delta`() {
        val buf = TextBuffer(text = "hello world", selection = 6..11)
        val next = buf.applyDelta("there")
        assertEquals("hello there", next.text)
        assertEquals(11..11, next.selection)
    }

    @Test
    fun `applyDelta with empty string on range selection performs delete`() {
        val buf = TextBuffer(text = "hello world", selection = 5..11)
        val next = buf.applyDelta("")
        assertEquals("hello", next.text)
        assertEquals(5..5, next.selection)
    }

    // ------------- backspace -------------

    @Test
    fun `backspace with empty caret removes previous character`() {
        val buf = TextBuffer(text = "hello", selection = 5..5)
        val next = buf.backspace()
        assertEquals("hell", next.text)
        assertEquals(4..4, next.selection)
    }

    @Test
    fun `backspace with range selection deletes selection ignoring count`() {
        val buf = TextBuffer(text = "hello world", selection = 5..11)
        val next = buf.backspace(count = 99)
        assertEquals("hello", next.text)
        assertEquals(5..5, next.selection)
    }

    @Test
    fun `backspace at start of buffer is a no-op`() {
        val buf = TextBuffer(text = "hi", selection = 0..0)
        val next = buf.backspace()
        assertEquals("hi", next.text)
        assertEquals(0..0, next.selection)
    }

    @Test
    fun `backspace multiple deletes multiple characters`() {
        val buf = TextBuffer(text = "hello", selection = 5..5)
        val next = buf.backspace(count = 3)
        assertEquals("he", next.text)
        assertEquals(2..2, next.selection)
    }

    // ------------- composing lifecycle -------------

    @Test
    fun `replaceComposing on empty buffer starts a composing region`() {
        val buf = TextBuffer.Empty
        val next = buf.replaceComposing("h")
        assertEquals("h", next.text)
        assertEquals(1..1, next.selection)
        assertNotNull(next.composing)
        assertEquals(0..1, next.composing)
        assertEquals("h", next.composingText)
        assertTrue(next.isComposing)
    }

    @Test
    fun `replaceComposing grows the composing region as user types`() {
        val a = TextBuffer.Empty.replaceComposing("h")
        val b = a.replaceComposing("he")
        val c = b.replaceComposing("hel")
        assertEquals("hel", c.text)
        assertEquals(0..3, c.composing)
        assertEquals(3..3, c.selection)
    }

    @Test
    fun `replaceComposing with empty string clears composing`() {
        val a = TextBuffer.Empty.replaceComposing("draft")
        val b = a.replaceComposing("")
        assertEquals("", b.text)
        assertEquals(0..0, b.selection)
        assertNull(b.composing)
    }

    @Test
    fun `replaceComposing replaces prior composing text in situ`() {
        val start = TextBuffer(text = "say draft here", selection = 14..14, composing = 4..9)
        assertEquals("draft", start.composingText)
        val next = start.replaceComposing("hello")
        assertEquals("say hello here", next.text)
        assertEquals(4..9, next.composing)
        assertEquals(9..9, next.selection)
    }

    // ------------- commit -------------

    @Test
    fun `commit inserts at caret and clears composing when none active`() {
        val buf = TextBuffer(text = "hi ", selection = 3..3)
        val next = buf.commit("there")
        assertEquals("hi there", next.text)
        assertEquals(8..8, next.selection)
        assertNull(next.composing)
    }

    @Test
    fun `commit replaces active composing region`() {
        val buf = TextBuffer.Empty
            .replaceComposing("hel")
            .replaceComposing("hell")
            .replaceComposing("hello")
        val committed = buf.commit("hello")
        assertEquals("hello", committed.text)
        assertEquals(5..5, committed.selection)
        assertNull(committed.composing)
    }

    @Test
    fun `commit of longer text expands past the composing bounds`() {
        val buf = TextBuffer(text = "say abc here", selection = 12..12, composing = 4..7)
        val next = buf.commit("hello")
        assertEquals("say hello here", next.text)
        assertEquals(9..9, next.selection)
        assertNull(next.composing)
    }

    // ------------- composing survives non-overlapping edits -------------

    @Test
    fun `applyDelta before composing shifts the composing range`() {
        val start = TextBuffer(text = "ab cdef", selection = 0..0, composing = 3..7)
        val next = start.applyDelta("XX")
        assertEquals("XXab cdef", next.text)
        // composing should shift right by 2.
        assertEquals(5..9, next.composing)
        assertEquals(2..2, next.selection)
    }

    @Test
    fun `applyDelta after composing leaves composing untouched`() {
        val start = TextBuffer(text = "hello world", selection = 11..11, composing = 0..5)
        val next = start.applyDelta("!")
        assertEquals("hello world!", next.text)
        assertEquals(0..5, next.composing)
        assertEquals(12..12, next.selection)
    }

    @Test
    fun `applyDelta overlapping composing drops the composing region`() {
        val start = TextBuffer(text = "hello world", selection = 3..8, composing = 4..9)
        val next = start.applyDelta("X")
        assertEquals("helXrld", next.text)
        assertNull(next.composing)
    }

    // ------------- caret / selection helpers -------------

    @Test
    fun `withCaret clamps to text bounds`() {
        val buf = TextBuffer(text = "abc", selection = 1..1)
        assertEquals(0..0, buf.withCaret(-5).selection)
        assertEquals(3..3, buf.withCaret(99).selection)
        assertEquals(2..2, buf.withCaret(2).selection)
    }

    @Test
    fun `withSelection normalises reversed bounds`() {
        val buf = TextBuffer(text = "abcdef", selection = 0..0)
        val next = buf.withSelection(5, 2)
        assertEquals(2..5, next.selection)
        assertTrue(next.hasSelection)
    }

    // ------------- immutability -------------

    @Test
    fun `mutators return new instances`() {
        val buf = TextBuffer.Empty
        val next = buf.applyDelta("a")
        assertFalse(buf === next)
        assertEquals("", buf.text)
        assertEquals("a", next.text)
    }
}

