package com.tyejaedon.coverscreenos.ui

import androidx.compose.ui.unit.dp
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
}

