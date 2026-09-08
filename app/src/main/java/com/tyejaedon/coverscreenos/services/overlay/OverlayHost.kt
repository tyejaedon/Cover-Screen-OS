package com.tyejaedon.coverscreenos.services.overlay

import android.view.Display
import kotlinx.coroutines.flow.StateFlow

/**
 * Strategy interface implemented by both the legacy
 * [WindowManagerOverlayHost] (owned by [ForegroundService], attaches a
 * `TYPE_APPLICATION_OVERLAY`) and the new [AccessibilityOverlayHost]
 * (delegates to [CoverAccessibilityService], attaches a
 * `TYPE_ACCESSIBILITY_OVERLAY`).
 *
 * The method surface is intentionally an exact mirror of the public
 * API of [OverlayWindowController] so the façade can dispatch without
 * translation and existing call sites (e.g. `ForegroundService.attachOverlayToTarget`)
 * see no behavior change when [OverlayHostMode] flips at runtime.
 *
 * Introduced in Phase 3 of the `TYPE_ACCESSIBILITY_OVERLAY` migration —
 * see `docs/architecture/Overlay-architecture-shift-plan.md` §5.3.
 */
internal interface OverlayHost {
    fun showOverlay(
        targetDisplay: Display?,
        forceReattach: Boolean,
        deviceLockState: StateFlow<Boolean>?
    ): Boolean

    fun removeOverlay()

    fun hideOverlay()

    fun suppressOverlayForLaunch()

    fun destroy()

    fun getActiveDisplayId(): Int?

    fun isOverlayAttached(): Boolean
}

