package com.tyejaedon.coverscreenos.models

import android.graphics.Bitmap

enum class CoverMediaTransportAction {
    PLAY_PAUSE,
    PREVIOUS,
    NEXT
}

data class CoverMediaModel(
    val title: String?,
    val artist: String?,
    val isPlaying: Boolean,
    val canPlayPause: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    val artworkBitmap: Bitmap?
)

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

