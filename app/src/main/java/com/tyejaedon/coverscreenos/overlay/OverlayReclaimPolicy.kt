package com.tyejaedon.coverscreenos.overlay

/**
 * Policy governing incoming-call passthrough and reclaim suppression.
 *
 * Ensures the cover launcher overlay stays suppressed while a dialer window or
 * active call notification is present—plus a short post-signal grace period
 * ([incomingCallReclaimBlockGraceMs]) to absorb transient window transitions—while
 * enforcing a hard upper bound ([incomingCallSuppressionMaxMs]) against stuck states.
 */
internal class OverlayReclaimPolicy(
    private val incomingCallSuppressionMaxMs: Long,
    private val incomingCallReclaimBlockGraceMs: Long
) {
    /**
     * Starts or refreshes incoming-call passthrough tracking in [state] for [packageName]
     * at monotonic timestamp [nowElapsedMs].
     */
    fun trackIncomingCallPassthrough(state: OverlaySuppressionState, packageName: String, nowElapsedMs: Long) {
        if (state.incomingCallPassthroughPackage == null) {
            state.incomingCallPassthroughStartedElapsedMs = nowElapsedMs
        }
        state.incomingCallPassthroughPackage = packageName
        state.incomingCallLastSignalElapsedMs = nowElapsedMs
    }

    /**
     * Clears both window-based and notification-based incoming-call passthrough fields
     * on [state], invoking [onDebugLog] if a passthrough session was active.
     */
    fun clearIncomingCallPassthrough(
        state: OverlaySuppressionState,
        reason: String,
        onDebugLog: ((String) -> Unit)? = null
    ) {
        val activePackage = state.incomingCallPassthroughPackage
        if (activePackage != null) {
            onDebugLog?.invoke("incoming_call_passthrough_cleared reason=$reason package=$activePackage")
        }
        state.incomingCallPassthroughPackage = null
        state.incomingCallPassthroughStartedElapsedMs = 0L
        state.incomingCallLastSignalElapsedMs = 0L
        state.callNotificationActive = false
        state.callNotificationPackage = null
        state.callNotificationLastSignalElapsedMs = 0L
    }

    /**
     * Evaluates during resume polling whether overlay suppression must continue for an
     * incoming or ongoing call.
     *
     * Checks, in order: the safety timeout ([incomingCallSuppressionMaxMs]), live call
     * foreground packages, active call notifications, and the grace window after the last
     * call signal. Invokes [onClearPassthrough] when passthrough has expired or exited.
     */
    fun shouldKeepOverlaySuppressedForIncomingCall(
        state: OverlaySuppressionState,
        nowElapsedMs: Long,
        resolveRawForegroundPackage: () -> String?,
        isIncomingCallPackage: (String) -> Boolean,
        onClearPassthrough: (String) -> Unit
    ): Boolean {
        if (state.incomingCallPassthroughPackage == null) return false

        if (state.incomingCallPassthroughStartedElapsedMs > 0L &&
            (nowElapsedMs - state.incomingCallPassthroughStartedElapsedMs) > incomingCallSuppressionMaxMs
        ) {
            onClearPassthrough("max_suppression_elapsed")
            return false
        }

        val foregroundPackage = resolveRawForegroundPackage()
        if (foregroundPackage != null && isIncomingCallPackage(foregroundPackage)) {
            state.incomingCallPassthroughPackage = foregroundPackage
            state.incomingCallLastSignalElapsedMs = nowElapsedMs
            return true
        }

        if (state.callNotificationActive) {
            state.callNotificationLastSignalElapsedMs = nowElapsedMs
            state.callNotificationPackage?.let { state.incomingCallPassthroughPackage = it }
            return true
        }

        if ((nowElapsedMs - state.incomingCallLastSignalElapsedMs) <= incomingCallReclaimBlockGraceMs) {
            return true
        }

        if ((nowElapsedMs - state.callNotificationLastSignalElapsedMs) <= incomingCallReclaimBlockGraceMs) {
            return true
        }

        onClearPassthrough("foreground_exited_call_surface")
        return false
    }

    /**
     * Synchronous guard checked when an accessibility reclaim signal arrives, returning `true`
     * if an active call surface, call notification, or recent grace window should block reclaim.
     */
    fun shouldBlockReclaimForIncomingCall(
        state: OverlaySuppressionState,
        nowElapsedMs: Long,
        resolveRawForegroundPackage: () -> String?,
        isIncomingCallPackage: (String) -> Boolean
    ): Boolean {
        if (state.incomingCallPassthroughPackage == null) return false

        val foregroundPackage = resolveRawForegroundPackage()
        if (foregroundPackage != null && isIncomingCallPackage(foregroundPackage)) {
            state.incomingCallPassthroughPackage = foregroundPackage
            state.incomingCallLastSignalElapsedMs = nowElapsedMs
            return true
        }

        if (state.callNotificationActive) return true

        return (nowElapsedMs - state.incomingCallLastSignalElapsedMs) <= incomingCallReclaimBlockGraceMs ||
            (nowElapsedMs - state.callNotificationLastSignalElapsedMs) <= incomingCallReclaimBlockGraceMs
    }
}

