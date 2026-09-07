package com.tyejaedon.coverscreenos.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class InputCapabilityDetectorTest {

    @Test
    fun `parse enabled ime ids strips subtype suffix and duplicates`() {
        val rawValue =
            "com.example.alpha/.AlphaIme;subtype=1:com.example.beta/.BetaIme:com.example.alpha/.AlphaIme"

        val parsed = parseEnabledInputMethodIds(rawValue)

        assertEquals(
            listOf("com.example.alpha/.AlphaIme", "com.example.beta/.BetaIme"),
            parsed
        )
    }

    @Test
    fun `parse enabled ime ids handles blank input`() {
        assertEquals(emptyList<String>(), parseEnabledInputMethodIds(null))
        assertEquals(emptyList<String>(), parseEnabledInputMethodIds("   "))
    }

    @Test
    fun `extract ime package name returns package segment`() {
        assertEquals("com.example.keyboard", extractImePackageName("com.example.keyboard/.SampleIme"))
        assertEquals("com.example.keyboard", extractImePackageName("com.example.keyboard"))
    }

    @Test
    fun `availability estimate reaches 100 percent for strong non samsung signals`() {
        val score = estimateInputAvailabilityPercent(
            enabledInputMethodCount = 3,
            hasDefaultInputMethod = true,
            isImePickerLikelyAvailable = true,
            isCoverT9Enabled = true,
            isCoverT9Default = true,
            hasAlternativeImeEnabled = true,
            hasSamsungOemRisk = false
        )

        assertEquals(100, score)
        assertEquals(InputAvailabilityTier.HIGH, availabilityTierForPercent(score))
    }

    @Test
    fun `availability estimate is zero with no enabled input methods`() {
        val score = estimateInputAvailabilityPercent(
            enabledInputMethodCount = 0,
            hasDefaultInputMethod = false,
            isImePickerLikelyAvailable = false,
            isCoverT9Enabled = false,
            isCoverT9Default = false,
            hasAlternativeImeEnabled = false,
            hasSamsungOemRisk = true
        )

        assertEquals(0, score)
        assertEquals(InputAvailabilityTier.LOW, availabilityTierForPercent(score))
    }

    @Test
    fun `samsung oem risk applies penalty`() {
        val scoreWithoutPenalty = estimateInputAvailabilityPercent(
            enabledInputMethodCount = 2,
            hasDefaultInputMethod = true,
            isImePickerLikelyAvailable = true,
            isCoverT9Enabled = true,
            isCoverT9Default = false,
            hasAlternativeImeEnabled = true,
            hasSamsungOemRisk = false
        )
        val scoreWithPenalty = estimateInputAvailabilityPercent(
            enabledInputMethodCount = 2,
            hasDefaultInputMethod = true,
            isImePickerLikelyAvailable = true,
            isCoverT9Enabled = true,
            isCoverT9Default = false,
            hasAlternativeImeEnabled = true,
            hasSamsungOemRisk = true
        )

        assertEquals(15, scoreWithoutPenalty - scoreWithPenalty)
        assertEquals(InputAvailabilityTier.HIGH, availabilityTierForPercent(scoreWithoutPenalty))
        assertEquals(InputAvailabilityTier.MODERATE, availabilityTierForPercent(scoreWithPenalty))
    }
}

