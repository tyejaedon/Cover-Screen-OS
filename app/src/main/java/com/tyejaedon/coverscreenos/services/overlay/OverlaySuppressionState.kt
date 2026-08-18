package com.tyejaedon.coverscreenos.services.overlay

internal class OverlaySuppressionState {
    var isOverlaySuppressedForAppLaunch: Boolean = false
    var suppressionReason: OverlaySuppressionReason = OverlaySuppressionReason.NONE
    var launchSuppressedPackageName: String? = null
    var launchSuppressedStartedElapsedMs: Long = 0L
    var suppressionSessionId: Long = 0L
    var completedSuppressionSessionId: Long? = null

    var resumeSignalStableCount: Int = 0
    var lastResumeSignalPackage: String? = null

    var lastOverlayReclaimElapsedMs: Long = 0L
    var lastOverlayReclaimReason: String? = null

    var incomingCallPassthroughPackage: String? = null
    var incomingCallPassthroughStartedElapsedMs: Long = 0L
    var incomingCallLastSignalElapsedMs: Long = 0L
    var callNotificationActive: Boolean = false
    var callNotificationPackage: String? = null
    var callNotificationLastSignalElapsedMs: Long = 0L

    fun markSuppressionStarted(packageName: String, reason: OverlaySuppressionReason, nowElapsedMs: Long) {
        suppressionSessionId += 1L
        completedSuppressionSessionId = null
        isOverlaySuppressedForAppLaunch = true
        suppressionReason = reason
        launchSuppressedPackageName = packageName
        launchSuppressedStartedElapsedMs = nowElapsedMs
        resetResumeSignalStability()
    }

    fun clearSuppressionState() {
        isOverlaySuppressedForAppLaunch = false
        suppressionReason = OverlaySuppressionReason.NONE
        launchSuppressedPackageName = null
        launchSuppressedStartedElapsedMs = 0L
        resetResumeSignalStability()
    }

    fun hasStableResumeSignal(packageName: String, requiredCount: Int): Boolean {
        if (packageName == lastResumeSignalPackage) {
            resumeSignalStableCount += 1
        } else {
            lastResumeSignalPackage = packageName
            resumeSignalStableCount = 1
        }
        return resumeSignalStableCount >= requiredCount
    }

    fun resetResumeSignalStability() {
        lastResumeSignalPackage = null
        resumeSignalStableCount = 0
    }

    fun shouldSkipReclaimRequest(normalizedReason: String, nowElapsedMs: Long, minIntervalMs: Long): Boolean {
        if (normalizedReason != lastOverlayReclaimReason) return false
        return (nowElapsedMs - lastOverlayReclaimElapsedMs) < minIntervalMs
    }

    fun markReclaimRequested(normalizedReason: String, nowElapsedMs: Long) {
        lastOverlayReclaimElapsedMs = nowElapsedMs
        lastOverlayReclaimReason = normalizedReason
    }
}

