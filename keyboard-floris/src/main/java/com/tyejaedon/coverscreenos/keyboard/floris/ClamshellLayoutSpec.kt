package com.tyejaedon.coverscreenos.keyboard.floris

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Anchor alignment for one-handed operation on clamshell cover screens.
 */
enum class ClamshellDockAnchor {
    LEFT,
    CENTER,
    RIGHT;

    fun toggled(): ClamshellDockAnchor = when (this) {
        LEFT -> RIGHT
        RIGHT -> LEFT
        CENTER -> LEFT
    }
}

/**
 * Viewport and sizing constraints tailored specifically for foldable clamshell cover screens.
 *
 * Prevents standard Android keyboard dimensions (>60% screen height) from occluding the focused
 * text field or search widgets on secondary hardware displays.
 */
data class ClamshellLayoutSpec(
    val maxHeightRatio: Float = 0.44f,
    val keyHeightFloorDp: Dp = 34.dp,
    val keyHeightCeilingDp: Dp = 58.dp,
    val rowSpacingDp: Dp = 3.dp,
    val dockAnchor: ClamshellDockAnchor = ClamshellDockAnchor.CENTER,
    val isOneHandedEnabled: Boolean = false,
    val extraHeightDp: Float = 0f
) {
    /**
     * Calculates the effective key row height given the available screen height.
     */
    fun calculateRowHeight(screenHeightDp: Dp, rowCount: Int): Dp {
        val safeRowCount = rowCount.coerceAtLeast(1)
        val maxTotalHeight = screenHeightDp * maxHeightRatio
        val availableForRows = maxTotalHeight - (rowSpacingDp * (safeRowCount - 1))
        val baseRowHeight = availableForRows / safeRowCount
        val adjusted = baseRowHeight + extraHeightDp.dp
        return adjusted.coerceIn(keyHeightFloorDp, keyHeightCeilingDp)
    }

    /**
     * Computes the total height of all keyboard rows combined.
     */
    fun calculateTotalKeyboardHeight(screenHeightDp: Dp, rowCount: Int): Dp {
        val rowHeight = calculateRowHeight(screenHeightDp, rowCount)
        val safeRowCount = rowCount.coerceAtLeast(1)
        return (rowHeight * safeRowCount) + (rowSpacingDp * (safeRowCount - 1))
    }

    companion object {
        val Default = ClamshellLayoutSpec()

        val Compact = ClamshellLayoutSpec(
            maxHeightRatio = 0.38f,
            keyHeightFloorDp = 32.dp,
            keyHeightCeilingDp = 48.dp
        )
    }
}
