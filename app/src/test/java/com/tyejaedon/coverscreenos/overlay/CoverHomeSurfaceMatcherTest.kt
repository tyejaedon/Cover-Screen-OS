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
}
