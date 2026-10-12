package com.tyejaedon.coverscreenos.ui

import android.graphics.Bitmap
import com.tyejaedon.coverscreenos.datastore.WallpaperMediaType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WallpaperVideoFrameTest {
    @Test
    fun `video rotation metadata swaps upright dimensions for quarter turns`() {
        assertEquals(1080 to 1920, uprightVideoDimensions(1920, 1080, 90))
        assertEquals(1080 to 1920, uprightVideoDimensions(1920, 1080, 270))
        assertEquals(1920 to 1080, uprightVideoDimensions(1920, 1080, 180))
        assertEquals(1920 to 1080, uprightVideoDimensions(1920, 1080, 0))
    }

    @Test
    fun `bitmap cache keeps source geometry rather than poster dimensions`() {
        val bitmap = Bitmap.createBitmap(80, 45, Bitmap.Config.ARGB_8888)
        val frame = WallpaperFrame(bitmap, WallpaperMediaType.VIDEO, 1080, 1920)
        val key = WallpaperBitmapCache.buildKey(
            "test-video",
            "video-cache-frame-test",
            requestedWidthPx = 480,
            requestedHeightPx = 854
        )

        WallpaperBitmapCache.put(key, frame)

        val cached = WallpaperBitmapCache.get(key)!!
        assertEquals(1080, cached.width)
        assertEquals(1920, cached.height)
        assertEquals(80, cached.bitmap.width)
        assertEquals(45, cached.bitmap.height)
    }
}
