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

internal data class CoverDisplayPolishSpec(
    val statusChipMinHeight: Dp,
    val dockBottomPadding: Dp,
    val cutoutTopPadding: Dp = 0.dp,
    val cutoutBottomPadding: Dp = 0.dp,
    val cutoutStartPadding: Dp = 0.dp,
    val cutoutEndPadding: Dp = 0.dp
)

@Composable
internal fun rememberCoverDisplayPolishSpec(): CoverDisplayPolishSpec {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val containerHeightPx = LocalWindowInfo.current.containerSize.height
    val containerHeightDp = with(density) { containerHeightPx.toDp() }

    val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()
    val cutoutTop = cutoutInsets.calculateTopPadding()
    val cutoutBottom = cutoutInsets.calculateBottomPadding()
    val cutoutStart = cutoutInsets.calculateStartPadding(layoutDirection)
    val cutoutEnd = cutoutInsets.calculateEndPadding(layoutDirection)

    return remember(containerHeightPx, cutoutTop, cutoutBottom, cutoutStart, cutoutEnd) {
        when {
            containerHeightDp <= 680.dp -> CoverDisplayPolishSpec(
                statusChipMinHeight = 28.dp,
                dockBottomPadding = 8.dp + cutoutBottom,
                cutoutTopPadding = cutoutTop,
                cutoutBottomPadding = cutoutBottom,
                cutoutStartPadding = cutoutStart,
                cutoutEndPadding = cutoutEnd
            )
            containerHeightDp <= 760.dp -> CoverDisplayPolishSpec(
                statusChipMinHeight = 30.dp,
                dockBottomPadding = 10.dp + cutoutBottom,
                cutoutTopPadding = cutoutTop,
                cutoutBottomPadding = cutoutBottom,
                cutoutStartPadding = cutoutStart,
                cutoutEndPadding = cutoutEnd
            )
            else -> CoverDisplayPolishSpec(
                statusChipMinHeight = 32.dp,
                dockBottomPadding = 12.dp + cutoutBottom,
                cutoutTopPadding = cutoutTop,
                cutoutBottomPadding = cutoutBottom,
                cutoutStartPadding = cutoutStart,
                cutoutEndPadding = cutoutEnd
            )
        }
    }
}
