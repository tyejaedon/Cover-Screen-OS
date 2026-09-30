package com.tyejaedon.coverscreenos.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WelcomeTourPreferenceTest {
    @Test
    fun `tour defaults unseen and stays seen across store instances and settings changes`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = LauncherSettingsStore(context)
        assertFalse(store.welcomeTourSeen.first())
        store.setThemePreference(ThemePreference.DARK)
        store.markWelcomeTourSeen()

        val reopened = LauncherSettingsStore(context)
        assertTrue(reopened.welcomeTourSeen.first())
        assertEquals(ThemePreference.DARK, reopened.settings.first().themePreference)
        reopened.setDockVisible(false)
        assertTrue(store.welcomeTourSeen.first())
    }
}
