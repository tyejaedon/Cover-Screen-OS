package com.tyejaedon.coverscreenos.notifications

import android.app.Notification
import android.media.session.PlaybackState
import com.tyejaedon.coverscreenos.ui.computeLivePlaybackPositionMs
import com.tyejaedon.coverscreenos.ui.formatMediaDurationMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaNotificationClassificationTest {

    @Test
    fun `media session token qualifies as media for non-blocked categories`() {
        assertTrue(
            CoverNotificationListenerService.isMediaNotification(
                category = null,
                hasMediaSessionToken = true
            )
        )
        assertTrue(
            CoverNotificationListenerService.isMediaNotification(
                category = Notification.CATEGORY_TRANSPORT,
                hasMediaSessionToken = true
            )
        )
    }

    @Test
    fun `notifications without media session token are rejected`() {
        assertFalse(
            CoverNotificationListenerService.isMediaNotification(
                category = Notification.CATEGORY_TRANSPORT,
                hasMediaSessionToken = false
            )
        )
        assertFalse(
            CoverNotificationListenerService.isMediaNotification(
                category = null,
                hasMediaSessionToken = false
            )
        )
    }

    @Test
    fun `non-media categories are rejected even with media session token`() {
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
                    hasMediaSessionToken = true
                )
            )
        }
    }

    @Test
    fun `playback capabilities map playback state and action flags`() {
        val playing = CoverNotificationListenerService.resolvePlaybackCapabilities(
            state = PlaybackState.STATE_PLAYING,
            actions = PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT
        )
        assertTrue(playing.isPlaying)
        assertTrue(playing.canPlayPause)
        assertFalse(playing.canSkipPrevious)
        assertTrue(playing.canSkipNext)

        val buffering = CoverNotificationListenerService.resolvePlaybackCapabilities(
            state = PlaybackState.STATE_BUFFERING,
            actions = PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        )
        assertTrue(buffering.isPlaying)
        assertTrue(buffering.canPlayPause)
        assertTrue(buffering.canSkipPrevious)
        assertFalse(buffering.canSkipNext)

        val paused = CoverNotificationListenerService.resolvePlaybackCapabilities(
            state = PlaybackState.STATE_PAUSED,
            actions = PlaybackState.ACTION_PLAY
        )
        assertFalse(paused.isPlaying)
        assertTrue(paused.canPlayPause)
        assertFalse(paused.canSkipPrevious)
        assertFalse(paused.canSkipNext)

        val none = CoverNotificationListenerService.resolvePlaybackCapabilities(
            state = null,
            actions = 0L
        )
        assertFalse(none.isPlaying)
        assertFalse(none.canPlayPause)
        assertFalse(none.canSkipPrevious)
        assertFalse(none.canSkipNext)
        assertFalse(none.canSeek)

        val seekable = CoverNotificationListenerService.resolvePlaybackCapabilities(
            state = PlaybackState.STATE_PLAYING,
            actions = PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO
        )
        assertTrue(seekable.canSeek)
    }

    @Test
    fun `standard transport labels are filtered while shuffle and repeat remain custom`() {
        assertTrue(CoverNotificationListenerService.isStandardMediaTransportLabel("Play"))
        assertTrue(CoverNotificationListenerService.isStandardMediaTransportLabel("  pause "))
        assertTrue(CoverNotificationListenerService.isStandardMediaTransportLabel("Previous"))
        assertTrue(CoverNotificationListenerService.isStandardMediaTransportLabel("Skip to next"))
        assertFalse(CoverNotificationListenerService.isStandardMediaTransportLabel("Shuffle"))
        assertFalse(CoverNotificationListenerService.isStandardMediaTransportLabel("Repeat"))
        assertFalse(CoverNotificationListenerService.isStandardMediaTransportLabel("Like"))
    }

    @Test
    fun `live playback position interpolates while playing and clamps to duration`() {
        assertEquals(
            0L,
            computeLivePlaybackPositionMs(
                positionMs = -1L,
                positionUpdateTimeMs = 1_000L,
                playbackSpeed = 1f,
                durationMs = 120_000L,
                isPlaying = true,
                nowElapsedMs = 5_000L
            )
        )
        assertEquals(
            14_000L,
            computeLivePlaybackPositionMs(
                positionMs = 10_000L,
                positionUpdateTimeMs = 1_000L,
                playbackSpeed = 1f,
                durationMs = 120_000L,
                isPlaying = true,
                nowElapsedMs = 5_000L
            )
        )
        assertEquals(
            10_000L,
            computeLivePlaybackPositionMs(
                positionMs = 10_000L,
                positionUpdateTimeMs = 1_000L,
                playbackSpeed = 1f,
                durationMs = 120_000L,
                isPlaying = false,
                nowElapsedMs = 5_000L
            )
        )
        assertEquals(
            120_000L,
            computeLivePlaybackPositionMs(
                positionMs = 118_000L,
                positionUpdateTimeMs = 1_000L,
                playbackSpeed = 1f,
                durationMs = 120_000L,
                isPlaying = true,
                nowElapsedMs = 10_000L
            )
        )
    }

    @Test
    fun `duration formatting renders minutes and hours cleanly`() {
        assertEquals("0:00", formatMediaDurationMs(0L))
        assertEquals("3:05", formatMediaDurationMs(185_000L))
        assertEquals("1:02:03", formatMediaDurationMs(3_723_000L))
    }
}
