package com.tyejaedon.coverscreenos.overlay

/**
 * Mutable state machine data backing [ForegroundService]'s overlay suppression lifecycle.
 *
 * Tracks the active suppression session ID (used to deduplicate concurrent resume completions),
 * consecutive foreground signals for debounce stability, reclaim throttling intervals, and
 * incoming-call passthrough timers.
 */
internal class OverlaySuppressionState {
    /** Whether the launcher overlay is currently suppressed (non-touchable/hidden). */
    var isOverlaySuppressedForAppLaunch: Boolean = false
    /** Active category driving suppression, or [OverlaySuppressionReason.NONE] when visible. */
    var suppressionReason: OverlaySuppressionReason = OverlaySuppressionReason.NONE
    /** Package name associated with the current suppression (e.g., launched app or dialer). */
    var launchSuppressedPackageName: String? = null
    /** Monotonic [android.os.SystemClock.elapsedRealtime] timestamp when suppression started. */
    var launchSuppressedStartedElapsedMs: Long = 0L
    /** Monotonically incremented ID identifying the current suppression session. */
    var suppressionSessionId: Long = 0L
    /** Last session ID that completed restoration, preventing duplicate retargets. */
    var completedSuppressionSessionId: Long? = null

    /** Consecutive identical foreground observations seen during resume polling. */
    var resumeSignalStableCount: Int = 0
    /** Package observed on the previous resume poll tick for stability comparison. */
    var lastResumeSignalPackage: String? = null

    /** Monotonic timestamp of the last accepted overlay reclaim request. */
    var lastOverlayReclaimElapsedMs: Long = 0L
    /** Normalized reason string of the last accepted overlay reclaim request. */
    var lastOverlayReclaimReason: String? = null

    /** Package currently holding incoming-call passthrough priority, if any. */
    var incomingCallPassthroughPackage: String? = null
    /** Monotonic timestamp when incoming-call passthrough began. */
    var incomingCallPassthroughStartedElapsedMs: Long = 0L
    /** Monotonic timestamp of the most recent foreground call-package observation. */
    var incomingCallLastSignalElapsedMs: Long = 0L
    /** Whether [com.tyejaedon.coverscreenos.notifications.CoverNotificationListenerService] reports an active call notification. */
    var callNotificationActive: Boolean = false
    /** Package owning the active call notification, if reported. */
    var callNotificationPackage: String? = null
    /** Monotonic timestamp of the last active call-notification signal. */
    var callNotificationLastSignalElapsedMs: Long = 0L

    /**
     * Begins a new suppression session for [packageName] and [reason] at [nowElapsedMs],
     * incrementing [suppressionSessionId] and resetting resume-stability counters.
     */
    fun markSuppressionStarted(packageName: String, reason: OverlaySuppressionReason, nowElapsedMs: Long) {
        suppressionSessionId += 1L
        completedSuppressionSessionId = null
        isOverlaySuppressedForAppLaunch = true
        suppressionReason = reason
        launchSuppressedPackageName = packageName
        launchSuppressedStartedElapsedMs = nowElapsedMs
        resetResumeSignalStability()
    }

    /** Clears active suppression state and returns [suppressionReason] to [OverlaySuppressionReason.NONE]. */
    fun clearSuppressionState() {
        isOverlaySuppressedForAppLaunch = false
        suppressionReason = OverlaySuppressionReason.NONE
        launchSuppressedPackageName = null
        launchSuppressedStartedElapsedMs = 0L
        resetResumeSignalStability()
    }

    /**
     * Debounces rapid foreground flicker by requiring [requiredCount] consecutive
     * observations of [packageName] before allowing resume.
     */
    fun hasStableResumeSignal(packageName: String, requiredCount: Int): Boolean {
        if (packageName == lastResumeSignalPackage) {
            resumeSignalStableCount += 1
        } else {
            lastResumeSignalPackage = packageName
            resumeSignalStableCount = 1
        }
        return resumeSignalStableCount >= requiredCount
    }

    /** Resets the consecutive foreground package counter used by [hasStableResumeSignal]. */
    fun resetResumeSignalStability() {
        lastResumeSignalPackage = null
        resumeSignalStableCount = 0
    }

    /**
     * Returns `true` if a reclaim request with the same [normalizedReason] arrived
     * within [minIntervalMs] of the last accepted reclaim.
     */
    fun shouldSkipReclaimRequest(normalizedReason: String, nowElapsedMs: Long, minIntervalMs: Long): Boolean {
        if (normalizedReason != lastOverlayReclaimReason) return false
        return (nowElapsedMs - lastOverlayReclaimElapsedMs) < minIntervalMs
    }

    /** Records an accepted reclaim request at [nowElapsedMs] for subsequent rate-limiting. */
    fun markReclaimRequested(normalizedReason: String, nowElapsedMs: Long) {
        lastOverlayReclaimElapsedMs = nowElapsedMs
        lastOverlayReclaimReason = normalizedReason
    }
}

