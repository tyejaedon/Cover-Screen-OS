package com.tyejaedon.coverscreenos.helpers

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

class CoverDisplayHelper(private val context: Context) {

    private val displayManager: DisplayManager by lazy {
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    /**
     * Discovers and returns the secondary display (the cover screen).
     * Returns null if no secondary display is currently connected.
     */
    fun getCoverDisplay(): Display? {
        // Prefer active presentation displays first.
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        presentationDisplays.firstOrNull { it.isUsableCoverDisplay() }?.let { return it }

        // Fallback to any active, valid non-default display.
        return displayManager.displays.firstOrNull { display ->
            display.isUsableCoverDisplay()
        }
    }

    fun getCoverDisplayId(): Int? {
        return getCoverDisplay()?.displayId
    }

    private fun Display.isUsableCoverDisplay(): Boolean {
        return displayId != Display.DEFAULT_DISPLAY && isValid && state != Display.STATE_OFF
    }

    fun describeDisplays(): String {
        return displayManager.displays.joinToString(prefix = "[", postfix = "]") { display ->
            "id=${display.displayId},state=${display.state},valid=${display.isValid}"
        }
    }
}