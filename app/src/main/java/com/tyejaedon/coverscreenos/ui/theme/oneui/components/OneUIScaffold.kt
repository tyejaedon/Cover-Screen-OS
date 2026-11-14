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
import androidx.compose.ui.platform.LocalDensity
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
    expandedHeight: Dp = 180.dp,
    collapsedHeight: Dp = 56.dp
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
 * Implements the 40/60 Viewing-to-Interaction split:
 * - Top 35%–40% Viewing Area: displays an expanded 34.sp title, subtitle/status, and generous negative space.
 * - Bottom 60%–65% Interaction Area: actionable thumb-friendly controls constrained to max 680dp width.
 * - Scroll-linked collapsing header: smoothly interpolates expanded 34.sp title into compact 56.dp pinned top bar.
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
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val currentHeightDp = with(density) { headerState.currentHeightPx.toDp() }
    val fraction = headerState.collapseFraction

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(OneUITheme.colors.canvasBackground)
            .nestedScroll(headerState.nestedScrollConnection)
    ) {
        val isWide = maxWidth >= 600.dp
        val horizontalPadding = if (isWide) OneUITheme.dimensions.screenPaddingExpanded else OneUITheme.dimensions.screenPaddingCompact

        // Content / Interaction Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = currentHeightDp + statusBarPadding.calculateTopPadding()),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = OneUITheme.dimensions.maxCardWidth)
                    .padding(horizontal = horizontalPadding)
            ) {
                content(
                    PaddingValues(
                        bottom = if (floatingNavBar != null) OneUITheme.dimensions.floatingNavHeight + 32.dp else 16.dp
                    )
                )
            }
        }

        // Viewing Area / Collapsible Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(currentHeightDp + statusBarPadding.calculateTopPadding())
                .background(
                    if (fraction > 0.85f) OneUITheme.colors.cardSurface else OneUITheme.colors.canvasBackground
                )
                .padding(top = statusBarPadding.calculateTopPadding()),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = OneUITheme.dimensions.maxCardWidth)
            ) {
                // Collapsed Compact Top Bar (56dp, 20sp SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (navigationIcon != null) {
                        navigationIcon()
                        Spacer(modifier = Modifier.height(1.dp).padding(end = 12.dp))
                    }
                    Text(
                        text = title,
                        style = OneUITheme.typography.titleLarge,
                        color = OneUITheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .alpha(fraction)
                    )
                    actions()
                }

                // Expanded Viewing Area (34sp Light, Subtitle, Negative Space)
                if (fraction < 0.95f) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = horizontalPadding, bottom = 20.dp, end = horizontalPadding)
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

        // Bottom Anchored Now Pill (e.g. background service status, fold posture, live task)
        if (nowPill != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (floatingNavBar != null) OneUITheme.dimensions.floatingNavHeight + 36.dp else 24.dp)
            ) {
                nowPill()
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
