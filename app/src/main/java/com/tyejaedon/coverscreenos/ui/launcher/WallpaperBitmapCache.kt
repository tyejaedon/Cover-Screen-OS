package com.tyejaedon.coverscreenos.ui

import android.util.LruCache

/**
 * Process-wide in-memory LRU cache of decoded [WallpaperFrame] poster and image bitmaps.
 *
 * Caching avoids re-running `ImageDecoder` or `MediaMetadataRetriever` frame extraction when
 * the cover overlay recomposes or rebinds across fold/screen-state transitions. Capacity is
 * capped at 10 MiB (measured in KiB units) and synchronized for safe access from `Dispatchers.IO`.
 */
object WallpaperBitmapCache {
    /**
     * In-memory least-recently-used (LRU) cache holding up to 10 MiB (`10 * 1024` KiB) of
     * decoded [WallpaperFrame] objects so waking the cover screen doesn't re-decode heavy media.
     */
    private val cache = object : LruCache<String, WallpaperFrame>(10 * 1024) {
        // LruCache expects sizeOf in consistent units; KiB avoids integer overflow on large bitmaps.
        override fun sizeOf(key: String, value: WallpaperFrame): Int = value.bitmap.byteCount / 1024
    }

    /**
     * Builds a composite cache key that invalidates whenever the source URI, file/transform
     * token, or target viewport dimensions change.
     */
    fun buildKey(uri: String, versionToken: String?, requestedWidthPx: Int, requestedHeightPx: Int): String {
        return "$uri|$versionToken|${requestedWidthPx}x$requestedHeightPx"
    }

    /** Returns the cached [WallpaperFrame] for [key], or `null` on a cache miss. */
    internal fun get(key: String): WallpaperFrame? = synchronized(cache) { cache.get(key) }

    /** Stores a decoded [WallpaperFrame] under [key], evicting least-recently-used entries if full. */
    internal fun put(key: String, frame: WallpaperFrame) { synchronized(cache) { cache.put(key, frame) } }

    /** Trims or evicts cached wallpaper frames in response to system memory pressure. */
    fun trimMemory(maxBytes: Int) {
        synchronized(cache) {
            if (maxBytes <= 0) {
                cache.evictAll()
            } else {
                cache.trimToSize((maxBytes / 1024).coerceAtLeast(0))
            }
        }
    }
}
