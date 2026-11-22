package com.tyejaedon.coverscreenos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Base Material 3 [Typography] instance supplied to [androidx.compose.material3.MaterialTheme]
 * by [CoverOSTheme]. Cover-specific visual hierarchy uses [CoverOSTextStyles].
 */
val CoverOSTypography = Typography()

/**
 * Cover-screen type scale.
 *
 * One UI's lock/cover typography leans on scale and weight contrast rather
 * than color or decoration: an oversized, light-weight clock; a small,
 * medium-weight date sitting close beneath it; and compact, slightly
 * heavier labels under app icons so they stay legible at cover-screen size.
 */
object CoverOSTextStyles {

    /** Oversized light-weight clock readout with tight negative tracking (`-0.5.sp`) for large numerals. */
    val ClockText = TextStyle(
        fontSize = 42.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.5).sp,
        lineHeight = 58.sp
    )

    /** Compact medium-weight date subtitle anchored directly below the cover clock. */
    val DateText = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    )

    /** Small launcher icon label sized for tight multi-column cover grids while remaining legible. */
    val AppLabelText = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    )

    /** Semi-bold section header with expanded tracking (`0.6.sp`) for group labels on compact screens. */
    val SectionLabelText = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp
    )
}