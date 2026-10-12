package com.tyejaedon.coverscreenos.datastore

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperMediaLimitsTest {
    @Test
    fun `image and animated dimensions accept exact limits and reject values beyond`() {
        assertTrue(WallpaperMediaLimits.hasValidDimensions(8000, 5000, animated = false))
        assertTrue(WallpaperMediaLimits.hasValidDimensions(2048, 2048, animated = true))
        assertFalse(WallpaperMediaLimits.hasValidDimensions(8193, 1, animated = false))
        assertFalse(WallpaperMediaLimits.hasValidDimensions(8001, 5000, animated = false))
        assertFalse(WallpaperMediaLimits.hasValidDimensions(2049, 1, animated = true))
        assertFalse(WallpaperMediaLimits.hasValidDimensions(2048, 2049, animated = true))
    }

    @Test
    fun `bounded stream copy accepts exact byte limit and rejects the next byte`() = runBlocking {
        val accepted = ByteArrayOutputStream()
        assertEquals(
            5L,
            WallpaperMediaLimits.copyBounded(ByteArrayInputStream(ByteArray(5)), accepted, maximumBytes = 5)
        )
        assertEquals(5, accepted.size())
        try {
            WallpaperMediaLimits.copyBounded(
                ByteArrayInputStream(ByteArray(6)),
                ByteArrayOutputStream(),
                maximumBytes = 5
            )
            throw AssertionError("Copy accepted data beyond the byte limit")
        } catch (_: IllegalArgumentException) {
        }
        try {
            WallpaperMediaLimits.copyBounded(
                ByteArrayInputStream(ByteArray(0)),
                ByteArrayOutputStream(),
                maximumBytes = 5
            )
            throw AssertionError("Copy accepted an empty file")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `bounded stream copy propagates cancellation during copying`() = runBlocking {
        val childJob = Job()
        val copied = ByteArrayOutputStream()
        try {
            withContext(childJob) {
                val job = currentCoroutineContext()[Job] ?: error("Copy coroutine has no Job")
                val input = object : ByteArrayInputStream(ByteArray(16)) {
                    private var cancelled = false

                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (!cancelled) {
                            cancelled = true
                            val count = super.read(buffer, offset, minOf(length, 4))
                            job.cancel()
                            return count
                        }
                        return super.read(buffer, offset, length)
                    }
                }
                WallpaperMediaLimits.copyBounded(input, copied, maximumBytes = 32)
            }
            throw AssertionError("Copy continued after cancellation")
        } catch (_: CancellationException) {
            assertEquals(0, copied.size())
        } finally {
            childJob.cancel()
        }
    }

    @Test
    fun `media type is sniffed from supported container signatures`() {
        val mp4 = ByteArray(12).apply {
            "ftyp".toByteArray().copyInto(this, destinationOffset = 4)
            "isom".toByteArray().copyInto(this, destinationOffset = 8)
        }
        val webm = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()) + "webm".toByteArray()
        assertEquals(WallpaperMediaType.VIDEO, WallpaperMediaLimits.detectMediaType(mp4))
        assertEquals(WallpaperMediaType.VIDEO, WallpaperMediaLimits.detectMediaType(webm))
        assertEquals(WallpaperMediaType.GIF, WallpaperMediaLimits.detectMediaType("GIF89a".toByteArray()))
        assertEquals(WallpaperMediaType.IMAGE, WallpaperMediaLimits.detectMediaType("image".toByteArray()))
        val quickTime = mp4.copyOf().apply { "qt  ".toByteArray().copyInto(this, destinationOffset = 8) }
        assertThrows(IllegalArgumentException::class.java) {
            WallpaperMediaLimits.detectMediaType(quickTime)
        }
    }

    @Test
    fun `animation frame count accepts limit and rejects one beyond`() {
        WallpaperMediaLimits.validateFrameCount(WallpaperMediaLimits.MAX_FRAME_COUNT)
        assertThrows(IllegalArgumentException::class.java) {
            WallpaperMediaLimits.validateFrameCount(WallpaperMediaLimits.MAX_FRAME_COUNT + 1)
        }
    }

    @Test
    fun `video duration frame rate and codec checks enforce documented boundaries`() {
        WallpaperMediaLimits.validateVideoDuration(WallpaperMediaLimits.MAX_DURATION_MS)
        assertThrows(IllegalArgumentException::class.java) {
            WallpaperMediaLimits.validateVideoDuration(WallpaperMediaLimits.MAX_DURATION_MS + 1)
        }
        WallpaperMediaLimits.validateVideoFrameRate(WallpaperMediaLimits.MAX_FRAME_RATE)
        assertThrows(IllegalArgumentException::class.java) {
            WallpaperMediaLimits.validateVideoFrameRate(WallpaperMediaLimits.MAX_FRAME_RATE + 1)
        }
        listOf("video/avc", "video/hevc", "video/vp9").forEach(WallpaperMediaLimits::validateVideoCodec)
        assertThrows(IllegalArgumentException::class.java) {
            WallpaperMediaLimits.validateVideoCodec("video/av1")
        }
    }

    @Test
    fun `GIF duration boundary succeeds and the next frame is rejected`() {
        assertEquals(10_000L, WallpaperMediaLimits.validateGif(gif(frameCount = 100, delayHundredths = 10)).durationMs)
        assertThrows(IllegalArgumentException::class.java) {
            WallpaperMediaLimits.validateGif(gif(frameCount = 101, delayHundredths = 10))
        }
    }

    @Test
    fun `GIF frame rate and frame count limits reject fast and excessive animations`() {
        assertThrows(IllegalArgumentException::class.java) {
            WallpaperMediaLimits.validateGif(gif(frameCount = 2, delayHundredths = 3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WallpaperMediaLimits.validateGif(gif(frameCount = 301, delayHundredths = 4))
        }
    }

    @Test
    fun `GIF parser rejects malformed and truncated media`() {
        assertThrows(IllegalArgumentException::class.java) {
            WallpaperMediaLimits.validateGif(ByteArrayInputStream("GIF89a".toByteArray()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WallpaperMediaLimits.validateGif(ByteArrayInputStream("not a GIF".toByteArray()))
        }
    }

    private fun gif(frameCount: Int, delayHundredths: Int): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        bytes.write("GIF89a".toByteArray())
        writeShort(bytes, 2)
        writeShort(bytes, 2)
        bytes.write(byteArrayOf(0, 0, 0))
        repeat(frameCount) {
            bytes.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0))
            writeShort(bytes, delayHundredths)
            bytes.write(byteArrayOf(0, 0))
            bytes.write(0x2C)
            writeShort(bytes, 0)
            writeShort(bytes, 0)
            writeShort(bytes, 2)
            writeShort(bytes, 2)
            bytes.write(byteArrayOf(0, 2, 1, 0, 0))
        }
        bytes.write(0x3B)
        return ByteArrayInputStream(bytes.toByteArray())
    }

    private fun writeShort(output: ByteArrayOutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write(value ushr 8 and 0xFF)
    }
}
