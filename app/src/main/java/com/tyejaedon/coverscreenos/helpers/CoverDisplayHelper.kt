package com.tyejaedon.coverscreenos.helpers

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import com.tyejaedon.coverscreenos.receivers.LockStatusReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CoverDisplayHelper(private val context: Context) {

    private companion object {
        private const val LOCK_STATUS_POLL_INTERVAL_MS = 10_000L
    }

    private val displayManager: DisplayManager by lazy {
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }
    private var lockStatusReceiver: LockStatusReceiver? = null
    private var isLockStatusReceiverRegistered = false
    private val mainHandler: Handler by lazy(LazyThreadSafetyMode.NONE) {
        Handler(Looper.getMainLooper())
    }
    private var lockStatusPoller: Runnable? = null
    private val _isDeviceLocked = MutableStateFlow(true)
    val isDeviceLocked: StateFlow<Boolean> = _isDeviceLocked.asStateFlow()

    /**
     * Discovers and returns the secondary display (the cover screen).
     * Returns null if no secondary display is currently connected.
     */
    fun getCoverDisplay(): Display? {
        // Prefer active presentation displays first.
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        presentationDisplays.firstOrNull { it.isUsableCoverDisplay() }?.let { return it }

        // Fallback to any active, valid non-default display.
        return displayManager.displays.firstOrNull { display ->
            display.isUsableCoverDisplay()
        }
    }

    fun getCoverDisplayId(): Int? {
        return getCoverDisplay()?.displayId
    }

    fun startLockStatusMonitoring() {
        if (isLockStatusReceiverRegistered) {
            _isDeviceLocked.value = LockStatusReceiver.currentLockStatus(context)
            return
        }

        val receiver = LockStatusReceiver { isLocked ->
            _isDeviceLocked.value = isLocked
        }

        _isDeviceLocked.value = LockStatusReceiver.currentLockStatus(context)
        context.registerReceiver(
            receiver,
            LockStatusReceiver.getIntentFilter(),
            Context.RECEIVER_NOT_EXPORTED
        )

        lockStatusReceiver = receiver
        isLockStatusReceiverRegistered = true
        startLockStatusPolling()
    }

    fun stopLockStatusMonitoring() {
        if (!isLockStatusReceiverRegistered) return

        lockStatusReceiver?.let { receiver ->
            runCatching { context.unregisterReceiver(receiver) }
                .onFailure { error ->
                    Log.w("CoverDisplayHelper", "Failed to unregister lock status receiver: ${error.message}")
                }
        }

        lockStatusReceiver = null
        isLockStatusReceiverRegistered = false
        stopLockStatusPolling()
    }

    fun getDisplayLockStatus(): Boolean {
        return isDeviceLocked.value
    }

    private fun Display.isUsableCoverDisplay(): Boolean {
        return displayId != Display.DEFAULT_DISPLAY && isValid && state == Display.STATE_ON
    }

    fun describeDisplays(): String {
        return displayManager.displays.joinToString(prefix = "[", postfix = "]") { display ->
            "id=${display.displayId},state=${display.state},valid=${display.isValid}"
        }
    }

    private fun startLockStatusPolling() {
        if (lockStatusPoller != null) return

        // Receiver updates remain primary; this low-frequency poll is a fallback for OEM edge cases.
        val poller = object : Runnable {
            override fun run() {
                _isDeviceLocked.value = LockStatusReceiver.currentLockStatus(context)
                mainHandler.postDelayed(this, LOCK_STATUS_POLL_INTERVAL_MS)
            }
        }

        lockStatusPoller = poller
        mainHandler.post(poller)
    }

    private fun stopLockStatusPolling() {
        lockStatusPoller?.let { mainHandler.removeCallbacks(it) }
        lockStatusPoller = null
    }
}