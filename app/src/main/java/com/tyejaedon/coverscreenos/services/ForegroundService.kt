package com.tyejaedon.coverscreenos.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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

class ForegroundService : Service() {
    companion object {
        private const val DISPLAY_CHANGE_DEBOUNCE_MS = 450L
        private const val COVER_DETACH_GRACE_MS = 2_000L
        private const val APP_LAUNCH_RESUME_POLL_INTERVAL_MS = 250L
        private const val APP_LAUNCH_RESUME_MIN_SUPPRESSION_MS = 450L
        private const val APP_LAUNCH_RESUME_STALE_EVENT_MAX_MS = 2_500L
        private const val APP_LAUNCH_RESUME_STABLE_SIGNAL_COUNT = 2

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
        const val ACTION_HIDE_OVERLAY = "com.tyejaedon.coverscreenos.action.HIDE_OVERLAY"
        private const val EXTRA_LAUNCH_PACKAGE_NAME = "com.tyejaedon.coverscreenos.extra.LAUNCH_PACKAGE_NAME"

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

        // Requests immediate overlay suppression so a launched cover app can receive input focus.
        fun createHideOverlayIntent(context: Context, packageName: String?): Intent {
            return Intent(context, ForegroundService::class.java).apply {
                action = ACTION_HIDE_OVERLAY
                packageName
                    ?.trim()
                    ?.takeUnless { it.isEmpty() }
                    ?.let { putExtra(EXTRA_LAUNCH_PACKAGE_NAME, it) }
            }
        }
    }

    private lateinit var overlayWindowController: OverlayWindowController
    private lateinit var launchCoordinator: CoverLaunchCoordinator
    private lateinit var coverDisplayHelper: CoverDisplayHelper
    private lateinit var displayManager: DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayRequested = false
    private var isDisplayListenerRegistered = false
    private var pendingDisplayRetarget: Runnable? = null
    private var pendingCoverDetach: Runnable? = null

    // Tracks if we have temporarily hidden the overlay to let another app run.
    private var isOverlaySuppressedForAppLaunch = false
    private var launchSuppressedPackageName: String? = null
    private var launchSuppressedStartedElapsedMs = 0L
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
        launchCoordinator = CoverLaunchCoordinator(
            onBeginLaunch = ::prepareForOverlayAppLaunch,
            onLaunchDispatched = ::onAppLaunchDispatchAcknowledged,
            onLaunchFailed = ::restoreOverlayAfterLaunchFailure,
            debugThrowOnThreadViolation = isDebuggableBuild()
        )
        overlayWindowController = OverlayWindowController(
            context = this,
            launchCoordinator = launchCoordinator
        )
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

            ACTION_HIDE_OVERLAY -> {
                val launchPackageName = intent
                    .getStringExtra(EXTRA_LAUNCH_PACKAGE_NAME)
                    ?.trim()
                    ?.takeUnless { it.isEmpty() }
                    ?: "<unspecified>"

                val suppressionApplied = prepareForOverlayAppLaunch(launchPackageName)
                if (!suppressionApplied) {
                    Log.w(
                        "CoverForegroundService",
                        "Overlay hide request rejected package=$launchPackageName"
                    )
                }
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

        if (isOverlaySuppressedForAppLaunch) {
            maybeResumeOverlayAfterAppLaunch(reason = "display_$changeType:$displayId")
            return
        }

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

    private fun prepareForOverlayAppLaunch(packageName: String): Boolean {
        if (!hasRuntimePrerequisites()) {
            stopServiceForMissingPrerequisites(reason = "launch_app_missing_prerequisites")
            return false
        }

        overlayRequested = true
        coverDisplayHelper.startLockStatusMonitoring()
        registerDisplayListenerIfNeeded()
        suppressOverlayForAppLaunch(packageName)
        return true
    }

    private fun onAppLaunchDispatchAcknowledged(packageName: String) {
        Log.d(
            "CoverForegroundService",
            "launch dispatch acknowledged package=$packageName"
        )
    }

    private fun restoreOverlayAfterLaunchFailure(packageName: String) {
        clearAppLaunchSuppression()
        scheduleRetarget(
            reason = "launch_app_failed_restore_overlay:$packageName",
            immediate = true
        )
    }

    private fun suppressOverlayForAppLaunch(packageName: String) {
        isOverlaySuppressedForAppLaunch = true
        launchSuppressedPackageName = packageName
        launchSuppressedStartedElapsedMs = SystemClock.elapsedRealtime()
        resetResumeSignalStability()

        clearPendingDisplayWork()
        overlayWindowController.suppressOverlayForLaunch()
        isOverlayActive = false

        startSuppressionResumePolling()
    }

    private fun startSuppressionResumePolling() {
        if (suppressionResumePoller != null) return

        val poller = object : Runnable {
            override fun run() {
                maybeResumeOverlayAfterAppLaunch(reason = "poll")
                if (isOverlaySuppressedForAppLaunch) {
                    mainHandler.postDelayed(this, APP_LAUNCH_RESUME_POLL_INTERVAL_MS)
                } else {
                    suppressionResumePoller = null
                }
            }
        }

        suppressionResumePoller = poller
        mainHandler.post(poller)
    }

    private fun maybeResumeOverlayAfterAppLaunch(reason: String) {
        if (!isOverlaySuppressedForAppLaunch) return
        if (!overlayRequested) {
            clearAppLaunchSuppression()
            return
        }

        val suppressedForMs = SystemClock.elapsedRealtime() - launchSuppressedStartedElapsedMs
        if (suppressedForMs < APP_LAUNCH_RESUME_MIN_SUPPRESSION_MS) {
            return
        }

        val isLocked = coverDisplayHelper.getDisplayLockStatus()
        if (isLocked) {
            clearAppLaunchSuppression()
            scheduleRetarget(reason = "resume_after_app_launch_locked:$reason", immediate = true)
            return
        }

        val foregroundPackage = resolveForegroundPackageForResume() ?: return
        if (!hasStableResumeSignal(foregroundPackage)) return

        clearAppLaunchSuppression()
        scheduleRetarget(reason = "resume_after_app_launch:$foregroundPackage:$reason", immediate = true)
    }

    private fun resolveForegroundPackageForResume(): String? {
        val foregroundPackage = CoverAccessibilityService.currentForegroundPackage()
            ?.trim()
            ?.takeUnless { it.isEmpty() }
            ?: return null

        val foregroundEventAgeMs = CoverAccessibilityService.currentForegroundPackageEventAgeMs()
        if (foregroundEventAgeMs > APP_LAUNCH_RESUME_STALE_EVENT_MAX_MS) return null
        if (!shouldResumeOverlayForPackage(foregroundPackage)) return null

        return foregroundPackage
    }

    private fun shouldResumeOverlayForPackage(packageName: String): Boolean {
        val launchedPackage = launchSuppressedPackageName
            ?.trim()
            ?.takeUnless { it.isEmpty() }

        if (launchedPackage != null && packageName == launchedPackage) {
            return false
        }

        if (packageName == this.packageName) {
            return true
        }

        return OVERLAY_RESUME_PACKAGE_PREFIXES.any { prefix ->
            packageName.startsWith(prefix)
        }
    }

    private fun hasStableResumeSignal(packageName: String): Boolean {
        if (packageName == lastResumeSignalPackage) {
            resumeSignalStableCount += 1
        } else {
            lastResumeSignalPackage = packageName
            resumeSignalStableCount = 1
        }
        return resumeSignalStableCount >= APP_LAUNCH_RESUME_STABLE_SIGNAL_COUNT
    }

    private fun resetResumeSignalStability() {
        lastResumeSignalPackage = null
        resumeSignalStableCount = 0
    }

    private fun clearAppLaunchSuppression() {
        suppressionResumePoller?.let { mainHandler.removeCallbacks(it) }
        suppressionResumePoller = null

        isOverlaySuppressedForAppLaunch = false
        launchSuppressedPackageName = null
        launchSuppressedStartedElapsedMs = 0L
        resetResumeSignalStability()
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

    private fun isDebuggableBuild(): Boolean {
        return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}