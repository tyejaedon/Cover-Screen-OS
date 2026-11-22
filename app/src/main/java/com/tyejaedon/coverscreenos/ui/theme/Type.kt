package com.tyejaedon.coverscreenos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Holds the default Material 3 [Typography] table wired into `MaterialTheme` by [CoverOSTheme].
 * Cover-screen-specific typography uses the custom presets inside [CoverOSTextStyles].
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

    /** Holds the large, thin-weight (`42.sp`, Light) clock readout style with tightened letter spacing so big numerals look cohesive. */
    val ClockText = TextStyle(
        fontSize = 42.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.5).sp,
        lineHeight = 58.sp
    )

    /** Holds the compact (`14.sp`, Medium) date line style placed directly beneath the cover clock. */
    val DateText = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    )

    /** Holds the small (`11.sp`, Medium) label style sized to keep app names crisp under compact launcher icons. */
    val AppLabelText = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    )

    /** Holds the spaced-out (`12.sp`, SemiBold) section-heading style used to label grouped rows on small displays. */
    val SectionLabelText = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp
    )
}