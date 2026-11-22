package com.tyejaedon.coverscreenos.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tyejaedon.coverscreenos.helpers.ForegroundServiceHelper

/**
 * Manifest-registered [BroadcastReceiver] that restores the Cover-Screen-OS foreground service
 * automatically after device startup or self-package updates.
 *
 * Listens for:
 * - [Intent.ACTION_BOOT_COMPLETED]: Standard boot broadcast after the user unlocks the device.
 * - [Intent.ACTION_LOCKED_BOOT_COMPLETED]: Direct Boot broadcast prior to initial credential unlock.
 * - [Intent.ACTION_MY_PACKAGE_REPLACED]: Broadcast delivered exclusively to this app after an update.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    // Starts the foreground service after boot/package replace when notification permission allows it.
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val shouldStart = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!shouldStart) return

        ForegroundServiceHelper.startForegroundService(context)
    }
}
