package com.tyejaedon.coverscreenos.helpers

import android.content.ComponentName
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import com.tyejaedon.coverscreenos.overlay.input.CoverInputAccessibilityService
import com.tyejaedon.coverscreenos.services.overlay.CoverAccessibilityService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherAccessibilityPermissionTest {
    @Test
    fun `launcher host is registered as an accessibility service`() {
        val context = RuntimeEnvironment.getApplication()
        val service = context.packageManager.getServiceInfo(
            ComponentName(context, CoverAccessibilityService::class.java), 0
        )
        assertTrue(service.enabled)
        assertTrue(service.exported)
        assertTrue(service.permission == Manifest.permission.BIND_ACCESSIBILITY_SERVICE)
        val requested = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_PERMISSIONS
        ).requestedPermissions.orEmpty()
        assertFalse(Manifest.permission.SYSTEM_ALERT_WINDOW in requested)
    }

    @Test
    fun `launcher accessibility does not mistake input service for launcher host`() {
        val context = RuntimeEnvironment.getApplication()
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ComponentName(context, CoverInputAccessibilityService::class.java).flattenToString()
        )
        assertFalse(AppPermissionHelper.isAccessibilityServiceEnabled(context))
        assertTrue(AppPermissionHelper.isInputAccessibilityServiceEnabled(context))

        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ComponentName(context, CoverAccessibilityService::class.java).flattenToString()
        )
        assertTrue(AppPermissionHelper.isAccessibilityServiceEnabled(context))
        assertFalse(AppPermissionHelper.isInputAccessibilityServiceEnabled(context))
    }
}
