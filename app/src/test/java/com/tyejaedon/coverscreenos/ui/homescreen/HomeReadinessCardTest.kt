package com.tyejaedon.coverscreenos.ui.homescreen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.tyejaedon.coverscreenos.ui.deprecated.hasFullHomeReadiness

class HomeReadinessCardTest {
    @Test
    fun `launcher alone does not complete cover keyboard readiness`() {
        assertFalse(
            hasFullHomeReadiness(
                notificationReady = true,
                accessibilityReady = true,
                inputAccessibilityReady = false,
                notificationListenerReady = true,
                batteryOptimizationReady = true,
                serviceRunning = true
            )
        )
        assertTrue(
            hasFullHomeReadiness(
                notificationReady = true,
                accessibilityReady = true,
                inputAccessibilityReady = true,
                notificationListenerReady = true,
                batteryOptimizationReady = true,
                serviceRunning = true
            )
        )
    }
}
