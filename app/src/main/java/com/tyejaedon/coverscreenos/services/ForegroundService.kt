package com.tyejaedon.coverscreenos.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import com.tyejaedon.coverscreenos.helpers.AppPermissionHelper
import com.tyejaedon.coverscreenos.helpers.CoverDisplayHelper

class ForegroundService : Service() {
    companion object {
        private const val DISPLAY_CHANGE_DEBOUNCE_MS = 450L
        private const val COVER_DETACH_GRACE_MS = 2_000L

        @Volatile
        var isOverlayActive: Boolean = false
            private set

        const val CHANNEL_ID = "foreground_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.tyejaedon.coverscreenos.action.START"
        const val ACTION_STOP = "com.tyejaedon.coverscreenos.action.STOP"

        // Creates an explicit intent used to start this foreground service.
        fun createStartIntent(context: Context): Intent {
            return Intent(context, ForegroundService::class.java).apply {
                action = ACTION_START
            }
        }

        // Creates an explicit intent used to stop this foreground service.
        fun createStopIntent(context: Context): Intent {
            return Intent(context, ForegroundService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }

    private lateinit var overlayWindowController: OverlayWindowController
    private lateinit var coverDisplayHelper: CoverDisplayHelper
    private lateinit var displayManager: DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayRequested = false
    private var isDisplayListenerRegistered = false
    private var pendingDisplayRetarget: Runnable? = null
    private var pendingCoverDetach: Runnable? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            onDisplayTopologyChanged("added", displayId)
        }

        override fun onDisplayRemoved(displayId: Int) {
            onDisplayTopologyChanged("removed", displayId)
        }

        override fun onDisplayChanged(displayId: Int) {
            onDisplayTopologyChanged("changed", displayId)
        }
    }

    // Creates the Android O+ notification channel required for foreground notifications.
    private fun createNotificationChannel() {

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Used for maintaining persistent background services"
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    // Builds the persistent notification shown while the service is in foreground mode.
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cover Screen OS Running")
            .setContentText("Monitoring system state in the background...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // Initializes one-time service dependencies.
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        overlayWindowController = OverlayWindowController(this)
        coverDisplayHelper = CoverDisplayHelper(this)
        displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
    }

    // Handles start/stop commands and keeps the service alive after process recreation.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                overlayRequested = false
                clearPendingDisplayWork()
                coverDisplayHelper.stopLockStatusMonitoring()
                overlayWindowController.removeOverlay()
                isOverlayActive = false
                unregisterDisplayListenerIfNeeded()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                if (AppPermissionHelper.canDrawOverlays(this)) {
                    overlayRequested = true
                    coverDisplayHelper.startLockStatusMonitoring()
                    registerDisplayListenerIfNeeded()
                    scheduleRetarget(reason = "service_start", immediate = true)
                } else {
                    overlayRequested = false
                    isOverlayActive = false
                    clearPendingDisplayWork()
                    coverDisplayHelper.stopLockStatusMonitoring()
                    unregisterDisplayListenerIfNeeded()
                }
            }
        }

        return START_STICKY
    }

    // Returns null because this is a started service, not a bound service.
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // Cleans up foreground state when the service is destroyed.
    override fun onDestroy() {
        overlayRequested = false
        clearPendingDisplayWork()
        coverDisplayHelper.stopLockStatusMonitoring()
        unregisterDisplayListenerIfNeeded()
        overlayWindowController.removeOverlay()
        isOverlayActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun onDisplayTopologyChanged(changeType: String, displayId: Int) {
        if (!overlayRequested) return
        if (!AppPermissionHelper.canDrawOverlays(this)) return

        scheduleRetarget(reason = "display_$changeType:$displayId", immediate = false)
    }

    private fun attachOrRetargetOverlay(reason: String) {
        val targetDisplay = coverDisplayHelper.getCoverDisplay()
        if (targetDisplay != null) {
            cancelPendingCoverDetach()
            attachOverlayToTarget(targetDisplay, reason)
            return
        }

        val activeId = overlayWindowController.getActiveDisplayId()
        val currentlyOnCover = overlayWindowController.isOverlayAttached() &&
            activeId != null &&
            activeId != Display.DEFAULT_DISPLAY

        if (currentlyOnCover) {
            scheduleCoverDetach(reason)
            Log.d(
                "CoverForegroundService",
                "overlay reason=$reason holding_on_cover activeId=$activeId detachGraceMs=$COVER_DETACH_GRACE_MS displays=${coverDisplayHelper.describeDisplays()}"
            )
            return
        }

        cancelPendingCoverDetach()
        if (overlayWindowController.isOverlayAttached()) {
            overlayWindowController.removeOverlay()
        }
        isOverlayActive = false
        Log.d(
            "CoverForegroundService",
            "overlay reason=$reason no_cover_available attached=false displays=${coverDisplayHelper.describeDisplays()}"
        )
    }

    private fun attachOverlayToTarget(targetDisplay: Display, reason: String) {
        val targetId = targetDisplay.displayId
        val activeId = overlayWindowController.getActiveDisplayId()
        val shouldForceRetarget = overlayWindowController.isOverlayAttached() && activeId != targetId

        val didAttach = overlayWindowController.showOverlay(
            targetDisplay,
            shouldForceRetarget,
            coverDisplayHelper.isDeviceLocked
        )
        isOverlayActive = didAttach

        Log.d(
            "CoverForegroundService",
            "overlay reason=$reason targetId=$targetId activeId=${overlayWindowController.getActiveDisplayId()} forceRetarget=$shouldForceRetarget attached=$didAttach displays=${coverDisplayHelper.describeDisplays()}"
        )
    }

    private fun scheduleRetarget(reason: String, immediate: Boolean) {
        pendingDisplayRetarget?.let { mainHandler.removeCallbacks(it) }

        val runnable = Runnable {
            pendingDisplayRetarget = null
            attachOrRetargetOverlay(reason)
        }
        pendingDisplayRetarget = runnable

        if (immediate) {
            mainHandler.post(runnable)
        } else {
            mainHandler.postDelayed(runnable, DISPLAY_CHANGE_DEBOUNCE_MS)
        }
    }

    private fun scheduleCoverDetach(reason: String) {
        if (pendingCoverDetach != null) return

        val runnable = Runnable {
            pendingCoverDetach = null
            if (!overlayRequested || !AppPermissionHelper.canDrawOverlays(this)) return@Runnable

            val coverDisplay = coverDisplayHelper.getCoverDisplay()
            if (coverDisplay != null) {
                attachOverlayToTarget(coverDisplay, "$reason grace_cancelled_cover_returned")
            } else {
                overlayWindowController.removeOverlay()
                isOverlayActive = false
                Log.d(
                    "CoverForegroundService",
                    "overlay reason=$reason grace_elapsed detached=true displays=${coverDisplayHelper.describeDisplays()}"
                )
            }
        }
        pendingCoverDetach = runnable
        mainHandler.postDelayed(runnable, COVER_DETACH_GRACE_MS)
    }

    private fun cancelPendingCoverDetach() {
        pendingCoverDetach?.let { mainHandler.removeCallbacks(it) }
        pendingCoverDetach = null
    }

    private fun clearPendingDisplayWork() {
        pendingDisplayRetarget?.let { mainHandler.removeCallbacks(it) }
        pendingDisplayRetarget = null
        cancelPendingCoverDetach()
    }

    private fun registerDisplayListenerIfNeeded() {
        if (isDisplayListenerRegistered) return
        displayManager.registerDisplayListener(displayListener, mainHandler)
        isDisplayListenerRegistered = true
    }

    private fun unregisterDisplayListenerIfNeeded() {
        if (!isDisplayListenerRegistered) return
        displayManager.unregisterDisplayListener(displayListener)
        isDisplayListenerRegistered = false
    }
}