package com.tyejaedon.coverscreenos.ui

import android.graphics.Bitmap
import android.util.LruCache
import kotlin.math.max

internal object WallpaperBitmapCache {

    private val bitmapCache by lazy {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt().coerceAtLeast(2048)
        val cacheSizeKb = (maxMemoryKb / 16).coerceAtLeast(1024)
        object : LruCache<String, Bitmap>(cacheSizeKb) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return max(1, value.byteCount / 1024)
            }
        }
    }

    fun buildKey(
        uri: String,
        requestedWidthPx: Int,
        requestedHeightPx: Int,
        versionToken: String? = null
    ): String {
        return if (versionToken.isNullOrBlank()) {
            "$uri|$requestedWidthPx|$requestedHeightPx"
        } else {
            "$uri|$versionToken|$requestedWidthPx|$requestedHeightPx"
        }
    }

    fun get(cacheKey: String): Bitmap? = synchronized(bitmapCache) {
        bitmapCache.get(cacheKey)
    }

    fun put(cacheKey: String, bitmap: Bitmap) {
        synchronized(bitmapCache) {
            bitmapCache.put(cacheKey, bitmap)
        }
    }
}

