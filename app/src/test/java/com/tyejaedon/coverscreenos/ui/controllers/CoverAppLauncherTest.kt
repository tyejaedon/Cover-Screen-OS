package com.tyejaedon.coverscreenos.ui.controllers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.content.pm.PackageManager
import android.view.Display
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CoverAppLauncherTest {

    private companion object {
        private const val PACKAGE_NAME = "com.example.target"
        private const val EXPLICIT_DISPLAY_ID = 24
        private const val CONTEXT_DISPLAY_ID = 7
        private const val PRESENTATION_DISPLAY_ID = 11
    }

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager

    @Before
    fun setup() {
        context = mockk()
        packageManager = mockk()

        every { context.packageManager } returns packageManager
    }

    @Test
    fun `launchPackageOnDisplay applies mandatory intent flags`() {
        val launchIntent = mockk<Intent>()
        val addedFlags = slot<Int>()
        val launchExecutor = RecordingLaunchExecutor()

        every { launchIntent.addFlags(capture(addedFlags)) } returns launchIntent
        every { packageManager.getLaunchIntentForPackage(PACKAGE_NAME) } returns launchIntent

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = EXPLICIT_DISPLAY_ID,
            activityLaunchExecutor = launchExecutor
        )

        val requiredFlags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED

        assertTrue(launched)
        assertEquals(requiredFlags, addedFlags.captured)
        assertEquals(EXPLICIT_DISPLAY_ID, launchExecutor.lastLaunchDisplayId)
    }

    @Test
    fun `launchPackageOnDisplay uses context display id when explicit id is null`() {
        val launchIntent = mockk<Intent>()
        val launchExecutor = RecordingLaunchExecutor()
        val coverDisplay = mockk<Display>()

        every { launchIntent.addFlags(any()) } returns launchIntent
        every { packageManager.getLaunchIntentForPackage(PACKAGE_NAME) } returns launchIntent
        every { context.display } returns coverDisplay
        every { coverDisplay.displayId } returns CONTEXT_DISPLAY_ID

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = null,
            activityLaunchExecutor = launchExecutor
        )

        assertTrue(launched)
        assertEquals(CONTEXT_DISPLAY_ID, launchExecutor.lastLaunchDisplayId)
    }

    @Test
    fun `launchPackageOnDisplay falls back to default display when context display lookup fails`() {
        val launchIntent = mockk<Intent>()
        val launchExecutor = RecordingLaunchExecutor()

        every { launchIntent.addFlags(any()) } returns launchIntent
        every { packageManager.getLaunchIntentForPackage(PACKAGE_NAME) } returns launchIntent
        every { context.display } throws IllegalStateException("Display unavailable")

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = null,
            activityLaunchExecutor = launchExecutor
        )

        assertTrue(launched)
        assertEquals(Display.DEFAULT_DISPLAY, launchExecutor.lastLaunchDisplayId)
    }

    @Test
    fun `launchPackageOnDisplay falls back to presentation display when explicit display is unavailable`() {
        val launchIntent = mockk<Intent>()
        val launchExecutor = RecordingLaunchExecutor()
        val displayManager = mockk<DisplayManager>()
        val unavailableDisplay = createDisplay(id = EXPLICIT_DISPLAY_ID, isValid = false)
        val presentationDisplay = createDisplay(id = PRESENTATION_DISPLAY_ID)

        every { launchIntent.addFlags(any()) } returns launchIntent
        every { packageManager.getLaunchIntentForPackage(PACKAGE_NAME) } returns launchIntent
        every { context.getSystemService(Context.DISPLAY_SERVICE) } returns displayManager
        every { displayManager.getDisplay(EXPLICIT_DISPLAY_ID) } returns unavailableDisplay
        every { context.display } throws IllegalStateException("Display unavailable")
        every {
            displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        } returns arrayOf(presentationDisplay)

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = EXPLICIT_DISPLAY_ID,
            activityLaunchExecutor = launchExecutor
        )

        assertTrue(launched)
        assertEquals(PRESENTATION_DISPLAY_ID, launchExecutor.lastLaunchDisplayId)
    }

    @Test
    fun `launchPackageOnDisplay returns false when package has no launcher intent`() {
        val launchExecutor = RecordingLaunchExecutor()

        every { packageManager.getLaunchIntentForPackage(PACKAGE_NAME) } returns null

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = EXPLICIT_DISPLAY_ID,
            activityLaunchExecutor = launchExecutor
        )

        assertFalse(launched)
        assertNull(launchExecutor.lastLaunchIntent)
    }

    @Test
    fun `launchPackageOnDisplay returns false when ActivityNotFoundException is thrown`() {
        val launchIntent = mockk<Intent>()
        val launchExecutor = RecordingLaunchExecutor(
            failure = ActivityNotFoundException("Target activity removed")
        )

        every { launchIntent.addFlags(any()) } returns launchIntent
        every { packageManager.getLaunchIntentForPackage(PACKAGE_NAME) } returns launchIntent

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = EXPLICIT_DISPLAY_ID,
            activityLaunchExecutor = launchExecutor
        )

        assertFalse(launched)
    }

    @Test
    fun `launchPackageOnDisplay returns false when SecurityException is thrown`() {
        val launchIntent = mockk<Intent>()
        val launchExecutor = RecordingLaunchExecutor(
            failure = SecurityException("Permission denied")
        )

        every { launchIntent.addFlags(any()) } returns launchIntent
        every { packageManager.getLaunchIntentForPackage(PACKAGE_NAME) } returns launchIntent

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = EXPLICIT_DISPLAY_ID,
            activityLaunchExecutor = launchExecutor
        )

        assertFalse(launched)
    }

    @Test
    fun `launchPackageOnDisplay returns false when RuntimeException is thrown`() {
        val launchIntent = mockk<Intent>()
        val launchExecutor = RecordingLaunchExecutor(
            failure = IllegalStateException("Unexpected runtime failure")
        )

        every { launchIntent.addFlags(any()) } returns launchIntent
        every { packageManager.getLaunchIntentForPackage(PACKAGE_NAME) } returns launchIntent

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = EXPLICIT_DISPLAY_ID,
            activityLaunchExecutor = launchExecutor
        )

        assertFalse(launched)
    }

    @Test
    fun `launchPackageOnDisplay dismisses overlay before dispatching startActivity`() {
        val launchIntent = mockk<Intent>()
        val launchEvents = mutableListOf<String>()
        val launchExecutor = CoverAppLauncher.ActivityLaunchExecutor { _, _, _ ->
            launchEvents += "launch"
        }
        val overlayDismissExecutor = CoverAppLauncher.OverlayDismissExecutor { _, _ ->
            launchEvents += "dismiss"
        }

        every { launchIntent.addFlags(any()) } returns launchIntent
        every { packageManager.getLaunchIntentForPackage(PACKAGE_NAME) } returns launchIntent

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = EXPLICIT_DISPLAY_ID,
            activityLaunchExecutor = launchExecutor,
            overlayDismissExecutor = overlayDismissExecutor
        )

        assertTrue(launched)
        assertEquals(listOf("dismiss", "launch"), launchEvents)
    }

    private class RecordingLaunchExecutor(
        private val failure: Throwable? = null
    ) : CoverAppLauncher.ActivityLaunchExecutor {

        var lastLaunchContext: Context? = null
        var lastLaunchIntent: Intent? = null
        var lastLaunchDisplayId: Int? = null

        override fun launch(context: Context, launchIntent: Intent, launchDisplayId: Int) {
            failure?.let { throw it }
            lastLaunchContext = context
            lastLaunchIntent = launchIntent
            lastLaunchDisplayId = launchDisplayId
        }
    }

    private fun createDisplay(
        id: Int,
        isValid: Boolean = true,
        state: Int = Display.STATE_ON
    ): Display {
        val display = mockk<Display>()
        every { display.displayId } returns id
        every { display.isValid } returns isValid
        every { display.state } returns state
        return display
    }
}




