package com.tyejaedon.coverscreenos.keyboard.floris

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Anchor alignment for one-handed operation on clamshell cover screens.
 */
enum class ClamshellDockAnchor {
    /** Hugs the left edge of the cover screen for left-thumb typing. */
    LEFT,
    /** Centered across the full width (standard two-handed mode). */
    CENTER,
    /** Hugs the right edge of the cover screen for right-thumb typing. */
    RIGHT;

    /** Flips between left and right one-handed edges (centered defaults to [LEFT]). */
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
 *
 * @property maxHeightRatio Maximum share of total cover-screen height the key grid may take (e.g. `0.44f` = 44%).
 * @property keyHeightFloorDp Minimum row height so keys never shrink below a comfortable tap target.
 * @property keyHeightCeilingDp Maximum row height so keys do not look oversized on taller screens.
 * @property rowSpacingDp Vertical gap placed between consecutive keyboard rows.
 * @property dockAnchor Left/center/right horizontal placement used when one-handed mode is active.
 * @property isOneHandedEnabled Whether the keyboard narrows and docks to one side for single-thumb reach.
 * @property extraHeightDp User drag-resize offset (in dp) added onto the computed base row height.
 */
data class ClamshellLayoutSpec(
    val maxHeightRatio: Float = 0.44f,
    val keyHeightFloorDp: Dp = 34.dp,
    val keyHeightCeilingDp: Dp = 52.dp,
    val rowSpacingDp: Dp = 5.dp,
    val dockAnchor: ClamshellDockAnchor = ClamshellDockAnchor.CENTER,
    val isOneHandedEnabled: Boolean = false,
    val extraHeightDp: Float = 0f
) {
    /**
     * Calculates the effective key row height given the available screen height.
     */
    fun calculateRowHeight(screenHeightDp: Dp, rowCount: Int): Dp {
        // Avoid division by zero if a layout ever passes an empty row list.
        val safeRowCount = rowCount.coerceAtLeast(1)
        // Total vertical budget reserved for the keyboard grid on this cover display.
        val maxTotalHeight = screenHeightDp * maxHeightRatio
        // Subtract gaps between rows so only actual key height gets divided per row.
        val availableForRows = maxTotalHeight - (rowSpacingDp * (safeRowCount - 1))
        val baseRowHeight = availableForRows / safeRowCount
        // Apply the user's vertical resize drag offset, then clamp within comfort bounds.
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

    /** Prebuilt clamshell sizing presets. */
    companion object {
        /** Balanced default profile (44% max height, 34–52dp key rows). */
        val Default = ClamshellLayoutSpec()

        /** Tighter profile for very short cover displays or widget overlays (38% max height). */
        val Compact = ClamshellLayoutSpec(
            maxHeightRatio = 0.38f,
            keyHeightFloorDp = 32.dp,
            keyHeightCeilingDp = 48.dp
        )
    }
}
