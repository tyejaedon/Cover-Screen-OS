package com.tyejaedon.coverscreenos.helpers

import android.content.Context
import android.util.Log
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Physical fold posture of a clamshell foldable device.
 */
enum class CoverFoldPosture {
    /**
     * Clamshell is completely closed. The outer cover display is active.
     */
    CLOSED,

    /**
     * Clamshell hinge is partially opened (Flex Mode, ~30° to 150°).
     * The cover screen must suppress itself to prevent phantom touches and save power.
     */
    HALF_FOLDED,

    /**
     * Device is fully unfolded (180° flat). The inner primary display is active.
     */
    FLAT
}

/**
 * Tracks physical folding postures via Jetpack WindowManager [WindowInfoTracker] and [FoldingFeature].
 *
 * Emits [CoverFoldPosture] transitions to coordinate overlay suppression when the user opens
 * the device, preventing battery drain, ghost touches, and unnecessary composition cycles.
 */
class FoldPostureTracker(
    // Host context passed to WindowInfoTracker to observe window/hinge layout events.
    private val context: Context,
    // Jetpack WindowManager handle that streams FoldingFeature hinge changes (injectable for tests).
    private val windowInfoTracker: WindowInfoTracker = WindowInfoTracker.getOrCreate(context)
) {

    // Internal mutable hinge state; starts as CLOSED so the outer screen stays active until an unfold event arrives.
    private val _posture = MutableStateFlow(CoverFoldPosture.CLOSED)
    /**
     * Reactive stream of the device's current [CoverFoldPosture]. Defaults to
     * [CoverFoldPosture.CLOSED] until [WindowInfoTracker] emits an unfolded [FoldingFeature].
     */
    val posture: StateFlow<CoverFoldPosture> = _posture.asStateFlow()

    // Active coroutine collecting windowLayoutInfo; kept non-null while listening so stopMonitoring() can cancel it.
    private var trackingJob: Job? = null

    /**
     * Starts listening for window layout / folding feature updates via
     * [WindowInfoTracker.windowLayoutInfo] inside [scope].
     */
    fun startMonitoring(scope: CoroutineScope) {
        // Avoid spawning duplicate collectors if monitoring is already active.
        if (trackingJob != null) return

        trackingJob = scope.launch {
            try {
                windowInfoTracker.windowLayoutInfo(context)
                    .catch { e ->
                        // Fall back to CLOSED so the cover screen stays usable if the stream errors.
                        Log.w(TAG, "WindowLayoutInfo flow error; defaulting to CLOSED posture", e)
                        _posture.value = CoverFoldPosture.CLOSED
                    }
                    .collect { layoutInfo ->
                        val newPosture = computePosture(layoutInfo)
                        if (_posture.value != newPosture) {
                            Log.d(TAG, "Fold posture changed: ${_posture.value} -> $newPosture")
                            _posture.value = newPosture
                        }
                    }
            } catch (t: Throwable) {
                // Non-visual contexts or missing Jetpack WindowManager OEM extensions can throw
                // synchronously when attaching the listener; stay safely in CLOSED.
                Log.w(TAG, "Could not attach WindowInfoTracker to context; remaining in CLOSED", t)
            }
        }
    }

    /**
     * Stops monitoring and releases coroutine resources.
     */
    fun stopMonitoring() {
        trackingJob?.cancel()
        trackingJob = null
    }

    /**
     * Evaluates [WindowLayoutInfo] to deduce posture from the first [FoldingFeature] in
     * `displayFeatures`.
     *
     * When the device is closed onto the cover display, Jetpack WindowManager typically emits no
     * [FoldingFeature] (`null`), which maps to [CoverFoldPosture.CLOSED]. [FoldingFeature.State.HALF_OPENED]
     * and [FoldingFeature.State.FLAT] suppress the cover overlay.
     */
    fun computePosture(layoutInfo: WindowLayoutInfo): CoverFoldPosture {
        val foldingFeature = layoutInfo.displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull()

        return when {
            foldingFeature == null -> CoverFoldPosture.CLOSED
            foldingFeature.state == FoldingFeature.State.HALF_OPENED -> CoverFoldPosture.HALF_FOLDED
            foldingFeature.state == FoldingFeature.State.FLAT -> CoverFoldPosture.FLAT
            else -> CoverFoldPosture.CLOSED
        }
    }

    /**
     * Sets posture manually for unit testing.
     */
    internal fun setPostureForTest(newPosture: CoverFoldPosture) {
        _posture.value = newPosture
    }

    companion object {
        private const val TAG = "FoldPostureTracker"
    }
}
