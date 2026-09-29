package com.tyejaedon.coverscreenos.datastore

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyOverlayPreferenceMigrationTest {
    @Test
    fun `existing legacy window preference is ignored without losing other settings`() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("overlay_host_mode") to "LEGACY_WINDOW",
            stringPreferencesKey("theme_preference") to "DARK"
        )
        val settings = LauncherSettingsStore(context).mapStoredPreferences(preferences)
        assertEquals(ThemePreference.DARK, settings.themePreference)
        assertEquals(LauncherSettings().dockPackages, settings.dockPackages)
    }
}
