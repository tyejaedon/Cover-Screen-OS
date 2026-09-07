package com.tyejaedon.coverscreenos.services.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayAttachmentPolicyTest {

    private val policy = OverlayAttachmentPolicy(defaultDisplayId = 0)

    @Test
    fun `hold suppressed when attached to a valid non-default display`() {
        val shouldHold = policy.shouldHoldSuppressedOnCurrentDisplay(
            isOverlayAttached = true,
            activeDisplayId = 1,
            isActiveDisplayStillValid = true
        )

        assertTrue(shouldHold)
    }

    @Test
    fun `do not hold suppressed when active display is default`() {
        val shouldHold = policy.shouldHoldSuppressedOnCurrentDisplay(
            isOverlayAttached = true,
            activeDisplayId = 0,
            isActiveDisplayStillValid = true
        )

        assertFalse(shouldHold)
    }

    @Test
    fun `do not hold suppressed when active display is no longer valid`() {
        val shouldHold = policy.shouldHoldSuppressedOnCurrentDisplay(
            isOverlayAttached = true,
            activeDisplayId = 3,
            isActiveDisplayStillValid = false
        )

        assertFalse(shouldHold)
    }

    @Test
    fun `do not hold suppressed when overlay is not attached`() {
        val shouldHold = policy.shouldHoldSuppressedOnCurrentDisplay(
            isOverlayAttached = false,
            activeDisplayId = 3,
            isActiveDisplayStillValid = true
        )

        assertFalse(shouldHold)
    }

    @Test
    fun `do not hold suppressed when display id is unknown`() {
        val shouldHold = policy.shouldHoldSuppressedOnCurrentDisplay(
            isOverlayAttached = true,
            activeDisplayId = null,
            isActiveDisplayStillValid = true
        )

        assertFalse(shouldHold)
    }
}

