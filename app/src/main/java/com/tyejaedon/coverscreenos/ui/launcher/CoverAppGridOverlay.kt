@file:Suppress("FrequentlyChangingValue")

package com.tyejaedon.coverscreenos.ui.launcher

import android.Manifest
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.tyejaedon.coverscreenos.notifications.CoverNotificationListenerService
import com.tyejaedon.coverscreenos.ui.AppGridPageTile
import com.tyejaedon.coverscreenos.ui.CompactNowPlayingCard
import com.tyejaedon.coverscreenos.ui.OneUiDockRow
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
import com.tyejaedon.coverscreenos.ui.theme.CoverOSBatteryChargingColor
import com.tyejaedon.coverscreenos.ui.theme.CoverOSCallActiveColor
import com.tyejaedon.coverscreenos.ui.theme.CoverOSDestructiveColor
import com.tyejaedon.coverscreenos.ui.theme.oneui.CoverOneUIType
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiChip
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private const val GRID_COLUMNS = 4
private const val GRID_ROWS = 3
private const val APPS_PER_GRID_PAGE = GRID_COLUMNS * GRID_ROWS
private const val DOCK_SLOT_COUNT = 4
private const val PAGER_HAPTIC_INTERVAL_MS = 150L
private const val OVERLAY_PERF_LOG_TAG = "CoverOverlayPerf"
private const val APP_SCAN_DEFER_AFTER_FIRST_FRAME_MS = 120L
private const val ICON_PREWARM_COUNT = APPS_PER_GRID_PAGE * 2
private const val ICON_PREWARM_BATCH_SIZE = 6
private const val ICON_PREWARM_BATCH_DELAY_MS = 32L
private const val NOTIFICATION_PANEL_PAGE_INDEX = 0
private const val LOCK_PAGER_PAGE_INDEX = 1
private const val WIDGET_HOST_PAGE_INDEX = 2
private const val FIRST_APP_GRID_PAGE_INDEX = 3
private const val SEARCH_QUERY_MAX_LENGTH = 64

private val WIDGET_TILE_VERTICAL_SWIPE_THRESHOLD = 72.dp
private val QUICK_SETTINGS_VERTICAL_SWIPE_THRESHOLD = 44.dp
private val LOCKED_SWIPE_UNLOCK_THRESHOLD = 36.dp

internal fun resolveAccessiblePageCount(isDeviceLocked: Boolean, totalPageCount: Int): Int {
    val safeTotal = totalPageCount.coerceAtLeast(1)
    return if (isDeviceLocked) {
        (LOCK_PAGER_PAGE_INDEX + 1).coerceAtMost(safeTotal)
    } else {
        safeTotal
    }
}

@Composable
fun CoverAppGridOverlay(
    repository: PackageManagerAppScannerRepository,
    onAppSelected: (AppModel) -> Unit,
    isDeviceLocked: Boolean,
    modifier: Modifier = Modifier,
    dockPackageSlots: List<String?> = List(DOCK_SLOT_COUNT) { null },
    dockSlotFourAllApps: Boolean = false,
    isDockVisible: Boolean = true,
    wallpaperUri: String? = null,
    wallpaperScaleMode: WallpaperScaleMode = WallpaperScaleMode.CROP,
    wallpaperDimAmount: Float = DEFAULT_WALLPAPER_DIM_AMOUNT,
    wallpaperBlurRadiusDp: Float = DEFAULT_WALLPAPER_BLUR_RADIUS_DP,
    wallpaperCropX: Float = 0f,
    wallpaperCropY: Float = 0f,
    wallpaperZoom: Float = 1f,
    wallpaperRotation: Int = 0,
    keyboardStrategy: KeyboardStrategy = DEFAULT_KEYBOARD_STRATEGY,
    onKeyboardStrategyChanged: (KeyboardStrategy) -> Unit = {},
    widgetIds: List<Int> = emptyList(),
    onRemoveWidget: (Int) -> Unit = {},
    onAddWidgetRequest: () -> Unit = {},
    onRequestUnlock: () -> Unit = {},
    onRequestScreenOff: () -> Unit = {},
    onRequestSystemShade: () -> Unit = {},
    onOpenCustomize: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val packageManager = remember(context) { context.packageManager }
    val widgetHostManager = remember(context) {
        com.tyejaedon.coverscreenos.ui.widgets.CoverAppWidgetHostManager(context)
    }
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
    val accessiblePageCount = resolveAccessiblePageCount(
        isDeviceLocked = isDeviceLocked,
        totalPageCount = totalPageCount
    )
    val pagerState = rememberPagerState(
        initialPage = LOCK_PAGER_PAGE_INDEX,
        pageCount = { accessiblePageCount }
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
                wallpaperCropX = wallpaperCropX,
                wallpaperCropY = wallpaperCropY,
                wallpaperZoom = wallpaperZoom,
                wallpaperRotation = wallpaperRotation,
                wallpaperDimAmount = wallpaperDimAmount,
                wallpaperBlurRadiusDp = wallpaperBlurRadiusDp,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
            )

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
                        dockSlotFourAllApps = dockSlotFourAllApps,
                        timeLabel = timeLabel,
                        dateLabel = dateLabel,
                        totalPageCount = accessiblePageCount,
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
                        widgetIds = widgetIds,
                        widgetHostManager = widgetHostManager,
                        onRemoveWidget = onRemoveWidget,
                        onAddWidgetRequest = onAddWidgetRequest,
                        onRequestUnlock = onRequestUnlock,
                        onRequestScreenOff = onRequestScreenOff,
                        onRequestSystemShade = onRequestSystemShade,
                        onOpenCustomize = onOpenCustomize,
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
    dockSlotFourAllApps: Boolean,
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
    widgetIds: List<Int>,
    widgetHostManager: com.tyejaedon.coverscreenos.ui.widgets.CoverAppWidgetHostManager,
    onRemoveWidget: (Int) -> Unit,
    onAddWidgetRequest: () -> Unit,
    onRequestUnlock: () -> Unit,
    onRequestScreenOff: () -> Unit,
    onRequestSystemShade: () -> Unit,
    onOpenCustomize: () -> Unit,
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
    val quickSettingsSwipeThresholdPx = with(density) { QUICK_SETTINGS_VERTICAL_SWIPE_THRESHOLD.toPx() }
    val lockedSwipeThresholdPx = with(density) { LOCKED_SWIPE_UNLOCK_THRESHOLD.toPx() }
    val latestIsDeviceLocked by androidx.compose.runtime.rememberUpdatedState(isDeviceLocked)
    val latestOnRequestUnlock by androidx.compose.runtime.rememberUpdatedState(onRequestUnlock)
    val latestOnRequestSystemShade by androidx.compose.runtime.rememberUpdatedState(onRequestSystemShade)
    val searchFieldFocusRequester = remember { FocusRequester() }
    var showPageLetterTooltip by remember { mutableStateOf(false) }
    var isIndicatorScrubbing by remember { mutableStateOf(false) }
    val maxPagerPage = (totalPageCount - 1).coerceAtLeast(0)
    var hintedPagerPage by remember { mutableIntStateOf(0) }
    var voiceHintMessage by remember { mutableStateOf<String?>(null) }
    var isVoiceListening by remember { mutableStateOf(false) }
    var isWidgetTileVisible by remember { mutableStateOf(false) }
    var isQuickSettingsVisible by remember { mutableStateOf(false) }
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
        if (pagerState.currentPage != LOCK_PAGER_PAGE_INDEX || isWidgetTileVisible || isQuickSettingsVisible) return
        if (latestIsDeviceLocked) {
            dismissSearchInput()
            latestOnRequestUnlock()
            return
        }
        isWidgetTileVisible = true
    }

    fun openQuickSettingsFromLockscreen() {
        if (pagerState.currentPage != LOCK_PAGER_PAGE_INDEX || isQuickSettingsVisible || isWidgetTileVisible) return
        isQuickSettingsVisible = true
    }

    fun closeQuickSettings() {
        isQuickSettingsVisible = false
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

    LaunchedEffect(isDeviceLocked) {
        if (!isDeviceLocked) return@LaunchedEffect
        if (isWidgetTileVisible) {
            dismissSearchInput()
            isWidgetTileVisible = false
        }
        if (pagerState.currentPage > LOCK_PAGER_PAGE_INDEX) {
            pagerState.scrollToPage(LOCK_PAGER_PAGE_INDEX)
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

    // Symmetric with the T9 path's onCoverKeyboardVisibilityChanged(true): when the
    // active strategy is SYSTEM_IME (either on initial tile open or after a mid-session
    // toggle), request focus and show the soft keyboard so Gboard/Samsung Keyboard
    // appears immediately instead of requiring a second manual tap on the field.
    LaunchedEffect(keyboardStrategy, isWidgetTileVisible) {
        if (isWidgetTileVisible && keyboardStrategy == KeyboardStrategy.SYSTEM_IME) {
            runCatching {
                searchFieldFocusRequester.requestFocus()
                keyboardController?.show()
            }
        }
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

    val systemShadeNestedScrollConnection = remember(quickSettingsSwipeThresholdPx, lockedSwipeThresholdPx) {
        object : NestedScrollConnection {
            private var unconsumedDownwardDrag = 0f
            private var triggered = false
            private var lockedForwardDrag = 0f
            private var pendingUnlockOnRelease = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || isWidgetTileVisible || isQuickSettingsVisible) {
                    return Offset.Zero
                }
                if (!latestIsDeviceLocked) {
                    lockedForwardDrag = 0f
                    pendingUnlockOnRelease = false
                    return Offset.Zero
                }
                val onLockEdge = pagerState.currentPage == LOCK_PAGER_PAGE_INDEX &&
                    pagerState.currentPageOffsetFraction >= -0.01f
                if (!onLockEdge) {
                    lockedForwardDrag = 0f
                    pendingUnlockOnRelease = false
                    return Offset.Zero
                }
                if (available.x < 0f) {
                    lockedForwardDrag += -available.x
                    if (lockedForwardDrag >= lockedSwipeThresholdPx) {
                        pendingUnlockOnRelease = true
                    }
                    // Consume forward swipe on Lock page while locked so Widgets/Apps never peek.
                    return Offset(available.x, 0f)
                } else if (available.x > 0f) {
                    lockedForwardDrag = 0f
                    pendingUnlockOnRelease = false
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput || isWidgetTileVisible || isQuickSettingsVisible) {
                    return Offset.Zero
                }
                if (available.y > 0f) {
                    unconsumedDownwardDrag += available.y
                    if (!triggered && unconsumedDownwardDrag >= quickSettingsSwipeThresholdPx) {
                        triggered = true
                        latestOnRequestSystemShade()
                    }
                } else if (available.y < 0f) {
                    unconsumedDownwardDrag = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pendingUnlockOnRelease &&
                    latestIsDeviceLocked &&
                    !isWidgetTileVisible &&
                    !isQuickSettingsVisible &&
                    pagerState.currentPage == LOCK_PAGER_PAGE_INDEX
                ) {
                    pendingUnlockOnRelease = false
                    lockedForwardDrag = 0f
                    dismissSearchInput()
                    latestOnRequestUnlock()
                }
                unconsumedDownwardDrag = 0f
                triggered = false
                lockedForwardDrag = 0f
                pendingUnlockOnRelease = false
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                unconsumedDownwardDrag = 0f
                triggered = false
                lockedForwardDrag = 0f
                pendingUnlockOnRelease = false
                return Velocity.Zero
            }
        }
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = displayPolishSpec.cutoutStartPadding,
                    end = displayPolishSpec.cutoutEndPadding
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(systemShadeNestedScrollConnection)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                        .testTag(CoverSearchUiTestTags.OVERLAY_PAGER),
                    userScrollEnabled = !isWidgetTileVisible && !isQuickSettingsVisible,
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
                                        .pointerInput(isQuickSettingsVisible, quickSettingsSwipeThresholdPx) {
                                            var cumulativeDrag = 0f
                                            detectVerticalDragGestures(
                                                onVerticalDrag = { _, dragAmount ->
                                                    cumulativeDrag += dragAmount
                                                },
                                                onDragEnd = {
                                                    if (cumulativeDrag <= -quickSettingsSwipeThresholdPx) {
                                                        openQuickSettingsFromLockscreen()
                                                    } else if (cumulativeDrag >= quickSettingsSwipeThresholdPx) {
                                                        onRequestSystemShade()
                                                    }
                                                    cumulativeDrag = 0f
                                                },
                                                onDragCancel = { cumulativeDrag = 0f }
                                            )
                                        }
                                        // Double tap on empty space turns the screen off. Long press
                                        // opens customize. Buttons, apps and notifications consume
                                        // their own taps first.
                                        .pointerInput(isQuickSettingsVisible, isWidgetTileVisible) {
                                            detectTapGestures(
                                                onDoubleTap = {
                                                    if (!isQuickSettingsVisible && !isWidgetTileVisible) {
                                                        dismissSearchInput()
                                                        onRequestScreenOff()
                                                    }
                                                },
                                                onLongPress = {
                                                    if (!isQuickSettingsVisible && !isWidgetTileVisible) {
                                                        dismissSearchInput()
                                                        onOpenCustomize()
                                                    }
                                                }
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
                                        dockSlotFourAllApps = dockSlotFourAllApps,
                                        onOpenAllApps = {
                                            if (latestIsDeviceLocked) {
                                                dismissSearchInput()
                                                latestOnRequestUnlock()
                                            } else {
                                                uiScope.launch {
                                                    pagerState.animateScrollToPage(FIRST_APP_GRID_PAGE_INDEX)
                                                }
                                            }
                                        },
                                        onNotificationOpen = { model ->
                                            dismissSearchInput()
                                            CoverNotificationListenerService.openNotificationFromOverlay(context, model)
                                        },
                                        onAppSelected = { app ->
                                            dismissSearchInput()
                                            onAppSelected(app)
                                        },
                                        onOpenSearch = { openWidgetTileFromLockscreen() },
                                        onRequestUnlock = {
                                            dismissSearchInput()
                                            onRequestUnlock()
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            WIDGET_HOST_PAGE_INDEX -> {
                                com.tyejaedon.coverscreenos.ui.widgets.CoverAppWidgetHostTile(
                                    widgetIds = widgetIds,
                                    widgetHostManager = widgetHostManager,
                                    onRemoveWidget = onRemoveWidget,
                                    onAddWidgetRequest = onAddWidgetRequest,
                                    hazeState = hazeState,
                                    modifier = Modifier.fillMaxSize()
                                )
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
                        onDonePressed = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            onCoverKeyboardVisibilityChanged(false)
                        },
                        modifier = Modifier.fillMaxSize(),
                        hazeState = hazeState

                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isQuickSettingsVisible,
                    enter = slideInVertically(initialOffsetY = { fullHeight -> -fullHeight }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { fullHeight -> -fullHeight }) + fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    QuickSettingsPanelTile(
                        onDismiss = { closeQuickSettings() },
                        swipeUpThresholdPx = widgetSwipeThresholdPx,
                        hazeState = hazeState,
                        onOpenCustomize = {
                            closeQuickSettings()
                            onOpenCustomize()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                GridPageLetterTooltip(
                    letter = hintedGridLetter,
                    visible = !isWidgetTileVisible && !isQuickSettingsVisible && showPageLetterTooltip && hintedGridLetter != null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                )
            }

            if (!isWidgetTileVisible && !isQuickSettingsVisible) {
                PageIndicator(
                    pagerState = pagerState,
                    pageCount = totalPageCount,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    onScrubActiveChanged = { active -> isIndicatorScrubbing = active }
                )
            }
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
