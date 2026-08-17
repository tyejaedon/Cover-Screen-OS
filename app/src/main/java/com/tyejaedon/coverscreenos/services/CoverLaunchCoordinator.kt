package com.tyejaedon.coverscreenos.services

import android.os.Looper
import android.util.Log

/**
 * Keeps overlay suppression and app launch dispatch in a single synchronous call path.
 *
 * In debug builds a non-main-thread call throws immediately so threading bugs surface early.
 * In release builds, non-main-thread calls fail safely and trigger rollback when needed.
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

    fun beginLaunch(packageName: String): Boolean {
        if (!isMainThreadGuarded("beginLaunch", packageName)) {
            return false
        }
        return onBeginLaunch(packageName)
    }

    fun completeLaunch(packageName: String, launchDispatched: Boolean) {
        val calledFromMainThread = isMainThreadGuarded("completeLaunch", packageName)
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

    private fun isMainThreadGuarded(actionName: String, packageName: String): Boolean {
        if (isMainThread()) {
            return true
        }

        val message = "Cover launch coordinator action=$actionName package=$packageName must run on main thread"
        if (debugThrowOnThreadViolation) {
            throw IllegalStateException(message)
        }

        logWarning("$message; request dropped in release mode")
        return false
    }

    private fun logWarning(message: String) {
        runCatching {
            Log.w(LOG_TAG, message)
        }
    }
}