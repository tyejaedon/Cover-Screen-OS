package com.tyejaedon.coverscreenos.notifications

import android.app.PendingIntent
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.Context
import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import com.tyejaedon.coverscreenos.models.CoverMediaCustomAction
import com.tyejaedon.coverscreenos.models.CoverMediaModel
import com.tyejaedon.coverscreenos.models.CoverMediaTransportAction
import com.tyejaedon.coverscreenos.models.CoverNotificationModel
import com.tyejaedon.coverscreenos.receivers.LockStatusReceiver
import com.tyejaedon.coverscreenos.notifications.CallPackageMatchers
import com.tyejaedon.coverscreenos.overlay.ForegroundService
import com.tyejaedon.coverscreenos.ui.controllers.CoverAppLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.graphics.createBitmap

/**
 * System-bound [NotificationListenerService] that mirrors active notifications and media playback
 * sessions onto the cover screen and coordinates call-overlay suppression with [ForegroundService].
 *
 * Bridges [StatusBarNotification] snapshots and active [MediaController] callbacks into a single
 * reactive [StateFlow] consumed by Cover Screen OS UI panels.
 */
class CoverNotificationListenerService : NotificationListenerService() {

    /** Pairs a bound [MediaController] with its registered callback and [MediaSession.Token]. */
    private data class RegisteredMediaController(
        val token: MediaSession.Token,
        val controller: MediaController,
        val callback: MediaController.Callback
    )

    // Main-thread Handler used both for coalescing refreshes and dispatching MediaController callbacks.
    private val callbackHandler by lazy { Handler(Looper.getMainLooper()) }
    // Active MediaController registrations keyed by StatusBarNotification.key so callbacks stay bound across updates.
    private val mediaControllers = mutableMapOf<String, RegisteredMediaController>()

    /** Prevents queuing duplicate refresh runnables within the same main-Looper frame. */
    @Volatile
    private var refreshScheduled = false

    /** Rebuilds the active notification and call-state snapshot on the main thread. */
    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private val refreshRunnable = Runnable {
        refreshScheduled = false
        refreshActiveNotifications()
    }

    companion object {
        private const val NOTIFICATION_LOG_TAG = "CoverNotifListener"
        // Notification categories that must stay normal notifications even if they attach a MediaSession token.
        private val NON_MEDIA_CATEGORIES = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_STATUS,
            Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_ERROR,
            "stopwatch"
        )

        /** Decoded playback status and transport control flags exposed to cover media cards. */
        internal data class MediaPlaybackCapabilities(
            val isPlaying: Boolean,
            val canPlayPause: Boolean,
            val canSkipPrevious: Boolean,
            val canSkipNext: Boolean,
            val canSeek: Boolean = false
        )

        private val STANDARD_MEDIA_TRANSPORT_LABELS = setOf(
            "play",
            "pause",
            "play/pause",
            "previous",
            "prev",
            "skip to previous",
            "next",
            "skip to next",
            "stop"
        )

        /**
         * Returns `true` if [label] corresponds to a primary transport button already rendered
         * by the cover player (`PREVIOUS`, `PLAY_PAUSE`, `NEXT`, `STOP`).
         */
        internal fun isStandardMediaTransportLabel(label: String): Boolean {
            return label.trim().lowercase() in STANDARD_MEDIA_TRANSPORT_LABELS
        }

        /**
         * Returns `true` when a notification attaches a [MediaSession.Token] and does not belong
         * to a non-media category such as calls, alarms, or navigation.
         */
        internal fun isMediaNotification(
            category: String?,
            hasMediaSessionToken: Boolean
        ): Boolean {
            if (category in NON_MEDIA_CATEGORIES) return false
            return hasMediaSessionToken
        }

        /**
         * Maps Android [PlaybackState] state codes and supported-action bitmasks into UI transport
         * button capabilities.
         */
        internal fun resolvePlaybackCapabilities(
            state: Int?,
            actions: Long
        ): MediaPlaybackCapabilities {
            // Treat buffering as active playback so the button immediately offers Pause.
            val isPlaying = state == PlaybackState.STATE_PLAYING ||
                state == PlaybackState.STATE_BUFFERING
            // Bitmask checks against PlaybackState.getActions() determine which media buttons to show.
            val canPlayPause = (actions and (
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE
                )) != 0L
            val canSkipPrevious = (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L
            val canSkipNext = (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L
            val canSeek = (actions and PlaybackState.ACTION_SEEK_TO) != 0L
            return MediaPlaybackCapabilities(
                isPlaying = isPlaying,
                canPlayPause = canPlayPause,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                canSeek = canSeek
            )
        }

        /** Tracks whether the OS currently binds this [NotificationListenerService]. */
        @Volatile
        private var listenerConnected: Boolean = false

        /** Weak singleton pointer to the live service instance for overlay actions. */
        @Volatile
        private var activeService: CoverNotificationListenerService? = null

        // Reactive StateFlow backing activeNotificationsFlow() so cover panels recompose on every change.
        private val activeNotificationModels = MutableStateFlow<List<CoverNotificationModel>>(emptyList())
        // Cached (isActive, packageName) pair used to deduplicate call-state broadcasts to ForegroundService.
        private var lastCallNotificationState: Pair<Boolean, String?> = false to null

        /**
         * Builds [ActivityOptions] allowing [PendingIntent] launches from the visible cover
         * overlay under Android 16+ background-activity-start restrictions.
         */
        private fun createPendingIntentLaunchOptions(): android.os.Bundle {
            return ActivityOptions.makeBasic().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    pendingIntentBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                }
            }.toBundle()
        }

        /** Exposes the live, sorted list of cover-displayable notifications and media cards. */
        fun activeNotificationsFlow(): StateFlow<List<CoverNotificationModel>> {
            return activeNotificationModels.asStateFlow()
        }

        /**
         * Re-sends the most recent call-notification state to [ForegroundService] after service
         * restart or rebind.
         */
        fun replayCallNotificationStateToForegroundService() {
            val (isActive, packageName) = lastCallNotificationState
            ForegroundService.updateCallNotificationState(
                isActive = isActive,
                packageName = packageName
            )
        }

        /** Dismisses a single notification by its [StatusBarNotification.getKey] via the OS listener. */
        fun dismissNotificationFromOverlay(notificationKey: String): Boolean {
            val service = activeService ?: return false
            return runCatching {
                service.cancelNotification(notificationKey)
                true
            }.getOrElse { error ->
                Log.w(NOTIFICATION_LOG_TAG, "Dismiss failed key=$notificationKey error=${error.message}")
                false
            }
        }

        /** Clears all dismissible notifications via [NotificationListenerService.cancelAllNotifications]. */
        fun clearAllNotificationsFromOverlay(): Boolean {
            val service = activeService ?: return false
            return runCatching {
                service.cancelAllNotifications()
                true
            }.getOrElse { error ->
                Log.w(NOTIFICATION_LOG_TAG, "Clear all failed error=${error.message}")
                false
            }
        }

        /**
         * Opens a tapped notification from the cover overlay when the device is unlocked.
         *
         * Hides the cover overlay first, attempts to invoke the notification's [PendingIntent]
         * (`contentIntent`), and falls back to launching the source app on the cover display if
         * the [PendingIntent] is missing, canceled, or fails.
         */
        fun openNotificationFromOverlay(context: Context, model: CoverNotificationModel): Boolean {
            if (isDeviceLocked(context)) {
                Log.d(NOTIFICATION_LOG_TAG, "Blocked notification open while locked key=${model.notificationKey}")
                return false
            }

            // Live listener instance and matching system notification looked up for its tap PendingIntent.
            val service = activeService
            val targetSbn = service?.findNotificationByKey(model.notificationKey)

            // Records whether ForegroundService accepted the command to hide the cover overlay before launching.
            val launchAcknowledged = runCatching {
                context.startService(
                    ForegroundService.createHideOverlayIntent(
                        context = context,
                        packageName = model.packageName
                    )
                )
            }.isSuccess

            // App-provided PendingIntent deep-linking into the specific conversation or item.
            val contentIntent = targetSbn?.notification?.contentIntent
            if (contentIntent != null) {
                val launchedFromIntent = runCatching {
                    val options = createPendingIntentLaunchOptions()
                    contentIntent.send(context, 0, null, null, null, null, options)
                    true
                }.getOrElse { error ->
                    val message = if (error is PendingIntent.CanceledException) {
                        "Notification content intent canceled key=${model.notificationKey}"
                    } else {
                        "Notification content intent failed key=${model.notificationKey} error=${error.message}"
                    }
                    Log.w(NOTIFICATION_LOG_TAG, message)
                    false
                }
                if (launchedFromIntent) return true
            }

            if (!launchAcknowledged) {
                Log.w(NOTIFICATION_LOG_TAG, "Overlay suppression dispatch failed package=${model.packageName}")
            }

            return CoverAppLauncher.launchPackageOnDisplay(
                context = context,
                packageName = model.packageName,
                displayId = runCatching { context.display.displayId }.getOrNull()
            )
        }

        /** Hides the cover overlay and launches [packageName] directly on the current display. */
        fun launchNotificationSourceApp(context: Context, packageName: String): Boolean {

            runCatching {
                context.startService(ForegroundService.createHideOverlayIntent(context, packageName))
            }
            return CoverAppLauncher.launchPackageOnDisplay(
                context = context,
                packageName = packageName,
                displayId = runCatching { context.display.displayId }.getOrNull()
            )
        }

        /**
         * Dispatches a play/pause, track-skip, seek, custom-session, or notification action
         * command to the media session or notification bound to [notificationKey].
         */
        fun performMediaTransportAction(
            notificationKey: String,
            action: CoverMediaTransportAction
        ): Boolean {
            val service = activeService ?: return false
            if (action is CoverMediaTransportAction.NotificationAction) {
                val targetSbn = service.findNotificationByKey(notificationKey) ?: return false
                val pendingIntent = targetSbn.notification.actions?.getOrNull(action.index)?.actionIntent
                    ?: return false
                return runCatching {
                    pendingIntent.send(service, 0, null, null, null, null, createPendingIntentLaunchOptions())
                    true
                }.getOrElse { error ->
                    Log.w(
                        NOTIFICATION_LOG_TAG,
                        "Media notification action failed key=$notificationKey index=${action.index} error=${error.message}"
                    )
                    false
                }
            }

            // MediaController handle bound to the media app's active MediaSession.Token.
            val controller = service.resolveMediaController(notificationKey) ?: return false
            // TransportControls interface translating play/pause/skip/seek/custom commands to the media app.
            val controls = controller.transportControls
            val playbackState = runCatching { controller.playbackState }.getOrNull()
            val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING ||
                playbackState?.state == PlaybackState.STATE_BUFFERING
            return runCatching {
                when (action) {
                    CoverMediaTransportAction.PLAY_PAUSE -> {
                        if (isPlaying) controls.pause() else controls.play()
                    }
                    CoverMediaTransportAction.PREVIOUS -> controls.skipToPrevious()
                    CoverMediaTransportAction.NEXT -> controls.skipToNext()
                    is CoverMediaTransportAction.SeekTo -> controls.seekTo(action.positionMs.coerceAtLeast(0L))
                    is CoverMediaTransportAction.CustomAction -> {
                        val matching = playbackState?.customActions?.firstOrNull { it.action == action.action }
                        if (matching != null) {
                            controls.sendCustomAction(matching, matching.extras)
                        } else {
                            controls.sendCustomAction(action.action, null)
                        }
                    }
                    is CoverMediaTransportAction.NotificationAction -> Unit
                }
                true
            }.getOrElse { error ->
                Log.w(
                    NOTIFICATION_LOG_TAG,
                    "Media transport action failed key=$notificationKey action=$action error=${error.message}"
                )
                false
            }
        }

        /** Checks both the cached lock receiver and [KeyguardManager] to enforce lockscreen safety. */
        private fun isDeviceLocked(context: Context): Boolean {
            if (LockStatusReceiver.currentLockStatus(context)) return true
            val keyguardManager = context.getSystemService(KeyguardManager::class.java)
            return keyguardManager?.isDeviceLocked
                ?: keyguardManager?.isKeyguardLocked
                ?: true
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
    }

    /**
     * Cleans up pending refresh callbacks, unregisters every [MediaController.Callback],
     * clears active notification/call state, and detaches the singleton listener reference.
     */
    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    override fun onDestroy() {
        callbackHandler.removeCallbacks(refreshRunnable)
        refreshScheduled = false
        unregisterAllMediaControllers()
        if (activeService === this) {
            activeService = null
        }
        listenerConnected = false
        activeNotificationModels.value = emptyList()
        dispatchCallNotificationState(isActive = false, packageName = null)
        super.onDestroy()
    }

    /** Called by Android once the notification listener binds; primes the initial notification snapshot. */
    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    override fun onListenerConnected() {
        super.onListenerConnected()
        activeService = this
        listenerConnected = true
        scheduleNotificationRefresh()
        Log.d(NOTIFICATION_LOG_TAG, "Notification listener connected")
    }

    /** Tears down media callbacks and clears cover notification state when unbound by the OS. */
    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    override fun onListenerDisconnected() {
        callbackHandler.removeCallbacks(refreshRunnable)
        refreshScheduled = false
        unregisterAllMediaControllers()
        if (activeService === this) {
            activeService = null
        }
        listenerConnected = false
        activeNotificationModels.value = emptyList()
        dispatchCallNotificationState(isActive = false, packageName = null)
        Log.d(NOTIFICATION_LOG_TAG, "Notification listener disconnected")
        super.onListenerDisconnected()
    }

    /** Schedules a coalesced snapshot refresh when a notification is posted or updated. */
    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        scheduleNotificationRefresh()
    }

    /** Schedules a coalesced snapshot refresh when a notification is dismissed or canceled. */
    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        scheduleNotificationRefresh()
    }

    /** Schedules at most one pending refresh on [callbackHandler] while the listener is connected. */
    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun scheduleNotificationRefresh() {
        if (!listenerConnected) return
        if (refreshScheduled) return
        refreshScheduled = true
        // Coalesce bursty posted/removed callbacks and avoid binder-heavy work in callback context.
        callbackHandler.post(refreshRunnable)
    }

    /**
     * Queries [NotificationListenerService.getActiveNotifications], reconciles [MediaController]
     * bindings, emits sorted [CoverNotificationModel] items, and notifies [ForegroundService]
     * if an active call notification is present.
     */
    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun refreshActiveNotifications() {
        // Live snapshot of all system status-bar notifications queried over Binder IPC.
        val activeSbns = runCatching {
            this.activeNotifications.toList()
        }.getOrElse { error ->
            Log.w(NOTIFICATION_LOG_TAG, "Unable to query active notifications: ${error.message}")
            emptyList()
        }

        syncMediaControllers(activeSbns)

        // UI-ready notification and media models sorted newest-first for the cover notification panel.
        val snapshot = runCatching {
            activeSbns
                .asSequence()
                .mapNotNull { sbn -> sbn.toCoverNotificationModel() }
                .sortedByDescending { model -> model.postTime }
                .toList()
        }.getOrElse { error ->
            Log.w(NOTIFICATION_LOG_TAG, "Unable to refresh notifications: ${error.message}")
            emptyList()
        }

        activeNotificationModels.value = snapshot

        // Package name of the newest active call notification so ForegroundService can yield the overlay.
        val activeCallPackage = activeSbns
            .asSequence()
            .filter { sbn -> sbn.isLikelyActiveCallNotification() }
            .maxByOrNull { sbn -> sbn.postTime }
            ?.packageName
            ?.trim()
            ?.takeUnless { it.isEmpty() }
        dispatchCallNotificationState(
            isActive = activeCallPackage != null,
            packageName = activeCallPackage
        )
    }

    /** Forwards call-overlay suppression changes to [ForegroundService] only when state changes. */
    private fun dispatchCallNotificationState(isActive: Boolean, packageName: String?) {
        val normalizedPackage = packageName?.trim()?.takeUnless { it.isEmpty() }
        val normalizedState = isActive to normalizedPackage
        if (lastCallNotificationState == normalizedState) return

        lastCallNotificationState = normalizedState
        ForegroundService.updateCallNotificationState(
            isActive = isActive,
            packageName = normalizedPackage
        )
    }

    /**
     * Reconciles active media notifications against [mediaControllers], unregistering stale
     * controllers and attaching [MediaController.Callback] listeners for newly discovered sessions
     * so playback state/metadata updates push refreshes immediately without polling.
     */
    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun syncMediaControllers(activeSbns: List<StatusBarNotification>) {
        // Desired notification key -> MediaSession.Token map from the current active notification snapshot.
        val desiredTokens = mutableMapOf<String, MediaSession.Token>()
        for (sbn in activeSbns) {
            if (sbn.isLikelyActiveCallNotification()) continue
            val token = sbn.extractMediaSessionToken()
            if (isMediaNotification(category = sbn.notification.category, hasMediaSessionToken = token != null) && token != null) {
                desiredTokens[sbn.key] = token
            }
        }

        // Drop controllers whose notification disappeared or rotated its MediaSession.Token.
        val iterator = mediaControllers.entries.iterator()
        while (iterator.hasNext()) {
            val (key, entry) = iterator.next()
            val desiredToken = desiredTokens[key]
            if (desiredToken == null || desiredToken != entry.token) {
                runCatching { entry.controller.unregisterCallback(entry.callback) }
                iterator.remove()
            }
        }

        // Bind new MediaControllers on callbackHandler so transport controls stay reactive.
        for ((key, token) in desiredTokens) {
            if (mediaControllers.containsKey(key)) continue
            // System MediaController bound to this media session for metadata, playback state, and transport controls.
            val controller = runCatching { MediaController(this, token) }.getOrNull() ?: continue
            // Callback pushing instant UI refreshes whenever track info, play state, or session lifecycle changes.
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    scheduleNotificationRefresh()
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    scheduleNotificationRefresh()
                }

                override fun onSessionDestroyed() {
                    scheduleNotificationRefresh()
                }
            }
            runCatching {
                controller.registerCallback(callback, callbackHandler)
                mediaControllers[key] = RegisteredMediaController(
                    token = token,
                    controller = controller,
                    callback = callback
                )
            }.onFailure { error ->
                Log.w(NOTIFICATION_LOG_TAG, "Unable to register MediaController callback key=$key: ${error.message}")
            }
        }
    }

    /** Unregisters all tracked [MediaController] callbacks to prevent binder/memory leaks. */
    private fun unregisterAllMediaControllers() {
        mediaControllers.values.forEach { entry ->
            runCatching { entry.controller.unregisterCallback(entry.callback) }
        }
        mediaControllers.clear()
    }

    /** Looks up the cached [MediaController] for [notificationKey] or instantiates one on demand. */
    private fun resolveMediaController(notificationKey: String): MediaController? {
        mediaControllers[notificationKey]?.let { return it.controller }
        val token = findNotificationByKey(notificationKey)?.extractMediaSessionToken() ?: return null
        return runCatching { MediaController(this, token) }.getOrNull()
    }

    /** Extracts the [MediaSession.Token] from [Notification.EXTRA_MEDIA_SESSION] if present. */
    private fun StatusBarNotification.extractMediaSessionToken(): MediaSession.Token? {
        val extras = notification.extras ?: return null
        return runCatching {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        }.getOrNull()
    }

    /** Finds a live [StatusBarNotification] matching [notificationKey] in [activeNotifications]. */
    private fun findNotificationByKey(notificationKey: String): StatusBarNotification? {
        return runCatching {
            activeNotifications.firstOrNull { item -> item.key == notificationKey }
        }.getOrElse { error ->
            Log.w(NOTIFICATION_LOG_TAG, "Unable to query active notification key=$notificationKey error=${error.message}")
            null
        }
    }

    /**
     * Heuristic detecting incoming or ongoing voice/VoIP call notifications using
     * [CallPackageMatchers], [Notification.CATEGORY_CALL], and ongoing call text cues.
     */
    private fun StatusBarNotification.isLikelyActiveCallNotification(): Boolean {
        val callPackage = packageName.trim().takeUnless { it.isEmpty() }
        val notificationCategory = notification.category.orEmpty()
        if (callPackage != null && CallPackageMatchers.isIncomingCallPackage(callPackage)) {
            return isOngoing || notificationCategory == Notification.CATEGORY_CALL
        }

        if (notificationCategory == Notification.CATEGORY_CALL) {
            return true
        }

        if (!isOngoing && (notification.flags and Notification.FLAG_ONGOING_EVENT) == 0) {
            return false
        }

        val extras = notification.extras
        val mergedText = buildString {
            append(extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
            append(' ')
            append(extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
            append(' ')
            append(extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty())
        }.trim().lowercase()

        return mergedText.contains("incoming call") ||
            mergedText.contains("ringing") ||
            mergedText.contains("calling")
    }

    /**
     * Converts a system [StatusBarNotification] into a cover-screen [CoverNotificationModel],
     * enriching media notifications with live [MediaController] metadata, playback actions, and
     * artwork.
     */
    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun StatusBarNotification.toCoverNotificationModel(): CoverNotificationModel? {
        // Notification payload Bundle holding title, body text, icons, and optional MediaSession token.
        val extras = notification.extras ?: return null
        val resolvedTitle = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            ?.trim()
            .orEmpty()
        val resolvedText = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
            )
            ?.toString()
            ?.trim()
            .orEmpty()

        // Fall back to the posting app's launcher label if the notification omitted a title.
        val title = resolvedTitle.ifEmpty {
            runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            }.getOrDefault(packageName)
        }

        // Non-null when the notification embeds an active MediaSession for playback controls.
        val mediaToken = extractMediaSessionToken()
        // Exclude active calls so call cards stay prioritized as call alerts rather than media players.
        val isMediaNotification = !isLikelyActiveCallNotification() && isMediaNotification(
            category = notification.category,
            hasMediaSessionToken = mediaToken != null
        )

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

        // Rich playback model populated from MediaController metadata, transport flags, and album art.
        val media = if (isMediaNotification) {
            val controller = mediaControllers[key]?.controller
                ?: mediaToken?.let {
                    runCatching { MediaController(this@CoverNotificationListenerService, it) }.getOrNull()
                }
            val metadata = runCatching { controller?.metadata }.getOrNull()
            val playbackState = runCatching { controller?.playbackState }.getOrNull()
            val capabilities = resolvePlaybackCapabilities(
                state = playbackState?.state,
                actions = playbackState?.actions ?: 0L
            )
            // Prefer MediaSession metadata over notification extras, which can lag during track skips.
            val mediaTitle = metadata
                ?.firstNonBlankString(
                    MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
                    MediaMetadata.METADATA_KEY_TITLE
                )
                ?: resolvedTitle.ifEmpty { null }
            val mediaArtist = metadata
                ?.firstNonBlankString(
                    MediaMetadata.METADATA_KEY_ARTIST,
                    MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                    MediaMetadata.METADATA_KEY_ALBUM_ARTIST
                )
                ?: mediaArtistExtra.ifEmpty { resolvedText.ifEmpty { null } }
            val mediaArtwork = resolveMediaArtwork(notification, extras, metadata)
            val durationMs = metadata
                ?.getLong(MediaMetadata.METADATA_KEY_DURATION)
                ?.takeIf { it > 0L }
                ?: 0L
            val positionMs = playbackState?.position?.takeIf { it >= 0L }
                ?: PlaybackState.PLAYBACK_POSITION_UNKNOWN
            val playbackSpeed = playbackState?.playbackSpeed ?: 0f
            val positionUpdateTimeMs = playbackState?.lastPositionUpdateTime ?: 0L
            val customActions = resolveCustomActions(
                packageName = packageName,
                notification = notification,
                playbackState = playbackState
            )

            CoverMediaModel(
                title = mediaTitle,
                artist = mediaArtist,
                isPlaying = capabilities.isPlaying,
                canPlayPause = capabilities.canPlayPause,
                canSkipPrevious = capabilities.canSkipPrevious,
                canSkipNext = capabilities.canSkipNext,
                artworkBitmap = mediaArtwork,
                durationMs = durationMs,
                positionMs = positionMs,
                playbackSpeed = playbackSpeed,
                positionUpdateTimeMs = positionUpdateTimeMs,
                canSeek = capabilities.canSeek,
                customActions = customActions
            )
        } else {
            null
        }

        // True for persistent/foreground notifications that users cannot simply swipe away.
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

    /** Returns the first non-blank string value found across [keys] in [MediaMetadata]. */
    private fun MediaMetadata.firstNonBlankString(vararg keys: String): String? {
        return keys
            .asSequence()
            .mapNotNull { key -> getString(key)?.trim()?.takeIf { it.isNotEmpty() } }
            .firstOrNull()
    }

    /**
     * Collects extra media controls (e.g. shuffle, repeat, favorite) from
     * [PlaybackState.getCustomActions] and non-standard [Notification.actions], deduplicated by
     * normalized label so primary transport buttons are not repeated.
     */
    private fun resolveCustomActions(
        packageName: String,
        notification: Notification,
        playbackState: PlaybackState?
    ): List<CoverMediaCustomAction> {
        val result = mutableListOf<CoverMediaCustomAction>()
        val seenLabels = mutableSetOf<String>()

        playbackState?.customActions.orEmpty().forEach { customAction ->
            val actionId = customAction.action?.trim().orEmpty()
            if (actionId.isEmpty()) return@forEach
            val label = customAction.name?.toString()?.trim().orEmpty().ifEmpty { actionId }
            val normalizedLabel = label.lowercase()
            if (isStandardMediaTransportLabel(normalizedLabel) || !seenLabels.add(normalizedLabel)) {
                return@forEach
            }
            val iconBitmap = if (customAction.icon != 0) {
                runCatching { Icon.createWithResource(packageName, customAction.icon) }
                    .getOrNull()
                    ?.let { icon -> iconToBitmap(icon) }
            } else {
                null
            }
            result += CoverMediaCustomAction(
                id = "custom:$actionId",
                label = label,
                iconBitmap = iconBitmap,
                transportAction = CoverMediaTransportAction.CustomAction(actionId)
            )
        }

        notification.actions.orEmpty().forEachIndexed { index, notifAction ->
            if (notifAction.actionIntent == null) return@forEachIndexed
            val label = notifAction.title?.toString()?.trim().orEmpty()
            if (label.isEmpty()) return@forEachIndexed
            val normalizedLabel = label.lowercase()
            if (isStandardMediaTransportLabel(normalizedLabel) || !seenLabels.add(normalizedLabel)) {
                return@forEachIndexed
            }
            val iconBitmap = runCatching { notifAction.getIcon() }
                .getOrNull()
                ?.let { icon -> iconToBitmap(icon) }
            result += CoverMediaCustomAction(
                id = "notif:$index:$normalizedLabel",
                label = label,
                iconBitmap = iconBitmap,
                transportAction = CoverMediaTransportAction.NotificationAction(index)
            )
        }

        return result
    }

    /**
     * Resolves album/media artwork by falling back from [MediaMetadata] bitmaps through
     * [Notification.EXTRA_PICTURE], [Notification.EXTRA_LARGE_ICON_BIG], and [Notification.getLargeIcon].
     */
    private fun resolveMediaArtwork(
        notification: Notification,
        extras: android.os.Bundle,
        metadata: MediaMetadata?
    ): Bitmap? {
        val metadataArtwork = resolveMediaArtworkFromMetadata(metadata)
        if (metadataArtwork != null) return metadataArtwork

        val pictureArtwork = getParcelableBitmap(extras, Notification.EXTRA_PICTURE)
        if (pictureArtwork != null) return pictureArtwork

        val largeIconBig = getParcelableBitmap(extras, Notification.EXTRA_LARGE_ICON_BIG)
        if (largeIconBig != null) return largeIconBig

        val notificationLargeIcon = runCatching { notification.getLargeIcon() }.getOrNull()
        return notificationLargeIcon?.let { icon -> iconToBitmap(icon) }
    }

    private fun resolveMediaArtworkFromMetadata(metadata: MediaMetadata?): Bitmap? {
        if (metadata == null) return null

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

    /** Reads a [Bitmap] or [Icon] extra safely via typed `getParcelable` and normalizes it. */
    private fun getParcelableBitmap(extras: android.os.Bundle, key: String): Bitmap? {
        val bitmapValue = runCatching {
            extras.getParcelable(key, Bitmap::class.java)
        }.getOrElse { error ->
            Log.w(NOTIFICATION_LOG_TAG, "Unable to read bitmap extra key=$key: ${error.message}")
            null
        }
        if (bitmapValue != null) {
            return toArgb8888Bitmap(bitmapValue)
        }

        val iconValue = runCatching {
            extras.getParcelable(key, Icon::class.java)
        }.getOrElse { error ->
            Log.w(NOTIFICATION_LOG_TAG, "Unable to read icon extra key=$key: ${error.message}")
            null
        } ?: return null

        return iconToBitmap(iconValue)?.let { bitmap -> toArgb8888Bitmap(bitmap) }
    }

    private fun iconToBitmap(icon: Icon): Bitmap? {
        val drawable = runCatching { icon.loadDrawable(this) }
            .getOrElse { error ->
                Log.w(NOTIFICATION_LOG_TAG, "Unable to load Icon drawable for media artwork: ${error.message}")
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
            Log.w(NOTIFICATION_LOG_TAG, "Unable to convert drawable to bitmap for media artwork: ${error.message}")
            null
        }
    }

    /**
     * Bounds oversized album art to 512px max edge and converts hardware/indexed bitmaps to
     * `ARGB_8888` so Compose `asImageBitmap()` can render them without hardware-bitmap errors.
     */
    private fun toArgb8888Bitmap(bitmap: Bitmap): Bitmap {
        val maxEdge = maxOf(bitmap.width, bitmap.height)
        val scaled = if (maxEdge > 512) {
            val scale = 512f / maxEdge.toFloat()
            val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
            runCatching { Bitmap.createScaledBitmap(bitmap, targetW, targetH, true) }.getOrDefault(bitmap)
        } else {
            bitmap
        }
        if (scaled.config == Bitmap.Config.ARGB_8888) return scaled
        return scaled.copy(Bitmap.Config.ARGB_8888, false) ?: scaled
    }

}
