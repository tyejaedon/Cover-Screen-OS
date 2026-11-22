package com.tyejaedon.coverscreenos.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverHomeSurfaceMatcherTest {

    private val transientPolicy = OverlayTransientSignalPolicy(
        transientSystemUiPrefixes = arrayOf(
            "com.android.systemui",
            "com.samsung.systemui",
            "com.samsung.android.app.aodservice"
        ),
        transientSystemUiResumeSafePrefixes = arrayOf(
            "com.samsung.systemui",
            "com.samsung.android.app.aodservice"
        ),
        overlayResumePackagePrefixes = arrayOf(
            "com.android.systemui",
            "com.samsung.systemui",
            "com.samsung.android.app.aodservice",
            "com.sec.android.app.launcher"
        ),
        launcherPackagePrefixes = arrayOf("com.sec.android.app.launcher"),
        transientSystemUiResumeGraceMs = 120L,
        transientExitFailsafeMinSuppressionMs = 700L,
        transientExitPatternWindowMs = 1_800L
    )

    @Test
    fun `subscreen home activity on One UI is recognized as cover home destination`() {
        assertTrue(
            CoverHomeSurfaceMatcher.isCoverHomeDestination(
                packageName = "com.android.systemui",
                className = "com.android.systemui.subscreen.SubHomeActivity"
            )
        )
        assertTrue(
            CoverHomeSurfaceMatcher.isCoverHomeDestination(
                packageName = "com.sec.android.app.launcher",
                className = "com.sec.android.app.launcher.activities.LauncherActivity"
            )
        )
    }

    @Test
    fun `transient SystemUI overlays are not treated as cover home destinations`() {
        assertFalse(
            CoverHomeSurfaceMatcher.isCoverHomeDestination(
                packageName = "com.android.systemui",
                className = "com.android.systemui.shade.NotificationShadeWindowView"
            )
        )
        assertFalse(
            CoverHomeSurfaceMatcher.isCoverHomeDestination(
                packageName = "com.android.systemui",
                className = null
            )
        )
    }

    @Test
    fun `transient signal policy allows SubHomeActivity resume immediately and blocks transient SystemUI`() {
        val homePkg = "com.android.systemui"
        val homeClass = "com.android.systemui.subscreen.SubHomeActivity"
        assertFalse(transientPolicy.isTransientSystemUiPackage(homePkg, homeClass))
        assertTrue(transientPolicy.isTransientForegroundSafeForResume(homePkg, homeClass))
        assertTrue(transientPolicy.isLauncherPackageForResume(homePkg, homeClass))
        assertTrue(
            transientPolicy.shouldResumeOverlayForPackage(
                packageName = homePkg,
                className = homeClass,
                launchedPackage = "com.android.chrome",
                appPackageName = "com.tyejaedon.coverscreenos"
            )
        )

        val shadeClass = "com.android.systemui.statusbar.phone.StatusBarWindowView"
        assertTrue(transientPolicy.isTransientSystemUiPackage(homePkg, shadeClass))
        assertFalse(transientPolicy.isTransientForegroundSafeForResume(homePkg, shadeClass))
    }

    @Test
    fun `known transient SystemUI overlay classes block fallback dwell while generic containers recover`() {
        assertTrue(CoverHomeSurfaceMatcher.isKnownTransientSystemUiOverlayClass("com.android.systemui.shade.NotificationShadeWindowView"))
        assertTrue(CoverHomeSurfaceMatcher.isKnownTransientSystemUiOverlayClass("com.android.systemui.volume.VolumeDialogImpl"))
        assertFalse(CoverHomeSurfaceMatcher.isKnownTransientSystemUiOverlayClass("android.widget.FrameLayout"))
        assertFalse(CoverHomeSurfaceMatcher.isKnownTransientSystemUiOverlayClass(null))

        val pkg = "com.android.systemui"
        val genericClass = "android.widget.FrameLayout"
        // Shade class must never satisfy sustained SystemUI fallback dwell even after timeout.
        assertFalse(
            transientPolicy.shouldResumeFromSustainedSystemUiFallback(
                packageName = pkg,
                className = "com.android.systemui.shade.NotificationShadeWindowView",
                suppressedForMs = 2_000L,
                nowElapsedMs = 1_000L
            )
        )
        assertFalse(
            transientPolicy.shouldResumeFromSustainedSystemUiFallback(
                packageName = pkg,
                className = "com.android.systemui.shade.NotificationShadeWindowView",
                suppressedForMs = 3_500L,
                nowElapsedMs = 2_500L
            )
        )

        // Generic SystemUI container starts dwell on tick 1 and succeeds after >= 1,200 ms.
        assertFalse(
            transientPolicy.shouldResumeFromSustainedSystemUiFallback(
                packageName = pkg,
                className = genericClass,
                suppressedForMs = 1_000L,
                nowElapsedMs = 5_000L
            )
        )
        assertFalse(
            transientPolicy.shouldResumeFromSustainedSystemUiFallback(
                packageName = pkg,
                className = genericClass,
                suppressedForMs = 1_600L,
                nowElapsedMs = 5_600L
            )
        )
        assertTrue(
            transientPolicy.shouldResumeFromSustainedSystemUiFallback(
                packageName = pkg,
                className = genericClass,
                suppressedForMs = 2_250L,
                nowElapsedMs = 6_250L
            )
        )
    }

    @Test
    fun `native Recents and Overview surfaces never match cover home or trigger reclaim`() {
        val samsungRecentsPkg = "com.sec.android.app.launcher"
        val samsungRecentsCls = "com.sec.android.app.launcher.recents.RecentsActivity"
        val systemRecentsPkg = "com.android.systemui"
        val systemRecentsCls = "com.android.systemui.recents.OverviewProxyService"

        assertTrue(CoverHomeSurfaceMatcher.isRecentsSurfaceClassName(samsungRecentsCls))
        assertTrue(CoverHomeSurfaceMatcher.isRecentsSurfaceClassName(systemRecentsCls))
        assertFalse(CoverHomeSurfaceMatcher.isCoverHomeDestination(samsungRecentsPkg, samsungRecentsCls))
        assertFalse(CoverHomeSurfaceMatcher.isCoverHomeDestination(systemRecentsPkg, systemRecentsCls))
        assertFalse(transientPolicy.isLauncherPackageForResume(samsungRecentsPkg, samsungRecentsCls))
        assertFalse(
            transientPolicy.shouldResumeOverlayForPackage(
                packageName = samsungRecentsPkg,
                className = samsungRecentsCls,
                launchedPackage = "com.android.chrome",
                appPackageName = "com.tyejaedon.coverscreenos"
            )
        )
        assertTrue(CoverAccessibilityService.isUserForegroundApplicationPackage(samsungRecentsPkg, samsungRecentsCls))
    }
}
