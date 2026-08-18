package com.tyejaedon.coverscreenos.services

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
import kotlin.time.Duration.Companion.milliseconds

class ForegroundService : Service() {
    companion object {
        private const val LOG_TAG = "CoverForegroundService"
        private const val DISPLAY_CHANGE_DEBOUNCE_MS = 450L
        private const val APP_LAUNCH_RESUME_POLL_INTERVAL_MS = 150L
        private const val APP_LAUNCH_RESUME_MIN_SUPPRESSION_MS = 450L
        private const val APP_LAUNCH_RESUME_STALE_EVENT_MAX_MS = 2_500L
        private const val APP_LAUNCH_RESUME_STABLE_SIGNAL_COUNT = 2
        private const val APP_LAUNCH_RESUME_MAX_SUPPRESSION_MS = 45_000L
        private const val APP_LAUNCH_RESUME_MIN_LAUNCHER_MS = 1_500L
        private const val TRANSIENT_SYSTEM_UI_RESUME_GRACE_MS = 700L
        private const val TRANSIENT_EXIT_FAILSAFE_MIN_SUPPRESSION_MS = 3_500L
        private const val TRANSIENT_EXIT_PATTERN_WINDOW_MS = 6_000L
        private const val INCOMING_CALL_SUPPRESSION_MAX_MS = 7_200_000L
        private const val INCOMING_CALL_RECLAIM_BLOCK_GRACE_MS = 5_000L
        private const val OVERLAY_RECLAIM_MIN_INTERVAL_MS = 500L
        private const val OVERLAY_RECLAIM_LOG_TAG = "CoverOverlayReclaim"

        private val TRANSIENT_SYSTEM_UI_PREFIXES = arrayOf(
            "com.android.systemui",
            "com.samsung.systemui",
            "com.samsung.android.app.aodservice"
        )

        private val TRANSIENT_SYSTEM_UI_RESUME_SAFE_PREFIXES = arrayOf(
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

        private val LAUNCHER_PACKAGE_PREFIXES = arrayOf(
            "com.sec.android.app.launcher",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher"
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

        fun updateCallNotificationState(isActive: Boolean, packageName: String?) {
            activeServiceRef?.get()?.updateCallNotificationStateInternal(isActive, packageName)
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

        fun isServiceRuntimeActive(): Boolean {
            return activeServiceRef?.get() != null
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
    private var suppressionResumePollerJob: Job? = null

    private var isOverlaySuppressedForAppLaunch = false
    private var suppressionReason: SuppressionReason = SuppressionReason.NONE
    private var launchSuppressedPackageName: String? = null
    private var launchSuppressedStartedElapsedMs = 0L
    private var suppressionSessionId = 0L
    private var completedSuppressionSessionId: Long? = null
    private var resumeSignalStableCount = 0
    private var lastResumeSignalPackage: String? = null
    private var lastOverlayReclaimElapsedMs = 0L
    private var lastOverlayReclaimReason: String? = null
    private var incomingCallPassthroughPackage: String? = null
    private var incomingCallPassthroughStartedElapsedMs: Long = 0L
    private var incomingCallLastSignalElapsedMs: Long = 0L
    private var callNotificationActive: Boolean = false
    private var callNotificationPackage: String? = null
    private var callNotificationLastSignalElapsedMs: Long = 0L
    private var pendingRetargetReason: String? = null
    private var transientForegroundPackage: String? = null
    private var transientForegroundSinceElapsedMs: Long = 0L
    private var transientSystemUiSeenElapsedMs: Long = 0L
    private var transientAodSeenElapsedMs: Long = 0L

    private enum class SuppressionReason {
        NONE,
        APP_LAUNCH,
        INCOMING_CALL
    }

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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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

        if (shouldBlockReclaimForIncomingCall(nowElapsedMs)) {
            if (Log.isLoggable(OVERLAY_RECLAIM_LOG_TAG, Log.DEBUG)) {
                Log.d(OVERLAY_RECLAIM_LOG_TAG, "reclaim_blocked_for_incoming_call reason=$normalizedReason activeCallPackage=$incomingCallPassthroughPackage")
            }
            return
        }

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
        trackIncomingCallPassthrough(normalizedPackageName)

        if (isOverlaySuppressedForAppLaunch && suppressionReason == SuppressionReason.INCOMING_CALL) {
            return
        }

        serviceScope.launch {
            suppressOverlayForReason(packageName = normalizedPackageName, reason = SuppressionReason.INCOMING_CALL)
        }
    }

    private fun updateCallNotificationStateInternal(isActive: Boolean, packageName: String?) {
        val normalizedPackageName = packageName?.trim()?.takeUnless { it.isEmpty() }
        val nowElapsedMs = SystemClock.elapsedRealtime()

        callNotificationActive = isActive
        callNotificationPackage = if (isActive) normalizedPackageName else null

        if (isActive) {
            callNotificationLastSignalElapsedMs = nowElapsedMs
            normalizedPackageName?.let { trackIncomingCallPassthrough(it) }

            if (!isOverlaySuppressedForAppLaunch || suppressionReason != SuppressionReason.INCOMING_CALL) {
                serviceScope.launch {
                    suppressOverlayForReason(
                        packageName = normalizedPackageName ?: "incoming_call_notification",
                        reason = SuppressionReason.INCOMING_CALL
                    )
                }
            }
            return
        }

        if (isOverlaySuppressedForAppLaunch && suppressionReason == SuppressionReason.INCOMING_CALL) {
            serviceScope.launch {
                maybeResumeOverlayAfterAppLaunch(reason = "call_notification_ended")
            }
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
        if (pendingDisplayRetargetJob?.isActive == true && pendingRetargetReason == reason) {
            return
        }

        pendingRetargetReason = reason
        pendingDisplayRetargetJob?.cancel()
        pendingDisplayRetargetJob = serviceScope.launch {
            if (!immediate) delay(DISPLAY_CHANGE_DEBOUNCE_MS.milliseconds)
            attachOrRetargetOverlay(reason)
        }.also { job ->
            job.invokeOnCompletion {
                if (pendingRetargetReason == reason) {
                    pendingRetargetReason = null
                }
            }
        }
    }

    private fun clearPendingDisplayWork() {
        pendingDisplayRetargetJob?.cancel()
        pendingRetargetReason = null
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
        suppressOverlayForReason(packageName = packageName, reason = SuppressionReason.APP_LAUNCH)
        return true
    }

    private fun onAppLaunchDispatchAcknowledged(packageName: String) {
        logDebug { "launch dispatch acknowledged package=$packageName" }
    }

    private fun restoreOverlayAfterLaunchFailure(packageName: String) {
        completeSuppressionAndRetargetOnce(reason = "launch_app_failed_restore_overlay:$packageName")
    }

    private fun suppressOverlayForReason(packageName: String, reason: SuppressionReason) {
        suppressionSessionId += 1L
        completedSuppressionSessionId = null
        isOverlaySuppressedForAppLaunch = true
        suppressionReason = reason
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
                delay(APP_LAUNCH_RESUME_POLL_INTERVAL_MS.milliseconds)
            }
        }
    }

    private fun maybeResumeOverlayAfterAppLaunch(reason: String) {
        if (!isOverlaySuppressedForAppLaunch) {
            logResumeDecision(reason = reason, decision = "skip_not_suppressed")
            return
        }
        if (!overlayRequested) {
            logResumeDecision(reason = reason, decision = "clear_no_overlay_request")
            clearAppLaunchSuppression()
            return
        }

        val suppressedForMs = SystemClock.elapsedRealtime() - launchSuppressedStartedElapsedMs
        val maxSuppressionMs = when (suppressionReason) {
            SuppressionReason.APP_LAUNCH -> APP_LAUNCH_RESUME_MAX_SUPPRESSION_MS
            SuppressionReason.INCOMING_CALL -> INCOMING_CALL_SUPPRESSION_MAX_MS
            SuppressionReason.NONE -> APP_LAUNCH_RESUME_MAX_SUPPRESSION_MS
        }
        if (suppressedForMs >= maxSuppressionMs) {
            logResumeDecision(
                reason = reason,
                decision = "resume_timeout",
                detail = "suppressedForMs=$suppressedForMs maxSuppressionMs=$maxSuppressionMs"
            )
            completeSuppressionAndRetargetOnce(
                reason = "resume_after_suppression_timeout:${suppressionReason.name.lowercase()}:$reason"
            )
            return
        }
        if (suppressedForMs < APP_LAUNCH_RESUME_MIN_SUPPRESSION_MS) {
            logResumeDecision(
                reason = reason,
                decision = "wait_min_suppression",
                detail = "suppressedForMs=$suppressedForMs requiredMs=$APP_LAUNCH_RESUME_MIN_SUPPRESSION_MS"
            )
            return
        }

        if (coverDisplayHelper.getDisplayLockStatus()) {
            logResumeDecision(reason = reason, decision = "resume_locked")
            completeSuppressionAndRetargetOnce(reason = "resume_after_app_launch_locked:$reason")
            return
        }

        if (shouldKeepOverlaySuppressedForIncomingCall()) {
            logResumeDecision(
                reason = reason,
                decision = "hold_incoming_call",
                detail = "incomingCallPackage=$incomingCallPassthroughPackage callNotificationActive=$callNotificationActive"
            )
            return
        }

        val foregroundPackage = resolveForegroundPackageForResume()
        if (foregroundPackage == null) {
            logResumeDecision(reason = reason, decision = "wait_no_eligible_foreground")
            return
        }
        var hasTransientResumeSignal = false
        if (reason == "poll" && isTransientSystemUiPackage(foregroundPackage)) {
            trackTransientExitPattern(foregroundPackage)

            if (shouldResumeFromTransientExitPattern(suppressedForMs)) {
                logResumeDecision(
                    reason = reason,
                    decision = "resume_transient_exit_pattern",
                    detail = "foreground=$foregroundPackage minSuppressionMs=$TRANSIENT_EXIT_FAILSAFE_MIN_SUPPRESSION_MS"
                )
                hasTransientResumeSignal = true
            } else {
            if (!isTransientForegroundSafeForResume(foregroundPackage)) {
                logResumeDecision(
                    reason = reason,
                    decision = "block_transient_resume_package",
                    detail = "foreground=$foregroundPackage"
                )
                return
            }
            if (!shouldResumeFromTransientForeground(foregroundPackage)) {
                logResumeDecision(
                    reason = reason,
                    decision = "skip_transient_poll",
                    detail = "foreground=$foregroundPackage"
                )
                return
            }
            logResumeDecision(
                reason = reason,
                decision = "resume_after_transient_grace",
                detail = "foreground=$foregroundPackage graceMs=$TRANSIENT_SYSTEM_UI_RESUME_GRACE_MS"
            )
            hasTransientResumeSignal = true
            }
        } else {
            resetTransientForegroundTracking()
        }
        if (!reason.startsWith("reclaim:") && !hasTransientResumeSignal && !hasStableResumeSignal(foregroundPackage)) {
            logResumeDecision(
                reason = reason,
                decision = "wait_stability",
                detail = "foreground=$foregroundPackage stableCount=$resumeSignalStableCount required=$APP_LAUNCH_RESUME_STABLE_SIGNAL_COUNT"
            )
            return
        }

        if (!reason.startsWith("reclaim:") &&
            suppressionReason == SuppressionReason.APP_LAUNCH &&
            isLauncherPackageForResume(foregroundPackage) &&
            suppressedForMs < APP_LAUNCH_RESUME_MIN_LAUNCHER_MS
        ) {
            logResumeDecision(
                reason = reason,
                decision = "wait_launcher_guard",
                detail = "foreground=$foregroundPackage suppressedForMs=$suppressedForMs requiredMs=$APP_LAUNCH_RESUME_MIN_LAUNCHER_MS"
            )
            return
        }

        logResumeDecision(
            reason = reason,
            decision = "resume_now",
            detail = "foreground=$foregroundPackage suppressedForMs=$suppressedForMs"
        )

        completeSuppressionAndRetargetOnce(reason = "resume_after_app_launch:$foregroundPackage:$reason")
    }

    private fun completeSuppressionAndRetargetOnce(reason: String) {
        if (!isOverlaySuppressedForAppLaunch) return

        val activeSessionId = suppressionSessionId
        if (completedSuppressionSessionId == activeSessionId) {
            if (Log.isLoggable(OVERLAY_RECLAIM_LOG_TAG, Log.DEBUG)) {
                Log.d(OVERLAY_RECLAIM_LOG_TAG, "suppression_completion_duplicate_ignored session=$activeSessionId reason=$reason")
            }
            return
        }

        completedSuppressionSessionId = activeSessionId
        clearAppLaunchSuppression()
        scheduleRetarget(reason = reason, immediate = true)
    }

    private fun resolveForegroundPackageForResume(): String? {
        val foregroundPackage = CoverAccessibilityService.currentForegroundPackage()?.trim()?.takeUnless { it.isEmpty() }
            ?: run {
                logResumeDecision(reason = "resolve", decision = "no_foreground_package")
                return null
            }
        val eventAgeMs = CoverAccessibilityService.currentForegroundPackageEventAgeMs()
        if (eventAgeMs > APP_LAUNCH_RESUME_STALE_EVENT_MAX_MS) {
            if (isTransientSystemUiPackage(foregroundPackage) && isTransientForegroundSafeForResume(foregroundPackage)) {
                logResumeDecision(
                    reason = "resolve",
                    decision = "stale_transient_allowed",
                    detail = "foreground=$foregroundPackage eventAgeMs=$eventAgeMs"
                )
                return foregroundPackage
            }
            logResumeDecision(
                reason = "resolve",
                decision = "stale_foreground_event",
                detail = "foreground=$foregroundPackage eventAgeMs=$eventAgeMs maxAgeMs=$APP_LAUNCH_RESUME_STALE_EVENT_MAX_MS"
            )
            return null
        }
        if (!shouldResumeOverlayForPackage(foregroundPackage)) {
            logResumeDecision(
                reason = "resolve",
                decision = "foreground_not_eligible",
                detail = "foreground=$foregroundPackage launchedPackage=$launchSuppressedPackageName"
            )
            return null
        }
        return foregroundPackage
    }

    private fun shouldResumeOverlayForPackage(packageName: String): Boolean {
        val launchedPackage = launchSuppressedPackageName?.trim()?.takeUnless { it.isEmpty() }
        if (launchedPackage != null && packageName == launchedPackage) return false
        if (packageName == this.packageName) return true
        return OVERLAY_RESUME_PACKAGE_PREFIXES.any { packageName.startsWith(it) }
    }

    private fun isTransientSystemUiPackage(packageName: String): Boolean = TRANSIENT_SYSTEM_UI_PREFIXES.any { packageName.startsWith(it) }

    private fun isTransientForegroundSafeForResume(packageName: String): Boolean {
        return TRANSIENT_SYSTEM_UI_RESUME_SAFE_PREFIXES.any { packageName.startsWith(it) }
    }

    private fun isLauncherPackageForResume(packageName: String): Boolean = LAUNCHER_PACKAGE_PREFIXES.any { packageName.startsWith(it) }

    private fun isIncomingCallPackage(packageName: String): Boolean = CallPackageMatchers.isIncomingCallPackage(packageName)

    private fun resolveRawForegroundPackageForCallGuard(): String? {
        val foregroundPackage = CoverAccessibilityService.currentForegroundPackage()?.trim()?.takeUnless { it.isEmpty() } ?: return null
        if (CoverAccessibilityService.currentForegroundPackageEventAgeMs() > APP_LAUNCH_RESUME_STALE_EVENT_MAX_MS) return null
        return foregroundPackage
    }

    private fun trackIncomingCallPassthrough(packageName: String) {
        val nowElapsedMs = SystemClock.elapsedRealtime()
        if (incomingCallPassthroughPackage == null) {
            incomingCallPassthroughStartedElapsedMs = nowElapsedMs
        }
        incomingCallPassthroughPackage = packageName
        incomingCallLastSignalElapsedMs = nowElapsedMs
    }

    private fun clearIncomingCallPassthrough(reason: String) {
        if (incomingCallPassthroughPackage != null && Log.isLoggable(OVERLAY_RECLAIM_LOG_TAG, Log.DEBUG)) {
            Log.d(OVERLAY_RECLAIM_LOG_TAG, "incoming_call_passthrough_cleared reason=$reason package=$incomingCallPassthroughPackage")
        }
        incomingCallPassthroughPackage = null
        incomingCallPassthroughStartedElapsedMs = 0L
        incomingCallLastSignalElapsedMs = 0L
        callNotificationActive = false
        callNotificationPackage = null
        callNotificationLastSignalElapsedMs = 0L
    }

    private fun shouldKeepOverlaySuppressedForIncomingCall(): Boolean {
        if (incomingCallPassthroughPackage == null) return false
        val nowElapsedMs = SystemClock.elapsedRealtime()

        if (incomingCallPassthroughStartedElapsedMs > 0L &&
            (nowElapsedMs - incomingCallPassthroughStartedElapsedMs) > INCOMING_CALL_SUPPRESSION_MAX_MS
        ) {
            clearIncomingCallPassthrough(reason = "max_suppression_elapsed")
            return false
        }

        val foregroundPackage = resolveRawForegroundPackageForCallGuard()
        if (foregroundPackage != null && isIncomingCallPackage(foregroundPackage)) {
            incomingCallPassthroughPackage = foregroundPackage
            incomingCallLastSignalElapsedMs = nowElapsedMs
            return true
        }

        if (callNotificationActive) {
            callNotificationLastSignalElapsedMs = nowElapsedMs
            callNotificationPackage?.let { incomingCallPassthroughPackage = it }
            return true
        }

        if ((nowElapsedMs - incomingCallLastSignalElapsedMs) <= INCOMING_CALL_RECLAIM_BLOCK_GRACE_MS) {
            return true
        }

        if ((nowElapsedMs - callNotificationLastSignalElapsedMs) <= INCOMING_CALL_RECLAIM_BLOCK_GRACE_MS) {
            return true
        }

        clearIncomingCallPassthrough(reason = "foreground_exited_call_surface")
        return false
    }

    private fun shouldBlockReclaimForIncomingCall(nowElapsedMs: Long): Boolean {
        if (incomingCallPassthroughPackage == null) return false

        val foregroundPackage = resolveRawForegroundPackageForCallGuard()
        if (foregroundPackage != null && isIncomingCallPackage(foregroundPackage)) {
            incomingCallPassthroughPackage = foregroundPackage
            incomingCallLastSignalElapsedMs = nowElapsedMs
            return true
        }

        if (callNotificationActive) return true

        return (nowElapsedMs - incomingCallLastSignalElapsedMs) <= INCOMING_CALL_RECLAIM_BLOCK_GRACE_MS ||
            (nowElapsedMs - callNotificationLastSignalElapsedMs) <= INCOMING_CALL_RECLAIM_BLOCK_GRACE_MS
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
        suppressionResumePollerJob?.cancel()
        suppressionResumePollerJob = null
        isOverlaySuppressedForAppLaunch = false
        suppressionReason = SuppressionReason.NONE
        launchSuppressedPackageName = null
        launchSuppressedStartedElapsedMs = 0L
        resetTransientForegroundTracking()
        resetResumeSignalStability()
        clearIncomingCallPassthrough(reason = "suppression_cleared")
    }

    private fun shouldResumeFromTransientForeground(packageName: String): Boolean {
        val nowElapsedMs = SystemClock.elapsedRealtime()
        if (transientForegroundPackage != packageName) {
            transientForegroundPackage = packageName
            transientForegroundSinceElapsedMs = nowElapsedMs
            return false
        }

        val seenForMs = (nowElapsedMs - transientForegroundSinceElapsedMs).coerceAtLeast(0L)
        return seenForMs >= TRANSIENT_SYSTEM_UI_RESUME_GRACE_MS
    }

    private fun resetTransientForegroundTracking() {
        transientForegroundPackage = null
        transientForegroundSinceElapsedMs = 0L
        transientSystemUiSeenElapsedMs = 0L
        transientAodSeenElapsedMs = 0L
    }

    private fun trackTransientExitPattern(packageName: String) {
        val nowElapsedMs = SystemClock.elapsedRealtime()
        when {
            packageName.startsWith("com.android.systemui") -> transientSystemUiSeenElapsedMs = nowElapsedMs
            packageName.startsWith("com.samsung.android.app.aodservice") -> transientAodSeenElapsedMs = nowElapsedMs
        }
    }

    private fun shouldResumeFromTransientExitPattern(suppressedForMs: Long): Boolean {
        if (suppressionReason != SuppressionReason.APP_LAUNCH) return false
        if (suppressedForMs < TRANSIENT_EXIT_FAILSAFE_MIN_SUPPRESSION_MS) return false
        if (transientSystemUiSeenElapsedMs <= 0L || transientAodSeenElapsedMs <= 0L) return false

        val nowElapsedMs = SystemClock.elapsedRealtime()
        val systemUiAgeMs = (nowElapsedMs - transientSystemUiSeenElapsedMs).coerceAtLeast(0L)
        val aodAgeMs = (nowElapsedMs - transientAodSeenElapsedMs).coerceAtLeast(0L)
        return systemUiAgeMs <= TRANSIENT_EXIT_PATTERN_WINDOW_MS &&
            aodAgeMs <= TRANSIENT_EXIT_PATTERN_WINDOW_MS
    }

    private fun logResumeDecision(reason: String, decision: String, detail: String? = null) {
        if (!Log.isLoggable(OVERLAY_RECLAIM_LOG_TAG, Log.DEBUG)) return

        val suppressedForMs = if (launchSuppressedStartedElapsedMs > 0L) {
            (SystemClock.elapsedRealtime() - launchSuppressedStartedElapsedMs).coerceAtLeast(0L)
        } else {
            -1L
        }
        val detailSuffix = detail?.let { " $it" } ?: ""
        Log.d(
            OVERLAY_RECLAIM_LOG_TAG,
            "resume decision=$decision reason=$reason suppressionReason=${suppressionReason.name} suppressed=$isOverlaySuppressedForAppLaunch suppressedForMs=$suppressedForMs overlayRequested=$overlayRequested launchedPackage=$launchSuppressedPackageName incomingCallPackage=$incomingCallPassthroughPackage callNotificationActive=$callNotificationActive$detailSuffix"
        )
    }

    private fun stopServiceForMissingPrerequisites(reason: String) {
        Log.w(LOG_TAG, "Stopping service because runtime prerequisites are missing. reason=$reason")
        teardownOverlayRuntime()
        stopForegroundIfStarted()
        stopSelf()
    }

    private fun isDebuggableBuild(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}