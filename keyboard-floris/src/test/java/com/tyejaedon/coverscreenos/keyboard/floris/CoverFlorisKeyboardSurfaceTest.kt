package com.tyejaedon.coverscreenos.keyboard.floris

import com.tyejaedon.coverscreenos.keyboard.core.contracts.InputAction
import com.tyejaedon.coverscreenos.keyboard.core.contracts.KeyModel
import com.tyejaedon.coverscreenos.keyboard.core.contracts.LayoutMode
import com.tyejaedon.coverscreenos.keyboard.nlp.CompactTrieDictionary
import com.tyejaedon.coverscreenos.keyboard.nlp.DTWGlideDecoder
import com.tyejaedon.coverscreenos.keyboard.nlp.QwertyGeometry
import com.tyejaedon.coverscreenos.keyboard.nlp.T9PredictiveEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            cursor = buffer.length
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
            val word = buffer.takeLastWhile { it.isLetterOrDigit() }
            buffer = buffer.dropLast(word.length) + candidate
            cursor = buffer.length
        }

        override fun onClear() {
            buffer = ""
            cursor = 0
        }

        override fun onDone() {
            doneCalled = true
        }

        override fun getTextBeforeCursor(maxChars: Int): String = buffer.takeLast(maxChars)

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
    fun testKeyActionDispatchingAndMutationRevision() {
        val callbacks = TestOverlayCallbacks()
        val bridge = CoverEditorBridge(overlayCallbacks = callbacks)
        assertEquals(0L, bridge.mutationRevision.value)

        // Simulate typing through layout key actions
        val qwertyRows = CoverLayoutFactory.createQwertyRows(CoverShiftState.LOWERCASE)
        val keyQ = qwertyRows[0][0]
        assertEquals("q", keyQ.primaryLabel)

        if (keyQ.action is CoverKeyAction.InputChar) {
            bridge.insertText(keyQ.action.char.toString())
        }
        assertEquals("q", callbacks.buffer)
        assertEquals(1L, bridge.mutationRevision.value)

        // Simulate space action
        bridge.insertText(" ")
        assertEquals("q ", callbacks.buffer)
        assertEquals(2L, bridge.mutationRevision.value)

        // Simulate backspace action
        bridge.deleteSurroundingText(1, 0)
        assertEquals("q", callbacks.buffer)
        assertEquals(3L, bridge.mutationRevision.value)
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

    @Test
    fun testFlorisTextInputManagerT9PredictiveAndMultiTapAndClearUndo() {
        val callbacks = TestOverlayCallbacks()
        val bridge = CoverEditorBridge(overlayCallbacks = callbacks)
        val trie = CompactTrieDictionary(mapOf("hello" to 1000L, "help" to 800L))
        val t9Engine = T9PredictiveEngine(trie)
        val glideDecoder = DTWGlideDecoder(trie)

        val manager = FlorisTextInputManager(
            bridge = bridge,
            initialMode = CoverLayoutMode.T9_PREDICTIVE,
            t9EngineProvider = { t9Engine },
            glideDecoderProvider = { glideDecoder },
            autocorrectProvider = { if (it == "helo") "hello" else it }
        )

        // 1. Predictive T9 typing: "43556" -> "hello"
        "43556".forEachIndexed { idx, digit ->
            manager.handleT9DigitTap(digit, nowMs = 1000L + idx * 100L)
        }
        assertEquals("hello", callbacks.buffer)

        // 2. Long-press Clear saves text and single-tap Clear restores it
        assertFalse(manager.handleClearTapUndo()) // Not empty -> does not wipe
        assertEquals("hello", callbacks.buffer)
        assertTrue(manager.handleClearLongPress())
        assertEquals("", callbacks.buffer)
        assertTrue(manager.handleClearTapUndo())
        assertEquals("hello", callbacks.buffer)

        // 3. Multi-tap T9 cycling: '2' twice within 650ms -> 'b'
        manager.handleClearLongPress()
        manager.isT9Predictive = false
        manager.handleT9DigitTap('2', "abc", nowMs = 5000L)
        assertEquals("a", callbacks.buffer)
        manager.handleT9DigitTap('2', "abc", nowMs = 5200L)
        assertEquals("b", callbacks.buffer)

        // 4. Switch to QWERTY and test autocorrect on Space + GlideComplete
        manager.dispatchInputAction(InputAction.ModeSwitch(LayoutMode.QWERTY_COMPACT))
        assertEquals(CoverLayoutMode.QWERTY, manager.activeMode)
        callbacks.buffer = "helo"
        manager.handleSpace(autocorrectEnabled = true)
        assertEquals("hello ", callbacks.buffer)

        // 5. GlideComplete action
        callbacks.buffer = ""
        val centroids = QwertyGeometry.getDefaultCentroidMap()
        val glidePath = listOf('h', 'e', 'l', 'o').mapNotNull { centroids[it] }
        manager.dispatchInputAction(InputAction.GlideComplete(glidePath))
        assertEquals("hello", callbacks.buffer)
    }

    @Test
    fun `normalizeGlidePathToQwerty maps container coordinates to QwertyGeometry centroids`() {
        val w = 1000f
        val h = 400f
        // Center of 'q' (col 0, row 0) = (50f, 50f) -> (0.0f, 0.0f)
        // Center of 'p' (col 9, row 0) = (950f, 50f) -> (9.0f, 0.0f)
        // Center of 'a' (col 0.5, row 1) = (100f, 150f) -> (0.5f, 1.0f)
        // Center of 'z' (col 1.5, row 2) = (200f, 250f) -> (1.5f, 2.0f)
        val raw = listOf(
            androidx.compose.ui.geometry.Offset(50f, 50f),
            androidx.compose.ui.geometry.Offset(950f, 50f),
            androidx.compose.ui.geometry.Offset(100f, 150f),
            androidx.compose.ui.geometry.Offset(200f, 250f)
        )
        val normalized = normalizeGlidePathToQwerty(raw, w, h)
        assertEquals(0.0f, normalized[0].x, 0.01f)
        assertEquals(0.0f, normalized[0].y, 0.01f)
        assertEquals(9.0f, normalized[1].x, 0.01f)
        assertEquals(0.0f, normalized[1].y, 0.01f)
        assertEquals(0.5f, normalized[2].x, 0.01f)
        assertEquals(1.0f, normalized[2].y, 0.01f)
        assertEquals(1.5f, normalized[3].x, 0.01f)
        assertEquals(2.0f, normalized[3].y, 0.01f)
    }

    @Test
    fun `FlorisThemePalette AmoledCover and Numeric and T9 rows with Clear key and trace callbacks work`() {
        val callbacks = TestOverlayCallbacks()
        val bridge = CoverEditorBridge(overlayCallbacks = callbacks)
        val traces = mutableListOf<Pair<String, String>>()

        val palette = FlorisThemePalette.AmoledCover
        assertEquals(FlorisThemeMode.AMOLED_COVER, palette.mode)
        assertEquals(0xFF141416L, palette.keyboardSurface.value.shr(32).toLong() and 0xFFFFFFFFL)

        val numericRows = CoverLayoutFactory.createNumericRows(includeClearKey = true)
        assertEquals(4, numericRows.size)
        assertTrue(numericRows.last().any { it.action == CoverKeyAction.ClearOrUndo && it.tag == "keyboard_clear" })

        val t9Rows = CoverLayoutFactory.createT9Rows(includeClearKey = true)
        assertEquals(4, t9Rows.size)
        assertTrue(t9Rows.last().any { it.action == CoverKeyAction.ClearOrUndo && it.tag == "keyboard_clear" })

        val manager = FlorisTextInputManager(
            bridge = bridge,
            initialMode = CoverLayoutMode.T9_PREDICTIVE,
            onT9TraceUpdated = { pred, multi -> traces.add(pred to multi) }
        )
        manager.isT9Predictive = false
        manager.handleT9DigitTap('4', "ghi", nowMs = 1000L)
        manager.handleT9DigitTap('4', "ghi", nowMs = 1200L)
        manager.handleT9DigitTap('3', "def", nowMs = 1500L)
        assertEquals("hd", callbacks.buffer)
        assertEquals("" to "44-3", traces.last())
    }
}
