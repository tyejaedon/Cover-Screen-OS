package com.tyejaedon.coverscreenos.overlay

import android.util.Log
import android.view.Display

/**
 * Window-command facade used by [ForegroundService] to show, suppress, or detach the
 * cover launcher overlay without coupling to `WindowManager` or Compose internals.
 *
 * [CoverAccessibilityService] owns the single `TYPE_ACCESSIBILITY_OVERLAY` surface
 * and its system accessibility window token; all commands here delegate to the active
 * accessibility service instance (no-op if unbound).
 */
internal class OverlayWindowController {
    /**
     * Attaches (or re-enables touch on) the launcher overlay on [targetDisplay].
     *
     * Pass [forceReattach] = `true` when switching between display IDs so the window
     * context is rebuilt for the new display. Returns `false` if [targetDisplay] is
     * `null` or no [CoverAccessibilityService] is bound.
     */
    fun showOverlay(
        targetDisplay: Display? = null,
        forceReattach: Boolean = false
    ): Boolean {
        if (targetDisplay == null) {
            Log.w(LOG_TAG, "Cannot show launcher without a cover display")
            return false
        }
        return CoverAccessibilityService.showLauncherOnActiveService(targetDisplay, forceReattach)
    }

    /** Detaches and destroys the overlay window from the active accessibility service. */
    fun removeOverlay() {
        CoverAccessibilityService.hideLauncherOnActiveService(reason = "remove_overlay")
    }

    /** Keeps the overlay attached in place while making it transparent and non-touchable. */
    fun hideOverlay() {
        suppressOverlayForLaunch()
    }

    /**
     * Suppresses user interaction by applying `FLAG_NOT_TOUCHABLE` and hiding alpha on
     * the overlay window so launched apps beneath it receive focus and touches.
     */
    fun suppressOverlayForLaunch() {
        CoverAccessibilityService.setLauncherTouchableOnActiveService(touchable = false)
    }

    /** Detaches the launcher overlay during [ForegroundService] shutdown. */
    fun destroy() {
        CoverAccessibilityService.hideLauncherOnActiveService(reason = "controller_destroy")
    }

    /** Returns the [Display] ID hosting the currently attached overlay, or `null`. */
    fun getActiveDisplayId(): Int? = CoverAccessibilityService.launcherActiveDisplayIdOnActiveService()

    /** Returns `true` if the launcher `ComposeView` is currently attached to a window. */
    fun isOverlayAttached(): Boolean = CoverAccessibilityService.isLauncherAttachedOnActiveService()

    private companion object {
        private const val LOG_TAG = "OverlayWindowController"
    }
}
