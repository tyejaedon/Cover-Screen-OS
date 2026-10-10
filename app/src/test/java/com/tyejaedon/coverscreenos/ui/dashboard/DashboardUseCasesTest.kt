package com.tyejaedon.coverscreenos.ui.dashboard

import com.tyejaedon.coverscreenos.services.overlay.RuntimeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardUseCasesTest {
    private val ready = DashboardPermissions(
        notifications = true,
        launcherAccessibility = true,
        notificationListener = true,
        batteryExemption = true,
        inputAccessibility = false
    )

    @Test
    fun runningServiceAndHostAreActiveEvenWithoutOptionalKeyboard() {
        val state = DashboardState(ready, RuntimeSnapshot(serviceActive = true, launcherHostActive = true))
        assertEquals(DashboardStatus.ACTIVE, state.status)
        assertEquals("Cover screen active", state.title)
        assertEquals(4, state.permissions.grantedCount)
    }

    @Test
    fun missingRequiredPermissionsTakePriorityOverStoppedService() {
        val permissions = ready.copy(notifications = false, notificationListener = false)
        val state = DashboardState(permissions, RuntimeSnapshot())
        assertEquals(DashboardStatus.SETUP_NEEDED, state.status)
        assertEquals(2, permissions.grantedCount)
        assertEquals(listOf("Notification permission", "Notification access"), permissions.missing)
    }

    @Test
    fun serviceStoppedWithPermissionsReadyIsRed() {
        assertEquals(DashboardStatus.STOPPED, DashboardState(ready, RuntimeSnapshot()).status)
    }

    @Test
    fun missingLiveHostRequiresSetupEvenIfServiceIsRunning() {
        assertEquals(
            DashboardStatus.SETUP_NEEDED,
            DashboardState(ready, RuntimeSnapshot(serviceActive = true)).status
        )
    }
}
