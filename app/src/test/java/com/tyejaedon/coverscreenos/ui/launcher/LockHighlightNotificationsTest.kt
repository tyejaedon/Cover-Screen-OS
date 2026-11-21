package com.tyejaedon.coverscreenos.ui.launcher

import com.tyejaedon.coverscreenos.models.CoverNotificationModel
import org.junit.Assert.assertEquals
import org.junit.Test

class LockHighlightNotificationsTest {

    private fun notification(
        key: String,
        packageName: String,
        postTime: Long,
        isOngoing: Boolean = false,
        isMedia: Boolean = false
    ) = CoverNotificationModel(
        notificationKey = key,
        packageName = packageName,
        title = key,
        previewText = key,
        isClearable = !isOngoing,
        isOngoing = isOngoing,
        postTime = postTime,
        isMediaNotification = isMedia,
        media = null
    )

    @Test
    fun `bans system ui and media notifications and caps to one ongoing and two normal`() {
        val input = listOf(
            notification("sys_ongoing", "com.android.systemui", postTime = 900L, isOngoing = true),
            notification("sys_normal", "com.samsung.systemui", postTime = 850L, isOngoing = false),
            notification("media_ongoing", "com.spotify.music", postTime = 800L, isOngoing = true, isMedia = true),
            notification("ongoing_old", "com.whatsapp", postTime = 500L, isOngoing = true),
            notification("ongoing_new", "com.google.android.dialer", postTime = 700L, isOngoing = true),
            notification("normal_1", "com.slack", postTime = 100L, isOngoing = false),
            notification("normal_2", "com.whatsapp", postTime = 300L, isOngoing = false),
            notification("normal_3_dup", "com.google.android.gm", postTime = 550L, isOngoing = false),
            notification("normal_3", "com.google.android.gm", postTime = 600L, isOngoing = false)
        )

        val result = selectLockHighlightNotifications(input)

        assertEquals(
            listOf("ongoing_new", "normal_3", "normal_2"),
            result.map { it.notificationKey }
        )
    }
}
