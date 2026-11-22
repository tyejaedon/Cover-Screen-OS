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
import com.tyejaedon.coverscreenos.overlay.input.CoverInputAccessibilityService
import com.tyejaedon.coverscreenos.notifications.CoverNotificationListenerService
import com.tyejaedon.coverscreenos.overlay.CoverAccessibilityService

/**
 * Centralizes permission checks, special system-service enablement queries, and settings intents
 * required by Cover-Screen-OS.
 *
 * Because the cover-screen overlay is hosted via an [AccessibilityService] window
 * (`TYPE_ACCESSIBILITY_OVERLAY`) rather than `SYSTEM_ALERT_WINDOW`, the primary gating checks
 * inspect `Settings.Secure` for enabled accessibility services and notification listeners alongside
 * standard runtime permissions and Doze/battery-optimization exemptions.
 */
object AppPermissionHelper {

    // Raw Settings.Secure key backing NotificationListenerService enablement; not exposed as a
    // public constant on android.provider.Settings.Secure across all SDK levels.
    private const val ENABLED_NOTIFICATION_LISTENERS_KEY = "enabled_notification_listeners"

    // Shared runtime permission list for photo/wallpaper pickers so requests and checks stay in sync.
    private val galleryMediaPermissions = arrayOf(
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )

    /**
     * Returns true when the app holds [Manifest.permission.POST_NOTIFICATIONS] (required on
     * Android 13+ / API 33+ to display the foreground service notification).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Returns the runtime media permission array used for gallery/photo selection flows. */
    fun galleryMediaPermissionsToRequest(): Array<String> {
        return galleryMediaPermissions
    }

    /** Returns true when all permissions in [galleryMediaPermissionsToRequest] are granted. */
    fun hasGalleryMediaPermissions(context: Context): Boolean {
        return galleryMediaPermissionsToRequest().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Returns true when [Manifest.permission.RECORD_AUDIO] is granted for voice/audio features. */
    fun hasMicrophonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns true when [Manifest.permission.BLUETOOTH_CONNECT] is granted (required on Android 12+
     * / API 31+ before querying or toggling Bluetooth state).
     */
    fun hasBluetoothConnectPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns true when [CoverAccessibilityService] (which hosts the cover-display overlay window)
     * is enabled in system Accessibility settings.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return isAccessibilityServiceEnabled(context, CoverAccessibilityService::class.java)
    }

    /**
     * Returns true when [CoverInputAccessibilityService] (used for input/navigation gestures and
     * BAL-exempt activity launches) is enabled in system Accessibility settings.
     */
    fun isInputAccessibilityServiceEnabled(context: Context): Boolean {
        return isAccessibilityServiceEnabled(context, CoverInputAccessibilityService::class.java)
    }

    /**
     * Returns true when [CoverNotificationListenerService] appears in the colon-delimited
     * `enabled_notification_listeners` [Settings.Secure] entry.
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        // Colon-separated list of flattened component names granted notification access in Android settings.
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            ENABLED_NOTIFICATION_LISTENERS_KEY
        ) ?: return false

        // Exact "package/service" string identifying our NotificationListenerService.
        val targetListener = ComponentName(context, CoverNotificationListenerService::class.java).flattenToString()
        return enabledListeners.split(':').any { it.equals(targetListener, ignoreCase = true) }
    }

    /**
     * Checks whether [serviceClass] is active by verifying both the master
     * [Settings.Secure.ACCESSIBILITY_ENABLED] flag and membership in the colon-separated
     * [Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES] list.
     */
    private fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService>
    ): Boolean {
        // Master toggle must be 1; Android can retain service entries even when accessibility is off.
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!accessibilityEnabled) return false

        // Colon-separated list of enabled accessibility service components stored by Android.
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        // Flattened "package/class" identifier for the accessibility service being checked.
        val targetService = ComponentName(context, serviceClass).flattenToString()
        return enabledServices.split(':').any { it.equals(targetService, ignoreCase = true) }
    }

    /** Builds an [Intent] to open the system Accessibility settings screen. */
    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    /** Builds an [Intent] to open the system Notification Access listener settings screen. */
    fun createNotificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    /** Builds an [Intent] to open the system On-screen Keyboard / Input Method settings screen. */
    fun createInputMethodSettingsIntent(): Intent {
        return Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
    }

    /**
     * Displays the system Input Method (IME) chooser dialog via [InputMethodManager], returning
     * false if the service is unavailable or the call fails.
     */
    fun showInputMethodPicker(context: Context): Boolean {
        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return false
        return runCatching {
            inputMethodManager.showInputMethodPicker()
            true
        }.getOrDefault(false)
    }

    /** Builds an [Intent] pointing directly to this app's system App Info screen. */
    fun createAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
    }

    /**
     * Returns true when the app is exempted from Doze / App Standby battery optimizations via
     * [PowerManager.isIgnoringBatteryOptimizations], preventing background overlay termination.
     */
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
