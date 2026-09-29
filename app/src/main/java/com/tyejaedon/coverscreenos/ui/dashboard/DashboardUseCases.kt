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
import com.tyejaedon.coverscreenos.services.overlay.ForegroundService
import com.tyejaedon.coverscreenos.services.overlay.RuntimeSnapshot

data class DashboardPermissions(
    val notifications: Boolean,
    val launcherAccessibility: Boolean,
    val notificationListener: Boolean,
    val batteryExemption: Boolean,
    val inputAccessibility: Boolean
) {
    val missing: List<String> get() = buildList {
        if (!notifications) add("Notification permission")
        if (!launcherAccessibility) add("Cover Screen OS Launcher accessibility")
        if (!notificationListener) add("Notification access")
        if (!batteryExemption) add("Battery optimization exemption")
    }
    val grantedCount: Int get() = 4 - missing.size
    val totalCount: Int get() = 4
}

enum class DashboardStatus { ACTIVE, SETUP_NEEDED, STOPPED }

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

object DashboardUseCases {
    fun readPermissions(context: Context): DashboardPermissions = DashboardPermissions(
        notifications = AppPermissionHelper.hasNotificationPermission(context),
        launcherAccessibility = AppPermissionHelper.isAccessibilityServiceEnabled(context),
        notificationListener = AppPermissionHelper.isNotificationListenerEnabled(context),
        batteryExemption = AppPermissionHelper.isBatteryOptimizationDisabled(context),
        inputAccessibility = AppPermissionHelper.isInputAccessibilityServiceEnabled(context)
    )
}

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
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        context.contentResolver.registerContentObserver(Settings.Secure.CONTENT_URI, true, settingsObserver)
        refresh()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            context.contentResolver.unregisterContentObserver(settingsObserver)
        }
    }
    return DashboardState(permissions, runtime)
}
