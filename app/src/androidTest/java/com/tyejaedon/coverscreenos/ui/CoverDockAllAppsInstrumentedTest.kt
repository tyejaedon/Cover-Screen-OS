package com.tyejaedon.coverscreenos.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverDockAllAppsInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `fourth slot routes to all apps only when enabled`() {
        var opened = 0
        composeRule.setContent {
            CoverOSTheme {
                CoverDockRow(
                    dockSlots = listOf(null, null, null, null),
                    onAppSelected = { error("Empty slot launched a package") },
                    slotFourAllApps = true,
                    onOpenAllApps = { opened++ }
                )
            }
        }
        composeRule.onNodeWithText("All apps").assertExists().performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun `legacy dock does not show all apps`() {
        composeRule.setContent {
            CoverOSTheme {
                CoverDockRow(
                    dockSlots = listOf(null, null, null, null),
                    onAppSelected = { error("Empty slot launched a package") }
                )
            }
        }
        composeRule.onNodeWithText("All apps").assertDoesNotExist()
    }
}
