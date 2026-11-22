package com.tyejaedon.coverscreenos.overlay.input

import android.R
import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.tyejaedon.coverscreenos.datastore.KeyboardAnchor
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.helpers.CoverDisplayHelper
import com.tyejaedon.coverscreenos.keyboard.floris.ClamshellDockAnchor
import com.tyejaedon.coverscreenos.keyboard.floris.CoverEditorBridge
import com.tyejaedon.coverscreenos.keyboard.floris.CoverFlorisKeyboardSurface
import com.tyejaedon.coverscreenos.keyboard.floris.CoverLayoutMode
import com.tyejaedon.coverscreenos.keyboard.floris.FlorisKeyboardCustomization
import com.tyejaedon.coverscreenos.keyboard.floris.FlorisThemePalette
import com.tyejaedon.coverscreenos.keyboard.nlp.CoverKeyboardNlpManager
import com.tyejaedon.coverscreenos.keyboard.voice.rememberVoiceDictationController
import com.tyejaedon.coverscreenos.overlay.surface.CoverComposeSurface
import com.tyejaedon.coverscreenos.overlay.surface.CoverSurfaceWindowConfig
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.ClipboardActionIcons
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.ClipboardHistoryRow
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.ComposingTextPreview
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.HapticTier
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.rememberHapticPerformer
import com.tyejaedon.coverscreenos.ui.keyboard.rememberContactSuggestionRevision
import kotlinx.coroutines.launch


/**
 * Hosts and manages the Compose-based cover keyboard window (`TYPE_ACCESSIBILITY_OVERLAY`)
 * attached to the secondary cover display via [CoverComposeSurface].
 *
 * Uses [WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH] so taps outside the keyboard
 * trigger deferred focus-loss verification ([CoverInputSessionManager.onOutsideTouch]) without
 * stealing focus from the underlying editor, and toggles `FLAG_SECURE` via
 * [CoverComposeSurface.setSecure] whenever a password field is active.
 */
class CoverKeyboardOverlayManager(private val context: AccessibilityService) {

    /** Window-backed Compose overlay surface anchored as `TYPE_ACCESSIBILITY_OVERLAY`. */
    private val surface = CoverComposeSurface(
        hostContext = context,
        windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        logTag = TAG
    )
    /** Resolves the outer clamshell hardware display (`displayId == 1`). */
    private val displayHelper = CoverDisplayHelper(context)
    /** Bottom-docked overlay layout parameters watching outside touches without taking input focus. */
    private val windowConfig = CoverSurfaceWindowConfig(
        height = WindowManager.LayoutParams.WRAP_CONTENT,
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        extraFlags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        windowAnimations = R.style.Animation_InputMethod,
        cutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
    )

    /**
     * Attaches or updates the keyboard overlay on the cover display.
     *
     * @param isPassword Enables `FLAG_SECURE` on the overlay window so password masks and key
     *   presses are excluded from screenshots/screen recordings.
     * @return `true` if the overlay is attached on the cover display, `false` if no usable cover
     *   display exists or window attachment fails.
     */
    fun showOverlay(isPassword: Boolean = false): Boolean {
        val display = displayHelper.getCoverDisplay()
            ?: run {
                Log.e(TAG, "No usable cover display available for accessibility keyboard")
                return false
            }

        // Reuse the existing window when already attached to the same display; only update security flags.
        if (surface.isAttached() && surface.activeDisplayId() == display.displayId) {
            if (surface.setSecure(isPassword)) return true
            surface.detach()
            return false
        }

        surface.detach()
        surface.setSecure(isPassword)
        val attached = surface.attach(
            display = display,
            windowConfig = windowConfig,
            onOutsideTouch = { CoverInputSessionManager.onOutsideTouch() }
        ) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CoverKeyboardOverlayRoot(
                    onDismiss = { CoverInputSessionManager.dismissOverlay("User tapped close") }
                )
            }
        }
        if (!attached) {
            surface.detach()
            Log.e(TAG, "Failed to attach accessibility keyboard surface to display=${display.displayId}")
        }
        return attached
    }

    /** Returns `true` while the overlay view is currently attached to the window manager. */
    fun isAttached(): Boolean = surface.isAttached()

    /** Detaches the keyboard overlay from the cover display without destroying manager state. */
    fun hideOverlay() {
        surface.detach()
    }

    /** Detaches any active surface when the hosting [CoverInputAccessibilityService] unbinds. */
    fun destroy() {
        surface.detach()
    }
}

/**
 * Root Compose UI for the cover-screen virtual keyboard overlay.
 *
 * Renders:
 * - [CoverInputHeader]: connection status indicator, mode switcher chips (`123` / `T9` / `ABC` /
 *   `SYS`), and close button.
 * - [CoverLiveBufferDisplay]: live mirrored buffer (masked with `*` for password fields).
 * - Mode-specific [CoverFlorisKeyboardSurface]: numeric PIN keypad, T9 multi-tap/predictive keypad,
 *   or full QWERTY keyboard with NLP suggestions, glide typing, and clipboard history toolbar.
 */
@Composable
fun CoverKeyboardOverlayRoot(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Live session snapshot driving the header status, buffer preview, and active keypad mode.
    val sessionState by CoverInputSessionManager.sessionState.collectAsState()
    val modeHaptic = rememberHapticPerformer(HapticTier.Strong)
    // Dash-delimited multi-tap sequence (e.g. "44-33") reset whenever focus moves to a new input box.
    var t9DigitTrace by remember(
        sessionState.metadata.packageName,
        sessionState.metadata.viewIdResourceName,
        sessionState.metadata.boundsInScreen
    ) { mutableStateOf("") }
    // Counter bumped on T9 prediction commits/toggles to force candidate refresh and keypad re-keying.
    var t9CandidateCommitCount by remember(
        sessionState.metadata.packageName,
        sessionState.metadata.viewIdResourceName,
        sessionState.metadata.boundsInScreen
    ) { mutableIntStateOf(0) }

    val context = LocalContext.current
    val settingsStore = remember(context) { LauncherSettingsStore(context.applicationContext) }
    val settings by settingsStore.settings.collectAsState(initial = LauncherSettings())
    // Watch contact opt-in and READ_CONTACTS permission changes while the overlay is open.
    val contactRevision = rememberContactSuggestionRevision(
        enabled = settings.suggestionStripVisible && !sessionState.metadata.isPassword
    )
    LaunchedEffect(contactRevision) {
        if (contactRevision > 0 && !sessionState.metadata.isPassword) {
            runCatching { CoverKeyboardNlpManager.refreshContactWords(context) }
            t9CandidateCommitCount++
        }
    }
    // Speech-to-text dictation controller hidden automatically on password/numeric inputs.
    val voiceDictation = rememberVoiceDictationController(
        enabled = !sessionState.metadata.isPassword &&
            sessionState.keyboardMode != CoverKeyboardMode.NUMERIC_PIN,
        onPhraseRecognized = { phrase ->
            CoverInputSessionManager.commitDictatedText(phrase)
            t9DigitTrace = ""
            t9CandidateCommitCount++
        },
        onStatusChanged = CoverInputSessionManager::reportVoiceStatus
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .border(1.dp, Color(0xFF333338), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        color = Color(0xFF141416),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CoverInputHeader(
                metadata = sessionState.metadata,
                statusMessage = sessionState.lastStatusMessage,
                isSuccess = sessionState.isSuccessFeedback,
                activeMode = sessionState.keyboardMode,
                onModeSelected = { mode ->
                    modeHaptic()
                    if (mode != CoverKeyboardMode.T9_MULTITAP) t9DigitTrace = ""
                    CoverInputSessionManager.switchMode(mode)
                },
                onUseSystemKeyboard = {
                    modeHaptic()
                   // CoverInputSessionManager.relinquishToSystemKeyboard()
                },
                onDismiss = onDismiss
            )

            CoverLiveBufferDisplay(
                buffer = sessionState.buffer,
                isPassword = sessionState.metadata.isPassword,
                hint = sessionState.metadata.hintText
            )

            // Scope bound to this overlay composition for persisting user keyboard adjustments
            // (height, one-handed mode, dock anchor) asynchronously to DataStore.
            val coroutineScope = rememberCoroutineScope()
            val bridge = remember {
                CoverEditorBridge(
                    overlayCallbacks = CoverInputSessionManager.asOverlayInputCallbacks()
                )
            }
            val dockAnchor = if (settings.keyboardOneHandedAnchor == KeyboardAnchor.Right) {
                ClamshellDockAnchor.RIGHT
            } else {
                ClamshellDockAnchor.LEFT
            }
            val beforeCursor = sessionState.buffer.take(sessionState.cursorPosition)

            when (sessionState.keyboardMode) {
                CoverKeyboardMode.NUMERIC_PIN -> {
                    CoverFlorisKeyboardSurface(
                        bridge = bridge,
                        initialMode = CoverLayoutMode.NUMERIC,
                        onDismissRequest = {
                            CoverInputSessionManager.commitAndFinish()
                        },
                        textBeforeCursor = beforeCursor,
                        customization = FlorisKeyboardCustomization(
                            extraHeightDp = settings.keyboardExtraHeightDp,
                            onExtraHeightChanged = { newDp ->
                                coroutineScope.launch { settingsStore.setKeyboardExtraHeightDp(newDp) }
                            },
                            oneHandedEnabled = settings.keyboardOneHandedEnabled,
                            onOneHandedToggle = {
                                coroutineScope.launch {
                                    settingsStore.setKeyboardOneHandedEnabled(!settings.keyboardOneHandedEnabled)
                                }
                            },
                            dockAnchor = dockAnchor,
                            onAnchorToggle = {
                                val next = if (settings.keyboardOneHandedAnchor == KeyboardAnchor.Left) {
                                    KeyboardAnchor.Right
                                } else {
                                    KeyboardAnchor.Left
                                }
                                coroutineScope.launch { settingsStore.setKeyboardOneHandedAnchor(next) }
                            },
                            suggestionStripVisible = false,
                            suggestionsEnabled = false,
                            autocorrectEnabled = false,
                            popupKeyPreviewEnabled = false,
                            imeOptions = sessionState.metadata.imeAction,
                            showModeSwitchChip = false,
                            includeClearKey = true,
                            themePalette = FlorisThemePalette.AmoledCover
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                CoverKeyboardMode.T9_MULTITAP -> {
                    val suggestionsEnabled = !sessionState.metadata.isPassword &&
                        !sessionState.metadata.isNumeric && !sessionState.metadata.isPhoneNumber
                    val predictiveDigits = if (sessionState.isT9Predictive) {
                        sessionState.t9PredictiveDigits.takeIf { it.isNotEmpty() }
                    } else {
                        t9DigitTrace.takeIf { '-' in it }
                    }
                    val t9Word = beforeCursor.takeLastWhile { it.isLetter() }
                    val t9Candidates = remember(beforeCursor, predictiveDigits, suggestionsEnabled, t9CandidateCommitCount) {
                        if (!suggestionsEnabled) {
                            emptyList()
                        } else when {
                            !predictiveDigits.isNullOrEmpty() -> CoverKeyboardNlpManager.getT9Suggestions(predictiveDigits)
                            t9Word.isNotEmpty() -> CoverKeyboardNlpManager.getQwertySuggestions(t9Word)
                            else -> CoverKeyboardNlpManager.getNextWordSuggestions(
                                beforeCursor.trimEnd().takeLastWhile { it.isLetter() }
                            )
                        }
                    }
                    key(
                        sessionState.metadata.packageName,
                        sessionState.metadata.viewIdResourceName,
                        sessionState.metadata.boundsInScreen,
                        t9CandidateCommitCount
                    ) {
                        CoverFlorisKeyboardSurface(
                            bridge = bridge,
                            initialMode = CoverLayoutMode.T9_PREDICTIVE,
                            candidates = t9Candidates,
                            selectedCandidateIndex = if (t9Word.isNotEmpty() || !predictiveDigits.isNullOrEmpty()) 0 else -1,
                            onCandidateSelected = { candidate ->
                                CoverInputSessionManager.commitCandidate(candidate)
                                t9DigitTrace = ""
                                t9CandidateCommitCount++
                            },
                            onDismissRequest = {
                                t9DigitTrace = ""
                                CoverInputSessionManager.commitAndFinish()
                            },
                            onModeChanged = { newMode ->
                                if (newMode == CoverLayoutMode.QWERTY) {
                                    t9DigitTrace = ""
                                    CoverInputSessionManager.switchMode(CoverKeyboardMode.QWERTY)
                                }
                            },
                            textBeforeCursor = beforeCursor,
                            onT9PredictiveDigit = CoverInputSessionManager::tapPredictiveDigit,
                            onT9TraceUpdated = { _, multiTapTrace ->
                                t9DigitTrace = multiTapTrace
                            },
                            t9Engine = CoverKeyboardNlpManager.t9Engine,
                            customization = FlorisKeyboardCustomization(
                                extraHeightDp = settings.keyboardExtraHeightDp,
                                onExtraHeightChanged = { newDp ->
                                    coroutineScope.launch { settingsStore.setKeyboardExtraHeightDp(newDp) }
                                },
                                oneHandedEnabled = settings.keyboardOneHandedEnabled,
                                onOneHandedToggle = {
                                    coroutineScope.launch {
                                        settingsStore.setKeyboardOneHandedEnabled(!settings.keyboardOneHandedEnabled)
                                    }
                                },
                                dockAnchor = dockAnchor,
                                onAnchorToggle = {
                                    val next = if (settings.keyboardOneHandedAnchor == KeyboardAnchor.Left) {
                                        KeyboardAnchor.Right
                                    } else {
                                        KeyboardAnchor.Left
                                    }
                                    coroutineScope.launch { settingsStore.setKeyboardOneHandedAnchor(next) }
                                },
                                suggestionStripVisible = settings.suggestionStripVisible,
                                suggestionsEnabled = suggestionsEnabled,
                                autocorrectEnabled = false,
                                popupKeyPreviewEnabled = settings.popupKeyPreviewEnabled && !sessionState.metadata.isPassword,
                                imeOptions = sessionState.metadata.imeAction,
                                includeClearKey = true,
                                isT9Predictive = sessionState.isT9Predictive,
                                onT9PredictiveToggle = if (suggestionsEnabled) {
                                    {
                                        val next = !sessionState.isT9Predictive
                                        CoverInputSessionManager.setT9Predictive(next)
                                        t9DigitTrace = ""
                                        t9CandidateCommitCount++
                                    }
                                } else null,
                                isVoiceInputActive = voiceDictation.isListening,
                                onVoiceInputToggle = voiceDictation.onToggle,
                                themePalette = FlorisThemePalette.AmoledCover
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                CoverKeyboardMode.QWERTY -> {
                    val clipboardHistory by CoverInputSessionManager.clipboardHistory.collectAsState()
                    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
                    // Enables the Paste toolbar icon when the system clipboard currently holds an entry.
                    var canPasteFromClipboard by remember { mutableStateOf(false) }
                    LaunchedEffect(sessionState.isActive) {
                        canPasteFromClipboard = clipboardManager?.hasPrimaryClip() == true
                    }

                    // Suppress word suggestions and autocorrection for passwords, numbers, and phone inputs.
                    val suggestionsAllowed = !sessionState.metadata.isPassword &&
                        !sessionState.metadata.isNumeric &&
                        !sessionState.metadata.isPhoneNumber
                    // Active partial word directly left of the cursor used to query QWERTY completions.
                    val currentWord = beforeCursor.takeLastWhile { it.isLetterOrDigit() }

                    // Word suggestions currently displayed in the keyboard smartbar.
                    var candidates by remember { mutableStateOf<List<String>>(emptyList()) }
                    // Revision counter bumped on learn/suppress actions to recompute suggestion chips immediately.
                    var nlpRevision by remember { mutableIntStateOf(0) }
                    LaunchedEffect(
                        sessionState.buffer,
                        sessionState.cursorPosition,
                        nlpRevision,
                        contactRevision
                    ) {
                        if (suggestionsAllowed) {
                            candidates = when {
                                currentWord.isNotBlank() -> CoverKeyboardNlpManager.getQwertySuggestions(currentWord)
                                beforeCursor.isNotBlank() -> {
                                    val prev = beforeCursor.trimEnd().takeLastWhile { it.isLetter() }
                                    if (prev.isNotEmpty()) {
                                        CoverKeyboardNlpManager.getNextWordSuggestions(prev)
                                    } else emptyList()
                                }
                                else -> emptyList()
                            }
                        } else {
                            candidates = emptyList()
                        }
                    }

                    CoverFlorisKeyboardSurface(
                        bridge = bridge,
                        initialMode = CoverLayoutMode.QWERTY,
                        candidates = candidates,
                        onCandidateSelected = { candidate ->
                            CoverInputSessionManager.commitCandidate(candidate)
                        },
                        onDismissRequest = {
                            CoverInputSessionManager.commitAndFinish()
                        },
                        onModeChanged = { newMode ->
                            if (newMode == CoverLayoutMode.T9_PREDICTIVE) {
                                CoverInputSessionManager.switchMode(CoverKeyboardMode.T9_MULTITAP)
                            }
                        },
                        headerExtraContent = if (sessionState.metadata.isPassword) null else {
                            {
                                ClipboardActionIcons(
                                    onCopy = { CoverInputSessionManager.copySelection() },
                                    onCut = { CoverInputSessionManager.cutSelection() },
                                    onPaste = {
                                        if (CoverInputSessionManager.pasteText()) {
                                            canPasteFromClipboard = true
                                        }
                                    },
                                    canPaste = canPasteFromClipboard,
                                    modifier = Modifier.testTag("clipboard_toolbar")
                                )
                            }
                        },
                        clipboardHistoryContent = if (!sessionState.metadata.isPassword && clipboardHistory.isNotEmpty()) {
                            {
                                ClipboardHistoryRow(
                                    history = clipboardHistory,
                                    onHistorySnippetSelected = { snippet ->
                                        CoverInputSessionManager.pasteText(snippet)
                                    }
                                )
                            }
                        } else null,
                        customization = FlorisKeyboardCustomization(
                            extraHeightDp = settings.keyboardExtraHeightDp,
                            onExtraHeightChanged = { newDp ->
                                coroutineScope.launch { settingsStore.setKeyboardExtraHeightDp(newDp) }
                            },
                            oneHandedEnabled = settings.keyboardOneHandedEnabled,
                            onOneHandedToggle = {
                                coroutineScope.launch {
                                    settingsStore.setKeyboardOneHandedEnabled(!settings.keyboardOneHandedEnabled)
                                }
                            },
                            dockAnchor = dockAnchor,
                            onAnchorToggle = {
                                val next = if (settings.keyboardOneHandedAnchor == KeyboardAnchor.Left) {
                                    KeyboardAnchor.Right
                                } else {
                                    KeyboardAnchor.Left
                                }
                                coroutineScope.launch { settingsStore.setKeyboardOneHandedAnchor(next) }
                            },
                            suggestionStripVisible = settings.suggestionStripVisible,
                            suggestionsEnabled = suggestionsAllowed,
                            autocorrectEnabled = settings.autocorrectEnabled,
                            popupKeyPreviewEnabled = settings.popupKeyPreviewEnabled && !sessionState.metadata.isPassword,
                            imeOptions = sessionState.metadata.imeAction,
                            includeClearKey = true,
                            isVoiceInputActive = voiceDictation.isListening,
                            onVoiceInputToggle = voiceDictation.onToggle,
                            themePalette = FlorisThemePalette.AmoledCover
                        ),
                        textBeforeCursor = beforeCursor,
                        selectedCandidateIndex = if (currentWord.isNotEmpty()) 0 else -1,
                        onLearnCandidate = { _, word ->
                            CoverKeyboardNlpManager.learnWord(word, context)
                            nlpRevision++
                        },
                        onSuppressCandidate = { _, word ->
                            CoverKeyboardNlpManager.suppressWord(word, context)
                            nlpRevision++
                        },
                        onAutocorrectWord = CoverKeyboardNlpManager::autocorrectWord,
                        t9Engine = CoverKeyboardNlpManager.t9Engine,
                        glideDecoder = CoverKeyboardNlpManager.glideDecoder,
                        modifier = Modifier
                    )
                }
            }
        }
    }
}

/**
 * Compact header bar showing the target app package + injection status dot, keypad mode chips
 * (`123`, `T9`, `ABC`, `SYS`), and a dismiss button.
 */
@Composable
private fun CoverInputHeader(
    metadata: CoverFieldMetadata,
    statusMessage: String,
    isSuccess: Boolean,
    activeMode: CoverKeyboardMode,
    onModeSelected: (CoverKeyboardMode) -> Unit,
    onUseSystemKeyboard: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isSuccess) Color(0xFF00E676) else Color(0xFFFFB300))
            )
            Text(
                text = if (metadata.packageName.isBlank()) statusMessage else "${metadata.packageName}: $statusMessage",
                color = Color(0xFFE0E0E0),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeChip(
                label = "123",
                selected = activeMode == CoverKeyboardMode.NUMERIC_PIN,
                onClick = { onModeSelected(CoverKeyboardMode.NUMERIC_PIN) }
            )
            ModeChip(
                label = "T9",
                selected = activeMode == CoverKeyboardMode.T9_MULTITAP,
                onClick = { onModeSelected(CoverKeyboardMode.T9_MULTITAP) }
            )
            ModeChip(
                label = "ABC",
                selected = activeMode == CoverKeyboardMode.QWERTY,
                onClick = { onModeSelected(CoverKeyboardMode.QWERTY) }
            )
            // Hands the focused field over to whichever IME the user has set
            // as their system default (Samsung Keyboard, Gboard, etc.). The
            // cover overlay releases its hold; the system IME's soft keyboard
            // then attaches to the field naturally.
            ModeChip(
                label = "SYS",
                selected = false,
                onClick = onUseSystemKeyboard
            )

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF28282C))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** Selectable pill button used in [CoverInputHeader] to switch keyboard modes. */
@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(0xFF3B82F6) else Color(0xFF222226))
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else Color(0xFFB0B0B8)
        )
    }
}

/**
 * Live preview strip displaying the cover keyboard's local [buffer] (masked with `*` for
 * password inputs, or falling back to [hint] when empty) via [ComposingTextPreview].
 */
@Composable
private fun CoverLiveBufferDisplay(
    buffer: String,
    isPassword: Boolean,
    hint: String
) {
    val displayString = when {
        buffer.isEmpty() && hint.isNotEmpty() -> hint
        buffer.isEmpty() -> "Type or tap keys..."
        isPassword -> "*".repeat(buffer.length)
        else -> buffer
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1D1D21))
            .border(1.dp, Color(0xFF2C2C32), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        ComposingTextPreview(
            // Password masking / empty-state hint text are already resolved above, so the
            // primitive only ever sees the final display string — no double-masking.
            text = displayString,
            textStyle = LocalTextStyle.current.copy(
                fontSize = if (isPassword) 18.sp else 14.sp,
                fontFamily = if (isPassword) FontFamily.Monospace else FontFamily.Default,
                fontWeight = FontWeight.SemiBold
            ),
            textColor = if (buffer.isEmpty()) Color(0xFF757580) else Color.White,
            counterColor = Color(0xFF6B7280),
            // The empty-state placeholder is already baked into displayString above; don't
            // show a redundant "0 chars" counter while it's showing.
            showCharCount = buffer.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

