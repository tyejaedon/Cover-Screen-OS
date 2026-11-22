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

    /** Hero numeric — clock, media artist/track on the expanded player. Tight negative tracking (`-1.5.sp`) keeps large light-weight glyphs cohesive. */
    val displayHero = TextStyle(
        fontSize = 48.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-1.5).sp,
        lineHeight = 54.sp
    )

    /** Section headings — "Notifications", "Now", "Earlier", "Media". Semi-bold weight establishes clear visual anchors without extra color. */
    val sectionTitle = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    )

    /** Tile primary text — app name, notification title. One line, ellipsized. */
    val tileTitle = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium
    )

    /** Tile secondary text — subtitle, timestamp, preview text. */
    val tileSubtitle = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal
    )

    /** Chip / pill label — status pills, mode toggles. Wide tracking (`0.8.sp`) improves small-size legibility on frosted surfaces. */
    val chipLabel = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp
    )
}