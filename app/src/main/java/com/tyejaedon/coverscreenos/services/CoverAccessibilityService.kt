package com.tyejaedon.coverscreenos.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

@SuppressLint("AccessibilityPolicy")
class CoverAccessibilityService : AccessibilityService() {
    companion object {
        private const val LOG_TAG = "CoverAccessibility"
        private const val OVERLAY_RECLAIM_LOG_TAG = "CoverOverlayReclaim"
        private const val GESTURE_DEBOUNCE_MS = 550L
        private const val ACTION_THROTTLE_MS = 300L
        private const val FOREGROUND_EVENT_REFRESH_MIN_INTERVAL_MS = 250L
        private val OVERLAY_RECLAIM_PACKAGE_PREFIXES = arrayOf(
            "com.sec.android.app.launcher",
            "com.samsung.android.app.aodservice",
            "com.android.systemui",
            "com.samsung.systemui"
        )
        private val INCOMING_CALL_PACKAGE_PREFIXES = arrayOf(
            "com.samsung.android.incallui",
            "com.android.incallui",
            "com.google.android.dialer",
            "com.android.server.telecom"
        )

        @Volatile
        private var latestForegroundPackage: String? = null

        @Volatile
        private var latestForegroundEventElapsedMs: Long = 0L

        fun currentForegroundPackage(): String? = latestForegroundPackage

        fun currentForegroundPackageEventAgeMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long {
            val eventElapsedMs = latestForegroundEventElapsedMs
            if (eventElapsedMs <= 0L) return Long.MAX_VALUE
            return (nowElapsedMs - eventElapsedMs).coerceAtLeast(0L)
        }
    }

    private inline fun logDebug(message: () -> String) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, message())
        }
    }

    private var lastWindowPackage: String? = null
    private var lastGestureId: Int? = null
    private var lastGestureAtMs: Long = 0L
    private var lastActionAtMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        logDebug { "Service connected" }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val isWindowEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (!isWindowEvent) return

        val foregroundPackage = event.packageName
            ?.toString()
            ?.trim()
            .takeUnless { it.isNullOrEmpty() }
            ?: return

        val nowElapsedMs = SystemClock.elapsedRealtime()
        val isSameForegroundPackage = latestForegroundPackage == foregroundPackage
        val shouldRefreshForegroundTimestamp = !isSameForegroundPackage ||
            (nowElapsedMs - latestForegroundEventElapsedMs) >= FOREGROUND_EVENT_REFRESH_MIN_INTERVAL_MS
        if (shouldRefreshForegroundTimestamp) {
            latestForegroundPackage = foregroundPackage
            latestForegroundEventElapsedMs = nowElapsedMs
        }

        if (foregroundPackage != lastWindowPackage) {
            lastWindowPackage = foregroundPackage
            logDebug { "Window changed: $foregroundPackage" }

            if (shouldAllowIncomingCallSurface(foregroundPackage)) {
                ForegroundService.requestIncomingCallPassthrough(foregroundPackage)
            } else if (shouldRequestOverlayReclaim(foregroundPackage)) {
                if (Log.isLoggable(OVERLAY_RECLAIM_LOG_TAG, Log.DEBUG)) {
                    Log.d(OVERLAY_RECLAIM_LOG_TAG, "trigger from accessibility package=$foregroundPackage")
                }
                ForegroundService.requestOverlayReclaim(reason = foregroundPackage)
            }
        }
    }

    @Deprecated("Uses legacy gesture callback for broad compatibility")
    @Suppress("DEPRECATION")
    override fun onGesture(gestureId: Int): Boolean {
        if (!ForegroundService.isOverlayActive) return false
        if (shouldDebounceGesture(gestureId)) return false

        val globalAction = when (gestureId) {
            GESTURE_SWIPE_LEFT -> GLOBAL_ACTION_BACK
            GESTURE_SWIPE_UP -> GLOBAL_ACTION_HOME
            GESTURE_SWIPE_RIGHT -> GLOBAL_ACTION_RECENTS
            GESTURE_SWIPE_DOWN -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return false
        }
        if (shouldThrottleGlobalAction()) return false

        val result = performGlobalAction(globalAction)
        logDebug { "Gesture=$gestureId action=$globalAction result=$result" }
        return result
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!ForegroundService.isOverlayActive) return super.onKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (shouldThrottleGlobalAction()) return true
            val result = performGlobalAction(GLOBAL_ACTION_BACK)
            logDebug { "Back key intercepted result=$result" }
            return result
        }

        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {
        logDebug { "Accessibility service interrupted" }
    }

    private fun shouldDebounceGesture(gestureId: Int): Boolean {
        val now = SystemClock.elapsedRealtime()
        val isRepeated = lastGestureId == gestureId && (now - lastGestureAtMs) < GESTURE_DEBOUNCE_MS
        lastGestureId = gestureId
        lastGestureAtMs = now
        if (isRepeated) {
            logDebug { "Gesture debounced id=$gestureId" }
        }
        return isRepeated
    }

    private fun shouldThrottleGlobalAction(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val throttled = (now - lastActionAtMs) < ACTION_THROTTLE_MS
        if (!throttled) {
            lastActionAtMs = now
        } else {
            logDebug { "Global action throttled" }
        }
        return throttled
    }

    private fun shouldRequestOverlayReclaim(packageName: String): Boolean {
        return OVERLAY_RECLAIM_PACKAGE_PREFIXES.any { prefix ->
            packageName.startsWith(prefix)
        }
    }

    private fun shouldAllowIncomingCallSurface(packageName: String): Boolean {
        return INCOMING_CALL_PACKAGE_PREFIXES.any { prefix ->
            packageName.startsWith(prefix)
        }
    }
}

