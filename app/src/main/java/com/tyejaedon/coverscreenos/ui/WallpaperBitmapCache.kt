package com.tyejaedon.coverscreenos.ui

import android.graphics.Bitmap
import android.util.LruCache
import kotlin.math.max

 object WallpaperBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun buildKey(uri: String, versionToken: String?, requestedWidthPx: Int, requestedHeightPx: Int): String {
        return "$uri|$versionToken|${requestedWidthPx}x$requestedHeightPx"
    }

    fun get(key: String): Bitmap? = synchronized(cache) { cache.get(key) }
    fun put(key: String, bitmap: Bitmap) { synchronized(cache) { cache.put(key, bitmap) } }
}
