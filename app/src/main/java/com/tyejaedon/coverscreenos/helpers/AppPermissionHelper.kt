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
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.tyejaedon.coverscreenos.services.notifications.CoverNotificationListenerService
import com.tyejaedon.coverscreenos.overlay.input.CoverInputAccessibilityService

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

    fun hasMicrophonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return isAccessibilityServiceEnabled(context, CoverInputAccessibilityService::class.java)
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

    fun createInputMethodSettingsIntent(): Intent {
        return Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
    }

    fun showInputMethodPicker(context: Context): Boolean {
        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return false
        return runCatching {
            inputMethodManager.showInputMethodPicker()
            true
        }.getOrDefault(false)
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

    /**
     * Builds a battery-optimization intent that is actually resolvable on this device.
     *
     * [Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS] is a *list* screen and accepts no
     * data URI - attaching `package:<pkg>` to it makes it unresolvable on several OEM builds
     * (notably Samsung), which previously crashed the app with `ActivityNotFoundException`.
     *
     * Preference order:
     *  1. [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS] + `package:` data - the direct
     *     per-app allow dialog (backed by the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission).
     *  2. [Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS] with *no* data - the system list.
     *  3. App details settings - always present, used as the guaranteed last resort so the
     *     button still does something useful even if resolution is filtered.
     */
    fun createBatteryOptimizationSettingsIntent(context: Context): Intent {
        val appDetailsFallbackIntent = createAppDetailsSettingsIntent(context)
        val preferredIntents = listOf(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:${context.packageName}".toUri()
            ),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )

        return preferredIntents.firstOrNull { intent -> intent.canBeResolved(context) }
            ?: appDetailsFallbackIntent
    }

    /** True when at least one activity on the device can handle [this] intent. */
    fun Intent.canBeResolved(context: Context): Boolean {
        return runCatching {
            context.packageManager.resolveActivity(this, PackageManager.MATCH_DEFAULT_ONLY) != null
        }.getOrDefault(false)
    }
}
