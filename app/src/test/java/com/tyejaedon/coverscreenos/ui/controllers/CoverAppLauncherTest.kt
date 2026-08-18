package com.tyejaedon.coverscreenos.ui.controllers

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.view.Display
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import com.tyejaedon.coverscreenos.services.ForegroundService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CoverAppLauncherTest {

    private companion object {
        private const val PACKAGE_NAME = "com.example.target"
        private const val EXPLICIT_DISPLAY_ID = 24
        private const val DEFAULT_DISPLAY_ID = 2
    }

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var keyguardManager: KeyguardManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        packageManager = mockk()
        keyguardManager = mockk()

        every { context.packageManager } returns packageManager
        every { context.getSystemService(Context.KEYGUARD_SERVICE) } returns keyguardManager
        every { keyguardManager.isDeviceLocked } returns false
        every { keyguardManager.isKeyguardLocked } returns false

        mockkObject(ForegroundService.Companion)
        every { ForegroundService.createHideOverlayIntent(any(), any()) } returns mockk(relaxed = true)

        mockkObject(UnlockBridgeActivity.Companion)
        every { UnlockBridgeActivity.startUnlockRequest(any(), any(), any()) } returns true
    }

    @After
    fun teardown() {
        unmockkObject(ForegroundService.Companion)
        unmockkObject(UnlockBridgeActivity.Companion)
    }

    @Test
    fun `launchPackageOnDisplay applies mandatory intent flags`() {
        val launchIntent = mockk<Intent>()
        val addedFlags = slot<Int>()
        val launchExecutor = RecordingLaunchExecutor()

        stubLaunchableActivity(launchIntent = launchIntent)
        every { launchIntent.addFlags(capture(addedFlags)) } returns launchIntent

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = EXPLICIT_DISPLAY_ID,
            activityLaunchExecutor = launchExecutor
        )

        val requiredFlags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP

        assertTrue(launched)
        assertEquals(requiredFlags, addedFlags.captured)
        assertEquals(EXPLICIT_DISPLAY_ID, launchExecutor.lastLaunchDisplayId)
    }

    @Test
    fun `launchPackageOnDisplay returns false when device is locked`() {
        every { keyguardManager.isDeviceLocked } returns true

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = EXPLICIT_DISPLAY_ID,
            activityLaunchExecutor = RecordingLaunchExecutor(),
            skipUnlockChallenge = false
        )

        assertFalse(launched)
        verify(exactly = 1) {
            UnlockBridgeActivity.startUnlockRequest(context, PACKAGE_NAME, EXPLICIT_DISPLAY_ID)
        }
    }

    @Test
    fun `launchPackageOnDisplay returns false when package has no launcher intent`() {
        every { packageManager.getLaunchIntentForPackage(PACKAGE_NAME) } returns null

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = EXPLICIT_DISPLAY_ID,
            activityLaunchExecutor = RecordingLaunchExecutor()
        )

        assertFalse(launched)
    }

    @Test
    fun `launchPackageOnDisplay returns false when launch activity is disabled`() {
        val launchIntent = mockk<Intent>()
        stubLaunchableActivity(
            launchIntent = launchIntent,
            activityInfo = createActivityInfo(enabled = false, exported = true)
        )
        every { launchIntent.addFlags(any()) } returns launchIntent

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = EXPLICIT_DISPLAY_ID,
            activityLaunchExecutor = RecordingLaunchExecutor()
        )

        assertFalse(launched)
    }

    @Test
    fun `launchPackageOnDisplay returns false when launch activity is not exported`() {
        val launchIntent = mockk<Intent>()
        stubLaunchableActivity(
            launchIntent = launchIntent,
            activityInfo = createActivityInfo(enabled = true, exported = false)
        )
        every { launchIntent.addFlags(any()) } returns launchIntent

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = EXPLICIT_DISPLAY_ID,
            activityLaunchExecutor = RecordingLaunchExecutor()
        )

        assertFalse(launched)
    }

    @Test
    fun `launchPackageOnDisplay starts hide-overlay service before launch dispatch`() {
        val launchIntent = mockk<Intent>()
        val launchEvents = mutableListOf<String>()
        val launchExecutor = CoverAppLauncher.ActivityLaunchExecutor { _, _, _ ->
            launchEvents += "launch"
        }

        stubLaunchableActivity(launchIntent = launchIntent)
        every { launchIntent.addFlags(any()) } returns launchIntent
        every { context.startService(any()) } answers {
            launchEvents += "hide"
            mockk<ComponentName>()
        }

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = EXPLICIT_DISPLAY_ID,
            activityLaunchExecutor = launchExecutor
        )

        assertTrue(launched)
        assertEquals(listOf("hide", "launch"), launchEvents)
    }

    @Test
    fun `launchPackageOnDisplay uses default display manager display when displayId is null`() {
        val launchIntent = mockk<Intent>()
        val launchExecutor = RecordingLaunchExecutor()
        val displayManager = mockk<DisplayManager>()
        val contextDisplay = mockk<Display>()
        val defaultDisplay = mockk<Display>()

        stubLaunchableActivity(launchIntent = launchIntent)
        every { launchIntent.addFlags(any()) } returns launchIntent
        every { context.display } returns contextDisplay
        every { contextDisplay.displayId } returns Display.INVALID_DISPLAY
        every { context.getSystemService(Context.DISPLAY_SERVICE) } returns displayManager
        every { displayManager.getDisplay(Display.DEFAULT_DISPLAY) } returns defaultDisplay
        every { defaultDisplay.displayId } returns DEFAULT_DISPLAY_ID

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = null,
            activityLaunchExecutor = launchExecutor
        )

        assertTrue(launched)
        assertEquals(DEFAULT_DISPLAY_ID, launchExecutor.lastLaunchDisplayId)
    }

    @Test
    fun `launchPackageOnDisplay falls back to Display_DEFAULT_DISPLAY when no display service is available`() {
        val launchIntent = mockk<Intent>()
        val launchExecutor = RecordingLaunchExecutor()

        stubLaunchableActivity(launchIntent = launchIntent)
        every { launchIntent.addFlags(any()) } returns launchIntent
        every { context.getSystemService(Context.DISPLAY_SERVICE) } returns null

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
    fun `launchPackageOnDisplay returns false when launch dispatch throws`() {
        val launchIntent = mockk<Intent>()
        val launchExecutor = RecordingLaunchExecutor(
            failure = ActivityNotFoundException("Target activity removed")
        )

        stubLaunchableActivity(launchIntent = launchIntent)
        every { launchIntent.addFlags(any()) } returns launchIntent

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = EXPLICIT_DISPLAY_ID,
            activityLaunchExecutor = launchExecutor
        )

        assertFalse(launched)
    }

    private fun stubLaunchableActivity(
        launchIntent: Intent,
        activityInfo: ActivityInfo = createActivityInfo(enabled = true, exported = true)
    ) {
        every { packageManager.getLaunchIntentForPackage(PACKAGE_NAME) } returns launchIntent
        every {
            launchIntent.resolveActivityInfo(packageManager, PackageManager.MATCH_DEFAULT_ONLY)
        } returns activityInfo
    }

    private fun createActivityInfo(enabled: Boolean, exported: Boolean): ActivityInfo {
        return ActivityInfo().apply {
            this.enabled = enabled
            this.exported = exported
        }
    }

    private class RecordingLaunchExecutor(
        private val failure: Throwable? = null
    ) : CoverAppLauncher.ActivityLaunchExecutor {

        var lastLaunchDisplayId: Int? = null

        override fun launch(context: Context, launchIntent: Intent, launchDisplayId: Int) {
            failure?.let { throw it }
            lastLaunchDisplayId = launchDisplayId
        }
    }
}




