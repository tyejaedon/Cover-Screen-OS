package com.tyejaedon.coverscreenos.services

import android.app.PendingIntent
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import com.tyejaedon.coverscreenos.models.CoverMediaModel
import com.tyejaedon.coverscreenos.models.CoverNotificationModel
import com.tyejaedon.coverscreenos.receivers.LockStatusReceiver
import com.tyejaedon.coverscreenos.ui.controllers.CoverAppLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.graphics.createBitmap

class CoverNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val MEDIA_STYLE_TEMPLATE_CLASS = "android.app.Notification\$MediaStyle"

        @Volatile
        private var listenerConnected: Boolean = false

        @Volatile
        private var activeService: CoverNotificationListenerService? = null

        private val activeNotificationModels = MutableStateFlow<List<CoverNotificationModel>>(emptyList())

        fun isListenerConnected(): Boolean = listenerConnected

        fun activeNotificationsFlow(): StateFlow<List<CoverNotificationModel>> {
            return activeNotificationModels.asStateFlow()
        }

        fun dismissNotificationFromOverlay(notificationKey: String): Boolean {
            val service = activeService ?: return false
            return runCatching {
                service.cancelNotification(notificationKey)
                true
            }.getOrElse { error ->
                Log.w("CoverNotifListener", "Dismiss failed key=$notificationKey error=${error.message}")
                false
            }
        }

        fun openNotificationFromOverlay(context: Context, model: CoverNotificationModel): Boolean {
            if (isDeviceLocked(context)) {
                Log.d("CoverNotifListener", "Blocked notification open while locked key=${model.notificationKey}")
                return false
            }

            val service = activeService
            val targetSbn = service?.findNotificationByKey(model.notificationKey)

            val launchAcknowledged = runCatching {
                context.startService(
                    ForegroundService.createHideOverlayIntent(
                        context = context,
                        packageName = model.packageName
                    )
                )
            }.isSuccess

            val contentIntent = targetSbn?.notification?.contentIntent
            if (contentIntent != null) {
                val launchedFromIntent = runCatching {
                    val options = ActivityOptions.makeBasic().apply {
                        pendingIntentBackgroundActivityStartMode =
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    }.toBundle()
                    contentIntent.send(context, 0, null, null, null, null, options)
                    true
                }.getOrElse { error ->
                    val message = if (error is PendingIntent.CanceledException) {
                        "Notification content intent canceled key=${model.notificationKey}"
                    } else {
                        "Notification content intent failed key=${model.notificationKey} error=${error.message}"
                    }
                    Log.w("CoverNotifListener", message)
                    false
                }
                if (launchedFromIntent) return true
            }

            if (!launchAcknowledged) {
                Log.w("CoverNotifListener", "Overlay suppression dispatch failed package=${model.packageName}")
            }

            return CoverAppLauncher.launchPackageOnDisplay(
                context = context,
                packageName = model.packageName,
                displayId = runCatching { context.display.displayId }.getOrNull()
            )
        }

        fun launchNotificationSourceApp(context: Context, packageName: String): Boolean {
            if (isDeviceLocked(context)) {
                Log.d("CoverNotifListener", "Blocked source-app launch while locked package=$packageName")
                return false
            }

            runCatching {
                context.startService(ForegroundService.createHideOverlayIntent(context, packageName))
            }
            return CoverAppLauncher.launchPackageOnDisplay(
                context = context,
                packageName = packageName,
                displayId = runCatching { context.display.displayId }.getOrNull()
            )
        }

        fun performNotificationAction(notificationKey: String, actionLabel: String? = null): Boolean {
            val service = activeService ?: return false
            val sbn = service.findNotificationByKey(notificationKey) ?: return false
            val actions = sbn.notification.actions ?: return false
            if (actions.isEmpty()) return false

            val requestedLabel = actionLabel
                ?.trim()
                ?.takeUnless { it.isEmpty() }

            val targetAction = actionLabel
                ?.let { label ->
                    actions.firstOrNull { action ->
                        action.title
                            ?.toString()
                            ?.trim()
                            ?.equals(label, ignoreCase = true) == true
                    }
                }
                ?: actions.firstOrNull { action -> action.actionIntent != null }
                ?: return false

            val intent = targetAction.actionIntent ?: return false
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val options = ActivityOptions.makeBasic().apply {
                        pendingIntentBackgroundActivityStartMode =
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    }.toBundle()
                    intent.send(service, 0, null, null, null, null, options)
                } else {
                    intent.send(service, 0, null)
                }
                true
            }.getOrElse { error ->
                val message = if (error is PendingIntent.CanceledException) {
                    "Notification action canceled key=$notificationKey requestedLabel=$requestedLabel resolvedLabel=${targetAction.title}"
                } else {
                    "Notification action failed key=$notificationKey requestedLabel=$requestedLabel resolvedLabel=${targetAction.title} error=${error.message}"
                }
                Log.w("CoverNotifListener", message)
                false
            }
        }

        private fun isDeviceLocked(context: Context): Boolean {
            if (LockStatusReceiver.currentLockStatus(context)) return true
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            return keyguardManager?.isDeviceLocked
                ?: keyguardManager?.isKeyguardLocked
                ?: true
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
    }

    override fun onDestroy() {
        if (activeService === this) {
            activeService = null
        }
        listenerConnected = false
        activeNotificationModels.value = emptyList()
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    override fun onListenerConnected() {
        super.onListenerConnected()
        activeService = this
        listenerConnected = true
        refreshActiveNotifications()
        Log.d("CoverNotifListener", "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        if (activeService === this) {
            activeService = null
        }
        listenerConnected = false
        activeNotificationModels.value = emptyList()
        Log.d("CoverNotifListener", "Notification listener disconnected")
        super.onListenerDisconnected()
    }

    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshActiveNotifications()
        super.onNotificationPosted(sbn)
    }

    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshActiveNotifications()
        super.onNotificationRemoved(sbn)
    }

    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun refreshActiveNotifications() {
        val snapshot = runCatching {
            this.activeNotifications
                .asSequence()
                .mapNotNull { sbn -> sbn.toCoverNotificationModel() }
                .sortedByDescending { model -> model.postTime }
                .toList()
        }.getOrElse { error ->
            Log.w("CoverNotifListener", "Unable to refresh notifications: ${error.message}")
            emptyList()
        }

        activeNotificationModels.value = snapshot
    }

    private fun findNotificationByKey(notificationKey: String): StatusBarNotification? {
        return runCatching {
            activeNotifications.firstOrNull { item -> item.key == notificationKey }
        }.getOrElse { error ->
            Log.w("CoverNotifListener", "Unable to query active notification key=$notificationKey error=${error.message}")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun StatusBarNotification.toCoverNotificationModel(): CoverNotificationModel? {
        val extras = notification.extras ?: return null
        val actions = notification.actions.orEmpty()
        val resolvedTitle = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)
            ?.toString()
            ?.trim()
            .orEmpty()
        val resolvedText = (
            extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(android.app.Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)
            )
            ?.toString()
            ?.trim()
            .orEmpty()

        val title = resolvedTitle.ifEmpty {
            runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            }.getOrDefault(packageName)
        }

        val templateClassName = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val hasMediaSession = extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
        val hasMediaStyleTemplate = templateClassName == MEDIA_STYLE_TEMPLATE_CLASS
        val hasPlaybackSemanticAction = actions.any { action ->
            action.semanticAction == Notification.Action.SEMANTIC_ACTION_PLAY ||
                action.semanticAction == Notification.Action.SEMANTIC_ACTION_PAUSE
        }
        val hasPlaybackLabeledAction = actions.any { action ->
            val label = action.title?.toString()?.trim()?.lowercase().orEmpty()
            label.contains("play") ||
                label.contains("pause") ||
                label.contains("next") ||
                label.contains("previous") ||
                label.contains("skip")
        }
        val isMediaNotification = notification.category == Notification.CATEGORY_TRANSPORT ||
            hasMediaSession ||
            hasMediaStyleTemplate ||
            hasPlaybackSemanticAction ||
            hasPlaybackLabeledAction

        val mediaArtistExtra = extras.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()
            .ifEmpty {
                extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            }

        val mediaTitle = if (isMediaNotification) {
            resolvedTitle.ifEmpty { null }
        } else {
            null
        }
        val mediaArtist = if (isMediaNotification) {
            mediaArtistExtra.ifEmpty { resolvedText.ifEmpty { null } }
        } else {
            null
        }

        val actionLabels = actions
            .mapNotNull { action ->
                action.title
                    ?.toString()
                    ?.trim()
                    ?.takeUnless { it.isEmpty() }
            }
            .distinct()
            .take(4)

        val playPauseActionLabel = resolvePlayPauseActionLabel(actions)

        val mediaArtwork = if (isMediaNotification) {
            resolveMediaArtwork(notification, extras)
        } else {
            null
        }

        val media = if (isMediaNotification) {
            CoverMediaModel(
                title = mediaTitle,
                artist = mediaArtist,
                actionLabels = actionLabels,
                playPauseActionLabel = playPauseActionLabel,
                artworkBitmap = mediaArtwork
            )
        } else {
            null
        }

        val ongoingLike = isOngoing ||
            (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0

        return CoverNotificationModel(
            notificationKey = key,
            packageName = packageName,
            title = title,
            previewText = resolvedText,
            isClearable = isClearable,
            isOngoing = ongoingLike,
            postTime = postTime,
            isMediaNotification = isMediaNotification,
            media = media
        )
    }

    private fun resolveMediaArtwork(notification: Notification, extras: android.os.Bundle): Bitmap? {
        val metadataArtwork = resolveMediaArtworkFromSession(extras)
        if (metadataArtwork != null) return metadataArtwork

        val pictureArtwork = getParcelableBitmap(extras, Notification.EXTRA_PICTURE)
        if (pictureArtwork != null) return pictureArtwork

        val largeIconBig = getParcelableBitmap(extras, Notification.EXTRA_LARGE_ICON_BIG)
        if (largeIconBig != null) return largeIconBig

        val largeIcon = getParcelableBitmap(extras, Notification.EXTRA_LARGE_ICON)
        if (largeIcon != null) return largeIcon

        val notificationLargeIcon = runCatching { notification.getLargeIcon() }.getOrNull()
        return notificationLargeIcon?.let { icon -> iconToBitmap(icon) }
    }

    private fun resolveMediaArtworkFromSession(extras: android.os.Bundle): Bitmap? {
        val token = runCatching {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        }.getOrNull() ?: return null

        val metadata = runCatching { MediaController(this, token).metadata }
            .getOrElse { error ->
                Log.d("CoverNotifListener", "Unable to read media metadata from session: ${error.message}")
                null
            } ?: return null

        val artKeys = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON
        )
        return artKeys
            .asSequence()
            .mapNotNull(metadata::getBitmap)
            .map { bitmap -> toArgb8888Bitmap(bitmap) }
            .firstOrNull()
    }

    private fun getParcelableBitmap(extras: android.os.Bundle, key: String): Bitmap? {
        val rawValue = runCatching {
            extras.getParcelable(key, android.os.Parcelable::class.java)
                ?: extras.get(key)
        }.getOrElse { error ->
            Log.w("CoverNotifListener", "Unable to read notification extra key=$key: ${error.message}")
            null
        } ?: return null

        val resolvedBitmap = when (rawValue) {
            is Bitmap -> rawValue
            is Icon -> iconToBitmap(rawValue)
            else -> null
        }
        return resolvedBitmap?.let { bitmap -> toArgb8888Bitmap(bitmap) }
    }

    private fun iconToBitmap(icon: Icon): Bitmap? {
        val drawable = runCatching { icon.loadDrawable(this) }
            .getOrElse { error ->
                Log.w("CoverNotifListener", "Unable to load Icon drawable for media artwork: ${error.message}")
                null
            } ?: return null
        return drawableToBitmap(drawable)?.let { bitmap -> toArgb8888Bitmap(bitmap) }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable) {
            drawable.bitmap?.let { bitmap -> return bitmap }
        }

        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        return runCatching {
            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }.getOrElse { error ->
            Log.w("CoverNotifListener", "Unable to convert drawable to bitmap for media artwork: ${error.message}")
            null
        }
    }

    private fun toArgb8888Bitmap(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        return bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
    }

    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun resolvePlayPauseActionLabel(actions: Array<out Notification.Action>): String? {
        val semanticMatch = actions.firstOrNull { action ->
            action.semanticAction == Notification.Action.SEMANTIC_ACTION_PLAY ||
                action.semanticAction == Notification.Action.SEMANTIC_ACTION_PAUSE
        }?.title?.toString()?.trim().orEmpty()
        if (semanticMatch.isNotEmpty()) return semanticMatch

        // Fallback when apps don't publish semanticAction: keep OEM-provided label text.
        val keywordMatch = actions.firstOrNull { action ->
            val label = action.title?.toString()?.trim()?.lowercase().orEmpty()
            label.contains("play") || label.contains("pause") || label.contains("resume")
        }?.title?.toString()?.trim().orEmpty()
        if (keywordMatch.isNotEmpty()) return keywordMatch

        return actions
            .firstOrNull { action -> action.actionIntent != null }
            ?.title
            ?.toString()
            ?.trim()
            ?.takeUnless { it.isEmpty() }
    }

}

