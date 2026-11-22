package com.tyejaedon.coverscreenos.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// Distance dragged upward inside the bottom strip before Home or Recents hold detection activates.
private val BOTTOM_BAR_SWIPE_UP_THRESHOLD = 28.dp
// Distance dragged horizontally along the bottom strip to trigger Back or Recents.
private val BOTTOM_BAR_HORIZONTAL_THRESHOLD = 36.dp
// Hold duration after swiping up before promoting the gesture from Home flick to Recents overview.
private const val HOLD_FOR_RECENTS_DELAY_MS = 260L

/**
 * Slim bottom-edge navigation pill strip for the cover screen.
 *
 * Hosts touch navigation in two contexts:
 * - Mounted as a narrow `TYPE_ACCESSIBILITY_OVERLAY` bottom strip while a user app is in the foreground.
 * - Embedded along the bottom of the unlocked launcher overlay.
 *
 * Supported gestures:
 * - **Swipe up + release quickly**: [onHomeGesture] (reclaim cover launcher home).
 * - **Swipe up + hold (>= 260 ms)**: [onRecentsGesture] (native-first Recents / Cover Recents fallback).
 * - **Swipe right**: [onRecentsGesture].
 * - **Swipe left**: [onBackGesture].
 */
@Composable
fun CoverBottomGestureBar(
    onHomeGesture: () -> Unit,
    onRecentsGesture: () -> Unit,
    onBackGesture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val swipeUpThresholdPx = with(density) { BOTTOM_BAR_SWIPE_UP_THRESHOLD.toPx() }
    val horizontalThresholdPx = with(density) { BOTTOM_BAR_HORIZONTAL_THRESHOLD.toPx() }

    val latestOnHome by rememberUpdatedState(onHomeGesture)
    val latestOnRecents by rememberUpdatedState(onRecentsGesture)
    val latestOnBack by rememberUpdatedState(onBackGesture)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .semantics { contentDescription = "Cover navigation gesture bar" }
            .pointerInput(swipeUpThresholdPx, horizontalThresholdPx) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var totalDx = 0f
                    var totalDy = 0f
                    var gestureHandled = false
                    var holdRecentsJob: Job? = null

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val activeChanges = event.changes
                            val anyPressed = activeChanges.any { it.pressed }

                            for (change in activeChanges) {
                                val delta = change.positionChange()
                                totalDx += delta.x
                                totalDy += delta.y
                            }

                            if (!gestureHandled) {
                                // Promote held upward drag to Recents once the pointer dwells past threshold.
                                if (-totalDy >= swipeUpThresholdPx && abs(totalDy) >= abs(totalDx)) {
                                    if (holdRecentsJob == null) {
                                        holdRecentsJob = coroutineScope.launch {
                                            delay(HOLD_FOR_RECENTS_DELAY_MS)
                                            if (!gestureHandled) {
                                                gestureHandled = true
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                latestOnRecents()
                                            }
                                        }
                                    }
                                } else if (holdRecentsJob != null) {
                                    holdRecentsJob.cancel()
                                    holdRecentsJob = null
                                }

                                // Immediate horizontal quick-gestures along the bottom strip.
                                if (abs(totalDx) >= horizontalThresholdPx && abs(totalDx) > abs(totalDy) * 1.25f) {
                                    holdRecentsJob?.cancel()
                                    holdRecentsJob = null
                                    gestureHandled = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (totalDx > 0f) {
                                        latestOnRecents()
                                    } else {
                                        latestOnBack()
                                    }
                                }
                            }

                            if (!anyPressed) {
                                holdRecentsJob?.cancel()
                                holdRecentsJob = null
                                if (!gestureHandled && -totalDy >= swipeUpThresholdPx && abs(totalDy) >= abs(totalDx)) {
                                    gestureHandled = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    latestOnHome()
                                }
                                break
                            }
                        }
                    } finally {
                        holdRecentsJob?.cancel()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(4.dp)
                .background(
                    color = Color.White.copy(alpha = 0.68f),
                    shape = RoundedCornerShape(50)
                )
                .border(
                    width = 0.75.dp,
                    color = Color.Black.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(50)
                )
        )
    }
}
