package com.tyejaedon.coverscreenos.services.overlay

import android.util.Log
import android.view.Display
import kotlinx.coroutines.flow.StateFlow

/**
 * `TYPE_ACCESSIBILITY_OVERLAY` implementation of [OverlayHost].
 *
 * Doesn't own any views itself — all Compose plumbing lives inside
 * [CoverAccessibilityService] (which alone has the accessibility
 * window context needed by the platform to accept the accessibility
 * overlay type). This class is a thin dispatcher so the [OverlayHostMode]
 * runtime switch inside [OverlayWindowController] can pick between
 * hosts without leaking `AccessibilityService` details to the
 * [ForegroundService].
 *
 * All entry points fail soft (`false` / `null`) when the accessibility
 * service is not currently bound or when the [LauncherOverlayHost]
 * handoff hasn't yet been installed. The [OverlayWindowController]
 * caller falls back to the legacy host in that case only if
 * explicitly re-configured — normal operation just retries on the
 * next display / reclaim signal.
 *
 * Introduced in Phase 3 of the migration —
 * see `docs/architecture/Overlay-architecture-shift-plan.md` §5.3, §7.
 */
internal class AccessibilityOverlayHost : OverlayHost {

    override fun showOverlay(
        targetDisplay: Display?,
        forceReattach: Boolean,
        // Unused: the AS reads device-lock state from the
        // LauncherOverlayHost.deviceLockState flow it was handed at
        // ForegroundService.onCreate. Kept for OverlayHost signature
        // parity with WindowManagerOverlayHost.
        deviceLockState: StateFlow<Boolean>?
    ): Boolean {
        val display = targetDisplay ?: run {
            Log.w(LOG_TAG, "showOverlay called without a target display — AS host requires one")
            return false
        }
        return CoverAccessibilityService.showLauncherOnActiveService(
            display = display,
            forceReattach = forceReattach
        )
    }

    override fun removeOverlay() {
        CoverAccessibilityService.hideLauncherOnActiveService(reason = "remove_overlay")
    }

    override fun hideOverlay() {
        suppressOverlayForLaunch()
    }

    override fun suppressOverlayForLaunch() {
        CoverAccessibilityService.setLauncherTouchableOnActiveService(touchable = false)
    }

    override fun destroy() {
        CoverAccessibilityService.hideLauncherOnActiveService(reason = "controller_destroy")
    }

    override fun getActiveDisplayId(): Int? =
        CoverAccessibilityService.launcherActiveDisplayIdOnActiveService()

    override fun isOverlayAttached(): Boolean =
        CoverAccessibilityService.isLauncherAttachedOnActiveService()

    private companion object {
        private const val LOG_TAG = "AccessibilityOvHost"
    }
}

