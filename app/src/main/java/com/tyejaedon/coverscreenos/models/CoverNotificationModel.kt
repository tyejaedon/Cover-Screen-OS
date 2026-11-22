package com.tyejaedon.coverscreenos.models

import android.graphics.Bitmap

/** Playback control actions dispatched from cover-screen media cards to an active media session. */
enum class CoverMediaTransportAction {
    /** Toggle between play and pause via `MediaController.TransportControls`. */
    PLAY_PAUSE,
    /** Skip to the previous track in the active queue. */
    PREVIOUS,
    /** Skip to the next track in the active queue. */
    NEXT
}

/**
 * Snapshot of active media playback state and metadata rendered inside a cover media card.
 *
 * @property title Track or media item title, if provided by the session's `MediaMetadata`.
 * @property artist Artist or subtitle text, if available.
 * @property isPlaying Whether the underlying `PlaybackState` is currently actively playing.
 * @property canPlayPause Whether the session advertises play/pause transport actions.
 * @property canSkipPrevious Whether the session advertises skip-to-previous support.
 * @property canSkipNext Whether the session advertises skip-to-next support.
 * @property artworkBitmap Optional album art decoded from `MediaMetadata` for background/thumbnail UI.
 */
data class CoverMediaModel(
    val title: String?,
    val artist: String?,
    val isPlaying: Boolean,
    val canPlayPause: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    val artworkBitmap: Bitmap?
)

/**
 * UI-ready representation of an active Android notification surfaced on the cover screen.
 *
 * @property notificationKey Unique `StatusBarNotification.key` used for updates and dismissal.
 * @property packageName Originating application package name.
 * @property title Primary notification heading.
 * @property previewText Collapsed body/summary text safe for compact cover display cards.
 * @property isClearable True if the notification can be dismissed by the user.
 * @property isOngoing True for persistent/foreground-service notifications.
 * @property postTime Epoch timestamp in milliseconds when the notification was posted.
 * @property isMediaNotification True when the notification carries a `MediaSession` style/token.
 * @property media Parsed media transport/metadata snapshot when [isMediaNotification] is true.
 */
data class CoverNotificationModel(
    val notificationKey: String,
    val packageName: String,
    val title: String,
    val previewText: String,
    val isClearable: Boolean,
    val isOngoing: Boolean,
    val postTime: Long,
    val isMediaNotification: Boolean,
    val media: CoverMediaModel?
)

