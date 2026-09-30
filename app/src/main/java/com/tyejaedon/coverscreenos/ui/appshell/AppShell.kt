package com.tyejaedon.coverscreenos.ui.appshell

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import com.tyejaedon.coverscreenos.R
import com.tyejaedon.coverscreenos.helpers.ForegroundServiceHelper
import com.tyejaedon.coverscreenos.permissions.PermissionScreen
import com.tyejaedon.coverscreenos.ui.dashboard.DashboardScreen
import com.tyejaedon.coverscreenos.ui.dashboard.DashboardStatusDetails
import com.tyejaedon.coverscreenos.ui.dashboard.rememberDashboardState
import com.tyejaedon.coverscreenos.ui.customize.CustomizeScreen

private data class HomeTab(val route: String, @StringRes val title: Int, val icon: ImageVector)

private val homeTabs = listOf(
    HomeTab(HomeRoutes.Dashboard, R.string.home_dashboard, Icons.Default.DashboardCustomize),
    HomeTab(HomeRoutes.Customize, R.string.home_customize, Icons.Default.Palette),
    HomeTab(HomeRoutes.Permissions, R.string.home_permissions, Icons.Default.VerifiedUser),
    HomeTab(HomeRoutes.About, R.string.home_about, Icons.Default.Info)
)

private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    modifier: Modifier = Modifier,
    startDestination: String = HomeRoutes.Dashboard,
    topBarActions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val dashboardState = rememberDashboardState()
    val navController = rememberNavController()
    val customizeActionScope = rememberCoroutineScope()
    val customizeSnackbar = remember { SnackbarHostState() }
    var categoryScrollPositions by rememberSaveable {
        mutableStateOf(IntArray(CustomizeCategory.entries.size))
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val selectedTab = homeTabs.firstOrNull { tab ->
        destination?.hierarchy?.any { it.route == tab.route } == true
    } ?: homeTabs.first()

    BoxWithConstraints(modifier = modifier) {
        val expanded = maxWidth >= 600.dp
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringResource(selectedTab.title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    actions = {
                        topBarActions()
                        TopBarStatusChip(dashboardState)
                    }
                )
            },
            bottomBar = {
                if (!expanded) {
                    NavigationBar {
                        homeTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab.route == tab.route,
                                onClick = { navController.navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.title)) },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
            },
            floatingActionButton = floatingActionButton,
            snackbarHost = { SnackbarHost(customizeSnackbar) },
            containerColor = MaterialTheme.colorScheme.surface
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (expanded) {
                    NavigationRail {
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
                    modifier = Modifier.weight(1f)
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
                            }
                        )
                    }
                    composable(
                        route = HomeRoutes.Permissions,
                        deepLinks = listOf(navDeepLink { uriPattern = HomeRoutes.deepLink(HomeRoutes.Permissions) })
                    ) {
                        PermissionScreen(
                            onPermissionsGranted = {},
                            grantedContent = {
                                Column(Modifier.fillMaxSize().padding(24.dp)) {
                                    Text("Required permissions ready", style = MaterialTheme.typography.titleLarge)
                                    DashboardStatusDetails(dashboardState)
                                }
                            }
                        )
                    }
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
                                    }
                                )
                            }
                        }
                    }
                    homeTabs.filter { it.route == HomeRoutes.About }.forEach { tab ->
                        composable(
                            route = tab.route,
                            deepLinks = listOf(navDeepLink { uriPattern = HomeRoutes.deepLink(tab.route) })
                        ) {
                            EmptyHomeDestination(tab.title)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeDestination(@StringRes title: Int) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
    }
}
