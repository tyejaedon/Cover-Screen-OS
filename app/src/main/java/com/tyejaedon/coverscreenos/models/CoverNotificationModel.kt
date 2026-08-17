package com.tyejaedon.coverscreenos.models

import android.graphics.Bitmap

data class CoverMediaModel(
    val title: String?,
    val artist: String?,
    val actionLabels: List<String>,
    val playPauseActionLabel: String?,
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

