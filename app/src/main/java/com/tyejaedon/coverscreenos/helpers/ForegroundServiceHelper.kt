package com.tyejaedon.coverscreenos.helpers

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.tyejaedon.coverscreenos.services.ForegroundService

object ForegroundServiceHelper {

    private const val LOG_TAG = "ForegroundServiceHelper"

    // Returns true when foreground notification posting is allowed.
    fun hasNotificationPermission(context: Context): Boolean {
        return AppPermissionHelper.hasNotificationPermission(context)
    }

    fun hasRequiredOverlayPermissions(context: Context): Boolean {
        return hasNotificationPermission(context) &&
            AppPermissionHelper.canDrawOverlays(context) &&
            AppPermissionHelper.isAccessibilityServiceEnabled(context) &&
            AppPermissionHelper.isNotificationListenerEnabled(context) &&
            AppPermissionHelper.isBatteryOptimizationDisabled(context)
    }

    // Starts the foreground service using the canonical start intent.
    fun startForegroundService(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!hasRequiredOverlayPermissions(appContext)) {
            Log.w(LOG_TAG, "Foreground service start skipped: required permissions are missing.")
            return false
        }
        ContextCompat.startForegroundService(appContext, ForegroundService.createStartIntent(appContext))
        return true
    }

    // Sends a stop command so the service can shut itself down cleanly.
    fun stopForegroundService(context: Context) {
        val appContext = context.applicationContext
        appContext.startService(ForegroundService.createStopIntent(appContext))
    }

    fun isForegroundServiceRunning(): Boolean {
        return ForegroundService.isServiceRuntimeActive()
    }
}

