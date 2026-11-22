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
    val notifications: Boolean,
    val launcherAccessibility: Boolean,
    val notificationListener: Boolean,
    val batteryExemption: Boolean,
    val inputAccessibility: Boolean
) {
    /** Human-readable labels for required grants that are not yet enabled. */
    val missing: List<String> get() = buildList {
        if (!notifications) add("Notification permission")
        if (!launcherAccessibility) add("Cover Screen OS Launcher accessibility")
        if (!notificationListener) add("Notification access")
        if (!batteryExemption) add("Battery optimization exemption")
    }
    val grantedCount: Int get() = 4 - missing.size
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
    val status: DashboardStatus get() = when {
        permissions.missing.isNotEmpty() -> DashboardStatus.SETUP_NEEDED
        !runtime.serviceActive -> DashboardStatus.STOPPED
        !runtime.launcherHostActive -> DashboardStatus.SETUP_NEEDED
        else -> DashboardStatus.ACTIVE
    }
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissions by remember(context, refreshKey) {
        mutableStateOf(DashboardUseCases.readPermissions(context))
    }
    val runtime by ForegroundService.runtime.collectAsState()

    DisposableEffect(context, lifecycleOwner, refreshKey) {
        fun refresh() { permissions = DashboardUseCases.readPermissions(context) }
        // Re-check permissions on ON_RESUME because users grant Accessibility, Notification
        // Listener, and battery exemptions in external Android Settings activities.
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        // Also watch Settings.Secure (including Accessibility/Notification Listener entries) so
        // external toggles—such as multi-window Settings or ADB—update the dashboard live.
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
