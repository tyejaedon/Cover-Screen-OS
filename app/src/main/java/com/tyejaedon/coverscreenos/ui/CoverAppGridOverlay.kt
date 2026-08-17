@file:Suppress("FrequentlyChangingValue")

package com.tyejaedon.coverscreenos.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tyejaedon.coverscreenos.datastore.DEFAULT_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.DEFAULT_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.WallpaperScaleMode
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCornerRadiusLarge
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCornerRadiusMedium
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCornerRadiusSmall
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTextStyles
import com.tyejaedon.coverscreenos.ui.theme.coverGlassSurface
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget
import com.tyejaedon.coverscreenos.ui.theme.coverScreenContentPadding
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri

private const val GRID_COLUMNS = 4
private const val GRID_ROWS = 3
private const val APPS_PER_GRID_PAGE = GRID_COLUMNS * GRID_ROWS
private const val DOCK_SLOT_COUNT = 4
private const val INDICATOR_SCRUB_SENSITIVITY = 1.45f
private const val INDICATOR_SCRUB_INTERVAL_MS = 42L
private const val INDICATOR_SCRUB_METRIC_LOG_INTERVAL_MS = 1_000L
private const val PAGER_HAPTIC_INTERVAL_MS = 150L
private const val SCRUB_METRIC_LOG_TAG = "CoverPageScrub"
private const val WALLPAPER_LOG_TAG = "CoverWallpaper"
private const val OVERLAY_PERF_LOG_TAG = "CoverOverlayPerf"
private const val APP_SCAN_DEFER_AFTER_FIRST_FRAME_MS = 180L
private const val ICON_CACHE_MAX_ENTRIES = 256
private const val ICON_PREWARM_COUNT = APPS_PER_GRID_PAGE * 2
private const val ICON_PREWARM_BATCH_SIZE = 4
private const val ICON_PREWARM_BATCH_DELAY_MS = 48L
private const val OVERLAY_CHEAP_MODE_MS = 900L
private const val GRID_HYDRATE_DELAY_MS = 180L
private const val WALLPAPER_RETRY_BACKOFF_MS = 5_000L

// One UI leans on a near-monochrome glass surface with a single accent for
// interactive/selected states, rather than a busy set of tinted Material
// containers. These alphas are tuned so cards read as "frosted glass" over
// the blurred wallpaper instead of opaque Material cards.
private const val ICON_DISABLED_TINT_ALPHA = 1f
private val GRID_TILE_GAP = 5.dp
private val GRID_CONTENT_PADDING = coverScreenContentPadding(horizontal = 6.dp, vertical = 2.dp)

private data class CoverDisplayPolishSpec(
    val statusChipMinHeight: Dp,
    val dockVerticalOffset: Dp
)

@Composable
private fun rememberCoverDisplayPolishSpec(): CoverDisplayPolishSpec {
    val density = LocalDensity.current
    val containerHeightPx = LocalWindowInfo.current.containerSize.height
    val containerHeightDp = with(density) { containerHeightPx.toDp() }
    return remember(containerHeightPx) {
        // Keep the lock/search/dock cluster balanced across compact and taller covers.
        when {
            containerHeightDp <= 680.dp -> CoverDisplayPolishSpec(
                statusChipMinHeight = 28.dp,
                dockVerticalOffset = (-2).dp
            )
            containerHeightDp <= 760.dp -> CoverDisplayPolishSpec(
                statusChipMinHeight = 30.dp,
                dockVerticalOffset = (-4).dp
            )
            else -> CoverDisplayPolishSpec(
                statusChipMinHeight = 32.dp,
                dockVerticalOffset = (-6).dp
            )
        }
    }
}

private object OverlayIconCache {
    private val cache = LruCache<String, Drawable.ConstantState>(ICON_CACHE_MAX_ENTRIES)

    fun get(context: android.content.Context, packageName: String): Drawable? {
        val constantState = synchronized(cache) { cache.get(packageName) } ?: return null
        return runCatching { constantState.newDrawable(context.resources) }.getOrNull()
    }

    fun put(packageName: String, drawable: Drawable) {
        val constantState = drawable.constantState ?: return
        synchronized(cache) { cache.put(packageName, constantState) }
    }
}

private object WallpaperDecodeBackoff {
    private val failedAtElapsedMsByKey = mutableMapOf<String, Long>()

    fun shouldSkip(cacheKey: String, nowElapsedMs: Long): Boolean {
        val failedAt = synchronized(failedAtElapsedMsByKey) { failedAtElapsedMsByKey[cacheKey] } ?: return false
        return (nowElapsedMs - failedAt) < WALLPAPER_RETRY_BACKOFF_MS
    }

    fun markFailure(cacheKey: String, nowElapsedMs: Long) {
        synchronized(failedAtElapsedMsByKey) {
            failedAtElapsedMsByKey[cacheKey] = nowElapsedMs
        }
    }

    fun clearFailure(cacheKey: String) {
        synchronized(failedAtElapsedMsByKey) {
            failedAtElapsedMsByKey.remove(cacheKey)
        }
    }
}

@Composable
private fun rememberAppIconDrawable(app: AppModel): Drawable {
    val context = LocalContext.current
    val packageManager = remember(context) { context.packageManager }

    val iconState = produceState(
        initialValue = OverlayIconCache.get(context, app.packageName) ?: app.iconDrawable,
        key1 = app.packageName
    ) {
        val cached = OverlayIconCache.get(context, app.packageName)
        if (cached != null) {
            value = cached
            return@produceState
        }

        val resolved = withContext(Dispatchers.IO) {
            runCatching { packageManager.getApplicationIcon(app.packageName) }.getOrNull()
        }
        if (resolved != null) {
            OverlayIconCache.put(app.packageName, resolved)
            value = resolved
        }
    }


    return iconState.value
}

@Composable
fun CoverAppGridOverlay(
    repository: PackageManagerAppScannerRepository,
    onAppSelected: (AppModel) -> Unit,
    isDeviceLocked: Boolean,
    modifier: Modifier = Modifier,
    dockPackageSlots: List<String?> = List(DOCK_SLOT_COUNT) { null },
    isDockVisible: Boolean = true,
    wallpaperUri: String? = null,
    wallpaperScaleMode: WallpaperScaleMode = WallpaperScaleMode.CROP,
    wallpaperDimAmount: Float = DEFAULT_WALLPAPER_DIM_AMOUNT,
    wallpaperBlurRadiusDp: Float = DEFAULT_WALLPAPER_BLUR_RADIUS_DP
) {
    val context = LocalContext.current
    val packageManager = remember(context) { context.packageManager }
    val overlayComposeStartMs = remember { SystemClock.uptimeMillis() }

    var isCheapMode by remember { mutableStateOf(true) }
    var isGridHydrated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        Log.d(
            OVERLAY_PERF_LOG_TAG,
            "firstFrameCommittedMs=${SystemClock.uptimeMillis() - overlayComposeStartMs}"
        )
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(GRID_HYDRATE_DELAY_MS.milliseconds)
        isGridHydrated = true
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(OVERLAY_CHEAP_MODE_MS.milliseconds)
        isCheapMode = false
    }

    var shouldLoadApps by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Let initial draw commit before doing expensive package work.
        withFrameNanos { }
        delay(APP_SCAN_DEFER_AFTER_FIRST_FRAME_MS.milliseconds)
        shouldLoadApps = true
    }

    // Scan installed applications off the main thread and expose a UI-safe list.
    val appsState = produceState(
        initialValue = emptyList<AppModel>(),
        key1 = repository,
        key2 = shouldLoadApps
    ) {
        if (!shouldLoadApps) {
            value = emptyList()
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            runCatching { repository.scanInstalledApplications() }.getOrDefault(emptyList())
        }
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
    LaunchedEffect(apps) {
        if (apps.isEmpty()) return@LaunchedEffect

        val prewarmTargets = apps
            .asSequence()
            .take(ICON_PREWARM_COUNT)
            .filter { app -> OverlayIconCache.get(context, app.packageName) == null }
            .toList()

        if (prewarmTargets.isEmpty()) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            val prewarmBatches = prewarmTargets.chunked(ICON_PREWARM_BATCH_SIZE)
            prewarmBatches.forEachIndexed { index, batch ->
                batch.forEach { app ->
                    runCatching { packageManager.getApplicationIcon(app.packageName) }
                        .getOrNull()
                        ?.let { drawable -> OverlayIconCache.put(app.packageName, drawable) }
                }

                if (index < prewarmBatches.lastIndex) {
                    delay(ICON_PREWARM_BATCH_DELAY_MS.milliseconds)
                }
            }
        }
    }

    // Page 0 is lock+dock; remaining pages are fixed-size app grid tiles.
    val dockApps = remember(apps, dockPackageSlots) {
        resolveDockSlots(apps = apps, dockPackageSlots = dockPackageSlots)
    }
    val constrainedWallpaperDim = wallpaperDimAmount.coerceIn(
        MIN_WALLPAPER_DIM_AMOUNT,
        MAX_WALLPAPER_DIM_AMOUNT
    )
    val appPages = remember(apps) { apps.chunked(APPS_PER_GRID_PAGE) }
    val totalPageCount = 1 + appPages.size
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { totalPageCount })

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) {
            isCheapMode = false
            isGridHydrated = true
        }
    }

    val isAppGridPage = pagerState.currentPage >= 1
    val effectiveWallpaperBlur = 0f

    LaunchedEffect(isGridHydrated, isCheapMode) {
        if (!isGridHydrated || isCheapMode) return@LaunchedEffect
        Log.d(
            OVERLAY_PERF_LOG_TAG,
            "overlayHydratedMs=${SystemClock.uptimeMillis() - overlayComposeStartMs}"
        )
    }

    val displayPolishSpec = rememberCoverDisplayPolishSpec()

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

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            CoverWallpaperLayer(
                wallpaperUri = wallpaperUri,
                wallpaperScaleMode = wallpaperScaleMode,
                blurRadiusDp = effectiveWallpaperBlur,
                modifier = Modifier.fillMaxSize()
            )

            if (isAppGridPage && constrainedWallpaperDim > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = constrainedWallpaperDim * 0.2f))
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                        deferGridHydration = !isGridHydrated,
                        dockApps = dockApps,
                        displayPolishSpec = displayPolishSpec,
                        onAppSelected = onAppSelected,
                        isDeviceLocked = isDeviceLocked,
                        isDockVisible = isDockVisible,
                        timeLabel = timeLabel,
                        dateLabel = dateLabel,
                        totalPageCount = totalPageCount,
                        pagerState = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.67f)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("FrequentlyChangingValue")
private fun InteractiveSection(
    appPages: List<List<AppModel>>,
    deferGridHydration: Boolean,
    dockApps: List<AppModel?>,
    displayPolishSpec: CoverDisplayPolishSpec,
    onAppSelected: (AppModel) -> Unit,
    isDeviceLocked: Boolean,
    isDockVisible: Boolean,
    timeLabel: String,
    dateLabel: String,
    totalPageCount: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
    modifier: Modifier = Modifier
) {
    // On the lock page the panel disappears entirely so the clock sits
    // directly on wallpaper, matching a real cover lock screen. Only the
    // app-grid pages get a floating glass panel beneath them.
    val showPanelChrome = pagerState.currentPage > 0

    var showPageLetterTooltip by remember { mutableStateOf(false) }
    val maxPagerPage = (totalPageCount - 1).coerceAtLeast(0)
    var hintedPagerPage by remember { mutableIntStateOf(0) }
    val hintedGridLetter = remember(hintedPagerPage, appPages) {
        gridPageStartLetterForPagerPage(hintedPagerPage, appPages)
    }

    LaunchedEffect(pagerState, maxPagerPage) {
        snapshotFlow {
            ((pagerState.currentPage + pagerState.currentPageOffsetFraction).roundToInt())
                .coerceIn(0, maxPagerPage)
        }.collect { hintedPage: Int ->
            hintedPagerPage = hintedPage
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) {
            showPageLetterTooltip = true
        } else {
            delay(260.milliseconds)
            if (!pagerState.isScrollInProgress) {
                showPageLetterTooltip = false
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 0) return@LaunchedEffect
        showPageLetterTooltip = true
        delay(260.milliseconds)
        if (!pagerState.isScrollInProgress) {
            showPageLetterTooltip = false
        }
    }

    // Bottom interaction zone: horizontally paged tiles with cutout-safe bottom padding.
    Box(
        modifier = modifier
            .then(
                if (showPanelChrome) {
                    Modifier
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 12.dp,
                    beyondViewportPageCount = 0
                ) { pageIndex ->
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // First tile is lock-style surface + dock; rest are app-grid pages.
                        if (pageIndex == 0) {
                            LockAndDockTile(
                                timeLabel = timeLabel,
                                dateLabel = dateLabel,
                                displayPolishSpec = displayPolishSpec,
                                isDeviceLocked = isDeviceLocked,
                                isDockVisible = isDockVisible,
                                dockSlots = dockApps,
                                onAppSelected = onAppSelected,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AppGridPageTile(
                                apps = appPages[pageIndex - 1],
                                deferHydration = deferGridHydration,
                                isDeviceLocked = isDeviceLocked,
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
                pageCount = totalPageCount,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LockAndDockTile(
    timeLabel: String,
    dateLabel: String,
    displayPolishSpec: CoverDisplayPolishSpec,
    isDeviceLocked: Boolean,
    isDockVisible: Boolean,
    dockSlots: List<AppModel?>,
    onAppSelected: (AppModel) -> Unit,
    modifier: Modifier = Modifier
) {
    // Keep lock tile as a pure launcher surface for faster interactions.
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .coverScreenPadding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LockStatusPill(
                isDeviceLocked = isDeviceLocked,
                minHeight = displayPolishSpec.statusChipMinHeight
            )

            Text(
                text = timeLabel,
                style = CoverOSTextStyles.ClockText,
                color = Color.White
            )
            Text(
                text = dateLabel,
                style = CoverOSTextStyles.DateText,
                color = Color.White
            )
        }

        if (isDockVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .coverScreenPadding(horizontal = 12.dp, vertical = 0.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CoverDockRow(
                        dockSlots = dockSlots,
                        isDeviceLocked = isDeviceLocked,
                        onAppSelected = onAppSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = displayPolishSpec.dockVerticalOffset)
                    )

                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun LockStatusPill(
    isDeviceLocked: Boolean,
    minHeight: Dp
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .heightIn(min = minHeight)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isDeviceLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            contentDescription = if (isDeviceLocked) "Locked" else "Unlocked",
            tint = Color.White,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = if (isDeviceLocked) "Locked" else "Swipe left for apps",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}


@Composable
private fun AppGridPageTile(
    apps: List<AppModel>,
    deferHydration: Boolean,
    isDeviceLocked: Boolean,
    onAppSelected: (AppModel) -> Unit,
    modifier: Modifier = Modifier
) {
    // Each pager tile shows a fixed-size, non-scrollable grid for predictable touch mapping.
    if (deferHydration) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(GRID_COLUMNS),
            modifier = modifier,
            contentPadding = GRID_CONTENT_PADDING,
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(GRID_TILE_GAP),
            verticalArrangement = Arrangement.spacedBy(GRID_TILE_GAP)
        ) {
            repeat(APPS_PER_GRID_PAGE) {
                item {
                AppGridPlaceholderTile()
                }
            }
        }
        return
    }

    if (apps.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "No launchable apps",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
        return
    }

    // Keep a consistent 3x3 matrix across pages so spacing stays visually stable.
    val displaySlots = remember(apps) {
        val placeholderCount = (APPS_PER_GRID_PAGE - apps.size).coerceAtLeast(0)
        apps.map { it as AppModel? } + List(placeholderCount) { null }
    }

    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(GRID_COLUMNS),
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
    if (!visible || letter == null) return

    Box(
        modifier = modifier
            .size(38.dp)
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
@Suppress("FrequentlyChangingValue")
private fun PageIndicator(
    pagerState: androidx.compose.foundation.pager.PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    var isDragging by remember { mutableStateOf(false) }
    var indicatorWidthPx by remember { mutableStateOf(0) }
    var lastScrubbedPage by remember { mutableStateOf(pagerState.currentPage) }
    var lastScrubTimestampMs by remember { mutableStateOf(0L) }
    var scrubStepCount by remember { mutableStateOf(0) }
    var scrubStartTimestampMs by remember { mutableStateOf(0L) }
    var lastScrubMetricLogTimestampMs by remember { mutableStateOf(0L) }
    var isScrollRequestInFlight by remember { mutableStateOf(false) }
    var pendingScrollTargetPage by remember { mutableStateOf<Int?>(null) }

    // Visual dimensions — a minimal floating dot row (no boxed track), the
    // way One UI's home/lock indicators read, while the hit-box padding
    // below keeps the same generous scrub/tap target as before.
    val dotDiameter = 6.dp
    val dotSpacing = 7.dp
    val hitAreaHorizontalPadding = 16.dp
    val hitAreaVerticalPadding = 10.dp

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
                pageCount = pageCount
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
    val activePillWidth = if (isDragging) 20.dp else 16.dp
    val activePillScale = if (isDragging) 1.08f else 1f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        // Invisible hit-box preserves the original generous tap/scrub target
        // while the visible dots stay minimal and float freely — no boxed
        // Material "track" behind them.
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .height(24.dp)
                .onSizeChanged { indicatorWidthPx = (it.width - (hitAreaHorizontalPadding.value * 2 * density.density).roundToInt()).coerceAtLeast(0) }
                .pointerInput(pageCount) {
                    // Tap to navigate directly to dot/segment
                    detectTapGestures { offset ->
                        if (indicatorWidthPx > 0) {
                            val localX = offset.x - with(density) { hitAreaHorizontalPadding.toPx() }
                            val fraction = (localX / indicatorWidthPx).coerceIn(0f, 1f)
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
                                val localX = offset.x - with(density) { hitAreaHorizontalPadding.toPx() }
                                val targetPage = targetPageForX(localX)
                                if (targetPage != lastScrubbedPage && requestPageScroll(targetPage)) {
                                    lastScrubbedPage = targetPage
                                    lastScrubTimestampMs = SystemClock.uptimeMillis()
                                    scrubStepCount += 1
                                }
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val now = SystemClock.uptimeMillis()
                            if ((now - lastScrubTimestampMs) < INDICATOR_SCRUB_INTERVAL_MS) return@detectHorizontalDragGestures

                            if (indicatorWidthPx > 0) {
                                val localX = change.position.x - with(density) { hitAreaHorizontalPadding.toPx() }
                                val targetPage = targetPageForX(localX)
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
                .padding(horizontal = hitAreaHorizontalPadding, vertical = hitAreaVerticalPadding),
            contentAlignment = Alignment.CenterStart
        ) {
            // Background inactive dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(dotSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) {
                    Box(
                        modifier = Modifier
                            .size(dotDiameter)
                            .clip(CircleShape)
                            .background(Color.White)
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
                    .background(com.tyejaedon.coverscreenos.ui.theme.CoverOSPrimary)
            )
        }
    }
}

@Composable
private fun CoverDockRow(
    dockSlots: List<AppModel?>,
    isDeviceLocked: Boolean,
    onAppSelected: (AppModel) -> Unit,
    modifier: Modifier = Modifier
) {
    // A single floating glass pill holds all four slots — icons sit
    // directly in it with no per-slot card, matching a real One UI dock.
    Row(
        modifier = modifier.coverScreenPadding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(DOCK_SLOT_COUNT) { index ->
            val app = dockSlots.getOrNull(index)
            val isSlotEnabled = app != null && !isDeviceLocked
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .coverMinimumTouchTarget()
                    .clip(RoundedCornerShape(CoverOSCornerRadiusSmall))
                    .clickable(enabled = isSlotEnabled) { app?.let(onAppSelected) },
                contentAlignment = Alignment.Center
            ) {
                if (app != null) {
                    val iconDrawable = rememberAppIconDrawable(app)
                    AndroidView(
                        factory = { viewContext ->
                            ImageView(viewContext).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                        },
                        update = { imageView ->
                            if (imageView.tag !== iconDrawable) {
                                imageView.setImageDrawable(iconDrawable)
                                imageView.tag = iconDrawable
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    )
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
            .heightIn(min = 84.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // A quiet dashed-feeling dot rather than a boxed placeholder card —
        // reserves the grid slot without competing for attention.
        Box(
            modifier = Modifier
                .padding(top = 24.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun AppGridTile(
    app: AppModel,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val iconDrawable = rememberAppIconDrawable(app)

    // Icon + label float directly over the blurred wallpaper with no card
    // background — the authentic One UI app-drawer treatment — while still
    // keeping the >=48dp interactive bounds required for cover-screen use.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .coverMinimumTouchTarget()
            .clip(RoundedCornerShape(CoverOSCornerRadiusMedium))
            .clickable(enabled = enabled) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .coverScreenPadding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { viewContext ->
                        ImageView(viewContext).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                    },
                    update = { imageView ->
                        if (imageView.tag !== iconDrawable) {
                            imageView.setImageDrawable(iconDrawable)
                            imageView.tag = iconDrawable
                        }
                    },
                    modifier = Modifier.size(46.dp)
                )
            }

            Text(
                text = app.name,
                style = CoverOSTextStyles.AppLabelText,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


private fun resolveDockSlots(
    apps: List<AppModel>,
    dockPackageSlots: List<String?>
): List<AppModel?> {
    val appByPackageName = apps.associateBy { it.packageName }
    val hasCustomSelection = dockPackageSlots.any { !it.isNullOrBlank() }
    if (!hasCustomSelection) {
        val defaults = apps.take(DOCK_SLOT_COUNT)
        return List(DOCK_SLOT_COUNT) { index -> defaults.getOrNull(index) }
    }

    return List(DOCK_SLOT_COUNT) { index ->
        dockPackageSlots.getOrNull(index)
            ?.trim()
            .takeUnless { it.isNullOrEmpty() }
            ?.let { packageName -> appByPackageName[packageName] }
    }
}


@Composable
private fun CoverWallpaperLayer(
    wallpaperUri: String?,
    wallpaperScaleMode: WallpaperScaleMode,
    blurRadiusDp: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val containerSize = LocalWindowInfo.current.containerSize
    val requestedWidthPx = remember(containerSize.width) { max(containerSize.width, 1) }
    val requestedHeightPx = remember(containerSize.height) { max(containerSize.height, 1) }
    val wallpaperCacheVersionToken = remember(wallpaperUri) {
        wallpaperUri
            ?.takeUnless { it.isBlank() }
            ?.let { runCatching { resolveWallpaperCacheVersionToken(it.toUri()) }.getOrNull() }
    }
    val wallpaperBitmap by produceState<Bitmap?>(
        initialValue = null,
        wallpaperUri,
        wallpaperCacheVersionToken,
        context,
        requestedWidthPx,
        requestedHeightPx
    ) {
        value = if (wallpaperUri.isNullOrBlank()) {
            Log.d(WALLPAPER_LOG_TAG, "Wallpaper skipped: empty URI")
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val parsedUri = wallpaperUri.toUri()
                    decodeSampledBitmapFromUri(
                        context = context,
                        uri = parsedUri,
                        cacheVersionToken = wallpaperCacheVersionToken,
                        requestedWidthPx = requestedWidthPx,
                        requestedHeightPx = requestedHeightPx
                    )
                }.also { result ->
                    if (result.getOrNull() == null) {
                        Log.w(
                            WALLPAPER_LOG_TAG,
                            "Wallpaper decode returned null uri=$wallpaperUri size=${requestedWidthPx}x${requestedHeightPx} token=$wallpaperCacheVersionToken"
                        )
                    }
                }.onFailure { error ->
                    Log.w(WALLPAPER_LOG_TAG, "Wallpaper decode failed for URI=$wallpaperUri: ${error.message}")
                }.getOrNull()
            }
        }
    }

    val bitmap = wallpaperBitmap
    if (bitmap == null) {
        if (!wallpaperUri.isNullOrBlank()) {
            Log.w(
                WALLPAPER_LOG_TAG,
                "Wallpaper fallback to gradient uri=$wallpaperUri size=${requestedWidthPx}x${requestedHeightPx}"
            )
        }
        // No custom wallpaper: fall back to a deep, near-black gradient
        // rather than a flat theme background, so the cover screen still
        // feels considered instead of empty.
        Box(
            modifier = modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        com.tyejaedon.coverscreenos.ui.theme.CoverOSDarkSurfaceVariant,
                        com.tyejaedon.coverscreenos.ui.theme.CoverOSDarkBackground
                    )
                )
            )
        )
    } else {
        Log.d(
            WALLPAPER_LOG_TAG,
            "Wallpaper render success bitmap=${bitmap.width}x${bitmap.height} size=${requestedWidthPx}x${requestedHeightPx}"
        )
        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
        Image(
            bitmap = imageBitmap,
            contentDescription = "Custom cover wallpaper",
            contentScale = if (wallpaperScaleMode == WallpaperScaleMode.CROP) {
                ContentScale.Crop
            } else {
                ContentScale.Fit
            },
            modifier = modifier
        )
    }
}

private fun decodeSampledBitmapFromUri(
    context: android.content.Context,
    uri: Uri,
    cacheVersionToken: String? = null,
    requestedWidthPx: Int,
    requestedHeightPx: Int
): Bitmap? {
    val cacheKey = WallpaperBitmapCache.buildKey(
        uri = uri.toString(),
        versionToken = cacheVersionToken ?: resolveWallpaperCacheVersionToken(uri),
        requestedWidthPx = requestedWidthPx,
        requestedHeightPx = requestedHeightPx
    )

    val nowElapsedMs = SystemClock.elapsedRealtime()
    if (WallpaperDecodeBackoff.shouldSkip(cacheKey, nowElapsedMs)) {
        Log.w(
            WALLPAPER_LOG_TAG,
            "Wallpaper decode skipped by backoff key=$cacheKey uri=$uri"
        )
        return null
    }

    WallpaperBitmapCache.get(cacheKey)?.let { cachedBitmap ->
        Log.d(
            WALLPAPER_LOG_TAG,
            "Wallpaper cache hit key=$cacheKey bitmap=${cachedBitmap.width}x${cachedBitmap.height}"
        )
        return cachedBitmap
    }

    val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    openWallpaperInputStream(context, uri)?.use { boundsStream ->
        BitmapFactory.decodeStream(boundsStream, null, boundsOptions)
    } ?: run {
        val fallbackDecoded = if (uri.scheme == "file") {
            decodeSampledBitmapFromFilePath(
                uri = uri,
                requestedWidthPx = requestedWidthPx,
                requestedHeightPx = requestedHeightPx
            )
        } else {
            null
        }

        if (fallbackDecoded != null) {
            WallpaperBitmapCache.put(cacheKey, fallbackDecoded)
            WallpaperDecodeBackoff.clearFailure(cacheKey)
            Log.d(
                WALLPAPER_LOG_TAG,
                "Wallpaper file fallback decode success uri=$uri bitmap=${fallbackDecoded.width}x${fallbackDecoded.height}"
            )
            return fallbackDecoded
        }

        Log.w(WALLPAPER_LOG_TAG, "Wallpaper stream unavailable for bounds decode uri=$uri")
        WallpaperDecodeBackoff.markFailure(cacheKey, nowElapsedMs)
        return null
    }

    if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
        Log.w(
            WALLPAPER_LOG_TAG,
            "Wallpaper bounds invalid uri=$uri out=${boundsOptions.outWidth}x${boundsOptions.outHeight}"
        )
        WallpaperDecodeBackoff.markFailure(cacheKey, nowElapsedMs)
        return null
    }

    val sampledOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(
            outWidth = boundsOptions.outWidth,
            outHeight = boundsOptions.outHeight,
            requestedWidthPx = requestedWidthPx,
            requestedHeightPx = requestedHeightPx
        )
        inPreferredConfig = Bitmap.Config.RGB_565
    }

    val decodedBitmap = openWallpaperInputStream(context, uri)?.use { decodeStream ->
        BitmapFactory.decodeStream(decodeStream, null, sampledOptions)
    }

    if (decodedBitmap == null) {
        Log.w(
            WALLPAPER_LOG_TAG,
            "Wallpaper bitmap decode null uri=$uri sample=${sampledOptions.inSampleSize} req=${requestedWidthPx}x${requestedHeightPx} bounds=${boundsOptions.outWidth}x${boundsOptions.outHeight}"
        )
        WallpaperDecodeBackoff.markFailure(cacheKey, nowElapsedMs)
    }

    if (decodedBitmap != null) {
        WallpaperBitmapCache.put(cacheKey, decodedBitmap)
        WallpaperDecodeBackoff.clearFailure(cacheKey)
        Log.d(
            WALLPAPER_LOG_TAG,
            "Wallpaper cache store key=$cacheKey bitmap=${decodedBitmap.width}x${decodedBitmap.height} sample=${sampledOptions.inSampleSize}"
        )
    }

    return decodedBitmap
}

private fun decodeSampledBitmapFromFilePath(
    uri: Uri,
    requestedWidthPx: Int,
    requestedHeightPx: Int
): Bitmap? {
    if (uri.scheme != "file") return null
    val path = uri.path ?: return null

    val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(path, boundsOptions)
    if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
        return null
    }

    val sampledOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(
            outWidth = boundsOptions.outWidth,
            outHeight = boundsOptions.outHeight,
            requestedWidthPx = requestedWidthPx,
            requestedHeightPx = requestedHeightPx
        )
        inPreferredConfig = Bitmap.Config.RGB_565
    }

    return BitmapFactory.decodeFile(path, sampledOptions)
}

private fun resolveWallpaperCacheVersionToken(uri: Uri): String? {
    if (uri.scheme != "file") return null
    val path = uri.path ?: return null
    val file = File(path)
    if (!file.exists() || !file.isFile) return null
    return "${file.lastModified()}_${file.length()}"
}

private fun openWallpaperInputStream(context: android.content.Context, uri: Uri): InputStream? {
    return when (uri.scheme) {
        "file" -> {
            val path = uri.path
            if (path.isNullOrBlank()) {
                Log.w(WALLPAPER_LOG_TAG, "File wallpaper URI has blank path uri=$uri")
                null
            } else {
                val wallpaperFile = File(path)
                runCatching { wallpaperFile.inputStream() }
                    .onFailure { error ->
                        Log.w(
                            WALLPAPER_LOG_TAG,
                            "File wallpaper stream open failed uri=$uri path=$path exists=${wallpaperFile.exists()} isFile=${wallpaperFile.isFile} canRead=${wallpaperFile.canRead()} length=${wallpaperFile.length()} error=${error.message}"
                        )
                    }
                    .getOrNull()
                    ?: runCatching { context.contentResolver.openInputStream(uri) }
                        .onFailure { error ->
                            Log.w(
                                WALLPAPER_LOG_TAG,
                                "ContentResolver fallback failed for file URI uri=$uri error=${error.message}"
                            )
                        }
                        .getOrNull()
            }
        }

        else -> runCatching { context.contentResolver.openInputStream(uri) }
            .onFailure { error ->
                Log.w(
                    WALLPAPER_LOG_TAG,
                    "ContentResolver wallpaper stream open failed uri=$uri scheme=${uri.scheme} error=${error.message}"
                )
            }
            .getOrNull()
    }
}

private fun calculateInSampleSize(
    outWidth: Int,
    outHeight: Int,
    requestedWidthPx: Int,
    requestedHeightPx: Int
): Int {
    if (outWidth <= 0 || outHeight <= 0) return 1

    var inSampleSize = 1
    val halfWidth = outWidth / 2
    val halfHeight = outHeight / 2

    while ((halfWidth / inSampleSize) >= requestedWidthPx && (halfHeight / inSampleSize) >= requestedHeightPx) {
        inSampleSize *= 2
    }
    return inSampleSize.coerceAtLeast(1)
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
    pageCount: Int
): IntArray {
    if (indicatorWidthPx <= 0 || pageCount <= 1) return IntArray(0)

    val maxPage = pageCount - 1
    return IntArray(indicatorWidthPx + 1) { xIndex ->
        val baseFraction = (xIndex.toFloat() / indicatorWidthPx).coerceIn(0f, 1f)
        val centered = baseFraction - 0.5f
        val adjustedFraction = ((centered * INDICATOR_SCRUB_SENSITIVITY) + 0.5f).coerceIn(0f, 1f)
        (adjustedFraction * maxPage).roundToInt()
    }
}

