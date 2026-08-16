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
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import com.tyejaedon.coverscreenos.helpers.CoverDisplayHelper
import com.tyejaedon.coverscreenos.helpers.ForegroundServiceHelper
import com.tyejaedon.coverscreenos.ui.controllers.CoverAppLauncher

class ForegroundService : Service() {
    companion object {
        private const val DISPLAY_CHANGE_DEBOUNCE_MS = 450L
        private const val COVER_DETACH_GRACE_MS = 2_000L
        private const val APP_LAUNCH_RESUME_POLL_INTERVAL_MS = 300L
        private const val APP_LAUNCH_RESUME_GRACE_MS = 850L
        private const val APP_LAUNCH_RESUME_STABLE_SIGNAL_COUNT = 2
        private const val APP_LAUNCH_EMPTY_PACKAGE_MAX_EVENT_AGE_MS = 1_500L

        private val OVERLAY_RESUME_PACKAGE_PREFIXES = arrayOf(
            "com.android.systemui",
            "com.samsung.systemui",
            "com.samsung.android.app.aodservice",
            "com.sec.android.app.launcher",
            "com.samsung.android.app.cocktailbarservice",
            "com.samsung.android.app.clockface"
        )

        @Volatile
        var isOverlayActive: Boolean = false
            private set

        const val CHANNEL_ID = "foreground_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.tyejaedon.coverscreenos.action.START"
        const val ACTION_STOP = "com.tyejaedon.coverscreenos.action.STOP"
        const val ACTION_LAUNCH_APP = "com.tyejaedon.coverscreenos.action.LAUNCH_APP"
        const val EXTRA_TARGET_PACKAGE = "com.tyejaedon.coverscreenos.extra.TARGET_PACKAGE"

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

        fun createLaunchAppIntent(context: Context, packageName: String): Intent {
            return Intent(context, ForegroundService::class.java).apply {
                action = ACTION_LAUNCH_APP
                putExtra(EXTRA_TARGET_PACKAGE, packageName)
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
    private var isOverlaySuppressedForAppLaunch = false
    private var launchSuppressedPackageName: String? = null
    private var launchSuppressedUntilElapsedMs: Long = 0L
    private var suppressionResumePoller: Runnable? = null
    private var resumeSignalStableCount = 0
    private var lastResumeSignalPackage: String? = null

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
                teardownOverlayRuntime()
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_LAUNCH_APP -> {
                val packageName = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
                    ?.trim()
                    .orEmpty()
                if (packageName.isEmpty()) {
                    return START_STICKY
                }

                handleAppLaunchRequest(packageName)
                return START_STICKY
            }

            ACTION_START, null -> {
                val foregroundStarted = runCatching {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    true
                }.getOrElse { error ->
                    Log.w("CoverForegroundService", "Unable to enter foreground mode: ${error.message}")
                    false
                }
                if (!foregroundStarted) {
                    teardownOverlayRuntime()
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (!hasRuntimePrerequisites()) {
                    stopServiceForMissingPrerequisites(reason = "start_request")
                    return START_NOT_STICKY
                }

                clearAppLaunchSuppression()
                overlayRequested = true
                coverDisplayHelper.startLockStatusMonitoring()
                registerDisplayListenerIfNeeded()
                scheduleRetarget(reason = "service_start", immediate = true)
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
        teardownOverlayRuntime()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }

    private fun onDisplayTopologyChanged(changeType: String, displayId: Int) {
        if (!overlayRequested) return
        if (isOverlaySuppressedForAppLaunch) return
        if (!hasRuntimePrerequisites()) {
            stopServiceForMissingPrerequisites(reason = "display_${changeType}_$displayId")
            return
        }

        scheduleRetarget(reason = "display_$changeType:$displayId", immediate = false)
    }

    private fun attachOrRetargetOverlay(reason: String) {
        if (isOverlaySuppressedForAppLaunch) return

        if (!hasRuntimePrerequisites()) {
            stopServiceForMissingPrerequisites(reason = "$reason prerequisites_lost")
            return
        }

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
            if (!overlayRequested) return@Runnable
            if (!hasRuntimePrerequisites()) {
                stopServiceForMissingPrerequisites(reason = "$reason detach_grace_prerequisites_lost")
                return@Runnable
            }

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

    private fun hasRuntimePrerequisites(): Boolean {
        return ForegroundServiceHelper.hasRequiredOverlayPermissions(this)
    }

    private fun teardownOverlayRuntime() {
        clearAppLaunchSuppression()
        overlayRequested = false
        clearPendingDisplayWork()
        coverDisplayHelper.stopLockStatusMonitoring()
        unregisterDisplayListenerIfNeeded()
        overlayWindowController.removeOverlay()
        isOverlayActive = false
    }

    private fun handleAppLaunchRequest(packageName: String) {
        if (!hasRuntimePrerequisites()) {
            stopServiceForMissingPrerequisites(reason = "launch_app_missing_prerequisites")
            return
        }

        overlayRequested = true
        coverDisplayHelper.startLockStatusMonitoring()
        registerDisplayListenerIfNeeded()

        val targetDisplayId = overlayWindowController.getActiveDisplayId()
            ?: coverDisplayHelper.getCoverDisplayId()

        suppressOverlayForAppLaunch(packageName)

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = this,
            packageName = packageName,
            displayId = targetDisplayId
        )
        if (!launched) {
            clearAppLaunchSuppression()
            scheduleRetarget(reason = "launch_app_failed_restore_overlay", immediate = true)
        }
    }

    private fun suppressOverlayForAppLaunch(packageName: String) {
        isOverlaySuppressedForAppLaunch = true
        launchSuppressedPackageName = packageName
        launchSuppressedUntilElapsedMs = SystemClock.elapsedRealtime() + APP_LAUNCH_RESUME_GRACE_MS
        resetResumeSignalStability()

        clearPendingDisplayWork()
        overlayWindowController.removeOverlay()
        isOverlayActive = false

        startSuppressionResumePolling()
    }

    private fun startSuppressionResumePolling() {
        if (suppressionResumePoller != null) return

        val poller = object : Runnable {
            override fun run() {
                if (!isOverlaySuppressedForAppLaunch) {
                    suppressionResumePoller = null
                    return
                }

                maybeResumeOverlayAfterAppLaunch()
                if (isOverlaySuppressedForAppLaunch) {
                    suppressionResumePoller = this
                    mainHandler.postDelayed(this, APP_LAUNCH_RESUME_POLL_INTERVAL_MS)
                } else {
                    suppressionResumePoller = null
                }
            }
        }

        suppressionResumePoller = poller
        mainHandler.postDelayed(poller, APP_LAUNCH_RESUME_POLL_INTERVAL_MS)
    }

    private fun maybeResumeOverlayAfterAppLaunch() {
        if (!isOverlaySuppressedForAppLaunch) return
        if (!overlayRequested) {
            clearAppLaunchSuppression()
            return
        }
        if (!hasRuntimePrerequisites()) {
            stopServiceForMissingPrerequisites(reason = "resume_after_launch_missing_prerequisites")
            return
        }
        val nowElapsedMs = SystemClock.elapsedRealtime()
        if (nowElapsedMs < launchSuppressedUntilElapsedMs) return

        val isLocked = coverDisplayHelper.getDisplayLockStatus()
        val foregroundPackage = CoverAccessibilityService.currentForegroundPackage()
        if (isLocked) {
            clearAppLaunchSuppression()
            scheduleRetarget(reason = "resume_after_app_launch_locked", immediate = true)
            return
        }

        val foregroundEventAgeMs = CoverAccessibilityService.currentForegroundPackageEventAgeMs(nowElapsedMs)
        val shouldResume = shouldResumeOverlayForPackage(
            foregroundPackage = foregroundPackage,
            foregroundPackageEventAgeMs = foregroundEventAgeMs
        )
        if (!shouldResume) {
            resetResumeSignalStability()
            return
        }
        if (!hasStableResumeSignal(foregroundPackage)) return

        clearAppLaunchSuppression()
        scheduleRetarget(reason = "resume_after_app_launch", immediate = true)
    }

    private fun shouldResumeOverlayForPackage(
        foregroundPackage: String?,
        foregroundPackageEventAgeMs: Long
    ): Boolean {
        val currentPackage = foregroundPackage?.trim().orEmpty()
        if (currentPackage.isEmpty()) {
            return foregroundPackageEventAgeMs <= APP_LAUNCH_EMPTY_PACKAGE_MAX_EVENT_AGE_MS
        }
        if (currentPackage == packageName) return true

        val launchedPackage = launchSuppressedPackageName
        if (!launchedPackage.isNullOrEmpty() && currentPackage == launchedPackage) {
            return false
        }

        return OVERLAY_RESUME_PACKAGE_PREFIXES.any { prefix ->
            currentPackage.startsWith(prefix)
        }
    }

    private fun hasStableResumeSignal(foregroundPackage: String?): Boolean {
        val signalPackage = foregroundPackage?.trim().orEmpty().ifEmpty { "<empty>" }
        if (signalPackage == lastResumeSignalPackage) {
            resumeSignalStableCount += 1
        } else {
            lastResumeSignalPackage = signalPackage
            resumeSignalStableCount = 1
        }

        return resumeSignalStableCount >= APP_LAUNCH_RESUME_STABLE_SIGNAL_COUNT
    }

    private fun resetResumeSignalStability() {
        lastResumeSignalPackage = null
        resumeSignalStableCount = 0
    }

    private fun clearAppLaunchSuppression() {
        isOverlaySuppressedForAppLaunch = false
        launchSuppressedPackageName = null
        launchSuppressedUntilElapsedMs = 0L
        resetResumeSignalStability()

        suppressionResumePoller?.let { mainHandler.removeCallbacks(it) }
        suppressionResumePoller = null
    }

    private fun stopServiceForMissingPrerequisites(reason: String) {
        Log.w(
            "CoverForegroundService",
            "Stopping service because runtime prerequisites are missing. reason=$reason"
        )
        teardownOverlayRuntime()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }
}