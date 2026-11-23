@file:Suppress("FrequentlyChangingValue")

package com.tyejaedon.coverscreenos.ui.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.tyejaedon.coverscreenos.datastore.DEFAULT_KEYBOARD_STRATEGY
import com.tyejaedon.coverscreenos.datastore.DEFAULT_WALLPAPER_BLUR_RADIUS_DP
import com.tyejaedon.coverscreenos.datastore.DEFAULT_WALLPAPER_DIM_AMOUNT
import com.tyejaedon.coverscreenos.datastore.InputTelemetryStore
import com.tyejaedon.coverscreenos.datastore.KeyboardStrategy
import com.tyejaedon.coverscreenos.datastore.WallpaperScaleMode
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.models.CoverNotificationModel
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import com.tyejaedon.coverscreenos.notifications.CoverNotificationListenerService
import com.tyejaedon.coverscreenos.ui.AppGridPageTile
import com.tyejaedon.coverscreenos.ui.CoverSearchUiTestTags
import com.tyejaedon.coverscreenos.ui.CoverWallpaperLayer
import com.tyejaedon.coverscreenos.ui.GridPageLetterTooltip
import com.tyejaedon.coverscreenos.ui.NotificationsPanelTile
import com.tyejaedon.coverscreenos.ui.PageIndicator
import com.tyejaedon.coverscreenos.ui.filterAppsForSearchQuery
import com.tyejaedon.coverscreenos.ui.gridPageStartLetterForPagerPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import com.tyejaedon.coverscreenos.overlay.CoverBottomGestureBar
import com.tyejaedon.coverscreenos.ui.launcher.WidgetGridPageTile
import com.tyejaedon.coverscreenos.ui.launcher.rememberVoiceInputHandle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** Fixed 4x3 cover launcher app grid dimensions and derived 12-app page capacity. */
private const val GRID_COLUMNS = 4
private const val GRID_ROWS = 3
private const val APPS_PER_GRID_PAGE = GRID_COLUMNS * GRID_ROWS

/** Number of shortcut slots displayed in the bottom One UI dock row. */
private const val DOCK_SLOT_COUNT = 4

/** Cooldown in milliseconds throttling haptic ticks when swiping rapidly across pager pages. */
private const val PAGER_HAPTIC_INTERVAL_MS = 150L

/** Logcat tag for overlay startup hydration timing and IME picker diagnostics. */
private const val OVERLAY_PERF_LOG_TAG = "CoverOverlayPerf"

/** Delay after the first frame commits before launching the background PackageManager app scan. */
private const val APP_SCAN_DEFER_AFTER_FIRST_FRAME_MS = 120L

/** Batched icon prewarm configuration covering the first two app-grid pages without IO spikes. */
private const val ICON_PREWARM_COUNT = APPS_PER_GRID_PAGE * 2
private const val ICON_PREWARM_BATCH_SIZE = 6
private const val ICON_PREWARM_BATCH_DELAY_MS = 32L

// Fixed leading pager pages: 0 = Notifications, 1 = Lock/Dock, 2 = Widget Host, 3+ = App Grid pages.
private const val NOTIFICATION_PANEL_PAGE_INDEX = 0
private const val LOCK_PAGER_PAGE_INDEX = 1
private const val WIDGET_HOST_PAGE_INDEX = 2
private const val FIRST_APP_GRID_PAGE_INDEX = 3

/** Hard character cap applied to search queries from T9, system IME, and voice input. */
private const val SEARCH_QUERY_MAX_LENGTH = 64

/** Upward/downward swipe distance required to dismiss the search sheet or quick settings panel. */
private val WIDGET_TILE_VERTICAL_SWIPE_THRESHOLD = 72.dp

/** Vertical drag distance on the lockscreen page to open quick settings (up) or system shade (down). */
private val QUICK_SETTINGS_VERTICAL_SWIPE_THRESHOLD = 44.dp

/** Forward horizontal drag distance on the locked screen required to trigger unlock on release. */
private val LOCKED_SWIPE_UNLOCK_THRESHOLD = 36.dp

/**
 * Limits the horizontal pager's exposed page count while the device is locked.
 *
 * When [isDeviceLocked] is `true`, only pages up through [LOCK_PAGER_PAGE_INDEX] (notifications and
 * lockscreen/dock) remain reachable so widgets and the full app drawer stay hidden until unlocked.
 */
internal fun resolveAccessiblePageCount(isDeviceLocked: Boolean, totalPageCount: Int): Int {
    val safeTotal = totalPageCount.coerceAtLeast(1)
    return if (isDeviceLocked) {
        (LOCK_PAGER_PAGE_INDEX + 1).coerceAtMost(safeTotal)
    } else {
        safeTotal
    }
}

/**
 * Root Compose overlay for the cover screen launcher, hosted inside the secondary-display
 * accessibility window.
 *
 * Coordinates staged startup hydration (`LaunchedEffect` + `withFrameNanos` deferral before
 * running `PackageManagerAppScannerRepository.scanInstalledApplications()` via `produceState`),
 * live clock/date tickers, sticky battery monitoring ([rememberBatteryStatus]), active
 * notification flow collection, batched background icon prewarming into `OverlayBitmapIconCache`,
 * shared `HazeState` blur sourcing (`hazeSource`) on [CoverWallpaperLayer], and multi-page
 * [HorizontalPager] state initialized at [LOCK_PAGER_PAGE_INDEX].
 */
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
    widgetHostManager: com.tyejaedon.coverscreenos.ui.widgets.CoverAppWidgetHostManager? = null,
    onRemoveWidget: (Int) -> Unit = {},
    onAddWidgetRequest: () -> Unit = {},
    onRequestUnlock: () -> Unit = {},
    onRequestScreenOff: () -> Unit = {},
    onRequestSystemShade: () -> Unit = {},
    onOpenCustomize: () -> Unit = {},
    recentAppPackages: List<String> = emptyList(),
    isRecentsVisible: Boolean = false,
    onRequestRecents: () -> Unit = {},
    onDismissRecentApp: (String) -> Unit = {},
    onClearAllRecentApps: () -> Unit = {},
    onDismissRecents: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val packageManager = remember(context) { context.packageManager }
    // Uses the caller-provided AppWidgetHostManager or creates a fallback scoped to this Context.
    val resolvedWidgetHostManager = remember(context, widgetHostManager) {
        widgetHostManager ?: com.tyejaedon.coverscreenos.ui.widgets.CoverAppWidgetHostManager(context)
    }
    // Uptime timestamp captured at initial composition to measure time-to-first-frame hydration.
    val overlayComposeStartMs = remember { SystemClock.uptimeMillis() }

    // Flips true after the first frame commits so grid pages swap skeleton placeholders for real app tiles.
    var isGridHydrated by remember { mutableStateOf(false) }
    // Gates PackageManager app scanning until after the initial lockscreen frame finishes drawing.
    var shouldLoadApps by remember { mutableStateOf(false) }

    // Defer heavy PackageManager scanning until after the first frame commits so the lockscreen
    // overlay appears immediately without stalling initial layout/draw.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(APP_SCAN_DEFER_AFTER_FIRST_FRAME_MS.milliseconds)
        shouldLoadApps = true
        isGridHydrated = true
        Log.d(OVERLAY_PERF_LOG_TAG, "firstFrameCommittedMs=${SystemClock.uptimeMillis() - overlayComposeStartMs}")
    }

    // Async state holding the scanned launchable apps list once shouldLoadApps transitions to true.
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

    // Periodic wall-clock and date tickers decoupled from Intent.ACTION_TIME_TICK receivers.
    // Formatted local time string refreshed every 30s for the lockscreen clock.
    val timeLabel by produceState(initialValue = currentCoverTime()) {
        while (true) {
            value = currentCoverTime()
            delay(30_000L.milliseconds)
        }
    }
    // Formatted short local date string refreshed every 60s beneath the lockscreen clock.
    val dateLabel by produceState(initialValue = currentCoverDate()) {
        while (true) {
            value = currentCoverDate()
            delay(60_000L.milliseconds)
        }
    }
    // Remembered live battery percentage and charging status from sticky/broadcast updates.
    val batteryStatus = rememberBatteryStatus()

    // Current scanned app list and live active notifications streamed from NotificationListenerService.
    val apps = appsState.value
    val notifications by CoverNotificationListenerService.activeNotificationsFlow().collectAsState()
    // Active search filter shared across the search overlay (T9/IME/voice) and app grid pagination.
    var searchQuery by remember { mutableStateOf("") }
    // Tracks whether the built-in T9 keyboard panel should remain expanded inside the search overlay.
    var isCoverKeyboardVisible by remember { mutableStateOf(true) }

    // Prewarm icon bitmaps into LRU (and disk thumbnail cache) in small IO batches to avoid spikes.
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

    // Resolved 4-slot AppModel list matching configured dock packages (or default leading apps).
    val dockApps = remember(apps, dockPackageSlots) {
        resolveDockSlots(apps = apps, dockPackageSlots = dockPackageSlots)
    }
    // Scanned apps matching searchQuery, later chunked into 4x3 pages.
    val filteredApps = remember(apps, searchQuery) {
        filterAppsForSearchQuery(
            apps = apps,
            query = searchQuery
        )
    }
    // Paginated 12-item (4x3) slices of filteredApps, always keeping at least one page for empty state.
    val appPages = remember(filteredApps) {
        if (filteredApps.isEmpty()) listOf(emptyList()) else filteredApps.chunked(APPS_PER_GRID_PAGE)
    }
    // Total pager pages across fixed panels + app grid, and lock-restricted reachable page count.
    val totalPageCount = FIRST_APP_GRID_PAGE_INDEX + appPages.size
    val accessiblePageCount = resolveAccessiblePageCount(
        isDeviceLocked = isDeviceLocked,
        totalPageCount = totalPageCount
    )
    // Shared HorizontalPager state anchored initially on LOCK_PAGER_PAGE_INDEX and bounded by lock state.
    val pagerState = rememberPagerState(
        initialPage = LOCK_PAGER_PAGE_INDEX,
        pageCount = { accessiblePageCount }
    )


    // True when settled on an app-grid page; displayPolishSpec adapts chip/dock/cutout padding to cover size.
    val isAppGridPage = pagerState.currentPage >= FIRST_APP_GRID_PAGE_INDEX
    val displayPolishSpec = rememberCoverDisplayPolishSpec()

    val hapticFeedback = LocalHapticFeedback.current
    // Guards against emitting a haptic tick on first composition and rate-limits rapid page swipes.
    var hasInitializedPager by remember { mutableStateOf(false) }
    var lastPagerHapticTimestampMs by remember { mutableStateOf(0L) }

    // Emit throttled haptic ticks when settling onto a new page, skipping initial composition.
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
                val recentApps = remember(apps, recentAppPackages) {
                    val appByPkg = apps.associateBy { it.packageName }
                    recentAppPackages.map { pkg ->
                        appByPkg[pkg] ?: AppModel(
                            name = pkg.substringAfterLast('.').ifBlank { pkg },
                            packageName = pkg
                        )
                    }
                }
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
                        widgetHostManager = resolvedWidgetHostManager,
                        onRemoveWidget = onRemoveWidget,
                        onAddWidgetRequest = onAddWidgetRequest,
                        onRequestUnlock = onRequestUnlock,
                        onRequestScreenOff = onRequestScreenOff,
                        onRequestSystemShade = onRequestSystemShade,
                        onOpenCustomize = onOpenCustomize,
                        recentApps = recentApps,
                        isRecentsVisible = isRecentsVisible,
                        onRequestRecents = onRequestRecents,
                        onDismissRecentApp = onDismissRecentApp,
                        onClearAllRecentApps = onClearAllRecentApps,
                        onDismissRecents = onDismissRecents,
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

/**
 * Interactive pager container and gesture/sheet coordinator for the cover overlay.
 *
 * Hosts the multi-page [HorizontalPager] (notifications, lock/dock, widget host, and chunked 4x3
 * app grid pages), attaches a [NestedScrollConnection] for system shade pull-down and locked
 * swipe-to-unlock gestures, coordinates slide-over search (`WidgetGridPageTile`) and quick-settings
 * (`QuickSettingsPanelTile`) sheets with Haze glassmorphism (`hazeState`), and binds [PageIndicator]
 * scrubbing to the floating [GridPageLetterTooltip].
 */
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
    recentApps: List<AppModel>,
    isRecentsVisible: Boolean,
    onRequestRecents: () -> Unit,
    onDismissRecentApp: (String) -> Unit,
    onClearAllRecentApps: () -> Unit,
    onDismissRecents: () -> Unit,
    pagerState: PagerState,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // Density-converted pixel drag thresholds for vertical sheet dismissals, quick settings/shade, and swipe-unlock.
    val widgetSwipeThresholdPx = with(density) { WIDGET_TILE_VERTICAL_SWIPE_THRESHOLD.toPx() }
    val quickSettingsSwipeThresholdPx = with(density) { QUICK_SETTINGS_VERTICAL_SWIPE_THRESHOLD.toPx() }
    val lockedSwipeThresholdPx = with(density) { LOCKED_SWIPE_UNLOCK_THRESHOLD.toPx() }
    // Keep fresh lock/callback references for long-lived remembered gesture and scroll handlers.
    val latestIsDeviceLocked by androidx.compose.runtime.rememberUpdatedState(isDeviceLocked)
    val latestOnRequestUnlock by androidx.compose.runtime.rememberUpdatedState(onRequestUnlock)
    val latestOnRequestSystemShade by androidx.compose.runtime.rememberUpdatedState(onRequestSystemShade)
    // FocusRequester driving immediate focus/soft-keyboard display when SYSTEM_IME strategy opens.
    val searchFieldFocusRequester = remember { FocusRequester() }
    // Controls visibility of the floating alphabetical letter badge while scrolling or scrubbing pages.
    var showPageLetterTooltip by remember { mutableStateOf(false) }
    // True while the user is pressing/dragging the bottom PageIndicator strip.
    var isIndicatorScrubbing by remember { mutableStateOf(false) }
    // Upper page clamp and nearest rounded page target used to look up the alphabetical tooltip letter.
    val maxPagerPage = (totalPageCount - 1).coerceAtLeast(0)
    var hintedPagerPage by remember { mutableIntStateOf(0) }
    // Transient voice-search status/error hint and active SpeechRecognizer listening flag.
    var voiceHintMessage by remember { mutableStateOf<String?>(null) }
    var isVoiceListening by remember { mutableStateOf(false) }
    // Modal sheet visibility toggles for the slide-up app search sheet and slide-down quick settings drawer.
    var isWidgetTileVisible by remember { mutableStateOf(false) }
    var isQuickSettingsVisible by remember { mutableStateOf(false) }
    // DataStore telemetry helper recording IME picker launch attempts.
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
        if (pagerState.currentPage != LOCK_PAGER_PAGE_INDEX || isWidgetTileVisible || isQuickSettingsVisible || isRecentsVisible) return
        if (latestIsDeviceLocked) {
            dismissSearchInput()
            latestOnRequestUnlock()
            return
        }
        isWidgetTileVisible = true
    }

    fun openQuickSettingsFromLockscreen() {
        if (pagerState.currentPage != LOCK_PAGER_PAGE_INDEX || isQuickSettingsVisible || isWidgetTileVisible || isRecentsVisible) return
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

    // Collapse unlocked-only search/widget/recents overlays and snap back to the lock page when locked.
    LaunchedEffect(isDeviceLocked) {
        if (!isDeviceLocked) return@LaunchedEffect
        onDismissRecents()
        if (isWidgetTileVisible) {
            dismissSearchInput()
            isWidgetTileVisible = false
        }
        if (pagerState.currentPage > LOCK_PAGER_PAGE_INDEX) {
            pagerState.scrollToPage(LOCK_PAGER_PAGE_INDEX)
        }
    }

    // Reset focus, soft keyboard, and voice listening cleanly whenever the search tile toggles.
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
    // First letter of the first app on hintedPagerPage (null when pointing at non-app pages).
    val hintedGridLetter = remember(hintedPagerPage, appPages) {
        gridPageStartLetterForPagerPage(
            hintedPagerPage,
            appPages,
            FIRST_APP_GRID_PAGE_INDEX
        )
    }

    // Track the nearest integer page continuously during drags/scrubs for the alphabetical tooltip.
    LaunchedEffect(pagerState, maxPagerPage) {
        snapshotFlow {
            ((pagerState.currentPage + pagerState.currentPageOffsetFraction).roundToInt()).coerceIn(0, maxPagerPage)
        }.collect { hintedPage ->
            hintedPagerPage = hintedPage
        }
    }

    // Keep the alphabetical tooltip visible while paging or scrubbing, with a brief settle delay.
    LaunchedEffect(pagerState.isScrollInProgress, isIndicatorScrubbing) {
        if (pagerState.isScrollInProgress || isIndicatorScrubbing) {
            showPageLetterTooltip = true
        } else {
            delay(280.milliseconds)
            if (!pagerState.isScrollInProgress && !isIndicatorScrubbing) showPageLetterTooltip = false
        }
    }

    // Empty-state copy distinguishing an unscanned/empty device list from a search query with zero matches.
    val appGridEmptyStateLabel = if (searchQuery.isBlank()) {
        "No launchable apps"
    } else {
        "No apps match \"$searchQuery\""
    }

    // Intercept unconsumed downward drags to open the system notification shade, and intercept
    // forward horizontal swipes on the locked home page to trigger unlock without revealing apps.
    val systemShadeNestedScrollConnection = remember(quickSettingsSwipeThresholdPx, lockedSwipeThresholdPx) {
        object : NestedScrollConnection {
            // Accumulated downward drag and one-shot guard preventing duplicate shade launches per gesture.
            private var unconsumedDownwardDrag = 0f
            private var triggered = false
            // Forward horizontal swipe distance on the locked page and release flag to invoke unlock.
            private var lockedForwardDrag = 0f
            private var pendingUnlockOnRelease = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || isWidgetTileVisible || isQuickSettingsVisible || isRecentsVisible) {
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
                if (source != NestedScrollSource.UserInput || isWidgetTileVisible || isQuickSettingsVisible || isRecentsVisible) {
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
                    !isRecentsVisible &&
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
                // beyondViewportPageCount = 0 avoids composing offscreen pages on the compact cover display;
                // horizontal paging freezes while search/quick-settings/recents modal overlays are visible.
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(CoverSearchUiTestTags.OVERLAY_PAGER),
                    userScrollEnabled = !isWidgetTileVisible && !isQuickSettingsVisible && !isRecentsVisible,
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
                                        // Swipe up opens cover quick settings; swipe down expands the system shade.
                                        .pointerInput(isQuickSettingsVisible, quickSettingsSwipeThresholdPx) {
                                            // Total vertical drag delta across the gesture to distinguish up vs down swipes.
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
                                    onLaunchWidgetApp = { packageName ->
                                        dismissSearchInput()
                                        onAppSelected(
                                            AppModel(
                                                name = packageName,
                                                packageName = packageName
                                            )
                                        )
                                    },
                                    hazeState = hazeState,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            else -> {
                                // Zero-based index into appPages after skipping the fixed leading pager pages.
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

                androidx.compose.animation.AnimatedVisibility(
                    visible = isRecentsVisible && !isDeviceLocked,
                    enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }) + fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    CoverRecentsPanelTile(
                        recentApps = recentApps,
                        notifications = notifications,
                        onAppSelected = { app ->
                            onDismissRecents()
                            dismissSearchInput()
                            onAppSelected(app)
                        },
                        onDismissApp = onDismissRecentApp,
                        onClearAll = onClearAllRecentApps,
                        onTriggerNativeRecents = onRequestRecents,
                        onDismissPanel = onDismissRecents,
                        hazeState = hazeState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                GridPageLetterTooltip(
                    letter = hintedGridLetter,
                    visible = !isWidgetTileVisible && !isQuickSettingsVisible && !isRecentsVisible && showPageLetterTooltip && hintedGridLetter != null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                )
            }

            if (!isWidgetTileVisible && !isQuickSettingsVisible) {
                if (!isRecentsVisible) {
                    PageIndicator(
                        pagerState = pagerState,
                        pageCount = totalPageCount,
                        modifier = Modifier.fillMaxWidth().padding(bottom = if (isDeviceLocked) 32.dp else 12.dp),
                        onScrubActiveChanged = { active -> isIndicatorScrubbing = active }
                    )
                }
                if (!isDeviceLocked) {
                    CoverBottomGestureBar(
                        onHomeGesture = {
                            if (isRecentsVisible) {
                                onDismissRecents()
                            } else {
                                uiScope.launch {
                                    pagerState.animateScrollToPage(LOCK_PAGER_PAGE_INDEX)
                                }
                            }
                        },
                        onRecentsGesture = {
                            if (!isRecentsVisible) {
                                onRequestRecents()
                            }
                        },
                        onBackGesture = {
                            if (isRecentsVisible) {
                                onDismissRecents()
                            } else if (pagerState.currentPage != LOCK_PAGER_PAGE_INDEX) {
                                uiScope.launch {
                                    pagerState.animateScrollToPage(LOCK_PAGER_PAGE_INDEX)
                                }
                            }
                        },
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}


/**
 * Resolves the 4 dock slots to matching [AppModel] entries, falling back to the first
 * [DOCK_SLOT_COUNT] scanned apps when no custom dock packages have been configured.
 */
private fun resolveDockSlots(apps: List<AppModel>, dockPackageSlots: List<String?>): List<AppModel?> {
    // Fast lookup table mapping installed package names to their scanned AppModel metadata.
    val appByPackageName = apps.associateBy { it.packageName }
    // True when the user configured at least one custom dock slot instead of using default leading apps.
    val hasCustomSelection = dockPackageSlots.any { !it.isNullOrBlank() }
    if (!hasCustomSelection) {
        val defaults = apps.take(DOCK_SLOT_COUNT)
        return List(DOCK_SLOT_COUNT) { defaults.getOrNull(it) }
    }

    return List(DOCK_SLOT_COUNT) { index ->
        dockPackageSlots.getOrNull(index)?.trim()?.takeUnless { it.isEmpty() }?.let { appByPackageName[it] }
    }
}
