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
        private const val GESTURE_DEBOUNCE_MS = 550L
        private const val ACTION_THROTTLE_MS = 300L
    }

    private var lastWindowPackage: CharSequence? = null
    private var lastGestureId: Int? = null
    private var lastGestureAtMs: Long = 0L
    private var lastActionAtMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("CoverAccessibility", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            if (event.packageName != lastWindowPackage) {
                lastWindowPackage = event.packageName
                Log.d("CoverAccessibility", "Window changed: ${event.packageName}")
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
        Log.d("CoverAccessibility", "Gesture=$gestureId action=$globalAction result=$result")
        return result
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!ForegroundService.isOverlayActive) return super.onKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (shouldThrottleGlobalAction()) return true
            val result = performGlobalAction(GLOBAL_ACTION_BACK)
            Log.d("CoverAccessibility", "Back key intercepted result=$result")
            return result
        }

        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {
        Log.d("CoverAccessibility", "Accessibility service interrupted")
    }

    private fun shouldDebounceGesture(gestureId: Int): Boolean {
        val now = SystemClock.elapsedRealtime()
        val isRepeated = lastGestureId == gestureId && (now - lastGestureAtMs) < GESTURE_DEBOUNCE_MS
        lastGestureId = gestureId
        lastGestureAtMs = now
        if (isRepeated) {
            Log.d("CoverAccessibility", "Gesture debounced id=$gestureId")
        }
        return isRepeated
    }

    private fun shouldThrottleGlobalAction(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val throttled = (now - lastActionAtMs) < ACTION_THROTTLE_MS
        if (!throttled) {
            lastActionAtMs = now
        } else {
            Log.d("CoverAccessibility", "Global action throttled")
        }
        return throttled
    }
}

