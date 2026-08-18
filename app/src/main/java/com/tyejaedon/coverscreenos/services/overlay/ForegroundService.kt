package com.tyejaedon.coverscreenos.services.overlay

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
import com.tyejaedon.coverscreenos.services.CallPackageMatchers
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
        private const val APP_LAUNCH_RESUME_POLL_INTERVAL_MS = 60L
        private const val APP_LAUNCH_RESUME_MIN_SUPPRESSION_MS = 120L
        private const val APP_LAUNCH_RESUME_STALE_EVENT_MAX_MS = 2_500L
        private const val APP_LAUNCH_RESUME_STABLE_SIGNAL_COUNT = 1
        private const val APP_LAUNCH_RESUME_MAX_SUPPRESSION_MS = 45_000L
        private const val APP_LAUNCH_RESUME_MIN_LAUNCHER_MS = 300L
        private const val TRANSIENT_SYSTEM_UI_RESUME_GRACE_MS = 120L
        private const val TRANSIENT_EXIT_FAILSAFE_MIN_SUPPRESSION_MS = 700L
        private const val TRANSIENT_EXIT_PATTERN_WINDOW_MS = 1_800L
        private const val INCOMING_CALL_SUPPRESSION_MAX_MS = 7_200_000L
        private const val INCOMING_CALL_RECLAIM_BLOCK_GRACE_MS = 5_000L
        private const val OVERLAY_RECLAIM_MIN_INTERVAL_MS = 80L
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

    private val suppressionState = OverlaySuppressionState()
    private val reclaimPolicy = OverlayReclaimPolicy(
        incomingCallSuppressionMaxMs = INCOMING_CALL_SUPPRESSION_MAX_MS,
        incomingCallReclaimBlockGraceMs = INCOMING_CALL_RECLAIM_BLOCK_GRACE_MS
    )
    private val transientSignalPolicy = OverlayTransientSignalPolicy(
        transientSystemUiPrefixes = TRANSIENT_SYSTEM_UI_PREFIXES,
        transientSystemUiResumeSafePrefixes = TRANSIENT_SYSTEM_UI_RESUME_SAFE_PREFIXES,
        overlayResumePackagePrefixes = OVERLAY_RESUME_PACKAGE_PREFIXES,
        launcherPackagePrefixes = LAUNCHER_PACKAGE_PREFIXES,
        transientSystemUiResumeGraceMs = TRANSIENT_SYSTEM_UI_RESUME_GRACE_MS,
        transientExitFailsafeMinSuppressionMs = TRANSIENT_EXIT_FAILSAFE_MIN_SUPPRESSION_MS,
        transientExitPatternWindowMs = TRANSIENT_EXIT_PATTERN_WINDOW_MS
    )

    private var overlayRequested = false
    private var isDisplayListenerRegistered = false
    private var hasEnteredForeground = false

    private var pendingDisplayRetargetJob: Job? = null
    private var suppressionResumePollerJob: Job? = null

    private var isOverlaySuppressedForAppLaunch: Boolean
        get() = suppressionState.isOverlaySuppressedForAppLaunch
        set(value) {
            suppressionState.isOverlaySuppressedForAppLaunch = value
        }

    private var suppressionReason: OverlaySuppressionReason
        get() = suppressionState.suppressionReason
        set(value) {
            suppressionState.suppressionReason = value
        }

    private var launchSuppressedPackageName: String?
        get() = suppressionState.launchSuppressedPackageName
        set(value) {
            suppressionState.launchSuppressedPackageName = value
        }

    private var launchSuppressedStartedElapsedMs: Long
        get() = suppressionState.launchSuppressedStartedElapsedMs
        set(value) {
            suppressionState.launchSuppressedStartedElapsedMs = value
        }

    private var suppressionSessionId: Long
        get() = suppressionState.suppressionSessionId
        set(value) {
            suppressionState.suppressionSessionId = value
        }

    private var completedSuppressionSessionId: Long?
        get() = suppressionState.completedSuppressionSessionId
        set(value) {
            suppressionState.completedSuppressionSessionId = value
        }

    private var resumeSignalStableCount: Int
        get() = suppressionState.resumeSignalStableCount
        set(value) {
            suppressionState.resumeSignalStableCount = value
        }

    private var lastResumeSignalPackage: String?
        get() = suppressionState.lastResumeSignalPackage
        set(value) {
            suppressionState.lastResumeSignalPackage = value
        }

    private var lastOverlayReclaimElapsedMs: Long
        get() = suppressionState.lastOverlayReclaimElapsedMs
        set(value) {
            suppressionState.lastOverlayReclaimElapsedMs = value
        }

    private var lastOverlayReclaimReason: String?
        get() = suppressionState.lastOverlayReclaimReason
        set(value) {
            suppressionState.lastOverlayReclaimReason = value
        }

    private var incomingCallPassthroughPackage: String?
        get() = suppressionState.incomingCallPassthroughPackage
        set(value) {
            suppressionState.incomingCallPassthroughPackage = value
        }

    private var incomingCallPassthroughStartedElapsedMs: Long
        get() = suppressionState.incomingCallPassthroughStartedElapsedMs
        set(value) {
            suppressionState.incomingCallPassthroughStartedElapsedMs = value
        }

    private var incomingCallLastSignalElapsedMs: Long
        get() = suppressionState.incomingCallLastSignalElapsedMs
        set(value) {
            suppressionState.incomingCallLastSignalElapsedMs = value
        }

    private var callNotificationActive: Boolean
        get() = suppressionState.callNotificationActive
        set(value) {
            suppressionState.callNotificationActive = value
        }

    private var callNotificationPackage: String?
        get() = suppressionState.callNotificationPackage
        set(value) {
            suppressionState.callNotificationPackage = value
        }

    private var callNotificationLastSignalElapsedMs: Long
        get() = suppressionState.callNotificationLastSignalElapsedMs
        set(value) {
            suppressionState.callNotificationLastSignalElapsedMs = value
        }
    private var pendingRetargetReason: String? = null
    // Transient foreground state is managed by OverlayTransientSignalPolicy.

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

        if (suppressionState.shouldSkipReclaimRequest(normalizedReason, nowElapsedMs, OVERLAY_RECLAIM_MIN_INTERVAL_MS)) {
            return
        }

        suppressionState.markReclaimRequested(normalizedReason, nowElapsedMs)

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

        if (isOverlaySuppressedForAppLaunch && suppressionReason == OverlaySuppressionReason.INCOMING_CALL) {
            return
        }

        serviceScope.launch {
            suppressOverlayForReason(packageName = normalizedPackageName, reason = OverlaySuppressionReason.INCOMING_CALL)
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

            if (!isOverlaySuppressedForAppLaunch || suppressionReason != OverlaySuppressionReason.INCOMING_CALL) {
                serviceScope.launch {
                    suppressOverlayForReason(
                        packageName = normalizedPackageName ?: "incoming_call_notification",
                        reason = OverlaySuppressionReason.INCOMING_CALL
                    )
                }
            }
            return
        }

        if (isOverlaySuppressedForAppLaunch && suppressionReason == OverlaySuppressionReason.INCOMING_CALL) {
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
        suppressOverlayForReason(packageName = packageName, reason = OverlaySuppressionReason.APP_LAUNCH)
        return true
    }

    private fun onAppLaunchDispatchAcknowledged(packageName: String) {
        logDebug { "launch dispatch acknowledged package=$packageName" }
    }

    private fun restoreOverlayAfterLaunchFailure(packageName: String) {
        completeSuppressionAndRetargetOnce(reason = "launch_app_failed_restore_overlay:$packageName")
    }

    private fun suppressOverlayForReason(packageName: String, reason: OverlaySuppressionReason) {
        suppressionState.markSuppressionStarted(
            packageName = packageName,
            reason = reason,
            nowElapsedMs = SystemClock.elapsedRealtime()
        )

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
            OverlaySuppressionReason.APP_LAUNCH -> APP_LAUNCH_RESUME_MAX_SUPPRESSION_MS
            OverlaySuppressionReason.INCOMING_CALL -> INCOMING_CALL_SUPPRESSION_MAX_MS
            OverlaySuppressionReason.NONE -> APP_LAUNCH_RESUME_MAX_SUPPRESSION_MS
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

        if (shouldKeepOverlaySuppressedForIncomingCall()) {
            logResumeDecision(
                reason = reason,
                decision = "hold_incoming_call",
                detail = "incomingCallPackage=$incomingCallPassthroughPackage callNotificationActive=$callNotificationActive"
            )
            return
        }

        if (coverDisplayHelper.getDisplayLockStatus()) {
            logResumeDecision(reason = reason, decision = "resume_locked")
            completeSuppressionAndRetargetOnce(reason = "resume_after_app_launch_locked:$reason")
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
            suppressionReason == OverlaySuppressionReason.APP_LAUNCH &&
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
        return transientSignalPolicy.shouldResumeOverlayForPackage(
            packageName = packageName,
            launchedPackage = launchedPackage,
            appPackageName = this.packageName
        )
    }

    private fun isTransientSystemUiPackage(packageName: String): Boolean {
        return transientSignalPolicy.isTransientSystemUiPackage(packageName)
    }

    private fun isTransientForegroundSafeForResume(packageName: String): Boolean {
        return transientSignalPolicy.isTransientForegroundSafeForResume(packageName)
    }

    private fun isLauncherPackageForResume(packageName: String): Boolean {
        return transientSignalPolicy.isLauncherPackageForResume(packageName)
    }

    private fun isIncomingCallPackage(packageName: String): Boolean = CallPackageMatchers.isIncomingCallPackage(packageName)

    private fun resolveRawForegroundPackageForCallGuard(): String? {
        val foregroundPackage = CoverAccessibilityService.currentForegroundPackage()?.trim()?.takeUnless { it.isEmpty() } ?: return null
        if (CoverAccessibilityService.currentForegroundPackageEventAgeMs() > APP_LAUNCH_RESUME_STALE_EVENT_MAX_MS) return null
        return foregroundPackage
    }

    private fun trackIncomingCallPassthrough(packageName: String) {
        reclaimPolicy.trackIncomingCallPassthrough(
            state = suppressionState,
            packageName = packageName,
            nowElapsedMs = SystemClock.elapsedRealtime()
        )
    }

    private fun clearIncomingCallPassthrough(reason: String) {
        reclaimPolicy.clearIncomingCallPassthrough(
            state = suppressionState,
            reason = reason,
            onDebugLog = { message ->
                if (Log.isLoggable(OVERLAY_RECLAIM_LOG_TAG, Log.DEBUG)) {
                    Log.d(OVERLAY_RECLAIM_LOG_TAG, message)
                }
            }
        )
    }

    private fun shouldKeepOverlaySuppressedForIncomingCall(): Boolean {
        return reclaimPolicy.shouldKeepOverlaySuppressedForIncomingCall(
            state = suppressionState,
            nowElapsedMs = SystemClock.elapsedRealtime(),
            resolveRawForegroundPackage = ::resolveRawForegroundPackageForCallGuard,
            isIncomingCallPackage = ::isIncomingCallPackage,
            onClearPassthrough = ::clearIncomingCallPassthrough
        )
    }

    private fun shouldBlockReclaimForIncomingCall(nowElapsedMs: Long): Boolean {
        return reclaimPolicy.shouldBlockReclaimForIncomingCall(
            state = suppressionState,
            nowElapsedMs = nowElapsedMs,
            resolveRawForegroundPackage = ::resolveRawForegroundPackageForCallGuard,
            isIncomingCallPackage = ::isIncomingCallPackage
        )
    }

    private fun hasStableResumeSignal(packageName: String): Boolean {
        return suppressionState.hasStableResumeSignal(packageName, APP_LAUNCH_RESUME_STABLE_SIGNAL_COUNT)
    }

    private fun resetResumeSignalStability() {
        suppressionState.resetResumeSignalStability()
    }

    private fun clearAppLaunchSuppression() {
        suppressionResumePollerJob?.cancel()
        suppressionResumePollerJob = null
        suppressionState.clearSuppressionState()
        resetTransientForegroundTracking()
        clearIncomingCallPassthrough(reason = "suppression_cleared")
    }

    private fun shouldResumeFromTransientForeground(packageName: String): Boolean {
        return transientSignalPolicy.shouldResumeFromTransientForeground(packageName)
    }

    private fun resetTransientForegroundTracking() {
        transientSignalPolicy.resetTransientForegroundTracking()
    }

    private fun trackTransientExitPattern(packageName: String) {
        transientSignalPolicy.trackTransientExitPattern(packageName)
    }

    private fun shouldResumeFromTransientExitPattern(suppressedForMs: Long): Boolean {
        return transientSignalPolicy.shouldResumeFromTransientExitPattern(
            suppressionReason = suppressionReason,
            suppressedForMs = suppressedForMs
        )
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
