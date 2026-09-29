package com.tyejaedon.coverscreenos.services.overlay

import android.util.Log
import android.view.Display

/**
 * Launcher dispatch seam; the accessibility service owns the only launcher window.
 */
internal class OverlayWindowController {
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

    fun removeOverlay() {
        CoverAccessibilityService.hideLauncherOnActiveService(reason = "remove_overlay")
    }

    fun hideOverlay() {
        suppressOverlayForLaunch()
    }

    fun suppressOverlayForLaunch() {
        CoverAccessibilityService.setLauncherTouchableOnActiveService(touchable = false)
    }

    fun destroy() {
        CoverAccessibilityService.hideLauncherOnActiveService(reason = "controller_destroy")
    }

    fun getActiveDisplayId(): Int? = CoverAccessibilityService.launcherActiveDisplayIdOnActiveService()

    fun isOverlayAttached(): Boolean = CoverAccessibilityService.isLauncherAttachedOnActiveService()

    private companion object {
        private const val LOG_TAG = "OverlayWindowController"
    }
}
