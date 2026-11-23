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

// Touch target height of the bottom gesture strip; kept >= swipe-up threshold for reliable grabs.
internal val BOTTOM_BAR_HEIGHT = 28.dp
// Distance dragged upward inside the bottom strip before Home or Recents hold detection activates.
private val BOTTOM_BAR_SWIPE_UP_THRESHOLD = 24.dp
// Hysteresis ratio below which a held upward drag cancels its pending Recents promotion.
private const val HOLD_CANCEL_HYSTERESIS_RATIO = 0.5f
// Distance dragged horizontally along the bottom strip to trigger Back or Recents.
private val BOTTOM_BAR_HORIZONTAL_THRESHOLD = 36.dp
// Distance dragged horizontally along the bottom strip to trigger Back or Recents before system SPY pilfering (~17dp).
// Fallback commit distance when the pointer stream ends or is pilfered (ACTION_CANCEL) by a system gesture monitor.
private val BOTTOM_BAR_CANCEL_COMMIT_THRESHOLD = 8.dp
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
 * - **Swipe up + hold (>= 260 ms)**: [onRecentsGesture] (Cover Recents overview).
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
    val cancelCommitThresholdPx = with(density) { BOTTOM_BAR_CANCEL_COMMIT_THRESHOLD.toPx() }

    val latestOnHome by rememberUpdatedState(onHomeGesture)
    val latestOnRecents by rememberUpdatedState(onRecentsGesture)
    val latestOnBack by rememberUpdatedState(onBackGesture)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BOTTOM_BAR_HEIGHT)
            .semantics { contentDescription = "Cover navigation gesture bar" }
            .pointerInput(swipeUpThresholdPx, horizontalThresholdPx) {
                val touchSlopPx = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var totalDx = 0f
                    var totalDy = 0f
                    var gestureHandled = false
                    var lockedVerticalUp = false
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
                                if (abs(totalDx) > touchSlopPx || abs(totalDy) > touchSlopPx) {
                                    change.consume()
                                }
                            }

                            if (!gestureHandled) {
                                // Lock into vertical-up mode once past threshold so minor horizontal drift doesn't cancel hold.
                                if (!lockedVerticalUp && -totalDy >= swipeUpThresholdPx && abs(totalDy) >= abs(totalDx)) {
                                    lockedVerticalUp = true
                                } else if (lockedVerticalUp && -totalDy < swipeUpThresholdPx * HOLD_CANCEL_HYSTERESIS_RATIO) {
                                    lockedVerticalUp = false
                                }

                                if (lockedVerticalUp) {
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

                                // Immediate horizontal quick-gestures along the bottom strip (only when not locked vertical-up).
                                if (!lockedVerticalUp && abs(totalDx) >= horizontalThresholdPx && abs(totalDx) > abs(totalDy) * 1.25f) {
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
                                if (!gestureHandled && (lockedVerticalUp || (-totalDy >= swipeUpThresholdPx && abs(totalDy) >= abs(totalDx)))) {
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
