package com.tyejaedon.coverscreenos.keyboard.floris

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * User customization and host configuration for [CoverFlorisKeyboardSurface].
 * Bridges `LauncherSettingsStore` preferences from `:app` into `:keyboard-floris`.
 *
 * @property extraHeightDp User-resized additional row height (in dp) persisted across sessions.
 * @property onExtraHeightChanged Optional callback invoked as the user drags the top bar vertically.
 * @property oneHandedEnabled Whether the keyboard narrows and docks to one thumb side.
 * @property onOneHandedToggle Optional callback toggled from the top-bar one-handed icon.
 * @property dockAnchor Left or right screen edge used when [oneHandedEnabled] is true.
 * @property onAnchorToggle Optional callback swapping left/right one-handed alignment.
 * @property suggestionStripVisible Master visibility flag for the prediction chip bar.
 * @property suggestionsEnabled Whether prediction engines should feed candidates into the strip.
 * @property autocorrectEnabled Whether Space automatically fixes typos on QWERTY.
 * @property popupKeyPreviewEnabled Whether pressing a letter pops up [FlorisKeyPreviewBubble].
 * @property imeOptions Android [EditorInfo] flags controlling the Enter key's icon (Search, Send, Done, etc.).
 * @property showModeSwitchChip Whether the QWERTY/T9 toggle button appears in the toolbar.
 * @property compactSymbolsLayout Uses a tighter symbols layout variant on smaller cover screens.
 * @property includeClearKey Adds a dedicated Clear/Undo key to the bottom row (used for search/PIN entry).
 * @property isT9Predictive True for dictionary-based T9 prediction; false for classic ABC multi-tap.
 * @property onT9PredictiveToggle Optional callback toggling T9 vs. ABC mode.
 * @property themePalette Color tokens styling keys, surface, smartbar, popups, and glide trail.
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
 * Unified One UI / Gboard-style 34dp top bar combining:
 * - Candidate suggestions when typing (with a compact leading button to peek at quick tools)
 * - Clean icon-only quick toolbar when idle (mode switch, one-handed dock, clipboard, dismiss)
 * - Integrated vertical drag-to-resize gesture on the bar itself (no extra grip row)
 */
@Composable
fun CoverKeyboardTopBar(
    candidates: List<String>,
    selectedIndex: Int,
    suggestionsVisible: Boolean,
    onCandidateSelected: (Int, String) -> Unit,
    onLearnCandidate: ((Int, String) -> Unit)?,
    onSuppressCandidate: ((Int, String) -> Unit)?,
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
    // Keep callback references fresh inside pointerInput(Unit) so vertical resize drags don't cancel.
    val onExtraHeightChangedState = rememberUpdatedState(onExtraHeightChanged)
    val onHapticState = rememberUpdatedState(onHaptic)

    // Lets the user temporarily reveal toolbar icons even while word suggestions are visible.
    var forceToolsVisible by remember { mutableStateOf(false) }
    val showCandidates = suggestionsVisible && candidates.isNotEmpty() && !forceToolsVisible

    LaunchedEffect(candidates.isEmpty()) {
        if (candidates.isEmpty()) {
            forceToolsVisible = false
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(themePalette.smartbarBackground)
            .padding(horizontal = 4.dp)
            .testTag("keyboard_resize_controls")
            .pointerInput(Unit) {
                // Tracks the previous 6dp height detent so haptic clicks fire only at step boundaries.
                var lastSnapped = extraHeightDp
                detectVerticalDragGestures(
                    onDragStart = { lastSnapped = extraHeightDp },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // Dragging up (negative Y) grows extraHeightDp within the 0..24dp range.
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCandidates) {
            SmartbarIconButton(
                onClick = {
                    onHaptic()
                    forceToolsVisible = true
                },
                contentDescription = "Show keyboard tools",
                tint = themePalette.keySecondaryForeground,
                modifier = Modifier.testTag("floris_tools_back")
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            FlorisSmartbar(
                candidates = candidates,
                selectedIndex = selectedIndex,
                onCandidateSelected = onCandidateSelected,
                onLearnCandidate = onLearnCandidate,
                onSuppressCandidate = onSuppressCandidate,
                onHaptic = onHaptic,
                stripHeight = 34.dp,
                themePalette = themePalette,
                modifier = Modifier
                    .weight(1f)
                    .testTag("keyboard_suggestions")
            )
        } else {
            if (suggestionsVisible && candidates.isNotEmpty()) {
                SmartbarIconButton(
                    onClick = {
                        onHaptic()
                        forceToolsVisible = false
                    },
                    contentDescription = "Back to suggestions",
                    tint = themePalette.actionKeyBackground
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (showModeSwitchChip) {
                val isT9 = activeMode == CoverLayoutMode.T9_PREDICTIVE
                SmartbarIconButton(
                    onClick = {
                        onHaptic()
                        onToggleMode()
                    },
                    contentDescription = if (isT9) "Switch to QWERTY" else "Switch to T9",
                    tint = themePalette.functionalKeyForeground,
                    modifier = Modifier.testTag("floris_mode_toggle")
                ) {
                    Icon(
                        imageVector = if (isT9) Icons.Rounded.Keyboard else Icons.Rounded.Dialpad,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            if (activeMode == CoverLayoutMode.T9_PREDICTIVE && onT9PredictiveToggle != null) {
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            if (isT9Predictive) themePalette.actionKeyBackground.copy(alpha = 0.18f)
                            else themePalette.functionalKeyBackground
                        )
                        .clickable {
                            onHaptic()
                            onT9PredictiveToggle()
                        }
                        .padding(horizontal = 8.dp)
                        .testTag("t9_prediction_mode_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isT9Predictive) "T9" else "Abc",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isT9Predictive) themePalette.actionKeyBackground
                        else themePalette.functionalKeyForeground
                    )
                }
            }

            SmartbarIconButton(
                onClick = {
                    onOneHandedToggle()
                    onHaptic()
                },
                contentDescription = if (oneHandedEnabled) "Disable one-handed mode" else "One-handed mode",
                tint = if (oneHandedEnabled) themePalette.actionKeyBackground
                else themePalette.functionalKeyForeground,
                modifier = Modifier.testTag("keyboard_one_handed_toggle")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Smartphone,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (oneHandedEnabled) {
                val anchorName = if (dockAnchor == ClamshellDockAnchor.RIGHT) "Right" else "Left"
                SmartbarIconButton(
                    onClick = {
                        onAnchorToggle()
                        onHaptic()
                    },
                    contentDescription = "Swap one-handed side (currently $anchorName)",
                    tint = themePalette.functionalKeyForeground,
                    modifier = Modifier.testTag("keyboard_one_handed_anchor_swap")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("keyboard_resize_grip"),
                contentAlignment = Alignment.Center
            ) {
                // Subtle pill indicator for vertical drag-to-resize
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(themePalette.keySecondaryForeground.copy(alpha = 0.25f))
                )
            }

            trailingContent?.invoke(this)

            if (onDismissRequest != null) {
                SmartbarIconButton(
                    onClick = {
                        onHaptic()
                        onDismissRequest()
                    },
                    contentDescription = "Hide keyboard",
                    tint = themePalette.functionalKeyForeground,
                    modifier = Modifier.testTag("floris_dismiss_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Circular 30dp icon button used across [CoverKeyboardTopBar] for toolbar actions.
 */
@Composable
private fun SmartbarIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides tint
        ) {
            content()
        }
    }
}

/**
 * One UI / Gboard-style Smartbar candidate strip:
 * - Clean borderless typography with subtle vertical dividers between suggestions
 * - Soft pill highlight only on the primary/selected candidate
 * - Long-press candidate menu to learn or suppress dictionary words
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
    stripHeight: Dp = 34.dp,
    themePalette: FlorisThemePalette = FlorisThemePalette.AmoledCover
) {
    // Horizontal scroll state for the candidate LazyRow.
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
            contentPadding = PaddingValues(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(stripHeight)
        ) {
            items(count = candidates.size) { index ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(themePalette.keySecondaryForeground.copy(alpha = 0.2f))
                    )
                }
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

/**
 * Individual word suggestion pill inside [FlorisSmartbar] supporting tap commit and
 * optional long-press Learn/Suppress menu actions.
 */
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
    // Controls visibility of the anchored Learn/Suppress dropdown menu on long-press.
    var menuExpanded by remember { mutableStateOf(false) }
    // Distinct pill colors highlight the top/hero candidate while secondary chips stay subtle.
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

    Box(modifier = Modifier.padding(horizontal = 2.dp)) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 44.dp, minHeight = 26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(bg)
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = if (onLearn != null || onSuppress != null) {
                        { menuExpanded = true }
                    } else null
                )
                .padding(horizontal = 10.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
