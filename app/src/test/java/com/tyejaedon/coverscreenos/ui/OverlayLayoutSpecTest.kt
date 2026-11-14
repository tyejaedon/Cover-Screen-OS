package com.tyejaedon.coverscreenos.ui

import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.ui.launcher.OverlayLayoutSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayLayoutSpecTest {

    @Test
    fun `overlay width preserves side margins on narrow screens`() {
        val width = OverlayLayoutSpec.overlayWidth(320.dp)

        assertEquals(288f, width.value, 0.001f)
    }

    @Test
    fun `overlay width is capped on large screens`() {
        val width = OverlayLayoutSpec.overlayWidth(900.dp)

        assertEquals(560f, width.value, 0.001f)
    }

    @Test
    fun `overlay width never goes negative`() {
        val width = OverlayLayoutSpec.overlayWidth(20.dp)

        assertEquals(0f, width.value, 0.001f)
    }

    @Test
    fun `layout constants provide safe defaults`() {
        assertTrue(OverlayLayoutSpec.horizontalMargin.value > 0f)
        assertTrue(OverlayLayoutSpec.maxOverlayWidth.value >= 320f)
        assertTrue(OverlayLayoutSpec.contentTopOffset.value >= 48f)
    }

    @Test
    fun `cover display polish spec preserves dynamic cutout padding`() {
        val specWithCutout = com.tyejaedon.coverscreenos.ui.launcher.CoverDisplayPolishSpec(
            statusChipMinHeight = 30.dp,
            dockBottomPadding = 10.dp + 16.dp,
            cutoutTopPadding = 24.dp,
            cutoutBottomPadding = 16.dp,
            cutoutStartPadding = 8.dp,
            cutoutEndPadding = 8.dp
        )

        assertEquals(24.dp, specWithCutout.cutoutTopPadding)
        assertEquals(16.dp, specWithCutout.cutoutBottomPadding)
        assertEquals(8.dp, specWithCutout.cutoutStartPadding)
        assertEquals(8.dp, specWithCutout.cutoutEndPadding)
        assertEquals(26.dp, specWithCutout.dockBottomPadding)
    }
}

