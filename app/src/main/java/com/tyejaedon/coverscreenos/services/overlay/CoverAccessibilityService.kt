package com.tyejaedon.coverscreenos.services.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityGestureEvent
import android.annotation.SuppressLint
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.tyejaedon.coverscreenos.services.CallPackageMatchers

@SuppressLint("AccessibilityPolicy")
class CoverAccessibilityService : AccessibilityService() {
    companion object {
        private const val LOG_TAG = "CoverAccessibility"
        private const val OVERLAY_RECLAIM_LOG_TAG = "CoverOverlayReclaim"
        private const val GESTURE_DEBOUNCE_MS = 550L
        private const val ACTION_THROTTLE_MS = 300L
        private const val FOREGROUND_EVENT_REFRESH_MIN_INTERVAL_MS = 250L
        private const val RECLAIM_STABILITY_DEBOUNCE_MS = 1_500L
        private const val RECENT_USER_APP_GUARD_MS = 2_000L
        private val OVERLAY_RECLAIM_PACKAGE_PREFIXES = arrayOf(
            "com.sec.android.app.launcher"
        )
        private val TRANSIENT_SYSTEM_UI_PREFIXES = arrayOf(
            "com.android.systemui",
            "com.samsung.systemui",
            "com.samsung.android.app.aodservice"
        )
        private val NON_USER_APP_PREFIXES = arrayOf(
            "com.android.systemui",
            "com.samsung.systemui",
            "com.samsung.android.app.aodservice",
            "com.sec.android.app.launcher",
            "com.tyejaedon.coverscreenos"
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
    private var reclaimCandidatePackage: String? = null
    private var reclaimCandidateSinceElapsedMs: Long = 0L
    private var lastKnownUserAppPackage: String? = null
    private var lastKnownUserAppElapsedMs: Long = 0L

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

        val eventTypeName = eventTypeToName(event.eventType)
        if (shouldTrackAsUserForegroundApp(foregroundPackage)) {
            lastKnownUserAppPackage = foregroundPackage
            lastKnownUserAppElapsedMs = nowElapsedMs
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            logReclaimDecision(
                eventTypeName = eventTypeName,
                packageName = foregroundPackage,
                decision = "skip_non_state_event"
            )
            return
        }

        if (foregroundPackage != lastWindowPackage) {
            lastWindowPackage = foregroundPackage
            logDebug { "Window changed: $foregroundPackage" }
        }

        if (shouldAllowIncomingCallSurface(foregroundPackage)) {
            logReclaimDecision(eventTypeName, foregroundPackage, "incoming_call_passthrough")
            ForegroundService.requestIncomingCallPassthrough(foregroundPackage)
            return
        }

        if (!shouldRequestOverlayReclaim(foregroundPackage)) {
            if (isTransientSystemUiPackage(foregroundPackage)) {
                logReclaimDecision(eventTypeName, foregroundPackage, "skip_transient_system_ui")
            }
            resetReclaimCandidate()
            return
        }

        if (reclaimCandidatePackage != foregroundPackage) {
            reclaimCandidatePackage = foregroundPackage
            reclaimCandidateSinceElapsedMs = nowElapsedMs
            logReclaimDecision(eventTypeName, foregroundPackage, "candidate_started")
            return
        }

        val stableForMs = nowElapsedMs - reclaimCandidateSinceElapsedMs
        if (stableForMs < RECLAIM_STABILITY_DEBOUNCE_MS) {
            logReclaimDecision(
                eventTypeName = eventTypeName,
                packageName = foregroundPackage,
                decision = "waiting_stability",
                detail = "stableForMs=$stableForMs requiredMs=$RECLAIM_STABILITY_DEBOUNCE_MS"
            )
            return
        }

        val userAppAgeMs = if (lastKnownUserAppElapsedMs > 0L) {
            (nowElapsedMs - lastKnownUserAppElapsedMs).coerceAtLeast(0L)
        } else {
            Long.MAX_VALUE
        }
        if (lastKnownUserAppPackage != null && userAppAgeMs < RECENT_USER_APP_GUARD_MS) {
            logReclaimDecision(
                eventTypeName = eventTypeName,
                packageName = foregroundPackage,
                decision = "blocked_recent_user_app",
                detail = "userApp=$lastKnownUserAppPackage ageMs=$userAppAgeMs guardMs=$RECENT_USER_APP_GUARD_MS"
            )
            return
        }

        logReclaimDecision(
            eventTypeName = eventTypeName,
            packageName = foregroundPackage,
            decision = "trigger_reclaim",
            detail = "stableForMs=$stableForMs"
        )
        ForegroundService.requestOverlayReclaim(reason = foregroundPackage)
    }

    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean {
        if (!ForegroundService.isOverlayActive) return false

        val gestureId = gestureEvent.gestureId
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

    private fun shouldTrackAsUserForegroundApp(packageName: String): Boolean {
        return NON_USER_APP_PREFIXES.none { prefix ->
            packageName.startsWith(prefix)
        }
    }

    private fun isTransientSystemUiPackage(packageName: String): Boolean {
        return TRANSIENT_SYSTEM_UI_PREFIXES.any { prefix ->
            packageName.startsWith(prefix)
        }
    }

    private fun resetReclaimCandidate() {
        reclaimCandidatePackage = null
        reclaimCandidateSinceElapsedMs = 0L
    }

    private fun eventTypeToName(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "TYPE_WINDOWS_CHANGED"
            else -> "TYPE_$eventType"
        }
    }

    private fun logReclaimDecision(
        eventTypeName: String,
        packageName: String,
        decision: String,
        detail: String? = null
    ) {
        if (!Log.isLoggable(OVERLAY_RECLAIM_LOG_TAG, Log.DEBUG)) return

        val candidatePackage = reclaimCandidatePackage ?: "<none>"
        val candidateAgeMs = if (reclaimCandidateSinceElapsedMs > 0L) {
            (SystemClock.elapsedRealtime() - reclaimCandidateSinceElapsedMs).coerceAtLeast(0L)
        } else {
            -1L
        }
        val userApp = lastKnownUserAppPackage ?: "<none>"
        val userAppAgeMs = if (lastKnownUserAppElapsedMs > 0L) {
            (SystemClock.elapsedRealtime() - lastKnownUserAppElapsedMs).coerceAtLeast(0L)
        } else {
            -1L
        }

        val detailSuffix = detail?.let { " $it" } ?: ""
        Log.d(
            OVERLAY_RECLAIM_LOG_TAG,
            "event=$eventTypeName package=$packageName decision=$decision candidate=$candidatePackage candidateAgeMs=$candidateAgeMs userApp=$userApp userAppAgeMs=$userAppAgeMs$detailSuffix"
        )
    }

    private fun shouldAllowIncomingCallSurface(packageName: String): Boolean {
        return CallPackageMatchers.isIncomingCallPackage(packageName)
    }
}


