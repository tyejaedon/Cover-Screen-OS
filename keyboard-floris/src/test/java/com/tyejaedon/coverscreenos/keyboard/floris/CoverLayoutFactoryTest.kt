package com.tyejaedon.coverscreenos.keyboard.floris

import com.tyejaedon.coverscreenos.keyboard.floris.layout.FlorisLayoutParser
import com.tyejaedon.coverscreenos.keyboard.floris.layout.FlorisLayoutType
import com.tyejaedon.coverscreenos.keyboard.floris.layout.KeyCode
import com.tyejaedon.coverscreenos.keyboard.floris.layout.PopupSet
import com.tyejaedon.coverscreenos.keyboard.floris.layout.TextKeyData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        assertEquals("1", rowsLower[0][0].secondaryLabel)
        assertEquals("keyboard_top_0", rowsLower[0][0].tag)
        assertEquals("p", rowsLower[0][9].primaryLabel)
        assertEquals("0", rowsLower[0][9].secondaryLabel)
        assertEquals("keyboard_top_9", rowsLower[0][9].tag)

        // Verify PopupSet on 'e' has main '3' and relevant accented characters
        val keyE = rowsLower[0][2]
        assertEquals("e", keyE.primaryLabel)
        assertEquals("3", keyE.popupSet.main?.label)
        assertTrue(keyE.popupOptions.contains("é"))

        val rowsShifted = CoverLayoutFactory.createQwertyRows(CoverShiftState.SINGLE_SHIFT)
        assertEquals("Q", rowsShifted[0][0].primaryLabel)
        assertEquals("P", rowsShifted[0][9].primaryLabel)
        assertTrue(rowsShifted[0][2].popupOptions.contains("É"))
    }

    @Test
    fun testQwertyWithIncludeClearKey() {
        val rowsWithClear = CoverLayoutFactory.createQwertyRows(
            shiftState = CoverShiftState.LOWERCASE,
            includeClearKey = true
        )
        assertEquals(6, rowsWithClear[3].size)
        val clearKey = rowsWithClear[3].firstOrNull { it.tag == "keyboard_clear" }
        assertNotNull(clearKey)
        assertEquals(CoverKeyAction.ClearOrUndo, clearKey?.action)
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
    fun testFlorisSymbolsPrimaryAndSecondaryLayouts() {
        val primarySymbols = CoverLayoutFactory.createSymbolsRows()
        assertEquals(4, primarySymbols.size)
        // Secondary toggle key =/< switches to SYMBOLS_SECONDARY
        assertEquals("=/<", primarySymbols[2][0].primaryLabel)
        assertEquals(CoverKeyAction.SwitchToSymbolsSecondary, primarySymbols[2][0].action)

        val secondarySymbols = CoverLayoutFactory.createSymbolsSecondaryRows()
        assertEquals(4, secondarySymbols.size)
        assertEquals("~", secondarySymbols[0][0].primaryLabel)
        assertEquals("?123", secondarySymbols[2][0].primaryLabel)
        assertEquals(CoverKeyAction.SwitchToSymbols, secondarySymbols[2][0].action)

        val compactSymbols = CoverLayoutFactory.createSymbolsRows(compactVariant = true)
        assertEquals("#", compactSymbols[2][0].primaryLabel)
        assertEquals("keyboard_symbol_0", compactSymbols[2][0].tag)
    }

    @Test
    fun testFlorisLayoutParserAndPopupSetMerge() {
        val arrangement = FlorisLayoutParser.loadArrangement(FlorisLayoutType.NUMERIC)
        assertEquals("numeric", arrangement.type)
        assertEquals(4, arrangement.arrangement.size)
        assertEquals(KeyCode.CLEAR_INPUT, arrangement.arrangement[3][0].code)

        val p1 = PopupSet(
            main = TextKeyData(code = 49, label = "1"),
            relevant = listOf(TextKeyData(code = 233, label = "é"))
        )
        val p2 = PopupSet(
            main = TextKeyData(code = 99, label = "c"),
            relevant = listOf(TextKeyData(code = 232, label = "è"))
        )
        val merged = p1.merge(p2)
        assertEquals("1", merged.main?.label)
        assertEquals(listOf("c", "é", "è"), merged.relevant.map { it.label })
        assertFalse(merged.isEmpty())
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
