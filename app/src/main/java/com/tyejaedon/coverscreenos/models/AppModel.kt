package com.tyejaedon.coverscreenos.models

import android.graphics.drawable.Drawable

/**
 * Lightweight descriptor for a launchable Android application discovered by [android.content.pm.PackageManager].
 *
 * @property name Localized launcher display label resolved from the app's manifest/resources.
 * @property packageName Unique Android application package ID used for launching and dock persistence.
 * @property iconDrawable Optional launcher icon [Drawable]; `null` when scanned in metadata-only mode
 * to avoid allocating bitmap/vector drawables unnecessarily.
 */
data class AppModel(
    // Human-readable app title shown beneath the icon in the drawer and dock picker.
    val name: String,
    // Unique Android package ID (e.g. "com.android.chrome") used as a stable key and launch target.
    val packageName: String,
    // Preloaded launcher icon; stays null during fast metadata-only scans to save memory.
    val iconDrawable: Drawable? = null
)

