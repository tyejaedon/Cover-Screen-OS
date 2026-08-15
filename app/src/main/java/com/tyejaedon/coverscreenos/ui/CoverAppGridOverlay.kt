package com.tyejaedon.coverscreenos.ui

import android.widget.ImageView
import android.os.SystemClock
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val GRID_COLUMNS = 4
private const val GRID_ROWS = 3
private const val APPS_PER_GRID_PAGE = GRID_COLUMNS * GRID_ROWS
private const val INDICATOR_SCRUB_SENSITIVITY = 1.45f
private const val INDICATOR_SCRUB_INTERVAL_MS = 42L
private const val INDICATOR_SCRUB_METRIC_LOG_INTERVAL_MS = 1_000L
private const val PAGER_HAPTIC_INTERVAL_MS = 150L
private const val SCRUB_METRIC_LOG_TAG = "CoverPageScrub"
private val GRID_TILE_GAP = 4.dp
private val GRID_CONTENT_PADDING = PaddingValues(horizontal = 0.dp, vertical = 0.dp)

@Composable
fun CoverAppGridOverlay(
    repository: PackageManagerAppScannerRepository,
    onAppSelected: (AppModel) -> Unit,
    isDeviceLocked: Boolean,
    modifier: Modifier = Modifier
) {
    // Scan installed applications off the main thread and expose a UI-safe list.
    val appsState = produceState(initialValue = emptyList<AppModel>(), key1 = repository) {
        value = runCatching { repository.scanInstalledApplications() }.getOrDefault(emptyList())
    }
    // Refresh time/date labels on lightweight intervals for lock-style header fidelity.
    val timeLabel by produceState(initialValue = currentCoverTime()) {
        while (true) {
            value = currentCoverTime()
            delay(30_000L.milliseconds)
        }
    }
    val dateLabel by produceState(initialValue = currentCoverDate()) {
        while (true) {
            value = currentCoverDate()
            delay(60_000L.milliseconds)
        }
    }


    val apps = appsState.value
    // Page 0 is lock+dock; remaining pages are fixed-size app grid tiles.
    val dockApps = remember(apps) { apps.take(4) }
    val appPages = remember(apps) { apps.chunked(APPS_PER_GRID_PAGE) }
    val pagerState = rememberPagerState(pageCount = { 1 + appPages.size })

    // Emit subtle haptic feedback when pager settles onto a new page.
    val hapticFeedback = LocalHapticFeedback.current
    var hasInitializedPager by remember { mutableStateOf(false) }
    var lastPagerHapticTimestampMs by remember { mutableStateOf(0L) }
    LaunchedEffect(pagerState.currentPage) {
        if (!hasInitializedPager) {
            hasInitializedPager = true
            return@LaunchedEffect
        }
        if (pagerState.pageCount > 1) {
            val now = SystemClock.uptimeMillis()
            if ((now - lastPagerHapticTimestampMs) >= PAGER_HAPTIC_INTERVAL_MS) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                lastPagerHapticTimestampMs = now
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color.Black) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                // Consume background taps so input does not leak to underlying system UI.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                InteractiveSection(
                    appPages = appPages,
                    dockApps = dockApps,
                    onAppSelected = onAppSelected,
                    isDeviceLocked = isDeviceLocked,
                    timeLabel = timeLabel,
                    dateLabel = dateLabel,
                    pagerState = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.67f)
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                )
            }
        }
    }
}



@Composable
private fun InteractiveSection(
    appPages: List<List<AppModel>>,
    dockApps: List<AppModel>,
    onAppSelected: (AppModel) -> Unit,
    isDeviceLocked: Boolean,
    timeLabel: String,
    dateLabel: String,
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    // Bottom interaction zone: horizontally paged tiles with cutout-safe bottom padding.
    Surface(
        modifier = modifier,
        color = Color.Black,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        border = BorderStroke(1.dp, Color(0xFF1C2540))
    ) {
        var showPageLetterTooltip by remember { mutableStateOf(false) }
        val maxPagerPage = (pagerState.pageCount - 1).coerceAtLeast(0)
        val hintedPagerPage = ((pagerState.currentPage + pagerState.currentPageOffsetFraction).roundToInt())
            .coerceIn(0, maxPagerPage)
        val hintedGridLetter = remember(hintedPagerPage, appPages) {
            gridPageStartLetterForPagerPage(hintedPagerPage, appPages)
        }

        LaunchedEffect(pagerState.isScrollInProgress) {
            if (pagerState.isScrollInProgress) {
                showPageLetterTooltip = true
            } else {
                delay(260)
                if (!pagerState.isScrollInProgress) {
                    showPageLetterTooltip = false
                }
            }
        }

        LaunchedEffect(pagerState.currentPage) {
            if (pagerState.currentPage == 0) return@LaunchedEffect
            showPageLetterTooltip = true
            delay(260)
            if (!pagerState.isScrollInProgress) {
                showPageLetterTooltip = false
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom))
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 10.dp,
                    beyondViewportPageCount = 1
                ) { pageIndex ->
                    // Apply subtle depth/opacity transform so horizontal swipes feel intentional.
                    val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction).absoluteValue
                    val clampedOffset = pageOffset.coerceIn(0f, 1f)
                    val scale = 1f - (clampedOffset * 0.05f)
                    val alpha = 1f - (clampedOffset * 0.18f)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            }
                    ) {
                        // First tile is lock-style surface + dock; rest are app-grid pages.
                        if (pageIndex == 0) {
                            LockAndDockTile(
                                timeLabel = timeLabel,
                                dateLabel = dateLabel,
                                isDeviceLocked = isDeviceLocked,
                                apps = dockApps,
                                onAppSelected = onAppSelected,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AppGridPageTile(
                                apps = appPages[pageIndex - 1],
                                isDeviceLocked = isDeviceLocked,
                                pageVisibility = 1f - clampedOffset,
                                onAppSelected = onAppSelected,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                GridPageLetterTooltip(
                    letter = hintedGridLetter,
                    visible = showPageLetterTooltip && hintedGridLetter != null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                )
            }

            PageIndicator(
                pagerState = pagerState,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LockAndDockTile(
    timeLabel: String,
    dateLabel: String,
    isDeviceLocked: Boolean,
    apps: List<AppModel>,
    onAppSelected: (AppModel) -> Unit,
    modifier: Modifier = Modifier
) {
    // Tile 0 prioritizes glanceable info and thumb-reachable quick-launch row.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            color = Color.Black,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF2A3556))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isDeviceLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = if (isDeviceLocked) "Locked" else "Unlocked",
                    tint = Color(0xFF8FA4FF),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCCD4FF)
                )
                Text(
                    text = if (isDeviceLocked) {
                        "Unlock your phone to launch apps"
                    } else {
                        "Swipe left for all apps"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9AAEFF)
                )
            }
        }

        CoverDockRow(
            apps = apps,
            isDeviceLocked = isDeviceLocked,
            onAppSelected = onAppSelected,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AppGridPageTile(
    apps: List<AppModel>,
    isDeviceLocked: Boolean,
    pageVisibility: Float,
    onAppSelected: (AppModel) -> Unit,
    modifier: Modifier = Modifier
) {
    // Each pager tile shows a fixed-size, non-scrollable grid for predictable touch mapping.
    if (apps.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "No launchable apps",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCCD4FF)
            )
        }
        return
    }

    // Keep a consistent 3x3 matrix across pages so spacing stays visually stable.
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
                AppGridPlaceholderTile(visibilityProgress = pageVisibility)
            } else {
                AppGridTile(
                    app = app,
                    enabled = !isDeviceLocked,
                    onClick = { onAppSelected(app) }
                )
            }
        }
    }
}

@Composable
private fun GridPageLetterTooltip(
    letter: String?,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && letter != null,
        enter = fadeIn(animationSpec = tween(durationMillis = 100)) +
            scaleIn(animationSpec = tween(durationMillis = 100), initialScale = 0.92f),
        exit = fadeOut(animationSpec = tween(durationMillis = 130)) +
            scaleOut(animationSpec = tween(durationMillis = 130), targetScale = 0.92f),
        modifier = modifier
    ) {
        Surface(
            color = Color(0xE60F1728),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF3F5B92))
        ) {
            Text(
                text = letter ?: "",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFE3EAFF),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                maxLines = 1
            )
        }
    }
}

private fun gridPageStartLetterForPagerPage(
    pagerPageIndex: Int,
    appPages: List<List<AppModel>>
): String? {
    if (pagerPageIndex <= 0) return null

    val firstAppName = appPages.getOrNull(pagerPageIndex - 1)
        ?.firstOrNull()
        ?.name
        ?.trim()
        .orEmpty()
    if (firstAppName.isEmpty()) return "#"

    val firstChar = firstAppName.first()
    return if (firstChar.isLetter()) {
        firstChar.uppercaseChar().toString()
    } else {
        firstChar.toString()
    }
}

@Composable
private fun PageIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    val pageCount = pagerState.pageCount
    if (pageCount <= 1) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    var isDragging by remember { mutableStateOf(false) }
    var indicatorWidthPx by remember(pageCount) { mutableStateOf(0) }
    var lastScrubbedPage by remember(pageCount) { mutableStateOf(pagerState.currentPage) }
    var lastScrubTimestampMs by remember(pageCount) { mutableStateOf(0L) }
    var scrubStepCount by remember(pageCount) { mutableStateOf(0) }
    var scrubStartTimestampMs by remember(pageCount) { mutableStateOf(0L) }
    var lastScrubMetricLogTimestampMs by remember(pageCount) { mutableStateOf(0L) }
    var isScrollRequestInFlight by remember(pageCount) { mutableStateOf(false) }
    var pendingScrollTargetPage by remember(pageCount) { mutableStateOf<Int?>(null) }

    // Visual dimensions
    val dotDiameter = 6.dp
    val dotSpacing = 6.dp
    val trackPaddingHorizontal = 8.dp
    val trackPaddingVertical = 5.dp

    val scrubTargetLookup by produceState(
        initialValue = IntArray(0),
        key1 = indicatorWidthPx,
        key2 = pageCount
    ) {
        if (indicatorWidthPx <= 0 || pageCount <= 1) {
            value = IntArray(0)
            return@produceState
        }

        value = withContext(Dispatchers.Default) {
            val startMs = SystemClock.uptimeMillis()
            buildScrubTargetLookup(
                indicatorWidthPx = indicatorWidthPx,
                pageCount = pageCount,
                sensitivity = INDICATOR_SCRUB_SENSITIVITY
            ).also {
                if (Log.isLoggable(SCRUB_METRIC_LOG_TAG, Log.DEBUG)) {
                    Log.d(
                        SCRUB_METRIC_LOG_TAG,
                        "lookupBuildMs=${SystemClock.uptimeMillis() - startMs} widthPx=$indicatorWidthPx pageCount=$pageCount entries=${it.size}"
                    )
                }
            }
        }
    }

    fun requestPageScroll(targetPage: Int): Boolean {
        if (targetPage == pagerState.currentPage) {
            pendingScrollTargetPage = null
            return false
        }
        if (isScrollRequestInFlight) {
            pendingScrollTargetPage = targetPage
            return false
        }

        isScrollRequestInFlight = true
        scope.launch {
            try {
                pagerState.scrollToPage(targetPage)
            } finally {
                isScrollRequestInFlight = false
                val pendingTarget = pendingScrollTargetPage
                pendingScrollTargetPage = null
                if (pendingTarget != null && pendingTarget != pagerState.currentPage) {
                    requestPageScroll(pendingTarget)
                }
            }
        }
        return true
    }

    fun dragFractionForX(x: Float): Float {
        if (indicatorWidthPx <= 0) return 0f
        val baseFraction = (x / indicatorWidthPx).coerceIn(0f, 1f)
        val centered = baseFraction - 0.5f
        return ((centered * INDICATOR_SCRUB_SENSITIVITY) + 0.5f).coerceIn(0f, 1f)
    }

    fun targetPageForX(x: Float): Int {
        if (indicatorWidthPx <= 0 || pageCount <= 1) return pagerState.currentPage

        val clampedX = x.coerceIn(0f, indicatorWidthPx.toFloat())
        val xIndex = clampedX.roundToInt().coerceIn(0, indicatorWidthPx)
        val lookup = scrubTargetLookup
        if (lookup.isNotEmpty() && xIndex < lookup.size) {
            return lookup[xIndex]
        }

        val fraction = dragFractionForX(clampedX)
        return (fraction * (pageCount - 1)).roundToInt()
    }

    fun logScrubStepRate(nowMs: Long, isFinal: Boolean = false) {
        if (!Log.isLoggable(SCRUB_METRIC_LOG_TAG, Log.DEBUG) || scrubStartTimestampMs == 0L) return
        val elapsedMs = (nowMs - scrubStartTimestampMs).coerceAtLeast(1L)
        if (!isFinal && (nowMs - lastScrubMetricLogTimestampMs) < INDICATOR_SCRUB_METRIC_LOG_INTERVAL_MS) return

        val stepsPerSecond = (scrubStepCount * 1000f) / elapsedMs
        Log.d(
            SCRUB_METRIC_LOG_TAG,
            "steps=$scrubStepCount elapsedMs=$elapsedMs stepsPerSecond=$stepsPerSecond intervalMs=$INDICATOR_SCRUB_INTERVAL_MS sensitivity=$INDICATOR_SCRUB_SENSITIVITY"
        )
        lastScrubMetricLogTimestampMs = nowMs
    }

    // Calculate current fractional progress for smooth sliding
    val currentProgress = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, (pageCount - 1).toFloat())

    // Keep the active marker responsive without bouncy spring animations.
    val activePillWidth = if (isDragging) 18.dp else 16.dp
    val activePillScale = if (isDragging) 1.05f else 1f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Enclosing Pill Track (Serves as the gesture hit-box and scrubber track)
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0F131E).copy(alpha = 0.85f))
                .border(
                    width = 1.dp,
                    color = if (isDragging) Color(0xFF4B63A8) else Color(0xFF1E2638),
                    shape = RoundedCornerShape(14.dp)
                )
                .onSizeChanged { indicatorWidthPx = it.width }
                .pointerInput(pageCount) {
                    // Tap to navigate directly to dot/segment
                    detectTapGestures { offset ->
                        if (indicatorWidthPx > 0) {
                            val fraction = (offset.x / indicatorWidthPx).coerceIn(0f, 1f)
                            val targetPage = (fraction * (pageCount - 1)).roundToInt()
                            if (requestPageScroll(targetPage)) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                }
                .pointerInput(pageCount) {
                    // Continuous drag & scrub across pages
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            lastScrubTimestampMs = 0L
                            scrubStepCount = 0
                            scrubStartTimestampMs = SystemClock.uptimeMillis()
                            lastScrubMetricLogTimestampMs = scrubStartTimestampMs
                            lastScrubbedPage = pagerState.currentPage
                            if (indicatorWidthPx > 0) {
                                val targetPage = targetPageForX(offset.x)
                                if (targetPage != lastScrubbedPage && requestPageScroll(targetPage)) {
                                    lastScrubbedPage = targetPage
                                    lastScrubTimestampMs = SystemClock.uptimeMillis()
                                    scrubStepCount += 1
                                }
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            if (pageCount <= 1) return@detectHorizontalDragGestures
                            val now = SystemClock.uptimeMillis()
                            if ((now - lastScrubTimestampMs) < INDICATOR_SCRUB_INTERVAL_MS) return@detectHorizontalDragGestures

                            if (indicatorWidthPx > 0) {
                                val targetPage = targetPageForX(change.position.x)
                                if (targetPage != lastScrubbedPage && requestPageScroll(targetPage)) {
                                    lastScrubbedPage = targetPage
                                    lastScrubTimestampMs = now
                                    scrubStepCount += 1
                                    logScrubStepRate(nowMs = now)
                                }
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            logScrubStepRate(nowMs = SystemClock.uptimeMillis(), isFinal = true)
                            scrubStartTimestampMs = 0L
                        },
                        onDragCancel = {
                            isDragging = false
                            logScrubStepRate(nowMs = SystemClock.uptimeMillis(), isFinal = true)
                            scrubStartTimestampMs = 0L
                        }
                    )
                }
                .padding(horizontal = trackPaddingHorizontal, vertical = trackPaddingVertical),
            contentAlignment = Alignment.CenterStart
        ) {
            // Background inactive dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(dotSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { index ->
                    Box(
                        modifier = Modifier
                            .size(dotDiameter)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                    )
                }
            }

            // Smooth sliding active navigator pill
            val stepDistancePx = with(density) { (dotDiameter + dotSpacing).toPx() }
            val activePillWidthPx = with(density) { activePillWidth.toPx() }
            val dotDiameterPx = with(density) { dotDiameter.toPx() }

            // Center the sliding indicator over each dot position
            val pillOffsetPx = (currentProgress * stepDistancePx) - ((activePillWidthPx - dotDiameterPx) / 2f)

            Box(
                modifier = Modifier
                    .offset { IntOffset(x = pillOffsetPx.roundToInt(), y = 0) }
                    .width(activePillWidth)
                    .height(dotDiameter)
                    .graphicsLayer {
                        scaleX = activePillScale
                        scaleY = activePillScale
                    }
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isDragging) Color(0xFFB8C8FF) else Color(0xFF9AAEFF))
            )
        }
    }
}

@Composable
private fun CoverDockRow(
    apps: List<AppModel>,
    isDeviceLocked: Boolean,
    onAppSelected: (AppModel) -> Unit,
    modifier: Modifier = Modifier
) {
    // Dock always renders four slots to preserve spatial memory even with fewer pinned apps.
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black)
            .border(1.dp, Color(0xFF2A3556), RoundedCornerShape(24.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            val app = apps.getOrNull(index)
            val isSlotEnabled = app != null && !isDeviceLocked
            val slotAlpha by animateFloatAsState(
                targetValue = if (app != null && isDeviceLocked) 0.72f else 1f,
                animationSpec = tween(durationMillis = 180),
                label = "dockSlotAlpha$index"
            )
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF121725))
                    .border(1.dp, Color(0xFF2A3556), RoundedCornerShape(18.dp))
                    .graphicsLayer { alpha = slotAlpha }
                    .clickable(enabled = isSlotEnabled) { app?.let(onAppSelected) },
                contentAlignment = Alignment.Center
            ) {
                if (app != null) {
                    AndroidView(
                        factory = { viewContext ->
                            ImageView(viewContext).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                        },
                        update = { imageView ->
                            if (imageView.tag !== app.iconDrawable) {
                                imageView.setImageDrawable(app.iconDrawable)
                                imageView.tag = app.iconDrawable
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    )

                    DisabledTapBadge(
                        visible = isDeviceLocked,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppGridPlaceholderTile(visibilityProgress: Float) {
    val clampedProgress = visibilityProgress.coerceIn(0f, 1f)
    val placeholderAlpha by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = tween(durationMillis = 140),
        label = "placeholderAlpha"
    )
    val placeholderScale by animateFloatAsState(
        targetValue = 0.97f + (0.03f * clampedProgress),
        animationSpec = tween(durationMillis = 140),
        label = "placeholderScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clip(RoundedCornerShape(20.dp))
            .graphicsLayer {
                alpha = placeholderAlpha
                scaleX = placeholderScale
                scaleY = placeholderScale
            }
            .background(Color(0xFF080B15))
            .border(1.dp, Color(0x1F42527D), RoundedCornerShape(20.dp))
    )
}

@Composable
private fun AppGridTile(
    app: AppModel,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tileAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.72f,
        animationSpec = tween(durationMillis = 180),
        label = "appGridTileAlpha"
    )

    // High-contrast tile with >=48dp interactive bounds for compact-cover accuracy.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .graphicsLayer { alpha = tileAlpha }
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF111625)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { viewContext ->
                        ImageView(viewContext).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                    },
                    update = { imageView ->
                        if (imageView.tag !== app.iconDrawable) {
                            imageView.setImageDrawable(app.iconDrawable)
                            imageView.tag = app.iconDrawable
                        }
                    },
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text = app.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) Color.White else Color(0xFFCBD2EA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

        }

        DisabledTapBadge(
            visible = !enabled,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 6.dp)
        )
    }
}

@Composable
private fun DisabledTapBadge(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 30)) +
            scaleIn(
                animationSpec = tween(durationMillis = 180, delayMillis = 30),
                initialScale = 0.9f
            ),
        exit = fadeOut(animationSpec = tween(durationMillis = 140)) +
            scaleOut(
                animationSpec = tween(durationMillis = 140),
                targetScale = 0.9f
            ),
        modifier = modifier
    ) {
        Surface(
            color = Color(0xE61A2238),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFF42527D))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TapDisabledGlyph()

            }
        }
    }
}

@Composable
private fun TapDisabledGlyph(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .border(1.dp, Color(0xFFD8E0FF), CircleShape)
        )
        Box(
            modifier = Modifier
                .width(7.dp)
                .height(1.5.dp)
                .graphicsLayer { rotationZ = -42f }
                .background(Color(0xFFD8E0FF), RoundedCornerShape(1.dp))
        )
    }
}

private fun currentCoverTime(): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    return LocalTime.now().format(formatter)
}

private fun currentCoverDate(): String {
    val formatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    return LocalDate.now().format(formatter)
}

private fun buildScrubTargetLookup(
    indicatorWidthPx: Int,
    pageCount: Int,
    sensitivity: Float
): IntArray {
    if (indicatorWidthPx <= 0 || pageCount <= 1) return IntArray(0)

    val maxPage = pageCount - 1
    return IntArray(indicatorWidthPx + 1) { xIndex ->
        val baseFraction = (xIndex.toFloat() / indicatorWidthPx).coerceIn(0f, 1f)
        val centered = baseFraction - 0.5f
        val adjustedFraction = ((centered * sensitivity) + 0.5f).coerceIn(0f, 1f)
        (adjustedFraction * maxPage).roundToInt()
    }
}

