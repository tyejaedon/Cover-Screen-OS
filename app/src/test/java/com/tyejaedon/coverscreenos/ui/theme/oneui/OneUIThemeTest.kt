package com.tyejaedon.coverscreenos.ui.theme.oneui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OneUIThemeTest {

    @Test
    fun `light mode tokens follow One UI 8 high-contrast cool gray palette`() {
        val light = OneUILightColors
        assertEquals(Color(0xFFF2F2F7), light.canvasBackground)
        assertEquals(Color(0xFFFFFFFF), light.cardSurface)
        assertEquals(Color(0xFFE5E5EA), light.cardSurfaceVariant)
        assertEquals(Color(0xFF000000), light.textPrimary)
        assertEquals(Color(0xFF6E6E73), light.textSecondary)
        assertEquals(Color(0xFF0381FE), light.accent) // Samsung Blue
        assertFalse(light.isDark)
    }

    @Test
    fun `dark mode tokens follow One UI 8 true OLED black palette`() {
        val dark = OneUIDarkColors
        assertEquals(Color(0xFF000000), dark.canvasBackground) // Pure OLED black
        assertEquals(Color(0xFF17171A), dark.cardSurface)
        assertEquals(Color(0xFF2C2C2E), dark.cardSurfaceVariant)
        assertEquals(Color(0xFFFFFFFF), dark.textPrimary)
        assertEquals(Color(0xFF8E8E93), dark.textSecondary)
        assertEquals(Color(0xFF0381FE), dark.accent)
        assertTrue(dark.isDark)
    }

    @Test
    fun `dimensions strictly enforce 8dp grid and One UI 8 geometry tokens`() {
        val dims = OneUIDimensions()
        assertEquals(8.dp, dims.gridBase)
        assertEquals(4.dp, dims.gridMicro)
        assertEquals(16.dp, dims.screenPaddingCompact)
        assertEquals(24.dp, dims.screenPaddingExpanded)
        assertEquals(24.dp, dims.cardCornerRadius)
        assertEquals(26.dp, dims.cardCornerRadiusLarge)
        assertEquals(28.dp, dims.sheetCornerRadius)
        assertEquals(680.dp, dims.maxCardWidth)
        assertEquals(60.dp, dims.floatingNavHeight)
        assertEquals(44.dp, dims.nowPillHeight)
        assertEquals(24.dp, dims.sliderHeight)
        assertEquals(40.dp, dims.encircledIconSize)
        assertEquals(16.dp, dims.cardSpacing)
        assertEquals(16.dp, dims.internalDividerIndent)
    }

    @Test
    fun `typography scale strictly adheres to One UI 8 specifications`() {
        val typo = OneUITypography()
        // Viewing Area Title: 34sp, Light (300), lineHeight 40sp, letterSpacing -0.5sp
        assertEquals(34.sp, typo.displayLarge.fontSize)
        assertEquals(FontWeight.Light, typo.displayLarge.fontWeight)
        assertEquals(40.sp, typo.displayLarge.lineHeight)
        assertEquals((-0.5).sp, typo.displayLarge.letterSpacing)

        // Collapsed Top Bar: 20sp, SemiBold (600), lineHeight 26sp
        assertEquals(20.sp, typo.titleLarge.fontSize)
        assertEquals(FontWeight.SemiBold, typo.titleLarge.fontWeight)
        assertEquals(26.sp, typo.titleLarge.lineHeight)

        // External Card Section Header: 14sp, SemiBold (600)
        assertEquals(14.sp, typo.labelMedium.fontSize)
        assertEquals(FontWeight.SemiBold, typo.labelMedium.fontWeight)

        // Primary List Title: 17sp, Medium (500), lineHeight 22sp
        assertEquals(17.sp, typo.bodyLarge.fontSize)
        assertEquals(FontWeight.Medium, typo.bodyLarge.fontWeight)
        assertEquals(22.sp, typo.bodyLarge.lineHeight)

        // Secondary List Subtitle: 13sp, Normal (400), lineHeight 18sp
        assertEquals(13.sp, typo.bodyMedium.fontSize)
        assertEquals(FontWeight.Normal, typo.bodyMedium.fontWeight)
        assertEquals(18.sp, typo.bodyMedium.lineHeight)
    }

    @Test
    fun `OneUI spring physics constants match 0_82 damping and 400 stiffness`() {
        assertEquals(0.82f, OneUISpringSpec.dampingRatio, 0.001f)
        assertEquals(400f, OneUISpringSpec.stiffness, 0.001f)
    }
}
