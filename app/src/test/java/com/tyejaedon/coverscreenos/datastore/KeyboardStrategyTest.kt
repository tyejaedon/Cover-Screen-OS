package com.tyejaedon.coverscreenos.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardStrategyTest {

    @Test
    fun `default strategy is the on-cover T9 keypad`() {
        assertEquals(KeyboardStrategy.T9, DEFAULT_KEYBOARD_STRATEGY)
        assertEquals(DEFAULT_KEYBOARD_STRATEGY, LauncherSettings().keyboardStrategy)
    }

    @Test
    fun `fromStorageValue round-trips every persisted enum name`() {
        KeyboardStrategy.entries.forEach { strategy ->
            assertEquals(strategy, KeyboardStrategy.fromStorageValue(strategy.name))
        }
    }

    @Test
    fun `fromStorageValue falls back to default for null blank and legacy values`() {
        assertEquals(DEFAULT_KEYBOARD_STRATEGY, KeyboardStrategy.fromStorageValue(null))
        assertEquals(DEFAULT_KEYBOARD_STRATEGY, KeyboardStrategy.fromStorageValue(""))
        assertEquals(DEFAULT_KEYBOARD_STRATEGY, KeyboardStrategy.fromStorageValue("   "))
        assertEquals(DEFAULT_KEYBOARD_STRATEGY, KeyboardStrategy.fromStorageValue("GBOARD_ONLY"))
        // Enum matching is intentionally case-sensitive so we never silently accept junk.
        assertEquals(DEFAULT_KEYBOARD_STRATEGY, KeyboardStrategy.fromStorageValue("system_ime"))
    }

    @Test
    fun `toggled flips between the two supported strategies`() {
        assertEquals(KeyboardStrategy.SYSTEM_IME, KeyboardStrategy.T9.toggled())
        assertEquals(KeyboardStrategy.T9, KeyboardStrategy.SYSTEM_IME.toggled())
        KeyboardStrategy.entries.forEach { strategy ->
            assertEquals(strategy, strategy.toggled().toggled())
        }
    }

    @Test
    fun `isSystemIme only reports true for the system IME strategy`() {
        assertTrue(KeyboardStrategy.SYSTEM_IME.isSystemIme)
        assertFalse(KeyboardStrategy.T9.isSystemIme)
    }
}

