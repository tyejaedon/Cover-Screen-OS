package com.tyejaedon.coverscreenos.ui

import com.tyejaedon.coverscreenos.ui.launcher.rememberPackageIconBitmap
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCornerRadiusSmall
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTextStyles
import com.tyejaedon.coverscreenos.ui.theme.coverBackdropBlur
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget
import com.tyejaedon.coverscreenos.ui.theme.coverScreenContentPadding
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val GRID_COLUMNS = 4
private const val GRID_ROWS = 3
private const val APPS_PER_GRID_PAGE = GRID_COLUMNS * GRID_ROWS
private const val DOCK_SLOT_COUNT = 4
private const val INDICATOR_SCRUB_SENSITIVITY = 1.45f
private const val INDICATOR_SCRUB_INTERVAL_MS = 42L

private val GRID_TILE_GAP = 6.dp
private val GRID_CONTENT_PADDING = coverScreenContentPadding(horizontal = 6.dp, vertical = 2.dp)

@Composable
internal fun AppGridPageTile(
    apps: List<AppModel>,
    deferHydration: Boolean,
    emptyStateLabel: String = "No launchable apps",
    onAppSelected: (AppModel) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    if (deferHydration) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = modifier,
            contentPadding = GRID_CONTENT_PADDING,
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(GRID_TILE_GAP),
            verticalArrangement = Arrangement.spacedBy(GRID_TILE_GAP)
        ) {
            repeat(APPS_PER_GRID_PAGE) {
                item { AppGridPlaceholderTile() }
            }
        }
        return
    }

    if (apps.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = emptyStateLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        return
    }

    val displaySlots = remember(apps) {
        val placeholderCount = (APPS_PER_GRID_PAGE - apps.size).coerceAtLeast(0)
        apps.map { it as AppModel? } + List(placeholderCount) { null }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = modifier,
        contentPadding = GRID_CONTENT_PADDING,
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(GRID_TILE_GAP),
        verticalArrangement = Arrangement.spacedBy(GRID_TILE_GAP)
    ) {
        itemsIndexed(
            items = displaySlots,
            key = { index, app -> app?.packageName ?: "placeholder_$index" }
        ) { _, app ->
            if (app == null) {
                AppGridPlaceholderTile()
            } else {
                AppGridTile(
                    app = app,
                    onClick = { onAppSelected(app) },
                    hazeState = hazeState
                )
            }
        }
    }
}

@Composable
internal fun GridPageLetterTooltip(letter: String?, visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible || letter == null) return
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(com.tyejaedon.coverscreenos.ui.theme.CoverOSPrimary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.Black,
            maxLines = 1
        )
    }
}

internal fun gridPageStartLetterForPagerPage(
    pagerPageIndex: Int,
    appPages: List<List<AppModel>>,
    firstAppGridPageIndex: Int
): String? {
    val appPageIndex = pagerPageIndex - firstAppGridPageIndex
    if (appPageIndex < 0) return null
    val firstAppName = appPages.getOrNull(appPageIndex)?.firstOrNull()?.name?.trim().orEmpty()
    if (firstAppName.isEmpty()) return "#"
    val firstChar = firstAppName.first()
    return if (firstChar.isLetter()) firstChar.uppercaseChar().toString() else firstChar.toString()
}

@Composable
internal fun PageIndicator(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier,
    onScrubActiveChanged: (Boolean) -> Unit = {}
) {
    if (pageCount <= 1) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val latestOnScrubActiveChanged by androidx.compose.runtime.rememberUpdatedState(onScrubActiveChanged)

    var isDragging by remember { mutableStateOf(false) }
    var indicatorWidthPx by remember { mutableIntStateOf(0) }
    var lastScrubbedPage by remember { androidx.compose.runtime.mutableIntStateOf(pagerState.currentPage) }
    var lastScrubTimestampMs by remember { mutableLongStateOf(0L) }

    val dotDiameter = 5.dp
    val dotSpacing = 6.dp
    val hitAreaHorizontalPadding = 14.dp
    val hitAreaVerticalPadding = 8.dp

    fun requestPageScroll(targetPage: Int) {
        if (targetPage != pagerState.currentPage) {
            scope.launch { pagerState.scrollToPage(targetPage) }
        }
    }

    val currentProgress = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, (pageCount - 1).toFloat())
    val activePillWidth = if (isDragging) 18.dp else 14.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .height(20.dp)
                .onSizeChanged {
                    indicatorWidthPx = (it.width - (hitAreaHorizontalPadding.value * 2 * density.density).roundToInt()).coerceAtLeast(0)
                }
                .pointerInput(pageCount) {
                    detectTapGestures(
                        onPress = { offset ->
                            latestOnScrubActiveChanged(true)
                            if (indicatorWidthPx > 0) {
                                val localX = offset.x - with(density) { hitAreaHorizontalPadding.toPx() }
                                val fraction = (localX / indicatorWidthPx).coerceIn(0f, 1f)
                                val targetPage = (fraction * (pageCount - 1)).roundToInt()
                                requestPageScroll(targetPage)
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            // tryAwaitRelease() returns true if the user actually released (a real
                            // tap) and false if the gesture was cancelled — which happens when the
                            // sibling horizontal-drag gesture takes over the pointer. In that case
                            // the drag gesture's onDragEnd/onDragCancel is responsible for the
                            // scrub=false transition, so do NOT emit false here (otherwise we get
                            // a transient true→false→true blip that trips the tooltip hide delay
                            // and makes the grid letter disappear mid-drag).
                            val released = tryAwaitRelease()
                            if (released) {
                                latestOnScrubActiveChanged(false)
                            }
                        }
                    )
                }
                .pointerInput(pageCount) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            latestOnScrubActiveChanged(true)
                            lastScrubbedPage = pagerState.currentPage
                            if (indicatorWidthPx > 0) {
                                val localX = offset.x - with(density) { hitAreaHorizontalPadding.toPx() }
                                val fraction = (localX / indicatorWidthPx).coerceIn(0f, 1f)
                                val targetPage = (fraction * (pageCount - 1)).roundToInt()
                                if (targetPage != lastScrubbedPage) {
                                    lastScrubbedPage = targetPage
                                    requestPageScroll(targetPage)
                                }
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val now = SystemClock.uptimeMillis()
                            if ((now - lastScrubTimestampMs) < INDICATOR_SCRUB_INTERVAL_MS) return@detectHorizontalDragGestures

                            if (indicatorWidthPx > 0) {
                                val localX = change.position.x - with(density) { hitAreaHorizontalPadding.toPx() }
                                val baseFraction = (localX / indicatorWidthPx).coerceIn(0f, 1f)
                                val centered = baseFraction - 0.5f
                                val adjustedFraction = ((centered * INDICATOR_SCRUB_SENSITIVITY) + 0.5f).coerceIn(0f, 1f)
                                val targetPage = (adjustedFraction * (pageCount - 1)).roundToInt()

                                if (targetPage != lastScrubbedPage) {
                                    lastScrubbedPage = targetPage
                                    lastScrubTimestampMs = now
                                    requestPageScroll(targetPage)
                                }
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            latestOnScrubActiveChanged(false)
                        },
                        onDragCancel = {
                            isDragging = false
                            latestOnScrubActiveChanged(false)
                        }
                    )
                }
                .padding(horizontal = hitAreaHorizontalPadding, vertical = hitAreaVerticalPadding),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dotSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) {
                    Box(
                        modifier = Modifier
                            .size(dotDiameter)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                }
            }

            val stepDistancePx = with(density) { (dotDiameter + dotSpacing).toPx() }
            val activePillWidthPx = with(density) { activePillWidth.toPx() }
            val dotDiameterPx = with(density) { dotDiameter.toPx() }
            val pillOffsetPx = (currentProgress * stepDistancePx) - ((activePillWidthPx - dotDiameterPx) / 2f)

            Box(
                modifier = Modifier
                    .offset { IntOffset(x = pillOffsetPx.roundToInt(), y = 0) }
                    .width(activePillWidth)
                    .height(dotDiameter)
                    .clip(RoundedCornerShape(3.dp))
                    .background(com.tyejaedon.coverscreenos.ui.theme.CoverOSPrimary)
            )
        }
    }
}

@Composable
internal fun CoverDockRow(
    dockSlots: List<AppModel?>,
    onAppSelected: (AppModel) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.coverScreenPadding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(DOCK_SLOT_COUNT) { index ->
            val app = dockSlots.getOrNull(index)
            val isSlotEnabled = app != null

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .coverMinimumTouchTarget()
                    .clip(RoundedCornerShape(CoverOSCornerRadiusSmall))
                    .clickable(enabled = isSlotEnabled) { app?.let(onAppSelected) },
                contentAlignment = Alignment.Center
            ) {
                if (app != null) {
                    val iconBitmap =
                        rememberPackageIconBitmap(
                            app.packageName
                        )
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = app.name,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppGridPlaceholderTile() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .padding(top = 20.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
        )
    }
}

@Composable
private fun AppGridTile(
    app: AppModel,
    onClick: () -> Unit,
    hazeState: HazeState? = null
) {
    val hapticFeedback = LocalHapticFeedback.current
    val iconBitmap =
        rememberPackageIconBitmap(app.packageName)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .coverMinimumTouchTarget()
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .coverScreenPadding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = app.name,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            // Blurred backdrop pill behind the label so the package name stays
            // legible over any wallpaper. Falls back to no backdrop when no
            // HazeState is threaded through (e.g. isolated previews).
            val labelBackdrop = if (hazeState != null) {
                Modifier.coverBackdropBlur(
                    hazeState = hazeState,
                    shape = RoundedCornerShape(10.dp),
                    tint = Color.Black.copy(alpha = 0.42f),
                    blurRadius = 18.dp
                )
            } else {
                Modifier
            }

            Text(
                text = app.name,
                style = CoverOSTextStyles.AppLabelText,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .wrapContentWidth()
                    .then(labelBackdrop)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

