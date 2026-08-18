package com.tyejaedon.coverscreenos.ui.launcher

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object OverlayLayoutSpec {
    val horizontalMargin: Dp = 16.dp
    val verticalPadding: Dp = 12.dp
    val maxOverlayWidth: Dp = 560.dp
    val contentTopOffset: Dp = 72.dp

    // Caps overlay width for large screens and preserves side margins on smaller screens.
    fun overlayWidth(screenWidth: Dp): Dp {
        val available = (screenWidth - (horizontalMargin * 2)).coerceAtLeast(0.dp)
        return available.coerceAtMost(maxOverlayWidth)
    }
}


