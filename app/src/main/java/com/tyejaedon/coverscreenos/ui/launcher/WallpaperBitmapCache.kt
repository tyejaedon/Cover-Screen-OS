package com.tyejaedon.coverscreenos.ui

import android.util.LruCache

object WallpaperBitmapCache {
    private val cache = object : LruCache<String, WallpaperFrame>(24 * 1024) {
        override fun sizeOf(key: String, value: WallpaperFrame): Int = value.bitmap.byteCount / 1024
    }

    fun buildKey(uri: String, versionToken: String?, requestedWidthPx: Int, requestedHeightPx: Int): String {
        return "$uri|$versionToken|${requestedWidthPx}x$requestedHeightPx"
    }

    internal fun get(key: String): WallpaperFrame? = synchronized(cache) { cache.get(key) }
    internal fun put(key: String, frame: WallpaperFrame) { synchronized(cache) { cache.put(key, frame) } }
}
