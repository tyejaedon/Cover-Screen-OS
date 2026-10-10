package com.tyejaedon.coverscreenos.ui.permissions

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PermissionsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val denied = PermissionGrants(
        notifications = false, launcherAccessibility = false, notificationListener = false,
        batteryExemption = false, inputAccessibility = false, media = false, microphone = false
    )

    @Test
    fun deniedRequirementAnnouncesStateAndFooterAllowsContinuing() {
        var continued = 0
        var action = 0
        rule.setContent {
            PermissionsContent(
                grants = denied,
                rows = listOf(
                    PermissionRowModel(
                        PermissionId.NOTIFICATIONS, "Notification permission", "Foreground notification",
                        Icons.Filled.Notifications, false, { action++ }
                    )
                ),
                onContinue = { continued++ },
                onOpenAppSettings = {},
                onRefresh = {}
            )
        }
        rule.onNodeWithText("0%").assertExists()
        rule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Notification permission -- denied -- tap to change"
            )
        ).assertHasClickAction().performClick()
        rule.onNodeWithText("Continue with limited features").assertExists().performClick()
        rule.runOnIdle {
            assertEquals(1, action)
            assertEquals(1, continued)
        }
    }

    @Test
    fun optionalDeniedDoesNotKeepFooterOrPreventPerfectHealth() {
        rule.setContent {
            PermissionsContent(
                grants = denied.copy(
                    notifications = true, launcherAccessibility = true,
                    notificationListener = true, batteryExemption = true,
                    inputAccessibility = true
                ),
                rows = emptyList(),
                onContinue = {},
                onOpenAppSettings = {},
                onRefresh = {}
            )
        }
        rule.onNodeWithText("100%").assertExists()
        rule.onNodeWithText("Continue with limited features").assertDoesNotExist()
    }
}
