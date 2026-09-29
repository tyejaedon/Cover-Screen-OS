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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.tyejaedon.coverscreenos.R

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
    val navController = rememberNavController()
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
                    title = { Text(stringResource(selectedTab.title)) },
                    actions = { topBarActions() }
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
                    homeTabs.forEach { tab ->
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
