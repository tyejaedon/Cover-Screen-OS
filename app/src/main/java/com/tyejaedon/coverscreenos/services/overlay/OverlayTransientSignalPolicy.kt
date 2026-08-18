package com.tyejaedon.coverscreenos.services.overlay

import android.os.SystemClock

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

    fun isTransientSystemUiPackage(packageName: String): Boolean {
        return transientSystemUiPrefixes.any { packageName.startsWith(it) }
    }

    fun isTransientForegroundSafeForResume(packageName: String): Boolean {
        return transientSystemUiResumeSafePrefixes.any { packageName.startsWith(it) }
    }

    fun isLauncherPackageForResume(packageName: String): Boolean {
        return launcherPackagePrefixes.any { packageName.startsWith(it) }
    }

    fun shouldResumeOverlayForPackage(packageName: String, launchedPackage: String?, appPackageName: String): Boolean {
        if (launchedPackage != null && packageName == launchedPackage) return false
        if (packageName == appPackageName) return true
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

