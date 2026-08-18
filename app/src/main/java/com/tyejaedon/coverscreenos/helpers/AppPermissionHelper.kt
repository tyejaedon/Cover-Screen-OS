package com.tyejaedon.coverscreenos.helpers

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.tyejaedon.coverscreenos.services.notifications.CoverNotificationListenerService
import com.tyejaedon.coverscreenos.services.overlay.CoverAccessibilityService

object AppPermissionHelper {

    private const val ENABLED_NOTIFICATION_LISTENERS_KEY = "enabled_notification_listeners"

    private val galleryMediaPermissions = arrayOf(
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )

    fun hasNotificationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun galleryMediaPermissionsToRequest(): Array<String> {
        return galleryMediaPermissions
    }

    fun hasGalleryMediaPermissions(context: Context): Boolean {
        return galleryMediaPermissionsToRequest().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return isAccessibilityServiceEnabled(context, CoverAccessibilityService::class.java)
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            ENABLED_NOTIFICATION_LISTENERS_KEY
        ) ?: return false

        val targetListener = ComponentName(context, CoverNotificationListenerService::class.java).flattenToString()
        return enabledListeners.split(':').any { it.equals(targetListener, ignoreCase = true) }
    }

    private fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService>
    ): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!accessibilityEnabled) return false

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val targetService = ComponentName(context, serviceClass).flattenToString()
        return enabledServices.split(':').any { it.equals(targetService, ignoreCase = true) }
    }

    fun createOverlaySettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri()
        )
    }

    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    fun createNotificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    fun createAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
    }

    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun createBatteryOptimizationSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }
    }
}
