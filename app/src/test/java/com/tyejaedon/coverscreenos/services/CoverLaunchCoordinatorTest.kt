package com.tyejaedon.coverscreenos.services

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverLaunchCoordinatorTest {

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
    }

    @After
    fun teardown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `beginLaunch executes callback synchronously on main thread`() {
        var callbackInvoked = false

        val coordinator = CoverLaunchCoordinator(
            onBeginLaunch = {
                callbackInvoked = true
                true
            },
            onLaunchDispatched = {},
            onLaunchFailed = {},
            debugThrowOnThreadViolation = false,
            isMainThread = { true }
        )

        val started = coordinator.beginLaunch("com.example.app")

        assertTrue(started)
        assertTrue(callbackInvoked)
    }

    @Test
    fun `completeLaunch routes success and failure outcomes`() {
        var dispatchedCount = 0
        var failedCount = 0

        val coordinator = CoverLaunchCoordinator(
            onBeginLaunch = { true },
            onLaunchDispatched = { dispatchedCount += 1 },
            onLaunchFailed = { failedCount += 1 },
            debugThrowOnThreadViolation = false,
            isMainThread = { true }
        )

        coordinator.completeLaunch("com.example.app", launchDispatched = true)
        coordinator.completeLaunch("com.example.app", launchDispatched = false)

        assertEquals(1, dispatchedCount)
        assertEquals(1, failedCount)
    }

    @Test
    fun `beginLaunch fails safely off main thread in release mode`() {
        var beginCount = 0

        val coordinator = CoverLaunchCoordinator(
            onBeginLaunch = {
                beginCount += 1
                true
            },
            onLaunchDispatched = {},
            onLaunchFailed = {},
            debugThrowOnThreadViolation = false,
            isMainThread = { false }
        )

        val started = coordinator.beginLaunch("com.example.app")

        assertFalse(started)
        assertEquals(0, beginCount)
    }

    @Test
    fun `completeLaunch triggers rollback off main thread in release mode`() {
        var failedCount = 0

        val coordinator = CoverLaunchCoordinator(
            onBeginLaunch = { true },
            onLaunchDispatched = {},
            onLaunchFailed = { failedCount += 1 },
            debugThrowOnThreadViolation = false,
            isMainThread = { false }
        )

        coordinator.completeLaunch("com.example.app", launchDispatched = true)

        assertEquals(1, failedCount)
    }

    @Test
    fun `thread violations throw in debug mode`() {
        val coordinator = CoverLaunchCoordinator(
            onBeginLaunch = { true },
            onLaunchDispatched = {},
            onLaunchFailed = {},
            debugThrowOnThreadViolation = true,
            isMainThread = { false }
        )

        assertThrows(IllegalStateException::class.java) {
            coordinator.beginLaunch("com.example.app")
        }
        assertThrows(IllegalStateException::class.java) {
            coordinator.completeLaunch("com.example.app", launchDispatched = true)
        }
    }
}

