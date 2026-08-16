@file:Suppress("FrequentlyChangingValue")

package com.tyejaedon.coverscreenos.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import android.os.SystemClock
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

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
private const val LOCK_SEARCH_RESULT_LIMIT = 8
private const val LOCK_SEARCH_HISTORY_LIMIT = 6

// One UI leans on a near-monochrome glass surface with a single accent for
// interactive/selected states, rather than a busy set of tinted Material
// containers. These alphas are tuned so cards read as "frosted glass" over
// the blurred wallpaper instead of opaque Material cards.
private const val GRID_PANEL_FILL_ALPHA = 0.46f
private const val GRID_PANEL_BORDER_ALPHA = 0.40f
private const val DOCK_FILL_ALPHA = 0.42f
private const val DOCK_BORDER_ALPHA = 0.50f
private const val SEARCH_RESULT_FILL_ALPHA = 0.55f
private const val ICON_DISABLED_TINT_ALPHA = 0.65f
private val GRID_TILE_GAP = 10.dp
private val GRID_CONTENT_PADDING = coverScreenContentPadding(horizontal = 6.dp, vertical = 4.dp)

private data class CoverDisplayPolishSpec(
    val statusChipMinHeight: Dp,
    val searchPanelMinHeight: Dp,
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
                searchPanelMinHeight = 100.dp,
                dockVerticalOffset = (-2).dp
            )
            containerHeightDp <= 760.dp -> CoverDisplayPolishSpec(
                statusChipMinHeight = 30.dp,
                searchPanelMinHeight = 108.dp,
                dockVerticalOffset = (-4).dp
            )
            else -> CoverDisplayPolishSpec(
                statusChipMinHeight = 32.dp,
                searchPanelMinHeight = 116.dp,
                dockVerticalOffset = (-6).dp
            )
        }
    }
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
    // Scan installed applications off the main thread and expose a UI-safe list.
    val appsState = produceState(initialValue = emptyList<AppModel>(), key1 = repository) {
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
    val isAppGridPage = pagerState.currentPage >= 1
    val constrainedWallpaperBlur = wallpaperBlurRadiusDp.coerceIn(
        MIN_WALLPAPER_BLUR_RADIUS_DP,
        MAX_WALLPAPER_BLUR_RADIUS_DP
    )
    val effectiveWallpaperBlur = if (isAppGridPage && !pagerState.isScrollInProgress) {
        constrainedWallpaperBlur
    } else {
        0f
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

            // One UI darkens toward the bottom edge only enough to guarantee
            // label/icon legibility — the wallpaper itself stays the hero.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = if (isAppGridPage) constrainedWallpaperDim * 0.35f else 0f),
                                Color.Black.copy(alpha = if (isAppGridPage) constrainedWallpaperDim else 0f)
                            )
                        )
                    )
            )
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
                        allApps = apps,
                        appPages = appPages,
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
    allApps: List<AppModel>,
    appPages: List<List<AppModel>>,
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
    val panelShape = RoundedCornerShape(
        topStart = CoverOSCornerRadiusLarge,
        topEnd = CoverOSCornerRadiusLarge
    )
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
                    Modifier.coverGlassSurface(
                        fillAlpha = GRID_PANEL_FILL_ALPHA,
                        borderAlpha = GRID_PANEL_BORDER_ALPHA,
                        shape = panelShape
                    )
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
                    beyondViewportPageCount = 1
                ) { pageIndex ->
                    val scale = 1f
                    val alpha = 1f

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
                                allApps = allApps,
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
                                isDeviceLocked = isDeviceLocked,
                                pageVisibility = alpha,
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
    allApps: List<AppModel>,
    timeLabel: String,
    dateLabel: String,
    displayPolishSpec: CoverDisplayPolishSpec,
    isDeviceLocked: Boolean,
    isDockVisible: Boolean,
    dockSlots: List<AppModel?>,
    onAppSelected: (AppModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDockMode by rememberSaveable(isDockVisible) { mutableStateOf(isDockVisible) }
    if (!isDockVisible && isDockMode) {
        isDockMode = false
    }
    val isSearchMode = !isDockMode
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var searchFocusRequestToken by rememberSaveable { mutableIntStateOf(0) }
    val normalizedQuery = searchQuery.trim()
    val searchedApps = remember(allApps, normalizedQuery, isDeviceLocked) {
        if (normalizedQuery.isBlank() || isDeviceLocked) {
            emptyList()
        } else {
            rankSearchResults(
                allApps = allApps,
                query = normalizedQuery,
                limit = LOCK_SEARCH_RESULT_LIMIT
            )
        }
    }
    val shouldShowSearchPanel = !isDeviceLocked && isSearchMode

    LaunchedEffect(isDockMode, isDeviceLocked) {
        if (isDockMode || isDeviceLocked) {
            searchQuery = ""
        }
    }

    // Tile 0 reads like a real lock screen: huge glanceable clock sitting
    // directly on the wallpaper, a lock-state pill, and bottom mode controls
    // anchored to the thumb-reachable bottom edge.
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
                color = Color.White.copy(alpha = 0.78f)
            )
        }

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
                BottomModeSwitch(
                    isDockMode = isDockMode,
                    canUseDock = isDockVisible,
                    onModeSelected = { selectedDockMode ->
                        isDockMode = selectedDockMode
                        if (selectedDockMode) {
                            searchQuery = ""
                        } else {
                            searchFocusRequestToken += 1
                        }
                    },
                    modifier = Modifier.wrapContentWidth()
                )

                AnimatedVisibility(
                    visible = shouldShowSearchPanel,
                    enter = fadeIn(animationSpec = tween(durationMillis = 160)) +
                        expandVertically(animationSpec = tween(durationMillis = 220)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
                        shrinkVertically(animationSpec = tween(durationMillis = 160))
                ) {
                    LockSearchPopupCard(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        searchedApps = searchedApps,
                        recentSearches = searchHistory,
                        focusRequestToken = searchFocusRequestToken,
                        onRecentSearchSelected = { selectedQuery ->
                            searchQuery = selectedQuery
                            searchFocusRequestToken += 1
                        },
                        onClearRecentSearches = { searchHistory = emptyList() },
                        onAppSelected = { selectedApp ->
                            searchHistory = pushSearchHistory(searchHistory, searchQuery)
                            onAppSelected(selectedApp)
                            searchQuery = ""
                        },
                        minHeight = displayPolishSpec.searchPanelMinHeight,
                        enabled = !isDeviceLocked,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                AnimatedVisibility(
                    visible = isDockMode,
                    enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
                        expandVertically(animationSpec = tween(durationMillis = 220)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 140)) +
                        shrinkVertically(animationSpec = tween(durationMillis = 180))
                ) {
                    CoverDockRow(
                        dockSlots = dockSlots,
                        isDeviceLocked = isDeviceLocked,
                        onAppSelected = onAppSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = displayPolishSpec.dockVerticalOffset)
                    )
                }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun BottomModeSwitch(
    isDockMode: Boolean,
    canUseDock: Boolean,
    onModeSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomModeOption(
            selected = isDockMode && canUseDock,
            icon = Icons.Filled.Layers,
            label = "Dock",
            enabled = canUseDock,
            onClick = {
                if (!canUseDock || isDockMode) return@BottomModeOption
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onModeSelected(true)
            }
        )

        BottomModeOption(
            selected = !isDockMode,
            icon = Icons.Filled.Search,
            label = "Search",
            enabled = true,
            onClick = {
                if (!isDockMode) return@BottomModeOption
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onModeSelected(false)
            }
        )
    }
}

@Composable
private fun BottomModeOption(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            com.tyejaedon.coverscreenos.ui.theme.CoverOSPrimary.copy(alpha = 0.24f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 180),
        label = "bottomModeOptionContainer$label"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            com.tyejaedon.coverscreenos.ui.theme.CoverOSPrimary
        } else if (!enabled) {
            Color.White.copy(alpha = 0.44f)
        } else {
            Color.White.copy(alpha = 0.86f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "bottomModeOptionContent$label"
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
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
            .background(Color.White.copy(alpha = 0.16f))
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
private fun CoverSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    focusRequestToken: Int,
    onSearchAction: () -> Unit,
    onClearQuery: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(focusRequestToken, enabled) {
        if (enabled && focusRequestToken > 0) {
            focusRequester.requestFocus()
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        enabled = enabled,
        placeholder = {
            Text(
                text = "Search apps",
                color = Color.White.copy(alpha = 0.55f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = if (value.isNotBlank()) {
            {
                IconButton(onClick = onClearQuery, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(onSearch = {
            onSearchAction()
            focusManager.clearFocus()
        }),
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            disabledTextColor = Color.White.copy(alpha = 0.4f),
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
            disabledContainerColor = Color.White.copy(alpha = 0.06f),
            focusedBorderColor = com.tyejaedon.coverscreenos.ui.theme.CoverOSPrimary,
            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
            disabledBorderColor = Color.White.copy(alpha = 0.08f),
            cursorColor = com.tyejaedon.coverscreenos.ui.theme.CoverOSPrimary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    )
}

@Composable
private fun LockSearchPopupCard(
    query: String,
    onQueryChange: (String) -> Unit,
    searchedApps: List<AppModel>,
    recentSearches: List<String>,
    focusRequestToken: Int,
    onRecentSearchSelected: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onAppSelected: (AppModel) -> Unit,
    minHeight: Dp,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val searchCardShape = RoundedCornerShape(CoverOSCornerRadiusMedium)
    Column(
        modifier = modifier
            .coverGlassSurface(
                fillAlpha = 0.5f,
                borderAlpha = 0.56f,
                shape = searchCardShape
            )
            .heightIn(min = minHeight)
            .animateContentSize()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val launchTopResult = {
            val topResult = searchedApps.firstOrNull()
            if (topResult != null) {
                onAppSelected(topResult)
            }
        }
        CoverSearchField(
            value = query,
            onValueChange = onQueryChange,
            enabled = enabled,
            focusRequestToken = focusRequestToken,
            onSearchAction = launchTopResult,
            onClearQuery = { onQueryChange("") }
        )

        if (query.trim().isBlank()) {
            if (recentSearches.isNotEmpty()) {
                LockSearchHistoryRow(
                    searches = recentSearches,
                    onSearchSelected = onRecentSearchSelected,
                    onClearHistory = onClearRecentSearches
                )
            } else {
                Text(
                    text = "Type to search installed apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
                )
            }
        } else if (searchedApps.isEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "No matching apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f)
                )
                Text(
                    text = lockSearchNoResultGuidance(query),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.58f)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                searchedApps.forEach { app ->
                    LockSearchResultRow(
                        app = app,
                        enabled = enabled,
                        onAppSelected = onAppSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun LockSearchHistoryRow(
    searches: List<String>,
    onSearchSelected: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent searches",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.64f)
            )
            Text(
                text = "Clear",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.clickable(onClick = onClearHistory)
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(searches, key = { it }) { search ->
                SearchHistoryChip(
                    text = search,
                    onClick = { onSearchSelected(search) }
                )
            }
        }
    }
}

@Composable
private fun SearchHistoryChip(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LockSearchResultRow(
    app: AppModel,
    enabled: Boolean,
    onAppSelected: (AppModel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CoverOSCornerRadiusSmall))
            .background(Color.White.copy(alpha = SEARCH_RESULT_FILL_ALPHA * 0.14f))
            .clickable(enabled = enabled) { onAppSelected(app) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
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
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = app.name,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
                color = Color.White.copy(alpha = 0.7f)
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
                scaleIn(animationSpec = tween(durationMillis = 100), initialScale = 0.9f),
        exit = fadeOut(animationSpec = tween(durationMillis = 130)) +
                scaleOut(animationSpec = tween(durationMillis = 130), targetScale = 0.9f),
        modifier = modifier
    ) {
        // A circular index badge (fast-scroll style) reads more native than
        // a boxed rounded-rect tooltip.
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(com.tyejaedon.coverscreenos.ui.theme.CoverOSPrimary.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter ?: "",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.Black,
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
                            .background(Color.White.copy(alpha = 0.12f))
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
        modifier = modifier
            .coverGlassSurface(
                fillAlpha = DOCK_FILL_ALPHA,
                borderAlpha = DOCK_BORDER_ALPHA,
                shape = RoundedCornerShape(28.dp)
            )
            .coverScreenPadding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(DOCK_SLOT_COUNT) { index ->
            val app = dockSlots.getOrNull(index)
            val isSlotEnabled = app != null && !isDeviceLocked
            val slotAlpha by animateFloatAsState(
                targetValue = if (app != null && isDeviceLocked) ICON_DISABLED_TINT_ALPHA else 1f,
                animationSpec = tween(durationMillis = 180),
                label = "dockSlotAlpha$index"
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .coverMinimumTouchTarget()
                    .graphicsLayer { alpha = slotAlpha }
                    .clip(RoundedCornerShape(CoverOSCornerRadiusSmall))
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
                    )
                }
            }
        }
    }
}

@Composable
private fun AppGridPlaceholderTile(visibilityProgress: Float) {
    val clampedProgress = visibilityProgress.coerceIn(0f, 1f)
    val placeholderAlpha = clampedProgress * 0.4f
    val placeholderScale = 0.97f + (0.03f * clampedProgress)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .graphicsLayer {
                alpha = placeholderAlpha
                scaleX = placeholderScale
                scaleY = placeholderScale
            },
        contentAlignment = Alignment.TopCenter
    ) {
        // A quiet dashed-feeling dot rather than a boxed placeholder card —
        // reserves the grid slot without competing for attention.
        Box(
            modifier = Modifier
                .padding(top = 24.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f))
        )
    }
}

@Composable
private fun AppGridTile(
    app: AppModel,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tileAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else ICON_DISABLED_TINT_ALPHA,
        animationSpec = tween(durationMillis = 180),
        label = "appGridTileAlpha"
    )
    val hapticFeedback = LocalHapticFeedback.current

    // Icon + label float directly over the blurred wallpaper with no card
    // background — the authentic One UI app-drawer treatment — while still
    // keeping the >=48dp interactive bounds required for cover-screen use.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .coverMinimumTouchTarget()
            .clip(RoundedCornerShape(CoverOSCornerRadiusMedium))
            .graphicsLayer { alpha = tileAlpha }
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
                    .shadow(elevation = if (enabled) 3.dp else 0.dp, shape = RoundedCornerShape(16.dp), clip = false)
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
                        if (imageView.tag !== app.iconDrawable) {
                            imageView.setImageDrawable(app.iconDrawable)
                            imageView.tag = app.iconDrawable
                        }
                    },
                    modifier = Modifier.size(46.dp)
                )
            }

            Text(
                text = app.name,
                style = CoverOSTextStyles.AppLabelText,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.6f),
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
                .padding(top = 4.dp, end = 10.dp)
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
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            TapDisabledGlyph()
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
                .size(9.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White, CircleShape)
        )
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(1.dp)
                .graphicsLayer { rotationZ = -42f }
                .background(Color.White, RoundedCornerShape(1.dp))
        )
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

private data class RankedSearchResult(
    val app: AppModel,
    val matchRank: Int,
    val tokenStartHits: Int
)

internal fun rankSearchResults(
    allApps: List<AppModel>,
    query: String,
    limit: Int = LOCK_SEARCH_RESULT_LIMIT
): List<AppModel> {
    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    if (normalizedQuery.isBlank()) return emptyList()
    val queryTokens = normalizedQuery.split("\\s+".toRegex()).filter { it.isNotBlank() }

    return allApps.asSequence()
        .mapNotNull { app ->
            val normalizedName = app.name.lowercase(Locale.getDefault())
            val normalizedPackage = app.packageName.lowercase(Locale.getDefault())
            val tokenStartHits = queryTokens.count { token ->
                normalizedName.split("\\s+".toRegex()).any { word -> word.startsWith(token) }
            }
            val acronym = buildAppNameAcronym(normalizedName)
            val rank = when {
                normalizedName == normalizedQuery -> 0
                normalizedPackage == normalizedQuery -> 1
                normalizedName.startsWith(normalizedQuery) -> 2
                tokenStartHits == queryTokens.size && queryTokens.isNotEmpty() -> 3
                normalizedName.contains(normalizedQuery) -> 4
                normalizedPackage.contains(normalizedQuery) -> 5
                acronym.startsWith(normalizedQuery) -> 6
                else -> null
            }

            rank?.let {
                RankedSearchResult(
                    app = app,
                    matchRank = it,
                    tokenStartHits = tokenStartHits
                )
            }
        }
        .sortedWith(
            compareBy<RankedSearchResult> { it.matchRank }
                .thenByDescending { it.tokenStartHits }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.app.name }
                .thenBy { it.app.name }
        )
        .map { it.app }
        .take(limit.coerceAtLeast(1))
        .toList()
}

internal fun pushSearchHistory(
    currentHistory: List<String>,
    query: String,
    maxEntries: Int = LOCK_SEARCH_HISTORY_LIMIT
): List<String> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return currentHistory

    val withoutDuplicate = currentHistory.filterNot { existing ->
        existing.equals(normalized, ignoreCase = true)
    }
    return (listOf(normalized) + withoutDuplicate).take(maxEntries.coerceAtLeast(1))
}

internal fun lockSearchNoResultGuidance(query: String): String {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank()) {
        return "Try app name, package name, or initials."
    }
    return "Try a shorter keyword, package name, or initials (for example: 'sst')."
}

private fun buildAppNameAcronym(normalizedName: String): String {
    if (normalizedName.isBlank()) return ""
    return normalizedName
        .split("\\s+".toRegex())
        .filter { it.isNotBlank() }
        .mapNotNull { token -> token.firstOrNull() }
        .joinToString(separator = "")
}

@Composable
private fun CoverWallpaperLayer(
    wallpaperUri: String?,
    wallpaperScaleMode: WallpaperScaleMode,
    blurRadiusDp: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val requestedWidthPx = remember(configuration.screenWidthDp, density) {
        max(with(density) { configuration.screenWidthDp.dp.roundToPx() }, 1)
    }
    val requestedHeightPx = remember(configuration.screenHeightDp, density) {
        max(with(density) { configuration.screenHeightDp.dp.roundToPx() }, 1)
    }
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
                }.onFailure { error ->
                    Log.w(WALLPAPER_LOG_TAG, "Wallpaper decode failed for URI=$wallpaperUri: ${error.message}")
                }.getOrNull()
            }
        }
    }

    val bitmap = wallpaperBitmap
    if (bitmap == null) {
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
        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
        val constrainedBlurRadius = blurRadiusDp.coerceIn(
            MIN_WALLPAPER_BLUR_RADIUS_DP,
            MAX_WALLPAPER_BLUR_RADIUS_DP
        )
        Image(
            bitmap = imageBitmap,
            contentDescription = "Custom cover wallpaper",
            contentScale = if (wallpaperScaleMode == WallpaperScaleMode.CROP) {
                ContentScale.Crop
            } else {
                ContentScale.Fit
            },
            modifier = if (constrainedBlurRadius > 0f) {
                modifier.blur(constrainedBlurRadius.dp)
            } else {
                modifier
            }
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
    WallpaperBitmapCache.get(cacheKey)?.let { cachedBitmap ->
        return cachedBitmap
    }

    val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    openWallpaperInputStream(context, uri)?.use { boundsStream ->
        BitmapFactory.decodeStream(boundsStream, null, boundsOptions)
    } ?: return null

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

    if (decodedBitmap != null) {
        WallpaperBitmapCache.put(cacheKey, decodedBitmap)
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

private fun openWallpaperInputStream(context: android.content.Context, uri: Uri): InputStream? {
    return when (uri.scheme) {
        "file" -> uri.path?.let { path -> runCatching { File(path).inputStream() }.getOrNull() }
        else -> runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
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

