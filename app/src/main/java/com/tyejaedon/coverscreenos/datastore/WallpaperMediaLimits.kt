package com.tyejaedon.coverscreenos.datastore

import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Supported media asset categories for custom cover-screen wallpapers. */
enum class WallpaperMediaType {
    /** Static bitmap image (PNG, JPEG, WebP, HEIF, etc.). */
    IMAGE,
    /** Multi-frame animated GIF (`GIF87a` / `GIF89a`). */
    GIF,
    /** Looping video clip (MP4 or WebM). */
    VIDEO
}

/**
 * Pure validation rules, file-signature sniffer, and binary GIF parser for cover wallpapers.
 *
 * Keeps wallpaper imports bounded in file size, resolution, pixel count, frame rate, and
 * duration so decoding inside the cover overlay service remains fast and memory-safe.
 */
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
    /** Minimum per-frame delay (~33.3 ms for 30 fps, rounded up to 34 ms). */
    const val MIN_FRAME_DELAY_MS = 34
    private val mp4Brands = setOf("isom", "iso2", "iso5", "iso6", "mp41", "mp42", "avc1", "dash", "M4V ")
    private val supportedVideoCodecs = setOf("video/avc", "video/hevc", "video/vp9")

    /**
     * Classifies media from its leading file header bytes rather than trusting URI extensions.
     *
     * Checks GIF (`GIF87a`/`GIF89a`), ISO BMFF `ftyp` MP4 boxes, and Matroska/WebM EBML headers
     * (`0x1A 0x45 0xDF 0xA3`), falling back to [WallpaperMediaType.IMAGE] for bitmap decoders.
     *
     * @throws IllegalArgumentException if an MP4/EBML header uses an unsupported video container brand.
     */
    fun detectMediaType(prefix: ByteArray): WallpaperMediaType {
        if (prefix.size >= 6) {
            val header = prefix.copyOfRange(0, 6).decodeToString()
            if (header == "GIF87a" || header == "GIF89a") return WallpaperMediaType.GIF
        }
        // ISO Base Media File Format: bytes 4..7 hold box type "ftyp" and 8..11 hold major brand.
        if (prefix.size >= 8 && prefix.copyOfRange(4, 8).decodeToString() == "ftyp") {
            require(prefix.size >= 12 && prefix.copyOfRange(8, 12).decodeToString() in mp4Brands) {
                "Video container is unsupported. Choose MP4 or WebM."
            }
            return WallpaperMediaType.VIDEO
        }
        // EBML header magic (0x1A45DFA3) used by Matroska/WebM; require "webm" DocType string.
        if (prefix.size >= 4 && prefix[0] == 0x1A.toByte() && prefix[1] == 0x45.toByte() &&
            prefix[2] == 0xDF.toByte() && prefix[3] == 0xA3.toByte()) {
            require(prefix.decodeToString().contains("webm")) {
                "Video container is unsupported. Choose MP4 or WebM."
            }
            return WallpaperMediaType.VIDEO
        }
        return WallpaperMediaType.IMAGE
    }

    /** Returns true when dimensions are positive and within static or animated pixel caps. */
    fun hasValidDimensions(width: Int, height: Int, animated: Boolean): Boolean {
        if (width !in 1..if (animated) MAX_ANIMATED_WIDTH else MAX_WIDTH) return false
        if (height !in 1..if (animated) MAX_ANIMATED_HEIGHT else MAX_HEIGHT) return false
        val maxPixels = if (animated) MAX_ANIMATED_PIXELS else MAX_IMAGE_PIXELS
        return width.toLong() * height.toLong() <= maxPixels
    }

    /** Requires total decoded frame count to be in `1..MAX_FRAME_COUNT`. */
    fun validateFrameCount(frameCount: Int) {
        require(frameCount in 1..MAX_FRAME_COUNT) { "Animation has more than $MAX_FRAME_COUNT frames" }
    }

    /** Requires video duration in milliseconds to be positive and at most [MAX_DURATION_MS]. */
    fun validateVideoDuration(durationMs: Long) {
        require(durationMs in 1..MAX_DURATION_MS) {
            "Video must be ${MAX_DURATION_MS / 1000} seconds or shorter."
        }
    }

    /** Requires reported video frame rate to be positive and at most [MAX_FRAME_RATE] fps. */
    fun validateVideoFrameRate(frameRate: Int) {
        require(frameRate in 1..MAX_FRAME_RATE) {
            "Video frame rate must be $MAX_FRAME_RATE fps or lower."
        }
    }

    /** Requires the video track MIME type to be H.264/AVC, HEVC, or VP9. */
    fun validateVideoCodec(codec: String?) {
        require(codec in supportedVideoCodecs) {
            "Supported video codecs are H.264/AVC, HEVC, and VP9."
        }
    }

    /**
     * Copies [input] to [output] while enforcing [maximumBytes] and coroutine cancellation.
     *
     * Checks [kotlinx.coroutines.Job.ensureActive] around blocking stream reads and fails fast if
     * the stream yields 0 bytes or exceeds [maximumBytes].
     */
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

    /**
     * Parses a GIF87a/89a stream block-by-block without allocating frame pixel buffers.
     *
     * Validates canvas dimensions, color table sizes, Graphic Control Extension delays (`0x21 0xF9`),
     * Image Descriptor bounds (`0x2C`), frame count (`2..MAX_FRAME_COUNT`), minimum frame delay, and
     * total loop duration (`<= MAX_DURATION_MS`).
     */
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
        // Logical Screen Descriptor: packed byte (bit 7 = Global Color Table flag, bits 0..2 = table size).
        val packed = reader.readByte()
        reader.skipBytes(2)
        if (packed and 0x80 != 0) reader.skipBytes(3 * (1 shl ((packed and 0x07) + 1)))

        var pendingDelayMs = MIN_FRAME_DELAY_MS
        var frameCount = 0
        var durationMs = 0L
        while (true) {
            when (reader.readByte()) {
                0x3B -> break // GIF Trailer (end of stream)
                0x21 -> { // Extension introducer
                    when (reader.readByte()) {
                        0xF9 -> { // Graphic Control Extension (delay stored in 1/100ths of a second)
                            require(reader.readByte() == 4) { "GIF contains a malformed frame control block" }
                            reader.readByte()
                            pendingDelayMs = reader.readUnsignedShort() * 10
                            reader.readByte()
                            require(reader.readByte() == 0) { "GIF contains a malformed frame control block" }
                        }
                        else -> reader.skipSubBlocks()
                    }
                }
                0x2C -> { // Image Descriptor (per-frame bounds and LZW sub-blocks)
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

    /** Summary metadata extracted from a validated animated GIF stream. */
    data class GifMetadata(val width: Int, val height: Int, val frameCount: Int, val durationMs: Long)
}

/** Minimal little-endian binary reader for traversing GIF block headers and data sub-blocks. */
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

    /** Reads a 16-bit little-endian unsigned integer as used in GIF descriptors and frame delays. */
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

    /** Skips length-prefixed GIF data sub-blocks until reaching the `0x00` block terminator. */
    fun skipSubBlocks() {
        while (true) {
            val length = readByte()
            if (length == 0) return
            skipBytes(length)
        }
    }
}
