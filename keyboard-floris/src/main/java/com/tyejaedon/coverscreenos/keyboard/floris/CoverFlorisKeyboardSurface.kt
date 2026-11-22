package com.tyejaedon.coverscreenos.keyboard.floris

import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyejaedon.coverscreenos.keyboard.nlp.DTWGlideDecoder
import com.tyejaedon.coverscreenos.keyboard.nlp.T9PredictiveEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Maps pixel touch coordinates on the 4-row QWERTY container (`widthPx` × `heightPx`)
 * into `QwertyGeometry` normalized key-centroid coordinates (`0.0..9.0` × `0.0..2.0`).
 */
internal fun normalizeGlidePathToQwerty(
    points: List<Offset>,
    widthPx: Float,
    heightPx: Float
): List<Offset> {
    if (widthPx <= 0f || heightPx <= 0f) return emptyList()
    return points.map { pt ->
        val normX = ((pt.x / widthPx) * 10f - 0.5f).coerceIn(0f, 9f)
        val normY = ((pt.y / heightPx) * 4f - 0.5f).coerceIn(0f, 2f)
        Offset(normX, normY)
    }
}

/**
 * Main Jetpack Compose keyboard surface for Cover-Screen-OS, powered by the
 * FlorisBoard JSON layout parser (`FlorisLayoutParser`), `PopupSet` popup system,
 * `FlorisSmartbar`, `FlorisThemePalette` (Snygg-style tokens), and `FlorisTextInputManager`.
 *
 * Unifies:
 * - System IME (`CoverT9InputMethodService`)
 * - Accessibility Overlay Input (`CoverScreenInputInjectionEngine`: QWERTY, T9, and Numeric PIN)
 * - Launcher App Drawer Search (`OverlaySearchKeyboardBridge`)
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
    headerExtraContent: (@Composable () -> Unit)? = null,
    customization: FlorisKeyboardCustomization = FlorisKeyboardCustomization(
        extraHeightDp = layoutSpec.extraHeightDp,
        oneHandedEnabled = layoutSpec.isOneHandedEnabled,
        dockAnchor = layoutSpec.dockAnchor
    ),
    textBeforeCursor: String? = null,
    selectedCandidateIndex: Int = -1,
    onLearnCandidate: ((Int, String) -> Unit)? = null,
    onSuppressCandidate: ((Int, String) -> Unit)? = null,
    onAutocorrectWord: ((String) -> String)? = null,
    onDeleteWordOverride: (() -> Unit)? = null,
    onClearOverride: (() -> Unit)? = null,
    onT9PredictiveDigit: ((Char) -> Unit)? = null,
    onT9TraceUpdated: ((predictiveDigits: String, multiTapTrace: String) -> Unit)? = null,
    onGlideCommitted: ((committedWord: String, alternatives: List<String>) -> Unit)? = null,
    t9Engine: T9PredictiveEngine? = null,
    glideDecoder: DTWGlideDecoder? = null,
    clipboardHistoryContent: (@Composable () -> Unit)? = null,
    onHapticOverride: (() -> Unit)? = null,
    onSoundOverride: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    // Cover display height in dp, used with ClamshellLayoutSpec to cap total keyboard height.
    val screenHeightDp = config.screenHeightDp.dp
    val view = LocalView.current
    val density = LocalDensity.current
    // Hardware vibrator cached once for key-tap feedback when no custom haptic override is provided.
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    val themePalette = customization.themePalette

    // Current layout mode displayed on screen (QWERTY, T9, Symbols, etc.), synced with initialMode.
    var activeMode by remember { mutableStateOf(initialMode) }
    LaunchedEffect(initialMode) {
        activeMode = initialMode
    }

    // Current letter casing state and timestamp of the last Shift tap (for 350ms double-tap Caps Lock).
    var shiftState by remember { mutableStateOf(CoverShiftState.LOWERCASE) }
    var lastShiftTapMs by remember { mutableLongStateOf(-1000L) }
    // Local fallback state holders used only when the host leaves customization callbacks null.
    var localDockAnchor by remember { mutableStateOf(customization.dockAnchor) }
    var localOneHanded by remember { mutableStateOf(customization.oneHandedEnabled) }
    var localExtraHeightDp by remember { mutableFloatStateOf(customization.extraHeightDp) }
    var localT9Predictive by remember { mutableStateOf(customization.isT9Predictive) }
    // Mirror of typed text used for auto-caps/autocorrect when the host does not pass textBeforeCursor.
    var internalBuffer by remember { mutableStateOf("") }
    // Snapshot of text erased by Clear so a follow-up tap can undo the clear if the field is still empty.
    var lastClearedText by remember { mutableStateOf<String?>(null) }
    // Live finger points sampled during a QWERTY swipe gesture to draw the glowing glide trail.
    var activeGlideTrail by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // Resolve controlled (host-persisted) vs. local (uncontrolled) settings for resize, one-handed, and T9.
    val effectiveExtraHeightDp = if (customization.onExtraHeightChanged != null) {
        customization.extraHeightDp
    } else {
        localExtraHeightDp
    }
    val effectiveOneHanded = if (customization.onOneHandedToggle != null) {
        customization.oneHandedEnabled
    } else {
        localOneHanded
    }
    val effectiveDockAnchor = if (customization.onAnchorToggle != null) {
        customization.dockAnchor
    } else {
        localDockAnchor
    }
    val effectiveT9Predictive = if (customization.onT9PredictiveToggle != null) {
        customization.isT9Predictive
    } else {
        localT9Predictive
    }

    // Coordinator handling T9 digit sequences, multi-tap timing, autocorrect, and glide decoding.
    val inputManager = remember(bridge) {
        FlorisTextInputManager(
            bridge = bridge,
            initialMode = initialMode,
            t9EngineProvider = { t9Engine },
            glideDecoderProvider = { glideDecoder },
            autocorrectProvider = onAutocorrectWord,
            onT9TraceUpdated = onT9TraceUpdated
        )
    }
    inputManager.isT9Predictive = effectiveT9Predictive
    inputManager.onT9TraceUpdated = onT9TraceUpdated

    LaunchedEffect(activeMode) {
        inputManager.setLayoutMode(activeMode)
    }

    // Host-supplied text takes priority over internalBuffer for auto-cap and autocorrect lookbacks.
    val effectiveBeforeCursor = textBeforeCursor ?: internalBuffer
    // Auto-capitalize at the start of a field or immediately after sentence-ending punctuation.
    val autoCapActive = remember(effectiveBeforeCursor, shiftState, activeMode) {
        activeMode == CoverLayoutMode.QWERTY &&
            shiftState == CoverShiftState.LOWERCASE &&
            FlorisTextInputManager.shouldAutoCap(effectiveBeforeCursor)
    }
    val effectiveShiftState = if (autoCapActive) CoverShiftState.SINGLE_SHIFT else shiftState

    // Key rows rebuilt only when layout mode, shift state, or structural options change.
    val currentRows = remember(
        activeMode,
        effectiveShiftState,
        customization.compactSymbolsLayout,
        customization.includeClearKey
    ) {
        when (activeMode) {
            CoverLayoutMode.QWERTY -> CoverLayoutFactory.createQwertyRows(
                shiftState = effectiveShiftState,
                context = context,
                includeClearKey = customization.includeClearKey
            )
            CoverLayoutMode.T9_PREDICTIVE -> CoverLayoutFactory.createT9Rows(
                context = context,
                includeClearKey = customization.includeClearKey
            )
            CoverLayoutMode.SYMBOLS -> CoverLayoutFactory.createSymbolsRows(
                context = context,
                compactVariant = customization.compactSymbolsLayout
            )
            CoverLayoutMode.SYMBOLS_SECONDARY -> CoverLayoutFactory.createSymbolsSecondaryRows(context = context)
            CoverLayoutMode.NUMERIC -> CoverLayoutFactory.createNumericRows(
                context = context,
                includeClearKey = customization.includeClearKey
            )
        }
    }

    // Clamped height per key row based on screen budget, row count, and drag-resize offset.
    val effectiveRowHeight = remember(screenHeightDp, currentRows.size, effectiveExtraHeightDp) {
        val spec = layoutSpec.copy(extraHeightDp = effectiveExtraHeightDp)
        spec.calculateRowHeight(screenHeightDp, currentRows.size)
    }

    fun performFeedback() {
        if (onHapticOverride != null) {
            onHapticOverride()
        } else if (Settings.System.getInt(
                context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1
            ) != 0
        ) {
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
        onSoundOverride?.invoke()
    }

    fun emitChar(char: Char, lettersHint: String = "") {
        lastClearedText = null
        if (activeMode == CoverLayoutMode.T9_PREDICTIVE && char in '1'..'9') {
            if (effectiveT9Predictive && char in '2'..'9' && onT9PredictiveDigit != null) {
                onT9PredictiveDigit(char)
            } else {
                inputManager.handleT9DigitTap(char, lettersHint = lettersHint, nowMs = SystemClock.uptimeMillis())
            }
        } else {
            internalBuffer += char
            inputManager.resetT9Composing()
            bridge.insertText(char.toString())
        }
        if (char.isLetter() && shiftState == CoverShiftState.SINGLE_SHIFT) {
            shiftState = CoverShiftState.LOWERCASE
        }
    }

    fun emitBackspace() {
        lastClearedText = null
        if (internalBuffer.isNotEmpty()) {
            internalBuffer = internalBuffer.dropLast(1)
        }
        inputManager.handleBackspace()
    }

    fun commitWord(candidate: String, trailingSpace: Boolean = false) {
        lastClearedText = null
        inputManager.resetT9Composing()
        if (onCandidateSelected != null) {
            val formatted = if (trailingSpace && !candidate.endsWith(" ")) "$candidate " else candidate
            onCandidateSelected(formatted)
            val wordLen = effectiveBeforeCursor.takeLastWhile { it.isLetterOrDigit() }.length
            internalBuffer = effectiveBeforeCursor.dropLast(wordLen) + formatted
        } else {
            bridge.commitCandidate(candidate, trailingSpace = trailingSpace)
        }
    }

    fun handleSpaceKey() {
        lastClearedText = null
        inputManager.resetT9Composing()
        val currentWord = effectiveBeforeCursor.takeLastWhile { it.isLetter() }
        if (customization.autocorrectEnabled &&
            customization.suggestionsEnabled &&
            activeMode == CoverLayoutMode.QWERTY &&
            currentWord.isNotEmpty()
        ) {
            val corrected = onAutocorrectWord?.invoke(currentWord) ?: currentWord
            if (corrected != currentWord) {
                commitWord(corrected, trailingSpace = true)
                return
            }
        }
        emitChar(' ')
    }

    val oneHandedAlignment = when {
        !effectiveOneHanded -> Alignment.Center
        effectiveDockAnchor == ClamshellDockAnchor.RIGHT -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        color = themePalette.keyboardSurface,
        contentColor = themePalette.keyForeground,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = oneHandedAlignment
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(if (effectiveOneHanded) 0.82f else 1f)
                    .padding(start = 5.dp, end = 5.dp, top = 2.dp, bottom = 5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. Unified One UI / Gboard Top Bar (Suggestions when typing, Quick Tools when idle)
                CoverKeyboardTopBar(
                    candidates = candidates,
                    selectedIndex = selectedCandidateIndex,
                    suggestionsVisible = customization.suggestionsEnabled && customization.suggestionStripVisible,
                    onCandidateSelected = { _, candidate ->
                        commitWord(candidate, trailingSpace = false)
                    },
                    onLearnCandidate = onLearnCandidate,
                    onSuppressCandidate = onSuppressCandidate,
                    activeMode = activeMode,
                    showModeSwitchChip = customization.showModeSwitchChip,
                    onToggleMode = {
                        activeMode = if (activeMode == CoverLayoutMode.T9_PREDICTIVE) {
                            CoverLayoutMode.QWERTY
                        } else {
                            CoverLayoutMode.T9_PREDICTIVE
                        }
                        onModeChanged?.invoke(activeMode)
                    },
                    extraHeightDp = effectiveExtraHeightDp,
                    onExtraHeightChanged = { nextExtra ->
                        if (customization.onExtraHeightChanged != null) {
                            customization.onExtraHeightChanged.invoke(nextExtra)
                        } else {
                            localExtraHeightDp = nextExtra
                        }
                    },
                    oneHandedEnabled = effectiveOneHanded,
                    onOneHandedToggle = {
                        if (customization.onOneHandedToggle != null) {
                            customization.onOneHandedToggle.invoke()
                        } else {
                            localOneHanded = !localOneHanded
                        }
                    },
                    dockAnchor = effectiveDockAnchor,
                    onAnchorToggle = {
                        if (customization.onAnchorToggle != null) {
                            customization.onAnchorToggle.invoke()
                        } else {
                            localDockAnchor = localDockAnchor.toggled()
                        }
                    },
                    isT9Predictive = effectiveT9Predictive,
                    onT9PredictiveToggle = if (activeMode == CoverLayoutMode.T9_PREDICTIVE) {
                        {
                            if (customization.onT9PredictiveToggle != null) {
                                customization.onT9PredictiveToggle.invoke()
                            } else {
                                localT9Predictive = !localT9Predictive
                            }
                            inputManager.resetT9Composing()
                        }
                    } else null,
                    isVoiceInputActive = customization.isVoiceInputActive,
                    onVoiceInputToggle = customization.onVoiceInputToggle,
                    onDismissRequest = onDismissRequest,
                    onHaptic = ::performFeedback,
                    themePalette = themePalette,
                    trailingContent = headerExtraContent?.let { extra -> { extra() } }
                )

                // Optional clipboard history row slot
                clipboardHistoryContent?.invoke()

                // 2. Keyboard Key Rows + Interactive Glide-Typing Trail Overlay
                // Total vertical budget reserved for all key rows plus inter-row spacing.
                val maxRowsHeight = effectiveRowHeight * currentRows.size +
                    layoutSpec.rowSpacingDp * (currentRows.size - 1).coerceAtLeast(0)
                // Swipe-to-type is active only on QWERTY when the host supplies a DTWGlideDecoder.
                val glideEnabled = activeMode == CoverLayoutMode.QWERTY && glideDecoder != null

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxRowsHeight)
                        .testTag("floris_key_grid_container")
                        .pointerInput(glideEnabled) {
                            if (!glideEnabled) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial
                                )
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                if (w <= 0f || h <= 0f) return@awaitEachGesture

                                // Only initiate letter glide when starting in the 3 letter rows
                                // (excluding Shift on row 2 left and Backspace on row 2 right).
                                val inLetterRows = down.position.y < h * 0.75f
                                val inRow2ModifierZone = down.position.y >= h * 0.50f &&
                                    (down.position.x < w * 0.14f || down.position.x > w * 0.86f)
                                if (!inLetterRows || inRow2ModifierZone) {
                                    return@awaitEachGesture
                                }

                                // Recorded finger path samples for both DTW decoding and visual trail rendering.
                                val sampledPoints = mutableListOf(down.position)
                                // Require sliding ~1.15 key widths before switching from tap to glide mode.
                                val glideThresholdPx = (w / 10f) * 1.15f
                                // True once the finger crosses glideThresholdPx and claims the gesture.
                                var isGliding = false
                                var active = true

                                while (active) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) {
                                        active = false
                                        if (isGliding && sampledPoints.size >= 2) {
                                            change?.consume()
                                            val normalized = normalizeGlidePathToQwerty(sampledPoints, w, h)
                                            val decoded = inputManager.handleGlideGesture(normalized)
                                            val topWord = decoded.firstOrNull()?.word
                                            if (topWord != null) {
                                                // Mirror space-separated word commit into the local fallback buffer.
                                                internalBuffer = if (internalBuffer.isNotEmpty() && !internalBuffer.last().isWhitespace()) {
                                                    "$internalBuffer $topWord"
                                                } else {
                                                    internalBuffer + topWord
                                                }
                                                onGlideCommitted?.invoke(topWord, decoded.map { it.word })
                                            }
                                        }
                                        activeGlideTrail = emptyList()
                                    } else {
                                        val pos = change.position
                                        val distFromStart = hypot(
                                            pos.x - down.position.x,
                                            pos.y - down.position.y
                                        )
                                        if (!isGliding && distFromStart >= glideThresholdPx) {
                                            isGliding = true
                                        }
                                        if (isGliding) {
                                            val lastPt = sampledPoints.last()
                                            // Downsample points spaced at least 4px apart to keep DTW fast.
                                            if (hypot(pos.x - lastPt.x, pos.y - lastPt.y) >= 4f) {
                                                sampledPoints.add(pos)
                                                activeGlideTrail = sampledPoints.toList()
                                            }
                                            // Consume move events once gliding so individual keys abort their tap.
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxRowsHeight),
                        verticalArrangement = Arrangement.spacedBy(layoutSpec.rowSpacingDp)
                    ) {
                        currentRows.forEachIndexed { rowIndex, rowKeys ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(effectiveRowHeight),
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                val centerQwertyRow2 = activeMode == CoverLayoutMode.QWERTY && rowIndex == 1
                                if (centerQwertyRow2) {
                                    Spacer(modifier = Modifier.weight(0.45f))
                                }
                                rowKeys.forEach { keyItem ->
                                    CoverKeyView(
                                        item = keyItem,
                                        imeOptions = customization.imeOptions,
                                        shiftState = effectiveShiftState,
                                        themePalette = themePalette,
                                        showKeyPreview = customization.popupKeyPreviewEnabled && !keyItem.isFunctional,
                                        onFeedback = ::performFeedback,
                                        onAction = { action ->
                                            when (action) {
                                                is CoverKeyAction.InputChar -> emitChar(action.char, keyItem.t9Chars)
                                                is CoverKeyAction.InputText -> {
                                                    lastClearedText = null
                                                    internalBuffer += action.text
                                                    inputManager.resetT9Composing()
                                                    bridge.insertText(action.text)
                                                }
                                                is CoverKeyAction.T9Tap -> {
                                                    emitChar(action.digit, action.letters)
                                                }
                                                CoverKeyAction.Backspace -> emitBackspace()
                                                CoverKeyAction.DeleteWord -> {
                                                    lastClearedText = null
                                                    inputManager.resetT9Composing()
                                                    if (onDeleteWordOverride != null) {
                                                        onDeleteWordOverride()
                                                    } else {
                                                        val len = Regex("\\S+\\s*$")
                                                            .find(effectiveBeforeCursor)?.value?.length ?: 1
                                                        repeat(len) { emitBackspace() }
                                                    }
                                                }
                                                CoverKeyAction.ClearOrUndo -> {
                                                    if (effectiveBeforeCursor.isEmpty() && lastClearedText != null) {
                                                        val restored = lastClearedText.orEmpty()
                                                        lastClearedText = null
                                                        restored.forEach { ch -> emitChar(ch) }
                                                    } else if (activeMode == CoverLayoutMode.NUMERIC && effectiveBeforeCursor.isNotEmpty()) {
                                                        lastClearedText = effectiveBeforeCursor
                                                        internalBuffer = ""
                                                        inputManager.resetT9Composing()
                                                        if (onClearOverride != null) {
                                                            onClearOverride()
                                                        } else {
                                                            bridge.clearAll()
                                                        }
                                                    }
                                                }
                                                CoverKeyAction.Space -> handleSpaceKey()
                                                CoverKeyAction.Enter -> {
                                                    inputManager.resetT9Composing()
                                                    bridge.finishInput()
                                                }
                                                CoverKeyAction.Shift -> {
                                                    val now = SystemClock.uptimeMillis()
                                                    shiftState = shiftState.nextState(lastShiftTapMs, now)
                                                    lastShiftTapMs = now
                                                }
                                                CoverKeyAction.SwitchToSymbols -> {
                                                    activeMode = if (activeMode == CoverLayoutMode.SYMBOLS) {
                                                        CoverLayoutMode.QWERTY
                                                    } else {
                                                        CoverLayoutMode.SYMBOLS
                                                    }
                                                    onModeChanged?.invoke(activeMode)
                                                }
                                                CoverKeyAction.SwitchToSymbolsSecondary -> {
                                                    activeMode = CoverLayoutMode.SYMBOLS_SECONDARY
                                                    onModeChanged?.invoke(activeMode)
                                                }
                                                CoverKeyAction.SwitchToLetters -> {
                                                    activeMode = CoverLayoutMode.QWERTY
                                                    onModeChanged?.invoke(activeMode)
                                                }
                                                CoverKeyAction.SwitchMode -> {
                                                    activeMode = if (activeMode == CoverLayoutMode.QWERTY) {
                                                        CoverLayoutMode.T9_PREDICTIVE
                                                    } else {
                                                        CoverLayoutMode.QWERTY
                                                    }
                                                    onModeChanged?.invoke(activeMode)
                                                }
                                                CoverKeyAction.ToggleT9 -> {
                                                    activeMode = CoverLayoutMode.T9_PREDICTIVE
                                                    onModeChanged?.invoke(activeMode)
                                                }
                                                CoverKeyAction.Dismiss -> onDismissRequest?.invoke()
                                            }
                                        },
                                        onLongPressAction = {
                                            when {
                                                keyItem.action == CoverKeyAction.ClearOrUndo -> {
                                                    if (effectiveBeforeCursor.isNotEmpty()) {
                                                        lastClearedText = effectiveBeforeCursor
                                                        internalBuffer = ""
                                                        inputManager.resetT9Composing()
                                                        if (onClearOverride != null) {
                                                            onClearOverride()
                                                        } else {
                                                            bridge.clearAll()
                                                        }
                                                    }
                                                    true
                                                }
                                                keyItem.popupSet.main != null -> {
                                                    val mainPopup = keyItem.popupSet.main.label
                                                    if (mainPopup.length == 1) {
                                                        emitChar(mainPopup[0])
                                                    } else if (mainPopup.isNotEmpty()) {
                                                        bridge.insertText(mainPopup)
                                                    }
                                                    true
                                                }
                                                else -> false
                                            }
                                        },
                                        onPopupOptionSelected = { option ->
                                            if (option.length == 1) {
                                                emitChar(option[0])
                                            } else {
                                                bridge.insertText(option)
                                            }
                                        },
                                        onMoveCursor = { delta ->
                                            bridge.moveCursor(delta)
                                        }
                                    )
                                }
                                if (centerQwertyRow2) {
                                    Spacer(modifier = Modifier.weight(0.45f))
                                }
                            }
                        }
                    }

                    // Interactive Glide-Typing Neon Trail Overlay
                    if (activeGlideTrail.size >= 2) {
                        Canvas(
                            modifier = Modifier
                                .matchParentSize()
                                .testTag("floris_glide_trail_canvas")
                        ) {
                            val path = Path().apply {
                                val first = activeGlideTrail.first()
                                moveTo(first.x, first.y)
                                for (i in 1 until activeGlideTrail.size) {
                                    val prev = activeGlideTrail[i - 1]
                                    val curr = activeGlideTrail[i]
                                    val midX = (prev.x + curr.x) / 2f
                                    val midY = (prev.y + curr.y) / 2f
                                    quadraticTo(prev.x, prev.y, midX, midY)
                                }
                                val last = activeGlideTrail.last()
                                lineTo(last.x, last.y)
                            }
                            drawPath(
                                path = path,
                                color = themePalette.glideTrailGlow,
                                style = Stroke(
                                    width = 10.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                            drawPath(
                                path = path,
                                color = themePalette.glideTrailPrimary,
                                style = Stroke(
                                    width = 4.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual key button within a FlorisBoard row supporting:
 * - Snygg-style [FlorisThemePalette] semantic styling
 * - Touch cancellation when sliding finger out of bounds
 * - Hold-to-repeat on Backspace + swipe-left to delete word
 * - Long-press `PopupSet.main` emission or `FlorisExtendedPopupMenu` (`PopupSet.relevant`)
 * - Horizontal drag cursor scrub on Spacebar
 * - Floating `FlorisKeyPreviewBubble` while pressed
 */
@Composable
private fun RowScope.CoverKeyView(
    item: CoverKeyItem,
    imeOptions: Int,
    shiftState: CoverShiftState,
    themePalette: FlorisThemePalette,
    showKeyPreview: Boolean,
    onFeedback: () -> Unit,
    onAction: (CoverKeyAction) -> Unit,
    onLongPressAction: () -> Boolean,
    onPopupOptionSelected: (String) -> Unit,
    onMoveCursor: ((Int) -> Unit)? = null
) {
    // True while the user's finger is touching inside (or dragging from) this key.
    var isPressed by remember { mutableStateOf(false) }
    // Controls visibility of the long-press alternate character strip (e.g. accented letters).
    var popupMenuExpanded by remember { mutableStateOf(false) }
    // Running horizontal pixel counter on Spacebar; emits one cursor step per 24dp dragged.
    var cursorDragPixels by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    // Fresh callback holders so pointerInput coroutines always call the latest lambdas without restarting.
    val onActionState = rememberUpdatedState(onAction)
    val onLongPressState = rememberUpdatedState(onLongPressAction)
    val onFeedbackState = rememberUpdatedState(onFeedback)
    val onMoveCursorState = rememberUpdatedState(onMoveCursor)

    // Pre-classified action flags used to wire special gestures (repeat delete, space scrub, Enter icon).
    val isBackspace = item.action == CoverKeyAction.Backspace
    val isSpace = item.action == CoverKeyAction.Space
    val isEnter = item.action == CoverKeyAction.Enter
    val isShift = item.action == CoverKeyAction.Shift
    val isClear = item.action == CoverKeyAction.ClearOrUndo

    // Match the Enter key icon and TalkBack description to the target text box's IME action.
    val (enterIcon, enterDescription) = remember(imeOptions) {
        when (imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> Icons.Rounded.Search to "Search"
            EditorInfo.IME_ACTION_SEND -> Icons.AutoMirrored.Rounded.Send to "Send"
            EditorInfo.IME_ACTION_GO -> Icons.AutoMirrored.Rounded.ArrowForward to "Go"
            EditorInfo.IME_ACTION_NEXT -> Icons.AutoMirrored.Rounded.ArrowForward to "Next"
            EditorInfo.IME_ACTION_DONE -> Icons.Rounded.Check to "Done"
            else -> Icons.AutoMirrored.Rounded.KeyboardReturn to "Enter"
        }
    }

    // Vector glyph shown instead of text for Backspace, Clear, and Enter keys.
    val iconVector: ImageVector? = when {
        isBackspace -> Icons.AutoMirrored.Rounded.Backspace
        isClear -> Icons.Rounded.Clear
        isEnter -> enterIcon
        else -> null
    }

    // Spoken label announced by accessibility services such as TalkBack.
    val accessibleDescription = when {
        isBackspace -> "Backspace"
        isSpace -> "Space"
        isClear -> "Clear"
        isEnter -> enterDescription
        isShift -> "Shift"
        else -> item.primaryLabel
    }

    // Visual styling tokens computed for resting, pressed, Shift-active, and Caps-Lock states.
    val isShiftActive = isShift && shiftState != CoverShiftState.LOWERCASE
    val isShiftLocked = isShift && shiftState == CoverShiftState.CAPS_LOCK
    val idleBg = when {
        isEnter -> themePalette.actionKeyBackground
        isShiftActive -> themePalette.shiftActiveBackground
        item.isFunctional -> themePalette.functionalKeyBackground
        else -> themePalette.keyBackground
    }
    val pressedBg = lerp(idleBg, Color.White, 0.14f)
    val bg = if (isPressed) pressedBg else idleBg
    val fgColor = when {
        isEnter -> themePalette.actionKeyForeground
        isShiftActive -> themePalette.shiftActiveForeground
        isClear -> themePalette.destructiveKeyForeground
        item.isFunctional -> themePalette.functionalKeyForeground
        else -> themePalette.keyForeground
    }
    // Subtle 4% shrink on press for tactile key-cap depth.
    val keyScale = if (isPressed) 0.96f else 1f

    // Enables the 400ms long-press coroutine only on keys that actually support a secondary action.
    val hasLongPress = isClear || item.popupSet.main != null || item.popupOptions.isNotEmpty()
    val keyShape = RoundedCornerShape(6.dp)

    Box(
        modifier = Modifier
            .weight(item.weight)
            .fillMaxHeight()
            .defaultMinSize(minWidth = 26.dp, minHeight = 28.dp)
            .then(if (item.tag != null) Modifier.testTag(item.tag) else Modifier)
            .scale(keyScale)
            .clip(keyShape)
            .background(bg)
            .then(
                if (isShiftLocked) {
                    Modifier.border(1.5.dp, themePalette.shiftLockedBorder, keyShape)
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = accessibleDescription
                onClick {
                    onActionState.value(item.action)
                    true
                }
            }
            .pointerInput(item.id, isBackspace, isSpace, hasLongPress) {
                val scope = CoroutineScope(currentCoroutineContext())
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    onFeedbackState.value()
                    // Gesture-phase flags: long-press consumed, horizontal drag started, or finger lifted.
                    var held = false
                    var dragged = false
                    var released = false
                    var lastX = down.position.x
                    cursorDragPixels = 0f

                    // Background timer driving repeat-backspace (350ms delay, 60ms ticks) or 400ms long-press popups.
                    val holdJob: Job? = if (isBackspace || hasLongPress) {
                        scope.launch {
                            if (isBackspace) {
                                delay(350L)
                                while (isPressed && !dragged) {
                                    onFeedbackState.value()
                                    onActionState.value(CoverKeyAction.Backspace)
                                    delay(60L)
                                }
                            } else {
                                delay(400L)
                                if (isPressed && !dragged) {
                                    held = true
                                    onFeedbackState.value()
                                    val handledDirectly = onLongPressState.value()
                                    if (!handledDirectly && item.popupOptions.isNotEmpty()) {
                                        popupMenuExpanded = true
                                    }
                                }
                            }
                        }
                    } else null

                    while (!released) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        if (pointer == null || !pointer.pressed || pointer.isConsumed) {
                            released = true
                            val inBoundsOnRelease = pointer != null &&
                                !pointer.isConsumed &&
                                pointer.position.x in 0f..size.width.toFloat() &&
                                pointer.position.y in 0f..size.height.toFloat()
                            // Only emit tap if the finger lifted inside key bounds and wasn't claimed by glide/drag.
                            val wasTap = inBoundsOnRelease && pointer.previousPressed
                            isPressed = false
                            holdJob?.cancel()
                            if (wasTap && !held && !dragged) {
                                onActionState.value(item.action)
                            }
                        } else {
                            val inBounds = pointer.position.x in 0f..size.width.toFloat() &&
                                pointer.position.y in 0f..size.height.toFloat()
                            if (isBackspace || isSpace) {
                                // Horizontal drag beyond 24px triggers Backspace swipe-delete-word or Spacebar cursor scrub.
                                val totalDx = pointer.position.x - down.position.x
                                if (abs(totalDx) >= 24f) {
                                    if (!dragged && isBackspace && totalDx < 0f) {
                                        onActionState.value(CoverKeyAction.DeleteWord)
                                    }
                                    dragged = true
                                    holdJob?.cancel()
                                    if (isSpace) {
                                        val deltaX = pointer.position.x - lastX
                                        cursorDragPixels += deltaX
                                        // Emit 1 cursor character step for each 24dp dragged left/right.
                                        val stepPx = with(density) { 24.dp.toPx() }
                                        val chars = (cursorDragPixels / stepPx).roundToInt()
                                        if (chars != 0) {
                                            onMoveCursorState.value?.invoke(chars)
                                            cursorDragPixels -= chars * stepPx
                                        }
                                    }
                                }
                                lastX = pointer.position.x
                                isPressed = inBounds || dragged
                            } else {
                                isPressed = inBounds
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isShift) {
            ShiftKeyIcon(shiftState = shiftState, tint = fgColor)
        } else if (iconVector != null) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(18.dp)
            )
        } else if (isSpace) {
            Text(
                text = if (item.t9Chars.isNotEmpty() || item.primaryLabel == "0") "0  ␣" else "English",
                color = themePalette.keySecondaryForeground,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        } else if (item.t9Chars.isNotEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.primaryLabel,
                    color = fgColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = item.t9Chars.uppercase(),
                    color = themePalette.keySecondaryForeground,
                    fontSize = 8.5.sp,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }
        } else {
            val hasSecondaryHint = !item.secondaryLabel.isNullOrEmpty()
            if (hasSecondaryHint) {
                Text(
                    text = item.secondaryLabel.orEmpty(),
                    color = themePalette.keySecondaryForeground.copy(alpha = 0.72f),
                    fontSize = 8.sp,
                    lineHeight = 8.sp,
                    maxLines = 1,
                    softWrap = false,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 3.dp)
                        .clearAndSetSemantics {}
                )
            }
            Text(
                text = item.primaryLabel,
                color = fgColor,
                fontSize = when {
                    item.primaryLabel.length > 3 -> 10.sp
                    item.primaryLabel.length > 2 -> 11.sp
                    else -> 17.sp
                },
                maxLines = 1,
                softWrap = false,
                fontWeight = if (item.isFunctional) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = if (hasSecondaryHint) Modifier.padding(top = 5.dp) else Modifier
            )
        }

        if (showKeyPreview && !isShift && !isSpace && iconVector == null && isPressed) {
            FlorisKeyPreviewBubble(
                label = item.primaryLabel,
                themePalette = themePalette
            )
        }

        FlorisExtendedPopupMenu(
            expanded = popupMenuExpanded,
            options = item.popupOptions,
            onDismiss = { popupMenuExpanded = false },
            onOptionSelected = onPopupOptionSelected,
            themePalette = themePalette
        )
    }
}

/**
 * Vector canvas drawing the Shift arrow icon across lowercase (outline), single-shift
 * (filled arrow), and Caps Lock (filled arrow + bottom lock bar) states.
 */
@Composable
private fun ShiftKeyIcon(shiftState: CoverShiftState, tint: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        // Fill the arrow interior whenever shift or Caps Lock is engaged.
        val isFilled = shiftState != CoverShiftState.LOWERCASE
        // Shorten the arrow stem slightly in Caps Lock to leave room for the underline lock bar.
        val arrowBottom = if (shiftState == CoverShiftState.CAPS_LOCK) h * 0.76f else h * 0.88f
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.92f, h * 0.54f)
            lineTo(w * 0.68f, h * 0.54f)
            lineTo(w * 0.68f, arrowBottom)
            lineTo(w * 0.32f, arrowBottom)
            lineTo(w * 0.32f, h * 0.54f)
            lineTo(w * 0.08f, h * 0.54f)
            close()
        }
        if (isFilled) {
            drawPath(path = path, color = tint)
        } else {
            drawPath(
                path = path,
                color = tint,
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        if (shiftState == CoverShiftState.CAPS_LOCK) {
            drawLine(
                color = tint,
                start = Offset(w * 0.28f, h * 0.92f),
                end = Offset(w * 0.72f, h * 0.92f),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
