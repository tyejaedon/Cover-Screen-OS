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

    @Test
    fun `per-app and global cover keyboard strategies round trip and resolve cleanly`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = LauncherSettingsStore(context)
        store.resetInputCustomization()
        try {
            assertEquals(
                CoverAppKeyboardStrategy.COVER_KEYBOARD,
                store.settings.first().defaultCoverAppKeyboardStrategy
            )
            store.setDefaultCoverAppKeyboardStrategy(CoverAppKeyboardStrategy.SYSTEM_IME)
            store.setCoverKeyboardStrategyForPackage("com.example.chat", CoverAppKeyboardStrategy.COVER_KEYBOARD)
            store.setCoverKeyboardStrategyForPackage("com.example.notes", CoverAppKeyboardStrategy.SYSTEM_IME)

            val reloaded = LauncherSettingsStore(context).settings.first()
            assertEquals(CoverAppKeyboardStrategy.SYSTEM_IME, reloaded.defaultCoverAppKeyboardStrategy)
            assertEquals(
                CoverAppKeyboardStrategy.COVER_KEYBOARD,
                reloaded.resolveCoverAppKeyboardStrategy("com.example.chat")
            )
            assertEquals(
                CoverAppKeyboardStrategy.SYSTEM_IME,
                reloaded.resolveCoverAppKeyboardStrategy("com.example.notes")
            )
            assertEquals(
                CoverAppKeyboardStrategy.SYSTEM_IME,
                reloaded.resolveCoverAppKeyboardStrategy("com.example.other")
            )

            // Clearing a single override falls back to global default.
            store.setCoverKeyboardStrategyForPackage("com.example.chat", null)
            assertEquals(
                CoverAppKeyboardStrategy.SYSTEM_IME,
                LauncherSettingsStore(context).settings.first().resolveCoverAppKeyboardStrategy("com.example.chat")
            )

            // JSON export/import preserves global and per-app cover keyboard strategies.
            val json = store.exportSettingsJson()
            val decoded = LauncherSettingsJson.decode(json)
            assertEquals(CoverAppKeyboardStrategy.SYSTEM_IME, decoded.defaultCoverAppKeyboardStrategy)
            assertEquals(
                mapOf("com.example.notes" to CoverAppKeyboardStrategy.SYSTEM_IME),
                decoded.coverKeyboardStrategyByPackage
            )

            store.clearSavedCoverKeyboardStrategies()
            assertTrue(store.settings.first().coverKeyboardStrategyByPackage.isEmpty())
        } finally {
            store.resetInputCustomization()
        }
    }
}
