package com.tyejaedon.coverscreenos.helpers

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.tyejaedon.coverscreenos.services.ForegroundService

object ForegroundServiceHelper {

    // Returns true when foreground notification posting is allowed.
    fun hasNotificationPermission(context: Context): Boolean {
        return AppPermissionHelper.hasNotificationPermission(context)
    }

    fun hasRequiredOverlayPermissions(context: Context): Boolean {
        return hasNotificationPermission(context) &&
            AppPermissionHelper.canDrawOverlays(context) &&
            AppPermissionHelper.isAccessibilityServiceEnabled(context)
    }

    // Starts the foreground service using the canonical start intent.
    fun startForegroundService(context: Context) {
        val appContext = context.applicationContext
        ContextCompat.startForegroundService(appContext, ForegroundService.createStartIntent(appContext))
    }

    // Sends a stop command so the service can shut itself down cleanly.
    fun stopForegroundService(context: Context) {
        val appContext = context.applicationContext
        appContext.startService(ForegroundService.createStopIntent(appContext))
    }

    // Provides a lightweight runtime check for service status to drive UI indicators.
    @Suppress("DEPRECATION")
    fun isForegroundServiceRunning(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == ForegroundService::class.java.name
        }
    }
}

