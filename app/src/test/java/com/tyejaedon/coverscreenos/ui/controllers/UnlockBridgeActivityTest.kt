package com.tyejaedon.coverscreenos.ui.controllers

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import io.mockk.Runs
import io.mockk.just
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnlockBridgeActivityTest {

    private companion object {
        private const val PACKAGE_NAME = "com.example.target"
        private const val REQUEST_DISPLAY_ID = 7
        private const val RESOLVED_DISPLAY_ID = 42
    }

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk(relaxed = true)

        mockkObject(CoverAppLauncher)
        every { CoverAppLauncher.resolveLaunchDisplayId(any(), any()) } returns RESOLVED_DISPLAY_ID
        every {
            CoverAppLauncher.dispatchActivityStartBalExempt(any(), any(), any(), any())
        } just Runs

        UnlockBridgeActivity.resetForTesting()
    }

    @After
    fun teardown() {
        unmockkObject(CoverAppLauncher)
        UnlockBridgeActivity.resetForTesting()
    }

    @Test
    fun `startUnlockRequest dispatches via BAL-exempt channel instead of direct startActivity`() {
        val started = UnlockBridgeActivity.startUnlockRequest(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = REQUEST_DISPLAY_ID
        )

        assertTrue(started)

        // The real bug: a plain startActivity call from a Service context can be silently
        // dropped by background-activity-launch policy. Assert we never fall back to it
        // directly and instead always route through the BAL-exempt dispatcher.
        verify(exactly = 0) { context.startActivity(any()) }
        verify(exactly = 0) { context.startActivity(any(), any()) }
        verify(exactly = 1) {
            CoverAppLauncher.dispatchActivityStartBalExempt(
                context = context,
                launchIntent = any(),
                launchOptions = any(),
                launchDisplayId = RESOLVED_DISPLAY_ID
            )
        }
    }

    @Test
    fun `startUnlockRequest resolves the launch display id for the requested display`() {
        UnlockBridgeActivity.startUnlockRequest(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = REQUEST_DISPLAY_ID
        )

        verify(exactly = 1) {
            CoverAppLauncher.resolveLaunchDisplayId(context = context, displayId = REQUEST_DISPLAY_ID)
        }
    }

    @Test
    fun `startUnlockRequest passes launchDisplayId through ActivityOptions`() {
        val optionsSlot = slot<Bundle>()

        UnlockBridgeActivity.startUnlockRequest(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = REQUEST_DISPLAY_ID
        )

        verify(exactly = 1) {
            CoverAppLauncher.dispatchActivityStartBalExempt(
                context = context,
                launchIntent = any(),
                launchOptions = capture(optionsSlot),
                launchDisplayId = RESOLVED_DISPLAY_ID
            )
        }
        assertEquals(RESOLVED_DISPLAY_ID, optionsSlot.captured.getInt("android.activity.launchDisplayId"))
    }

    @Test
    fun `startUnlockRequest packages the requested package name into the bridge intent`() {
        val intentSlot = slot<Intent>()

        UnlockBridgeActivity.startUnlockRequest(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = REQUEST_DISPLAY_ID
        )

        verify(exactly = 1) {
            CoverAppLauncher.dispatchActivityStartBalExempt(
                context = context,
                launchIntent = capture(intentSlot),
                launchOptions = any(),
                launchDisplayId = RESOLVED_DISPLAY_ID
            )
        }
        assertEquals(
            PACKAGE_NAME,
            intentSlot.captured.getStringExtra("com.tyejaedon.coverscreenos.extra.UNLOCK_PACKAGE_NAME")
        )
    }

    @Test
    fun `startUnlockRequest without a package sends an unlock-only request`() {
        val intentSlot = slot<Intent>()

        val started = UnlockBridgeActivity.startUnlockRequest(
            context = context,
            packageName = null,
            displayId = REQUEST_DISPLAY_ID
        )

        assertTrue(started)
        verify(exactly = 1) {
            CoverAppLauncher.dispatchActivityStartBalExempt(
                context = context,
                launchIntent = capture(intentSlot),
                launchOptions = any(),
                launchDisplayId = RESOLVED_DISPLAY_ID
            )
        }
        assertFalse(intentSlot.captured.hasExtra("com.tyejaedon.coverscreenos.extra.UNLOCK_PACKAGE_NAME"))
    }

    @Test
    fun `startUnlockRequest skips duplicate requests already in progress`() {
        UnlockBridgeActivity.startUnlockRequest(context = context, packageName = PACKAGE_NAME, displayId = REQUEST_DISPLAY_ID)
        val secondStarted = UnlockBridgeActivity.startUnlockRequest(
            context = context,
            packageName = "com.example.other",
            displayId = REQUEST_DISPLAY_ID
        )

        assertTrue(secondStarted)
        verify(exactly = 1) {
            CoverAppLauncher.dispatchActivityStartBalExempt(any(), any(), any(), any())
        }
    }

    @Test
    fun `startUnlockRequest clears in-progress guard when dispatch throws`() {
        every {
            CoverAppLauncher.dispatchActivityStartBalExempt(any(), any(), any(), any())
        } throws RuntimeException("dispatch failed")

        val started = UnlockBridgeActivity.startUnlockRequest(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = REQUEST_DISPLAY_ID
        )

        assertTrue(!started)

        // Guard must be released so a subsequent tap can retry instead of being stuck forever.
        every {
            CoverAppLauncher.dispatchActivityStartBalExempt(any(), any(), any(), any())
        } just Runs
        val retryStarted = UnlockBridgeActivity.startUnlockRequest(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = REQUEST_DISPLAY_ID
        )
        assertTrue(retryStarted)
    }

    @Test
    fun `startUnlockRequest resets stale in-progress guard after timeout`() {
        UnlockBridgeActivity.startUnlockRequest(context = context, packageName = PACKAGE_NAME, displayId = REQUEST_DISPLAY_ID)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(16))

        val restarted = UnlockBridgeActivity.startUnlockRequest(
            context = context,
            packageName = PACKAGE_NAME,
            displayId = REQUEST_DISPLAY_ID
        )

        assertTrue(restarted)
        verify(exactly = 2) {
            CoverAppLauncher.dispatchActivityStartBalExempt(any(), any(), any(), any())
        }
    }

    @Test
    fun `already unlocked keyguard finishes bridge immediately without biometric prompt`() {
        val appContext = RuntimeEnvironment.getApplication()
        val keyguardManager = appContext.getSystemService(KeyguardManager::class.java)
        shadowOf(keyguardManager).setKeyguardLocked(false)

        val launchIntent = Intent(appContext, UnlockBridgeActivity::class.java)
        val controller = Robolectric.buildActivity(UnlockBridgeActivity::class.java, launchIntent).create()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun `biometric unavailable falls back to requestDismissKeyguard instead of aborting`() {
        val appContext = RuntimeEnvironment.getApplication()
        val keyguardManager = appContext.getSystemService(KeyguardManager::class.java)
        shadowOf(keyguardManager).setKeyguardLocked(true)

        val launchIntent = Intent(appContext, UnlockBridgeActivity::class.java)
        val controller = Robolectric.buildActivity(UnlockBridgeActivity::class.java, launchIntent).create()
        shadowOf(Looper.getMainLooper()).idle()

        // Should stay open awaiting KeyguardManager.requestDismissKeyguard callback rather than
        // aborting immediately on biometric unavailability.
        assertFalse(controller.get().isFinishing)

        controller.destroy()
    }
}
