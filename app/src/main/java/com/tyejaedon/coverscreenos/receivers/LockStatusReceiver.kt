package com.tyejaedon.coverscreenos.receivers

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class LockStatusReceiver(
    private val onLockStatusChanged: (isLocked: Boolean) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val action = intent?.action ?: return

        val isLocked = when (action) {
            Intent.ACTION_SCREEN_OFF -> true
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_USER_UNLOCKED -> false
            Intent.ACTION_SCREEN_ON -> currentLockStatus(context)
            else -> return
        }
        onLockStatusChanged(isLocked)
    }

    companion object {
        fun currentLockStatus(context: Context): Boolean {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            return keyguardManager?.isDeviceLocked
                ?: keyguardManager?.isKeyguardLocked
                ?: true
        }

        fun getIntentFilter(): IntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_USER_UNLOCKED)
        }
    }
}

