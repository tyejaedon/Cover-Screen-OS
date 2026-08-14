package com.tyejaedon.coverscreenos.receivers

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.tyejaedon.coverscreenos.services.ForegroundService

class BootCompletedReceiver : BroadcastReceiver() {

    // Starts the service after boot/package replace when notification permission allows it.
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val shouldStart = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!shouldStart) return

        val hasNotificationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasNotificationPermission) return

        ContextCompat.startForegroundService(context, ForegroundService.createStartIntent(context))
    }
}

