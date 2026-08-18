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

internal object OverlayIconThumbnailDiskCache {
    fun get(context: Context, packageName: String, versionToken: String?): Drawable? {
        val cacheFile = cacheFileFor(
            context = context,
            packageName = packageName,
            versionToken = versionToken
        )
        if (!cacheFile.exists() || !cacheFile.isFile) return null

        return runCatching {
            val bitmap = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath) ?: return null
            cacheFile.setLastModified(System.currentTimeMillis())
            bitmap.toDrawable(context.resources)
        }.onFailure { error ->
            Log.w(
                ICON_THUMBNAIL_CACHE_LOG_TAG,
                "Icon disk cache read failed package=$packageName error=${error.message}"
            )
        }.getOrNull()
    }

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

    private fun cacheFileFor(context: Context, packageName: String, versionToken: String?): File {
        val cacheRoot = File(context.cacheDir, ICON_THUMBNAIL_CACHE_DIR_NAME)
        val safeVersionToken = versionToken?.takeUnless { it.isBlank() } ?: "unknown"
        val digestInput = "$packageName|$safeVersionToken"
        val hashedName = digestInput.sha256Hex()
        return File(cacheRoot, "$hashedName.png")
    }

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

    private fun String.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                append(((byte.toInt() and 0xFF) ushr 4).toString(16))
                append((byte.toInt() and 0x0F).toString(16))
            }
        }
    }

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

        val previousBounds = bounds
        setBounds(left, top, left + drawWidth, top + drawHeight)
        draw(canvas)
        setBounds(previousBounds.left, previousBounds.top, previousBounds.right, previousBounds.bottom)

        return targetBitmap
    }
}



