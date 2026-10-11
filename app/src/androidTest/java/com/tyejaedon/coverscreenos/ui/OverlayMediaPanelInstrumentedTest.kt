package com.tyejaedon.coverscreenos.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tyejaedon.coverscreenos.models.CoverMediaModel
import com.tyejaedon.coverscreenos.models.CoverNotificationModel
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayMediaPanelInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pillKeepsItsHeightAndOpensExpandedPlayer() {
        var opened = 0
        composeRule.setContent {
            CoverOSTheme {
                CompactNowPlayingCard(
                    mediaNotification = notification("A long title ".repeat(20)),
                    onOpenExpanded = { opened++ },
                    modifier = Modifier.testTag("pill")
                )
            }
        }
        composeRule.onNodeWithTag("pill").assertHeightIsEqualTo(48.dp).performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun emptyPillOpensMediaApp() {
        var opened = 0
        composeRule.setContent {
            CoverOSTheme {
                CompactNowPlayingCard(
                    mediaNotification = null,
                    onOpenExpanded = { error("No media to expand") },
                    onOpenMediaApp = { opened++; true }
                )
            }
        }
        composeRule.onNodeWithText("Open a media app").performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun artworkAndActionsUpdateWithTrackAndClearWhenMissing() {
        val current = mutableStateOf(notification("First", artwork(android.graphics.Color.RED)))
        var dismissed = 0
        val actions = mutableListOf<String?>()
        composeRule.setContent {
            CoverOSTheme {
                ExpandedNowPlayingPanel(
                    mediaNotification = current.value,
                    visible = true,
                    onDismiss = { dismissed++ },
                    onLaunchSourceApp = {},
                    onAction = { _, action -> actions += action },
                    onOpenNotification = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("Previous").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Pause").performClick()
        composeRule.runOnIdle { assertEquals(listOf("Pause"), actions) }
        var pixels = composeRule.onNodeWithTag("media_artwork_background").captureToImage().toPixelMap()
        assertTrue(pixels[pixels.width / 2, 2].red > pixels[pixels.width / 2, 2].blue)

        composeRule.runOnIdle {
            current.value = notification("Second", artwork(android.graphics.Color.BLUE), listOf("Play"))
        }
        composeRule.onNodeWithText("First").assertDoesNotExist()
        composeRule.onNodeWithText("Second").assertExists()
        composeRule.onNodeWithContentDescription("Play").performClick()
        pixels = composeRule.onNodeWithTag("media_artwork_background").captureToImage().toPixelMap()
        assertTrue(pixels[pixels.width / 2, 2].blue > pixels[pixels.width / 2, 2].red)
        composeRule.runOnIdle { current.value = notification("No art", actions = listOf("Favorite")) }
        composeRule.onNodeWithContentDescription("Play/Pause").assertIsNotEnabled()
        pixels = composeRule.onNodeWithTag("media_artwork_background").captureToImage().toPixelMap()
        assertTrue(kotlin.math.abs(pixels[pixels.width / 2, 2].red - pixels[pixels.width / 2, 2].blue) < 0.02f)
        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.runOnIdle { assertEquals(1, dismissed) }
    }

    private fun artwork(color: Int) =
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun notification(
        title: String,
        artwork: Bitmap? = null,
        actions: List<String> = listOf("Pause", "Next")
    ) = CoverNotificationModel(
        notificationKey = "same-session",
        packageName = "com.tyejaedon.coverscreenos",
        title = title,
        previewText = "Artist",
        isClearable = false,
        isOngoing = true,
        postTime = 1L,
        isMediaNotification = true,
        media = CoverMediaModel(title, "Artist", actions, null, artwork)
    )
}
