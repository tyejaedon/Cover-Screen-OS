package com.tyejaedon.coverscreenos.ui.appshell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeTourTest {
    @Test
    fun `tour waits for stored flag and does not cover deep links`() {
        assertFalse(shouldShowWelcomeTour(null, deepLinkLaunch = false, dismissed = false))
        assertTrue(shouldShowWelcomeTour(false, deepLinkLaunch = false, dismissed = false))
        assertFalse(shouldShowWelcomeTour(false, deepLinkLaunch = true, dismissed = false))
        assertFalse(shouldShowWelcomeTour(false, deepLinkLaunch = false, dismissed = true))
        assertFalse(shouldShowWelcomeTour(true, deepLinkLaunch = false, dismissed = false))
    }
}
