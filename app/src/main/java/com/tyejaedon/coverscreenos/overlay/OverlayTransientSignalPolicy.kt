package com.tyejaedon.coverscreenos.overlay

import android.os.SystemClock

/**
 * Classifies cover-display home destinations across Android / Samsung One UI versions.
 *
 * On One UI 9.0 (Android 17) foldable cover displays, exiting a cover app returns
 * to `com.android.systemui/.subscreen.SubHomeActivity` rather than `com.sec.android.app.launcher`.
 */
object CoverHomeSurfaceMatcher {
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private val LAUNCHER_HOME_PREFIXES = arrayOf(
        "com.sec.android.app.launcher",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher"
    )

    fun isCoverHomeDestination(packageName: String, className: String? = null): Boolean {
        if (LAUNCHER_HOME_PREFIXES.any { packageName.startsWith(it) }) {
            return true
        }
        if (packageName == SYSTEM_UI_PACKAGE && !className.isNullOrBlank()) {
            return isSubscreenHomeClassName(className)
        }
        return false
    }

    fun isSubscreenHomeClassName(className: String): Boolean {
        val trimmed = className.trim()
        return trimmed == "com.android.systemui.subscreen.SubHomeActivity" ||
            trimmed.endsWith(".SubHomeActivity")
    }
}

internal class OverlayTransientSignalPolicy(
    private val transientSystemUiPrefixes: Array<String>,
    private val transientSystemUiResumeSafePrefixes: Array<String>,
    private val overlayResumePackagePrefixes: Array<String>,
    private val launcherPackagePrefixes: Array<String>,
    private val transientSystemUiResumeGraceMs: Long,
    private val transientExitFailsafeMinSuppressionMs: Long,
    private val transientExitPatternWindowMs: Long
) {
    private var transientForegroundPackage: String? = null
    private var transientForegroundSinceElapsedMs: Long = 0L
    private var transientSystemUiSeenElapsedMs: Long = 0L
    private var transientAodSeenElapsedMs: Long = 0L

    fun isTransientSystemUiPackage(packageName: String, className: String? = null): Boolean {
        if (CoverHomeSurfaceMatcher.isCoverHomeDestination(packageName, className)) {
            return false
        }
        return transientSystemUiPrefixes.any { packageName.startsWith(it) }
    }

    fun isTransientForegroundSafeForResume(packageName: String, className: String? = null): Boolean {
        if (CoverHomeSurfaceMatcher.isCoverHomeDestination(packageName, className)) {
            return true
        }
        return transientSystemUiResumeSafePrefixes.any { packageName.startsWith(it) }
    }

    fun isLauncherPackageForResume(packageName: String, className: String? = null): Boolean {
        if (CoverHomeSurfaceMatcher.isCoverHomeDestination(packageName, className)) {
            return true
        }
        return launcherPackagePrefixes.any { packageName.startsWith(it) }
    }

    fun shouldResumeOverlayForPackage(
        packageName: String,
        className: String? = null,
        launchedPackage: String?,
        appPackageName: String
    ): Boolean {
        if (launchedPackage != null && packageName == launchedPackage) return false
        // Do NOT treat our own package as a "user is back on our launcher" signal.
        // Our launcher/keyboard/media panels are accessibility overlay windows
        // that emit accessibility window events under our own package name. If we
        // resumed on those, adding the cover keyboard would immediately un-suppress
        // the launcher overlay and paint it on top of the app the user just launched.
        // A genuine "user returned to us" signal is instead expressed by the launcher
        // package coming back to the foreground below, which we do still honour.
        if (packageName == appPackageName) return false
        if (CoverHomeSurfaceMatcher.isCoverHomeDestination(packageName, className)) return true
        if (launcherPackagePrefixes.any { packageName.startsWith(it) }) return true
        return overlayResumePackagePrefixes.any { packageName.startsWith(it) }
    }

    fun shouldResumeFromTransientForeground(packageName: String, nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
        if (transientForegroundPackage != packageName) {
            transientForegroundPackage = packageName
            transientForegroundSinceElapsedMs = nowElapsedMs
            return false
        }

        val seenForMs = (nowElapsedMs - transientForegroundSinceElapsedMs).coerceAtLeast(0L)
        return seenForMs >= transientSystemUiResumeGraceMs
    }

    fun resetTransientForegroundTracking() {
        transientForegroundPackage = null
        transientForegroundSinceElapsedMs = 0L
        transientSystemUiSeenElapsedMs = 0L
        transientAodSeenElapsedMs = 0L
    }

    fun trackTransientExitPattern(packageName: String, nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        when {
            packageName.startsWith("com.android.systemui") -> transientSystemUiSeenElapsedMs = nowElapsedMs
            packageName.startsWith("com.samsung.android.app.aodservice") -> transientAodSeenElapsedMs = nowElapsedMs
        }
    }

    fun shouldResumeFromTransientExitPattern(
        suppressionReason: OverlaySuppressionReason,
        suppressedForMs: Long,
        nowElapsedMs: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        if (suppressionReason != OverlaySuppressionReason.APP_LAUNCH) return false
        if (suppressedForMs < transientExitFailsafeMinSuppressionMs) return false
        if (transientSystemUiSeenElapsedMs <= 0L || transientAodSeenElapsedMs <= 0L) return false

        val systemUiAgeMs = (nowElapsedMs - transientSystemUiSeenElapsedMs).coerceAtLeast(0L)
        val aodAgeMs = (nowElapsedMs - transientAodSeenElapsedMs).coerceAtLeast(0L)
        return systemUiAgeMs <= transientExitPatternWindowMs && aodAgeMs <= transientExitPatternWindowMs
    }
}
