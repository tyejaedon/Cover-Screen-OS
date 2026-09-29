package com.tyejaedon.coverscreenos.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionScreenRequirementsTest {
    @Test
    fun `only core permissions gate launcher onboarding`() {
        assertTrue(ready())
        assertFalse(ready(battery = false))
        assertFalse(ready(accessibility = false))
        assertFalse(ready(notification = false))
        assertFalse(ready(notificationListener = false))
    }

    private fun ready(
        notification: Boolean = true,
        accessibility: Boolean = true,
        notificationListener: Boolean = true,
        battery: Boolean = true
    ): Boolean = areRequiredPermissionsGranted(
        notification = notification,
        accessibility = accessibility,
        notificationListener = notificationListener,
        batteryExemption = battery
    )
}
