package com.tyejaedon.coverscreenos.ui.launcher

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared layout dimensions and width-clamping rules for cover-screen overlay sheets.
 *
 * Ensures overlay content preserves consistent side margins on compact clamshell cover
 * displays while capping maximum width on larger foldable or tablet screens.
 */
object OverlayLayoutSpec {
    /** Outer horizontal margin reserved on both the left and right edges of overlay sheets. */
    val horizontalMargin: Dp = 16.dp

    /** Default top and bottom inner padding applied inside overlay sheet containers. */
    val verticalPadding: Dp = 12.dp

    /** Maximum width allowed for overlay sheets so content stays compact on larger displays. */
    val maxOverlayWidth: Dp = 560.dp

    /** Top vertical offset clearing system/cover status indicators above overlay content. */
    val contentTopOffset: Dp = 72.dp

    /**
     * Computes the overlay content width for a given [screenWidth].
     *
     * Caps overlay width for large screens and preserves side margins on smaller screens.
     */
    fun overlayWidth(screenWidth: Dp): Dp {
        // Remaining width after reserving both horizontal margins, never dropping below zero.
        val available = (screenWidth - (horizontalMargin * 2)).coerceAtLeast(0.dp)
        return available.coerceAtMost(maxOverlayWidth)
    }
}


