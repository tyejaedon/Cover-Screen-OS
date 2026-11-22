package com.tyejaedon.coverscreenos.ui.launcher

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Display-dependent spacing and hardware cutout insets for the cover screen overlay.
 *
 * Adapts compact status chips, dock bottom spacing, and edge padding around camera
 * punch-holes or display cutouts exposed via [WindowInsets.displayCutout].
 */
internal data class CoverDisplayPolishSpec(
    val statusChipMinHeight: Dp,
    val dockBottomPadding: Dp,
    val cutoutTopPadding: Dp = 0.dp,
    val cutoutBottomPadding: Dp = 0.dp,
    val cutoutStartPadding: Dp = 0.dp,
    val cutoutEndPadding: Dp = 0.dp
)

/**
 * Computes and remembers a [CoverDisplayPolishSpec] matched to the current cover window height
 * and display cutout insets.
 *
 * Uses [LocalWindowInfo] container bounds (converted to `dp` via [LocalDensity]) together with
 * layout-direction-aware [WindowInsets.displayCutout] padding so chips and the dock remain
 * clear of camera cutouts across different clamshell cover display sizes.
 */
@Composable
internal fun rememberCoverDisplayPolishSpec(): CoverDisplayPolishSpec {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val containerHeightPx = LocalWindowInfo.current.containerSize.height
    val containerHeightDp = with(density) { containerHeightPx.toDp() }

    // Resolve directional cutout insets so overlay content clears hinge/camera obstructions.
    val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()
    val cutoutTop = cutoutInsets.calculateTopPadding()
    val cutoutBottom = cutoutInsets.calculateBottomPadding()
    val cutoutStart = cutoutInsets.calculateStartPadding(layoutDirection)
    val cutoutEnd = cutoutInsets.calculateEndPadding(layoutDirection)

    return remember(containerHeightPx, cutoutTop, cutoutBottom, cutoutStart, cutoutEnd) {
        // Bucket cover displays by height so status chips and dock padding stay proportional.
        when {
            containerHeightDp <= 680.dp -> CoverDisplayPolishSpec(
                statusChipMinHeight = 28.dp,
                dockBottomPadding = 2.dp,
                cutoutTopPadding = cutoutTop,
                cutoutBottomPadding = cutoutBottom,
                cutoutStartPadding = cutoutStart,
                cutoutEndPadding = cutoutEnd
            )
            containerHeightDp <= 760.dp -> CoverDisplayPolishSpec(
                statusChipMinHeight = 30.dp,
                dockBottomPadding = 4.dp,
                cutoutTopPadding = cutoutTop,
                cutoutBottomPadding = cutoutBottom,
                cutoutStartPadding = cutoutStart,
                cutoutEndPadding = cutoutEnd
            )
            else -> CoverDisplayPolishSpec(
                statusChipMinHeight = 32.dp,
                dockBottomPadding = 4.dp,
                cutoutTopPadding = cutoutTop,
                cutoutBottomPadding = cutoutBottom,
                cutoutStartPadding = cutoutStart,
                cutoutEndPadding = cutoutEnd
            )
        }
    }
}
