package com.tyejaedon.coverscreenos.services.overlay

import android.hardware.display.DisplayManager
import android.view.Display
import com.tyejaedon.coverscreenos.helpers.CoverDisplayHelper
import com.tyejaedon.coverscreenos.helpers.ForegroundServiceHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Attach / retarget decision tests for [ForegroundService], parameterized
 * over [OverlayHostMode] (Phase 3 of the accessibility-overlay
 * migration — see `docs/architecture/Overlay-architecture-shift-plan.md` §8.1).
 *
 * The [OverlayWindowController] is mocked, so the test proves the
 * façade selection is independent of the host implementation: for
 * every hostMode value the same decision matrix (`showOverlay` /
 * `suppressOverlayForLaunch` / `removeOverlay`) must hold.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class ForegroundServiceAttachOrRetargetOverlayRobolectricTest(
    private val overlayHostMode: OverlayHostMode
) {

    private companion object {
        private const val FOREGROUND_LOG_TAG = "CoverForegroundService"
        private const val TRANSITION_LOG_TAG = "CoverOverlayTransition"
        private const val HELD_HIDDEN_MARKER = "marker=held_hidden"
        private const val REMOVED_STALE_MARKER = "marker=removed_stale"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "overlayHostMode={0}")
        fun overlayHostModes(): Iterable<Array<Any>> = OverlayHostMode.entries
            .map { arrayOf<Any>(it) }
    }

    private lateinit var serviceController: ServiceController<ForegroundService>
    private lateinit var service: ForegroundService

    @Before
    fun setup() {
        mockkObject(ForegroundServiceHelper)
        every { ForegroundServiceHelper.hasRequiredOverlayPermissions(any()) } returns true
        ShadowLog.clear()

        serviceController = Robolectric.buildService(ForegroundService::class.java).create()
        service = serviceController.get()
        setPrivateField("currentOverlayHostMode", overlayHostMode)
    }

    @After
    fun teardown() {
        serviceController.destroy()
        unmockkObject(ForegroundServiceHelper)
        ShadowLog.clear()
    }

    @Test
    fun `attachOrRetargetOverlay attaches overlay when cover display is available`() {
        val overlayWindowController = mockk<OverlayWindowController>(relaxed = true)
        val coverDisplayHelper = mockk<CoverDisplayHelper>(relaxed = true)
        val displayManager = mockk<DisplayManager>(relaxed = true)
        val targetDisplay = mockk<Display>()

        every { targetDisplay.displayId } returns 5
        every { coverDisplayHelper.getCoverDisplay() } returns targetDisplay
        every { overlayWindowController.isOverlayAttached() } returns false
        every { overlayWindowController.getActiveDisplayId() } returns null
        every { overlayWindowController.showOverlay(targetDisplay, false, any()) } returns true

        injectRuntimeDependencies(
            overlayWindowController = overlayWindowController,
            coverDisplayHelper = coverDisplayHelper,
            displayManager = displayManager
        )

        invokeAttachOrRetargetOverlay(reason = "test_cover_available")

        verify(exactly = 1) { overlayWindowController.showOverlay(targetDisplay, false, any()) }
        verify(exactly = 0) { overlayWindowController.suppressOverlayForLaunch() }
        verify(exactly = 0) { overlayWindowController.removeOverlay() }
        assertFalse(hasTransitionMarker(HELD_HIDDEN_MARKER))
        assertFalse(hasTransitionMarker(REMOVED_STALE_MARKER))
    }

    @Test
    fun `attachOrRetargetOverlay holds hidden overlay when active cover display remains valid`() {
        val overlayWindowController = mockk<OverlayWindowController>(relaxed = true)
        val coverDisplayHelper = mockk<CoverDisplayHelper>(relaxed = true)
        val displayManager = mockk<DisplayManager>(relaxed = true)
        val activeDisplay = mockk<Display>()

        every { coverDisplayHelper.getCoverDisplay() } returns null
        every { overlayWindowController.isOverlayAttached() } returns true
        every { overlayWindowController.getActiveDisplayId() } returns 7
        every { displayManager.getDisplay(7) } returns activeDisplay
        every { activeDisplay.isValid } returns true

        injectRuntimeDependencies(
            overlayWindowController = overlayWindowController,
            coverDisplayHelper = coverDisplayHelper,
            displayManager = displayManager
        )

        invokeAttachOrRetargetOverlay(reason = "test_hold_hidden")

        verify(exactly = 1) { overlayWindowController.suppressOverlayForLaunch() }
        verify(exactly = 0) { overlayWindowController.removeOverlay() }
        assertTrue(hasTransitionMarker(HELD_HIDDEN_MARKER))
        assertFalse(hasTransitionMarker(REMOVED_STALE_MARKER))
    }

    @Test
    fun `attachOrRetargetOverlay removes stale overlay when active display becomes invalid`() {
        val overlayWindowController = mockk<OverlayWindowController>(relaxed = true)
        val coverDisplayHelper = mockk<CoverDisplayHelper>(relaxed = true)
        val displayManager = mockk<DisplayManager>(relaxed = true)
        val invalidDisplay = mockk<Display>()

        every { coverDisplayHelper.getCoverDisplay() } returns null
        every { overlayWindowController.isOverlayAttached() } returns true
        every { overlayWindowController.getActiveDisplayId() } returns 9
        every { displayManager.getDisplay(9) } returns invalidDisplay
        every { invalidDisplay.isValid } returns false

        injectRuntimeDependencies(
            overlayWindowController = overlayWindowController,
            coverDisplayHelper = coverDisplayHelper,
            displayManager = displayManager
        )

        invokeAttachOrRetargetOverlay(reason = "test_remove_invalid")

        verify(exactly = 1) { overlayWindowController.removeOverlay() }
        verify(exactly = 0) { overlayWindowController.suppressOverlayForLaunch() }
        assertTrue(hasTransitionMarker(REMOVED_STALE_MARKER))
        assertFalse(hasTransitionMarker(HELD_HIDDEN_MARKER))
    }

    @Test
    fun `attachOrRetargetOverlay removes stale state when attached view is already gone`() {
        val overlayWindowController = mockk<OverlayWindowController>(relaxed = true)
        val coverDisplayHelper = mockk<CoverDisplayHelper>(relaxed = true)
        val displayManager = mockk<DisplayManager>(relaxed = true)
        val orphanDisplay = mockk<Display>()

        every { coverDisplayHelper.getCoverDisplay() } returns null
        every { overlayWindowController.isOverlayAttached() } returns false
        every { overlayWindowController.getActiveDisplayId() } returns 3
        every { displayManager.getDisplay(3) } returns orphanDisplay
        every { orphanDisplay.isValid } returns true

        injectRuntimeDependencies(
            overlayWindowController = overlayWindowController,
            coverDisplayHelper = coverDisplayHelper,
            displayManager = displayManager
        )

        invokeAttachOrRetargetOverlay(reason = "test_remove_orphan")

        verify(exactly = 1) { overlayWindowController.removeOverlay() }
        verify(exactly = 0) { overlayWindowController.suppressOverlayForLaunch() }
        assertTrue(hasTransitionMarker(REMOVED_STALE_MARKER))
    }

    @Test
    fun `attachOrRetargetOverlay returns early when suppression is active`() {
        val overlayWindowController = mockk<OverlayWindowController>(relaxed = true)
        val coverDisplayHelper = mockk<CoverDisplayHelper>(relaxed = true)
        val displayManager = mockk<DisplayManager>(relaxed = true)

        setSuppressionActive()
        injectRuntimeDependencies(
            overlayWindowController = overlayWindowController,
            coverDisplayHelper = coverDisplayHelper,
            displayManager = displayManager
        )

        invokeAttachOrRetargetOverlay(reason = "test_suppression_guard")

        verify(exactly = 0) { coverDisplayHelper.getCoverDisplay() }
        verify(exactly = 0) { overlayWindowController.showOverlay(any(), any(), any()) }
        verify(exactly = 0) { overlayWindowController.suppressOverlayForLaunch() }
        verify(exactly = 0) { overlayWindowController.removeOverlay() }
        assertFalse(hasTransitionMarker(HELD_HIDDEN_MARKER))
        assertFalse(hasTransitionMarker(REMOVED_STALE_MARKER))
    }

    @Test
    fun `attachOrRetargetOverlay stops runtime when prerequisites are missing`() {
        val overlayWindowController = mockk<OverlayWindowController>(relaxed = true)
        val coverDisplayHelper = mockk<CoverDisplayHelper>(relaxed = true)
        val displayManager = mockk<DisplayManager>(relaxed = true)

        every { ForegroundServiceHelper.hasRequiredOverlayPermissions(any()) } returns false
        injectRuntimeDependencies(
            overlayWindowController = overlayWindowController,
            coverDisplayHelper = coverDisplayHelper,
            displayManager = displayManager
        )

        invokeAttachOrRetargetOverlay(reason = "test_prereq")

        verify(exactly = 1) { coverDisplayHelper.stopLockStatusMonitoring() }
        verify(exactly = 1) { overlayWindowController.removeOverlay() }
        verify(exactly = 0) { coverDisplayHelper.getCoverDisplay() }
        verify(exactly = 0) { overlayWindowController.showOverlay(any(), any(), any()) }
        verify(exactly = 0) { overlayWindowController.suppressOverlayForLaunch() }
        assertTrue(
            ShadowLog.getLogsForTag(FOREGROUND_LOG_TAG)
                .any { entry -> entry.msg?.contains("reason=test_prereq prerequisites_lost") == true }
        )
        assertFalse(hasTransitionMarker(HELD_HIDDEN_MARKER))
        assertFalse(hasTransitionMarker(REMOVED_STALE_MARKER))
    }

    private fun injectRuntimeDependencies(
        overlayWindowController: OverlayWindowController,
        coverDisplayHelper: CoverDisplayHelper,
        displayManager: DisplayManager
    ) {
        setPrivateField("overlayWindowController", overlayWindowController)
        setPrivateField("coverDisplayHelper", coverDisplayHelper)
        setPrivateField("displayManager", displayManager)
    }

    private fun invokeAttachOrRetargetOverlay(reason: String) {
        val method = ForegroundService::class.java.getDeclaredMethod("attachOrRetargetOverlay", String::class.java)
        method.isAccessible = true
        method.invoke(service, reason)
    }

    private fun setPrivateField(fieldName: String, value: Any) {
        val field = ForegroundService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(service, value)
    }

    private fun setSuppressionActive() {
        val field = ForegroundService::class.java.getDeclaredField("suppressionState")
        field.isAccessible = true
        val suppressionState = field.get(service) as OverlaySuppressionState
        suppressionState.isOverlaySuppressedForAppLaunch = true
        suppressionState.suppressionReason = OverlaySuppressionReason.APP_LAUNCH
    }

    private fun hasTransitionMarker(marker: String): Boolean {
        return ShadowLog.getLogsForTag(TRANSITION_LOG_TAG)
            .any { entry -> entry.msg?.contains(marker) == true }
    }
}

