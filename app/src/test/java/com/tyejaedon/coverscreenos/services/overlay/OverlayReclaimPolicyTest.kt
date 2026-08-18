package com.tyejaedon.coverscreenos.services.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReclaimPolicyTest {

    private val policy = OverlayReclaimPolicy(
        incomingCallSuppressionMaxMs = 10_000L,
        incomingCallReclaimBlockGraceMs = 5_000L
    )

    @Test
    fun `keep suppressed when foreground remains on call package`() {
        val state = OverlaySuppressionState().apply {
            incomingCallPassthroughPackage = "com.whatsapp"
            incomingCallPassthroughStartedElapsedMs = 100L
            incomingCallLastSignalElapsedMs = 100L
        }

        val keepSuppressed = policy.shouldKeepOverlaySuppressedForIncomingCall(
            state = state,
            nowElapsedMs = 700L,
            resolveRawForegroundPackage = { "com.whatsapp" },
            isIncomingCallPackage = { packageName -> packageName.startsWith("com.whatsapp") },
            onClearPassthrough = {}
        )

        assertTrue(keepSuppressed)
        assertEquals("com.whatsapp", state.incomingCallPassthroughPackage)
        assertEquals(700L, state.incomingCallLastSignalElapsedMs)
    }

    @Test
    fun `keep suppressed when active call notification remains`() {
        val state = OverlaySuppressionState().apply {
            incomingCallPassthroughPackage = "com.whatsapp"
            incomingCallPassthroughStartedElapsedMs = 100L
            incomingCallLastSignalElapsedMs = 120L
            callNotificationActive = true
            callNotificationPackage = "com.whatsapp.w4b"
            callNotificationLastSignalElapsedMs = 120L
        }

        val keepSuppressed = policy.shouldKeepOverlaySuppressedForIncomingCall(
            state = state,
            nowElapsedMs = 900L,
            resolveRawForegroundPackage = { null },
            isIncomingCallPackage = { false },
            onClearPassthrough = {}
        )

        assertTrue(keepSuppressed)
        assertEquals("com.whatsapp.w4b", state.incomingCallPassthroughPackage)
        assertEquals(900L, state.callNotificationLastSignalElapsedMs)
    }

    @Test
    fun `clear passthrough after grace when no foreground or notification signal`() {
        val state = OverlaySuppressionState().apply {
            incomingCallPassthroughPackage = "com.whatsapp"
            incomingCallPassthroughStartedElapsedMs = 100L
            incomingCallLastSignalElapsedMs = 1_000L
            callNotificationLastSignalElapsedMs = 1_000L
            callNotificationActive = false
        }
        var clearReason: String? = null

        val keepSuppressed = policy.shouldKeepOverlaySuppressedForIncomingCall(
            state = state,
            nowElapsedMs = 7_000L,
            resolveRawForegroundPackage = { null },
            isIncomingCallPackage = { false },
            onClearPassthrough = { reason ->
                clearReason = reason
                policy.clearIncomingCallPassthrough(state = state, reason = reason)
            }
        )

        assertFalse(keepSuppressed)
        assertEquals("foreground_exited_call_surface", clearReason)
        assertEquals(null, state.incomingCallPassthroughPackage)
        assertFalse(state.callNotificationActive)
    }

    @Test
    fun `clear passthrough when max call suppression is exceeded`() {
        val state = OverlaySuppressionState().apply {
            incomingCallPassthroughPackage = "com.whatsapp"
            incomingCallPassthroughStartedElapsedMs = 100L
            incomingCallLastSignalElapsedMs = 100L
        }
        var clearReason: String? = null

        val keepSuppressed = policy.shouldKeepOverlaySuppressedForIncomingCall(
            state = state,
            nowElapsedMs = 10_200L,
            resolveRawForegroundPackage = { "com.whatsapp" },
            isIncomingCallPackage = { true },
            onClearPassthrough = { reason ->
                clearReason = reason
                policy.clearIncomingCallPassthrough(state = state, reason = reason)
            }
        )

        assertFalse(keepSuppressed)
        assertEquals("max_suppression_elapsed", clearReason)
        assertEquals(null, state.incomingCallPassthroughPackage)
    }

    @Test
    fun `reclaim is blocked while within recent call signal grace`() {
        val state = OverlaySuppressionState().apply {
            incomingCallPassthroughPackage = "com.whatsapp"
            incomingCallLastSignalElapsedMs = 1_000L
            callNotificationLastSignalElapsedMs = 0L
            callNotificationActive = false
        }

        val blocked = policy.shouldBlockReclaimForIncomingCall(
            state = state,
            nowElapsedMs = 5_500L,
            resolveRawForegroundPackage = { null },
            isIncomingCallPackage = { false }
        )

        assertTrue(blocked)
    }

    @Test
    fun `reclaim is blocked when call notification remains active`() {
        val state = OverlaySuppressionState().apply {
            incomingCallPassthroughPackage = "com.whatsapp"
            incomingCallLastSignalElapsedMs = 0L
            callNotificationActive = true
            callNotificationLastSignalElapsedMs = 0L
        }

        val blocked = policy.shouldBlockReclaimForIncomingCall(
            state = state,
            nowElapsedMs = 20_000L,
            resolveRawForegroundPackage = { null },
            isIncomingCallPackage = { false }
        )

        assertTrue(blocked)
    }

    @Test
    fun `reclaim is allowed after grace when no active call signals remain`() {
        val state = OverlaySuppressionState().apply {
            incomingCallPassthroughPackage = "com.whatsapp"
            incomingCallLastSignalElapsedMs = 1_000L
            callNotificationLastSignalElapsedMs = 1_000L
            callNotificationActive = false
        }

        val blocked = policy.shouldBlockReclaimForIncomingCall(
            state = state,
            nowElapsedMs = 7_000L,
            resolveRawForegroundPackage = { null },
            isIncomingCallPackage = { false }
        )

        assertFalse(blocked)
    }
}

