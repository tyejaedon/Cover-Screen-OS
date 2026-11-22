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
    /** Accumulated vertical collapse distance in pixels (`0f` = fully expanded, `collapseRangePx` = fully collapsed). */
    var scrollOffset by mutableFloatStateOf(0f)
        private set

    /** Total pixel travel between expanded and collapsed header states (clamped >= 1f to prevent /0). */
    val collapseRangePx: Float
        get() = (maxExpandedHeightPx - minCollapsedHeightPx).coerceAtLeast(1f)

    /** Normalized progress in `0f..1f` (`0f` = fully expanded viewing area, `1f` = pinned bar). */
    val collapseFraction: Float
        get() = (scrollOffset / collapseRangePx).coerceIn(0f, 1f)

    /** Interpolated header height in pixels driven by [scrollOffset]. */
    val currentHeightPx: Float
        get() = (maxExpandedHeightPx - scrollOffset).coerceIn(minCollapsedHeightPx, maxExpandedHeightPx)

    /**
     * [NestedScrollConnection] attached to the scaffold root so upward/downward scroll gestures
     * collapse or expand the Viewing Area before child scrollable lists consume remaining delta.
     */
    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Upward finger drag yields negative available.y, so negate it to grow scrollOffset.
            val delta = -available.y
            // Snapshot of scrollOffset before clamping so we know exactly how many pixels the header absorbed.
            val previousOffset = scrollOffset
            scrollOffset = (scrollOffset + delta).coerceIn(0f, collapseRangePx)
            // Exact vertical pixels consumed by collapsing/expanding the header; leftover pixels flow to the child list.
            val consumed = scrollOffset - previousOffset
            return Offset(0f, -consumed)
        }
    }
}

/**
 * Remembers a [OneUICollapsingHeaderState] converted to pixels via [LocalDensity],
 * using compact cover-display header bounds when `screenHeightDp <= 480`.
 */
@Composable
fun rememberOneUICollapsingHeaderState(
    expandedHeight: Dp = if (LocalConfiguration.current.screenHeightDp <= 480) 52.dp else 180.dp,
    collapsedHeight: Dp = if (LocalConfiguration.current.screenHeightDp <= 480) 48.dp else 56.dp
): OneUICollapsingHeaderState {
    // Screen density multiplier used to convert Dp header heights into raw scroll pixels.
    val density = LocalDensity.current
    // Expanded Viewing Area height in raw pixels.
    val expandedPx = with(density) { expandedHeight.toPx() }
    // Pinned compact toolbar height in raw pixels.
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
    // Pixel density used to convert headerState's pixel height back into Compose Dp layout units.
    val density = LocalDensity.current
    // Active LTR/RTL reading direction so cutout start/end padding maps to the proper physical screen side.
    val layoutDirection = LocalLayoutDirection.current
    // System status bar inset measurements keeping the header below clock/battery icons.
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    // Hardware camera-hole / hinge cutout measurements avoiding clipped text on irregular screens.
    val cutoutPadding = WindowInsets.displayCutout.asPaddingValues()
    // Effective top clearance picking whichever inset (status bar or display cutout) is taller.
    val topInset = maxOf(statusBarPadding.calculateTopPadding(), cutoutPadding.calculateTopPadding())
    // Horizontal cutout clearance on the leading edge (left in LTR, right in RTL).
    val cutoutStart = cutoutPadding.calculateStartPadding(layoutDirection)
    // Horizontal cutout clearance on the trailing edge (right in LTR, left in RTL).
    val cutoutEnd = cutoutPadding.calculateEndPadding(layoutDirection)
    // Live collapsible header height converted from pixels to Dp for layout modifiers.
    val currentHeightDp = with(density) { headerState.currentHeightPx.toDp() }
    // Shorthand for `0f..1f` header collapse progress driving title alpha cross-fades.
    val fraction = headerState.collapseFraction

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(OneUITheme.colors.canvasBackground)
            .nestedScroll(headerState.nestedScrollConnection)
    ) {
        // True on tablets or unfolded inner screens (>= 600dp wide) needing wider outer margins.
        val isWide = maxWidth >= 600.dp
        // True on short outer cover screens (<= 480dp tall) where the big header collapses permanently.
        val isCompactHeight = maxHeight <= 480.dp
        // Hides the floating Now Pill on compact cover screens so it never blocks cards in tight vertical space.
        val showFloatingNowPill = nowPill != null && !isCompactHeight
        // Baseline left/right page padding selected for wide, compact-cover, or standard phone sizes.
        val baseHorizontalPadding = if (isWide) OneUITheme.dimensions.screenPaddingExpanded else if (isCompactHeight) 12.dp else OneUITheme.dimensions.screenPaddingCompact
        // Final leading margin ensuring content clears both screen padding and any side display cutout.
        val startPadding = maxOf(baseHorizontalPadding, cutoutStart)
        // Final trailing margin ensuring content clears both screen padding and any side display cutout.
        val endPadding = maxOf(baseHorizontalPadding, cutoutEnd)
        // Fixed 52dp top bar on compact cover displays, or smoothly collapsing height on standard displays.
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
                    // Constrain maximum card column width on wide screens while filling narrow displays.
                    .widthIn(max = OneUITheme.dimensions.maxCardWidth)
                    .padding(start = startPadding, end = endPadding)
            ) {
                // Extra bottom clearance passed to child scroll containers so the final item scrolls clear of floating bars.
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
                // Transition header surface from canvasBackground to elevated cardSurface once mostly collapsed.
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
                    // Cross-fade collapsed toolbar title in as the expanded Viewing Area collapses.
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
                    // WindowInsets.safeDrawing keeps the floating pill clear of system gesture/nav bars.
                    .padding(bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 16.dp)
            ) {
                floatingNavBar()
            }
        }
    }
}

/**
 * Dual-mode `@Preview` showcasing [OneUIScaffold]'s Viewing-to-Interaction layout
 * across Light and OLED Dark themes.
 */
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