package com.tyejaedon.coverscreenos.overlay

import android.os.Looper
import android.util.Log

/**
 * Keeps overlay suppression and app launch dispatch in a single synchronous call path.
 *
 * Prevents race conditions during rapid consecutive taps and guarantees that
 * the foreground service is always notified of a launch's success or failure.
 */
internal class CoverLaunchCoordinator(
    private val onBeginLaunch: (String) -> Boolean,
    private val onLaunchDispatched: (String) -> Unit,
    private val onLaunchFailed: (String) -> Unit,
    private val debugThrowOnThreadViolation: Boolean,
    private val isMainThread: () -> Boolean = { Looper.myLooper() == Looper.getMainLooper() }
) {

    private companion object {
        private const val LOG_TAG = "CoverLaunchCoordinator"
    }

    // Tracks the current launch to prevent rapid-fire double-tap race conditions.
    private var activeLaunchPackage: String? = null

    /**
     * Modern, safe wrapper for launching apps. Guarantees that the launch lifecycle
     * is properly completed and cleaned up, even if the execution block throws an exception.
     */
    inline fun launchWithCoordination(packageName: String, launchAction: () -> Boolean): Boolean {
        val ready = beginLaunch(packageName)
        if (!ready) return false

        var dispatched = false
        return try {
            dispatched = launchAction()
            dispatched
        } finally {
            completeLaunch(packageName, dispatched)
        }
    }

    /**
     * Begins a coordinated launch for [packageName] on the main thread.
     *
     * Rejects overlapping launches while [activeLaunchPackage] is non-null, invokes
     * `onBeginLaunch` to suppress the overlay before Activity dispatch, and locks
     * [activeLaunchPackage] if suppression succeeds.
     */
    fun beginLaunch(packageName: String): Boolean {
        if (!isMainThreadGuarded("beginLaunch", packageName)) {
            return false
        }

        if (activeLaunchPackage != null) {
            logWarning("Launch rejected for $packageName; already launching $activeLaunchPackage")
            return false
        }

        val canLaunch = onBeginLaunch(packageName)
        if (canLaunch) {
            activeLaunchPackage = packageName
        }
        return canLaunch
    }

    /**
     * Finalizes an in-flight launch initiated by [beginLaunch], releasing the single-flight
     * lock and notifying either `onLaunchDispatched` or `onLaunchFailed`.
     */
    fun completeLaunch(packageName: String, launchDispatched: Boolean) {
        val calledFromMainThread = isMainThreadGuarded("completeLaunch", packageName)

        // Ensure we are completing the package that was actually tracked
        if (activeLaunchPackage != packageName) {
            logWarning("Attempted to complete launch for $packageName but active launch is $activeLaunchPackage")
        }

        // Always clear the lock so the system can recover for the next interaction
        activeLaunchPackage = null

        if (!calledFromMainThread) {
            // If caller violates threading in release, recover overlay state conservatively.
            onLaunchFailed(packageName)
            return
        }

        if (launchDispatched) {
            onLaunchDispatched(packageName)
        } else {
            onLaunchFailed(packageName)
        }
    }

    /**
     * Enforces main-thread execution for coordinator state changes. Throws in debug builds
     * when [debugThrowOnThreadViolation] is enabled, or drops the request in release builds.
     */
    private fun isMainThreadGuarded(actionName: String, packageName: String): Boolean {
        if (isMainThread()) return true

        val message = "Cover launch coordinator action=$actionName package=$packageName must run on main thread"
        if (debugThrowOnThreadViolation) {
            throw IllegalStateException(message)
        }

        logWarning("$message; request dropped in release mode")
        return false
    }

    private fun logWarning(message: String) {
        Log.w(LOG_TAG, message)
    }
}
