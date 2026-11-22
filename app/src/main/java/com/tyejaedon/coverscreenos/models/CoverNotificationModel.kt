package com.tyejaedon.coverscreenos.models

import android.graphics.Bitmap

/** Playback control actions dispatched from cover-screen media cards to an active media session. */
sealed interface CoverMediaTransportAction {
    /** Toggle between play and pause via `MediaController.TransportControls`. */
    data object PLAY_PAUSE : CoverMediaTransportAction
    /** Skip to the previous track in the active queue. */
    data object PREVIOUS : CoverMediaTransportAction
    /** Skip to the next track in the active queue. */
    data object NEXT : CoverMediaTransportAction
    /** Seek to a specific playback position in milliseconds. */
    data class SeekTo(val positionMs: Long) : CoverMediaTransportAction
    /** Trigger a session-defined `PlaybackState.CustomAction` (e.g., shuffle, repeat, like). */
    data class CustomAction(val action: String) : CoverMediaTransportAction
    /** Invoke a `Notification.Action` button attached to the media notification. */
    data class NotificationAction(val index: Int) : CoverMediaTransportAction
}

/**
 * Extra playback control exposed by a media session's `PlaybackState.CustomAction` or
 * `Notification.Action` (such as shuffle, repeat, or favorite).
 *
 * @property id Stable identifier for Compose keying.
 * @property label Human-readable action label used for accessibility and text fallback.
 * @property iconBitmap Optional icon bitmap decoded from the source app's resources.
 * @property transportAction Command dispatched when the user taps this control.
 */
data class CoverMediaCustomAction(
    val id: String,
    val label: String,
    val iconBitmap: Bitmap?,
    val transportAction: CoverMediaTransportAction
)

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
 * @property durationMs Total track duration in milliseconds, or `0L` if unknown.
 * @property positionMs Last reported playback position in milliseconds, or `-1L` if unknown.
 * @property playbackSpeed Playback speed multiplier reported by `PlaybackState`.
 * @property positionUpdateTimeMs `SystemClock.elapsedRealtime()` timestamp of [positionMs].
 * @property canSeek Whether the session advertises `PlaybackState.ACTION_SEEK_TO`.
 * @property customActions Additional session/notification controls (e.g. shuffle, repeat, favorite).
 */
data class CoverMediaModel(
    // Track or episode title shown as the primary line on the cover media card.
    val title: String?,
    // Performing artist or app subtitle shown below the title.
    val artist: String?,
    // True when PlaybackState reports active playback so UI shows Pause instead of Play.
    val isPlaying: Boolean,
    // Capability flags read from PlaybackState actions to enable/disable transport buttons.
    val canPlayPause: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    // Decoded album/podcast cover bitmap displayed behind or beside the controls.
    val artworkBitmap: Bitmap?,
    // Track duration and position snapshot for progress bar and live seeking.
    val durationMs: Long = 0L,
    val positionMs: Long = -1L,
    val playbackSpeed: Float = 0f,
    val positionUpdateTimeMs: Long = 0L,
    val canSeek: Boolean = false,
    // Extra controls (shuffle, repeat, like, etc.) advertised by the media session/notification.
    val customActions: List<CoverMediaCustomAction> = emptyList()
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
    // System-assigned unique key identifying this notification for updates or swipe-to-dismiss.
    val notificationKey: String,
    // Package name of the app that posted the notification (used for icons and grouping).
    val packageName: String,
    // Short heading line extracted from the notification extras.
    val title: String,
    // Compact message preview trimmed for narrow cover-screen cards.
    val previewText: String,
    // False for pinned system alerts that cannot be swiped away.
    val isClearable: Boolean,
    // True for ongoing background alerts such as active calls, timers, or media players.
    val isOngoing: Boolean,
    // Creation/post timestamp in epoch milliseconds used to order newest alerts first.
    val postTime: Long,
    // True when the notification represents a media session rather than a regular alert.
    val isMediaNotification: Boolean,
    // Rich playback metadata and control flags when isMediaNotification is true; otherwise null.
    val media: CoverMediaModel?
)

