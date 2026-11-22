package com.tyejaedon.coverscreenos.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverRecentAppsTrackerTest {

    @Test
    fun `records user apps most-recent-first without duplicates and enforces cap`() {
        val tracker = CoverRecentAppsTracker(selfPackageName = "com.tyejaedon.coverscreenos")

        for (index in 1..12) {
            tracker.recordAppActive("com.example.app$index")
        }

        val packages = tracker.recentPackages.value
        assertEquals(CoverRecentAppsTracker.MAX_RECENT_APPS, packages.size)
        assertEquals("com.example.app12", packages.first())
        assertFalse("com.example.app1" in packages)

        // Re-opening an older recent app promotes it to the front without duplicating it.
        tracker.recordAppActive("com.example.app5")
        assertEquals("com.example.app5", tracker.recentPackages.value.first())
        assertEquals(1, tracker.recentPackages.value.count { it == "com.example.app5" })
    }

    @Test
    fun `filters out self, system UI, AOD, and stock launchers`() {
        val tracker = CoverRecentAppsTracker(selfPackageName = "com.tyejaedon.coverscreenos")

        tracker.recordAppActive("")
        tracker.recordAppActive("com.tyejaedon.coverscreenos")
        tracker.recordAppActive("com.android.systemui")
        tracker.recordAppActive("com.samsung.android.app.aodservice")
        tracker.recordAppActive("com.sec.android.app.launcher")
        tracker.recordAppActive("com.whatsapp")

        assertEquals(listOf("com.whatsapp"), tracker.recentPackages.value)
    }

    @Test
    fun `removeApp, clearAll, and sheet visibility mutate state cleanly`() {
        val tracker = CoverRecentAppsTracker(selfPackageName = "com.tyejaedon.coverscreenos")
        tracker.recordAppActive("com.example.one")
        tracker.recordAppActive("com.example.two")
        tracker.setRecentsSheetVisible(true)

        assertTrue(tracker.isRecentsSheetVisible.value)

        tracker.removeApp("com.example.one")
        assertEquals(listOf("com.example.two"), tracker.recentPackages.value)

        tracker.clearAll()
        assertTrue(tracker.recentPackages.value.isEmpty())

        tracker.setRecentsSheetVisible(false)
        assertFalse(tracker.isRecentsSheetVisible.value)
    }
}
