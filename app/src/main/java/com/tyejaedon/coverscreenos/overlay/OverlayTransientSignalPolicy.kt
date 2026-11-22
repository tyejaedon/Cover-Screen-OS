package com.tyejaedon.coverscreenos.overlay

import android.os.SystemClock

/**
 * Classifies cover-display home destinations across Android / Samsung One UI versions.
 *
 * On One UI 9.0 (Android 17) foldable cover displays, exiting a cover app returns
 * to `com.android.systemui/.subscreen.SubHomeActivity` rather than `com.sec.android.app.launcher`.
 */
object CoverHomeSurfaceMatcher {
    // Package name of Android/One UI SystemUI, which hosts the cover SubHomeActivity on newer builds.
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    // Known launcher package prefixes treated as native home destinations.
    private val LAUNCHER_HOME_PREFIXES = arrayOf(
        "com.sec.android.app.launcher",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher"
    )

    /**
     * Returns `true` when [packageName] and optional [className] identify a native
     * home/cover-home activity rather than a user application.
     */
    fun isCoverHomeDestination(packageName: String, className: String? = null): Boolean {
        if (LAUNCHER_HOME_PREFIXES.any { packageName.startsWith(it) }) {
            return true
        }
        if (packageName == SYSTEM_UI_PACKAGE && !className.isNullOrBlank()) {
            return isSubscreenHomeClassName(className)
        }
        return false
    }

    /** Returns `true` if [className] refers to Samsung One UI's cover `SubHomeActivity`. */
    fun isSubscreenHomeClassName(className: String): Boolean {
        val trimmed = className.trim()
        return trimmed == "com.android.systemui.subscreen.SubHomeActivity" ||
            trimmed.endsWith(".SubHomeActivity")
    }
}

/**
 * Classifies transient SystemUI, Always-On Display (AOD), and launcher window signals
 * while the cover overlay is suppressed during an app launch.
 *
 * Distinguishes brief system chrome (notification shade, volume/AOD flashes) from genuine
 * exits back to the cover home surface using two recovery mechanisms:
 * - **Sustained transient dwell**: holding on a resume-safe SystemUI/AOD package for at least
 *   [transientSystemUiResumeGraceMs].
 * - **Transient exit pattern**: observing both `com.android.systemui` and
 *   `com.samsung.android.app.aodservice` within [transientExitPatternWindowMs] after
 *   [transientExitFailsafeMinSuppressionMs].
 */
internal class OverlayTransientSignalPolicy(
    // Prefixes for transient system overlays (SystemUI, Samsung SystemUI, AOD).
    private val transientSystemUiPrefixes: Array<String>,
    // Subset of transient system prefixes safe to resume after [transientSystemUiResumeGraceMs].
    private val transientSystemUiResumeSafePrefixes: Array<String>,
    // Any foreground prefix suggesting the cover screen returned to system/home chrome.
    private val overlayResumePackagePrefixes: Array<String>,
    // Stock launcher package prefixes subject to the brief post-launch guard.
    private val launcherPackagePrefixes: Array<String>,
    // How long a resume-safe transient package must stay foregrounded before triggering resume.
    private val transientSystemUiResumeGraceMs: Long,
    // Minimum suppression duration before SystemUI+AOD exit-pattern detection is allowed.
    private val transientExitFailsafeMinSuppressionMs: Long,
    // Maximum age linking SystemUI and AOD timestamps as one combined cover-app exit signal.
    private val transientExitPatternWindowMs: Long
) {
    // Package currently holding transient foreground focus during dwell timing.
    private var transientForegroundPackage: String? = null
    // Monotonic timestamp when [transientForegroundPackage] first took focus.
    private var transientForegroundSinceElapsedMs: Long = 0L
    // Monotonic timestamp of the latest `com.android.systemui` observation during suppression.
    private var transientSystemUiSeenElapsedMs: Long = 0L
    // Monotonic timestamp of the latest `com.samsung.android.app.aodservice` observation during suppression.
    private var transientAodSeenElapsedMs: Long = 0L

    /**
     * Returns `true` for transient SystemUI / AOD windows, excluding explicit cover-home
     * destinations such as `SubHomeActivity`.
     */
    fun isTransientSystemUiPackage(packageName: String, className: String? = null): Boolean {
        if (CoverHomeSurfaceMatcher.isCoverHomeDestination(packageName, className)) {
            return false
        }
        return transientSystemUiPrefixes.any { packageName.startsWith(it) }
    }

    /**
     * Returns `true` if dwelling on [packageName]/[className] is safe to resume the overlay
     * once the grace period elapses (cover home or allowlisted Samsung SystemUI/AOD packages).
     */
    fun isTransientForegroundSafeForResume(packageName: String, className: String? = null): Boolean {
        if (CoverHomeSurfaceMatcher.isCoverHomeDestination(packageName, className)) {
            return true
        }
        return transientSystemUiResumeSafePrefixes.any { packageName.startsWith(it) }
    }

    /** Returns `true` if [packageName]/[className] represents a system launcher or cover home. */
    fun isLauncherPackageForResume(packageName: String, className: String? = null): Boolean {
        if (CoverHomeSurfaceMatcher.isCoverHomeDestination(packageName, className)) {
            return true
        }
        return launcherPackagePrefixes.any { packageName.startsWith(it) }
    }

    /**
     * Determines whether an accessibility foreground observation of [packageName]/[className]
     * is an eligible signal to un-suppress the cover launcher overlay.
     */
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

    /**
     * Returns `true` once [packageName] has continuously held transient foreground focus for
     * at least [transientSystemUiResumeGraceMs].
     */
    fun shouldResumeFromTransientForeground(packageName: String, nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
        if (transientForegroundPackage != packageName) {
            transientForegroundPackage = packageName
            transientForegroundSinceElapsedMs = nowElapsedMs
            return false
        }

        val seenForMs = (nowElapsedMs - transientForegroundSinceElapsedMs).coerceAtLeast(0L)
        return seenForMs >= transientSystemUiResumeGraceMs
    }

    /** Resets transient foreground dwell and exit-pattern timestamps. */
    fun resetTransientForegroundTracking() {
        transientForegroundPackage = null
        transientForegroundSinceElapsedMs = 0L
        transientSystemUiSeenElapsedMs = 0L
        transientAodSeenElapsedMs = 0L
    }

    /** Records timestamps for SystemUI and AOD appearances used by [shouldResumeFromTransientExitPattern]. */
    fun trackTransientExitPattern(packageName: String, nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        when {
            packageName.startsWith("com.android.systemui") -> transientSystemUiSeenElapsedMs = nowElapsedMs
            packageName.startsWith("com.samsung.android.app.aodservice") -> transientAodSeenElapsedMs = nowElapsedMs
        }
    }

    /**
     * Failsafe check that resumes an [OverlaySuppressionReason.APP_LAUNCH] overlay when both
     * SystemUI and Samsung AOD events occurred recently within [transientExitPatternWindowMs].
     */
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
