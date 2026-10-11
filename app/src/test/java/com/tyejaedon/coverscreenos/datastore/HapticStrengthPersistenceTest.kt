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
 * Phase B acceptance: the "Haptic strength" setting changes feedback intensity live and
 * survives app restart (DataStore-backed), modeled here as a new [LauncherSettingsStore]
 * instance reading back what a prior instance wrote.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HapticStrengthPersistenceTest {

    @Test
    fun `default haptic strength is Standard`() {
        assertEquals(HapticStrength.Standard, LauncherSettings().hapticStrength)
    }

    @Test
    fun `haptic strength round trips across store instances for every level`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = LauncherSettingsStore(context)
        try {
            HapticStrength.entries.forEach { strength ->
                store.setHapticStrength(strength)
                assertEquals(strength, LauncherSettingsStore(context).settings.first().hapticStrength)
            }
        } finally {
            store.setHapticStrength(HapticStrength.Standard)
        }
    }
}
