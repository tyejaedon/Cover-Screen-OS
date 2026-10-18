package com.coveros.keyboard.core.contracts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardContractsTest {

    @Test
    fun testLayoutModeValues() {
        val modes = LayoutMode.values()
        assertEquals(5, modes.size)
        assertTrue(modes.contains(LayoutMode.QWERTY_COMPACT))
        assertTrue(modes.contains(LayoutMode.T9_PREDICTIVE))
        assertTrue(modes.contains(LayoutMode.SYMBOLS_PRIMARY))
        assertTrue(modes.contains(LayoutMode.SYMBOLS_SECONDARY))
        assertTrue(modes.contains(LayoutMode.NUMBER_PAD))
    }

    @Test
    fun testShiftStateValues() {
        val states = ShiftState.values()
        assertEquals(3, states.size)
        assertTrue(states.contains(ShiftState.LOWERCASE))
        assertTrue(states.contains(ShiftState.SINGLE_SHIFT))
        assertTrue(states.contains(ShiftState.CAPS_LOCK))
    }

    @Test
    fun testKeyModelDefaultsAndProperties() {
        val key = KeyModel(
            id = 42,
            primaryLabel = "a",
            secondaryLabel = "@",
            t9Chars = "abc",
            bounds = Rect(0f, 0f, 50f, 60f),
            centroid = Offset(25f, 30f),
            isFunctional = false
        )

        assertEquals(42, key.id)
        assertEquals("a", key.primaryLabel)
        assertEquals("@", key.secondaryLabel)
        assertEquals("abc", key.t9Chars)
        assertEquals(Rect(0f, 0f, 50f, 60f), key.bounds)
        assertEquals(Offset(25f, 30f), key.centroid)
        assertFalse(key.isFunctional)
    }

    @Test
    fun testCandidateWordProperties() {
        val candidate = CandidateWord(
            word = "cover",
            score = 95.5f,
            isAutocorrectDefault = true
        )

        assertEquals("cover", candidate.word)
        assertEquals(95.5f, candidate.score, 0.001f)
        assertTrue(candidate.isAutocorrectDefault)
    }

    @Test
    fun testInputActionHierarchy() {
        val key = KeyModel(id = 1, primaryLabel = "k")

        val tap: InputAction = InputAction.KeyTap(key, Offset(10f, 10f))
        val longPress: InputAction = InputAction.LongPressSelect(key, "$")
        val glide: InputAction = InputAction.GlideComplete(listOf(Offset.Zero, Offset(10f, 10f)))
        val spacebar: InputAction = InputAction.SpacebarCursorDelta(1)
        val delete: InputAction = InputAction.GestureDeleteUpdate(2, isFinalCommit = true)
        val mode: InputAction = InputAction.ModeSwitch(LayoutMode.T9_PREDICTIVE)

        assertNotNull(tap)
        assertNotNull(longPress)
        assertNotNull(glide)
        assertNotNull(spacebar)
        assertNotNull(delete)
        assertNotNull(mode)
    }
}
