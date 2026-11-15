package com.tyejaedon.coverscreenos.keyboard.floris

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * User customization and host configuration for [CoverFlorisKeyboardSurface].
 * Bridges `LauncherSettingsStore` preferences from `:app` into `:keyboard-floris`.
 */
data class FlorisKeyboardCustomization(
    val extraHeightDp: Float = 0f,
    val onExtraHeightChanged: ((Float) -> Unit)? = null,
    val oneHandedEnabled: Boolean = false,
    val onOneHandedToggle: (() -> Unit)? = null,
    val dockAnchor: ClamshellDockAnchor = ClamshellDockAnchor.LEFT,
    val onAnchorToggle: (() -> Unit)? = null,
    val suggestionStripVisible: Boolean = true,
    val suggestionsEnabled: Boolean = true,
    val autocorrectEnabled: Boolean = true,
    val popupKeyPreviewEnabled: Boolean = true,
    val imeOptions: Int = EditorInfo.IME_ACTION_DONE,
    val showModeSwitchChip: Boolean = true,
    val compactSymbolsLayout: Boolean = false,
    val includeClearKey: Boolean = false,
    val isT9Predictive: Boolean = true,
    val onT9PredictiveToggle: (() -> Unit)? = null,
    val themePalette: FlorisThemePalette = FlorisThemePalette.AmoledCover
)

/**
 * Upstream FlorisBoard Smartbar component adapted for clamshell cover displays.
 *
 * Renders:
 * - Horizontally scrollable candidate suggestions with hero primary highlight.
 * - Long-press candidate menu to learn or suppress dictionary words.
 * - Stable fixed container height (`32.dp`) so the keyboard rows never jump when candidates appear.
 */
@Composable
fun FlorisSmartbar(
    candidates: List<String>,
    onCandidateSelected: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
    selectedIndex: Int = -1,
    onLearnCandidate: ((Int, String) -> Unit)? = null,
    onSuppressCandidate: ((Int, String) -> Unit)? = null,
    onHaptic: () -> Unit = {},
    stripHeight: Dp = 32.dp,
    themePalette: FlorisThemePalette = FlorisThemePalette.AmoledCover
) {
    val listState = rememberLazyListState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(stripHeight)
            .background(themePalette.smartbarBackground)
    ) {
        if (candidates.isEmpty()) return@Box

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(stripHeight)
        ) {
            items(count = candidates.size) { index ->
                val candidate = candidates[index]
                FlorisCandidateChip(
                    label = candidate,
                    selected = index == selectedIndex,
                    themePalette = themePalette,
                    onLearn = onLearnCandidate?.let { { it(index, candidate) } },
                    onSuppress = onSuppressCandidate?.let { { it(index, candidate) } },
                    onClick = {
                        onHaptic()
                        onCandidateSelected(index, candidate)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlorisCandidateChip(
    label: String,
    selected: Boolean,
    themePalette: FlorisThemePalette,
    onLearn: (() -> Unit)?,
    onSuppress: (() -> Unit)?,
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val bg = if (selected) {
        themePalette.candidatePrimaryBackground
    } else {
        themePalette.candidateChipBackground
    }
    val fg = if (selected) {
        themePalette.candidatePrimaryForeground
    } else {
        themePalette.candidateChipForeground
    }

    Box {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 32.dp, minHeight = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = if (onLearn != null || onSuppress != null) {
                        { menuExpanded = true }
                    } else null
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = LocalTextStyle.current.copy(
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = fg
                )
            )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (onLearn != null) {
                DropdownMenuItem(
                    text = { Text("Learn $label") },
                    onClick = {
                        menuExpanded = false
                        onLearn()
                    }
                )
            }
            if (onSuppress != null) {
                DropdownMenuItem(
                    text = { Text("Suppress $label") },
                    onClick = {
                        menuExpanded = false
                        onSuppress()
                    }
                )
            }
        }
    }
}

/**
 * Top control bar combining drag-to-resize grip, one-handed mode toggle & anchor swap,
 * optional QWERTY <-> T9 mode switch, clipboard actions slot, and dismiss button.
 */
@Composable
fun FlorisResizeAndControlBar(
    activeMode: CoverLayoutMode,
    showModeSwitchChip: Boolean,
    onToggleMode: () -> Unit,
    extraHeightDp: Float,
    onExtraHeightChanged: (Float) -> Unit,
    oneHandedEnabled: Boolean,
    onOneHandedToggle: () -> Unit,
    dockAnchor: ClamshellDockAnchor,
    onAnchorToggle: () -> Unit,
    onDismissRequest: (() -> Unit)?,
    onHaptic: () -> Unit,
    modifier: Modifier = Modifier,
    isT9Predictive: Boolean = true,
    onT9PredictiveToggle: (() -> Unit)? = null,
    themePalette: FlorisThemePalette = FlorisThemePalette.AmoledCover,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    val density = LocalDensity.current
    val onExtraHeightChangedState = rememberUpdatedState(onExtraHeightChanged)
    val onHapticState = rememberUpdatedState(onHaptic)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("keyboard_resize_controls"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical drag resize grip
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(16.dp)
                .testTag("keyboard_resize_grip")
                .clickable {
                    onHaptic()
                    val next = if (extraHeightDp >= 16f) 0f else extraHeightDp + 8f
                    onExtraHeightChanged(next)
                }
                .pointerInput(Unit) {
                    var lastSnapped = extraHeightDp
                    detectVerticalDragGestures(
                        onDragStart = { lastSnapped = extraHeightDp },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val deltaDp = with(density) { -dragAmount.toDp().value }
                            val next = (extraHeightDp + deltaDp).coerceIn(0f, 24f)
                            onExtraHeightChangedState.value(next)
                            if (abs(next - lastSnapped) >= 6f) {
                                lastSnapped = next
                                onHapticState.value()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(themePalette.keySecondaryForeground.copy(alpha = 0.6f))
            )
        }

        // One-handed mode toggle
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (oneHandedEnabled) themePalette.actionKeyBackground
                    else themePalette.functionalKeyBackground
                )
                .clickable {
                    onOneHandedToggle()
                    onHaptic()
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("keyboard_one_handed_toggle")
        ) {
            Text(
                text = if (oneHandedEnabled) "1-hand: on" else "1-hand",
                style = MaterialTheme.typography.labelSmall,
                color = if (oneHandedEnabled) themePalette.actionKeyForeground
                else themePalette.functionalKeyForeground
            )
        }

        if (oneHandedEnabled) {
            val anchorName = if (dockAnchor == ClamshellDockAnchor.RIGHT) "Right" else "Left"
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = "Swap one-handed side (currently $anchorName)",
                tint = themePalette.functionalKeyForeground,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable {
                        onAnchorToggle()
                        onHaptic()
                    }
                    .testTag("keyboard_one_handed_anchor_swap")
            )
        }

        if (showModeSwitchChip) {
            Text(
                text = if (activeMode == CoverLayoutMode.T9_PREDICTIVE) "T9" else "QWERTY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = themePalette.actionKeyForeground
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(themePalette.actionKeyBackground)
                    .clickable {
                        onHaptic()
                        onToggleMode()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("floris_mode_toggle")
            )
        }

        if (activeMode == CoverLayoutMode.T9_PREDICTIVE && onT9PredictiveToggle != null) {
            Text(
                text = if (isT9Predictive) "T9 Pred" else "Multi-Tap",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isT9Predictive) themePalette.actionKeyForeground else themePalette.functionalKeyForeground
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isT9Predictive) themePalette.actionKeyBackground
                        else themePalette.functionalKeyBackground
                    )
                    .clickable {
                        onHaptic()
                        onT9PredictiveToggle()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("t9_prediction_mode_toggle")
            )
        }

        Box(modifier = Modifier.weight(1f))

        trailingContent?.invoke(this)

        if (onDismissRequest != null) {
            Text(
                text = "✕",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = themePalette.functionalKeyForeground
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(themePalette.functionalKeyBackground)
                    .clickable {
                        onHaptic()
                        onDismissRequest()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("floris_dismiss_button")
            )
        }
    }
}
