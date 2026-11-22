package com.tyejaedon.coverscreenos.ui.dashboard

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import com.tyejaedon.coverscreenos.helpers.AppPermissionHelper
import com.tyejaedon.coverscreenos.overlay.ForegroundService
import com.tyejaedon.coverscreenos.overlay.RuntimeSnapshot

/**
 * Snapshot of Android runtime and special-access permissions tracked for dashboard readiness.
 *
 * The four required items gate [missing], [grantedCount], and [totalCount], while
 * [inputAccessibility] is tracked for keyboard input readiness without blocking core startup.
 */
data class DashboardPermissions(
    /** True if the app is allowed to post its persistent foreground service notification (Android 13+). */
    val notifications: Boolean,
    /** True if the main Cover Screen OS AccessibilityService is enabled to host the cover overlay. */
    val launcherAccessibility: Boolean,
    /** True if NotificationListenerService access is enabled so alerts can appear on the cover screen. */
    val notificationListener: Boolean,
    /** True if Android battery optimization is disabled so the cover service isn't killed in the background. */
    val batteryExemption: Boolean,
    /** True if the optional input AccessibilityService is enabled for cover-screen keyboard typing. */
    val inputAccessibility: Boolean
) {
    /** Human-readable labels for required grants that are not yet enabled. */
    val missing: List<String> get() = buildList {
        if (!notifications) add("Notification permission")
        if (!launcherAccessibility) add("Cover Screen OS Launcher accessibility")
        if (!notificationListener) add("Notification access")
        if (!batteryExemption) add("Battery optimization exemption")
    }
    /** Number of the 4 required permissions that are currently enabled. */
    val grantedCount: Int get() = 4 - missing.size
    /** Fixed denominator (4) representing all mandatory setup requirements. */
    val totalCount: Int get() = 4
}

/** High-level operational state derived from permissions and [ForegroundService] liveness. */
enum class DashboardStatus { ACTIVE, SETUP_NEEDED, STOPPED }

/**
 * Combined UI model consumed by [DashboardScreen], [DashboardHeroCard], and [TopBarStatusChip].
 */
data class DashboardState(
    val permissions: DashboardPermissions,
    val runtime: RuntimeSnapshot
) {
    /** Overall readiness badge: prioritizes missing setup, then stopped service, then missing host binding. */
    val status: DashboardStatus get() = when {
        permissions.missing.isNotEmpty() -> DashboardStatus.SETUP_NEEDED
        !runtime.serviceActive -> DashboardStatus.STOPPED
        !runtime.launcherHostActive -> DashboardStatus.SETUP_NEEDED
        else -> DashboardStatus.ACTIVE
    }
    /** Short human-friendly headline corresponding to [status]. */
    val title: String get() = when (status) {
        DashboardStatus.ACTIVE -> "Cover screen active"
        DashboardStatus.SETUP_NEEDED -> "Setup needed"
        DashboardStatus.STOPPED -> "Service stopped"
    }
}

/**
 * Queries current permission and special system-access state via [AppPermissionHelper].
 */
object DashboardUseCases {
    fun readPermissions(context: Context): DashboardPermissions = DashboardPermissions(
        notifications = AppPermissionHelper.hasNotificationPermission(context),
        launcherAccessibility = AppPermissionHelper.isAccessibilityServiceEnabled(context),
        notificationListener = AppPermissionHelper.isNotificationListenerEnabled(context),
        batteryExemption = AppPermissionHelper.isBatteryOptimizationDisabled(context),
        inputAccessibility = AppPermissionHelper.isInputAccessibilityServiceEnabled(context)
    )
}

/**
 * Collects live [ForegroundService.runtime] updates and automatically re-polls permissions when
 * returning from system Settings or when secure settings change externally.
 *
 * Pass an incrementing [refreshKey] when another screen (such as `PermissionsScreen`) completes an
 * interactive grant flow to force an immediate recomposition refresh.
 */
@Composable
fun rememberDashboardState(refreshKey: Int = 0): DashboardState {
    // Android Context used to check permissions and register a Settings ContentObserver.
    val context = LocalContext.current
    // Activity/Fragment lifecycle owner watched for ON_RESUME when returning from System Settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    // Latest snapshot of Android permissions; recomputed when lifecycle resumes, settings change, or refreshKey bumps.
    var permissions by remember(context, refreshKey) {
        mutableStateOf(DashboardUseCases.readPermissions(context))
    }
    // Live service, accessibility-host, overlay, and event snapshot streamed from ForegroundService.
    val runtime by ForegroundService.runtime.collectAsState()

    DisposableEffect(context, lifecycleOwner, refreshKey) {
        fun refresh() { permissions = DashboardUseCases.readPermissions(context) }
        // Observer that refreshes permissions as soon as the user returns from an external Settings screen.
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        // ContentObserver watching Settings.Secure (Accessibility/Notification Listener toggles) for live split-screen or ADB changes.
        val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        context.contentResolver.registerContentObserver(Settings.Secure.CONTENT_URI, true, settingsObserver)
        refresh()
        onDispose {
            // Unregister both observers to avoid leaking the Activity/LifecycleOwner context.
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            context.contentResolver.unregisterContentObserver(settingsObserver)
        }
    }
    return DashboardState(permissions, runtime)
}
