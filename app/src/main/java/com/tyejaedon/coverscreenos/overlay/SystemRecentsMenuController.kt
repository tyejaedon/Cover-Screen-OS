package com.tyejaedon.coverscreenos.overlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Preserved, currently unused implementation for launching Android/One UI's native
 * system Recents menu (`GLOBAL_ACTION_RECENTS`) on the cover display with verification
 * and fallback to the in-overlay Cover Recents carousel.
 *
 * Kept separate from [ForegroundService] while development focuses on the custom
 * Cover Recents UI (`CoverRecentsPanelTile` + `CoverRecentAppsTracker`) for
 * predictable gesture recognition and overlay-reclaim state transitions.
 */
@Suppress("unused")
internal class SystemRecentsMenuController(
    private val serviceScope: CoroutineScope,
    private val recentAppsTracker: CoverRecentAppsTracker,
    private val isOverlaySuppressedForAppLaunch: () -> Boolean,
    private val launchSuppressedPackageName: () -> String?,
    private val suppressForNativeRecents: (packageName: String) -> Unit,
    private val completeSuppressionAndRetarget: (reason: String) -> Unit,
    private val scheduleRetarget: (reason: String) -> Unit
) {
    private var pendingVerifyJob: Job? = null

    fun cancelPendingVerification() {
        pendingVerifyJob?.cancel()
        pendingVerifyJob = null
    }

    fun requestSystemRecents(): Boolean {
        cancelPendingVerification()

        recentAppsTracker.setRecentsSheetVisible(false)
        suppressForNativeRecents(NATIVE_RECENTS_SENTINEL_PACKAGE)

        val dispatched = CoverAccessibilityService.performGlobalRecentsOnActiveService()
        if (dispatched) {
            pendingVerifyJob = serviceScope.launch {
                delay(NATIVE_RECENTS_VERIFY_DELAY_MS.milliseconds)
                if (!isOverlaySuppressedForAppLaunch() ||
                    launchSuppressedPackageName() != NATIVE_RECENTS_SENTINEL_PACKAGE
                ) {
                    return@launch
                }
                val latestClass = CoverAccessibilityService.currentForegroundClassName()
                if (CoverHomeSurfaceMatcher.isRecentsSurfaceClassName(latestClass)) {
                    return@launch
                }
                recentAppsTracker.setRecentsSheetVisible(true)
                completeSuppressionAndRetarget("native_recents_no_op_fallback")
            }
            return true
        }

        recentAppsTracker.setRecentsSheetVisible(true)
        if (isOverlaySuppressedForAppLaunch()) {
            completeSuppressionAndRetarget("fallback_cover_recents")
        } else {
            scheduleRetarget("fallback_cover_recents")
        }
        return true
    }

    companion object {
        internal const val NATIVE_RECENTS_SENTINEL_PACKAGE = "com.android.systemui.recents"
        internal const val NATIVE_RECENTS_VERIFY_DELAY_MS = 450L
    }
}
