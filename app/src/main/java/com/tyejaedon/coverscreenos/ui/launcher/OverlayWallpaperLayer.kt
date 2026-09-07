package com.tyejaedon.coverscreenos.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.net.toUri
import com.tyejaedon.coverscreenos.datastore.WallpaperScaleMode
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

private const val WALLPAPER_LOG_TAG = "CoverWallpaper"
private const val WALLPAPER_RETRY_BACKOFF_MS = 4_000L
private const val WALLPAPER_OVERLAY_RETRY_DELAY_MS = 600L
private const val WALLPAPER_OVERLAY_RETRY_MAX_ATTEMPTS = 6

private object WallpaperDecodeBackoff {
    private val failedAtElapsedMsByKey = mutableMapOf<String, Long>()

    fun shouldSkip(cacheKey: String, nowElapsedMs: Long): Boolean {
        val failedAt = synchronized(failedAtElapsedMsByKey) { failedAtElapsedMsByKey[cacheKey] } ?: return false
        return (nowElapsedMs - failedAt) < WALLPAPER_RETRY_BACKOFF_MS
    }

    fun markFailure(cacheKey: String, nowElapsedMs: Long) {
        synchronized(failedAtElapsedMsByKey) { failedAtElapsedMsByKey[cacheKey] = nowElapsedMs }
    }

    fun clearFailure(cacheKey: String) {
        synchronized(failedAtElapsedMsByKey) { failedAtElapsedMsByKey.remove(cacheKey) }
    }
}

@Composable
internal fun CoverWallpaperLayer(
    wallpaperUri: String?,
    wallpaperScaleMode: WallpaperScaleMode,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val containerSize = LocalWindowInfo.current.containerSize
    val normalizedWallpaperUri = wallpaperUri?.trim().takeUnless { it.isNullOrEmpty() }
    var decodeRetryAttempt by remember(normalizedWallpaperUri) { mutableIntStateOf(0) }
    var lastGoodWallpaperBitmap by remember(normalizedWallpaperUri) { mutableStateOf<Bitmap?>(null) }
    val requestedWidthPx = remember(containerSize.width) { max(containerSize.width, 1) }
    val requestedHeightPx = remember(containerSize.height) { max(containerSize.height, 1) }
    val wallpaperCacheVersionToken = remember(normalizedWallpaperUri, decodeRetryAttempt) {
        normalizedWallpaperUri
            ?.takeUnless { it.isBlank() }
            ?.let { runCatching { resolveWallpaperCacheVersionToken(it.toUri()) }.getOrNull() }
    }

    val wallpaperBitmap by produceState<Bitmap?>(
        initialValue = lastGoodWallpaperBitmap,
        normalizedWallpaperUri,
        wallpaperCacheVersionToken,
        decodeRetryAttempt,
        context,
        requestedWidthPx,
        requestedHeightPx
    ) {
        value = if (normalizedWallpaperUri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    decodeSampledBitmapFromUri(
                        context = context,
                        uri = normalizedWallpaperUri.toUri(),
                        cacheVersionToken = wallpaperCacheVersionToken,
                        requestedWidthPx = requestedWidthPx,
                        requestedHeightPx = requestedHeightPx
                    )
                }.onFailure { error ->
                    Log.w(WALLPAPER_LOG_TAG, "Overlay wallpaper decode failed error=${error.message}")
                }.getOrNull()
            }
        }
    }

    LaunchedEffect(normalizedWallpaperUri, wallpaperBitmap) {
        if (normalizedWallpaperUri.isNullOrBlank()) {
            lastGoodWallpaperBitmap = null
            return@LaunchedEffect
        }
        if (wallpaperBitmap != null) {
            lastGoodWallpaperBitmap = wallpaperBitmap
            decodeRetryAttempt = 0
        }
    }

    LaunchedEffect(normalizedWallpaperUri, wallpaperBitmap, decodeRetryAttempt) {
        if (normalizedWallpaperUri.isNullOrBlank()) return@LaunchedEffect
        if (wallpaperBitmap != null) return@LaunchedEffect
        if (decodeRetryAttempt >= WALLPAPER_OVERLAY_RETRY_MAX_ATTEMPTS) return@LaunchedEffect

        delay(WALLPAPER_OVERLAY_RETRY_DELAY_MS.milliseconds)
        decodeRetryAttempt += 1
    }

    val bitmap = wallpaperBitmap ?: lastGoodWallpaperBitmap
    if (bitmap == null) {
        Box(
            modifier = modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        com.tyejaedon.coverscreenos.ui.theme.CoverOSDarkSurfaceVariant,
                        com.tyejaedon.coverscreenos.ui.theme.CoverOSDarkBackground
                    )
                )
            )
        )
    } else {
        Image(
            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
            contentDescription = "Cover Wallpaper",
            contentScale = if (wallpaperScaleMode == WallpaperScaleMode.CROP) ContentScale.Crop else ContentScale.Fit,
            modifier = modifier
        )
    }
}

private fun decodeSampledBitmapFromUri(
    context: Context,
    uri: Uri,
    cacheVersionToken: String? = null,
    requestedWidthPx: Int,
    requestedHeightPx: Int
): Bitmap? {
    val cacheKey = WallpaperBitmapCache.buildKey(
        uri = uri.toString(),
        versionToken = cacheVersionToken ?: resolveWallpaperCacheVersionToken(uri),
        requestedWidthPx = requestedWidthPx,
        requestedHeightPx = requestedHeightPx
    )

    WallpaperBitmapCache.get(cacheKey)?.let { return it }

    val nowElapsedMs = SystemClock.elapsedRealtime()
    if (WallpaperDecodeBackoff.shouldSkip(cacheKey, nowElapsedMs)) return null

    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openWallpaperInputStream(context, uri)?.use { boundsStream ->
        BitmapFactory.decodeStream(boundsStream, null, boundsOptions)
    } ?: run {
        return decodeWallpaperBitmapWithImageDecoder(
            context = context,
            uri = uri,
            cacheKey = cacheKey,
            requestedWidthPx = requestedWidthPx,
            requestedHeightPx = requestedHeightPx,
            nowElapsedMs = nowElapsedMs
        )
    }

    if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
        return decodeWallpaperBitmapWithImageDecoder(
            context = context,
            uri = uri,
            cacheKey = cacheKey,
            requestedWidthPx = requestedWidthPx,
            requestedHeightPx = requestedHeightPx,
            nowElapsedMs = nowElapsedMs
        )
    }

    val sampledOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, requestedWidthPx, requestedHeightPx)
        inPreferredConfig = Bitmap.Config.RGB_565
    }

    val decodedBitmap = openWallpaperInputStream(context, uri)?.use { decodeStream ->
        BitmapFactory.decodeStream(decodeStream, null, sampledOptions)
    } ?: decodeWallpaperBitmapWithImageDecoder(
        context = context,
        uri = uri,
        cacheKey = cacheKey,
        requestedWidthPx = requestedWidthPx,
        requestedHeightPx = requestedHeightPx,
        nowElapsedMs = nowElapsedMs
    )

    if (decodedBitmap != null) {
        WallpaperBitmapCache.put(cacheKey, decodedBitmap)
        WallpaperDecodeBackoff.clearFailure(cacheKey)
    } else {
        return decodeWallpaperBitmapWithImageDecoder(
            context = context,
            uri = uri,
            cacheKey = cacheKey,
            requestedWidthPx = requestedWidthPx,
            requestedHeightPx = requestedHeightPx,
            nowElapsedMs = nowElapsedMs
        )
    }

    return decodedBitmap
}

private fun decodeWallpaperBitmapWithImageDecoder(
    context: Context,
    uri: Uri,
    cacheKey: String,
    requestedWidthPx: Int,
    requestedHeightPx: Int,
    nowElapsedMs: Long
): Bitmap? {
    val source = when (uri.scheme) {
        "file" -> {
            val path = uri.path ?: run {
                WallpaperDecodeBackoff.markFailure(cacheKey, nowElapsedMs)
                return null
            }
            val file = File(path)
            if (!file.exists() || !file.isFile || !file.canRead()) {
                WallpaperDecodeBackoff.markFailure(cacheKey, nowElapsedMs)
                return null
            }
            ImageDecoder.createSource(file)
        }
        else -> runCatching { ImageDecoder.createSource(context.contentResolver, uri) }
            .getOrNull()
            ?: run {
                WallpaperDecodeBackoff.markFailure(cacheKey, nowElapsedMs)
                return null
            }
    }

    val decodedBitmap = runCatching {
        ImageDecoder.decodeBitmap(source) { decoder, imageInfo, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val sampleSize = calculateImageDecoderSampleSize(
                sourceWidth = imageInfo.size.width,
                sourceHeight = imageInfo.size.height,
                requestedWidthPx = requestedWidthPx,
                requestedHeightPx = requestedHeightPx
            )
            if (sampleSize > 1) {
                decoder.setTargetSampleSize(sampleSize)
            }
        }
    }.onFailure { error ->
        Log.w(WALLPAPER_LOG_TAG, "ImageDecoder wallpaper decode failed uri=$uri error=${error.message}")
    }.getOrNull()

    if (decodedBitmap != null) {
        WallpaperBitmapCache.put(cacheKey, decodedBitmap)
        WallpaperDecodeBackoff.clearFailure(cacheKey)
    } else {
        WallpaperDecodeBackoff.markFailure(cacheKey, nowElapsedMs)
    }

    return decodedBitmap
}

private fun resolveWallpaperCacheVersionToken(uri: Uri): String? {
    if (uri.scheme != "file") return null
    val path = uri.path ?: return null
    val file = File(path)
    if (!file.exists() || !file.isFile) return null
    return "${file.lastModified()}_${file.length()}"
}

private fun openWallpaperInputStream(context: Context, uri: Uri): InputStream? {
    return when (uri.scheme) {
        "file" -> uri.path?.let { path -> runCatching { File(path).inputStream() }.getOrNull() }
        else -> runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            ?: runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")
                    ?.let { descriptor ->
                        ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                    }
            }.getOrNull()
    }
}

private fun calculateImageDecoderSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    requestedWidthPx: Int,
    requestedHeightPx: Int
): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0) return 1
    val widthRatio = sourceWidth.toFloat() / requestedWidthPx.coerceAtLeast(1)
    val heightRatio = sourceHeight.toFloat() / requestedHeightPx.coerceAtLeast(1)
    return max(widthRatio, heightRatio).toInt().coerceAtLeast(1)
}

private fun calculateInSampleSize(
    outWidth: Int,
    outHeight: Int,
    requestedWidthPx: Int,
    requestedHeightPx: Int
): Int {
    if (outWidth <= 0 || outHeight <= 0) return 1
    var inSampleSize = 1
    val halfWidth = outWidth / 2
    val halfHeight = outHeight / 2

    while ((halfWidth / inSampleSize) >= requestedWidthPx && (halfHeight / inSampleSize) >= requestedHeightPx) {
        inSampleSize *= 2
    }
    return inSampleSize.coerceAtLeast(1)
}

