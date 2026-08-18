package com.tyejaedon.coverscreenos.services.overlay

internal class OverlayReclaimPolicy(
    private val incomingCallSuppressionMaxMs: Long,
    private val incomingCallReclaimBlockGraceMs: Long
) {
    fun trackIncomingCallPassthrough(state: OverlaySuppressionState, packageName: String, nowElapsedMs: Long) {
        if (state.incomingCallPassthroughPackage == null) {
            state.incomingCallPassthroughStartedElapsedMs = nowElapsedMs
        }
        state.incomingCallPassthroughPackage = packageName
        state.incomingCallLastSignalElapsedMs = nowElapsedMs
    }

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

