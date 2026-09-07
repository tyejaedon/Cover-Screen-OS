@file:Suppress("FrequentlyChangingValue")

package com.tyejaedon.coverscreenos.ui.launcher

import android.Manifest
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.tyejaedon.coverscreenos.datastore.DEFAULT_KEYBOARD_STRATEGY
import com.tyejaedon.coverscreenos.datastore.DEFAULT_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.DEFAULT_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.InputTelemetryStore
import com.tyejaedon.coverscreenos.datastore.KeyboardStrategy
import com.tyejaedon.coverscreenos.datastore.MAX_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.MIN_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.WallpaperScaleMode
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.models.CoverNotificationModel
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import com.tyejaedon.coverscreenos.services.notifications.CoverNotificationListenerService
import com.tyejaedon.coverscreenos.ui.AppGridPageTile
import com.tyejaedon.coverscreenos.ui.CompactNowPlayingCard
import com.tyejaedon.coverscreenos.ui.CoverDockRow
import com.tyejaedon.coverscreenos.ui.CoverSearchUiTestTags
import com.tyejaedon.coverscreenos.ui.CoverWallpaperLayer
import com.tyejaedon.coverscreenos.ui.ExpandedNowPlayingPanel
import com.tyejaedon.coverscreenos.ui.GridPageLetterTooltip
import com.tyejaedon.coverscreenos.ui.NotificationsPanelTile
import com.tyejaedon.coverscreenos.ui.OverlayIconThumbnailDiskCache
import com.tyejaedon.coverscreenos.ui.PageIndicator
import com.tyejaedon.coverscreenos.ui.filterAppsForSearchQuery
import com.tyejaedon.coverscreenos.ui.gridPageStartLetterForPagerPage
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.graphics.createBitmap
import com.tyejaedon.coverscreenos.ui.launcher.WidgetGridPageTile
import com.tyejaedon.coverscreenos.ui.launcher.rememberVoiceInputHandle
import com.tyejaedon.coverscreenos.ui.openMediaApplicationFromOverlay
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTextStyles
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private const val GRID_COLUMNS = 4
private const val GRID_ROWS = 3
private const val APPS_PER_GRID_PAGE = GRID_COLUMNS * GRID_ROWS
private const val DOCK_SLOT_COUNT = 4
private const val PAGER_HAPTIC_INTERVAL_MS = 150L
private const val OVERLAY_PERF_LOG_TAG = "CoverOverlayPerf"
private const val APP_SCAN_DEFER_AFTER_FIRST_FRAME_MS = 120L
private const val ICON_CACHE_MAX_ENTRIES = 256
private const val ICON_PREWARM_COUNT = APPS_PER_GRID_PAGE * 2
private const val ICON_PREWARM_BATCH_SIZE = 6
private const val ICON_PREWARM_BATCH_DELAY_MS = 32L
private const val NOTIFICATION_PANEL_PAGE_INDEX = 0
private const val LOCK_PAGER_PAGE_INDEX = 1
private const val FIRST_APP_GRID_PAGE_INDEX = 2
private const val SEARCH_QUERY_MAX_LENGTH = 64

private val WIDGET_TILE_VERTICAL_SWIPE_THRESHOLD = 72.dp

private data class CoverDisplayPolishSpec(
    val statusChipMinHeight: Dp,
    val dockVerticalOffset: Dp
)

private data class BatteryStatusSnapshot(
    val levelPercent: Int,
    val isCharging: Boolean
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
        val receiver = object : android.content.BroadcastReceiver() {
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
internal object OverlayBitmapIconCache {
    private val cache = LruCache<String, ImageBitmap>(ICON_CACHE_MAX_ENTRIES)

    fun get(packageName: String): ImageBitmap? {
        return synchronized(cache) { cache.get(packageName) }
    }

    fun put(packageName: String, bitmap: ImageBitmap) {
        synchronized(cache) { cache.put(packageName, bitmap) }
    }
}


private fun drawableToImageBitmap(drawable: Drawable, sizePx: Int = 128): ImageBitmap? {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap.asImageBitmap()
    }
    val bitmap = createBitmap(
        if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else sizePx,
        if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else sizePx
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}

internal suspend fun resolvePackageIconBitmap(
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
internal fun rememberPackageIconBitmap(packageName: String): ImageBitmap? {
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
internal fun rememberPackageLabel(packageName: String): String {
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
    wallpaperBlurRadiusDp: Float = DEFAULT_WALLPAPER_BLUR_RADIUS_DP,
    keyboardStrategy: KeyboardStrategy = DEFAULT_KEYBOARD_STRATEGY,
    onKeyboardStrategyChanged: (KeyboardStrategy) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
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
    var searchQuery by remember { mutableStateOf("") }
    var isCoverKeyboardVisible by remember { mutableStateOf(true) }

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
    val filteredApps = remember(apps, searchQuery) {
        filterAppsForSearchQuery(
            apps = apps,
            query = searchQuery
        )
    }
    val appPages = remember(filteredApps) {
        if (filteredApps.isEmpty()) listOf(emptyList()) else filteredApps.chunked(APPS_PER_GRID_PAGE)
    }
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
        // Shared HazeState: the wallpaper below is the blur "source"; app tiles &
        // notification cards apply hazeEffect on top so their text stays legible
        // against arbitrary wallpapers.
        val hazeState = remember { HazeState() }
        Box(modifier = Modifier.fillMaxSize()) {
            CoverWallpaperLayer(
                wallpaperUri = wallpaperUri,
                wallpaperScaleMode = wallpaperScaleMode,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
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
                        onClick = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            isCoverKeyboardVisible = false
                        }
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    InteractiveSection(
                        notifications = notifications,
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
                        searchQuery = searchQuery,
                        onSearchQueryChanged = { updatedQuery ->
                            searchQuery = updatedQuery.take(SEARCH_QUERY_MAX_LENGTH)
                        },
                        isCoverKeyboardVisible = isCoverKeyboardVisible,
                        onCoverKeyboardVisibilityChanged = { visible ->
                            isCoverKeyboardVisible = visible
                        },
                        keyboardStrategy = keyboardStrategy,
                        onKeyboardStrategyChanged = onKeyboardStrategyChanged,
                        pagerState = pagerState,
                        hazeState = hazeState,
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
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    isCoverKeyboardVisible: Boolean,
    onCoverKeyboardVisibilityChanged: (Boolean) -> Unit,
    keyboardStrategy: KeyboardStrategy,
    onKeyboardStrategyChanged: (KeyboardStrategy) -> Unit,
    pagerState: PagerState,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val widgetSwipeThresholdPx = with(density) { WIDGET_TILE_VERTICAL_SWIPE_THRESHOLD.toPx() }
    val searchFieldFocusRequester = remember { FocusRequester() }
    var showPageLetterTooltip by remember { mutableStateOf(false) }
    var isIndicatorScrubbing by remember { mutableStateOf(false) }
    val maxPagerPage = (totalPageCount - 1).coerceAtLeast(0)
    var hintedPagerPage by remember { mutableIntStateOf(0) }
    var voiceHintMessage by remember { mutableStateOf<String?>(null) }
    var isVoiceListening by remember { mutableStateOf(false) }
    var isWidgetTileVisible by remember { mutableStateOf(false) }
    val inputTelemetryStore = remember(context) { InputTelemetryStore(context.applicationContext) }

    val voiceInputHandle = rememberVoiceInputHandle(
        onPartialResult = { partialResult ->
            onSearchQueryChanged(partialResult.take(SEARCH_QUERY_MAX_LENGTH))
            voiceHintMessage = "Listening..."
        },
        onFinalResult = { finalResult ->
            onSearchQueryChanged(finalResult.take(SEARCH_QUERY_MAX_LENGTH))
            voiceHintMessage = null
        },
        onListeningStateChanged = { isListening ->
            isVoiceListening = isListening
        },
        onError = { errorMessage ->
            voiceHintMessage = errorMessage
        }
    )

    fun dismissSearchInput() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onCoverKeyboardVisibilityChanged(false)
        voiceInputHandle.stopListening()
    }

    fun showSystemImePicker() {
        val pickerShown = runCatching {
            val inputMethodManager = context.getSystemService<InputMethodManager>()
                ?: return@runCatching false
            inputMethodManager.showInputMethodPicker()
            true
        }.getOrElse { error ->
            Log.w(OVERLAY_PERF_LOG_TAG, "IME picker request failed: ${error.message}")
            false
        }

        if (!pickerShown) {
            voiceHintMessage = "Keyboard picker is unavailable on this device."
        }

        uiScope.launch {
            runCatching { inputTelemetryStore.recordImeShowRequest(success = pickerShown) }
        }
    }

    fun openWidgetTileFromLockscreen() {
        if (pagerState.currentPage != LOCK_PAGER_PAGE_INDEX || isWidgetTileVisible) return
        isWidgetTileVisible = true
    }

    fun returnToLockscreenFromWidgetTile() {
        dismissSearchInput()
        isWidgetTileVisible = false
        uiScope.launch {
            if (pagerState.currentPage != LOCK_PAGER_PAGE_INDEX) {
                pagerState.scrollToPage(LOCK_PAGER_PAGE_INDEX)
            }
        }
    }

    LaunchedEffect(isWidgetTileVisible) {
        if (!isWidgetTileVisible) {
            dismissSearchInput()
            return@LaunchedEffect
        }
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onCoverKeyboardVisibilityChanged(true)
        voiceInputHandle.stopListening()
    }
    val hintedGridLetter = remember(hintedPagerPage, appPages) {
        gridPageStartLetterForPagerPage(
            hintedPagerPage,
            appPages,
            FIRST_APP_GRID_PAGE_INDEX
        )
    }

    LaunchedEffect(pagerState, maxPagerPage) {
        snapshotFlow {
            ((pagerState.currentPage + pagerState.currentPageOffsetFraction).roundToInt()).coerceIn(0, maxPagerPage)
        }.collect { hintedPage ->
            hintedPagerPage = hintedPage
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress, isIndicatorScrubbing) {
        if (pagerState.isScrollInProgress || isIndicatorScrubbing) {
            showPageLetterTooltip = true
        } else {
            delay(280.milliseconds)
            if (!pagerState.isScrollInProgress && !isIndicatorScrubbing) showPageLetterTooltip = false
        }
    }

    val appGridEmptyStateLabel = if (searchQuery.isBlank()) {
        "No launchable apps"
    } else {
        "No apps match \"$searchQuery\""
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    // Shrink the pager region so the PageIndicator below sits higher in the viewport.
                    .padding(bottom = 20.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                        .testTag(CoverSearchUiTestTags.OVERLAY_PAGER),
                    userScrollEnabled = !isWidgetTileVisible,
                    pageSpacing = 10.dp,
                    beyondViewportPageCount = 0
                ) { pageIndex ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (pageIndex) {
                            NOTIFICATION_PANEL_PAGE_INDEX -> {
                                NotificationsPanelTile(
                                    notifications = notifications,
                                    onNotificationOpen = { model ->
                                        dismissSearchInput()
                                        CoverNotificationListenerService.openNotificationFromOverlay(
                                            context,
                                            model
                                        )
                                    },
                                    hazeState = hazeState,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            LOCK_PAGER_PAGE_INDEX -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag(CoverSearchUiTestTags.LOCKSCREEN_TILE_PAGE)
                                        .pointerInput(isWidgetTileVisible, widgetSwipeThresholdPx) {
                                            var cumulativeDrag = 0f
                                            detectVerticalDragGestures(
                                                onVerticalDrag = { _, dragAmount ->
                                                    cumulativeDrag += dragAmount
                                                },
                                                onDragEnd = {
                                                    if (cumulativeDrag >= widgetSwipeThresholdPx) {
                                                        openWidgetTileFromLockscreen()
                                                    }
                                                    cumulativeDrag = 0f
                                                },
                                                onDragCancel = { cumulativeDrag = 0f }
                                            )
                                        }
                                ) {
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
                                            dismissSearchInput()
                                            CoverNotificationListenerService.openNotificationFromOverlay(context, model)
                                        },
                                        onAppSelected = { app ->
                                            dismissSearchInput()
                                            onAppSelected(app)
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            else -> {
                                val gridIndex = pageIndex - FIRST_APP_GRID_PAGE_INDEX
                                AppGridPageTile(
                                    apps = appPages.getOrElse(gridIndex) { emptyList() },
                                    deferHydration = deferGridHydration,
                                    emptyStateLabel = appGridEmptyStateLabel,
                                    onAppSelected = { app ->
                                        dismissSearchInput()
                                        onAppSelected(app)
                                    },
                                    hazeState = hazeState,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isWidgetTileVisible,
                    enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }) + fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    WidgetGridPageTile(
                        searchQuery = searchQuery,
                        onQueryChanged = { updatedQuery ->
                            onSearchQueryChanged(updatedQuery.take(SEARCH_QUERY_MAX_LENGTH))
                            voiceHintMessage = null
                        },
                        onSearchFieldTapped = { onCoverKeyboardVisibilityChanged(true) },
                        onVoiceInputTap = {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                voiceHintMessage =
                                    "Grant microphone permission in setup for voice search."
                                return@WidgetGridPageTile
                            }

                            if (!voiceInputHandle.isAvailable) {
                                voiceHintMessage = "Voice recognition unavailable on this device."
                                return@WidgetGridPageTile
                            }

                            if (isVoiceListening) {
                                voiceInputHandle.stopListening()
                            } else {
                                voiceHintMessage = "Listening..."
                                voiceInputHandle.startListening()
                            }
                        },
                        isVoiceListening = isVoiceListening,
                        voiceHintMessage = voiceHintMessage,
                        focusRequester = searchFieldFocusRequester,
                        onDismissInputTap = { dismissSearchInput() },
                        onDismissTileTap = { returnToLockscreenFromWidgetTile() },
                        onSwipeUpToReturn = { returnToLockscreenFromWidgetTile() },
                        swipeThresholdPx = widgetSwipeThresholdPx,
                        keyboardStrategy = keyboardStrategy,
                        onKeyboardStrategyToggle = {
                            // Reset any in-flight input state so the two strategies never
                            // fight over focus / soft-keyboard visibility.
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            voiceInputHandle.stopListening()

                            val nextStrategy = keyboardStrategy.toggled()
                            onCoverKeyboardVisibilityChanged(nextStrategy == KeyboardStrategy.T9)
                            onKeyboardStrategyChanged(nextStrategy)
                        },
                        onOpenImePicker = { showSystemImePicker() },
                        isCoverKeyboardVisible = isCoverKeyboardVisible,
                        onCharTyped = { char ->
                            onSearchQueryChanged((searchQuery + char).take(SEARCH_QUERY_MAX_LENGTH))
                            voiceHintMessage = null
                        },
                        onBackspacePressed = {
                            if (searchQuery.isNotEmpty()) {
                                onSearchQueryChanged(searchQuery.dropLast(1))
                            }
                            voiceHintMessage = null
                        },
                        onDonePressed = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            onCoverKeyboardVisibilityChanged(false)
                        },
                        modifier = Modifier.fillMaxSize(),
                        onClearPressed = {
                            onSearchQueryChanged("")

                        },
                        hazeState = hazeState

                    )
                }

                GridPageLetterTooltip(
                    letter = hintedGridLetter,
                    visible = !isWidgetTileVisible && showPageLetterTooltip && hintedGridLetter != null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                )
            }

            if (!isWidgetTileVisible) {
                PageIndicator(
                    pagerState = pagerState,
                    pageCount = totalPageCount,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    onScrubActiveChanged = { active -> isIndicatorScrubbing = active }
                )
            }
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


                if (batteryStatus != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (isDeviceLocked) {
                            LockStatusPill(
                                isDeviceLocked = true,
                                minHeight = displayPolishSpec.statusChipMinHeight
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
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
                        onOpenMediaApp = {
                            openMediaApplicationFromOverlay(
                                context,
                                notifications
                            )
                        },
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
                CoverNotificationListenerService.launchNotificationSourceApp(
                    context,
                    media.packageName
                )
            },
            onAction = { media, actionLabel ->
                CoverNotificationListenerService.performNotificationAction(
                    media.notificationKey,
                    actionLabel
                )
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

private fun resolvePackageIconVersionToken(packageManager: PackageManager, packageName: String): String? {
    val packageInfo = runCatching {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    }.getOrNull() ?: return null

    val versionCode = packageInfo.longVersionCode
    return "$packageName|$versionCode|${packageInfo.lastUpdateTime}"
}

private fun currentCoverTime(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

private fun currentCoverDate(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
