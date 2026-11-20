package com.tyejaedon.coverscreenos.notifications

import android.app.Notification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaNotificationClassificationTest {

    @Test
    fun `media session token, media style, or transport category qualify as media`() {
        assertTrue(
            CoverNotificationListenerService.isMediaNotification(
                category = null,
                templateClassName = null,
                hasMediaSessionToken = true,
                hasProgressBar = false,
                hasPlaybackSemanticAction = false,
                actionLabels = emptyList()
            )
        )
        assertTrue(
            CoverNotificationListenerService.isMediaNotification(
                category = null,
                templateClassName = "android.app.Notification\$MediaStyle",
                hasMediaSessionToken = false,
                hasProgressBar = false,
                hasPlaybackSemanticAction = false,
                actionLabels = emptyList()
            )
        )
        assertTrue(
            CoverNotificationListenerService.isMediaNotification(
                category = Notification.CATEGORY_TRANSPORT,
                templateClassName = null,
                hasMediaSessionToken = false,
                hasProgressBar = false,
                hasPlaybackSemanticAction = false,
                actionLabels = emptyList()
            )
        )
    }

    @Test
    fun `non-media categories are rejected even with media-like actions`() {
        val nonMediaCategories = listOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_STATUS,
            "stopwatch"
        )
        nonMediaCategories.forEach { category ->
            assertFalse(
                "Expected category=$category to be rejected",
                CoverNotificationListenerService.isMediaNotification(
                    category = category,
                    templateClassName = null,
                    hasMediaSessionToken = false,
                    hasProgressBar = false,
                    hasPlaybackSemanticAction = true,
                    actionLabels = listOf("Pause", "Next")
                )
            )
        }
    }

    @Test
    fun `ongoing utility notifications with single action or progress bar are rejected`() {
        // Timer / recorder with only Pause
        assertFalse(
            CoverNotificationListenerService.isMediaNotification(
                category = null,
                templateClassName = null,
                hasMediaSessionToken = false,
                hasProgressBar = false,
                hasPlaybackSemanticAction = true,
                actionLabels = listOf("Pause", "Stop")
            )
        )
        // Onboarding / navigation with Next and Skip
        assertFalse(
            CoverNotificationListenerService.isMediaNotification(
                category = null,
                templateClassName = null,
                hasMediaSessionToken = false,
                hasProgressBar = false,
                hasPlaybackSemanticAction = false,
                actionLabels = listOf("Next", "Skip")
            )
        )
        // Substring false positive ("display" contains "play")
        assertFalse(
            CoverNotificationListenerService.isMediaNotification(
                category = null,
                templateClassName = null,
                hasMediaSessionToken = false,
                hasProgressBar = false,
                hasPlaybackSemanticAction = false,
                actionLabels = listOf("Display", "Next")
            )
        )
        // Download / backup notification with progress bar
        assertFalse(
            CoverNotificationListenerService.isMediaNotification(
                category = null,
                templateClassName = null,
                hasMediaSessionToken = false,
                hasProgressBar = true,
                hasPlaybackSemanticAction = false,
                actionLabels = listOf("Pause", "Skip")
            )
        )
    }

    @Test
    fun `fallback requires both play-pause and track-stepping controls`() {
        assertTrue(
            CoverNotificationListenerService.isMediaNotification(
                category = null,
                templateClassName = null,
                hasMediaSessionToken = false,
                hasProgressBar = false,
                hasPlaybackSemanticAction = false,
                actionLabels = listOf("Pause", "Next")
            )
        )
    }
}
