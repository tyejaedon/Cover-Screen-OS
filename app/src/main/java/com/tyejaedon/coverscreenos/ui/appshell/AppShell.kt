package com.tyejaedon.coverscreenos.ui.appshell

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import kotlin.math.abs
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import com.tyejaedon.coverscreenos.ui.theme.oneui.OneUITheme
import com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUIFloatingNavBar
import com.tyejaedon.coverscreenos.ui.theme.oneui.components.OneUINavItem
import com.tyejaedon.coverscreenos.R
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.helpers.ForegroundServiceHelper
import com.tyejaedon.coverscreenos.ui.permissions.PermissionsScreen
import com.tyejaedon.coverscreenos.ui.dashboard.DashboardScreen
import com.tyejaedon.coverscreenos.ui.dashboard.rememberDashboardState
import com.tyejaedon.coverscreenos.ui.customize.CustomizeScreen
import com.tyejaedon.coverscreenos.ui.about.AboutScreen
import kotlinx.coroutines.launch

/**
 * Metadata binding a top-level [HomeRoutes] destination to its tab label and Material icon.
 */
private data class HomeTab(val route: String, @StringRes val title: Int, val icon: ImageVector)

// Canonical tab list driving the floating bottom bar, side navigation rail, and swipe navigation.
private val homeTabs = listOf(
    HomeTab(HomeRoutes.Dashboard, R.string.home_dashboard, Icons.Default.DashboardCustomize),
    HomeTab(HomeRoutes.Customize, R.string.home_customize, Icons.Default.Palette),
    HomeTab(HomeRoutes.Permissions, R.string.home_permissions, Icons.Default.VerifiedUser),
    HomeTab(HomeRoutes.About, R.string.home_about, Icons.Default.Info)
)

/**
 * Returns `true` only when DataStore confirms the welcome tour has not been seen (`seen == false`,
 * ignoring the initial `null` loading state), the activity was not entered via deep link, and the
 * user has not dismissed it during this UI session.
 */
internal fun shouldShowWelcomeTour(seen: Boolean?, deepLinkLaunch: Boolean, dismissed: Boolean): Boolean =
    seen == false && !deepLinkLaunch && !dismissed

/**
 * Unwraps nested [ContextWrapper] layers to inspect the hosting [Activity]'s launch [Intent] and
 * report whether the app was opened directly via a deep link (`ACTION_VIEW` with a URI).
 */
private fun isDeepLinkLaunch(context: Context): Boolean {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current.intent?.let { it.action == Intent.ACTION_VIEW && it.data != null } == true
        }
        current = current.baseContext
    }
    return false
}

/**
 * Switches between top-level tabs while preserving and restoring each tab's back stack state.
 */
private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Root adaptive navigation shell for the phone-side companion UI of Cover Screen OS.
 *
 * Hosts a Material3 [Scaffold] switching between [OneUIFloatingNavBar] on compact widths
 * (`< 600.dp`) and a [NavigationRail] on expanded widths (`>= 600.dp`), wires the [NavHost]
 * across [HomeRoutes] (`Dashboard`, `Permissions`, nested [CustomizeCategory] subgraph, and
 * `About`), auto-starts [ForegroundServiceHelper] once required permissions are satisfied, and
 * surfaces [WelcomeHomeTour] on first launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    modifier: Modifier = Modifier,
    topBarActions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {}
) {
    // Android Context used for reading launch intents, DataStore setup, and starting/stopping the service.
    val context = LocalContext.current
    // Persistent settings store holding whether the user has already seen the first-run tour.
    val tourStore = remember(context) { LauncherSettingsStore(context) }
    // Stored tour flag: starts at `null` while loading from disk so the sheet never flickers early.
    val tourSeen by tourStore.welcomeTourSeen.collectAsState(initial = null)
    // True when opened via a coverscreenos:// deep link so we jump straight to the target page without the tour.
    val deepLinkLaunch = remember(context) { isDeepLinkLaunch(context) }
    // Immediate in-memory dismiss flag so closing the tour hides it right away while DataStore saves.
    var tourDismissed by rememberSaveable { mutableStateOf(false) }
    // Counter bumped after PermissionsScreen changes grants so rememberDashboardState refreshes right away.
    var permissionRefreshKey by remember { mutableIntStateOf(0) }
    // Live combined snapshot of permission readiness and ForegroundService status shared across tabs.
    val dashboardState = rememberDashboardState(permissionRefreshKey)
    // First tab shown at launch (Permissions if setup is incomplete, else Dashboard); remembered once so fixing permissions mid-session won't jump tabs unexpectedly.
    val startDestination = remember {
        HomeRoutes.initialDestination(dashboardState.permissions.missing.isNotEmpty())
    }
    // Automatically start the cover foreground service as soon as all required grants become active.
    LaunchedEffect(dashboardState.permissions.missing.isEmpty()) {
        if (dashboardState.permissions.missing.isEmpty() && !ForegroundServiceHelper.isForegroundServiceRunning()) {
            ForegroundServiceHelper.startForegroundService(context)
        }
    }
    // Jetpack Navigation controller driving tab switches, nested Customize routes, and back navigation.
    val navController = rememberNavController()
    // Coroutine scope for saving welcome-tour dismissal and running Customize screen import/export actions.
    val customizeActionScope = rememberCoroutineScope()
    // Host state for transient confirmation/error snackbars triggered from CustomizeScreen.
    val customizeSnackbar = remember { SnackbarHostState() }
    // One saved scroll offset per CustomizeCategory tab so switching categories remembers where you scrolled.
    var categoryScrollPositions by rememberSaveable {
        mutableStateOf(IntArray(CustomizeCategory.entries.size))
    }
    // Current top entry on the Compose navigation back stack.
    val backStackEntry by navController.currentBackStackEntryAsState()
    // Route metadata for the active screen, used to highlight the right nav bar or rail item.
    val destination = backStackEntry?.destination
    // Active top-level tab (checks destination.hierarchy so nested Customize pages still highlight Customize).
    val selectedTab = homeTabs.firstOrNull { tab ->
        destination?.hierarchy?.any { it.route == tab.route } == true
    } ?: homeTabs.first { it.route == startDestination }

    BoxWithConstraints(modifier = modifier) {
        // Material3 width breakpoint (>= 600dp): shows a side NavigationRail instead of the floating bottom bar.
        val expanded = maxWidth >= 600.dp
        // Height breakpoint (<= 480dp): trims bottom bar margin on short landscape or half-folded windows.
        val isCompactHeight = maxHeight <= 480.dp
        // Localized navigation items passed to OneUIFloatingNavBar on compact-width screens.
        val navItems = homeTabs.map {
            OneUINavItem(
                route = it.route,
                title = stringResource(it.title),
                icon = it.icon
            )
        }

        Scaffold(
            bottomBar = {
                if (!expanded) {
                    OneUIFloatingNavBar(
                        items = navItems,
                        selectedRoute = selectedTab.route,
                        onItemSelected = { navController.navigateToTab(it) },
                        modifier = Modifier
                            // Keep the floating nav bar clear of system gesture bars and display cutouts.
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                                )
                            )
                            .padding(bottom = if (isCompactHeight) 6.dp else 12.dp)
                    )
                }
            },
            floatingActionButton = floatingActionButton,
            snackbarHost = { SnackbarHost(customizeSnackbar) },
            containerColor = OneUITheme.colors.canvasBackground
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (expanded) {
                    NavigationRail(
                        containerColor = OneUITheme.colors.cardSurface
                    ) {
                        homeTabs.forEach { tab ->
                            NavigationRailItem(
                                selected = selectedTab.route == tab.route,
                                onClick = { navController.navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.title)) },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.weight(1f).testTag("home_tab_content")
                        // Support horizontal swipe gestures across top-level tabs beyond a 72.dp threshold.
                        .pointerInput(selectedTab.route) {
                            // Accumulated horizontal finger movement (in pixels) for the current swipe gesture.
                            var distance = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { distance = 0f },
                                onHorizontalDrag = { _, delta -> distance += delta },
                                onDragEnd = {
                                    if (abs(distance) >= 72.dp.toPx()) {
                                        // Position of the active tab and clamped neighbor index to navigate to.
                                        val index = homeTabs.indexOfFirst { it.route == selectedTab.route }
                                        val next = (index + if (distance < 0) 1 else -1)
                                            .coerceIn(0, homeTabs.lastIndex)
                                        if (next != index) navController.navigateToTab(homeTabs[next].route)
                                    }
                                }
                            )
                        }
                ) {
                    composable(
                        route = HomeRoutes.Dashboard,
                        deepLinks = listOf(navDeepLink { uriPattern = HomeRoutes.deepLink(HomeRoutes.Dashboard) })
                    ) {
                        DashboardScreen(
                            state = dashboardState,
                            onPermissions = { navController.navigateToTab(HomeRoutes.Permissions) },
                            onToggleService = {
                                if (dashboardState.runtime.serviceActive) {
                                    ForegroundServiceHelper.stopForegroundService(context)
                                    true
                                } else {
                                    ForegroundServiceHelper.startForegroundService(context)
                                }
                            },
                            topBarActions = topBarActions
                        )
                    }
                    composable(
                        route = HomeRoutes.Permissions,
                        deepLinks = listOf(navDeepLink { uriPattern = HomeRoutes.deepLink(HomeRoutes.Permissions) })
                    ) {
                        PermissionsScreen(
                            onContinue = { navController.navigateToTab(HomeRoutes.Dashboard) },
                            onPermissionsChanged = { permissionRefreshKey++ }
                        )
                    }
                    // Nested navigation subgraph grouping all CustomizeCategory destinations under Customize.
                    navigation(
                        route = HomeRoutes.Customize,
                        startDestination = CustomizeCategory.WALLPAPER.route,
                        deepLinks = listOf(navDeepLink { uriPattern = HomeRoutes.deepLink(HomeRoutes.Customize) })
                    ) {
                        CustomizeCategory.entries.forEach { category ->
                            composable(
                                route = category.route,
                                deepLinks = listOf(navDeepLink { uriPattern = category.deepLink })
                            ) {
                                CustomizeScreen(
                                    category = category,
                                    expanded = expanded,
                                    actionScope = customizeActionScope,
                                    snackbar = customizeSnackbar,
                                    initialScrollPosition = categoryScrollPositions[category.ordinal],
                                    onScrollPositionChanged = { selected, position ->
                                        categoryScrollPositions = categoryScrollPositions.copyOf().also {
                                            it[selected.ordinal] = position
                                        }
                                    },
                                    onCategorySelected = { selected ->
                                        if (selected != category) {
                                            navController.navigate(selected.route) {
                                                popUpTo(HomeRoutes.Customize) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    permissions = dashboardState.permissions,
                                    onPermissions = { navController.navigateToTab(HomeRoutes.Permissions) },
                                    onBack = {
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        } else {
                                            (context as? Activity)?.finish()
                                        }
                                    }
                                )
                            }
                        }
                    }
                    composable(
                        route = HomeRoutes.About,
                        deepLinks = listOf(navDeepLink { uriPattern = HomeRoutes.deepLink(HomeRoutes.About) })
                    ) {
                        AboutScreen(
                            onDashboard = { navController.navigateToTab(HomeRoutes.Dashboard) },
                            onCustomize = { navController.navigateToTab(HomeRoutes.Customize) }
                        )
                    }
                }
            }
        }
        // Overlay the first-run welcome sheet above the Scaffold and persist completion in DataStore.
        if (shouldShowWelcomeTour(tourSeen, deepLinkLaunch, tourDismissed)) {
            WelcomeHomeTour(onDismiss = {
                tourDismissed = true
                customizeActionScope.launch { tourStore.markWelcomeTourSeen() }
            })
        }
    }
}
