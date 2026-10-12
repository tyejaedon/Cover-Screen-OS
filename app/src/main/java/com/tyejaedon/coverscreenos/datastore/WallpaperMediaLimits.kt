package com.tyejaedon.coverscreenos.datastore

import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class WallpaperMediaType {
    IMAGE,
    GIF,
    VIDEO
}

object WallpaperMediaLimits {
    const val MAX_FILE_BYTES = 64L * 1024L * 1024L
    const val MAX_WIDTH = 8192
    const val MAX_HEIGHT = 8192
    const val MAX_IMAGE_PIXELS = 40_000_000L
    const val MAX_ANIMATED_WIDTH = 2048
    const val MAX_ANIMATED_HEIGHT = 2048
    const val MAX_ANIMATED_PIXELS = 4_194_304L
    const val MAX_DURATION_MS = 10_000L
    const val MAX_FRAME_RATE = 30
    const val MAX_FRAME_COUNT = 300
    const val MIN_FRAME_DELAY_MS = 34
    private val mp4Brands = setOf("isom", "iso2", "iso5", "iso6", "mp41", "mp42", "avc1", "dash", "M4V ")
    private val supportedVideoCodecs = setOf("video/avc", "video/hevc", "video/vp9")

    fun detectMediaType(prefix: ByteArray): WallpaperMediaType {
        if (prefix.size >= 6) {
            val header = prefix.copyOfRange(0, 6).decodeToString()
            if (header == "GIF87a" || header == "GIF89a") return WallpaperMediaType.GIF
        }
        if (prefix.size >= 8 && prefix.copyOfRange(4, 8).decodeToString() == "ftyp") {
            require(prefix.size >= 12 && prefix.copyOfRange(8, 12).decodeToString() in mp4Brands) {
                "Video container is unsupported. Choose MP4 or WebM."
            }
            return WallpaperMediaType.VIDEO
        }
        if (prefix.size >= 4 && prefix[0] == 0x1A.toByte() && prefix[1] == 0x45.toByte() &&
            prefix[2] == 0xDF.toByte() && prefix[3] == 0xA3.toByte()) {
            require(prefix.decodeToString().contains("webm")) {
                "Video container is unsupported. Choose MP4 or WebM."
            }
            return WallpaperMediaType.VIDEO
        }
        return WallpaperMediaType.IMAGE
    }

    fun hasValidDimensions(width: Int, height: Int, animated: Boolean): Boolean {
        if (width !in 1..if (animated) MAX_ANIMATED_WIDTH else MAX_WIDTH) return false
        if (height !in 1..if (animated) MAX_ANIMATED_HEIGHT else MAX_HEIGHT) return false
        val maxPixels = if (animated) MAX_ANIMATED_PIXELS else MAX_IMAGE_PIXELS
        return width.toLong() * height.toLong() <= maxPixels
    }

    fun validateFrameCount(frameCount: Int) {
        require(frameCount in 1..MAX_FRAME_COUNT) { "Animation has more than $MAX_FRAME_COUNT frames" }
    }

    fun validateVideoDuration(durationMs: Long) {
        require(durationMs in 1..MAX_DURATION_MS) {
            "Video must be ${MAX_DURATION_MS / 1000} seconds or shorter."
        }
    }

    fun validateVideoFrameRate(frameRate: Int) {
        require(frameRate in 1..MAX_FRAME_RATE) {
            "Video frame rate must be $MAX_FRAME_RATE fps or lower."
        }
    }

    fun validateVideoCodec(codec: String?) {
        require(codec in supportedVideoCodecs) {
            "Supported video codecs are H.264/AVC, HEVC, and VP9."
        }
    }

    suspend fun copyBounded(input: InputStream, output: OutputStream, maximumBytes: Long = MAX_FILE_BYTES): Long {
        require(maximumBytes > 0L) { "Maximum byte count must be positive" }
        val coroutineContext = currentCoroutineContext()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            coroutineContext.ensureActive()
            val count = input.read(buffer)
            coroutineContext.ensureActive()
            if (count < 0) break
            if (count == 0) {
                val next = input.read()
                if (next < 0) break
                if (total >= maximumBytes) {
                    throw IllegalArgumentException("Media exceeds the $maximumBytes byte limit")
                }
                output.write(next)
                total++
                continue
            }
            if (count.toLong() > maximumBytes - total) {
                throw IllegalArgumentException("Media exceeds the $maximumBytes byte limit")
            }
            output.write(buffer, 0, count)
            total += count
        }
        require(total > 0L) { "Media file is empty" }
        return total
    }

    fun validateGif(input: InputStream): GifMetadata {
        val reader = GifReader(input)
        val signature = reader.readBytes(6)
        require(signature.contentEquals("GIF87a".toByteArray()) ||
            signature.contentEquals("GIF89a".toByteArray())) { "File is not a supported GIF animation" }
        val width = reader.readUnsignedShort()
        val height = reader.readUnsignedShort()
        require(hasValidDimensions(width, height, animated = true)) {
            "GIF dimensions exceed ${MAX_ANIMATED_WIDTH}x$MAX_ANIMATED_HEIGHT or $MAX_ANIMATED_PIXELS pixels"
        }
        val packed = reader.readByte()
        reader.skipBytes(2)
        if (packed and 0x80 != 0) reader.skipBytes(3 * (1 shl ((packed and 0x07) + 1)))

        var pendingDelayMs = MIN_FRAME_DELAY_MS
        var frameCount = 0
        var durationMs = 0L
        while (true) {
            when (reader.readByte()) {
                0x3B -> break
                0x21 -> {
                    when (reader.readByte()) {
                        0xF9 -> {
                            require(reader.readByte() == 4) { "GIF contains a malformed frame control block" }
                            reader.readByte()
                            pendingDelayMs = reader.readUnsignedShort() * 10
                            reader.readByte()
                            require(reader.readByte() == 0) { "GIF contains a malformed frame control block" }
                        }
                        else -> reader.skipSubBlocks()
                    }
                }
                0x2C -> {
                    val left = reader.readUnsignedShort()
                    val top = reader.readUnsignedShort()
                    val frameWidth = reader.readUnsignedShort()
                    val frameHeight = reader.readUnsignedShort()
                    require(left.toLong() + frameWidth <= width && top.toLong() + frameHeight <= height &&
                        frameWidth > 0 && frameHeight > 0) { "GIF contains an invalid frame" }
                    val imagePacked = reader.readByte()
                    if (imagePacked and 0x80 != 0) reader.skipBytes(3 * (1 shl ((imagePacked and 0x07) + 1)))
                    reader.readByte()
                    reader.skipSubBlocks()
                    frameCount++
                    require(frameCount <= MAX_FRAME_COUNT) { "GIF has more than $MAX_FRAME_COUNT frames" }
                    require(pendingDelayMs >= MIN_FRAME_DELAY_MS) {
                        "GIF frame rate exceeds $MAX_FRAME_RATE frames per second"
                    }
                    durationMs += pendingDelayMs
                    pendingDelayMs = MIN_FRAME_DELAY_MS
                    require(durationMs <= MAX_DURATION_MS) { "GIF is longer than ${MAX_DURATION_MS / 1000} seconds" }
                }
                else -> throw IllegalArgumentException("GIF contains malformed animation data")
            }
        }
        require(frameCount > 1) { "Choose an animated GIF with at least two frames" }
        return GifMetadata(width, height, frameCount, durationMs)
    }

    data class GifMetadata(val width: Int, val height: Int, val frameCount: Int, val durationMs: Long)
}

private class GifReader(private val input: InputStream) {
    fun readByte(): Int = input.read().takeIf { it >= 0 }
        ?: throw IllegalArgumentException("GIF file is truncated")

    fun readBytes(count: Int): ByteArray {
        val bytes = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(bytes, offset, count - offset)
            if (read <= 0) throw IllegalArgumentException("GIF file is truncated")
            offset += read
        }
        return bytes
    }

    fun readUnsignedShort(): Int = readByte() or (readByte() shl 8)

    fun skipBytes(count: Int) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining.toLong()).toInt()
            if (skipped > 0) {
                remaining -= skipped
            } else {
                readByte()
                remaining--
            }
        }
    }

    fun skipSubBlocks() {
        while (true) {
            val length = readByte()
            if (length == 0) return
            skipBytes(length)
        }
    }
}
