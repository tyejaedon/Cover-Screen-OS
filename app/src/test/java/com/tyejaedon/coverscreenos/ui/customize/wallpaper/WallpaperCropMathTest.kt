package com.tyejaedon.coverscreenos.ui.customize.wallpaper

import com.tyejaedon.coverscreenos.datastore.WallpaperScaleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperCropMathTest {

    @Test
    fun panTracksZoomedHorizontalOverflowAndClampsToBounds() {
        // Square 1000x1000 image in 500x500 container at 2x zoom => scaledWidth = 1000, maxPanX = 250
        val (x1, y1) = computeNextWallpaperCrop(
            currentCropX = 0f,
            currentCropY = 0f,
            panDeltaX = 125f,
            panDeltaY = -125f,
            containerWidthPx = 500f,
            containerHeightPx = 500f,
            sourceWidth = 1000,
            sourceHeight = 1000,
            scaleMode = WallpaperScaleMode.CROP,
            zoom = 2f,
            rotation = 0
        )
        assertEquals(0.5f, x1, 0.001f)
        assertEquals(-0.5f, y1, 0.001f)

        val (clampedX, clampedY) = computeNextWallpaperCrop(
            currentCropX = x1,
            currentCropY = y1,
            panDeltaX = 500f,
            panDeltaY = -500f,
            containerWidthPx = 500f,
            containerHeightPx = 500f,
            sourceWidth = 1000,
            sourceHeight = 1000,
            scaleMode = WallpaperScaleMode.CROP,
            zoom = 2f,
            rotation = 0
        )
        assertEquals(1f, clampedX, 0.001f)
        assertEquals(-1f, clampedY, 0.001f)
    }

    @Test
    fun fitModeWithNoOverflowKeepsCropCentered() {
        val (x, y) = computeNextWallpaperCrop(
            currentCropX = 0f,
            currentCropY = 0f,
            panDeltaX = 80f,
            panDeltaY = 80f,
            containerWidthPx = 500f,
            containerHeightPx = 500f,
            sourceWidth = 1000,
            sourceHeight = 1000,
            scaleMode = WallpaperScaleMode.FIT,
            zoom = 1f,
            rotation = 0
        )
        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
    }

    @Test
    fun rotationSwapsSourceDimensionsBeforeComputingPan() {
        // 2000x1000 rotated 90 deg becomes 1000x2000 in 500x500 container at 1x CROP:
        // baseScale = max(0.5, 0.25) = 0.5 => scaledWidth = 500 (no X pan), scaledHeight = 1000 (maxPanY = 250)
        val (x, y) = computeNextWallpaperCrop(
            currentCropX = 0f,
            currentCropY = 0f,
            panDeltaX = 50f,
            panDeltaY = 125f,
            containerWidthPx = 500f,
            containerHeightPx = 500f,
            sourceWidth = 2000,
            sourceHeight = 1000,
            scaleMode = WallpaperScaleMode.CROP,
            zoom = 1f,
            rotation = 90
        )
        assertEquals(0f, x, 0.001f)
        assertTrue(y > 0.49f && y < 0.51f)
    }
}
