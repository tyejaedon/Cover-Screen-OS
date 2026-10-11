package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import com.tyejaedon.coverscreenos.datastore.HapticStrength
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase B: unifies haptic feedback. Pins [HapticTier.scaledBy] for all four
 * [HapticStrength] settings so a "Haptic strength" preference change produces
 * the documented effect (Off disables, Light caps, Standard is a no-op,
 * Strong bumps each tier up one notch).
 */
class HapticTierScalingTest {

    @Test fun `Off strength silences every tier including None`() {
        HapticTier.entries.forEach { tier ->
            assertEquals(HapticTier.None, tier.scaledBy(HapticStrength.Off))
        }
    }

    @Test fun `Light strength caps every non-None tier at Light`() {
        assertEquals(HapticTier.None, HapticTier.None.scaledBy(HapticStrength.Light))
        assertEquals(HapticTier.Light, HapticTier.Light.scaledBy(HapticStrength.Light))
        assertEquals(HapticTier.Light, HapticTier.Standard.scaledBy(HapticStrength.Light))
        assertEquals(HapticTier.Light, HapticTier.Strong.scaledBy(HapticStrength.Light))
    }

    @Test fun `Standard strength is a no-op, tiers fire as resolved`() {
        HapticTier.entries.forEach { tier ->
            assertEquals(tier, tier.scaledBy(HapticStrength.Standard))
        }
    }

    @Test fun `Strong strength bumps each tier up one notch, Strong stays Strong`() {
        assertEquals(HapticTier.None, HapticTier.None.scaledBy(HapticStrength.Strong))
        assertEquals(HapticTier.Standard, HapticTier.Light.scaledBy(HapticStrength.Strong))
        assertEquals(HapticTier.Strong, HapticTier.Standard.scaledBy(HapticStrength.Strong))
        assertEquals(HapticTier.Strong, HapticTier.Strong.scaledBy(HapticStrength.Strong))
    }
}
