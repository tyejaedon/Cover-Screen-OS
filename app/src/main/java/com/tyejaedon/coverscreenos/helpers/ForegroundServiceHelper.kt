package com.tyejaedon.coverscreenos.helpers

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.tyejaedon.coverscreenos.overlay.ForegroundService

/**
 * Facade for permission-gated lifecycle control of [ForegroundService].
 *
 * Ensures the persistent foreground service is only started once all critical permissions
 * (notification posting, accessibility overlay service, notification listener, and battery
 * optimization exemption) are granted.
 */
object ForegroundServiceHelper {

    private const val LOG_TAG = "ForegroundServiceHelper"

    /** Returns true when foreground notification posting (`POST_NOTIFICATIONS`) is allowed. */
    fun hasNotificationPermission(context: Context): Boolean {
        return AppPermissionHelper.hasNotificationPermission(context)
    }

    /**
     * Returns true when all mandatory permissions and special system accesses required for the
     * cover-screen overlay runtime are enabled.
     */
    fun hasCoreOverlayPermissions(context: Context): Boolean {
        return hasNotificationPermission(context) &&
            AppPermissionHelper.isAccessibilityServiceEnabled(context) &&
            AppPermissionHelper.isNotificationListenerEnabled(context) &&
            AppPermissionHelper.isBatteryOptimizationDisabled(context)
    }

    /**
     * Starts the foreground service using the canonical start intent via
     * [ContextCompat.startForegroundService] if [hasCoreOverlayPermissions] passes.
     *
     * Uses `applicationContext` to prevent leaking short-lived UI contexts.
     */
    fun startForegroundService(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!hasCoreOverlayPermissions(appContext)) {
            Log.w(LOG_TAG, "Foreground service start skipped: required permissions are missing.")
            return false
        }
        ContextCompat.startForegroundService(
            appContext,
            ForegroundService.createStartIntent(appContext)
        )
        return true
    }

    /** Sends a stop command so the service can shut itself down cleanly. */
    fun stopForegroundService(context: Context) {
        val appContext = context.applicationContext
        appContext.startService(ForegroundService.createStopIntent(appContext))
    }

    /** Returns true when [ForegroundService] reports an active runtime instance. */
    fun isForegroundServiceRunning(): Boolean {
        return ForegroundService.isServiceRuntimeActive()
    }
}
