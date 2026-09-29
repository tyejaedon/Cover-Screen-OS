package com.tyejaedon.coverscreenos.datastore

import com.tyejaedon.coverscreenos.overlay.input.CoverKeyboardMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardModePersistenceTest {
    @Test
    fun `saved modes round trip across store instances and reset independently`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = LauncherSettingsStore(context)
        store.clearSavedKeyboardModes()
        try {
            store.setKeyboardModeForPackage("com.example.chat", CoverKeyboardMode.QWERTY)
            store.setKeyboardModeForPackage("com.example.pay", CoverKeyboardMode.T9_MULTITAP)
            assertEquals(
                mapOf(
                    "com.example.chat" to CoverKeyboardMode.QWERTY,
                    "com.example.pay" to CoverKeyboardMode.T9_MULTITAP
                ),
                LauncherSettingsStore(context).settings.first().keyboardModeByPackage
            )
            store.clearSavedKeyboardModes()
            assertTrue(store.settings.first().keyboardModeByPackage.isEmpty())
        } finally {
            store.clearSavedKeyboardModes()
        }
    }
}
