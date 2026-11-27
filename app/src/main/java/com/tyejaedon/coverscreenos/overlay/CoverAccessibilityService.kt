package com.tyejaedon.coverscreenos.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityGestureEvent
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.overlay.surface.CoverComposeSurface
import com.tyejaedon.coverscreenos.notifications.CallPackageMatchers
import com.tyejaedon.coverscreenos.ui.launcher.CoverAppGridOverlay
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import java.lang.ref.WeakReference
import kotlinx.coroutines.launch

/**
 * Owns the launcher's accessibility overlay window on the selected cover display.
 *
 * [ForegroundService] supplies dependencies through [LauncherOverlayHost] and
 * sends window commands through [OverlayWindowController]. This connected
 * [AccessibilityService] provides the privileged system token for
 * [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] (which requires no
 * `SYSTEM_ALERT_WINDOW` draw-over-other-apps grant); the shared
 * [CoverComposeSurface] handles the display-scoped `WindowManager`,
 * Compose lifecycle, and view cleanup.
 *
 * Foreground window events (`onAccessibilityEvent`), accessibility gestures
 * (`onGesture`), and hardware back keys (`onKeyEvent`) feed runtime
 * suppression/reclaim policy and global navigation actions while the cover
 * overlay is active. Unbinding or destroying this service detaches its
 * launcher surface.
 */
@SuppressLint("AccessibilityPolicy")
class CoverAccessibilityService : AccessibilityService() {
    companion object {
        private const val LOG_TAG = "CoverAccessibility"
        private const val LAUNCHER_SURFACE_LOG_TAG = "CoverA11yLauncher"
        private const val OVERLAY_RECLAIM_LOG_TAG = "CoverOverlayReclaim"
        // How long to ignore the exact same accessibility swipe gesture so one flick isn't handled twice.
        private const val GESTURE_DEBOUNCE_MS = 550L
        // Minimum pause between back-to-back system actions (back, home, shade, lock) to prevent double triggers.
        private const val ACTION_THROTTLE_MS = 300L
        // Rate limit for updating the shared timestamp when the same app keeps firing window events.
        private const val FOREGROUND_EVENT_REFRESH_MIN_INTERVAL_MS = 250L
        // How long the native cover home screen must stay on top before we bring our launcher overlay back.
        private const val RECLAIM_STABILITY_DEBOUNCE_MS = 1_500L
        // Safety cooldown after seeing a real user app so a brief system flash doesn't snatch focus away.
        private const val RECENT_USER_APP_GUARD_MS = 2_000L
        // Package prefixes for Samsung's stock launcher that signal the user exited back to home.
        private val OVERLAY_RECLAIM_PACKAGE_PREFIXES = arrayOf(
            "com.sec.android.app.launcher"
        )
        // Temporary system UI overlays (like volume bars or AOD) that shouldn't immediately reclaim our launcher.
        private val TRANSIENT_SYSTEM_UI_PREFIXES = arrayOf(
            "com.android.systemui",
            "com.samsung.systemui",
            "com.samsung.android.app.aodservice"
        )
        // System, launcher, and self packages excluded when tracking whether a real user app was recently open.
        private val NON_USER_APP_PREFIXES = arrayOf(
            "com.android.systemui",
            "com.samsung.systemui",
            "com.samsung.android.app.aodservice",
            "com.sec.android.app.launcher",
            "com.tyejaedon.coverscreenos"
        )
        /** Most recent non-self package seen on the cover display (shared across threads for polling). */
        @Volatile
        private var latestForegroundPackage: String? = null

        /** Component class name paired with [latestForegroundPackage] (e.g., `SubHomeActivity`). */
        @Volatile
        private var latestForegroundClassName: String? = null

        /** Monotonic uptime timestamp (`elapsedRealtime`) when [latestForegroundPackage] was last updated. */
        @Volatile
        private var latestForegroundEventElapsedMs: Long = 0L

        /** Latest non-self foreground package observed on the active cover display, or `null`. */
        fun currentForegroundPackage(): String? = latestForegroundPackage

        /** Latest foreground component class name paired with [currentForegroundPackage], or `null`. */
        fun currentForegroundClassName(): String? = latestForegroundClassName

        /** Snapshot of live interactive windows on a cover display from [inspectDisplayWindowState]. */
        internal data class CoverWindowSnapshot(
            val hasLaunchedAppWindow: Boolean,
            val hasAnyUserAppWindowAboveHome: Boolean = false,
            val activeUserAppPackage: String? = null,
            val coverHomePackage: String?,
            val coverHomeClassName: String?
        )

        /** Returns `true` for third-party / user-launched apps and active Recents/Overview surfaces. */
        internal fun isUserForegroundApplicationPackage(packageName: String, className: String? = null): Boolean {
            if (CoverHomeSurfaceMatcher.isRecentsSurfaceClassName(className)) {
                return true
            }
            if (CoverHomeSurfaceMatcher.isCoverHomeDestination(packageName, className)) {
                return false
            }
            return NON_USER_APP_PREFIXES.none { prefix ->
                packageName.startsWith(prefix)
            }
        }

        /**
         * Inspects live interactive windows on [displayId] (API 30+) to detect whether [launchedPackage]
         * or any other user app window remains visible above cover home, or whether the native cover
         * home (`SubHomeActivity`, launcher, or SystemUI cover home window) is on top.
         */
        internal fun inspectDisplayWindowState(
            displayId: Int,
            launchedPackage: String?
        ): CoverWindowSnapshot? {
            // AccessibilityService.getWindowsOnAllDisplays() requires Android 11 (API 30).
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            val service = activeServiceRef?.get() ?: return null
            val windows = runCatching { service.windowsOnAllDisplays.get(displayId) }.getOrNull()
                ?: return null

            val selfPackage = service.packageName
            var hasLaunchedAppWindow = false
            var highestUserAppLayer = Int.MIN_VALUE
            var activeUserAppPackage: String? = null
            var coverHomePackage: String? = null
            var coverHomeClassName: String? = null
            var highestHomeLayer = Int.MIN_VALUE

            for (window in windows) {
                if (window.displayId != displayId) continue
                val windowPkg = runCatching { window.root?.packageName?.toString()?.trim() }
                    .getOrNull()
                    ?.takeUnless { it.isEmpty() }
                    ?: continue
                // Ignore our own overlay windows so they never count as foreground targets.
                if (windowPkg == selfPackage) continue

                val windowTitle = runCatching { window.title?.toString()?.trim() }.getOrNull()

                if (launchedPackage != null &&
                    windowPkg == launchedPackage &&
                    window.type == AccessibilityWindowInfo.TYPE_APPLICATION
                ) {
                    hasLaunchedAppWindow = true
                }

                // Track any non-home, non-system user application window (e.g., permission dialogs, OAuth tabs, chained activities).
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    isUserForegroundApplicationPackage(windowPkg, windowTitle) &&
                    window.layer > highestUserAppLayer
                ) {
                    highestUserAppLayer = window.layer
                    activeUserAppPackage = windowPkg
                }

                // Track the top-most (highest z-order layer) cover-home application or named SystemUI cover window.
                val isCoverHomeWindowType = window.type == AccessibilityWindowInfo.TYPE_APPLICATION ||
                    (window.type == AccessibilityWindowInfo.TYPE_SYSTEM &&
                        (windowPkg == "com.android.systemui" || windowPkg == "com.samsung.android.app.aodservice") &&
                        !CoverHomeSurfaceMatcher.isKnownTransientSystemUiOverlayClass(windowTitle))
                if (isCoverHomeWindowType && window.layer > highestHomeLayer) {
                    val isNamedCoverSystemUiTitle = windowTitle.equals("System UI", ignoreCase = true) ||
                        (windowTitle != null && (
                            CoverHomeSurfaceMatcher.isSubscreenHomeClassName(windowTitle) ||
                                windowTitle.contains("SubScreen", ignoreCase = true) ||
                                windowTitle.contains("CoverScreen", ignoreCase = true)
                            ))
                    val isSubscreenSystemUiActivity = windowPkg == "com.android.systemui" &&
                        if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                            windowTitle.isNullOrEmpty() || isNamedCoverSystemUiTitle
                        } else {
                            // For TYPE_SYSTEM windows, require an explicit cover/SystemUI home title so generic navbars don't bypass debounce.
                            isNamedCoverSystemUiTitle
                        }
                    val isCoverHome = isSubscreenSystemUiActivity ||
                        (windowPkg == "com.samsung.android.app.aodservice" &&
                            !CoverHomeSurfaceMatcher.isKnownTransientSystemUiOverlayClass(windowTitle)) ||
                        CoverHomeSurfaceMatcher.isCoverHomeDestination(
                            packageName = windowPkg,
                            className = windowTitle
                        )
                    if (isCoverHome) {
                        highestHomeLayer = window.layer
                        coverHomePackage = windowPkg
                        coverHomeClassName = if (isSubscreenSystemUiActivity) {
                            "com.android.systemui.subscreen.SubHomeActivity"
                        } else {
                            windowTitle
                        }
                    }
                }
            }

            val hasAnyUserAppWindowAboveHome = activeUserAppPackage != null &&
                highestUserAppLayer > highestHomeLayer

            return CoverWindowSnapshot(
                hasLaunchedAppWindow = hasLaunchedAppWindow,
                hasAnyUserAppWindowAboveHome = hasAnyUserAppWindowAboveHome,
                activeUserAppPackage = activeUserAppPackage,
                coverHomePackage = coverHomePackage,
                coverHomeClassName = coverHomeClassName
            )
        }

        /**
         * Returns milliseconds elapsed since the most recent foreground window update
         * (or [Long.MAX_VALUE] if no foreground event has been observed yet).
         */
        fun currentForegroundPackageEventAgeMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long {
            val eventElapsedMs = latestForegroundEventElapsedMs
            if (eventElapsedMs <= 0L) return Long.MAX_VALUE
            return (nowElapsedMs - eventElapsedMs).coerceAtLeast(0L)
        }

        // ---- Launcher host static handoff --------------------------------
        //
        // Mirrors the CoverInputSessionManager.bindService / unbindService
        // pattern used by CoverScreenInputInjectionEngine so that
        // ForegroundService can hand a LauncherOverlayHost to whichever
        // CoverAccessibilityService instance the system happens to have
        // running — including across AS disable/enable cycles when the
        // instance is destroyed and later recreated.
        //
        // Invariants:
        //  - `pendingHost` survives AS re-creation. Callers set it once via
        //    attachLauncherHost(...) and it is re-applied to each fresh AS
        //    instance from onServiceConnected().
        //  - detachLauncherHost() clears BOTH pending and any live instance.
        //  - The AS instance mirrors the pending value into its own
        //    `launcherHost` field for read access.
        //
        // Phase 2 scope: state plumbing only. No view work.

        /** Weak reference to the live bound [CoverAccessibilityService] so static callers avoid leaking the Service. */
        @Volatile
        private var activeServiceRef: WeakReference<CoverAccessibilityService>? = null

        /** Stashed [LauncherOverlayHost] surviving unbind/rebind cycles until `onServiceConnected` installs it. */
        @Volatile
        private var pendingLauncherHost: LauncherOverlayHost? = null

        /**
         * Attach a [LauncherOverlayHost] to the accessibility service. Safe
         * to call before the AS is connected — the host is stashed and
         * re-applied on the next `onServiceConnected`. Replacing an existing
         * host is allowed and takes effect immediately on the live instance.
         */
        fun attachLauncherHost(host: LauncherOverlayHost) {
            pendingLauncherHost = host
            activeServiceRef?.get()?.installLauncherHost(host)
        }

        /**
         * Release any attached [LauncherOverlayHost]. Clears both the
         * pending handoff and the currently-connected instance's reference.
         */
        fun detachLauncherHost() {
            pendingLauncherHost = null
            activeServiceRef?.get()?.releaseLauncherHost()
        }

        /**
         * Currently-installed host on the live AS instance, or `null` if
         * no AS is connected. Prefer this over reading [pendingLauncherHost]
         * — a non-null pending value with no live AS means the host has not
         * yet been installed anywhere.
         */
        fun currentLauncherHost(): LauncherOverlayHost? =
            activeServiceRef?.get()?.launcherHost

        // ---- Launcher hosting bridges ------------------------------------
        //
        // No-op when the launcher accessibility service is not bound.

        /** @see CoverAccessibilityService.showLauncher */
        fun showLauncherOnActiveService(display: Display, forceReattach: Boolean): Boolean =
            activeServiceRef?.get()?.showLauncher(display, forceReattach) ?: false

        /** @see CoverAccessibilityService.hideLauncher */
        fun hideLauncherOnActiveService(reason: String) {
            activeServiceRef?.get()?.hideLauncher(reason)
        }

        /** @see CoverAccessibilityService.setLauncherTouchable */
        fun setLauncherTouchableOnActiveService(touchable: Boolean) {
            activeServiceRef?.get()?.setLauncherTouchable(touchable)
        }

        /** @see CoverAccessibilityService.launcherActiveDisplayId */
        fun launcherActiveDisplayIdOnActiveService(): Int? =
            activeServiceRef?.get()?.launcherActiveDisplayId()

        /** Records the active cover [displayId] so suppression survives screen-off surface detachments. */
        fun updateMonitoredCoverDisplayIdOnActiveService(displayId: Int?) {
            activeServiceRef?.get()?.updateMonitoredCoverDisplayId(displayId)
        }

        /** @see CoverAccessibilityService.isLauncherAttached */
        fun isLauncherAttachedOnActiveService(): Boolean =
            activeServiceRef?.get()?.isLauncherAttached() == true

        /** Attempts to open native Android/One UI Recents via `GLOBAL_ACTION_RECENTS`. */
        fun performGlobalRecentsOnActiveService(): Boolean =
            activeServiceRef?.get()?.performGlobalRecents() == true

        /**
         * Launches [launchIntent] with optional [launchOptions] (such as target display ID)
         * from the bound [AccessibilityService] context, catching missing or restricted targets.
         */
        fun startActivityFromLauncherService(launchIntent: Intent, launchOptions: Bundle?): Boolean {
            val service = activeServiceRef?.get() ?: return false
            return try {
                service.startActivity(launchIntent, launchOptions)
                true
            } catch (error: ActivityNotFoundException) {
                Log.w(LOG_TAG, "Launcher accessibility-service launch target missing", error)
                false
            } catch (error: SecurityException) {
                Log.w(LOG_TAG, "Launcher accessibility-service launch failed", error)
                false
            }
        }
    }

    private inline fun logDebug(message: () -> String) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, message())
        }
    }

    // Last package logged on TYPE_WINDOW_STATE_CHANGED so we only log actual foreground transitions.
    private var lastWindowPackage: String? = null
    // ID of the last accessibility swipe gesture processed, paired with [lastGestureAtMs] for debouncing.
    private var lastGestureId: Int? = null
    // Monotonic uptime timestamp when [lastGestureId] was seen.
    private var lastGestureAtMs: Long = 0L
    // Monotonic uptime timestamp of the last `performGlobalAction` call for throttling rapid presses.
    private var lastActionAtMs: Long = 0L
    // Native cover-home package currently waiting out [RECLAIM_STABILITY_DEBOUNCE_MS] before reclaiming.
    private var reclaimCandidatePackage: String? = null
    // Monotonic uptime timestamp when [reclaimCandidatePackage] first became top-most.
    private var reclaimCandidateSinceElapsedMs: Long = 0L
    // Most recent third-party user app observed on the cover display (used to guard against premature reclaim).
    private var lastKnownUserAppPackage: String? = null
    // Monotonic uptime timestamp when [lastKnownUserAppPackage] was last observed.
    private var lastKnownUserAppElapsedMs: Long = 0L
    // Main-thread handler hosting the delayed reclaim stability verification callback.
    private val reclaimHandler = Handler(Looper.getMainLooper())
    // Pending delayed runnable verifying via `inspectDisplayWindowState` that the user app actually exited.
    private var pendingReclaimCheck: Runnable? = null

    /**
     * Currently-installed launcher host, or `null` if no
     * [LauncherOverlayHost] has been attached. Read via
     * [CoverAccessibilityService.currentLauncherHost].
     */
    @Volatile
    internal var launcherHost: LauncherOverlayHost? = null
        private set

    /**
     * Phase 3: `TYPE_ACCESSIBILITY_OVERLAY` compose surface owned by
     * this AS instance. Non-null while [showLauncher] has successfully
     * attached the launcher grid to a display. Cleared by
     * [hideLauncher], [releaseLauncherHost], [onUnbind], and [onDestroy].
     */
    private var launcherSurface: CoverComposeSurface? = null

    /**
     * Last non-default cover [Display.displayId] seen by this service. Retained across
     * `ACTION_SCREEN_OFF` surface detachments so [onAccessibilityEvent] does not become
     * blind to cover-display events if the screen wakes while still suppressed.
     */
    @Volatile
    private var monitoredCoverDisplayId: Int? = null

    /**
     * Narrow bottom-edge gesture strip (`TYPE_ACCESSIBILITY_OVERLAY`) mounted on the cover
     * display while a launched app owns the foreground, giving the user Swipe-Up Home,
     * Swipe-Up-Hold / Right Recents, and Swipe-Left Back navigation.
     */
    private var gestureBarSurface: CoverComposeSurface? = null

    /**
     * Called by Android when the accessibility service binds and receives its system window token.
     *
     * Registers [activeServiceRef], resets stale foreground tracking, re-installs any
     * [pendingLauncherHost] supplied by [ForegroundService], and triggers overlay attachment
     * if a host is already present.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        latestForegroundPackage = null
        latestForegroundEventElapsedMs = 0L
        activeServiceRef = WeakReference(this)
        // Re-apply any host that was attached while the service was
        // between instances (AS disabled/enabled, app updated, etc.).
        pendingLauncherHost?.let { installLauncherHost(it) }
        logDebug { "Service connected; launcherHost=${if (launcherHost != null) "attached" else "none"}" }
        if (launcherHost != null) ForegroundService.onLauncherAccessibilityConnected()
    }

    /**
     * Called when the system unbinds this accessibility service instance.
     *
     * Tears down the live surface and clears [activeServiceRef], while intentionally keeping
     * [pendingLauncherHost] so toggling the service back on reconnects seamlessly.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        // Live instance is going away. Clear our reference and the static
        // pointer — but keep `pendingLauncherHost` intact so the next
        // `onServiceConnected` re-installs it. That is what allows the AS
        // to be toggled off then back on in system settings without
        // forcing a ForegroundService restart.
        releaseLauncherHost()
        resetReclaimCandidate()
        if (activeServiceRef?.get() === this) {
            activeServiceRef?.clear()
            activeServiceRef = null
        }
        ForegroundService.onLauncherAccessibilityDisconnected()
        return super.onUnbind(intent)
    }

    /** Final cleanup releasing any hosted Compose surface, pending reclaim timers, and static reference. */
    override fun onDestroy() {
        releaseLauncherHost()
        resetReclaimCandidate()
        if (activeServiceRef?.get() === this) {
            activeServiceRef?.clear()
            activeServiceRef = null
        }
        super.onDestroy()
    }

    /**
     * Install a [LauncherOverlayHost] on this live instance.
     *
     * If a **different** host is already installed and a
     * [launcherSurface] is live, the previous surface is torn down
     * first — its Compose tree subscribed to the outgoing host's
     * flows and would otherwise render stale data on the next frame.
     * A caller re-installing the same host instance is a no-op on
     * the surface (used by the AS-reconnect path via
     * [onServiceConnected]).
     */
    internal fun installLauncherHost(host: LauncherOverlayHost) {
        if (launcherHost != null && launcherHost !== host) {
            detachLauncherSurface(reason = "install_launcher_host_replaced")
        }
        launcherHost = host
        ForegroundService.onLauncherHostChanged()
    }

    /** Phase 2: pure state release. Phase 3 also tears down the compose surface. */
    internal fun releaseLauncherHost() {
        // Tear down any Phase-3 hosted launcher surface first so
        // subsequent host handoffs start clean. Safe to call while
        // nothing is attached.
        monitoredCoverDisplayId = null
        detachLauncherSurface(reason = "release_launcher_host")
        launcherHost = null
        ForegroundService.onLauncherHostChanged()
    }

    // ---- Phase 3: launcher hosting API -----------------------------------
    //
    // See `docs/architecture/Overlay-architecture-shift-plan.md` §5.2 / §7.
    // The foreground service invokes these methods through OverlayWindowController.

    /**
     * Build and attach a [CoverComposeSurface] hosting [CoverAppGridOverlay]
     * on [display] via `TYPE_ACCESSIBILITY_OVERLAY`. No-op returning
     * `false` when no [LauncherOverlayHost] has been installed —
     * production code guarantees the [ForegroundService] performs the
     * handoff on `onCreate` before requesting any show, but tests
     * exercise the un-hosted branch to prove the guard.
     *
     * Idempotent for the same display: repeated calls without
     * [forceReattach] simply re-enable touch on the existing surface
     * and return `true`.
     */
    fun showLauncher(display: Display, forceReattach: Boolean): Boolean {
        val host = launcherHost ?: run {
            logDebug { "showLauncher no-op: no LauncherOverlayHost attached" }
            return false
        }
        if (display.displayId != Display.DEFAULT_DISPLAY) {
            monitoredCoverDisplayId = display.displayId
        }

        val existingSurface = launcherSurface
        if (existingSurface != null &&
            existingSurface.isAttached() &&
            !forceReattach &&
            existingSurface.activeDisplayId() == display.displayId
        ) {
            setLauncherTouchable(true)
            return true
        }

        // Different display OR force reattach OR stale surface — tear down
        // whatever we have before building a fresh one.
        detachLauncherSurface(reason = "show_launcher_reattach")

        val surface = CoverComposeSurface(
            hostContext = this,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            logTag = LAUNCHER_SURFACE_LOG_TAG
        )
        val attached = surface.attach(display) {
            val overlayScope = rememberCoroutineScope()
            val isDeviceLocked by host.deviceLockState
                .collectAsStateWithLifecycle(initialValue = false)
            val launcherSettings by host.settings
                .collectAsStateWithLifecycle(initialValue = LauncherSettings())
            val recentAppPackages by host.recentAppPackages
                .collectAsStateWithLifecycle(initialValue = emptyList())
            val isRecentsVisible by host.isRecentsSheetVisible
                .collectAsStateWithLifecycle(initialValue = false)

            CoverOSTheme(
                themePreference = launcherSettings.themePreference,
                accentColor = launcherSettings.accentColor,
                panelCornerRadiusDp = launcherSettings.panelCornerRadiusDp
            ) {
                CoverAppGridOverlay(
                    repository = host.appRepository,
                    onAppSelected = { appModel -> host.onAppSelected(appModel) },
                    isDeviceLocked = isDeviceLocked,
                    dockPackageSlots = launcherSettings.dockPackages,
                    dockSlotFourAllApps = launcherSettings.dockSlotFourAllApps,
                    isDockVisible = launcherSettings.isDockVisible,
                    wallpaperUri = launcherSettings.wallpaperUri,
                    wallpaperScaleMode = launcherSettings.wallpaperScaleMode,
                    wallpaperDimAmount = launcherSettings.wallpaperDimAmount,
                    wallpaperBlurRadiusDp = launcherSettings.wallpaperBlurRadiusDp,
                    wallpaperCropX = launcherSettings.wallpaperCropX,
                    wallpaperCropY = launcherSettings.wallpaperCropY,
                    wallpaperZoom = launcherSettings.wallpaperZoom,
                    wallpaperRotation = launcherSettings.wallpaperRotation,
                    keyboardStrategy = launcherSettings.keyboardStrategy,
                    widgetIds = launcherSettings.widgetIds,
                    widgetHostManager = host.widgetHostManager,
                    onRemoveWidget = { id ->
                        host.widgetHostManager?.deleteAppWidgetId(id)
                        overlayScope.launch {
                            runCatching {
                                host.launcherSettingsStore.removeWidgetId(id)
                            }
                        }
                    },
                    onAddWidgetRequest = { host.onAddWidgetRequest() },
                    onRequestUnlock = { host.onRequestUnlock() },
                    onRequestScreenOff = { requestScreenOff() },
                    onRequestSystemShade = { requestSystemShade() },
                    onOpenCustomize = { host.onOpenCustomize() },
                    recentAppPackages = recentAppPackages,
                    isRecentsVisible = isRecentsVisible,
                    onRequestRecents = { host.onRequestRecents() },
                    onDismissRecentApp = { pkg -> host.onDismissRecentApp(pkg) },
                    onClearAllRecentApps = { host.onClearAllRecentApps() },
                    onDismissRecents = { host.onSetRecentsSheetVisible(false) },
                    onKeyboardStrategyChanged = { nextStrategy ->
                        overlayScope.launch {
                            runCatching {
                                host.persistKeyboardStrategy(nextStrategy)
                            }.onFailure { error ->
                                Log.w(
                                    LAUNCHER_SURFACE_LOG_TAG,
                                    "Unable to persist keyboard strategy=$nextStrategy: ${error.message}"
                                )
                            }
                        }
                    }
                )
            }
        }

        if (!attached) {
            Log.w(LAUNCHER_SURFACE_LOG_TAG, "Failed to attach launcher surface to display=${display.displayId}")
            return false
        }

        launcherSurface = surface
        setLauncherTouchable(true)
        return true
    }

    /**
     * Detach the launcher surface if attached. [reason] is recorded for
     * logging and matches the vocabulary used by
     * [ForegroundService]'s transition markers ("reclaim", "remove_overlay",
     * "controller_destroy", …).
     */
    fun hideLauncher(reason: String) {
        detachLauncherSurface(reason = reason)
    }

    /** Suppress touch and visibility while a cover app takes foreground focus. */
    fun setLauncherTouchable(touchable: Boolean) {
        launcherSurface?.setTouchable(touchable)
        if (touchable) {
            detachGestureBar(reason = "launcher_touchable")
        } else {
            attachGestureBarIfNeeded()
        }
    }

    /** Dispatches Android's native `GLOBAL_ACTION_RECENTS` on this accessibility service. */
    internal fun performGlobalRecents(): Boolean {
        val dispatched = runCatching { performGlobalAction(GLOBAL_ACTION_RECENTS) }.getOrDefault(false)
        logDebug { "Global ACTION_RECENTS dispatched=$dispatched" }
        return dispatched
    }

    /** Returns the [Display] ID currently hosting [launcherSurface], or `null` if detached. */
    fun launcherActiveDisplayId(): Int? = launcherSurface?.activeDisplayId()

    /** Returns the active surface display ID, or the last monitored cover display ID across screen-off detaches. */
    fun effectiveCoverDisplayId(): Int? = launcherSurface?.activeDisplayId() ?: monitoredCoverDisplayId

    /** Records a non-default cover [displayId] so accessibility tracking persists while screen-off removes the surface. */
    fun updateMonitoredCoverDisplayId(displayId: Int?) {
        monitoredCoverDisplayId = displayId?.takeIf { it != Display.DEFAULT_DISPLAY }
    }

    /** Turns the screen off and locks the device, like a power-key press. */
    private fun requestScreenOff() {
        if (shouldThrottleGlobalAction()) return
        val result = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        logDebug { "Double tap screen off result=$result" }
    }

    /** Opens the native OS notification/quick-settings shade. */
    private fun requestSystemShade() {
        if (shouldThrottleGlobalAction()) return
        val result = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        logDebug { "System shade swipe down result=$result" }
    }

    /** Returns `true` when [launcherSurface] is actively attached to its display window. */
    fun isLauncherAttached(): Boolean = launcherSurface?.isAttached() == true

    /** Safely detaches [launcherSurface] and any companion gesture bar, clearing local references. */
    private fun detachLauncherSurface(reason: String) {
        detachGestureBar(reason = reason)
        val surface = launcherSurface ?: return
        launcherSurface = null
        runCatching { surface.detach() }
            .onFailure { error ->
                Log.w(
                    LAUNCHER_SURFACE_LOG_TAG,
                    "Detach failed reason=$reason: ${error.message}"
                )
            }
        logDebug { "Launcher surface detached reason=$reason" }
    }

    /** Mounts a slim bottom navigation gesture strip on non-default cover displays while suppressed. */
    private fun attachGestureBarIfNeeded() {
        val coverDisplayId = launcherSurface?.activeDisplayId()
            ?.takeIf { it != Display.DEFAULT_DISPLAY }
            ?: return
        val existingBar = gestureBarSurface
        if (existingBar != null &&
            existingBar.isAttached() &&
            existingBar.activeDisplayId() == coverDisplayId
        ) {
            return
        }
        detachGestureBar(reason = "reattach_gesture_bar")

        val coverDisplay = (getSystemService(DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(coverDisplayId)
            ?.takeIf { it.isValid }
            ?: return
        val barHeightPx = (BOTTOM_BAR_HEIGHT.value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

        val displayDensity = createDisplayContext(coverDisplay).resources.displayMetrics.density
        val config = com.tyejaedon.coverscreenos.overlay.surface.CoverSurfaceWindowConfig(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = barHeightPx,
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            extraFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        val barSurface = CoverComposeSurface(
            hostContext = this,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            logTag = LAUNCHER_SURFACE_LOG_TAG
        )
        val attached = barSurface.attach(display = coverDisplay, windowConfig = config) {
            CoverBottomGestureBar(
                onHomeGesture = { ForegroundService.requestOverlayReclaim(reason = "gesture_bar_home") },
                onRecentsGesture = { ForegroundService.requestCoverRecents() },
                onBackGesture = {
                    if (!shouldThrottleGlobalAction()) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                }
            )
        }
        if (attached) {
            gestureBarSurface = barSurface
        }
    }

    /** Detaches the bottom gesture strip if currently mounted. */
    private fun detachGestureBar(reason: String) {
        val bar = gestureBarSurface ?: return
        gestureBarSurface = null
        runCatching { bar.detach() }
            .onFailure { error ->
                Log.w(LAUNCHER_SURFACE_LOG_TAG, "Gesture bar detach failed reason=$reason: ${error.message}")
            }
    }

    /**
     * Monitors window transitions on the active cover display to feed suppression and
     * overlay-reclaim policies.
     *
     * Handles:
     * 1. Filtering to `TYPE_WINDOW_STATE_CHANGED` / `TYPE_WINDOWS_CHANGED` on [effectiveCoverDisplayId].
     * 2. Updating [latestForegroundPackage] while ignoring our own package so Cover-Screen-OS
     *    overlays (keyboard, media panel, launcher) never self-trigger overlay resumption.
     * 3. Tracking recently active user apps (`lastKnownUserAppPackage`) to guard against premature reclaim.
     * 4. Delegating incoming-call UI detection to [ForegroundService.requestIncomingCallPassthrough].
     * 5. Running a two-stage debounced reclaim ([RECLAIM_STABILITY_DEBOUNCE_MS] +
     *    [scheduleDelayedReclaimCheck]) when a native cover-home destination stabilizes on top.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val isWindowEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (!isWindowEvent) return
        if (event.displayId != effectiveCoverDisplayId()) return

        val foregroundPackage = event.packageName
            ?.toString()
            ?.trim()
            .takeUnless { it.isNullOrEmpty() }
            ?: return
        val foregroundClassName = event.className
            ?.toString()
            ?.trim()
            ?.takeUnless { it.isEmpty() }

        // Never let window events emitted by our own overlay surfaces refresh the
        // shared foreground-package state read by ForegroundService's resume
        // poller. Doing so would treat every keyboard attach on the cover
        // display as "user is back on our launcher", un-suppressing the cover
        // launcher overlay on top of the app the user just launched.
        if (foregroundPackage == this.packageName) return

        val nowElapsedMs = SystemClock.elapsedRealtime()
        val isSameForegroundPackage = latestForegroundPackage == foregroundPackage &&
            (foregroundClassName == null || latestForegroundClassName == foregroundClassName)
        val shouldRefreshForegroundTimestamp = !isSameForegroundPackage ||
            (nowElapsedMs - latestForegroundEventElapsedMs) >= FOREGROUND_EVENT_REFRESH_MIN_INTERVAL_MS
        if (shouldRefreshForegroundTimestamp) {
            latestForegroundPackage = foregroundPackage
            latestForegroundClassName = foregroundClassName
            latestForegroundEventElapsedMs = nowElapsedMs
        }

        val eventTypeName = eventTypeToName(event.eventType)
        if (shouldTrackAsUserForegroundApp(foregroundPackage, foregroundClassName)) {
            lastKnownUserAppPackage = foregroundPackage
            lastKnownUserAppElapsedMs = nowElapsedMs
            if (!CoverHomeSurfaceMatcher.isRecentsSurfaceClassName(foregroundClassName)) {
                ForegroundService.recordRecentCoverApp(foregroundPackage)
            }
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            logReclaimDecision(
                eventTypeName = eventTypeName,
                packageName = foregroundPackage,
                decision = "skip_non_state_event"
            )
            return
        }

        if (foregroundPackage != lastWindowPackage) {
            lastWindowPackage = foregroundPackage
            logDebug { "Window changed: $foregroundPackage/$foregroundClassName" }
        }

        if (shouldAllowIncomingCallSurface(foregroundPackage, foregroundClassName)) {
            logReclaimDecision(eventTypeName, foregroundPackage, "incoming_call_passthrough")
            ForegroundService.requestIncomingCallPassthrough(foregroundPackage)
            return
        }

        if (!shouldRequestOverlayReclaim(foregroundPackage, foregroundClassName)) {
            if (isTransientSystemUiPackage(foregroundPackage, foregroundClassName)) {
                logReclaimDecision(eventTypeName, foregroundPackage, "skip_transient_system_ui", "class=$foregroundClassName")
            }
            resetReclaimCandidate()
            return
        }

        if (reclaimCandidatePackage != foregroundPackage) {
            reclaimCandidatePackage = foregroundPackage
            reclaimCandidateSinceElapsedMs = nowElapsedMs
            logReclaimDecision(eventTypeName, foregroundPackage, "candidate_started")
            scheduleDelayedReclaimCheck(foregroundPackage, foregroundClassName)
            return
        }

        val stableForMs = nowElapsedMs - reclaimCandidateSinceElapsedMs
        if (stableForMs < RECLAIM_STABILITY_DEBOUNCE_MS) {
            logReclaimDecision(
                eventTypeName = eventTypeName,
                packageName = foregroundPackage,
                decision = "waiting_stability",
                detail = "stableForMs=$stableForMs requiredMs=$RECLAIM_STABILITY_DEBOUNCE_MS"
            )
            return
        }

        val userAppAgeMs = if (lastKnownUserAppElapsedMs > 0L) {
            (nowElapsedMs - lastKnownUserAppElapsedMs).coerceAtLeast(0L)
        } else {
            Long.MAX_VALUE
        }
        if (lastKnownUserAppPackage != null && userAppAgeMs < RECENT_USER_APP_GUARD_MS) {
            logReclaimDecision(
                eventTypeName = eventTypeName,
                packageName = foregroundPackage,
                decision = "blocked_recent_user_app",
                detail = "userApp=$lastKnownUserAppPackage ageMs=$userAppAgeMs guardMs=$RECENT_USER_APP_GUARD_MS"
            )
            return
        }

        logReclaimDecision(
            eventTypeName = eventTypeName,
            packageName = foregroundPackage,
            decision = "trigger_reclaim",
            detail = "stableForMs=$stableForMs"
        )
        ForegroundService.requestOverlayReclaim(reason = foregroundPackage)
    }

    /**
     * Routes accessibility swipe gestures to system navigation (`GLOBAL_ACTION_BACK`,
     * `HOME`, `RECENTS`, `NOTIFICATIONS`) while the cover overlay is active, applying
     * gesture debounce and action throttling.
     */
    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean {
        if (!ForegroundService.isOverlayActive) return false

        val gestureId = gestureEvent.gestureId
        if (shouldDebounceGesture(gestureId)) return false

        if (gestureId == GESTURE_SWIPE_RIGHT) {
            if (shouldThrottleGlobalAction()) return false
            return ForegroundService.requestCoverRecents()
        }

        val globalAction = when (gestureId) {
            GESTURE_SWIPE_LEFT -> GLOBAL_ACTION_BACK
            GESTURE_SWIPE_UP -> GLOBAL_ACTION_HOME
            GESTURE_SWIPE_DOWN -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return false
        }
        if (shouldThrottleGlobalAction()) return false

        val result = performGlobalAction(globalAction)
        logDebug { "Gesture=$gestureId action=$globalAction result=$result" }
        return result
    }

    /**
     * Intercepts hardware `KEYCODE_BACK` presses when [ForegroundService.isOverlayActive]
     * is `true` so back navigation is routed through [handleHardwareBackPress] rather than
     * leaking to an underlying window.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!ForegroundService.isOverlayActive) return super.onKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            return handleHardwareBackPress()
        }

        return super.onKeyEvent(event)
    }

    /**
     * Dispatches [GLOBAL_ACTION_BACK] (throttled) and falls back to requesting overlay
     * reclaim if the system rejects or does not support global back.
     */
    private fun handleHardwareBackPress(): Boolean {
        if (shouldThrottleGlobalAction()) {
            logDebug { "Hardware back press throttled while overlay is active" }
            return true
        }

        val result = performGlobalAction(GLOBAL_ACTION_BACK)
        if (!result) {
            logDebug { "Global ACTION_BACK unavailable; reclaiming overlay for hardware back fallback" }
            ForegroundService.requestOverlayReclaim(reason = "hardware_back_fallback")
        } else {
            logDebug { "Hardware back routed through GLOBAL_ACTION_BACK result=$result" }
        }
        return true
    }

    /** Called by the system when accessibility feedback/processing is interrupted. */
    override fun onInterrupt() {
        logDebug { "Accessibility service interrupted" }
    }

    /** Prevents duplicate global actions when Android dispatches rapid repeated gesture callbacks. */
    private fun shouldDebounceGesture(gestureId: Int): Boolean {
        val now = SystemClock.elapsedRealtime()
        val isRepeated = lastGestureId == gestureId && (now - lastGestureAtMs) < GESTURE_DEBOUNCE_MS
        lastGestureId = gestureId
        lastGestureAtMs = now
        if (isRepeated) {
            logDebug { "Gesture debounced id=$gestureId" }
        }
        return isRepeated
    }

    /** Rate-limits `performGlobalAction` calls to at most once per [ACTION_THROTTLE_MS]. */
    private fun shouldThrottleGlobalAction(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val throttled = (now - lastActionAtMs) < ACTION_THROTTLE_MS
        if (!throttled) {
            lastActionAtMs = now
        } else {
            logDebug { "Global action throttled" }
        }
        return throttled
    }

    /** Returns `true` if [packageName]/[className] identifies a native home surface eligible to reclaim the overlay. */
    private fun shouldRequestOverlayReclaim(packageName: String, className: String? = null): Boolean {
        if (CoverHomeSurfaceMatcher.isRecentsSurfaceClassName(className)) {
            return false
        }
        return CoverHomeSurfaceMatcher.isCoverHomeDestination(packageName, className) ||
            OVERLAY_RECLAIM_PACKAGE_PREFIXES.any { prefix ->
                packageName.startsWith(prefix)
            }
    }

    /** Returns `true` for third-party / user-launched apps (excluding SystemUI, AOD, launcher, and self). */
    private fun shouldTrackAsUserForegroundApp(packageName: String, className: String? = null): Boolean {
        return isUserForegroundApplicationPackage(packageName, className)
    }

    /** Returns `true` for transient SystemUI or AOD surfaces that are not cover-home destinations. */
    private fun isTransientSystemUiPackage(packageName: String, className: String? = null): Boolean {
        if (CoverHomeSurfaceMatcher.isCoverHomeDestination(packageName, className)) {
            return false
        }
        return TRANSIENT_SYSTEM_UI_PREFIXES.any { prefix ->
            packageName.startsWith(prefix)
        }
    }

    /**
     * Schedules a post-debounce check after [RECLAIM_STABILITY_DEBOUNCE_MS] to verify via
     * [inspectDisplayWindowState] that no launched user app or secondary user dialog remains visible before reclaiming.
     */
    private fun scheduleDelayedReclaimCheck(candidatePackage: String, candidateClassName: String?) {
        pendingReclaimCheck?.let { reclaimHandler.removeCallbacks(it) }
        val targetDisplayId = effectiveCoverDisplayId() ?: Display.DEFAULT_DISPLAY
        val runnable = Runnable {
            pendingReclaimCheck = null
            if (reclaimCandidatePackage != candidatePackage) return@Runnable
            val snapshot = inspectDisplayWindowState(
                displayId = targetDisplayId,
                launchedPackage = lastKnownUserAppPackage
            )
            if (snapshot?.hasLaunchedAppWindow == true || snapshot?.hasAnyUserAppWindowAboveHome == true) {
                logReclaimDecision(
                    eventTypeName = "DELAYED_RECLAIM_CHECK",
                    packageName = candidatePackage,
                    decision = "aborted_user_app_still_visible",
                    detail = "activeUserApp=${snapshot.activeUserAppPackage}"
                )
                resetReclaimCandidate()
                return@Runnable
            }
            if (!shouldRequestOverlayReclaim(candidatePackage, candidateClassName)) return@Runnable
            logReclaimDecision(
                eventTypeName = "DELAYED_RECLAIM_CHECK",
                packageName = candidatePackage,
                decision = "trigger_reclaim"
            )
            ForegroundService.requestOverlayReclaim(reason = candidatePackage)
        }
        pendingReclaimCheck = runnable
        reclaimHandler.postDelayed(runnable, RECLAIM_STABILITY_DEBOUNCE_MS)
    }

    /** Cancels any pending [scheduleDelayedReclaimCheck] runnable and clears reclaim candidate state. */
    private fun resetReclaimCandidate() {
        pendingReclaimCheck?.let { reclaimHandler.removeCallbacks(it) }
        pendingReclaimCheck = null
        reclaimCandidatePackage = null
        reclaimCandidateSinceElapsedMs = 0L
    }

    private fun eventTypeToName(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "TYPE_WINDOWS_CHANGED"
            else -> "TYPE_$eventType"
        }
    }

    private fun logReclaimDecision(
        eventTypeName: String,
        packageName: String,
        decision: String,
        detail: String? = null
    ) {
        if (!Log.isLoggable(OVERLAY_RECLAIM_LOG_TAG, Log.DEBUG)) return

        val candidatePackage = reclaimCandidatePackage ?: "<none>"
        val candidateAgeMs = if (reclaimCandidateSinceElapsedMs > 0L) {
            (SystemClock.elapsedRealtime() - reclaimCandidateSinceElapsedMs).coerceAtLeast(0L)
        } else {
            -1L
        }
        val userApp = lastKnownUserAppPackage ?: "<none>"
        val userAppAgeMs = if (lastKnownUserAppElapsedMs > 0L) {
            (SystemClock.elapsedRealtime() - lastKnownUserAppElapsedMs).coerceAtLeast(0L)
        } else {
            -1L
        }

        val detailSuffix = detail?.let { " $it" } ?: ""
        Log.d(
            OVERLAY_RECLAIM_LOG_TAG,
            "event=$eventTypeName package=$packageName decision=$decision candidate=$candidatePackage candidateAgeMs=$candidateAgeMs userApp=$userApp userAppAgeMs=$userAppAgeMs$detailSuffix"
        )
    }

    /** Returns `true` if [packageName]/[className] matches a known dialer or incoming-call surface. */
    private fun shouldAllowIncomingCallSurface(packageName: String, className: String? = null): Boolean {
        return CallPackageMatchers.isIncomingCallWindow(
            packageName = packageName,
            className = className,
            isCallNotificationActive = ForegroundService.isCallNotificationActive()
        )
    }
}
