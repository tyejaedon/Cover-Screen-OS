package com.tyejaedon.coverscreenos.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tyejaedon.coverscreenos.helpers.ForegroundServiceHelper

class BootCompletedReceiver : BroadcastReceiver() {

    // Starts the service after boot/package replace when notification permission allows it.
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val shouldStart = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!shouldStart) return

        if (!ForegroundServiceHelper.hasRequiredOverlayPermissions(context)) return

        ForegroundServiceHelper.startForegroundService(context)
    }
}

