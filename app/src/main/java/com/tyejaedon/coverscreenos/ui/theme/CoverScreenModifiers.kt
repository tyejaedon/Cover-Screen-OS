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

private val CoverScreenHorizontalPadding = 8.dp
private val CoverScreenVerticalPadding = 8.dp
private val CoverScreenMinTouchTarget = 48.dp

/** One UI uses noticeably larger, "continuous" corner radii than stock Material. */
val CoverOSCornerRadiusLarge = 34.dp
val CoverOSCornerRadiusMedium = 24.dp
val CoverOSCornerRadiusSmall = 16.dp

fun Modifier.coverScreenPadding(
    horizontal: Dp = CoverScreenHorizontalPadding,
    vertical: Dp = CoverScreenVerticalPadding
): Modifier = this.padding(horizontal = horizontal, vertical = vertical)

fun Modifier.coverMinimumTouchTarget(minSize: Dp = CoverScreenMinTouchTarget): Modifier =
    this.sizeIn(minWidth = minSize, minHeight = minSize)

@Composable
fun Modifier.coverTopLevelSafeInsets(): Modifier =
    this.windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    )

@Composable
fun Modifier.navbarPadding(): Modifier =
    this.windowInsetsPadding(
        WindowInsets.statusBars.only(WindowInsetsSides.Top)
    )

fun coverScreenContentPadding(
    horizontal: Dp = CoverScreenHorizontalPadding,
    vertical: Dp = CoverScreenVerticalPadding
): PaddingValues = PaddingValues(horizontal = horizontal, vertical = vertical)
