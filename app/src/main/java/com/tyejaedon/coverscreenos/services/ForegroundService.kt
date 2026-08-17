package com.tyejaedon.coverscreenos.services

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.helpers.CoverDisplayHelper
import com.tyejaedon.coverscreenos.helpers.ForegroundServiceHelper
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class ForegroundService : Service() {
    companion object {
        private const val LOG_TAG = "CoverForegroundService"
        private const val DISPLAY_CHANGE_DEBOUNCE_MS = 450L
        private const val COVER_DETACH_GRACE_MS = 2_000L
        private const val APP_LAUNCH_RESUME_POLL_INTERVAL_MS = 250L
        private const val APP_LAUNCH_RESUME_MIN_SUPPRESSION_MS = 450L
        private const val APP_LAUNCH_RESUME_STALE_EVENT_MAX_MS = 2_500L
        private const val APP_LAUNCH_RESUME_STABLE_SIGNAL_COUNT = 2
        private const val APP_LAUNCH_RESUME_MAX_SUPPRESSION_MS = 45_000L
        private const val OVERLAY_RECLAIM_MIN_INTERVAL_MS = 500L
        private const val OVERLAY_RECLAIM_LOG_TAG = "CoverOverlayReclaim"

        private val TRANSIENT_SYSTEM_UI_PREFIXES = arrayOf(
            "com.android.systemui",
            "com.samsung.systemui",
            "com.samsung.android.app.aodservice"
        )

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

        @Volatile
        private var activeServiceRef: WeakReference<ForegroundService>? = null

        const val CHANNEL_ID = "foreground_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.tyejaedon.coverscreenos.action.START"
        const val ACTION_STOP = "com.tyejaedon.coverscreenos.action.STOP"
        const val ACTION_HIDE_OVERLAY = "com.tyejaedon.coverscreenos.action.HIDE_OVERLAY"
        private const val EXTRA_LAUNCH_PACKAGE_NAME = "com.tyejaedon.coverscreenos.extra.LAUNCH_PACKAGE_NAME"

        fun requestOverlayReclaim(reason: String = "external") {
            activeServiceRef?.get()?.requestOverlayReclaimInternal(reason)
        }

        fun requestIncomingCallPassthrough(packageName: String) {
            activeServiceRef?.get()?.requestIncomingCallPassthroughInternal(packageName)
        }

        fun createStartIntent(context: Context): Intent = Intent(context, ForegroundService::class.java).apply {
            action = ACTION_START
        }

        fun createStopIntent(context: Context): Intent = Intent(context, ForegroundService::class.java).apply {
            action = ACTION_STOP
        }

        fun createHideOverlayIntent(context: Context, packageName: String?): Intent = Intent(context, ForegroundService::class.java).apply {
            action = ACTION_HIDE_OVERLAY
            packageName?.trim()?.takeUnless { it.isEmpty() }?.let { putExtra(EXTRA_LAUNCH_PACKAGE_NAME, it) }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var overlayWindowController: OverlayWindowController
    private lateinit var launchCoordinator: CoverLaunchCoordinator
    private lateinit var coverDisplayHelper: CoverDisplayHelper
    private lateinit var displayManager: DisplayManager

    private var overlayRequested = false
    private var isDisplayListenerRegistered = false
    private var hasEnteredForeground = false

    private var pendingDisplayRetargetJob: Job? = null
    private var pendingCoverDetachJob: Job? = null
    private var suppressionResumePollerJob: Job? = null

    private var isOverlaySuppressedForAppLaunch = false
    private var launchSuppressedPackageName: String? = null
    private var launchSuppressedStartedElapsedMs = 0L
    private var resumeSignalStableCount = 0
    private var lastResumeSignalPackage: String? = null
    private var lastOverlayReclaimElapsedMs = 0L
    private var lastOverlayReclaimReason: String? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = onDisplayTopologyChanged("added", displayId)
        override fun onDisplayRemoved(displayId: Int) = onDisplayTopologyChanged("removed", displayId)
        override fun onDisplayChanged(displayId: Int) = onDisplayTopologyChanged("changed", displayId)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Used for maintaining persistent background services"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cover Screen OS Running")
            .setContentText("Monitoring system state in the background...")
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        activeServiceRef = WeakReference(this)
        createNotificationChannel()

        launchCoordinator = CoverLaunchCoordinator(
            onBeginLaunch = ::prepareForOverlayAppLaunch,
            onLaunchDispatched = ::onAppLaunchDispatchAcknowledged,
            onLaunchFailed = ::restoreOverlayAfterLaunchFailure,
            debugThrowOnThreadViolation = isDebuggableBuild()
        )
        val appRepository = PackageManagerAppScannerRepository(this)
        val launcherSettingsStore = LauncherSettingsStore(this)
        overlayWindowController = OverlayWindowController(
            context = this,
            launchCoordinator = launchCoordinator,
            appRepository = appRepository,
            launcherSettingsStore = launcherSettingsStore
        )
        coverDisplayHelper = CoverDisplayHelper(this)
        displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                teardownOverlayRuntime()
                stopForegroundIfStarted()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_HIDE_OVERLAY -> {
                if (!ensureForegroundSession()) {
                    teardownOverlayRuntime()
                    stopSelf()
                    return START_NOT_STICKY
                }

                val launchPackageName = intent.getStringExtra(EXTRA_LAUNCH_PACKAGE_NAME)?.trim()?.takeUnless { it.isEmpty() } ?: "<unspecified>"
                if (!prepareForOverlayAppLaunch(launchPackageName)) {
                    Log.w(LOG_TAG, "Overlay hide request rejected package=$launchPackageName")
                }
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                if (!ensureForegroundSession()) {
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (activeServiceRef?.get() === this) {
            activeServiceRef?.clear()
            activeServiceRef = null
        }
        serviceScope.cancel() // Instantly terminates all polling and delays
        teardownOverlayRuntime()
        stopForegroundIfStarted()
        super.onDestroy()
    }

    private fun requestOverlayReclaimInternal(reason: String) {
        if (!overlayRequested || !hasRuntimePrerequisites()) return

        val nowElapsedMs = SystemClock.elapsedRealtime()
        val normalizedReason = reason.trim().ifEmpty { "external" }

        if (normalizedReason == lastOverlayReclaimReason && (nowElapsedMs - lastOverlayReclaimElapsedMs) < OVERLAY_RECLAIM_MIN_INTERVAL_MS) {
            return
        }

        lastOverlayReclaimElapsedMs = nowElapsedMs
        lastOverlayReclaimReason = normalizedReason

        logDebug { "request reason=$normalizedReason overlayActive=$isOverlayActive display=${overlayWindowController.getActiveDisplayId()} suppressed=$isOverlaySuppressedForAppLaunch" }

        serviceScope.launch {
            if (isOverlaySuppressedForAppLaunch) {
                maybeResumeOverlayAfterAppLaunch(reason = "reclaim:$normalizedReason")
            } else {
                scheduleRetarget(reason = "foreground_reclaim:$normalizedReason", immediate = true)
            }
        }
    }

    private fun requestIncomingCallPassthroughInternal(packageName: String) {
        if (!hasRuntimePrerequisites()) return
        val normalizedPackageName = packageName.trim().ifEmpty { "incoming_call" }
        if (isOverlaySuppressedForAppLaunch && launchSuppressedPackageName == normalizedPackageName) return

        serviceScope.launch {
            prepareForOverlayAppLaunch(normalizedPackageName)
        }
    }

    private fun ensureForegroundSession(): Boolean {
        if (hasEnteredForeground) return true
        return runCatching {
            // Updated for Android 14+ Compatibility
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE // Or TYPE_DISPLAY depending on your manifest
            )
            hasEnteredForeground = true
            true
        }.getOrElse { error ->
            Log.w(LOG_TAG, "Unable to enter foreground mode: ${error.message}")
            false
        }
    }

    private inline fun logDebug(message: () -> String) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) Log.d(LOG_TAG, message())
    }

    private fun stopForegroundIfStarted() {
        if (!hasEnteredForeground) return
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        hasEnteredForeground = false
    }

    private fun onDisplayTopologyChanged(changeType: String, displayId: Int) {
        if (!overlayRequested || isOverlaySuppressedForAppLaunch) return
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
        val currentlyOnCover = overlayWindowController.isOverlayAttached() && activeId != null && activeId != Display.DEFAULT_DISPLAY

        if (currentlyOnCover) {
            overlayWindowController.suppressOverlayForLaunch()
            isOverlayActive = false
            logDebug { "overlay reason=$reason holding_hidden_on_cover activeId=$activeId displays=${coverDisplayHelper.describeDisplays()}" }
            return
        }

        cancelPendingCoverDetach()
        if (overlayWindowController.isOverlayAttached()) overlayWindowController.removeOverlay()
        isOverlayActive = false
        logDebug { "overlay reason=$reason no_cover_available attached=false displays=${coverDisplayHelper.describeDisplays()}" }
    }

    private fun attachOverlayToTarget(targetDisplay: Display, reason: String) {
        val targetId = targetDisplay.displayId
        val activeId = overlayWindowController.getActiveDisplayId()
        val shouldForceRetarget = overlayWindowController.isOverlayAttached() && activeId != targetId

        isOverlayActive = overlayWindowController.showOverlay(targetDisplay, shouldForceRetarget, coverDisplayHelper.isDeviceLocked)
        logDebug { "overlay reason=$reason targetId=$targetId activeId=${overlayWindowController.getActiveDisplayId()} forceRetarget=$shouldForceRetarget attached=$isOverlayActive displays=${coverDisplayHelper.describeDisplays()}" }
    }

    private fun scheduleRetarget(reason: String, immediate: Boolean) {
        pendingDisplayRetargetJob?.cancel()
        pendingDisplayRetargetJob = serviceScope.launch {
            if (!immediate) delay(DISPLAY_CHANGE_DEBOUNCE_MS)
            attachOrRetargetOverlay(reason)
        }
    }

    private fun scheduleCoverDetach(reason: String) {
        if (pendingCoverDetachJob?.isActive == true) return
        pendingCoverDetachJob = serviceScope.launch {
            delay(COVER_DETACH_GRACE_MS)
            if (!overlayRequested) return@launch
            if (!hasRuntimePrerequisites()) {
                stopServiceForMissingPrerequisites(reason = "$reason detach_grace_prerequisites_lost")
                return@launch
            }

            val coverDisplay = coverDisplayHelper.getCoverDisplay()
            if (coverDisplay != null) {
                attachOverlayToTarget(coverDisplay, "$reason grace_cancelled_cover_returned")
            } else {
                overlayWindowController.removeOverlay()
                isOverlayActive = false
                logDebug { "overlay reason=$reason grace_elapsed detached=true displays=${coverDisplayHelper.describeDisplays()}" }
            }
        }
    }

    private fun cancelPendingCoverDetach() {
        pendingCoverDetachJob?.cancel()
    }

    private fun clearPendingDisplayWork() {
        pendingDisplayRetargetJob?.cancel()
        cancelPendingCoverDetach()
    }

    private fun registerDisplayListenerIfNeeded() {
        if (isDisplayListenerRegistered) return
        displayManager.registerDisplayListener(displayListener, null) // Handled safely by DisplayManager internals
        isDisplayListenerRegistered = true
    }

    private fun unregisterDisplayListenerIfNeeded() {
        if (!isDisplayListenerRegistered) return
        displayManager.unregisterDisplayListener(displayListener)
        isDisplayListenerRegistered = false
    }

    private fun hasRuntimePrerequisites(): Boolean = ForegroundServiceHelper.hasRequiredOverlayPermissions(this)

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
        logDebug { "launch dispatch acknowledged package=$packageName" }
    }

    private fun restoreOverlayAfterLaunchFailure(packageName: String) {
        clearAppLaunchSuppression()
        scheduleRetarget(reason = "launch_app_failed_restore_overlay:$packageName", immediate = true)
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
        suppressionResumePollerJob?.cancel()
        suppressionResumePollerJob = serviceScope.launch {
            while (isActive && isOverlaySuppressedForAppLaunch) {
                maybeResumeOverlayAfterAppLaunch(reason = "poll")
                delay(APP_LAUNCH_RESUME_POLL_INTERVAL_MS)
            }
        }
    }

    private fun maybeResumeOverlayAfterAppLaunch(reason: String) {
        if (!isOverlaySuppressedForAppLaunch) return
        if (!overlayRequested) {
            clearAppLaunchSuppression()
            return
        }

        val suppressedForMs = SystemClock.elapsedRealtime() - launchSuppressedStartedElapsedMs
        if (suppressedForMs >= APP_LAUNCH_RESUME_MAX_SUPPRESSION_MS) {
            clearAppLaunchSuppression()
            scheduleRetarget(reason = "resume_after_app_launch_timeout:$reason", immediate = true)
            return
        }
        if (suppressedForMs < APP_LAUNCH_RESUME_MIN_SUPPRESSION_MS) return

        if (coverDisplayHelper.getDisplayLockStatus()) {
            clearAppLaunchSuppression()
            scheduleRetarget(reason = "resume_after_app_launch_locked:$reason", immediate = true)
            return
        }

        val foregroundPackage = resolveForegroundPackageForResume() ?: return
        if (reason == "poll" && isTransientSystemUiPackage(foregroundPackage)) return
        if (!reason.startsWith("reclaim:") && !hasStableResumeSignal(foregroundPackage)) return

        clearAppLaunchSuppression()
        scheduleRetarget(reason = "resume_after_app_launch:$foregroundPackage:$reason", immediate = true)
    }

    private fun resolveForegroundPackageForResume(): String? {
        val foregroundPackage = CoverAccessibilityService.currentForegroundPackage()?.trim()?.takeUnless { it.isEmpty() } ?: return null
        if (CoverAccessibilityService.currentForegroundPackageEventAgeMs() > APP_LAUNCH_RESUME_STALE_EVENT_MAX_MS) return null
        if (!shouldResumeOverlayForPackage(foregroundPackage)) return null
        return foregroundPackage
    }

    private fun shouldResumeOverlayForPackage(packageName: String): Boolean {
        val launchedPackage = launchSuppressedPackageName?.trim()?.takeUnless { it.isEmpty() }
        if (launchedPackage != null && packageName == launchedPackage) return false
        if (packageName == this.packageName) return true
        return OVERLAY_RESUME_PACKAGE_PREFIXES.any { packageName.startsWith(it) }
    }

    private fun isTransientSystemUiPackage(packageName: String): Boolean = TRANSIENT_SYSTEM_UI_PREFIXES.any { packageName.startsWith(it) }

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
        suppressionResumePollerJob?.cancel()
        suppressionResumePollerJob = null
        isOverlaySuppressedForAppLaunch = false
        launchSuppressedPackageName = null
        launchSuppressedStartedElapsedMs = 0L
        resetResumeSignalStability()
    }

    private fun stopServiceForMissingPrerequisites(reason: String) {
        Log.w(LOG_TAG, "Stopping service because runtime prerequisites are missing. reason=$reason")
        teardownOverlayRuntime()
        stopForegroundIfStarted()
        stopSelf()
    }

    private fun isDebuggableBuild(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}