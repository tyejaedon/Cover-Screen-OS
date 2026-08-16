package com.tyejaedon.coverscreenos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

    val ClockText = TextStyle(
        fontSize = 56.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.5).sp,
        lineHeight = 58.sp
    )

    val DateText = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    )

    val AppLabelText = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    )

    val SectionLabelText = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp
    )
}