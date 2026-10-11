package com.tyejaedon.coverscreenos.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Phase D acceptance: the chosen keyboard height/anchor persists across app restarts
 * (modeled as a fresh [LauncherSettingsStore] instance reading back a prior instance's
 * writes), and out-of-range values are clamped on both write and read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardResizePersistenceTest {

    @Test
    fun `normalizeExtraKeyHeightDp clamps to the documented bounds`() {
        assertEquals(MIN_EXTRA_KEY_HEIGHT_DP, normalizeExtraKeyHeightDp(-50f))
        assertEquals(MAX_EXTRA_KEY_HEIGHT_DP, normalizeExtraKeyHeightDp(50f))
        assertEquals(STANDARD_EXTRA_KEY_HEIGHT_DP, normalizeExtraKeyHeightDp(STANDARD_EXTRA_KEY_HEIGHT_DP))
        assertEquals(COMPACT_EXTRA_KEY_HEIGHT_DP, normalizeExtraKeyHeightDp(Float.NaN))
    }

    @Test
    fun `defaults match the documented Phase D defaults`() {
        val defaults = LauncherSettings()
        assertEquals(COMPACT_EXTRA_KEY_HEIGHT_DP, defaults.keyboardExtraHeightDp)
        assertEquals(false, defaults.keyboardOneHandedEnabled)
        assertEquals(KeyboardAnchor.Left, defaults.keyboardOneHandedAnchor)
    }

    @Test
    fun `extra height preference round trips and clamps out-of-range writes`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = LauncherSettingsStore(context)
        try {
            store.setKeyboardExtraHeightDp(STANDARD_EXTRA_KEY_HEIGHT_DP)
            assertEquals(
                STANDARD_EXTRA_KEY_HEIGHT_DP,
                LauncherSettingsStore(context).settings.first().keyboardExtraHeightDp
            )

            // Out-of-range writes clamp rather than persisting a value that would break the
            // [44, 88]dp key-height ceiling.
            store.setKeyboardExtraHeightDp(1000f)
            assertEquals(
                MAX_EXTRA_KEY_HEIGHT_DP,
                LauncherSettingsStore(context).settings.first().keyboardExtraHeightDp
            )
            store.setKeyboardExtraHeightDp(-1000f)
            assertEquals(
                MIN_EXTRA_KEY_HEIGHT_DP,
                LauncherSettingsStore(context).settings.first().keyboardExtraHeightDp
            )
        } finally {
            store.setKeyboardExtraHeightDp(COMPACT_EXTRA_KEY_HEIGHT_DP)
        }
    }

    @Test
    fun `one-handed enabled and anchor preferences round trip across store instances`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = LauncherSettingsStore(context)
        try {
            store.setKeyboardOneHandedEnabled(true)
            store.setKeyboardOneHandedAnchor(KeyboardAnchor.Right)
            val reloaded = LauncherSettingsStore(context).settings.first()
            assertEquals(true, reloaded.keyboardOneHandedEnabled)
            assertEquals(KeyboardAnchor.Right, reloaded.keyboardOneHandedAnchor)

            store.setKeyboardOneHandedAnchor(KeyboardAnchor.Left)
            assertEquals(
                KeyboardAnchor.Left,
                LauncherSettingsStore(context).settings.first().keyboardOneHandedAnchor
            )
        } finally {
            store.setKeyboardOneHandedEnabled(false)
            store.setKeyboardOneHandedAnchor(KeyboardAnchor.Left)
        }
    }
}
