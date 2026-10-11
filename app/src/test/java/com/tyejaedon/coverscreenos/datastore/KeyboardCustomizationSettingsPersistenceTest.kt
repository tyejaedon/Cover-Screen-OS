package com.tyejaedon.coverscreenos.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Phase G acceptance: each of the four keyboard-customization toggles changes behavior live
 * and persists across app restart (modeled as a fresh [LauncherSettingsStore] instance
 * reading back what a prior instance wrote).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardCustomizationSettingsPersistenceTest {

    @Test
    fun `defaults match the documented Phase G defaults`() {
        val defaults = LauncherSettings()
        assertEquals(false, defaults.keyPressSoundEnabled)
        assertTrue(defaults.suggestionStripVisible)
        assertTrue(defaults.autocorrectEnabled)
        assertTrue(defaults.popupKeyPreviewEnabled)
    }

    @Test
    fun `key press sound preference round trips across store instances`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = LauncherSettingsStore(context)
        try {
            store.setKeyPressSoundEnabled(true)
            assertTrue(LauncherSettingsStore(context).settings.first().keyPressSoundEnabled)
            store.setKeyPressSoundEnabled(false)
            assertEquals(false, LauncherSettingsStore(context).settings.first().keyPressSoundEnabled)
        } finally {
            store.setKeyPressSoundEnabled(false)
        }
    }

    @Test
    fun `suggestion strip visibility preference round trips across store instances`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = LauncherSettingsStore(context)
        try {
            store.setSuggestionStripVisible(false)
            assertEquals(false, LauncherSettingsStore(context).settings.first().suggestionStripVisible)
            store.setSuggestionStripVisible(true)
            assertTrue(LauncherSettingsStore(context).settings.first().suggestionStripVisible)
        } finally {
            store.setSuggestionStripVisible(true)
        }
    }

    @Test
    fun `autocorrect preference round trips across store instances`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = LauncherSettingsStore(context)
        try {
            store.setAutocorrectEnabled(false)
            assertEquals(false, LauncherSettingsStore(context).settings.first().autocorrectEnabled)
            store.setAutocorrectEnabled(true)
            assertTrue(LauncherSettingsStore(context).settings.first().autocorrectEnabled)
        } finally {
            store.setAutocorrectEnabled(true)
        }
    }

    @Test
    fun `popup key preview preference round trips across store instances`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = LauncherSettingsStore(context)
        try {
            store.setPopupKeyPreviewEnabled(false)
            assertEquals(false, LauncherSettingsStore(context).settings.first().popupKeyPreviewEnabled)
            store.setPopupKeyPreviewEnabled(true)
            assertTrue(LauncherSettingsStore(context).settings.first().popupKeyPreviewEnabled)
        } finally {
            store.setPopupKeyPreviewEnabled(true)
        }
    }
}
