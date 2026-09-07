package com.tyejaedon.coverscreenos.services.overlay

import android.view.Display

internal class OverlayAttachmentPolicy(
    private val defaultDisplayId: Int = Display.DEFAULT_DISPLAY
) {
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

