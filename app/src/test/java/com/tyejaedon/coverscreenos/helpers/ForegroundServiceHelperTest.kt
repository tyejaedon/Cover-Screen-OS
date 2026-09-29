package com.tyejaedon.coverscreenos.helpers

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ForegroundServiceHelperTest {
    private val context = mockk<Context>()

    @Before
    fun setUp() {
        mockkObject(AppPermissionHelper)
        every { AppPermissionHelper.hasNotificationPermission(context) } returns true
        every { AppPermissionHelper.isAccessibilityServiceEnabled(context) } returns true
        every { AppPermissionHelper.isNotificationListenerEnabled(context) } returns true
        every { AppPermissionHelper.isBatteryOptimizationDisabled(context) } returns true
    }

    @After
    fun tearDown() {
        unmockkObject(AppPermissionHelper)
    }

    @Test
    fun `launcher requires accessibility and no legacy permission`() {
        assertTrue(ForegroundServiceHelper.hasCoreOverlayPermissions(context))
        every { AppPermissionHelper.isAccessibilityServiceEnabled(context) } returns false
        assertFalse(ForegroundServiceHelper.hasCoreOverlayPermissions(context))
    }

    @Test
    fun `notification access and battery exemption remain mandatory`() {
        every { AppPermissionHelper.isNotificationListenerEnabled(context) } returns false
        assertFalse(ForegroundServiceHelper.hasCoreOverlayPermissions(context))
        every { AppPermissionHelper.isNotificationListenerEnabled(context) } returns true
        every { AppPermissionHelper.isBatteryOptimizationDisabled(context) } returns false
        assertFalse(ForegroundServiceHelper.hasCoreOverlayPermissions(context))
    }
}
