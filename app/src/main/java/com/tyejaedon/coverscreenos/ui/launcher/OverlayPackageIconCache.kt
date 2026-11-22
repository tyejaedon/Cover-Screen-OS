package com.tyejaedon.coverscreenos.ui.launcher

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.createBitmap
import com.tyejaedon.coverscreenos.ui.OverlayIconThumbnailDiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ICON_CACHE_MAX_BYTES = 6L * 1024L * 1024L
private const val ICON_CACHE_SIZE_UNIT_BYTES = 1024
private const val ICON_BITMAP_EDGE_PX = 128

// Global LRU Bitmap Cache for hardware-accelerated Compose Image rendering
/**
 * Process-wide in-memory [LruCache] mapping app `packageName` keys to rasterized [ImageBitmap]
 * icons, bounded by [ICON_CACHE_MAX_BYTES] (6 MiB, tracked in 1 KiB units).
 *
 * Accesses are synchronized because background `Dispatchers.IO` resolvers populate the cache
 * concurrently while Compose UI threads perform synchronous cache lookups during composition.
 */
internal object OverlayBitmapIconCache {
    private val cache = object : LruCache<String, ImageBitmap>(
        (ICON_CACHE_MAX_BYTES / ICON_CACHE_SIZE_UNIT_BYTES).toInt()
    ) {
        // Approximate ARGB_8888 memory footprint (4 bytes per pixel) converted to KiB units.
        override fun sizeOf(key: String, bitmap: ImageBitmap): Int =
            (bitmap.width.toLong() * bitmap.height * 4 / ICON_CACHE_SIZE_UNIT_BYTES)
                .toInt()
                .coerceAtLeast(1)
    }

    /** Returns the cached icon for [packageName], or `null` if not currently resident in memory. */
    fun get(packageName: String): ImageBitmap? {
        return synchronized(cache) { cache.get(packageName) }
    }

    /** Caches the rasterized [bitmap] for [packageName] in memory. */
    fun put(packageName: String, bitmap: ImageBitmap) {
        synchronized(cache) { cache.put(packageName, bitmap) }
    }
}

/**
 * Rasterizes an arbitrary Android [Drawable] (such as an `AdaptiveIconDrawable`, vector, or
 * legacy bitmap drawable) into a centered, aspect-fit square [ImageBitmap] of [sizePx] pixels.
 *
 * Restores the drawable's original bounds in a `finally` block so shared system drawables are
 * not mutated for other callers.
 */
private fun drawableToImageBitmap(drawable: Drawable, sizePx: Int = ICON_BITMAP_EDGE_PX): ImageBitmap {
    val bitmap = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bitmap)
    val sourceWidth = drawable.intrinsicWidth.coerceAtLeast(1)
    val sourceHeight = drawable.intrinsicHeight.coerceAtLeast(1)
    val scale = minOf(sizePx.toFloat() / sourceWidth, sizePx.toFloat() / sourceHeight)
    val drawWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
    val drawHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)
    val left = (sizePx - drawWidth) / 2
    val top = (sizePx - drawHeight) / 2
    val previousBounds = android.graphics.Rect(drawable.bounds)
    try {
        // Render into centered, aspect-preserving bounds, then restore original bounds for shared drawables.
        drawable.setBounds(left, top, left + drawWidth, top + drawHeight)
        drawable.draw(canvas)
    } finally {
        drawable.bounds = previousBounds
    }
    return bitmap.asImageBitmap()
}

/**
 * Resolves an app's icon as an [ImageBitmap] using a two-tier cache:
 * 1. Synchronous in-memory lookup in [OverlayBitmapIconCache].
 * 2. Off-thread (`Dispatchers.IO`) check of `OverlayIconThumbnailDiskCache` keyed by a package
 *    version token, falling back to `PackageManager.getApplicationIcon` and rasterizing via
 *    [drawableToImageBitmap] on a disk miss.
 */
internal suspend fun resolvePackageIconBitmap(
    context: Context,
    packageManager: PackageManager,
    packageName: String
): ImageBitmap? {
    OverlayBitmapIconCache.get(packageName)?.let { return it }

    val resolved = withContext(Dispatchers.IO) {
        val versionToken = resolvePackageIconVersionToken(packageManager, packageName)
        OverlayIconThumbnailDiskCache.getBitmap(
            context = context,
            packageName = packageName,
            versionToken = versionToken
        )?.let { cachedBitmap ->
            return@withContext cachedBitmap.asImageBitmap()
        }

        runCatching {
            val drawable = packageManager.getApplicationIcon(packageName)
            OverlayIconThumbnailDiskCache.put(
                context = context,
                packageName = packageName,
                versionToken = versionToken,
                drawable = drawable
            )
            drawableToImageBitmap(drawable)
        }.getOrNull()
    } ?: return null

    OverlayBitmapIconCache.put(packageName, resolved)
    return resolved
}

/**
 * Composable helper that returns the [ImageBitmap] for [packageName], seeded synchronously from
 * [OverlayBitmapIconCache] to avoid empty placeholder flashes and resolved via
 * [resolvePackageIconBitmap] on cache misses.
 */
@Composable
internal fun rememberPackageIconBitmap(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    val packageManager = remember(context) { context.packageManager }

    val iconState = produceState<ImageBitmap?>(
        initialValue = OverlayBitmapIconCache.get(packageName),
        key1 = packageName
    ) {
        if (value == null) {
            val resolved = resolvePackageIconBitmap(context, packageManager, packageName)
            if (resolved != null) value = resolved
        }
    }
    return iconState.value
}

/**
 * Composable helper that resolves the user-visible app name for [packageName] on
 * `Dispatchers.IO` via `PackageManager.getApplicationLabel`, initially displaying the package's
 * trailing segment while loading.
 */
@Composable
internal fun rememberPackageLabel(packageName: String): String {
    val context = LocalContext.current
    val packageManager = remember(context) { context.packageManager }

    val labelState = produceState(
        initialValue = packageName.substringAfterLast('.'),
        key1 = packageName
    ) {
        val resolvedLabel = withContext(Dispatchers.IO) {
            runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            }.getOrNull()
        }
        if (!resolvedLabel.isNullOrBlank()) {
            value = resolvedLabel
        }
    }
    return labelState.value
}

/**
 * Builds a cache-busting version token (`packageName|longVersionCode|lastUpdateTime`) using
 * `PackageManager.PackageInfoFlags` so updated apps automatically invalidate stale disk icons.
 */
private fun resolvePackageIconVersionToken(packageManager: PackageManager, packageName: String): String? {
    val packageInfo = runCatching {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    }.getOrNull() ?: return null

    val versionCode = packageInfo.longVersionCode
    return "$packageName|$versionCode|${packageInfo.lastUpdateTime}"
}
