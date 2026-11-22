package com.tyejaedon.coverscreenos.ui

import android.graphics.Bitmap
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayMediaPanelTest {
    @Test
    fun `artwork supports small images and rejects recycled images`() {
        assertNull(usableMediaArtwork(null))
        val artwork = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        assertSame(artwork, usableMediaArtwork(artwork))
        artwork.recycle()
        assertNull(usableMediaArtwork(artwork))
    }

    @Test
    fun `expanded layout uses local height and keeps accessible controls`() {
        assertEquals(0.72f, expandedPlayerLayoutSpec(340.dp).panelHeightFraction)
        assertEquals(0.78f, expandedPlayerLayoutSpec(341.dp).panelHeightFraction)
        assertEquals(0.78f, expandedPlayerLayoutSpec(420.dp).panelHeightFraction)
        assertEquals(0.84f, expandedPlayerLayoutSpec(421.dp).panelHeightFraction)
        listOf(240.dp, 340.dp, 420.dp, 780.dp).forEach { height ->
            val layout = expandedPlayerLayoutSpec(height)
            assertTrue(layout.sideControlSize >= 48.dp)
            assertTrue(layout.primaryControlSize >= 48.dp)
        }
    }
}
