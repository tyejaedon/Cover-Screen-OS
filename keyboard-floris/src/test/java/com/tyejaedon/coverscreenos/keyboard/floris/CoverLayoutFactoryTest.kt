package com.tyejaedon.coverscreenos.keyboard.floris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverLayoutFactoryTest {

    @Test
    fun testQwertyLayoutRows() {
        val rowsLower = CoverLayoutFactory.createQwertyRows(CoverShiftState.LOWERCASE)
        assertEquals(4, rowsLower.size)
        assertEquals(10, rowsLower[0].size) // q-p
        assertEquals(9, rowsLower[1].size)  // a-l
        assertEquals(9, rowsLower[2].size)  // shift + z-m + backspace
        assertEquals(5, rowsLower[3].size)  // ?123, comma, space, dot, enter

        assertEquals("q", rowsLower[0][0].primaryLabel)
        assertEquals("keyboard_top_0", rowsLower[0][0].tag)
        assertEquals("p", rowsLower[0][9].primaryLabel)
        assertEquals("keyboard_top_9", rowsLower[0][9].tag)

        val rowsShifted = CoverLayoutFactory.createQwertyRows(CoverShiftState.SINGLE_SHIFT)
        assertEquals("Q", rowsShifted[0][0].primaryLabel)
        assertEquals("P", rowsShifted[0][9].primaryLabel)
    }

    @Test
    fun testT9LayoutRows() {
        val t9Rows = CoverLayoutFactory.createT9Rows()
        assertEquals(4, t9Rows.size)
        // 3 keys per row in telephone keypad
        assertEquals(3, t9Rows[0].size) // 1, 2, 3
        assertEquals(3, t9Rows[1].size) // 4, 5, 6
        assertEquals(3, t9Rows[2].size) // 7, 8, 9
        assertEquals(3, t9Rows[3].size) // ABC, 0, Backspace

        val key2 = t9Rows[0][1]
        assertEquals("2", key2.primaryLabel)
        assertEquals("abc", key2.t9Chars)

        val key9 = t9Rows[2][2]
        assertEquals("9", key9.primaryLabel)
        assertEquals("wxyz", key9.t9Chars)
    }

    @Test
    fun testShiftStateTransitions() {
        val lower = CoverShiftState.LOWERCASE
        val single = lower.nextState(0L, 1000L)
        assertEquals(CoverShiftState.SINGLE_SHIFT, single)

        // Rapid double tap -> CAPS_LOCK
        val caps = single.nextState(1000L, 1200L)
        assertEquals(CoverShiftState.CAPS_LOCK, caps)

        // Tap on CAPS_LOCK -> LOWERCASE
        val backToLower = caps.nextState(1200L, 2000L)
        assertEquals(CoverShiftState.LOWERCASE, backToLower)
    }
}
