package com.tyejaedon.coverscreenos.overlay

import android.view.Display

/**
 * Decides whether an attached cover overlay should be held hidden in place rather than
 * detached when the target cover display becomes temporarily unavailable.
 *
 * Keeping a valid secondary-display window suppressed avoids unnecessary Compose view
 * teardown/recreation across brief display-topology transitions, while strictly preventing
 * the overlay from ever lingering on [Display.DEFAULT_DISPLAY].
 */
internal class OverlayAttachmentPolicy(
    private val defaultDisplayId: Int = Display.DEFAULT_DISPLAY
) {
    /**
     * Returns `true` if the overlay is attached to a non-default [Display] that
     * [android.hardware.display.DisplayManager] still reports as valid (`isValid == true`).
     */
    fun shouldHoldSuppressedOnCurrentDisplay(
        isOverlayAttached: Boolean,
        activeDisplayId: Int?,
        isActiveDisplayStillValid: Boolean
    ): Boolean {
        if (!isOverlayAttached) return false
        if (activeDisplayId == null) return false
        if (activeDisplayId == defaultDisplayId) return false
        return isActiveDisplayStillValid
    }
}

