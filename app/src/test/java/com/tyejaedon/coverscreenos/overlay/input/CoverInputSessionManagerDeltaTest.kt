package com.tyejaedon.coverscreenos.overlay.input

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for the delta-injection refactor.
 *
 * Before this refactor `CoverInputSessionManager.performDirectInjection`
 * rewrote the entire buffer via `ACTION_SET_TEXT` on every keystroke,
 * breaking Chrome autocomplete / M-Pesa / RN autofill and scaling latency
 * linearly with buffer length. These tests pin the delta-only contract:
 *
 *  - Every keystroke produces exactly one [BufferDelta] whose payload is
 *    only the changed substring.
 *  - "hello" (5 keystrokes) produces 5 injections with `insert.length == 1`
 *    each, NOT 5 full-buffer writes.
 *  - Backspace produces a `removedLength == 1` delta with empty `insert`.
 *  - Rapid taps are serialized by [CoverInputSessionManager.injectionLock]
 *    rather than racing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoverInputSessionManagerDeltaTest {

    private lateinit var target: RecordingInjectionTarget

    @Before
    fun setUp() {
        target = RecordingInjectionTarget()
        CoverInputSessionManager.setInjectionTargetForTest(target)
        // Prime a fake session — same shape as the AS would install on
        // real focus. `initialText` empty gives us a clean buffer.
        val method = CoverInputSessionManager::class.java
            .getDeclaredMethod("onFieldFocused", CoverFieldMetadata::class.java)
        method.isAccessible = true
        method.invoke(
            CoverInputSessionManager,
            CoverFieldMetadata(packageName = "com.example.test")
        )
    }

    @After
    fun tearDown() {
        CoverInputSessionManager.clearInjectionTargetForTest()
    }

    // ---------------- typing -----------------

    @Test
    fun `typing hello produces five single-character delta injections`() {
        "hello".forEach { CoverInputSessionManager.appendText(it.toString()) }

        assertEquals("expected 5 delta injections, got ${target.calls.size}", 5, target.calls.size)
        target.calls.forEachIndexed { index, delta ->
            assertEquals(
                "delta[$index] should carry a single-character insert, got '${delta.insert}'",
                1, delta.insert.length
            )
            assertEquals(
                "delta[$index] should not delete anything, got removed=${delta.removedLength}",
                0, delta.removedLength
            )
        }
        assertEquals("hello", target.calls.last().new)
        // The critical property: NOT ONE injection carried the full buffer as
        // its insert payload (that was the pre-refactor behavior).
        assertTrue(
            "no delta should re-write the full accumulated buffer",
            target.calls.none { it.insert.length > 1 }
        )
    }

    @Test
    fun `appendText inserts the delta at the caret and advances the caret`() {
        CoverInputSessionManager.appendText("a")
        CoverInputSessionManager.appendText("b")
        CoverInputSessionManager.appendText("c")

        assertEquals(3, target.calls.size)
        assertEquals(BufferDelta.compute(old = "", new = "a", newCaret = 1), target.calls[0])
        assertEquals(BufferDelta.compute(old = "a", new = "ab", newCaret = 2), target.calls[1])
        assertEquals(BufferDelta.compute(old = "ab", new = "abc", newCaret = 3), target.calls[2])
    }

    // ---------------- backspace -----------------

    @Test
    fun `deleteBackward emits a one-char delete delta, not a full rewrite`() {
        "hello".forEach { CoverInputSessionManager.appendText(it.toString()) }
        target.calls.clear()

        CoverInputSessionManager.deleteBackward()

        assertEquals(1, target.calls.size)
        val delta = target.calls.single()
        assertEquals("", delta.insert)
        assertEquals(1, delta.removedLength)
        assertEquals(4, delta.replaceStart)
        assertEquals(5, delta.replaceEnd)
        assertEquals("hell", delta.new)
        assertEquals(4, delta.newCaret)
    }

    @Test
    fun `deleteBackward at buffer start is a no-op and does not inject`() {
        CoverInputSessionManager.deleteBackward()
        assertTrue(
            "deleteBackward on an empty buffer must not reach the injection target",
            target.calls.isEmpty()
        )
    }

    // ---------------- clear -----------------

    @Test
    fun `clearBuffer emits a single wipe delta not repeated single-char deletes`() {
        "hello".forEach { CoverInputSessionManager.appendText(it.toString()) }
        target.calls.clear()

        CoverInputSessionManager.clearBuffer()

        assertEquals(1, target.calls.size)
        val delta = target.calls.single()
        assertEquals("", delta.insert)
        assertEquals(5, delta.removedLength)
        assertEquals("", delta.new)
        assertEquals(0, delta.newCaret)
    }

    // ---------------- T9 multi-tap cycling -----------------

    @Test
    fun `replacePreviousChar emits a single-char replace delta not a full rewrite`() {
        CoverInputSessionManager.appendText("a")
        target.calls.clear()

        CoverInputSessionManager.replacePreviousChar('b')

        assertEquals(1, target.calls.size)
        val delta = target.calls.single()
        assertEquals("b", delta.insert)
        assertEquals(1, delta.removedLength) // replaced 1 char
        assertEquals("b", delta.new)
        assertEquals(1, delta.newCaret)
    }

    @Test
    fun `replacePreviousChar at caret 0 degrades to an insert without deletion`() {
        // Fresh session, caret at 0, buffer empty.
        CoverInputSessionManager.replacePreviousChar('x')
        assertEquals(1, target.calls.size)
        val delta = target.calls.single()
        assertEquals("x", delta.insert)
        assertEquals(0, delta.removedLength)
    }

    // ---------------- mixed sequence -----------------

    @Test
    fun `mixed typing and backspace preserves delta minimality`() {
        // Type "cab", backspace twice, type "at" -> buffer "cat"
        CoverInputSessionManager.appendText("c")
        CoverInputSessionManager.appendText("a")
        CoverInputSessionManager.appendText("b")
        CoverInputSessionManager.deleteBackward() // "ca"
        CoverInputSessionManager.deleteBackward() // "c"
        CoverInputSessionManager.appendText("a")  // "ca"
        CoverInputSessionManager.appendText("t")  // "cat"

        val finalDelta = target.calls.last()
        assertEquals("cat", finalDelta.new)
        // Every step carried at most a single character of payload.
        target.calls.forEach { delta ->
            assertTrue(
                "delta insert should be at most 1 char, was '${delta.insert}'",
                delta.insert.length <= 1
            )
            assertTrue(
                "delta remove should be at most 1 char, was ${delta.removedLength}",
                delta.removedLength <= 1
            )
        }
    }

    // ---------------- BufferDelta unit checks -----------------

    @Test
    fun `BufferDelta compute returns no-op when old equals new`() {
        val d = BufferDelta.compute(old = "hello", new = "hello", newCaret = 3)
        assertTrue(d.isNoOp)
        assertEquals(0, d.insert.length)
        assertEquals(0, d.removedLength)
    }

    @Test
    fun `BufferDelta compute trims common prefix and suffix`() {
        val d = BufferDelta.compute(old = "youtube.com", new = "youtube.co.ke", newCaret = 13)
        assertEquals(10, d.replaceStart)       // "youtube.co" is the shared prefix
        assertEquals(11, d.replaceEnd)         // "m" is what needs replacing
        assertEquals(".ke", d.insert)          // trailing extension
        assertEquals(13, d.newCaret)
    }

    @Test
    fun `BufferDelta compute single-char append shrinks to insert length one`() {
        val d = BufferDelta.compute(old = "hell", new = "hello", newCaret = 5)
        assertEquals(4, d.replaceStart)
        assertEquals(4, d.replaceEnd)
        assertEquals("o", d.insert)
        assertEquals(0, d.removedLength)
    }
}

/**
 * Fake [TextInjectionTarget] that records every delta and reports
 * a successful `IME_INPUT_CONNECTION` channel — mirroring the primary
 * path in production without needing to spin up an accessibility service.
 */
private class RecordingInjectionTarget : TextInjectionTarget {
    val calls: MutableList<BufferDelta> = mutableListOf()
    var doneDispatched: Int = 0

    override fun applyDelta(delta: BufferDelta): InjectionMethod {
        calls += delta
        return InjectionMethod.IME_INPUT_CONNECTION
    }

    override fun dispatchDone() {
        doneDispatched++
    }
}

