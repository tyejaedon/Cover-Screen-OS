package com.tyejaedon.coverscreenos.keyboard.floris

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Main Jetpack Compose keyboard surface for Cover-Screen-OS.
 *
 * Implements an ultra-compact, high-performance touch keyboard with:
 * - Dual layout modes (Compact QWERTY and T9 9-key predictive keypad).
 * - Clamshell screen height constraints and one-handed edge anchoring.
 * - Multi-tap, long-press accent popups, and spacebar cursor scrub.
 * - Unified text injection via [CoverEditorBridge].
 */
@Composable
fun CoverFlorisKeyboardSurface(
    bridge: CoverEditorBridge,
    modifier: Modifier = Modifier,
    initialMode: CoverLayoutMode = CoverLayoutMode.QWERTY,
    candidates: List<String> = emptyList(),
    onCandidateSelected: ((String) -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null,
    onModeChanged: ((CoverLayoutMode) -> Unit)? = null,
    layoutSpec: ClamshellLayoutSpec = ClamshellLayoutSpec.Default,
    headerExtraContent: (@Composable () -> Unit)? = null
) {
    var activeMode by remember { mutableStateOf(initialMode) }
    var shiftState by remember { mutableStateOf(CoverShiftState.LOWERCASE) }
    var lastShiftTapMs by remember { mutableLongStateOf(0L) }
    var dockAnchor by remember { mutableStateOf(layoutSpec.dockAnchor) }
    var extraHeightDp by remember { mutableFloatStateOf(layoutSpec.extraHeightDp) }

    val config = LocalConfiguration.current
    val screenHeightDp = config.screenHeightDp.dp
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val currentRows = remember(activeMode, shiftState) {
        when (activeMode) {
            CoverLayoutMode.QWERTY -> CoverLayoutFactory.createQwertyRows(shiftState)
            CoverLayoutMode.T9_PREDICTIVE -> CoverLayoutFactory.createT9Rows()
            CoverLayoutMode.SYMBOLS, CoverLayoutMode.NUMERIC -> CoverLayoutFactory.createSymbolsRows()
        }
    }

    val effectiveRowHeight = remember(screenHeightDp, currentRows.size, extraHeightDp) {
        val spec = layoutSpec.copy(extraHeightDp = extraHeightDp)
        spec.calculateRowHeight(screenHeightDp, currentRows.size)
    }

    fun performHaptic(constant: Int = HapticFeedbackConstants.KEYBOARD_TAP) {
        view.performHapticFeedback(constant)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 1. Clamshell Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Quick mode toggle: QWERTY <-> T9
                    Text(
                        text = if (activeMode == CoverLayoutMode.T9_PREDICTIVE) "T9" else "QWERTY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                            .clickable {
                                performHaptic()
                                activeMode = if (activeMode == CoverLayoutMode.T9_PREDICTIVE) {
                                    CoverLayoutMode.QWERTY
                                } else {
                                    CoverLayoutMode.T9_PREDICTIVE
                                }
                                onModeChanged?.invoke(activeMode)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    // One-handed dock toggle
                    Text(
                        text = when (dockAnchor) {
                            ClamshellDockAnchor.LEFT -> "⇦ Left"
                            ClamshellDockAnchor.RIGHT -> "Right ⇨"
                            ClamshellDockAnchor.CENTER -> "Full"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable {
                                performHaptic()
                                dockAnchor = dockAnchor.toggled()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )

                    headerExtraContent?.invoke()
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Height adjust toggle (-/+)
                    Text(
                        text = "↕",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable {
                                performHaptic()
                                extraHeightDp = if (extraHeightDp >= 16f) 0f else extraHeightDp + 8f
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    // Dismiss button
                    if (onDismissRequest != null) {
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable {
                                    performHaptic()
                                    onDismissRequest()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 2. Smartbar / Suggestion Strip
            if (candidates.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(candidates) { candidate ->
                        Text(
                            text = candidate,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .clickable {
                                    performHaptic()
                                    if (onCandidateSelected != null) {
                                        onCandidateSelected(candidate)
                                    } else {
                                        bridge.commitCandidate(candidate)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 3. One-Handed Width Box Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (dockAnchor == ClamshellDockAnchor.RIGHT) 36.dp else 0.dp,
                        end = if (dockAnchor == ClamshellDockAnchor.LEFT) 36.dp else 0.dp
                    )
            ) {
                // 4. Keyboard Key Rows
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(layoutSpec.rowSpacingDp)
                ) {
                    currentRows.forEach { rowKeys ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(effectiveRowHeight),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            rowKeys.forEach { keyItem ->
                                CoverKeyView(
                                    item = keyItem,
                                    height = effectiveRowHeight,
                                    onAction = { action ->
                                        performHaptic()
                                        when (action) {
                                            is CoverKeyAction.InputChar -> {
                                                bridge.insertText(action.char.toString())
                                                if (shiftState == CoverShiftState.SINGLE_SHIFT) {
                                                    shiftState = CoverShiftState.LOWERCASE
                                                }
                                            }
                                            is CoverKeyAction.InputText -> {
                                                bridge.insertText(action.text)
                                            }
                                            CoverKeyAction.Backspace -> {
                                                bridge.deleteSurroundingText(1, 0)
                                            }
                                            CoverKeyAction.DeleteWord -> {
                                                bridge.deletePreviousWord()
                                            }
                                            CoverKeyAction.Space -> {
                                                bridge.insertText(" ")
                                            }
                                            CoverKeyAction.Enter -> {
                                                bridge.finishInput()
                                            }
                                            CoverKeyAction.Shift -> {
                                                val now = SystemClock.uptimeMillis()
                                                shiftState = shiftState.nextState(lastShiftTapMs, now)
                                                lastShiftTapMs = now
                                            }
                                            CoverKeyAction.SwitchToSymbols -> {
                                                activeMode = CoverLayoutMode.SYMBOLS
                                            }
                                            CoverKeyAction.SwitchToLetters -> {
                                                activeMode = CoverLayoutMode.QWERTY
                                            }
                                            CoverKeyAction.SwitchMode -> {
                                                activeMode = if (activeMode == CoverLayoutMode.QWERTY) {
                                                    CoverLayoutMode.T9_PREDICTIVE
                                                } else {
                                                    CoverLayoutMode.QWERTY
                                                }
                                            }
                                            CoverKeyAction.ToggleT9 -> {
                                                activeMode = CoverLayoutMode.T9_PREDICTIVE
                                            }
                                            CoverKeyAction.Dismiss -> {
                                                onDismissRequest?.invoke()
                                            }
                                        }
                                    },
                                    onMoveCursor = { delta ->
                                        bridge.moveCursor(delta)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual key button within a keyboard row.
 */
@Composable
private fun RowScope.CoverKeyView(
    item: CoverKeyItem,
    height: Dp,
    onAction: (CoverKeyAction) -> Unit,
    onMoveCursor: ((Int) -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var dragPixels by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Key colors
    val backgroundColor = when {
        isPressed -> MaterialTheme.colorScheme.primaryContainer
        item.isFunctional -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    }

    val textColor = when {
        isPressed -> MaterialTheme.colorScheme.onPrimaryContainer
        item.isFunctional -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Repeat on hold for backspace
    LaunchedEffect(isPressed) {
        if (isPressed && item.action == CoverKeyAction.Backspace) {
            delay(350)
            while (isActive && isPressed) {
                onAction(CoverKeyAction.Backspace)
                delay(60)
            }
        }
    }

    Box(
        modifier = Modifier
            .weight(item.weight)
            .fillMaxHeight()
            .then(if (item.tag != null) Modifier.testTag(item.tag) else Modifier)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onAction(item.action) }
            )
            .then(
                if (item.action == CoverKeyAction.Space) {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragPixels = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                dragPixels += dragAmount
                                val charThreshold = with(density) { 16.dp.toPx() }
                                if (kotlin.math.abs(dragPixels) >= charThreshold) {
                                    val delta = if (dragPixels > 0) 1 else -1
                                    onMoveCursor?.invoke(delta)
                                    dragPixels -= (delta * charThreshold)
                                }
                            }
                        )
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.primaryLabel,
                color = textColor,
                fontSize = if (item.primaryLabel.length > 2) 11.sp else 16.sp,
                fontWeight = if (item.isFunctional) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )

            if (!item.secondaryLabel.isNullOrEmpty() || item.t9Chars.isNotEmpty()) {
                Text(
                    text = item.secondaryLabel ?: item.t9Chars,
                    color = textColor.copy(alpha = 0.65f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
