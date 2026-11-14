package com.tyejaedon.coverscreenos.keyboard.core.contracts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardContractsTest {

    @Test
    fun `LayoutMode enum has expected values`() {
        val modes = LayoutMode.entries
        assertEquals(5, modes.size)
        assertTrue(modes.contains(LayoutMode.QWERTY_COMPACT))
        assertTrue(modes.contains(LayoutMode.T9_PREDICTIVE))
        assertTrue(modes.contains(LayoutMode.SYMBOLS_PRIMARY))
        assertTrue(modes.contains(LayoutMode.SYMBOLS_SECONDARY))
        assertTrue(modes.contains(LayoutMode.NUMBER_PAD))
    }

    @Test
    fun `ShiftState transitions correctly`() {
        val states = ShiftState.entries
        assertEquals(3, states.size)
        assertEquals(ShiftState.LOWERCASE, ShiftState.valueOf("LOWERCASE"))
        assertEquals(ShiftState.SINGLE_SHIFT, ShiftState.valueOf("SINGLE_SHIFT"))
        assertEquals(ShiftState.CAPS_LOCK, ShiftState.valueOf("CAPS_LOCK"))
    }

    @Test
    fun `KeyModel instantiates with default values`() {
        val key = KeyModel(
            id = 1,
            primaryLabel = "a",
            bounds = Rect(0f, 0f, 10f, 20f),
            centroid = Offset(5f, 10f)
        )
        assertEquals(1, key.id)
        assertEquals("a", key.primaryLabel)
        assertNull(key.secondaryLabel)
        assertEquals("", key.t9Chars)
        assertFalse(key.isFunctional)
    }

    @Test
    fun `CandidateWord retains scores and flags`() {
        val candidate = CandidateWord("test", 0.95f, isAutocorrectDefault = true)
        assertEquals("test", candidate.word)
        assertEquals(0.95f, candidate.score, 0.001f)
        assertTrue(candidate.isAutocorrectDefault)
    }

    @Test
    fun `InputAction subclasses hold correct payload`() {
        val key = KeyModel(1, "b")
        val tap = InputAction.KeyTap(key, Offset(2f, 3f))
        assertEquals(key, tap.key)
        assertEquals(Offset(2f, 3f), tap.touchOffset)

        val longPress = InputAction.LongPressSelect(key, "β")
        assertEquals("β", longPress.selectedPopupChar)

        val points = listOf(Offset.Zero, Offset(10f, 10f))
        val glide = InputAction.GlideComplete(points)
        assertEquals(2, glide.sampledPoints.size)

        val cursor = InputAction.SpacebarCursorDelta(3)
        assertEquals(3, cursor.charDelta)

        val delete = InputAction.GestureDeleteUpdate(2, isFinalCommit = true)
        assertEquals(2, delete.wordsToDelete)
        assertTrue(delete.isFinalCommit)

        val modeSwitch = InputAction.ModeSwitch(LayoutMode.NUMBER_PAD)
        assertEquals(LayoutMode.NUMBER_PAD, modeSwitch.targetMode)
    }
}
