package com.tyejaedon.coverscreenos.ui.appshell

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.core.net.toUri
import org.junit.Rule
import org.junit.Test

class AppShellPermissionsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun permissionsDeepLinkStillAllowsNavigationThroughAllFourTabs() {
        rule.activity.intent = Intent(Intent.ACTION_VIEW, HomeRoutes.deepLink(HomeRoutes.Permissions).toUri())
        rule.setContent { AppShell() }
        rule.onNodeWithText("Permission health").assertExists()
        rule.onNodeWithTag("home_tab_content").performTouchInput { swipeLeft() }
        rule.onNodeWithText("Permission health").assertDoesNotExist()
        rule.onNodeWithTag("home_tab_content").performTouchInput { swipeRight() }
        rule.onNodeWithText("Permission health").assertExists()
        rule.onNodeWithText("Dashboard").performClick()
        rule.onNodeWithText("Preview overlay").assertExists()
        rule.onNodeWithText("Customize").performClick()
        rule.onAllNodesWithText("Wallpaper").onFirst().assertExists()
        rule.onNodeWithText("About").performClick()
        rule.onNodeWithText("Permissions").performClick()
        rule.onNodeWithText("Permission health").assertExists()
    }

    @Test
    fun explicitDashboardDeepLinkOverridesPermissionsStartHeuristic() {
        rule.activity.intent = Intent(Intent.ACTION_VIEW, HomeRoutes.deepLink(HomeRoutes.Dashboard).toUri())
        rule.setContent { AppShell() }
        rule.onNodeWithText("Preview overlay").assertExists()
    }
}
