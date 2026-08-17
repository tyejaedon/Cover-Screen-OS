@file:Suppress("FrequentlyChangingValue")

package com.tyejaedon.coverscreenos.ui

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.BatteryManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.tyejaedon.coverscreenos.datastore.DEFAULT_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.DEFAULT_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.WallpaperScaleMode
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.models.CoverNotificationModel
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import com.tyejaedon.coverscreenos.services.CoverNotificationListenerService
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCornerRadiusLarge
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCornerRadiusSmall
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTextStyles
import com.tyejaedon.coverscreenos.ui.theme.coverGlassSurface
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget
import com.tyejaedon.coverscreenos.ui.theme.coverScreenContentPadding
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val GRID_COLUMNS = 4
private const val GRID_ROWS = 3
private const val APPS_PER_GRID_PAGE = GRID_COLUMNS * GRID_ROWS
private const val DOCK_SLOT_COUNT = 4
private const val INDICATOR_SCRUB_SENSITIVITY = 1.45f
private const val INDICATOR_SCRUB_INTERVAL_MS = 42L
private const val PAGER_HAPTIC_INTERVAL_MS = 150L
private const val COMPACT_CARD_MIN_ART_EDGE_PX = 180

private val PREFERRED_MEDIA_PACKAGES = listOf(
    "com.spotify.music",
    "com.apple.android.music",
    "com.google.android.apps.youtube.music",
    "com.pandora.android",
    "com.soundcloud.android",
    "com.amazon.mp3",
    "com.samsung.android.app.music"
)

private const val WALLPAPER_LOG_TAG = "CoverWallpaper"
private const val OVERLAY_PERF_LOG_TAG = "CoverOverlayPerf"
private const val APP_SCAN_DEFER_AFTER_FIRST_FRAME_MS = 120L
private const val ICON_CACHE_MAX_ENTRIES = 256
private const val ICON_PREWARM_COUNT = APPS_PER_GRID_PAGE * 2
private const val ICON_PREWARM_BATCH_SIZE = 6
private const val ICON_PREWARM_BATCH_DELAY_MS = 32L
private const val NOTIFICATION_PANEL_PAGE_INDEX = 0
private const val LOCK_PAGER_PAGE_INDEX = 1
private const val FIRST_APP_GRID_PAGE_INDEX = 2
private const val NOTIFICATION_PANEL_BLUR_CACHE_MAX_KB = 20 * 1024
private const val NOTIFICATION_PANEL_BLUR_DOWNSAMPLE = 6
private const val WALLPAPER_RETRY_BACKOFF_MS = 4_000L
private const val WALLPAPER_OVERLAY_RETRY_DELAY_MS = 600L
private const val WALLPAPER_OVERLAY_RETRY_MAX_ATTEMPTS = 6

private val GRID_TILE_GAP = 6.dp
private val GRID_CONTENT_PADDING = coverScreenContentPadding(horizontal = 6.dp, vertical = 2.dp)

private data class CoverDisplayPolishSpec(
    val statusChipMinHeight: Dp,
    val dockVerticalOffset: Dp
)

private data class BatteryStatusSnapshot(
    val levelPercent: Int,
    val isCharging: Boolean
)

private data class ExpandedPlayerLayoutSpec(
    val panelWidthFraction: Float,
    val panelHeightFraction: Float,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val contentSpacing: Dp,
    val showArtwork: Boolean,
    val artworkWidthFraction: Float,
    val artworkBottomPadding: Dp,
    val sideControlSize: Dp,
    val primaryControlSize: Dp,
    val actionButtonTopSpacer: Dp,
    val actionButtonVerticalPadding: Dp
)

@Composable
private fun rememberCoverDisplayPolishSpec(): CoverDisplayPolishSpec {
    val density = LocalDensity.current
    val containerHeightPx = LocalWindowInfo.current.containerSize.height
    val containerHeightDp = with(density) { containerHeightPx.toDp() }
    return remember(containerHeightPx) {
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

@Composable
private fun rememberBatteryStatus(): BatteryStatusSnapshot? {
    val context = LocalContext.current
    var status by remember(context) { mutableStateOf(readBatteryStatus(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                status = resolveBatteryStatusSnapshot(intent)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        if (stickyIntent != null) {
            status = resolveBatteryStatusSnapshot(stickyIntent)
        }

        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return status
}

private fun readBatteryStatus(context: Context): BatteryStatusSnapshot? {
    val stickyIntent = context.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        Context.RECEIVER_NOT_EXPORTED
    )
    return resolveBatteryStatusSnapshot(stickyIntent)
}

private fun resolveBatteryStatusSnapshot(intent: Intent?): BatteryStatusSnapshot? {
    intent ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null

    val levelPercent = ((level.toFloat() / scale.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

    return BatteryStatusSnapshot(levelPercent = levelPercent, isCharging = isCharging)
}

// Global LRU Bitmap Cache for hardware-accelerated Compose Image rendering
private object OverlayBitmapIconCache {
    private val cache = LruCache<String, ImageBitmap>(ICON_CACHE_MAX_ENTRIES)

    fun get(packageName: String): ImageBitmap? {
        return synchronized(cache) { cache.get(packageName) }
    }

    fun put(packageName: String, bitmap: ImageBitmap) {
        synchronized(cache) { cache.put(packageName, bitmap) }
    }
}

private object NotificationPanelBlurBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(NOTIFICATION_PANEL_BLUR_CACHE_MAX_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(cacheKey: String): Bitmap? = synchronized(cache) { cache.get(cacheKey) }
    fun put(cacheKey: String, bitmap: Bitmap) { synchronized(cache) { cache.put(cacheKey, bitmap) } }
}



private object WallpaperDecodeBackoff {
    private val failedAtElapsedMsByKey = mutableMapOf<String, Long>()

    fun shouldSkip(cacheKey: String, nowElapsedMs: Long): Boolean {
        val failedAt = synchronized(failedAtElapsedMsByKey) { failedAtElapsedMsByKey[cacheKey] } ?: return false
        return (nowElapsedMs - failedAt) < WALLPAPER_RETRY_BACKOFF_MS
    }

    fun markFailure(cacheKey: String, nowElapsedMs: Long) {
        synchronized(failedAtElapsedMsByKey) { failedAtElapsedMsByKey[cacheKey] = nowElapsedMs }
    }

    fun clearFailure(cacheKey: String) {
        synchronized(failedAtElapsedMsByKey) { failedAtElapsedMsByKey.remove(cacheKey) }
    }
}

private fun drawableToImageBitmap(drawable: Drawable, sizePx: Int = 128): ImageBitmap? {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap.asImageBitmap()
    }
    val bitmap = Bitmap.createBitmap(
        if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else sizePx,
        if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else sizePx,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}

private suspend fun resolvePackageIconBitmap(
    context: Context,
    packageManager: PackageManager,
    packageName: String
): ImageBitmap? {
    OverlayBitmapIconCache.get(packageName)?.let { return it }

    val resolved = withContext(Dispatchers.IO) {
        val versionToken = resolvePackageIconVersionToken(packageManager, packageName)
        OverlayIconThumbnailDiskCache.get(
            context = context,
            packageName = packageName,
            versionToken = versionToken
        )?.let { diskCachedDrawable ->
            return@withContext drawableToImageBitmap(diskCachedDrawable)
        }

        runCatching {
            val drawable = packageManager.getApplicationIcon(packageName)
            OverlayIconThumbnailDiskCache.put(
                context = context,
                packageName = packageName,
                versionToken = versionToken,
                drawable = drawable
            )
            drawableToImageBitmap(drawable)
        }.getOrNull()
    } ?: return null

    OverlayBitmapIconCache.put(packageName, resolved)
    return resolved
}

@Composable
private fun rememberPackageIconBitmap(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    val packageManager = remember(context) { context.packageManager }

    val iconState = produceState<ImageBitmap?>(
        initialValue = OverlayBitmapIconCache.get(packageName),
        key1 = packageName
    ) {
        if (value == null) {
            val resolved = resolvePackageIconBitmap(context, packageManager, packageName)
            if (resolved != null) value = resolved
        }
    }
    return iconState.value
}

@Composable
private fun rememberPackageLabel(packageName: String): String {
    val context = LocalContext.current
    val packageManager = remember(context) { context.packageManager }

    val labelState = produceState(
        initialValue = packageName.substringAfterLast('.'),
        key1 = packageName
    ) {
        val resolvedLabel = withContext(Dispatchers.IO) {
            runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            }.getOrNull()
        }
        if (!resolvedLabel.isNullOrBlank()) {
            value = resolvedLabel
        }
    }
    return labelState.value
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

    var isGridHydrated by remember { mutableStateOf(false) }
    var shouldLoadApps by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(APP_SCAN_DEFER_AFTER_FIRST_FRAME_MS.milliseconds)
        shouldLoadApps = true
        isGridHydrated = true
        Log.d(OVERLAY_PERF_LOG_TAG, "firstFrameCommittedMs=${SystemClock.uptimeMillis() - overlayComposeStartMs}")
    }

    val appsState = produceState(
        initialValue = emptyList<AppModel>(),
        key1 = repository,
        key2 = shouldLoadApps
    ) {
        if (!shouldLoadApps) return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching { repository.scanInstalledApplications() }.getOrDefault(emptyList())
        }
    }

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
    val batteryStatus = rememberBatteryStatus()

    val apps = appsState.value
    val notifications by CoverNotificationListenerService.activeNotificationsFlow().collectAsState()

    // Prewarm icon bitmaps into LRU
    LaunchedEffect(apps) {
        if (apps.isEmpty()) return@LaunchedEffect
        val targets = apps.take(ICON_PREWARM_COUNT).filter { OverlayBitmapIconCache.get(it.packageName) == null }
        if (targets.isEmpty()) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            targets.chunked(ICON_PREWARM_BATCH_SIZE).forEachIndexed { index, batch ->
                batch.forEach { resolvePackageIconBitmap(context, packageManager, it.packageName) }
                if (index < batch.size - 1) delay(ICON_PREWARM_BATCH_DELAY_MS.milliseconds)
            }
        }
    }

    val dockApps = remember(apps, dockPackageSlots) {
        resolveDockSlots(apps = apps, dockPackageSlots = dockPackageSlots)
    }
    val constrainedWallpaperDim = wallpaperDimAmount.coerceIn(MIN_WALLPAPER_DIM_AMOUNT, MAX_WALLPAPER_DIM_AMOUNT)
    val appPages = remember(apps) { apps.chunked(APPS_PER_GRID_PAGE) }
    val totalPageCount = FIRST_APP_GRID_PAGE_INDEX + appPages.size
    val pagerState = rememberPagerState(
        initialPage = LOCK_PAGER_PAGE_INDEX,
        pageCount = { totalPageCount }
    )

    val isAppGridPage = pagerState.currentPage >= FIRST_APP_GRID_PAGE_INDEX
    val displayPolishSpec = rememberCoverDisplayPolishSpec()

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
                modifier = Modifier.fillMaxSize()
            )

            if (isAppGridPage && constrainedWallpaperDim > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = constrainedWallpaperDim * 0.25f))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    InteractiveSection(
                        notifications = notifications,
                        wallpaperUri = wallpaperUri,
                        wallpaperScaleMode = wallpaperScaleMode,
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
                        batteryStatus = batteryStatus,
                        pagerState = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractiveSection(
    notifications: List<CoverNotificationModel>,
    wallpaperUri: String?,
    wallpaperScaleMode: WallpaperScaleMode,
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
    batteryStatus: BatteryStatusSnapshot?,
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPageLetterTooltip by remember { mutableStateOf(false) }
    val maxPagerPage = (totalPageCount - 1).coerceAtLeast(0)
    var hintedPagerPage by remember { mutableIntStateOf(0) }
    val hintedGridLetter = remember(hintedPagerPage, appPages) {
        gridPageStartLetterForPagerPage(hintedPagerPage, appPages, FIRST_APP_GRID_PAGE_INDEX)
    }

    LaunchedEffect(pagerState, maxPagerPage) {
        snapshotFlow {
            ((pagerState.currentPage + pagerState.currentPageOffsetFraction).roundToInt()).coerceIn(0, maxPagerPage)
        }.collect { hintedPage ->
            hintedPagerPage = hintedPage
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) {
            showPageLetterTooltip = true
        } else {
            delay(280.milliseconds)
            if (!pagerState.isScrollInProgress) showPageLetterTooltip = false
        }
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 10.dp,
                    beyondViewportPageCount = 0
                ) { pageIndex ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (pageIndex) {
                            NOTIFICATION_PANEL_PAGE_INDEX -> {
                                NotificationsPanelTile(
                                    notifications = notifications,
                                    wallpaperUri = wallpaperUri,
                                    wallpaperScaleMode = wallpaperScaleMode,
                                    onNotificationOpen = { model ->
                                        CoverNotificationListenerService.openNotificationFromOverlay(context, model)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            LOCK_PAGER_PAGE_INDEX -> {
                                LockAndDockTile(
                                    notifications = notifications,
                                    timeLabel = timeLabel,
                                    dateLabel = dateLabel,
                                    batteryStatus = batteryStatus,
                                    displayPolishSpec = displayPolishSpec,
                                    isDeviceLocked = isDeviceLocked,
                                    isDockVisible = isDockVisible,
                                    dockSlots = dockApps,
                                    onNotificationOpen = { model ->
                                        CoverNotificationListenerService.openNotificationFromOverlay(context, model)
                                    },
                                    onAppSelected = onAppSelected,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            else -> {
                                val gridIndex = pageIndex - FIRST_APP_GRID_PAGE_INDEX
                                AppGridPageTile(
                                    apps = appPages.getOrElse(gridIndex) { emptyList() },
                                    deferHydration = deferGridHydration,
                                    isDeviceLocked = isDeviceLocked,
                                    onAppSelected = onAppSelected,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                GridPageLetterTooltip(
                    letter = hintedGridLetter,
                    visible = showPageLetterTooltip && hintedGridLetter != null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
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
    notifications: List<CoverNotificationModel>,
    timeLabel: String,
    dateLabel: String,
    batteryStatus: BatteryStatusSnapshot?,
    displayPolishSpec: CoverDisplayPolishSpec,
    isDeviceLocked: Boolean,
    isDockVisible: Boolean,
    dockSlots: List<AppModel?>,
    onNotificationOpen: (CoverNotificationModel) -> Unit,
    onAppSelected: (AppModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaNotification = remember(notifications) {
        notifications.filter { it.isMediaNotification && it.isOngoing }.maxByOrNull { it.postTime }
    }
    var showExpandedMedia by remember(mediaNotification?.notificationKey) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .coverScreenPadding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (isDeviceLocked) {
                        LockStatusPill(
                            isDeviceLocked = isDeviceLocked,
                            minHeight = displayPolishSpec.statusChipMinHeight
                        )
                    }
                }

                if (batteryStatus != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        BatteryStatusPill(status = batteryStatus)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(0.95f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        LockTopNotificationHighlights(
                            notifications = notifications,
                            onNotificationOpen = onNotificationOpen,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = timeLabel,
                            style = CoverOSTextStyles.ClockText.copy(fontSize = 28.sp),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                        Text(
                            text = dateLabel,
                            style = CoverOSTextStyles.DateText,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    CompactNowPlayingCard(
                        mediaNotification = mediaNotification,
                        onOpenExpanded = { showExpandedMedia = true },
                        onOpenMediaApp = { openMediaApplicationFromOverlay(context, notifications) },
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }

            if (isDockVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .coverScreenPadding(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
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
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }

        ExpandedNowPlayingPanel(
            mediaNotification = mediaNotification,
            visible = showExpandedMedia,
            onDismiss = { showExpandedMedia = false },
            onLaunchSourceApp = { media ->
                CoverNotificationListenerService.launchNotificationSourceApp(context, media.packageName)
            },
            onAction = { media, actionLabel ->
                CoverNotificationListenerService.performNotificationAction(media.notificationKey, actionLabel)
            },
            onOpenNotification = { media ->
                CoverNotificationListenerService.openNotificationFromOverlay(context, media)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun BatteryStatusPill(status: BatteryStatusSnapshot, modifier: Modifier = Modifier) {
    val levelFraction = (status.levelPercent / 100f).coerceIn(0f, 1f)
    val trackColor = Color.White.copy(alpha = 0.18f)
    val fillColor = when {
        status.levelPercent <= 15 -> Color(0xFFD32F2F)
        status.isCharging -> Color(0xFF4CAF50)
        else -> Color.White
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(trackColor)
                .padding(1.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(levelFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(fillColor)
            )
        }

        Text(
            text = if (status.isCharging) "${status.levelPercent}% CHG" else "${status.levelPercent}%",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1
        )
    }
}

private fun openMediaApplicationFromOverlay(
    context: Context,
    notifications: List<CoverNotificationModel>
): Boolean {
    val packageManager = context.packageManager
    val mediaCandidateFromNotifications = notifications
        .asSequence()
        .filter { it.isMediaNotification }
        .map { it.packageName }
        .firstOrNull()

    val candidatePackages = buildList {
        mediaCandidateFromNotifications?.let { add(it) }
        addAll(PREFERRED_MEDIA_PACKAGES)
    }.distinct()

    candidatePackages.forEach { packageName ->
        val canLaunch = runCatching { packageManager.getLaunchIntentForPackage(packageName) != null }.getOrDefault(false)
        if (canLaunch) {
            val launched = CoverNotificationListenerService.launchNotificationSourceApp(context, packageName)
            if (launched) return true
        }
    }

    val categoryMusicIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_APP_MUSIC)
    }
    val fallbackPackage = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                categoryMusicIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )?.activityInfo?.packageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(categoryMusicIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }
    }.getOrNull()

    return fallbackPackage?.let { packageName ->
        CoverNotificationListenerService.launchNotificationSourceApp(context, packageName)
    } ?: false
}

@Composable
private fun CompactNowPlayingCard(
    mediaNotification: CoverNotificationModel?,
    onOpenExpanded: () -> Unit,
    onOpenMediaApp: (() -> Boolean)? = null,
    modifier: Modifier = Modifier
) {
    val containerShape = RoundedCornerShape(16.dp)
    val hasMedia = mediaNotification != null
    val isActionEnabled = hasMedia || onOpenMediaApp != null

    Surface(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(containerShape)
            .coverGlassSurface(
                color = Color.White.copy(alpha = 0.12f),
                borderColor = Color.White.copy(alpha = 0.22f),
                shape = containerShape
            )
            .clickable(enabled = isActionEnabled) {
                if (hasMedia) {
                    onOpenExpanded()
                } else {
                    onOpenMediaApp?.invoke()
                }
            },
        color = Color.Transparent,
        shape = containerShape
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (!hasMedia) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.15f))
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Open a media app",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 1
                        )
                    }
                }
            } else {
                val iconBitmap = rememberPackageIconBitmap(mediaNotification.packageName)
                val artworkBitmap = mediaNotification.media?.artworkBitmap
                val displayArtwork = remember(artworkBitmap) {
                    artworkBitmap?.takeIf { minOf(it.width, it.height) >= COMPACT_CARD_MIN_ART_EDGE_PX }
                }
                val titleText = mediaNotification.media?.title ?: mediaNotification.title
                val subtitleText = mediaNotification.media?.artist ?: mediaNotification.previewText.ifBlank { "Playing" }

                Box(modifier = Modifier.fillMaxWidth()) {
                    if (displayArtwork != null) {
                        Image(
                            bitmap = displayArtwork.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.Low,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                                    )
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF2C3E50).copy(alpha = 0.8f),
                                            Color(0xFF000000).copy(alpha = 0.8f)
                                        )
                                    )
                                )
                        )
                    }

                    if (iconBitmap != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 6.dp)
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(2.dp)
                        ) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandedNowPlayingPanel(
    mediaNotification: CoverNotificationModel?,
    visible: Boolean,
    onDismiss: () -> Unit,
    onLaunchSourceApp: (CoverNotificationModel) -> Unit,
    onAction: (CoverNotificationModel, String?) -> Unit,
    onOpenNotification: (CoverNotificationModel) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && mediaNotification != null,
        enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
            slideInVertically(animationSpec = tween(durationMillis = 260), initialOffsetY = { it / 3 }),
        exit = fadeOut(animationSpec = tween(durationMillis = 150)) +
            slideOutVertically(animationSpec = tween(durationMillis = 220), targetOffsetY = { it / 3 }),
        modifier = modifier.fillMaxSize()
    ) {
        if (mediaNotification == null) return@AnimatedVisibility

        val actionLabels = remember(mediaNotification.media) {
            mediaNotification.media?.actionLabels.orEmpty().filter { it.isNotBlank() }.distinct()
        }
        val previousAction = remember(actionLabels) {
            findMediaActionLabel(actionLabels, "previous", "prev", "rewind", "back")
        }
        val playPauseAction = remember(mediaNotification.media, actionLabels) {
            mediaNotification.media?.playPauseActionLabel?.trim()?.takeUnless { it.isEmpty() }
                ?: findMediaActionLabel(actionLabels, "play", "pause", "resume")
                ?: actionLabels.firstOrNull()
        }
        val nextAction = remember(actionLabels) {
            findMediaActionLabel(actionLabels, "next", "skip", "forward")
        }
        val isPauseAction = remember(playPauseAction) { isPauseLikeActionLabel(playPauseAction) }
        val artworkBitmap = mediaNotification.media?.artworkBitmap

        val sourceIcon = rememberPackageIconBitmap(mediaNotification.packageName)
        val titleText = mediaNotification.media?.title ?: mediaNotification.title
        val subtitleText = mediaNotification.media?.artist ?: mediaNotification.previewText.ifBlank { "Open app" }
        val density = LocalDensity.current
        val containerHeightPx = LocalWindowInfo.current.containerSize.height
        val containerHeightDp = with(density) { containerHeightPx.toDp() }
        val layoutSpec = remember(containerHeightPx) {
            if (containerHeightDp <= 340.dp) {
                ExpandedPlayerLayoutSpec(
                    panelWidthFraction = 0.97f,
                    panelHeightFraction = 0.72f,
                    horizontalPadding = 8.dp,
                    verticalPadding = 6.dp,
                    contentSpacing = 5.dp,
                    showArtwork = false,
                    artworkWidthFraction = 0.48f,
                    artworkBottomPadding = 6.dp,
                    sideControlSize = 42.dp,
                    primaryControlSize = 52.dp,
                    actionButtonTopSpacer = 4.dp,
                    actionButtonVerticalPadding = 6.dp
                )
            } else if (containerHeightDp <= 420.dp) {
                ExpandedPlayerLayoutSpec(
                    panelWidthFraction = 0.96f,
                    panelHeightFraction = 0.78f,
                    horizontalPadding = 10.dp,
                    verticalPadding = 8.dp,
                    contentSpacing = 6.dp,
                    showArtwork = true,
                    artworkWidthFraction = 0.52f,
                    artworkBottomPadding = 8.dp,
                    sideControlSize = 46.dp,
                    primaryControlSize = 58.dp,
                    actionButtonTopSpacer = 6.dp,
                    actionButtonVerticalPadding = 8.dp
                )
            } else {
                ExpandedPlayerLayoutSpec(
                    panelWidthFraction = 0.94f,
                    panelHeightFraction = 0.84f,
                    horizontalPadding = 14.dp,
                    verticalPadding = 12.dp,
                    contentSpacing = 8.dp,
                    showArtwork = true,
                    artworkWidthFraction = 0.62f,
                    artworkBottomPadding = 12.dp,
                    sideControlSize = 54.dp,
                    primaryControlSize = 70.dp,
                    actionButtonTopSpacer = 8.dp,
                    actionButtonVerticalPadding = 10.dp
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(layoutSpec.panelWidthFraction)
                    .fillMaxHeight(layoutSpec.panelHeightFraction)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = layoutSpec.horizontalPadding, vertical = layoutSpec.verticalPadding),
                    verticalArrangement = Arrangement.spacedBy(layoutSpec.contentSpacing)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { onLaunchSourceApp(mediaNotification) }
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            if (sourceIcon != null) {
                                Image(
                                    bitmap = sourceIcon,
                                    contentDescription = "App Icon",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "Open app",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = onDismiss
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (layoutSpec.showArtwork && artworkBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = layoutSpec.artworkBottomPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = artworkBitmap.asImageBitmap(),
                                contentDescription = "Album Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth(layoutSpec.artworkWidthFraction)
                                    .aspectRatio(1f)
                            )
                        }
                    }

                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = subtitleText,
                        style = if (layoutSpec.showArtwork) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MediaControlIconButton(
                            icon = Icons.Filled.SkipPrevious,
                            contentDescription = previousAction ?: "Previous",
                            enabled = previousAction != null,
                            onClick = { previousAction?.let { onAction(mediaNotification, it) } },
                            modifier = Modifier.size(layoutSpec.sideControlSize)
                        )

                        MediaControlIconButton(
                            icon = if (isPauseAction) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = playPauseAction ?: "Play/Pause",
                            enabled = playPauseAction != null,
                            onClick = { playPauseAction?.let { onAction(mediaNotification, it) } },
                            modifier = Modifier.size(layoutSpec.primaryControlSize)
                        )

                        MediaControlIconButton(
                            icon = Icons.Filled.SkipNext,
                            contentDescription = nextAction ?: "Next",
                            enabled = nextAction != null,
                            onClick = { nextAction?.let { onAction(mediaNotification, it) } },
                            modifier = Modifier.size(layoutSpec.sideControlSize)
                        )
                    }

                    Spacer(modifier = Modifier.height(layoutSpec.actionButtonTopSpacer))

                    Text(
                        text = "Open full media application",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onOpenNotification(mediaNotification) }
                            .padding(vertical = layoutSpec.actionButtonVerticalPadding)
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaControlIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "mediaControlScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.size(24.dp)
        )
    }
}

private fun findMediaActionLabel(actionLabels: List<String>, vararg keywords: String): String? {
    if (actionLabels.isEmpty()) return null
    val normalizedKeywords = keywords.map { it.lowercase(Locale.getDefault()) }
    return actionLabels.firstOrNull { label ->
        val normalizedLabel = label.lowercase(Locale.getDefault())
        normalizedKeywords.any { keyword -> normalizedLabel.contains(keyword) }
    }
}

private fun isPauseLikeActionLabel(actionLabel: String?): Boolean {
    val normalized = actionLabel?.trim()?.lowercase(Locale.getDefault()).orEmpty()
    return normalized.contains("pause") || normalized.contains("stop")
}

@Composable
private fun LockStatusPill(isDeviceLocked: Boolean, minHeight: Dp) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .heightIn(min = minHeight)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isDeviceLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            contentDescription = if (isDeviceLocked) "Locked" else "Unlocked",
            tint = Color.White,
            modifier = Modifier.size(12.dp)
        )
    }
}

@Composable
private fun LockTopNotificationHighlights(
    notifications: List<CoverNotificationModel>,
    onNotificationOpen: (CoverNotificationModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val callNotifications = remember(notifications) {
        notifications.filter { it.isOngoing && isCallNotification(it) }.sortedByDescending { it.postTime }.take(3)
    }
    if (callNotifications.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        callNotifications.forEach { notification ->
            val iconBitmap = rememberPackageIconBitmap(notification.packageName)
            Surface(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable { onNotificationOpen(notification) },
                color = Color(0xFF1B5E20).copy(alpha = 0.6f),
                shape = CircleShape
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun isCallNotification(notification: CoverNotificationModel): Boolean {
    val text = "${notification.title} ${notification.previewText} ${notification.packageName}".lowercase(Locale.getDefault())
    return text.contains("call") || text.contains("dial") || text.contains("phone")
}

private data class NotificationProviderGroup(
    val packageName: String,
    val notifications: List<CoverNotificationModel>
)

private fun groupNotificationsByProvider(notifications: List<CoverNotificationModel>): List<NotificationProviderGroup> {
    return notifications
        .sortedByDescending { it.postTime }
        .groupBy { it.packageName }
        .map { (pkg, list) -> NotificationProviderGroup(pkg, list.sortedByDescending { it.postTime }) }
        .sortedByDescending { it.notifications.firstOrNull()?.postTime ?: 0L }
}

@Composable
private fun OngoingCollapseToggle(collapseOngoing: Boolean, ongoingCount: Int, onToggle: () -> Unit) {
    val label = if (collapseOngoing) "Show ongoing ($ongoingCount)" else "Hide ongoing"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.9f)
            )
            Icon(
                imageVector = if (collapseOngoing) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun NotificationsPanelTile(
    notifications: List<CoverNotificationModel>,
    wallpaperUri: String?,
    wallpaperScaleMode: WallpaperScaleMode,
    onNotificationOpen: (CoverNotificationModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var collapseOngoing by remember { mutableStateOf(false) }

    val ongoingNotifications = remember(notifications) {
        notifications.filter { it.isOngoing || !it.isClearable }
    }
    val importantNotifications = remember(notifications) {
        notifications.filterNot { it.isOngoing || !it.isClearable }
    }
    val ongoingGroupedNotifications = remember(ongoingNotifications) {
        groupNotificationsByProvider(ongoingNotifications)
    }
    val importantGroupedNotifications = remember(importantNotifications) {
        groupNotificationsByProvider(importantNotifications)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsNone,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "No new notifications",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = coverScreenContentPadding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (ongoingNotifications.isNotEmpty()) {
                item(key = "ongoing_toggle") {
                    OngoingCollapseToggle(
                        collapseOngoing = collapseOngoing,
                        ongoingCount = ongoingNotifications.size,
                        onToggle = { collapseOngoing = !collapseOngoing }
                    )
                }

                if (!collapseOngoing) {
                    items(
                        items = ongoingGroupedNotifications,
                        key = { "ongoing_${it.packageName}" }
                    ) { group ->
                        NotificationProviderGroupCard(
                            group = group,
                            onNotificationOpen = onNotificationOpen
                        )
                    }
                }
            }

            items(
                items = importantGroupedNotifications,
                key = { "important_${it.packageName}" }
            ) { group ->
                NotificationProviderGroupCard(
                    group = group,
                    onNotificationOpen = onNotificationOpen
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun NotificationProviderGroupCard(
    group: NotificationProviderGroup,
    onNotificationOpen: (CoverNotificationModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(16.dp)
    val providerIcon = rememberPackageIconBitmap(group.packageName)
    val providerLabel = rememberPackageLabel(group.packageName)
    var isExpanded by remember(group.packageName) { mutableStateOf(false) }
    val providerNotifications = group.notifications

    val visibleNotifications = if (isExpanded || providerNotifications.size <= 2) {
        providerNotifications
    } else {
        providerNotifications.take(2)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .coverGlassSurface(
                color = Color.White.copy(alpha = 0.08f),
                borderColor = Color.White.copy(alpha = 0.16f),
                shape = cardShape
            )
            .animateContentSize(animationSpec = spring()),
        color = Color.Transparent,
        shape = cardShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (providerIcon != null) {
                        Image(
                            bitmap = providerIcon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Text(
                    text = providerLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (providerNotifications.size > 1) {
                    Text(
                        text = providerNotifications.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                visibleNotifications.forEachIndexed { index, notification ->
                    NotificationGroupRow(
                        notification = notification,
                        onOpen = { onNotificationOpen(notification) },
                        onDismiss = {
                            CoverNotificationListenerService.dismissNotificationFromOverlay(
                                notification.notificationKey
                            )
                        }
                    )

                    if (index < visibleNotifications.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.05f))
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = providerNotifications.size > 2,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val hiddenCount = (providerNotifications.size - visibleNotifications.size).coerceAtLeast(0)
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isExpanded) "Show less" else "Show $hiddenCount more",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { isExpanded = !isExpanded }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationGroupRow(
    notification: CoverNotificationModel,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false, // Critical: preserves horizontal pager gesture
        enableDismissFromStartToEnd = notification.isClearable,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFFD32F2F).copy(alpha = 0.5f)
                    else -> Color.Transparent
                },
                animationSpec = spring(),
                label = "dismissColor"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(color)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Dismiss",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = notification.previewText.ifBlank { "Tap to open" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (notification.isOngoing || !notification.isClearable) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Ongoing",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun AppGridPageTile(
    apps: List<AppModel>,
    deferHydration: Boolean,
    isDeviceLocked: Boolean,
    onAppSelected: (AppModel) -> Unit,
    modifier: Modifier = Modifier
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
                text = "No launchable apps",
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
                    enabled = !isDeviceLocked,
                    onClick = { onAppSelected(app) }
                )
            }
        }
    }
}

@Composable
private fun GridPageLetterTooltip(letter: String?, visible: Boolean, modifier: Modifier = Modifier) {
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

private fun gridPageStartLetterForPagerPage(
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
private fun PageIndicator(
    pagerState: PagerState,
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
                    detectTapGestures { offset ->
                        if (indicatorWidthPx > 0) {
                            val localX = offset.x - with(density) { hitAreaHorizontalPadding.toPx() }
                            val fraction = (localX / indicatorWidthPx).coerceIn(0f, 1f)
                            val targetPage = (fraction * (pageCount - 1)).roundToInt()
                            requestPageScroll(targetPage)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
                .pointerInput(pageCount) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
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
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
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
private fun CoverDockRow(
    dockSlots: List<AppModel?>,
    isDeviceLocked: Boolean,
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
                    val iconBitmap = rememberPackageIconBitmap(app.packageName)
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
    enabled: Boolean,
    onClick: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val iconBitmap = rememberPackageIconBitmap(app.packageName)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .coverMinimumTouchTarget()
            .clickable(enabled = enabled) {
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

private fun resolveDockSlots(apps: List<AppModel>, dockPackageSlots: List<String?>): List<AppModel?> {
    val appByPackageName = apps.associateBy { it.packageName }
    val hasCustomSelection = dockPackageSlots.any { !it.isNullOrBlank() }
    if (!hasCustomSelection) {
        val defaults = apps.take(DOCK_SLOT_COUNT)
        return List(DOCK_SLOT_COUNT) { defaults.getOrNull(it) }
    }

    return List(DOCK_SLOT_COUNT) { index ->
        dockPackageSlots.getOrNull(index)?.trim()?.takeUnless { it.isEmpty() }?.let { appByPackageName[it] }
    }
}

@Composable
private fun CoverWallpaperLayer(
    wallpaperUri: String?,
    wallpaperScaleMode: WallpaperScaleMode,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val containerSize = LocalWindowInfo.current.containerSize
    val normalizedWallpaperUri = wallpaperUri?.trim().takeUnless { it.isNullOrEmpty() }
    var decodeRetryAttempt by remember(normalizedWallpaperUri) { mutableIntStateOf(0) }
    var lastGoodWallpaperBitmap by remember(normalizedWallpaperUri) { mutableStateOf<Bitmap?>(null) }
    val requestedWidthPx = remember(containerSize.width) { max(containerSize.width, 1) }
    val requestedHeightPx = remember(containerSize.height) { max(containerSize.height, 1) }
    val wallpaperCacheVersionToken = remember(normalizedWallpaperUri, decodeRetryAttempt) {
        normalizedWallpaperUri
            ?.takeUnless { it.isBlank() }
            ?.let { runCatching { resolveWallpaperCacheVersionToken(it.toUri()) }.getOrNull() }
    }

    val wallpaperBitmap by produceState<Bitmap?>(
        initialValue = lastGoodWallpaperBitmap,
        normalizedWallpaperUri,
        wallpaperCacheVersionToken,
        decodeRetryAttempt,
        context,
        requestedWidthPx,
        requestedHeightPx
    ) {
        value = if (normalizedWallpaperUri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    decodeSampledBitmapFromUri(
                        context = context,
                        uri = normalizedWallpaperUri.toUri(),
                        cacheVersionToken = wallpaperCacheVersionToken,
                        requestedWidthPx = requestedWidthPx,
                        requestedHeightPx = requestedHeightPx
                    )
                }.onFailure { error ->
                    Log.w(WALLPAPER_LOG_TAG, "Overlay wallpaper decode failed error=${error.message}")
                }.getOrNull()
            }
        }
    }

    LaunchedEffect(normalizedWallpaperUri, wallpaperBitmap) {
        if (normalizedWallpaperUri.isNullOrBlank()) {
            lastGoodWallpaperBitmap = null
            return@LaunchedEffect
        }
        if (wallpaperBitmap != null) {
            lastGoodWallpaperBitmap = wallpaperBitmap
            decodeRetryAttempt = 0
        }
    }

    LaunchedEffect(normalizedWallpaperUri, wallpaperBitmap, decodeRetryAttempt) {
        if (normalizedWallpaperUri.isNullOrBlank()) return@LaunchedEffect
        if (wallpaperBitmap != null) return@LaunchedEffect
        if (decodeRetryAttempt >= WALLPAPER_OVERLAY_RETRY_MAX_ATTEMPTS) return@LaunchedEffect

        delay(WALLPAPER_OVERLAY_RETRY_DELAY_MS.milliseconds)
        decodeRetryAttempt += 1
    }

    val bitmap = wallpaperBitmap ?: lastGoodWallpaperBitmap
    if (bitmap == null) {
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
        Image(
            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
            contentDescription = "Cover Wallpaper",
            contentScale = if (wallpaperScaleMode == WallpaperScaleMode.CROP) ContentScale.Crop else ContentScale.Fit,
            modifier = modifier
        )
    }
}

private fun decodeSampledBitmapFromUri(
    context: Context,
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

    WallpaperBitmapCache.get(cacheKey)?.let { return it }

    val nowElapsedMs = SystemClock.elapsedRealtime()
    if (WallpaperDecodeBackoff.shouldSkip(cacheKey, nowElapsedMs)) return null

    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openWallpaperInputStream(context, uri)?.use { boundsStream ->
        BitmapFactory.decodeStream(boundsStream, null, boundsOptions)
    } ?: run {
        return decodeWallpaperBitmapWithImageDecoder(
            context = context,
            uri = uri,
            cacheKey = cacheKey,
            requestedWidthPx = requestedWidthPx,
            requestedHeightPx = requestedHeightPx,
            nowElapsedMs = nowElapsedMs
        )
    }

    if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
        return decodeWallpaperBitmapWithImageDecoder(
            context = context,
            uri = uri,
            cacheKey = cacheKey,
            requestedWidthPx = requestedWidthPx,
            requestedHeightPx = requestedHeightPx,
            nowElapsedMs = nowElapsedMs
        )
    }

    val sampledOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, requestedWidthPx, requestedHeightPx)
        inPreferredConfig = Bitmap.Config.RGB_565
    }

    val decodedBitmap = openWallpaperInputStream(context, uri)?.use { decodeStream ->
        BitmapFactory.decodeStream(decodeStream, null, sampledOptions)
    } ?: decodeWallpaperBitmapWithImageDecoder(
        context = context,
        uri = uri,
        cacheKey = cacheKey,
        requestedWidthPx = requestedWidthPx,
        requestedHeightPx = requestedHeightPx,
        nowElapsedMs = nowElapsedMs
    )

    if (decodedBitmap != null) {
        WallpaperBitmapCache.put(cacheKey, decodedBitmap)
        WallpaperDecodeBackoff.clearFailure(cacheKey)
    } else {
        return decodeWallpaperBitmapWithImageDecoder(
            context = context,
            uri = uri,
            cacheKey = cacheKey,
            requestedWidthPx = requestedWidthPx,
            requestedHeightPx = requestedHeightPx,
            nowElapsedMs = nowElapsedMs
        )
    }

    return decodedBitmap
}

private fun decodeWallpaperBitmapWithImageDecoder(
    context: Context,
    uri: Uri,
    cacheKey: String,
    requestedWidthPx: Int,
    requestedHeightPx: Int,
    nowElapsedMs: Long
): Bitmap? {
    val source = when (uri.scheme) {
        "file" -> {
            val path = uri.path ?: run {
                WallpaperDecodeBackoff.markFailure(cacheKey, nowElapsedMs)
                return null
            }
            val file = File(path)
            if (!file.exists() || !file.isFile || !file.canRead()) {
                WallpaperDecodeBackoff.markFailure(cacheKey, nowElapsedMs)
                return null
            }
            ImageDecoder.createSource(file)
        }
        else -> runCatching { ImageDecoder.createSource(context.contentResolver, uri) }
            .getOrNull()
            ?: run {
                WallpaperDecodeBackoff.markFailure(cacheKey, nowElapsedMs)
                return null
            }
    }

    val decodedBitmap = runCatching {
        ImageDecoder.decodeBitmap(source) { decoder, imageInfo, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val sampleSize = calculateImageDecoderSampleSize(
                sourceWidth = imageInfo.size.width,
                sourceHeight = imageInfo.size.height,
                requestedWidthPx = requestedWidthPx,
                requestedHeightPx = requestedHeightPx
            )
            if (sampleSize > 1) {
                decoder.setTargetSampleSize(sampleSize)
            }
        }
    }.onFailure { error ->
        Log.w(WALLPAPER_LOG_TAG, "ImageDecoder wallpaper decode failed uri=$uri error=${error.message}")
    }.getOrNull()

    if (decodedBitmap != null) {
        WallpaperBitmapCache.put(cacheKey, decodedBitmap)
        WallpaperDecodeBackoff.clearFailure(cacheKey)
    } else {
        WallpaperDecodeBackoff.markFailure(cacheKey, nowElapsedMs)
    }

    return decodedBitmap
}

private fun resolveWallpaperCacheVersionToken(uri: Uri): String? {
    if (uri.scheme != "file") return null
    val path = uri.path ?: return null
    val file = File(path)
    if (!file.exists() || !file.isFile) return null
    return "${file.lastModified()}_${file.length()}"
}

private fun openWallpaperInputStream(context: Context, uri: Uri): InputStream? {
    return when (uri.scheme) {
        "file" -> uri.path?.let { path -> runCatching { File(path).inputStream() }.getOrNull() }
        else -> runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            ?: runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")
                    ?.let { descriptor ->
                        ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                    }
            }.getOrNull()
    }
}

private fun calculateImageDecoderSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    requestedWidthPx: Int,
    requestedHeightPx: Int
): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0) return 1
    val widthRatio = sourceWidth.toFloat() / requestedWidthPx.coerceAtLeast(1)
    val heightRatio = sourceHeight.toFloat() / requestedHeightPx.coerceAtLeast(1)
    return max(widthRatio, heightRatio).toInt().coerceAtLeast(1)
}

private fun resolvePackageIconVersionToken(packageManager: PackageManager, packageName: String): String? {
    val packageInfo = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    }.getOrNull() ?: return null

    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    return "$packageName|$versionCode|${packageInfo.lastUpdateTime}"
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

private fun currentCoverTime(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

private fun currentCoverDate(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))