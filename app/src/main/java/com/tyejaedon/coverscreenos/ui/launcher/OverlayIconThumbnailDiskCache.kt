package com.tyejaedon.coverscreenos.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.max
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable

private const val ICON_THUMBNAIL_CACHE_LOG_TAG = "CoverIconThumbCache"
private const val ICON_THUMBNAIL_CACHE_DIR_NAME = "cover_icon_thumbs"
private const val ICON_THUMBNAIL_EDGE_PX = 128
private const val ICON_THUMBNAIL_CACHE_MAX_BYTES = 36L * 1024L * 1024L

/**
 * Disk-backed PNG thumbnail cache for app launcher icons under `cacheDir/cover_icon_thumbs`.
 *
 * Complements the in-memory `LruCache` (`OverlayBitmapIconCache`) by persisting pre-rasterized
 * `128x128` px square bitmaps keyed by package name and app version token, avoiding expensive
 * `PackageManager` adaptive-icon loading across overlay restarts while staying within a 36 MiB cap.
 */
internal object OverlayIconThumbnailDiskCache {
    /**
     * Decodes a cached icon [Bitmap] from disk via [android.graphics.BitmapFactory.decodeFile],
     * touching `lastModified` on hit to preserve approximate LRU eviction order.
     */
    fun getBitmap(context: Context, packageName: String, versionToken: String?): Bitmap? {
        val cacheFile = cacheFileFor(
            context = context,
            packageName = packageName,
            versionToken = versionToken
        )
        if (!cacheFile.exists() || !cacheFile.isFile) return null

        return runCatching {
            val bitmap = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath) ?: return null
            // Refresh mtime so frequently accessed icons survive age-based disk trimming.
            cacheFile.setLastModified(System.currentTimeMillis())
            bitmap
        }.onFailure { error ->
            Log.w(
                ICON_THUMBNAIL_CACHE_LOG_TAG,
                "Icon disk cache read failed package=$packageName error=${error.message}"
            )
        }.getOrNull()
    }

    /**
     * Convenience wrapper around [getBitmap] that wraps the decoded bitmap in a [Drawable].
     */
    fun get(context: Context, packageName: String, versionToken: String?): Drawable? {
        return getBitmap(context, packageName, versionToken)?.toDrawable(context.resources)
    }

    /**
     * Rasterizes [drawable] into a square `128x128` px bitmap, writes it losslessly with
     * [Bitmap.CompressFormat.PNG], and trims older cache files if total usage exceeds the budget.
     */
    fun put(context: Context, packageName: String, versionToken: String?, drawable: Drawable) {
        val cacheFile = cacheFileFor(
            context = context,
            packageName = packageName,
            versionToken = versionToken
        )

        runCatching {
            val cacheDir = cacheFile.parentFile ?: return@runCatching
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val bitmap = drawable.toSquareBitmap(ICON_THUMBNAIL_EDGE_PX)
            FileOutputStream(cacheFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.flush()
            }
            cacheFile.setLastModified(System.currentTimeMillis())
            trimToBudget(cacheDir)
        }.onFailure { error ->
            Log.w(
                ICON_THUMBNAIL_CACHE_LOG_TAG,
                "Icon disk cache write failed package=$packageName error=${error.message}"
            )
        }
    }

    /**
     * Builds a deterministic SHA-256 filename from `packageName|versionToken` so app updates
     * automatically miss stale icon thumbnails.
     */
    private fun cacheFileFor(context: Context, packageName: String, versionToken: String?): File {
        val cacheRoot = File(context.cacheDir, ICON_THUMBNAIL_CACHE_DIR_NAME)
        val safeVersionToken = versionToken?.takeUnless { it.isBlank() } ?: "unknown"
        val digestInput = "$packageName|$safeVersionToken"
        val hashedName = digestInput.sha256Hex()
        return File(cacheRoot, "$hashedName.png")
    }

    /**
     * Deletes the oldest cached files by `lastModified` timestamp until the cache directory
     * drops within [ICON_THUMBNAIL_CACHE_MAX_BYTES].
     */
    private fun trimToBudget(cacheDir: File) {
        val files = cacheDir.listFiles { file -> file.isFile }?.toList().orEmpty()
        var totalBytes = files.sumOf { file -> file.length() }
        if (totalBytes <= ICON_THUMBNAIL_CACHE_MAX_BYTES) return

        val byAge = files.sortedBy { file -> file.lastModified() }
        byAge.forEach { file ->
            if (totalBytes <= ICON_THUMBNAIL_CACHE_MAX_BYTES) return
            val length = file.length()
            if (file.delete()) {
                totalBytes -= length
            }
        }
    }

    /** Computes a lowercase SHA-256 hex digest used as a filesystem-safe cache key. */
    private fun String.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                append(((byte.toInt() and 0xFF) ushr 4).toString(16))
                append((byte.toInt() and 0x0F).toString(16))
            }
        }
    }

    /**
     * Renders any [Drawable] centered and aspect-fitted onto an [edgePx] square [Bitmap],
     * reusing an existing [BitmapDrawable] bitmap directly when dimensions already match.
     */
    private fun Drawable.toSquareBitmap(edgePx: Int): Bitmap {
        if (this is BitmapDrawable) {
            val existing = bitmap
            if (existing != null && !existing.isRecycled && existing.width == edgePx && existing.height == edgePx) {
                return existing
            }
        }

        val targetBitmap = createBitmap(edgePx, edgePx)
        val canvas = Canvas(targetBitmap)

        val sourceWidth = max(intrinsicWidth, 1)
        val sourceHeight = max(intrinsicHeight, 1)
        val scale = minOf(edgePx.toFloat() / sourceWidth, edgePx.toFloat() / sourceHeight)
        val drawWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
        val drawHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)
        val left = (edgePx - drawWidth) / 2
        val top = (edgePx - drawHeight) / 2

        // Temporarily mutate bounds to center-fit on the target canvas, then restore original bounds.
        val previousBounds = bounds
        setBounds(left, top, left + drawWidth, top + drawHeight)
        draw(canvas)
        setBounds(previousBounds.left, previousBounds.top, previousBounds.right, previousBounds.bottom)

        return targetBitmap
    }
}



