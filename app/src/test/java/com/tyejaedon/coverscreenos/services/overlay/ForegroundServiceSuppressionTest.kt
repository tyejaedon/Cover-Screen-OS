package com.tyejaedon.coverscreenos.services.overlay

import android.os.SystemClock
import com.tyejaedon.coverscreenos.helpers.CoverDisplayHelper
import com.tyejaedon.coverscreenos.overlay.input.CoverInputAccessibilityService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForegroundServiceSuppressionTest {
    private lateinit var service: ForegroundService
    private lateinit var displayHelper: CoverDisplayHelper
    private lateinit var state: OverlaySuppressionState
    private var foregroundPackage: String? = null
    private var foregroundEventAgeMs = 0L
    private var launcherForegroundPackage: String? = null
    private var launcherEventAgeMs = 0L

    @Before
    fun setUp() {
        mockkObject(CoverInputAccessibilityService.Companion)
        mockkObject(CoverAccessibilityService.Companion)
        every { CoverInputAccessibilityService.currentForegroundPackage() } answers { foregroundPackage }
        every { CoverInputAccessibilityService.currentForegroundPackageEventAgeMs(any()) } answers {
            foregroundEventAgeMs
        }
        every { CoverAccessibilityService.currentForegroundPackage() } answers { launcherForegroundPackage }
        every { CoverAccessibilityService.currentForegroundPackageEventAgeMs(any()) } answers {
            launcherEventAgeMs
        }

        service = Robolectric.buildService(ForegroundService::class.java).create().get()
        displayHelper = mockk(relaxed = true)
        setField("coverDisplayHelper", displayHelper)
        setField("overlayWindowController", mockk<OverlayWindowController>(relaxed = true))
        setField("overlayRequested", true)
        state = getField("suppressionState")
    }

    @After
    fun tearDown() {
        service.onDestroy()
        unmockkObject(CoverInputAccessibilityService.Companion)
        unmockkObject(CoverAccessibilityService.Companion)
    }

    @Test
    fun `locked cover resumes without foreground event`() {
        startSuppression("com.example.first")
        every { displayHelper.getDisplayLockStatus() } returns true

        resume()

        assertFalse(state.isOverlaySuppressedForAppLaunch)
        assertEquals(null, state.launchSuppressedPackageName)
    }

    @Test
    fun `launched app and unrelated apps cannot prematurely resume overlay`() {
        startSuppression("com.example.first")
        foregroundPackage = "com.example.first"
        resume()
        assertTrue(state.isOverlaySuppressedForAppLaunch)

        foregroundPackage = "com.example.other"
        resume()
        assertTrue(state.isOverlaySuppressedForAppLaunch)
    }

    @Test
    fun `required launcher service resumes without optional input service`() {
        startSuppression("com.example.first")
        launcherForegroundPackage = "com.sec.android.app.launcher"
        resume()
        assertTrue(state.isOverlaySuppressedForAppLaunch)
        resume()
        assertFalse(state.isOverlaySuppressedForAppLaunch)
    }

    @Test
    fun `fresh launched app from input service wins over stale launcher event`() {
        startSuppression("com.example.first")
        launcherForegroundPackage = "com.sec.android.app.launcher"
        launcherEventAgeMs = 2_000L
        foregroundPackage = "com.example.first"
        foregroundEventAgeMs = 0L
        resume()
        resume()
        assertTrue(state.isOverlaySuppressedForAppLaunch)
    }

    @Test
    fun `stale launcher event cannot resume but fresh stable launcher can`() {
        startSuppression("com.example.first", elapsedMs = 1_000L)
        foregroundPackage = "com.sec.android.app.launcher"
        foregroundEventAgeMs = 3_000L
        resume()
        assertTrue(state.isOverlaySuppressedForAppLaunch)

        foregroundEventAgeMs = 0L
        resume()
        assertTrue(state.isOverlaySuppressedForAppLaunch)
        resume()
        assertFalse(state.isOverlaySuppressedForAppLaunch)
    }

    @Test
    fun `launcher remains gated during launch handoff`() {
        startSuppression("com.example.first", elapsedMs = 150L)
        foregroundPackage = "com.sec.android.app.launcher"
        resume()

        assertTrue(state.isOverlaySuppressedForAppLaunch)
    }

    @Test
    fun `explicit reclaim unblocks a suppressed launch without waiting for poll stability`() {
        startSuppression("com.example.first")
        foregroundPackage = "com.android.systemui"

        invoke("maybeResumeOverlayAfterAppLaunch", "reclaim:home")

        assertFalse(state.isOverlaySuppressedForAppLaunch)
    }

    @Test
    fun `failed launch restores suppression and a later launch tracks its own package`() {
        startSuppression("com.example.first")
        invoke("restoreOverlayAfterLaunchFailure", "com.example.first")
        assertFalse(state.isOverlaySuppressedForAppLaunch)

        startSuppression("com.example.second")
        foregroundPackage = "com.example.second"
        resume()
        assertTrue(state.isOverlaySuppressedForAppLaunch)

        foregroundPackage = "com.sec.android.app.launcher"
        resume()
        assertTrue(state.isOverlaySuppressedForAppLaunch)
        resume()
        assertFalse(state.isOverlaySuppressedForAppLaunch)
        assertEquals(null, state.launchSuppressedPackageName)
    }

    @Test
    fun `new suppression cycle discards stable signals from previous launch`() {
        startSuppression("com.example.first")
        assertFalse(state.hasStableResumeSignal("com.sec.android.app.launcher", requiredCount = 2))
        assertTrue(state.hasStableResumeSignal("com.sec.android.app.launcher", requiredCount = 2))

        startSuppression("com.example.second")

        assertFalse(state.hasStableResumeSignal("com.sec.android.app.launcher", requiredCount = 2))
        assertEquals(1, state.resumeSignalStableCount)
    }

    private fun startSuppression(packageName: String, elapsedMs: Long = 1_000L) {
        state.markSuppressionStarted(
            packageName,
            OverlaySuppressionReason.APP_LAUNCH,
            SystemClock.elapsedRealtime() - elapsedMs
        )
    }

    private fun resume() {
        invoke("maybeResumeOverlayAfterAppLaunch", "test")
    }

    private fun invoke(name: String, argument: String) {
        ForegroundService::class.java.getDeclaredMethod(name, String::class.java).apply {
            isAccessible = true
        }.invoke(service, argument)
    }

    private fun setField(name: String, value: Any) {
        ForegroundService::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.set(service, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(name: String): T {
        return ForegroundService::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.get(service) as T
    }
}
