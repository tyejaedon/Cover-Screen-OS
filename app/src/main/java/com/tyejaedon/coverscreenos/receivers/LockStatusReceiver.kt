package com.tyejaedon.coverscreenos.receivers

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Dynamically registered [BroadcastReceiver] that tracks screen and keyguard lock transitions
 * so the cover-screen overlay can adapt between locked and unlocked states.
 *
 * Must be registered in code with [getIntentFilter] because Android prohibits manifest-declared
 * receivers from subscribing to [Intent.ACTION_SCREEN_ON] and [Intent.ACTION_SCREEN_OFF].
 *
 * @param onLockStatusChanged Callback invoked whenever a lock-state-relevant broadcast arrives.
 */
class LockStatusReceiver(
    private val onLockStatusChanged: (isLocked: Boolean) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val action = intent?.action ?: return

        val isLocked = when (action) {
            // Turning the screen off engages lock/keyguard protection immediately on secure devices.
            Intent.ACTION_SCREEN_OFF -> true
            // ACTION_USER_PRESENT fires when the user dismisses the keyguard; ACTION_USER_UNLOCKED
            // fires when Direct Boot credential-encrypted storage becomes available.
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_USER_UNLOCKED -> false
            // Waking the screen does not imply unlock, so query KeyguardManager directly.
            Intent.ACTION_SCREEN_ON -> currentLockStatus(context)
            else -> return
        }
        onLockStatusChanged(isLocked)
    }

    /** Keyguard inspection and dynamic broadcast-filter factory helpers. */
    companion object {
        /**
         * Queries [KeyguardManager] for the device's instantaneous lock state.
         *
         * Prefers [KeyguardManager.isDeviceLocked] (whether credential unlock is required),
         * falls back to [KeyguardManager.isKeyguardLocked], and defaults conservatively to `true`
         * if the system service is unavailable.
         */
        fun currentLockStatus(context: Context): Boolean {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            return keyguardManager?.isDeviceLocked
                ?: keyguardManager?.isKeyguardLocked
                ?: true
        }

        /**
         * Builds the [IntentFilter] covering screen power and user unlock broadcasts required
         * for dynamic registration via `Context.registerReceiver`.
         */
        fun getIntentFilter(): IntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_USER_UNLOCKED)
        }
    }
}

