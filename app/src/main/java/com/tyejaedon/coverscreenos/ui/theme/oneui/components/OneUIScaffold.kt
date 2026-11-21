package com.tyejaedon.coverscreenos.ui.theme.oneui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUITheme

/**
 * State tracking scroll-linked collapse progress between the expanded One UI Viewing Area
 * (35-40% viewport, 34sp Light) and the compact pinned top bar (56dp, 20sp SemiBold).
 */
@Stable
class OneUICollapsingHeaderState(
    val maxExpandedHeightPx: Float,
    val minCollapsedHeightPx: Float
) {
    var scrollOffset by mutableFloatStateOf(0f)
        private set

    val collapseRangePx: Float
        get() = (maxExpandedHeightPx - minCollapsedHeightPx).coerceAtLeast(1f)

    val collapseFraction: Float
        get() = (scrollOffset / collapseRangePx).coerceIn(0f, 1f)

    val currentHeightPx: Float
        get() = (maxExpandedHeightPx - scrollOffset).coerceIn(minCollapsedHeightPx, maxExpandedHeightPx)

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = -available.y
            val previousOffset = scrollOffset
            scrollOffset = (scrollOffset + delta).coerceIn(0f, collapseRangePx)
            val consumed = scrollOffset - previousOffset
            return Offset(0f, -consumed)
        }
    }
}

@Composable
fun rememberOneUICollapsingHeaderState(
    expandedHeight: Dp = if (LocalConfiguration.current.screenHeightDp <= 480) 52.dp else 180.dp,
    collapsedHeight: Dp = if (LocalConfiguration.current.screenHeightDp <= 480) 48.dp else 56.dp
): OneUICollapsingHeaderState {
    val density = LocalDensity.current
    val expandedPx = with(density) { expandedHeight.toPx() }
    val collapsedPx = with(density) { collapsedHeight.toPx() }
    return remember(expandedPx, collapsedPx) {
        OneUICollapsingHeaderState(
            maxExpandedHeightPx = expandedPx,
            minCollapsedHeightPx = collapsedPx
        )
    }
}

/**
 * Signature Samsung One UI 8 Screen Scaffold.
 *
 * Implements the 40/60 Viewing-to-Interaction split on standard displays, and automatically
 * switches to a compact 52dp header on compact cover displays (<= 480dp height) so content
 * remains immediately accessible.
 */
@Composable
fun OneUIScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingNavBar: (@Composable () -> Unit)? = null,
    nowPill: (@Composable () -> Unit)? = null,
    headerState: OneUICollapsingHeaderState = rememberOneUICollapsingHeaderState(),
    content: @Composable (PaddingValues) -> Unit
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val cutoutPadding = WindowInsets.displayCutout.asPaddingValues()
    val topInset = maxOf(statusBarPadding.calculateTopPadding(), cutoutPadding.calculateTopPadding())
    val cutoutStart = cutoutPadding.calculateStartPadding(layoutDirection)
    val cutoutEnd = cutoutPadding.calculateEndPadding(layoutDirection)
    val currentHeightDp = with(density) { headerState.currentHeightPx.toDp() }
    val fraction = headerState.collapseFraction

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(OneUITheme.colors.canvasBackground)
            .nestedScroll(headerState.nestedScrollConnection)
    ) {
        val isWide = maxWidth >= 600.dp
        val isCompactHeight = maxHeight <= 480.dp
        val showFloatingNowPill = nowPill != null && !isCompactHeight
        val baseHorizontalPadding = if (isWide) OneUITheme.dimensions.screenPaddingExpanded else if (isCompactHeight) 12.dp else OneUITheme.dimensions.screenPaddingCompact
        val startPadding = maxOf(baseHorizontalPadding, cutoutStart)
        val endPadding = maxOf(baseHorizontalPadding, cutoutEnd)
        val effectiveTopBarHeight = if (isCompactHeight) 52.dp else currentHeightDp

        // Content / Interaction Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = effectiveTopBarHeight + topInset),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = OneUITheme.dimensions.maxCardWidth)
                    .padding(start = startPadding, end = endPadding)
            ) {
                val bottomContentPadding = when {
                    floatingNavBar != null && showFloatingNowPill -> OneUITheme.dimensions.floatingNavHeight + OneUITheme.dimensions.nowPillHeight + 48.dp
                    floatingNavBar != null -> OneUITheme.dimensions.floatingNavHeight + 32.dp
                    showFloatingNowPill -> OneUITheme.dimensions.nowPillHeight + 32.dp
                    else -> 12.dp
                }
                content(
                    PaddingValues(
                        bottom = bottomContentPadding
                    )
                )
            }
        }

        // Viewing Area / Collapsible Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(effectiveTopBarHeight + topInset)
                .background(
                    if (isCompactHeight || fraction > 0.85f) OneUITheme.colors.cardSurface else OneUITheme.colors.canvasBackground
                )
                .padding(top = topInset),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = OneUITheme.dimensions.maxCardWidth)
            ) {
                // Collapsed Compact Top Bar (52dp on compact cover, 56dp on standard)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isCompactHeight) 52.dp else 56.dp)
                        .padding(start = startPadding, end = endPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (navigationIcon != null) {
                        navigationIcon()
                        Spacer(modifier = Modifier.height(1.dp).padding(end = 8.dp))
                    }
                    Text(
                        text = title,
                        style = OneUITheme.typography.titleLarge,
                        color = OneUITheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .alpha(if (isCompactHeight) 1f else fraction)
                    )
                    actions()
                }

                // Expanded Viewing Area (34sp Light, Subtitle, Negative Space) - hidden on compact cover height
                if (!isCompactHeight && fraction < 0.95f) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = startPadding, bottom = 20.dp, end = endPadding)
                            .alpha(1f - fraction)
                    ) {
                        Text(
                            text = title,
                            style = OneUITheme.typography.displayLarge,
                            color = OneUITheme.colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                style = OneUITheme.typography.bodyMedium,
                                color = OneUITheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // Bottom Anchored Now Pill (hidden on compact cover screens to avoid overlapping cards)
        if (showFloatingNowPill) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (floatingNavBar != null) OneUITheme.dimensions.floatingNavHeight + 36.dp else 24.dp)
            ) {
                nowPill?.invoke()
            }
        }

        // Floating Navigation Bar (pill suspended 16dp above navigation bar inset)
        if (floatingNavBar != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 16.dp)
            ) {
                floatingNavBar()
            }
        }
    }
}

@Preview(name = "Light Mode", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "OLED Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun OneUIScaffoldPreview() {
    OneUITheme {
        OneUIScaffold(
            title = "Cover Screen OS",
            subtitle = "Clamshell Outer Display Launcher"
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Interaction Area Content", style = OneUITheme.typography.bodyLarge)
            }
        }
    }
}
