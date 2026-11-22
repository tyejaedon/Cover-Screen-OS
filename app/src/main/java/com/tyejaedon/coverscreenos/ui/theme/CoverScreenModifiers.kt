package com.tyejaedon.coverscreenos.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Holds the default left/right outer margin (8dp) so UI stays roomy without wasting tight cover-screen width.
private val CoverScreenHorizontalPadding = 8.dp
// Holds the default top/bottom outer margin (8dp) matching the horizontal breathing room on compact displays.
private val CoverScreenVerticalPadding = 8.dp

// Holds Android's 48dp minimum recommended tap-target size so small icons stay easy to hit with a thumb.
private val CoverScreenMinTouchTarget = 48.dp

/** One UI uses noticeably larger, "continuous" corner radii than stock Material. */
val CoverOSCornerRadiusLarge = 34.dp
/** Holds the 24dp medium continuous corner radius used for standard overlay cards and inner panels. */
val CoverOSCornerRadiusMedium = 24.dp
/** Holds the 16dp small continuous corner radius used for compact tiles, badges, and chips. */
val CoverOSCornerRadiusSmall = 16.dp

/**
 * Applies standard cover-screen outer padding around a layout element.
 *
 * Keeps spacing uniform across compact outer displays while allowing individual
 * callers to override [horizontal] or [vertical] margins when needed.
 */
fun Modifier.coverScreenPadding(
    horizontal: Dp = CoverScreenHorizontalPadding,
    vertical: Dp = CoverScreenVerticalPadding
): Modifier = this.padding(horizontal = horizontal, vertical = vertical)

/**
 * Enforces a minimum accessible hit target size ([minSize], defaulting to 48dp)
 * using [Modifier.sizeIn] without forcing smaller visual icons to stretch.
 */
fun Modifier.coverMinimumTouchTarget(minSize: Dp = CoverScreenMinTouchTarget): Modifier =
    this.sizeIn(minWidth = minSize, minHeight = minSize)

/**
 * Insets top-level cover screen content away from system status bars and hardware
 * display cutouts along the top and horizontal edges.
 *
 * Uses Compose's [WindowInsets.safeDrawing] filtered with [WindowInsetsSides.only] so
 * bottom edges remain unobstructed for overlays or scrollable content.
 */
@Composable
fun Modifier.coverTopLevelSafeInsets(): Modifier =
    this.windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    )

/**
 * Adds top padding matching the system status bar height ([WindowInsets.statusBars]
 * constrained to [WindowInsetsSides.Top]) so top navigation bars sit cleanly below
 * status icons without applying bottom or side insets.
 */
@Composable
fun Modifier.navbarPadding(): Modifier =
    this.windowInsetsPadding(
        WindowInsets.statusBars.only(WindowInsetsSides.Top)
    )

/**
 * Creates [PaddingValues] suitable for scrollable containers (`LazyColumn`, grids)
 * so list content can scroll edge-to-edge while resting with standard cover-screen margins.
 */
fun coverScreenContentPadding(
    horizontal: Dp = CoverScreenHorizontalPadding,
    vertical: Dp = CoverScreenVerticalPadding
): PaddingValues = PaddingValues(horizontal = horizontal, vertical = vertical)