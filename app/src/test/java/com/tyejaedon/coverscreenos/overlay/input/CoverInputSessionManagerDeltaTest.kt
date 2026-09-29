package com.tyejaedon.coverscreenos.overlay.input

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
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
    fun `refocusing same field after autofill replaces stale local buffer and caret`() {
        CoverInputSessionManager.appendText("helo")
        target.calls.clear()

        CoverInputSessionManager.onFieldFocused(
            CoverFieldMetadata(
                packageName = "com.example.test",
                initialText = "autofilled",
                selectionStart = 4,
                selectionEnd = 4
            )
        )

        assertEquals("autofilled", CoverInputSessionManager.sessionState.value.buffer)
        assertEquals(4, CoverInputSessionManager.sessionState.value.cursorPosition)
        assertTrue(target.calls.isEmpty())

        CoverInputSessionManager.appendText("X")
        assertEquals("autoXfilled", CoverInputSessionManager.sessionState.value.buffer)
        assertEquals("autofilled", target.calls.single().old)
        assertEquals(4, target.calls.single().replaceStart)
    }

    @Test
    fun `same field tap updates selection without changing text or injecting`() {
        CoverInputSessionManager.appendText("hello")
        target.calls.clear()

        CoverInputSessionManager.onFieldFocused(
            CoverFieldMetadata(
                packageName = "com.example.test",
                initialText = "hello",
                selectionStart = 2,
                selectionEnd = 2
            )
        )

        assertEquals(2, CoverInputSessionManager.sessionState.value.cursorPosition)
        assertTrue(target.calls.isEmpty())
        CoverInputSessionManager.appendText("!")
        assertEquals("he!llo", CoverInputSessionManager.sessionState.value.buffer)
    }

    @Test
    fun `password refocus does not import editor text into secure local buffer`() {
        CoverInputSessionManager.onFieldFocused(
            CoverFieldMetadata(packageName = "com.example.secure", isPassword = true)
        )
        CoverInputSessionManager.appendText("local")

        CoverInputSessionManager.onFieldFocused(
            CoverFieldMetadata(
                packageName = "com.example.secure",
                isPassword = true,
                initialText = "external"
            )
        )

        assertEquals("local", CoverInputSessionManager.sessionState.value.buffer)
        assertTrue(target.calls.isEmpty())
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

    @Test
    fun `password edits remain local until DONE and dispatch one injection`() {
        CoverInputSessionManager.onFieldFocused(
            CoverFieldMetadata(packageName = "com.example.secure", isPassword = true)
        )
        target.method = InjectionMethod.ACTION_SET_TEXT
        "secret".forEach { CoverInputSessionManager.appendText(it.toString()) }
        CoverInputSessionManager.deleteBackward()
        assertTrue(target.calls.isEmpty())
        assertEquals("secre", CoverInputSessionManager.sessionState.value.buffer)

        CoverInputSessionManager.commitAndFinish()
        assertEquals(1, target.calls.size)
        assertEquals("secre", target.calls.single().new)
        assertEquals(1, target.doneDispatched)
    }

    @Test
    fun `failed password injection does not submit or use clipboard`() {
        CoverInputSessionManager.onFieldFocused(
            CoverFieldMetadata(packageName = "com.example.secure", isPassword = true)
        )
        target.method = InjectionMethod.NONE
        CoverInputSessionManager.appendText("secret")
        CoverInputSessionManager.commitAndFinish()
        assertEquals(1, target.calls.size)
        assertEquals(0, target.doneDispatched)
        assertFalse(CoverInputSessionManager.sessionState.value.isSuccessFeedback)
        assertTrue(CoverInputSessionManager.sessionState.value.isActive)
    }

    @Test
    fun `saved app mode survives refocus but numeric fields force PIN`() {
        CoverInputSessionManager.switchMode(CoverKeyboardMode.QWERTY)
        CoverInputSessionManager.dismissOverlay("test")
        CoverInputSessionManager.onFieldFocused(CoverFieldMetadata(packageName = "com.example.test"))
        assertEquals(CoverKeyboardMode.QWERTY, CoverInputSessionManager.sessionState.value.keyboardMode)
        CoverInputSessionManager.onFieldFocused(
            CoverFieldMetadata(packageName = "com.example.test", isNumeric = true)
        )
        assertEquals(CoverKeyboardMode.NUMERIC_PIN, CoverInputSessionManager.sessionState.value.keyboardMode)
        CoverInputSessionManager.switchMode(CoverKeyboardMode.QWERTY)
        assertEquals(CoverKeyboardMode.NUMERIC_PIN, CoverInputSessionManager.sessionState.value.keyboardMode)
        CoverInputSessionManager.onFieldFocused(CoverFieldMetadata(packageName = "com.example.test"))
        assertEquals(CoverKeyboardMode.QWERTY, CoverInputSessionManager.sessionState.value.keyboardMode)
    }

    @Test
    fun `NEXT action does not overwrite a newly focused field session`() {
        target.onDone = {
            CoverInputSessionManager.onFieldFocused(
                CoverFieldMetadata(packageName = "com.example.test", viewIdResourceName = "second")
            )
        }
        CoverInputSessionManager.appendText("first")
        CoverInputSessionManager.commitAndFinish()
        assertEquals("second", CoverInputSessionManager.sessionState.value.metadata.viewIdResourceName)
        assertTrue(CoverInputSessionManager.sessionState.value.isActive)
    }

    @Test
    fun `candidate replaces word before caret in one delta`() {
        CoverInputSessionManager.appendText("hel")
        target.calls.clear()
        CoverInputSessionManager.commitCandidate("hello")
        assertEquals("hello", CoverInputSessionManager.sessionState.value.buffer)
        assertEquals(1, target.calls.size)
        assertEquals("lo", target.calls.single().insert)
        assertEquals(0, target.calls.single().removedLength)
    }

    @Test
    fun `delete word removes word and trailing whitespace in one delta`() {
        CoverInputSessionManager.appendText("hello there ")
        target.calls.clear()
        CoverInputSessionManager.deletePreviousWord()
        assertEquals("hello ", CoverInputSessionManager.sessionState.value.buffer)
        assertEquals(1, target.calls.size)
        assertEquals(6, target.calls.single().removedLength)
    }

    @Test
    fun `secure fields ignore candidate commits`() {
        CoverInputSessionManager.onFieldFocused(
            CoverFieldMetadata(packageName = "com.example.secure", isPassword = true)
        )
        CoverInputSessionManager.appendText("sec")
        CoverInputSessionManager.commitCandidate("secret")
        assertEquals("sec", CoverInputSessionManager.sessionState.value.buffer)
        assertTrue(target.calls.isEmpty())
    }

    @Test
    fun `cursor moves clamp within the active buffer`() {
        CoverInputSessionManager.appendText("abc")
        CoverInputSessionManager.moveCursor(-2)
        assertEquals(1, CoverInputSessionManager.sessionState.value.cursorPosition)
        CoverInputSessionManager.appendText("X")
        assertEquals("aXbc", CoverInputSessionManager.sessionState.value.buffer)
        CoverInputSessionManager.moveCursor(100)
        assertEquals(4, CoverInputSessionManager.sessionState.value.cursorPosition)
    }

    @Test
    fun `predictive T9 digits wait for a candidate and commit once`() {
        CoverInputSessionManager.setT9Predictive(true)
        assertTrue(CoverInputSessionManager.sessionState.value.isT9Predictive)
        "43556".forEach(CoverInputSessionManager::tapPredictiveDigit)
        assertEquals("43556", CoverInputSessionManager.sessionState.value.t9PredictiveDigits)
        assertEquals("", CoverInputSessionManager.sessionState.value.buffer)
        assertTrue(target.calls.isEmpty())

        CoverInputSessionManager.commitCandidate("hello")
        assertEquals("hello", CoverInputSessionManager.sessionState.value.buffer)
        assertEquals("", CoverInputSessionManager.sessionState.value.t9PredictiveDigits)
        assertEquals(1, target.calls.size)
        assertEquals("hello", target.calls.single().insert)
    }

    @Test
    fun `pending predictive digits backspace locally and block submission`() {
        CoverInputSessionManager.setT9Predictive(true)
        CoverInputSessionManager.tapPredictiveDigit('4')
        CoverInputSessionManager.tapPredictiveDigit('3')
        CoverInputSessionManager.deleteBackward()
        assertEquals("4", CoverInputSessionManager.sessionState.value.t9PredictiveDigits)
        assertTrue(target.calls.isEmpty())
        CoverInputSessionManager.appendText(" ")
        CoverInputSessionManager.commitAndFinish()
        assertEquals("", CoverInputSessionManager.sessionState.value.buffer)
        assertEquals(0, target.doneDispatched)
        CoverInputSessionManager.deleteBackward()
        assertEquals("", CoverInputSessionManager.sessionState.value.t9PredictiveDigits)
    }

    @Test
    fun `predictive T9 cannot activate on password or numeric fields`() {
        CoverInputSessionManager.onFieldFocused(
            CoverFieldMetadata(packageName = "com.example.secure", isPassword = true)
        )
        CoverInputSessionManager.setT9Predictive(true)
        CoverInputSessionManager.tapPredictiveDigit('4')
        assertFalse(CoverInputSessionManager.sessionState.value.isT9Predictive)
        assertEquals("", CoverInputSessionManager.sessionState.value.t9PredictiveDigits)
        CoverInputSessionManager.onFieldFocused(
            CoverFieldMetadata(packageName = "com.example.secure", isNumeric = true)
        )
        CoverInputSessionManager.setT9Predictive(true)
        assertFalse(CoverInputSessionManager.sessionState.value.isT9Predictive)
    }

    @Test
    fun `DONE retries failed injection and does not submit if it still fails`() {
        target.method = InjectionMethod.NONE
        CoverInputSessionManager.appendText("hello")
        CoverInputSessionManager.commitAndFinish()
        assertEquals(2, target.calls.size)
        assertEquals("hello", target.calls.last().insert)
        assertEquals(0, target.doneDispatched)
        assertTrue(CoverInputSessionManager.sessionState.value.isActive)

        target.method = InjectionMethod.ACTION_SET_TEXT
        CoverInputSessionManager.commitAndFinish()
        assertEquals(3, target.calls.size)
        assertEquals("hello", target.calls.last().insert)
        assertEquals(1, target.doneDispatched)
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
    var method: InjectionMethod = InjectionMethod.IME_INPUT_CONNECTION
    var onDone: (() -> Unit)? = null

    override fun applyDelta(delta: BufferDelta): InjectionMethod {
        calls += delta
        return method
    }

    override fun dispatchDone() {
        doneDispatched++
        onDone?.invoke()
    }
}
