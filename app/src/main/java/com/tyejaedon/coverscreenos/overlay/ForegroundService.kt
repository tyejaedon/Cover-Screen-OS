package com.tyejaedon.coverscreenos.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.helpers.CoverDisplayHelper
import com.tyejaedon.coverscreenos.helpers.ForegroundServiceHelper
import com.tyejaedon.coverscreenos.overlay.input.CoverInputAccessibilityService
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import com.tyejaedon.coverscreenos.notifications.CallPackageMatchers
import com.tyejaedon.coverscreenos.notifications.CoverNotificationListenerService
import com.tyejaedon.coverscreenos.ui.controllers.CoverAppLauncher
import com.tyejaedon.coverscreenos.ui.controllers.UnlockBridgeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds

/** Timestamped runtime milestone recorded in [RuntimeSnapshot.events] for diagnostics. */
data class OverlayEvent(val description: String, val elapsedRealtimeMs: Long)

/**
 * Immutable snapshot of [ForegroundService] lifecycle and overlay state exposed via
 * [ForegroundService.runtime] for UI status displays and diagnostics.
 */
data class RuntimeSnapshot(
    val serviceActive: Boolean = false,
    val launcherHostActive: Boolean = false,
    val overlayActive: Boolean = false,
    val startedElapsedRealtimeMs: Long? = null,
    val events: List<OverlayEvent> = emptyList()
)

/**
 * Persistent runtime engine for display monitoring, overlay policy, and app launches.
 *
 * This service supplies a [LauncherOverlayHost] and delegates window commands
 * through [OverlayWindowController]. [CoverAccessibilityService], not this
 * service, owns the launcher Compose window and its accessibility token.
 *
 * Coordinates [DisplayManager.DisplayListener] topology changes, [PowerManager] /
 * screen-on/off broadcasts, foldable posture transitions, and a multi-gate
 * suppression/reclaim state machine ([OverlaySuppressionState],
 * [OverlayReclaimPolicy], [OverlayTransientSignalPolicy], [OverlayAttachmentPolicy]).
 *
 * A [ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE] foreground notification supports
 * the started session. Normal start requests use [START_STICKY] for system-managed
 * recovery; explicit stop and rejected setup requests do not. Android and OEM
 * restrictions still apply.
 */
class ForegroundService : Service() {
    companion object {
        // Backing mutable flow holding the latest [RuntimeSnapshot] published to UI/diagnostics.
        private val mutableRuntime = MutableStateFlow(RuntimeSnapshot())
        /** Observable runtime snapshot flow for status screens and instrumentation. */
        val runtime = mutableRuntime.asStateFlow()

        private fun publishRuntime(event: String? = null) {
            val service = activeServiceRef?.get()
            val previous = mutableRuntime.value
            mutableRuntime.value = RuntimeSnapshot(
                serviceActive = service != null,
                launcherHostActive = CoverAccessibilityService.currentLauncherHost() != null,
                overlayActive = isOverlayActive,
                startedElapsedRealtimeMs = if (service == null) null
                    else previous.startedElapsedRealtimeMs ?: SystemClock.elapsedRealtime(),
                events = if (event == null) previous.events else
                    (listOf(OverlayEvent(event, SystemClock.elapsedRealtime())) + previous.events).take(20)
            )
        }

        /** Re-publishes [runtime] after [CoverAccessibilityService] attaches or releases a host. */
        fun onLauncherHostChanged() {
            publishRuntime()
        }

        private const val LOG_TAG = "CoverForegroundService"
        // Polling cadence while suppressed to detect when the user returns to the cover home screen.
        private const val APP_LAUNCH_RESUME_POLL_INTERVAL_MS = 250L
        // Minimum hold time after suppressing so launch transitions aren't immediately undone on frame 1.
        private const val APP_LAUNCH_RESUME_MIN_SUPPRESSION_MS = 120L
        // Maximum age before a cached accessibility foreground event is considered too stale to act on.
        private const val APP_LAUNCH_RESUME_STALE_EVENT_MAX_MS = 2_500L
        // Number of consecutive matching poll observations required before treating a return-to-home signal as stable.
        private const val APP_LAUNCH_RESUME_STABLE_SIGNAL_COUNT = 2
        // Effectively disable the "auto-reclaim after N seconds of no interaction"
        // behavior. Previously this was 45s, which meant that while the user was
        // watching a movie (no touch input for 45s) the cover launcher overlay
        // would slam itself back on top of the running app. The overlay now only
        // returns when the user actively navigates back to the launcher (detected
        // via the accessibility service reclaim signal) or when the display is
        // relocked, not on an idle timer.
        private const val APP_LAUNCH_RESUME_MAX_SUPPRESSION_MS = Long.MAX_VALUE
        // Minimum time a stock launcher must stay in foreground after launching an app before we reclaim.
        private const val APP_LAUNCH_RESUME_MIN_LAUNCHER_MS = 300L
        // Grace period a safe SystemUI/AOD screen must hold focus before triggering overlay resume.
        private const val TRANSIENT_SYSTEM_UI_RESUME_GRACE_MS = 120L
        // Minimum elapsed launch suppression time before SystemUI+AOD exit-pattern failsafe can fire.
        private const val TRANSIENT_EXIT_FAILSAFE_MIN_SUPPRESSION_MS = 700L
        // Max time window connecting a SystemUI event and an AOD event into a single "app exited" pattern.
        private const val TRANSIENT_EXIT_PATTERN_WINDOW_MS = 1_800L
        // Two-hour failsafe ceiling on incoming-call suppression so a missed end-call event can't hide the launcher forever.
        private const val INCOMING_CALL_SUPPRESSION_MAX_MS = 7_200_000L
        // Grace window after the last call signal to absorb brief screen transitions before reclaiming.
        private const val INCOMING_CALL_RECLAIM_BLOCK_GRACE_MS = 5_000L
        // Cooldown between identical reclaim requests so bursts of accessibility events don't spam retargets.
        private const val OVERLAY_RECLAIM_MIN_INTERVAL_MS = 80L
        private const val OVERLAY_RECLAIM_LOG_TAG = "CoverOverlayReclaim"
        private const val OVERLAY_TRANSITION_LOG_TAG = "CoverOverlayTransition"
        private const val OVERLAY_MARKER_HELD_HIDDEN = "marker=held_hidden"
        private const val OVERLAY_MARKER_REMOVED_STALE = "marker=removed_stale"

        // Packages that often flash briefly over apps (status/shade/AOD) without meaning the user exited.
        private val TRANSIENT_SYSTEM_UI_PREFIXES = arrayOf(
            "com.android.systemui",
            "com.samsung.systemui",
            "com.samsung.android.app.aodservice"
        )

        // Subset of transient Samsung packages considered safe to resume after the brief dwell grace period.
        private val TRANSIENT_SYSTEM_UI_RESUME_SAFE_PREFIXES = arrayOf(
            "com.samsung.systemui",
            "com.samsung.android.app.aodservice"
        )

        // Packages whose foreground presence indicates the user is back on a home/cover system surface.
        private val OVERLAY_RESUME_PACKAGE_PREFIXES = arrayOf(
            "com.android.systemui",
            "com.samsung.systemui",
            "com.samsung.android.app.aodservice",
            "com.sec.android.app.launcher",
            "com.samsung.android.app.cocktailbarservice",
            "com.samsung.android.app.clockface"
        )

        // Standard Android / Samsung launcher package prefixes subject to the brief post-launch guard.
        private val LAUNCHER_PACKAGE_PREFIXES = arrayOf(
            "com.sec.android.app.launcher",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher"
        )

        /** Whether the cover launcher overlay is currently attached and interactive. */
        @Volatile
        var isOverlayActive: Boolean = false
            private set

        /** Weak reference to the running [ForegroundService] so static bridges avoid leaking the Service context. */
        @Volatile
        private var activeServiceRef: WeakReference<ForegroundService>? = null

        const val CHANNEL_ID = "foreground_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.tyejaedon.coverscreenos.action.START"
        const val ACTION_STOP = "com.tyejaedon.coverscreenos.action.STOP"
        const val ACTION_HIDE_OVERLAY = "com.tyejaedon.coverscreenos.action.HIDE_OVERLAY"
        private const val EXTRA_LAUNCH_PACKAGE_NAME = "com.tyejaedon.coverscreenos.extra.LAUNCH_PACKAGE_NAME"

        /**
         * Requests restoration or retargeting of the cover overlay (e.g., when
         * [CoverAccessibilityService] detects the native cover home stabilized on top).
         */
        fun requestOverlayReclaim(reason: String = "external") {
            activeServiceRef?.get()?.requestOverlayReclaimInternal(reason)
        }

        /** Records [packageName] in the cover-screen recent-apps history ring buffer. */
        fun recordRecentCoverApp(packageName: String) {
            activeServiceRef?.get()?.recordRecentCoverAppInternal(packageName)
        }

        /**
         * Opens the in-overlay Cover Recents carousel.
         */
        fun requestCoverRecents(): Boolean {
            return activeServiceRef?.get()?.requestCoverRecentsInternal() ?: false
        }

        /** Notifies the running service that [CoverAccessibilityService] connected with a host. */
        fun onLauncherAccessibilityConnected() {
            activeServiceRef?.get()?.refreshLauncherAccessibilityHost()
        }

        /** Notifies the running service that [CoverAccessibilityService] unbound/disconnected. */
        fun onLauncherAccessibilityDisconnected() {
            activeServiceRef?.get()?.refreshLauncherAccessibilityHost()
        }

        /**
         * Suppresses the overlay for an incoming or active call UI observed in
         * [packageName] so the dialer remains interactive on the cover screen.
         */
        fun requestIncomingCallPassthrough(packageName: String) {
            activeServiceRef?.get()?.requestIncomingCallPassthroughInternal(packageName)
        }

        /** Returns `true` while an active call notification is reported by [CoverNotificationListenerService]. */
        fun isCallNotificationActive(): Boolean =
            activeServiceRef?.get()?.callNotificationActive == true

        /**
         * Updates active call notification state from [CoverNotificationListenerService],
         * suppressing during active calls and evaluating resume once the call ends.
         */
        fun updateCallNotificationState(isActive: Boolean, packageName: String?) {
            activeServiceRef?.get()?.updateCallNotificationStateInternal(isActive, packageName)
        }

        /**
         * Hides the launcher while the system unlock prompt is open so the overlay
         * does not draw on top of it. The overlay comes back once the prompt closes.
         */
        fun setUnlockPromptActive(active: Boolean) {
            val service = activeServiceRef?.get() ?: return
            service.runOnMainThread { service.setUnlockPromptActiveInternal(active) }
        }

        /** Builds an [Intent] that starts [ForegroundService] and activates cover monitoring. */
        fun createStartIntent(context: Context): Intent = Intent(context, ForegroundService::class.java).apply {
            action = ACTION_START
        }

        /** Builds an [Intent] that tears down the overlay and stops [ForegroundService]. */
        fun createStopIntent(context: Context): Intent = Intent(context, ForegroundService::class.java).apply {
            action = ACTION_STOP
        }

        /** Builds an [Intent] that suppresses the overlay for launching [packageName]. */
        fun createHideOverlayIntent(context: Context, packageName: String?): Intent = Intent(context, ForegroundService::class.java).apply {
            action = ACTION_HIDE_OVERLAY
            packageName?.trim()?.takeUnless { it.isEmpty() }?.let { putExtra(EXTRA_LAUNCH_PACKAGE_NAME, it) }
        }

        /** Returns `true` when an active [ForegroundService] instance is live in-process. */
        fun isServiceRuntimeActive(): Boolean {
            return activeServiceRef?.get() != null
        }
    }

    // Main-thread SupervisorJob scope backing polling, fold observation, and display retargeting; cancelled in onDestroy().
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    // Window command facade forwarding show/suppress/detach requests to CoverAccessibilityService.
    private lateinit var overlayWindowController: OverlayWindowController
    // Single-flight launch coordinator preventing overlapping taps and pairing suppression with Activity dispatch.
    private lateinit var launchCoordinator: CoverLaunchCoordinator
    // Helper locating the cover hardware display, monitoring keyguard lock status, and tracking fold posture.
    private lateinit var coverDisplayHelper: CoverDisplayHelper
    // System DisplayManager queried for display validity and monitored via [displayListener].
    private lateinit var displayManager: DisplayManager
    // Installed-app catalog supplied to the launcher composition via [launcherOverlayHost].
    private lateinit var appRepository: PackageManagerAppScannerRepository
    // DataStore-backed user preferences (theme, dock, wallpaper, widgets, keyboard strategy).
    private lateinit var launcherSettingsStore: LauncherSettingsStore
    // Read-only dependency/callback bundle handed to CoverAccessibilityService so it can render the launcher grid.
    private var launcherOverlayHost: LauncherOverlayHost? = null
    // AppWidgetHost lifecycle wrapper kept listening only while the cover launcher overlay is visible.
    private val widgetHostManager by lazy {
        com.tyejaedon.coverscreenos.ui.widgets.CoverAppWidgetHostManager(this)
    }
    // Tracks recently opened cover apps (most-recent-first) and Cover Recents carousel visibility.
    private lateinit var recentAppsTracker: CoverRecentAppsTracker
    // Verifies shortly after dispatching GLOBAL_ACTION_RECENTS whether native Recents actually appeared.
    private var pendingNativeRecentsVerifyJob: Job? = null
    // Cached per-session flag when the cover firmware ignores GLOBAL_ACTION_RECENTS so fallback opens instantly.
    @Volatile
    private var nativeRecentsUnsupportedOnCover = false

    // Mutable state holder for suppression session IDs, resume stability counts, reclaim cooldown, and call passthrough.
    private val suppressionState = OverlaySuppressionState()
    // Policy governing incoming-call passthrough timeouts and reclaim blocking grace windows.
    private val reclaimPolicy = OverlayReclaimPolicy(
        incomingCallSuppressionMaxMs = INCOMING_CALL_SUPPRESSION_MAX_MS,
        incomingCallReclaimBlockGraceMs = INCOMING_CALL_RECLAIM_BLOCK_GRACE_MS
    )
    // Policy classifying SystemUI/AOD flicker vs genuine returns to the native cover home screen.
    private val transientSignalPolicy = OverlayTransientSignalPolicy(
        transientSystemUiPrefixes = TRANSIENT_SYSTEM_UI_PREFIXES,
        transientSystemUiResumeSafePrefixes = TRANSIENT_SYSTEM_UI_RESUME_SAFE_PREFIXES,
        overlayResumePackagePrefixes = OVERLAY_RESUME_PACKAGE_PREFIXES,
        launcherPackagePrefixes = LAUNCHER_PACKAGE_PREFIXES,
        transientSystemUiResumeGraceMs = TRANSIENT_SYSTEM_UI_RESUME_GRACE_MS,
        transientExitFailsafeMinSuppressionMs = TRANSIENT_EXIT_FAILSAFE_MIN_SUPPRESSION_MS,
        transientExitPatternWindowMs = TRANSIENT_EXIT_PATTERN_WINDOW_MS
    )
    // Policy deciding whether to hold an existing cover-display window hidden during transient display loss.
    private val overlayAttachmentPolicy = OverlayAttachmentPolicy()

    // True once the service has received a start/launch request asking the overlay to stay active.
    private var overlayRequested = false
    // Guards balanced registerDisplayListener / unregisterDisplayListener calls.
    private var isDisplayListenerRegistered = false
    // Tracks whether ServiceCompat.startForeground has succeeded so stopForegroundIfStarted only runs when promoted.
    private var hasEnteredForeground = false
    // Guards balanced registerReceiver / unregisterReceiver calls for [screenStateReceiver].
    private var isScreenStateReceiverRegistered = false
    // Cached screen-power state from PowerManager / ACTION_SCREEN_ON|OFF; prevents attaching over doze/AOD.
    private var isScreenInteractive = true
    // True while UnlockBridgeActivity / system keyguard unlock prompt is open so the overlay yields touch/visibility.
    private var isUnlockPromptActive = false

    /**
     * Receives system power/display broadcasts (`ACTION_SCREEN_OFF` and `ACTION_SCREEN_ON`)
     * to detach the overlay during doze/AOD and rebuild it when the screen becomes interactive.
     */
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenTurnedOff()
                Intent.ACTION_SCREEN_ON -> onScreenTurnedOn()
            }
        }
    }

    // Active coroutine job performing the next overlay attach/retarget pass (cancelled on newer triggers or screen-off).
    private var pendingDisplayRetargetJob: Job? = null
    // Periodic coroutine job polling [maybeResumeOverlayAfterAppLaunch] while the overlay is suppressed.
    private var suppressionResumePollerJob: Job? = null
    // Coroutine collecting foldable posture emissions to suppress when unfolded and restore when closed.
    private var foldPostureJob: Job? = null

    // Pass-through delegates exposing [suppressionState] fields directly inside ForegroundService without duplicating state.
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
    // Transient foreground state is managed by OverlayTransientSignalPolicy.

    /**
     * Observes [DisplayManager] topology events (`added`, `removed`, `changed`) so the
     * overlay attaches when the cover display wakes/appears and cleans up if removed.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = onDisplayTopologyChanged("added", displayId)
        override fun onDisplayRemoved(displayId: Int) = onDisplayTopologyChanged("removed", displayId)
        override fun onDisplayChanged(displayId: Int) = onDisplayTopologyChanged("changed", displayId)
    }

    /** Registers the low-importance notification channel required for foreground service status on API 26+. */
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

    /** Builds the ongoing status notification anchored to [ensureForegroundSession]. */
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cover Screen OS Running")
            .setContentText("Monitoring system state in the background...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Initializes runtime dependencies, display/launch helpers, and the [LauncherOverlayHost]
     * handed off to [CoverAccessibilityService].
     */
    override fun onCreate() {
        super.onCreate()
        activeServiceRef = WeakReference(this)
        publishRuntime("Service created")
        createNotificationChannel()

        launchCoordinator = CoverLaunchCoordinator(
            onBeginLaunch = ::prepareForOverlayAppLaunch,
            onLaunchDispatched = ::onAppLaunchDispatchAcknowledged,
            onLaunchFailed = ::restoreOverlayAfterLaunchFailure,
            debugThrowOnThreadViolation = isDebuggableBuild()
        )
        appRepository = PackageManagerAppScannerRepository(this)
        launcherSettingsStore = LauncherSettingsStore(this)
        recentAppsTracker = CoverRecentAppsTracker(selfPackageName = packageName)
        overlayWindowController = OverlayWindowController()
        coverDisplayHelper = CoverDisplayHelper(this)
        displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager

        // Handoff survives accessibility-service disable/enable cycles.
        val appContext = applicationContext
        val host = LauncherOverlayHost(
            appRepository = appRepository,
            launcherSettingsStore = launcherSettingsStore,
            launchCoordinator = launchCoordinator,
            deviceLockState = coverDisplayHelper.isDeviceLocked,
            onAppSelected = { appModel ->
                val packageName = appModel.packageName
                val coverDisplayId = resolveCoverDisplayId()
                val ready = launchCoordinator.beginLaunch(packageName)
                if (!ready) {
                    Log.w(LOG_TAG, "LauncherOverlayHost.onAppSelected rejected package=$packageName")
                    false
                } else {
                    recentAppsTracker.setRecentsSheetVisible(false)
                    val launched = CoverAppLauncher.launchPackageOnDisplay(
                        context = appContext,
                        packageName = packageName,
                        displayId = coverDisplayId
                    )
                    if (launched) {
                        recentAppsTracker.recordAppActive(packageName)
                    }
                    launchCoordinator.completeLaunch(packageName = packageName, launchDispatched = launched)
                    launched
                }
            },
            persistKeyboardStrategy = { strategy ->
                launcherSettingsStore.setKeyboardStrategy(strategy)
            },
            onRequestUnlock = {
                val displayId = overlayWindowController.getActiveDisplayId()
                    ?: coverDisplayHelper.getCoverDisplayId()
                UnlockBridgeActivity.startUnlockRequest(
                    context = appContext,
                    packageName = null,
                    displayId = displayId
                )
            },
            onOpenCustomize = {
                val packageName = appContext.packageName
                val ready = launchCoordinator.beginLaunch(packageName)
                if (!ready) {
                    Log.w(LOG_TAG, "LauncherOverlayHost.onOpenCustomize rejected")
                    false
                } else {
                    val coverDisplayId = overlayWindowController.getActiveDisplayId()
                        ?: coverDisplayHelper.getCoverDisplayId()
                    val customizeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("coverscreenos://customize")).apply {
                        setPackage(packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    val launched = CoverAppLauncher.launchIntentOnDisplay(
                        context = appContext,
                        intent = customizeIntent,
                        displayId = coverDisplayId
                    )
                    launchCoordinator.completeLaunch(packageName = packageName, launchDispatched = launched)
                    launched
                }
            },
            widgetHostManager = widgetHostManager,
            onAddWidgetRequest = {
                val packageName = appContext.packageName
                val ready = launchCoordinator.beginLaunch(packageName)
                if (!ready) {
                    Log.w(LOG_TAG, "LauncherOverlayHost.onAddWidgetRequest rejected")
                    false
                } else {
                    val coverDisplayId = resolveCoverDisplayId()
                    val pickerIntent = Intent(
                        appContext,
                        com.tyejaedon.coverscreenos.ui.widgets.CoverWidgetPickerActivity::class.java
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    val launched = CoverAppLauncher.launchIntentOnDisplay(
                        context = appContext,
                        intent = pickerIntent,
                        displayId = coverDisplayId
                    )
                    launchCoordinator.completeLaunch(packageName = packageName, launchDispatched = launched)
                    launched
                }
            },
            recentAppPackages = recentAppsTracker.recentPackages,
            isRecentsSheetVisible = recentAppsTracker.isRecentsSheetVisible,
            onRequestRecents = { requestCoverRecentsInternal() },
            onDismissRecentApp = { pkg -> recentAppsTracker.removeApp(pkg) },
            onClearAllRecentApps = { recentAppsTracker.clearAll() },
            onSetRecentsSheetVisible = { visible -> recentAppsTracker.setRecentsSheetVisible(visible) }
        )
        launcherOverlayHost = host
        CoverAccessibilityService.attachLauncherHost(host)
    }

    private fun recordRecentCoverAppInternal(packageName: String) {
        recentAppsTracker.recordAppActive(packageName)
    }

    /**
     * Opens the custom in-overlay Cover Recents carousel and reclaims/retargets the cover overlay
     * if it is currently suppressed for an active foreground app.
     */
    private fun requestCoverRecentsInternal(): Boolean {
        if (!overlayRequested || !hasRuntimePrerequisites() || isDeviceUnfolded()) return false

        pendingNativeRecentsVerifyJob?.cancel()
        pendingNativeRecentsVerifyJob = null

        if (preferNative && !nativeRecentsUnsupportedOnCover) {
            recentAppsTracker.setRecentsSheetVisible(false)
            val wasSuppressedBeforeRecents = isOverlaySuppressedForAppLaunch
            val dispatched = CoverAccessibilityService.performGlobalRecentsOnActiveService()
            if (dispatched) {
                pendingNativeRecentsVerifyJob = serviceScope.launch {
                    delay(NATIVE_RECENTS_VERIFY_DELAY_MS.milliseconds)
                    val latestClass = CoverAccessibilityService.currentForegroundClassName()
                    if (CoverHomeSurfaceMatcher.isRecentsSurfaceClassName(latestClass)) {
                        // Native Recents opened; suppress now only if triggered from the active launcher.
                        if (!wasSuppressedBeforeRecents && !isOverlaySuppressedForAppLaunch) {
                            suppressOverlayForReason(
                                packageName = NATIVE_RECENTS_SENTINEL_PACKAGE,
                                reason = OverlaySuppressionReason.APP_LAUNCH
                            )
                        }
                        return@launch
                    }
                    nativeRecentsUnsupportedOnCover = true
                    recentAppsTracker.setRecentsSheetVisible(true)
                    if (isOverlaySuppressedForAppLaunch) {
                        completeSuppressionAndRetargetOnce(reason = "native_recents_no_op_fallback")
                    }
                }
                return true
            }
        }

        recentAppsTracker.setRecentsSheetVisible(true)
        if (isOverlaySuppressedForAppLaunch) {
            completeSuppressionAndRetargetOnce(reason = "cover_recents")
        } else {
            scheduleRetarget(reason = "cover_recents")
        }
        return true
    }

    /**
     * Handles start, stop, and launch-suppression commands.
     *
     * A null Intent is a restart request and follows the normal start path,
     * rebuilding monitoring after [onCreate] restores the runtime dependencies.
     * Successful normal starts return [START_STICKY]; stop, hide-overlay, and
     * failed setup paths return [START_NOT_STICKY]. The last command result
     * controls the restart request; sticky mode does not prevent force-stop.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                publishRuntime("Service stopping")
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
                publishRuntime("Service started")
                coverDisplayHelper.startLockStatusMonitoring()
                coverDisplayHelper.startPostureMonitoring(serviceScope)
                startFoldPostureObservation()
                registerDisplayListenerIfNeeded()

                // Rehydrate call suppression state after service restarts so active calls
                // do not depend on a future listener state-change callback.
                CoverNotificationListenerService.replayCallNotificationStateToForegroundService()

                scheduleRetarget(reason = "service_start")
            }
        }
        return START_STICKY
    }

    /** Returns `null`; this component operates purely as a started service without IPC binding. */
    override fun onBind(intent: Intent?): IBinder? = null

    /** Detaches the host handoff, cancels coroutines, tears down listeners/overlay, and exits foreground mode. */
    override fun onDestroy() {
        if (activeServiceRef?.get() === this) {
            activeServiceRef?.clear()
            activeServiceRef = null
        }
        // Release the launcher host handoff first so the AS drops its
        // reference even if teardown below throws.
        if (launcherOverlayHost != null) {
            CoverAccessibilityService.detachLauncherHost()
            launcherOverlayHost = null
        }
        serviceScope.cancel() // Instantly terminates all polling and delays
        teardownOverlayRuntime()
        stopForegroundIfStarted()
        publishRuntime("Service stopped")
        super.onDestroy()
    }

    /**
     * Handles an incoming reclaim request after verifying runtime prerequisites,
     * unfolded posture, incoming-call blocks, and [OVERLAY_RECLAIM_MIN_INTERVAL_MS] throttling.
     *
     * Routes to [maybeResumeOverlayAfterAppLaunch] when suppressed, or schedules an
     * immediate display retarget otherwise.
     */
    private fun requestOverlayReclaimInternal(reason: String) {
        if (!overlayRequested || !hasRuntimePrerequisites()) return

        if (isDeviceUnfolded()) {
            if (isOverlaySuppressedForAppLaunch && suppressionReason == OverlaySuppressionReason.APP_LAUNCH) {
                runOnMainThread {
                    suppressOverlayForReason(
                        packageName = "com.android.internal.posture",
                        reason = OverlaySuppressionReason.POSTURE_UNFOLDED
                    )
                }
            }
            return
        }

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
        publishRuntime("Overlay reclaim requested")

        logDebug { "request reason=$normalizedReason overlayActive=$isOverlayActive display=${overlayWindowController.getActiveDisplayId()} suppressed=$isOverlaySuppressedForAppLaunch" }

        if (normalizedReason == "gesture_bar_home") {
            recentAppsTracker.setRecentsSheetVisible(false)
            if (isOverlaySuppressedForAppLaunch) {
                completeSuppressionAndRetargetOnce(reason = "reclaim:$normalizedReason")
            } else {
                scheduleRetarget(reason = "foreground_reclaim:$normalizedReason")
            }
            return
        }

        serviceScope.launch {
            if (isOverlaySuppressedForAppLaunch) {
                maybeResumeOverlayAfterAppLaunch(reason = "reclaim:$normalizedReason")
            } else {
                scheduleRetarget(reason = "foreground_reclaim:$normalizedReason")
            }
        }
    }

    /** Re-evaluates display attachment whenever [CoverAccessibilityService] connects or unbinds. */
    private fun refreshLauncherAccessibilityHost() {
        if (overlayRequested && !isOverlaySuppressedForAppLaunch) {
            scheduleRetarget(reason = "launcher_accessibility_connection_changed")
        }
    }

    /** Suppresses the launcher overlay under [OverlaySuppressionReason.INCOMING_CALL] for [packageName]. */
    private fun requestIncomingCallPassthroughInternal(packageName: String) {
        if (!hasRuntimePrerequisites()) return
        val normalizedPackageName = packageName.trim().ifEmpty { "incoming_call" }
        trackIncomingCallPassthrough(normalizedPackageName)

        if (isOverlaySuppressedForAppLaunch && suppressionReason == OverlaySuppressionReason.INCOMING_CALL) {
            return
        }

        runOnMainThread {
            suppressOverlayForReason(packageName = normalizedPackageName, reason = OverlaySuppressionReason.INCOMING_CALL)
        }
    }

    /**
     * Synchronizes notification-driven call state into [suppressionState], suppressing while a
     * call notification is active and prompting resume evaluation once it ends.
     */
    private fun updateCallNotificationStateInternal(isActive: Boolean, packageName: String?) {
        val normalizedPackageName = packageName?.trim()?.takeUnless { it.isEmpty() }
        val nowElapsedMs = SystemClock.elapsedRealtime()

        callNotificationActive = isActive
        callNotificationPackage = if (isActive) normalizedPackageName else null

        if (isActive) {
            callNotificationLastSignalElapsedMs = nowElapsedMs
            normalizedPackageName?.let { trackIncomingCallPassthrough(it) }

            if (!isOverlaySuppressedForAppLaunch || suppressionReason != OverlaySuppressionReason.INCOMING_CALL) {
                runOnMainThread {
                    suppressOverlayForReason(
                        packageName = normalizedPackageName ?: "incoming_call_notification",
                        reason = OverlaySuppressionReason.INCOMING_CALL
                    )
                }
            }
            return
        }

        if (isOverlaySuppressedForAppLaunch && suppressionReason == OverlaySuppressionReason.INCOMING_CALL) {
            runOnMainThread {
                maybeResumeOverlayAfterAppLaunch(reason = "call_notification_ended")
            }
        }
    }

    /**
     * Hides overlay touchability while the system keyguard/unlock prompt is visible,
     * refreshing lock status and retargeting when the prompt closes.
     */
    private fun setUnlockPromptActiveInternal(active: Boolean) {
        if (isUnlockPromptActive == active) return
        isUnlockPromptActive = active
        if (active) {
            clearPendingDisplayWork()
            overlayWindowController.suppressOverlayForLaunch()
            isOverlayActive = false
            publishRuntime("Overlay hidden: unlock prompt open")
            return
        }
        if (!active) {
            coverDisplayHelper.refreshLockStatus()
        }
        publishRuntime("Unlock prompt closed")
        if (overlayRequested && !isOverlaySuppressedForAppLaunch) {
            scheduleRetarget(reason = "unlock_prompt_closed")
        }
    }

    /** Handles `Intent.ACTION_SCREEN_OFF` by detaching the overlay so AOD/doze remains unobstructed. */
    private fun onScreenTurnedOff() {
        isScreenInteractive = false
        clearPendingDisplayWork()
        // Fully detach the launcher so nothing is drawn over the doze / always-on
        // state and the panel can go dark. It is rebuilt on ACTION_SCREEN_ON.
        overlayWindowController.removeOverlay()
        isOverlayActive = false
        publishRuntime("Overlay removed: screen off")
    }

    /**
     * Handles `Intent.ACTION_SCREEN_ON` by refreshing lock status and either re-evaluating active
     * suppression immediately (so waking to lockscreen/cover home reclaims right away) or scheduling retarget.
     */
    private fun onScreenTurnedOn() {
        isScreenInteractive = true
        coverDisplayHelper.refreshLockStatus()
        if (!overlayRequested) return
        if (isOverlaySuppressedForAppLaunch) {
            maybeResumeOverlayAfterAppLaunch(reason = "screen_on")
            return
        }
        scheduleRetarget(reason = "screen_on")
    }

    private inline fun runOnMainThread(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        serviceScope.launch { block() }
    }

    /**
     * Promotes this service to foreground mode using [ServiceCompat.startForeground] and
     * [ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE] (required on Android 14+ / API 34+).
     */
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

    /** Demotes foreground status and removes the persistent notification if active. */
    private fun stopForegroundIfStarted() {
        if (!hasEnteredForeground) return
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        hasEnteredForeground = false
    }

    /** Responds to [DisplayManager.DisplayListener] changes by checking permissions and retargeting. */
    private fun onDisplayTopologyChanged(changeType: String, displayId: Int) {
        if (!overlayRequested || isOverlaySuppressedForAppLaunch) return
        if (!hasRuntimePrerequisites()) {
            stopServiceForMissingPrerequisites(reason = "display_${changeType}_$displayId")
            return
        }
        scheduleRetarget(reason = "display_$changeType:$displayId")
    }

    private fun isDeviceUnfolded(): Boolean {
        val posture = runCatching { coverDisplayHelper.foldPosture.value }.getOrNull()
        return posture == com.tyejaedon.coverscreenos.helpers.CoverFoldPosture.HALF_FOLDED ||
            posture == com.tyejaedon.coverscreenos.helpers.CoverFoldPosture.FLAT
    }

    /**
     * Evaluates whether the launcher overlay should attach, move, hide, or detach.
     *
     * Guards against unfolded posture, active suppression, missing permissions, non-interactive
     * screens (`!isScreenInteractive`), and open unlock prompts. When [CoverDisplayHelper.getCoverDisplay]
     * returns `null`, consults [OverlayAttachmentPolicy] to decide between holding the
     * existing secondary-display window hidden vs removing stale overlay state.
     */
    private fun attachOrRetargetOverlay(reason: String) {
        if (isOverlaySuppressedForAppLaunch) return
        if (!hasRuntimePrerequisites()) {
            stopServiceForMissingPrerequisites(reason = "$reason prerequisites_lost")
            return
        }
        if (isDeviceUnfolded()) {
            overlayWindowController.suppressOverlayForLaunch()
            isOverlayActive = false
            logDebug { "overlay reason=$reason skipped: device unfolded" }
            return
        }
        if (!isScreenInteractive) {
            logDebug { "overlay reason=$reason skipped: screen off" }
            return
        }
        if (isUnlockPromptActive) {
            overlayWindowController.suppressOverlayForLaunch()
            isOverlayActive = false
            logDebug { "overlay reason=$reason held hidden: unlock prompt open" }
            return
        }

        val targetDisplay = coverDisplayHelper.getCoverDisplay()
        if (targetDisplay != null) {
            attachOverlayToTarget(targetDisplay, reason)
            return
        }

        val activeId = overlayWindowController.getActiveDisplayId()
        val isOverlayAttached = overlayWindowController.isOverlayAttached()
        val activeDisplayIsValid = activeId
            ?.let { displayManager.getDisplay(it) }
            ?.isValid == true
        val shouldHoldSuppressedOverlay = overlayAttachmentPolicy.shouldHoldSuppressedOnCurrentDisplay(
            isOverlayAttached = isOverlayAttached,
            activeDisplayId = activeId,
            isActiveDisplayStillValid = activeDisplayIsValid
        )

        if (shouldHoldSuppressedOverlay) {
            overlayWindowController.suppressOverlayForLaunch()
            isOverlayActive = false
            publishRuntime("Overlay hidden: cover display unavailable")
            Log.i(
                OVERLAY_TRANSITION_LOG_TAG,
                "$OVERLAY_MARKER_HELD_HIDDEN reason=$reason activeId=$activeId activeDisplayValid=$activeDisplayIsValid active=${coverDisplayHelper.describeDisplayState(activeId)} displays=${coverDisplayHelper.describeDisplays()}"
            )
            logDebug {
                "overlay reason=$reason holding_hidden_on_cover activeId=$activeId activeDisplayValid=$activeDisplayIsValid displays=${coverDisplayHelper.describeDisplays()}"
            }
            return
        }

        val removedStaleOverlayState = isOverlayAttached || activeId != null
        if (removedStaleOverlayState) {
            overlayWindowController.removeOverlay()
            Log.i(
                OVERLAY_TRANSITION_LOG_TAG,
                "$OVERLAY_MARKER_REMOVED_STALE reason=$reason activeId=$activeId activeDisplayValid=$activeDisplayIsValid attached=$isOverlayAttached"
            )
        }
        isOverlayActive = false
        if (removedStaleOverlayState) publishRuntime("Overlay removed: cover display unavailable")
        logDebug {
            "overlay reason=$reason no_cover_available overlayStateCleared=true activeId=$activeId activeDisplayValid=$activeDisplayIsValid displays=${coverDisplayHelper.describeDisplays()}"
        }
    }

    /**
     * Attaches (or re-enables) the launcher overlay on [targetDisplay], forcing a window
     * rebuild if the active display ID changed, and syncs [widgetHostManager] listening.
     */
    private fun attachOverlayToTarget(targetDisplay: Display, reason: String) {
        val targetId = targetDisplay.displayId
        if (targetId != Display.DEFAULT_DISPLAY) {
            CoverAccessibilityService.updateMonitoredCoverDisplayIdOnActiveService(targetId)
        }
        val activeId = overlayWindowController.getActiveDisplayId()
        val shouldForceRetarget = overlayWindowController.isOverlayAttached() && activeId != targetId

        isOverlayActive = overlayWindowController.showOverlay(targetDisplay, shouldForceRetarget)
        if (isOverlayActive) {
            widgetHostManager.startListening()
        } else {
            widgetHostManager.stopListening()
        }
        publishRuntime(if (isOverlayActive) "Overlay attached" else "Overlay attachment failed")
        logDebug { "overlay reason=$reason targetId=$targetId activeId=${overlayWindowController.getActiveDisplayId()} forceRetarget=$shouldForceRetarget attached=$isOverlayActive displays=${coverDisplayHelper.describeDisplays()}" }
    }

    /** Cancels any queued retarget coroutine and launches a fresh [attachOrRetargetOverlay] pass. */
    private fun scheduleRetarget(reason: String) {
        pendingDisplayRetargetJob?.cancel()
        pendingDisplayRetargetJob = serviceScope.launch {
            attachOrRetargetOverlay(reason)
        }
    }

    private fun clearPendingDisplayWork() {
        pendingDisplayRetargetJob?.cancel()
    }

    /**
     * Registers [screenStateReceiver] (`ACTION_SCREEN_OFF` / `ACTION_SCREEN_ON`) and seeds
     * [isScreenInteractive] from [PowerManager.isInteractive].
     */
    private fun registerScreenStateReceiverIfNeeded() {
        if (isScreenStateReceiverRegistered) return
        isScreenInteractive = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        isScreenStateReceiverRegistered = true
    }

    private fun unregisterScreenStateReceiverIfNeeded() {
        if (!isScreenStateReceiverRegistered) return
        runCatching { unregisterReceiver(screenStateReceiver) }
        isScreenStateReceiverRegistered = false
        isScreenInteractive = true
    }

    /** Registers [displayListener] with [DisplayManager] alongside the screen-state receiver. */
    private fun registerDisplayListenerIfNeeded() {
        registerScreenStateReceiverIfNeeded()
        if (isDisplayListenerRegistered) return
        displayManager.registerDisplayListener(displayListener, null) // Handled safely by DisplayManager internals
        isDisplayListenerRegistered = true
    }

    private fun unregisterDisplayListenerIfNeeded() {
        unregisterScreenStateReceiverIfNeeded()
        if (!isDisplayListenerRegistered) return
        displayManager.unregisterDisplayListener(displayListener)
        isDisplayListenerRegistered = false
    }

    private fun hasRuntimePrerequisites(): Boolean =
        ForegroundServiceHelper.hasCoreOverlayPermissions(this)

    /** Stops monitoring, clears suppression state, unregisters listeners, and detaches the overlay. */
    private fun teardownOverlayRuntime() {
        nativeRecentsUnsupportedOnCover = false
        clearAppLaunchSuppression()
        foldPostureJob?.cancel()
        foldPostureJob = null
        overlayRequested = false
        isUnlockPromptActive = false
        clearPendingDisplayWork()
        coverDisplayHelper.stopLockStatusMonitoring()
        coverDisplayHelper.stopPostureMonitoring()
        widgetHostManager.stopListening()
        unregisterDisplayListenerIfNeeded()
        overlayWindowController.removeOverlay()
        isOverlayActive = false
        publishRuntime()
    }

    /** [CoverLaunchCoordinator] preflight hook that prepares monitoring and suppresses the overlay. */
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

    /** Restores the overlay immediately when [CoverLaunchCoordinator] reports launch dispatch failure. */
    private fun restoreOverlayAfterLaunchFailure(packageName: String) {
        completeSuppressionAndRetargetOnce(reason = "launch_app_failed_restore_overlay:$packageName")
    }

    /**
     * Enters a new suppression session in [suppressionState], makes the overlay non-touchable,
     * stops widget updates, and starts [startSuppressionResumePolling].
     */
    private fun suppressOverlayForReason(packageName: String, reason: OverlaySuppressionReason) {
        val knownCoverDisplayId = overlayWindowController.getActiveDisplayId()
            ?: coverDisplayHelper.getCoverDisplayId()
        if (knownCoverDisplayId != null && knownCoverDisplayId != Display.DEFAULT_DISPLAY) {
            CoverAccessibilityService.updateMonitoredCoverDisplayIdOnActiveService(knownCoverDisplayId)
        }
        suppressionState.markSuppressionStarted(
            packageName = packageName,
            reason = reason,
            nowElapsedMs = SystemClock.elapsedRealtime()
        )

        clearPendingDisplayWork()
        overlayWindowController.suppressOverlayForLaunch()
        widgetHostManager.stopListening()
        isOverlayActive = false
        publishRuntime("Overlay suppressed: ${reason.name.lowercase()}")

        startSuppressionResumePolling()
    }

    /** Polls [maybeResumeOverlayAfterAppLaunch] every [APP_LAUNCH_RESUME_POLL_INTERVAL_MS] while suppressed. */
    private fun startSuppressionResumePolling() {
        suppressionResumePollerJob?.cancel()
        suppressionResumePollerJob = serviceScope.launch {
            while (isActive && isOverlaySuppressedForAppLaunch) {
                maybeResumeOverlayAfterAppLaunch(reason = "poll")
                delay(APP_LAUNCH_RESUME_POLL_INTERVAL_MS.milliseconds)
            }
        }
    }

    /**
     * Collects [CoverDisplayHelper.foldPosture] changes so unfolding (`HALF_FOLDED` / `FLAT`)
     * suppresses the cover overlay and folding shut (`CLOSED`) restores it.
     */
    private fun startFoldPostureObservation() {
        foldPostureJob?.cancel()
        foldPostureJob = serviceScope.launch {
            coverDisplayHelper.foldPosture.collect { posture ->
                when (posture) {
                    com.tyejaedon.coverscreenos.helpers.CoverFoldPosture.HALF_FOLDED,
                    com.tyejaedon.coverscreenos.helpers.CoverFoldPosture.FLAT -> {
                        if (isOverlayActive ||
                            (isOverlaySuppressedForAppLaunch && suppressionReason == OverlaySuppressionReason.APP_LAUNCH)
                        ) {
                            Log.d(LOG_TAG, "Device unfolded ($posture); suppressing cover overlay")
                            suppressOverlayForReason(
                                packageName = "com.android.internal.posture",
                                reason = OverlaySuppressionReason.POSTURE_UNFOLDED
                            )
                        }
                    }
                    com.tyejaedon.coverscreenos.helpers.CoverFoldPosture.CLOSED -> {
                        if (isOverlaySuppressedForAppLaunch && suppressionReason == OverlaySuppressionReason.POSTURE_UNFOLDED) {
                            Log.d(LOG_TAG, "Device closed ($posture); restoring cover overlay")
                            completeSuppressionAndRetargetOnce(reason = "posture_closed_resume_overlay")
                        }
                    }
                }
            }
        }
    }

    /**
     * Evaluates the multi-gate resume state machine to decide whether a suppressed overlay
     * should be restored to the cover screen.
     *
     * Gate sequence:
     * 1. Verify active suppression and [overlayRequested].
     * 2. If [OverlaySuppressionReason.POSTURE_UNFOLDED], resume only once fold posture returns to `CLOSED`.
     * 3. Enforce max timeout (`maxSuppressionMs`) and minimum dwell ([APP_LAUNCH_RESUME_MIN_SUPPRESSION_MS]).
     * 4. Hold while [OverlayReclaimPolicy] keeps incoming-call passthrough active.
     * 5. Immediately resume if the cover display re-locked.
     * 6. Resolve the current foreground package via [resolveForegroundPackageForResume].
     * 7. Handle transient SystemUI/AOD exit patterns and grace periods via [OverlayTransientSignalPolicy].
     * 8. Require consecutive signal stability ([APP_LAUNCH_RESUME_STABLE_SIGNAL_COUNT]) and
     *    minimum launcher dwell ([APP_LAUNCH_RESUME_MIN_LAUNCHER_MS]) for non-reclaim polls before
     *    invoking [completeSuppressionAndRetargetOnce].
     */
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

        if (suppressionReason == OverlaySuppressionReason.POSTURE_UNFOLDED) {
            if (coverDisplayHelper.foldPosture.value == com.tyejaedon.coverscreenos.helpers.CoverFoldPosture.CLOSED) {
                completeSuppressionAndRetargetOnce(reason = "posture_closed_resume_overlay")
            }
            return
        }

        val suppressedForMs = SystemClock.elapsedRealtime() - launchSuppressedStartedElapsedMs
        val maxSuppressionMs = when (suppressionReason) {
            OverlaySuppressionReason.APP_LAUNCH -> APP_LAUNCH_RESUME_MAX_SUPPRESSION_MS
            OverlaySuppressionReason.INCOMING_CALL -> INCOMING_CALL_SUPPRESSION_MAX_MS
            OverlaySuppressionReason.POSTURE_UNFOLDED -> Long.MAX_VALUE
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

        // If a mid-app incoming call finished while the pre-call user app is still open on the cover display,
        // downgrade back to APP_LAUNCH instead of reclaiming the launcher over the user's open app.
        val interruptedAppPackage = suppressionState.priorAppLaunchPackageName
        if (suppressionReason == OverlaySuppressionReason.INCOMING_CALL && interruptedAppPackage != null) {
            val coverDisplayId = resolveCoverDisplayId()
            val windowSnapshot = coverDisplayId?.let { displayId ->
                CoverAccessibilityService.inspectDisplayWindowState(
                    displayId = displayId,
                    launchedPackage = interruptedAppPackage
                )
            }
            val liveForeground = latestForegroundSignal()
            val liveUserApp = when {
                windowSnapshot?.hasLaunchedAppWindow == true -> interruptedAppPackage
                windowSnapshot?.hasAnyUserAppWindowAboveHome == true -> windowSnapshot.activeUserAppPackage
                liveForeground != null &&
                    liveForeground.third <= APP_LAUNCH_RESUME_STALE_EVENT_MAX_MS &&
                    CoverAccessibilityService.isUserForegroundApplicationPackage(
                        packageName = liveForeground.first,
                        className = liveForeground.second
                    ) -> liveForeground.first
                else -> null
            }
            if (liveUserApp != null && suppressionState.restorePriorAppLaunchSuppressionIfPresent(liveUserApp)) {
                logResumeDecision(
                    reason = reason,
                    decision = "downgrade_call_to_app_launch",
                    detail = "restoredPackage=$liveUserApp"
                )
                return
            }
        }

        val (foregroundPackage, foregroundClassName) = resolveForegroundPackageForResume()
            ?: run {
                logResumeDecision(reason = reason, decision = "wait_no_eligible_foreground")
                return
            }
        var hasTransientResumeSignal = false
        if (reason == "poll" && isTransientSystemUiPackage(foregroundPackage, foregroundClassName)) {
            trackTransientExitPattern(foregroundPackage)

            if (shouldResumeFromTransientExitPattern(suppressedForMs)) {
                logResumeDecision(
                    reason = reason,
                    decision = "resume_transient_exit_pattern",
                    detail = "foreground=$foregroundPackage minSuppressionMs=$TRANSIENT_EXIT_FAILSAFE_MIN_SUPPRESSION_MS"
                )
                hasTransientResumeSignal = true
            } else if (!isTransientForegroundSafeForResume(foregroundPackage, foregroundClassName)) {
                if (transientSignalPolicy.shouldResumeFromSustainedSystemUiFallback(
                        packageName = foregroundPackage,
                        className = foregroundClassName,
                        suppressedForMs = suppressedForMs
                    )
                ) {
                    logResumeDecision(
                        reason = reason,
                        decision = "resume_after_systemui_fallback_dwell",
                        detail = "foreground=$foregroundPackage class=$foregroundClassName"
                    )
                    hasTransientResumeSignal = true
                } else {
                    logResumeDecision(
                        reason = reason,
                        decision = "block_transient_resume_package",
                        detail = "foreground=$foregroundPackage class=$foregroundClassName"
                    )
                    return
                }
            } else {
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
            isLauncherPackageForResume(foregroundPackage, foregroundClassName) &&
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

    /**
     * Atomically completes the active suppression session once (keyed by [suppressionSessionId]),
     * clears suppression tracking, and schedules overlay re-attachment.
     */
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
        scheduleRetarget(reason = reason)
    }

    private fun resolveCoverDisplayId(): Int? =
        overlayWindowController.getActiveDisplayId()?.takeIf { it != Display.DEFAULT_DISPLAY }
            ?: coverDisplayHelper.getCoverDisplayId()?.takeIf { it != Display.DEFAULT_DISPLAY }

    /**
     * Resolves an eligible foreground `(packageName, className)` candidate for overlay resumption.
     *
     * First inspects live cover-display windows on API 30+ via
     * [CoverAccessibilityService.inspectDisplayWindowState] after [TRANSIENT_EXIT_FAILSAFE_MIN_SUPPRESSION_MS],
     * then falls back to the freshest accessibility foreground event from [latestForegroundSignal]
     * if within [APP_LAUNCH_RESUME_STALE_EVENT_MAX_MS].
     */
    private fun resolveForegroundPackageForResume(): Pair<String, String?>? {
        val coverDisplayId = resolveCoverDisplayId()
        val launchedPackage = launchSuppressedPackageName?.trim()?.takeUnless { it.isEmpty() }
        val suppressedForMs = if (isOverlaySuppressedForAppLaunch && launchSuppressedStartedElapsedMs != 0L) {
            (SystemClock.elapsedRealtime() - launchSuppressedStartedElapsedMs).coerceAtLeast(0L)
        } else {
            0L
        }
        if (coverDisplayId != null &&
            launchedPackage != null &&
            suppressionReason == OverlaySuppressionReason.APP_LAUNCH &&
            suppressedForMs >= TRANSIENT_EXIT_FAILSAFE_MIN_SUPPRESSION_MS
        ) {
            val snapshot = CoverAccessibilityService.inspectDisplayWindowState(
                displayId = coverDisplayId,
                launchedPackage = launchedPackage
            )
            if (snapshot != null) {
                if (snapshot.hasLaunchedAppWindow || snapshot.hasAnyUserAppWindowAboveHome) {
                    logResumeDecision(
                        reason = "resolve_windows",
                        decision = "user_app_window_above_home",
                        detail = "display=$coverDisplayId launched=$launchedPackage activeUserApp=${snapshot.activeUserAppPackage}"
                    )
                    return null
                }
                if (snapshot.coverHomePackage != null) {
                    logResumeDecision(
                        reason = "resolve_windows",
                        decision = "cover_home_window_top",
                        detail = "display=$coverDisplayId pkg=${snapshot.coverHomePackage} title=${snapshot.coverHomeClassName}"
                    )
                    return snapshot.coverHomePackage to snapshot.coverHomeClassName
                }
            }
        }

        val (foregroundPackage, foregroundClassName, eventAgeMs) = latestForegroundSignal()
            ?: run {
                logResumeDecision(reason = "resolve", decision = "no_foreground_package")
                return null
            }
        if (eventAgeMs > APP_LAUNCH_RESUME_STALE_EVENT_MAX_MS) {
            if (isTransientSystemUiPackage(foregroundPackage, foregroundClassName) &&
                isTransientForegroundSafeForResume(foregroundPackage, foregroundClassName)
            ) {
                logResumeDecision(
                    reason = "resolve",
                    decision = "stale_transient_allowed",
                    detail = "foreground=$foregroundPackage class=$foregroundClassName eventAgeMs=$eventAgeMs"
                )
                return foregroundPackage to foregroundClassName
            }
            logResumeDecision(
                reason = "resolve",
                decision = "stale_foreground_event",
                detail = "foreground=$foregroundPackage class=$foregroundClassName eventAgeMs=$eventAgeMs maxAgeMs=$APP_LAUNCH_RESUME_STALE_EVENT_MAX_MS"
            )
            return null
        }
        if (!shouldResumeOverlayForPackage(foregroundPackage, foregroundClassName)) {
            logResumeDecision(
                reason = "resolve",
                decision = "foreground_not_eligible",
                detail = "foreground=$foregroundPackage class=$foregroundClassName launchedPackage=$launchSuppressedPackageName"
            )
            return null
        }
        return foregroundPackage to foregroundClassName
    }

    /**
     * Picks the most recent foreground `(package, className, ageMs)` observation across both
     * [CoverAccessibilityService] and [CoverInputAccessibilityService].
     */
    private fun latestForegroundSignal(): Triple<String, String?, Long>? = listOfNotNull(
        CoverAccessibilityService.currentForegroundPackage()?.trim()
            ?.takeUnless(String::isEmpty)
            ?.let {
                Triple(
                    it,
                    CoverAccessibilityService.currentForegroundClassName(),
                    CoverAccessibilityService.currentForegroundPackageEventAgeMs()
                )
            },
        CoverInputAccessibilityService.currentForegroundPackage()?.trim()
            ?.takeUnless(String::isEmpty)
            ?.let {
                Triple(
                    it,
                    CoverInputAccessibilityService.currentForegroundClassName(),
                    CoverInputAccessibilityService.currentForegroundPackageEventAgeMs()
                )
            }
    ).minByOrNull { (_, _, ageMs) -> ageMs }

    private fun shouldResumeOverlayForPackage(packageName: String, className: String? = null): Boolean {
        val launchedPackage = launchSuppressedPackageName?.trim()?.takeUnless { it.isEmpty() }
        return transientSignalPolicy.shouldResumeOverlayForPackage(
            packageName = packageName,
            className = className,
            launchedPackage = launchedPackage,
            appPackageName = this.packageName
        )
    }

    private fun isTransientSystemUiPackage(packageName: String, className: String? = null): Boolean {
        return transientSignalPolicy.isTransientSystemUiPackage(packageName, className)
    }

    private fun isTransientForegroundSafeForResume(packageName: String, className: String? = null): Boolean {
        return transientSignalPolicy.isTransientForegroundSafeForResume(packageName, className)
    }

    private fun isLauncherPackageForResume(packageName: String, className: String? = null): Boolean {
        return transientSignalPolicy.isLauncherPackageForResume(packageName, className)
    }

    private fun isIncomingCallPackage(packageName: String): Boolean = CallPackageMatchers.isIncomingCallPackage(packageName)

    private fun resolveRawForegroundPackageForCallGuard(): String? {
        val (foregroundPackage, _, eventAgeMs) = latestForegroundSignal() ?: return null
        if (eventAgeMs > APP_LAUNCH_RESUME_STALE_EVENT_MAX_MS) return null
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

    /** Cancels resume polling and resets suppression, transient signal, and call passthrough state. */
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

    /** Tears down runtime state, exits foreground mode, and stops the service when permissions are revoked. */
    private fun stopServiceForMissingPrerequisites(reason: String) {
        Log.w(LOG_TAG, "Stopping service because runtime prerequisites are missing. reason=$reason")
        teardownOverlayRuntime()
        stopForegroundIfStarted()
        stopSelf()
    }

    private fun isDebuggableBuild(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
