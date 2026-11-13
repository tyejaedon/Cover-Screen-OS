package com.tyejaedon.coverscreenos.keyboard.floris

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClamshellLayoutSpecTest {

    @Test
    fun testRowHeightClampedWithinFloorAndCeiling() {
        val spec = ClamshellLayoutSpec(
            maxHeightRatio = 0.40f,
            keyHeightFloorDp = 34.dp,
            keyHeightCeilingDp = 58.dp,
            rowSpacingDp = 3.dp
        )

        // Short screen (e.g. 300dp screen height)
        val shortHeight = spec.calculateRowHeight(300.dp, rowCount = 4)
        assertEquals(34.dp, shortHeight)

        // Tall screen (e.g. 1000dp screen height)
        val tallHeight = spec.calculateRowHeight(1000.dp, rowCount = 4)
        assertEquals(58.dp, tallHeight)

        // Mid-size clamshell screen (e.g. 512dp)
        val midHeight = spec.calculateRowHeight(512.dp, rowCount = 4)
        assertTrue(midHeight in 34.dp..58.dp)
    }

    @Test
    fun testDockAnchorToggle() {
        assertEquals(ClamshellDockAnchor.RIGHT, ClamshellDockAnchor.LEFT.toggled())
        assertEquals(ClamshellDockAnchor.LEFT, ClamshellDockAnchor.RIGHT.toggled())
        assertEquals(ClamshellDockAnchor.LEFT, ClamshellDockAnchor.CENTER.toggled())
    }

    @Test
    fun testTotalKeyboardHeight() {
        val spec = ClamshellLayoutSpec(
            maxHeightRatio = 0.40f,
            keyHeightFloorDp = 40.dp,
            keyHeightCeilingDp = 60.dp,
            rowSpacingDp = 4.dp
        )

        val totalHeight = spec.calculateTotalKeyboardHeight(500.dp, rowCount = 4)
        // 500 * 0.40 = 200dp max. (200 - 12) / 4 = 47dp per row.
        // Total = (47 * 4) + 12 = 200dp
        assertEquals(200.dp, totalHeight)
    }
}
