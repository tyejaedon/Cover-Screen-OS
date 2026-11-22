package com.tyejaedon.coverscreenos.ui.theme.oneui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * One UI 8 type scale (see plan section 4.3). Content-first typography: oversized headings,
 * tight leading, weight contrast carries hierarchy instead of color or decoration.
 *
 * All sizes use `sp` (scale-independent pixels) so overlay typography honors user font-scale
 * accessibility settings.
 */
object CoverOneUIType {

    /** Holds the oversized `48.sp` light-weight hero style for the cover clock and expanded media headers, with `-1.5.sp` tracking so large digits stay compact. */
    val displayHero = TextStyle(
        fontSize = 48.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-1.5).sp,
        lineHeight = 54.sp
    )

    /** Holds the `22.sp` semi-bold section-title style ("Notifications", "Now", "Media") that anchors groups using weight rather than extra colors. */
    val sectionTitle = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    )

    /** Holds the `15.sp` medium-weight primary row title style for app names and notification headings. */
    val tileTitle = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium
    )

    /** Holds the `13.sp` regular-weight secondary style for subtitles, message previews, and timestamps. */
    val tileSubtitle = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal
    )

    /** Holds the `12.sp` semi-bold pill/chip label style with wide (`0.8.sp`) letter spacing so tiny status tags stay legible over glass surfaces. */
    val chipLabel = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp
    )
}