package com.tyejaedon.coverscreenos.ui.permissions

import com.tyejaedon.coverscreenos.ui.dashboard.DashboardPermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionGrantsTest {
    private val granted = PermissionGrants(
        notifications = true,
        launcherAccessibility = true,
        notificationListener = true,
        batteryExemption = true,
        inputAccessibility = true,
        media = false,
        microphone = false
    )

    @Test
    fun groupsMatchServiceRequirementsWithoutDuplicateBatteryOrForegroundService() {
        assertEquals(
            listOf(
                PermissionId.NOTIFICATIONS, PermissionId.LAUNCHER_ACCESSIBILITY,
                PermissionId.NOTIFICATION_LISTENER, PermissionId.BATTERY_EXEMPTION
            ),
            PermissionId.entries.filter { it.group == PermissionGroup.REQUIRED }
        )
        assertEquals(
            listOf(PermissionId.INPUT_ACCESSIBILITY),
            PermissionId.entries.filter { it.group == PermissionGroup.RECOMMENDED }
        )
        assertEquals(
            listOf(PermissionId.MEDIA, PermissionId.MICROPHONE),
            PermissionId.entries.filter { it.group == PermissionGroup.OPTIONAL }
        )
    }

    @Test
    fun healthIgnoresOptionalButIncludesRecommended() {
        assertEquals(100, granted.healthPercent)
        assertFalse(granted.requiredMissing)
        assertEquals(80, granted.copy(inputAccessibility = false).healthPercent)
        assertFalse(granted.copy(inputAccessibility = false).requiredMissing)
        assertEquals(60, granted.copy(notifications = false, batteryExemption = false).healthPercent)
        assertTrue(granted.copy(notifications = false).requiredMissing)
        assertEquals(
            0, granted.copy(
                notifications = false, launcherAccessibility = false,
                notificationListener = false, batteryExemption = false,
                inputAccessibility = false, media = true, microphone = true
            ).healthPercent
        )
    }

    @Test
    fun requiredGroupingMatchesDashboardAndServiceStartPrerequisitesForEveryCombination() {
        for (mask in 0..15) {
            val grants = granted.copy(
                notifications = mask and 1 != 0,
                launcherAccessibility = mask and 2 != 0,
                notificationListener = mask and 4 != 0,
                batteryExemption = mask and 8 != 0
            )
            val dashboard = DashboardPermissions(
                notifications = grants.notifications,
                launcherAccessibility = grants.launcherAccessibility,
                notificationListener = grants.notificationListener,
                batteryExemption = grants.batteryExemption,
                inputAccessibility = grants.inputAccessibility
            )
            assertEquals(dashboard.missing.isNotEmpty(), grants.requiredMissing)
        }
    }
}
