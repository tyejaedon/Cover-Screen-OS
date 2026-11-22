package com.tyejaedon.coverscreenos.overlay.input

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.keyboard.floris.CoverOverlayInputCallbacks
import com.tyejaedon.coverscreenos.keyboard.voice.formatDictatedPhrase
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.ClipboardHistory
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.TextBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Central coordinator for active cover-screen text input sessions.
 *
 * Bridges:
 * - [CoverInputAccessibilityService] (focus detection, node/InputConnection injection, focus-loss verification)
 * - [CoverKeyboardOverlayManager] (overlay window lifecycle on display 1)
 * - Compose keyboard surfaces ([CoverKeyboardOverlayRoot] / [CoverOverlayInputCallbacks])
 * - [LauncherSettingsStore] (per-package keyboard mode persistence via DataStore)
 *
 * All buffer mutations and injection calls are serialized with [injectionLock] (a synchronous
 * [ReentrantLock]) so rapid Compose tap callbacks never race against accessibility echo events.
 */
object CoverInputSessionManager {

    private val _sessionState = MutableStateFlow(CoverInputSessionState())
    /** Reactive session snapshot observed by the Compose keyboard overlay UI. */
    val sessionState: StateFlow<CoverInputSessionState> = _sessionState.asStateFlow()

    /** Bounded recent-first store of copied/cut snippets shown in the clipboard-history bar. */
    private val clipboardHistoryBuffer = ClipboardHistory()
    private val _clipboardHistory = MutableStateFlow<List<String>>(emptyList())
    /** Most-recent-first clipboard snippets for the Phase H clipboard-history strip. */
    val clipboardHistory: StateFlow<List<String>> = _clipboardHistory.asStateFlow()

    /** Currently connected accessibility service driving focus tracking and text injection. */
    private var activeAccessibilityService: CoverInputAccessibilityService? = null
    /** Overlay window controller that attaches/detaches the Compose keyboard on display 1. */
    private var overlayManager: CoverKeyboardOverlayManager? = null
    /** DataStore wrapper persisting per-package keyboard mode preferences. */
    private var settingsStore: LauncherSettingsStore? = null
    /** Main-thread coroutine scope hosting the DataStore settings collector while bound. */
    private var settingsScope: CoroutineScope? = null
    /** Active collector job cancelled on [unbindService] to avoid leaking scopes across reconnects. */
    private var settingsJob: Job? = null
    /** Dedicated IO coroutine scope for non-blocking per-package keyboard mode writes. */
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Cached map of package name -> user's preferred [CoverKeyboardMode] loaded from DataStore. */
    private var savedModes: Map<String, CoverKeyboardMode> = emptyMap()
    /** Prevents incoming DataStore emissions from overriding a mode the user explicitly picked this session. */
    private var modeChosenThisSession = false

    /**
     * Adapter that translates a [BufferDelta] into a minimal injection call
     * on the currently-bound accessibility service. Kept as a field so tests
     * can swap in a [TextInjectionTarget] fake without touching the object's
     * public API.
     */
    private var injectionTarget: TextInjectionTarget? = null

    /**
     * Composing-aware in-memory buffer. Keeps the overlay's view of the
     * field in a form that survives delta computation and can carry
     * composing regions from T9 multi-tap / prediction candidates through
     * to the injection layer without collapsing them on every keystroke.
     */
    private var textBuffer: TextBuffer = TextBuffer.Empty

    /**
     * Serializes concurrent `applyDelta` invocations. Rapid taps from the
     * keypad and asynchronous echo signals from
     * [CoverInputAccessibilityService.handleTextChangedEvent] can otherwise
     * interleave, producing race conditions where the injected text lags
     * the buffer or overwrites a still-in-flight edit.
     */
    private val injectionLock = ReentrantLock()

    /** Most recent full text string successfully written to the target editor (used for delta diffing & echo filtering). */
    private var lastInjectedText: String? = null
    /** `SystemClock.elapsedRealtime()` timestamp of the last successful text write into the target editor. */
    private var lastInjectionTimestamp: Long = 0L

    /**
     * Package name of a focused field the user explicitly opted out of the
     * cover keyboard for by tapping the "SYS" mode chip. While this matches the
     * currently focused package the cover overlay stays out of the way so the
     * user's default system IME (e.g. Samsung Keyboard) can drive the field.
     * Cleared as soon as focus moves to a field owned by a different package.
     */
    private var relinquishedPackage: String? = null

    /**
     * Binds a connected [CoverInputAccessibilityService], constructs the overlay manager and
     * production [AccessibilityServiceInjectionTarget], and begins collecting saved per-package
     * keyboard modes on [Dispatchers.Main.immediate].
     */
    fun bindService(service: CoverInputAccessibilityService) {
        activeAccessibilityService = service
        overlayManager = CoverKeyboardOverlayManager(service)
        injectionTarget = AccessibilityServiceInjectionTarget(service)
        settingsStore = LauncherSettingsStore(service.applicationContext)
        // Main.immediate ensures settings updates apply synchronously with UI state without
        // blocking the service's accessibility callback looper.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        settingsScope = scope
        settingsJob = scope.launch {
            settingsStore?.settings?.collect { settings ->
                savedModes = settings.keyboardModeByPackage
                val current = _sessionState.value
                // Only update keyboard mode dynamically if the user has not manually switched
                // modes or started typing in the active field yet.
                if (current.isActive && !modeChosenThisSession &&
                    current.metadata.packageName.isNotBlank() &&
                    current.buffer == current.metadata.initialText
                ) {
                    _sessionState.value = current.copy(
                        keyboardMode = compatibleKeyboardMode(
                            current.metadata, savedModes[current.metadata.packageName]
                        )
                    )
                }
            }
        }
    }

    /**
     * Tears down any active session, detaches the overlay window, releases service references,
     * and cancels DataStore collection jobs when the accessibility service is destroyed.
     */
    fun unbindService() {
        dismissOverlay(reason = "Service unbound")
        overlayManager?.destroy()
        overlayManager = null
        activeAccessibilityService = null
        injectionTarget = null
        settingsJob?.cancel()
        settingsScope?.coroutineContext?.get(Job)?.cancel()
        settingsJob = null
        settingsScope = null
        settingsStore = null
        savedModes = emptyMap()
    }

    /**
     * Test-only seam. Substitutes the accessibility-service-backed
     * [TextInjectionTarget] with a fake so `applyDelta` behaviour can be
     * asserted without spinning up the platform accessibility framework.
     *
     * Callers are responsible for calling [clearInjectionTargetForTest]
     * before another test rebinds the real service.
     */
    internal fun setInjectionTargetForTest(target: TextInjectionTarget) {
        injectionTarget = target
    }

    /** Resets test injection target and in-memory session state after unit tests. */
    internal fun clearInjectionTargetForTest() {
        injectionTarget = null
        textBuffer = TextBuffer.Empty
        _sessionState.value = CoverInputSessionState()
        savedModes = emptyMap()
    }

    /** Seeds per-package mode preferences directly for unit testing without DataStore I/O. */
    internal fun setSavedModesForTest(modes: Map<String, CoverKeyboardMode>) {
        savedModes = modes
    }

    /**
     * Handles an editable node gaining focus (or being tapped) on the cover display.
     *
     * Two main paths:
     * 1. **Same field re-focused**: cancels pending focus-loss timers and reconciles any external
     *    text or cursor changes (ignoring stale echoes of our own recent injection within
     *    [INJECTION_ECHO_IGNORE_WINDOW_MS]).
     * 2. **New field focused**: respects [relinquishedPackage] if the user opted into the system
     *    IME for that app; otherwise seeds the local [TextBuffer] (kept empty for password fields
     *    so existing masked bullets are never treated as literal characters) and attaches the
     *    overlay window on display 1.
     */
    fun onFieldFocused(metadata: CoverFieldMetadata) {
        val current = _sessionState.value
        if (current.isActive &&
            current.metadata.packageName == metadata.packageName &&
            current.metadata.viewIdResourceName == metadata.viewIdResourceName &&
            current.metadata.boundsInScreen == metadata.boundsInScreen &&
            current.metadata.hintText == metadata.hintText &&
            current.metadata.isNumeric == metadata.isNumeric &&
            current.metadata.isPhoneNumber == metadata.isPhoneNumber &&
            current.metadata.isPassword == metadata.isPassword
        ) {
            activeAccessibilityService?.cancelPendingFocusLossVerification()
            if (!metadata.isPassword) {
                injectionLock.withLock {
                    val state = _sessionState.value
                    val observedText = metadata.initialText
                    // Ignore accessibility snapshots that still show the pre-injection text shortly
                    // after we already pushed `state.buffer` to the editor.
                    val recentOldEcho = lastInjectionTimestamp > 0L &&
                        SystemClock.elapsedRealtime() - lastInjectionTimestamp < INJECTION_ECHO_IGNORE_WINDOW_MS &&
                        observedText == state.metadata.initialText &&
                        lastInjectedText == state.buffer
                    if (!recentOldEcho) {
                        val changedText = observedText != state.buffer
                        val selection = metadata.observedSelection(observedText)
                            ?: if (changedText) observedText.length..observedText.length
                            else textBuffer.selection
                        if (changedText || selection != textBuffer.selection) {
                            textBuffer = TextBuffer(observedText, selection, composing = null)
                            if (changedText) {
                                lastInjectedText = observedText
                                lastInjectionTimestamp = 0L
                                Log.d(TAG, "Focused editor changed externally; resetting local buffer")
                            }
                            _sessionState.value = state.copy(
                                buffer = observedText,
                                cursorPosition = selection.first,
                                metadata = metadata,
                                t9PredictiveDigits = "",
                                lastStatusMessage = if (changedText) "Field updated" else state.lastStatusMessage
                            )
                        }
                    }
                }
            }
            return
        }
        // If the user previously relinquished control to their default IME for
        // this same package, honour that choice and stay out of the way. Focus
        // in any *other* package resets the opt-out so the cover keyboard can
        // attach again by default.
        if (relinquishedPackage != null && relinquishedPackage != metadata.packageName) {
            relinquishedPackage = null
        }
        if (relinquishedPackage == metadata.packageName) {
            Log.d(TAG, "Skipping cover overlay for ${metadata.packageName}; user prefers system keyboard")
            _sessionState.value = CoverInputSessionState(isActive = false)
            overlayManager?.hideOverlay()
            activeAccessibilityService?.restoreSoftKeyboardMode()
            return
        }

        val defaultMode = compatibleKeyboardMode(metadata, savedModes[metadata.packageName])

        modeChosenThisSession = false
        // Do not read back masked bullet characters from password fields; accumulate locally and
        // inject once on commitAndFinish().
        val initialBuffer = if (metadata.isPassword) "" else metadata.initialText
        val initialSelection = if (metadata.isPassword) null else metadata.observedSelection(initialBuffer)
        val selection = initialSelection ?: initialBuffer.length..initialBuffer.length
        _sessionState.value = CoverInputSessionState(
            isActive = true,
            buffer = initialBuffer,
            cursorPosition = selection.first,
            keyboardMode = defaultMode,
            metadata = metadata,
            lastStatusMessage = "Attached to ${cleanAppLabel(metadata.packageName)}"
        )
        textBuffer = TextBuffer(
            text = initialBuffer,
            selection = selection,
            composing = null
        )
        lastInjectedText = if (metadata.isPassword) null else metadata.initialText
        lastInjectionTimestamp = 0L

        activeAccessibilityService?.cancelPendingFocusLossVerification()
        if (overlayManager?.showOverlay(metadata.isPassword) == false) {
            dismissOverlay(reason = "Keyboard surface attach failed")
        }
    }

    /** Validates and converts the node's selection indices into a clamped [IntRange], or `null` if unset. */
    private fun CoverFieldMetadata.observedSelection(text: String): IntRange? =
        if (selectionStart in 0..text.length && selectionEnd in selectionStart..text.length) {
            selectionStart..selectionEnd
        } else {
            null
        }

    /** Dismisses the cover keyboard overlay once focus loss is verified or the target window exits. */
    fun onFieldLostFocus(reason: String = "unknown") {
        dismissOverlay(reason = "Field lost focus ($reason)")
    }

    /**
     * A touch landed outside the cover keyboard. This does not immediately mean the user is
     * done: taps on the field itself (to reposition the cursor) and on the host app's own
     * chrome both arrive here. Verify focus was genuinely lost before dismissing.
     */
    fun onOutsideTouch() {
        if (!_sessionState.value.isActive) return
        activeAccessibilityService?.scheduleFocusLossVerification("outside touch")
    }

    /** Inserts [text] at the current caret, blocking direct entry while T9 predictive digits await selection. */
    fun appendText(text: String) {
        if (_sessionState.value.t9PredictiveDigits.isNotEmpty()) {
            _sessionState.value = _sessionState.value.copy(
                lastStatusMessage = "Choose a prediction before typing further"
            )
            return
        }
        applyDelta(TextDelta.Insert(text))
    }

    /**
     * Inserts a spoken [phrase] returned by voice dictation at the active caret, prepending a
     * space when the character immediately before the caret is non-whitespace.
     */
    fun commitDictatedText(phrase: String) {
        val state = _sessionState.value
        if (!state.isActive || state.metadata.isPassword) return
        val beforeCaret = state.buffer.take(state.cursorPosition.coerceIn(0, state.buffer.length))
        val formatted = formatDictatedPhrase(beforeCaret, phrase)
        if (formatted.isEmpty()) return
        if (state.t9PredictiveDigits.isNotEmpty()) {
            _sessionState.value = state.copy(t9PredictiveDigits = "")
        }
        applyDelta(TextDelta.Insert(formatted))
    }

    /** Updates the header status pill (used by voice dictation feedback without altering the buffer). */
    fun reportVoiceStatus(message: String) {
        val state = _sessionState.value
        if (!state.isActive) return
        _sessionState.value = state.copy(
            lastStatusMessage = message,
            isSuccessFeedback = false
        )
    }

    /**
     * Phase H: copies the focused field's current selection via the accessibility
     * `ACTION_COPY` channel and records it in [clipboardHistory]. No-ops (returns false)
     * when there's no bound service, no active selection, or the field is secure.
     */
    fun copySelection(): Boolean {
        val copied = activeAccessibilityService?.copySelectionFromFocusedNode() ?: return false
        clipboardHistoryBuffer.record(copied)
        _clipboardHistory.value = clipboardHistoryBuffer.snippets
        return true
    }

    /** Phase H: cuts the focused field's current selection, mirroring [copySelection]. */
    fun cutSelection(): Boolean {
        val cut = activeAccessibilityService?.cutSelectionFromFocusedNode() ?: return false
        clipboardHistoryBuffer.record(cut)
        _clipboardHistory.value = clipboardHistoryBuffer.snippets
        return true
    }

    /**
     * Phase H: pastes [text] into the focused field. When [text] is null, pastes whatever is
     * currently on the system clipboard (used by the toolbar's primary Paste button); when
     * non-null, re-pastes a specific entry from [clipboardHistory] (used by its history chips).
     */
    fun pasteText(text: String? = null): Boolean {
        val service = activeAccessibilityService ?: return false
        val toPaste = text ?: service.currentClipboardText() ?: return false
        return service.pasteTextIntoFocusedNode(toPaste)
    }

    /** Replaces the character before the caret with [char] for T9 multi-tap key cycling. */
    fun replacePreviousChar(char: Char) {
        if (_sessionState.value.t9PredictiveDigits.isNotEmpty()) {
            _sessionState.value = _sessionState.value.copy(
                lastStatusMessage = "Choose a prediction before typing further"
            )
            return
        }
        applyDelta(TextDelta.ReplacePreviousChar(char))
    }

    /** Deletes either the last uncommitted T9 predictive digit or one character before the caret. */
    fun deleteBackward() {
        val state = _sessionState.value
        if (state.t9PredictiveDigits.isNotEmpty()) {
            _sessionState.value = state.copy(t9PredictiveDigits = state.t9PredictiveDigits.dropLast(1))
            return
        }
        applyDelta(TextDelta.Backspace())
    }

    /** Clears uncommitted T9 digits and resets the entire editor buffer to empty. */
    fun clearBuffer() {
        _sessionState.value = _sessionState.value.copy(t9PredictiveDigits = "")
        applyDelta(TextDelta.Clear)
    }

    /** Enables or disables predictive T9 digit accumulation when in [CoverKeyboardMode.T9_MULTITAP]. */
    fun setT9Predictive(enabled: Boolean) {
        val state = _sessionState.value
        if (!state.isActive || state.keyboardMode != CoverKeyboardMode.T9_MULTITAP ||
            state.metadata.isPassword || state.metadata.isNumeric || state.metadata.isPhoneNumber
        ) {
            Log.w(TAG, "Predictive T9 unavailable for this field")
            return
        }
        if (state.isT9Predictive == enabled) return
        if (enabled && textBuffer.text.take(textBuffer.selection.first).lastOrNull()?.isLetter() == true) {
            _sessionState.value = state.copy(lastStatusMessage = "Finish this word before predictive T9")
            return
        }
        val discarded = !enabled && state.t9PredictiveDigits.isNotEmpty()
        if (discarded) {
            Log.w(TAG, "Discarding uncommitted predictive digits on mode change")
        }
        _sessionState.value = state.copy(
            isT9Predictive = enabled,
            t9PredictiveDigits = "",
            lastStatusMessage = when {
                discarded -> "Uncommitted prediction discarded"
                enabled -> "Predictive T9 ready"
                else -> "Multi-tap T9 ready"
            }
        )
    }

    /** Appends a keypad digit (`'2'..'9'`) to the active predictive T9 sequence (capped at 32 digits). */
    fun tapPredictiveDigit(digit: Char) {
        val state = _sessionState.value
        if (!state.isActive || !state.isT9Predictive || digit !in '2'..'9') {
            Log.w(TAG, "Predictive T9 digit rejected")
            return
        }
        if (state.t9PredictiveDigits.length >= 32) {
            _sessionState.value = state.copy(lastStatusMessage = "Prediction is too long")
            return
        }
        val digits = state.t9PredictiveDigits + digit
        _sessionState.value = state.copy(
            t9PredictiveDigits = digits,
            lastStatusMessage = "Choose a prediction"
        )
    }

    /** Deletes the word preceding the caret using [previousWordLength]. */
    fun deletePreviousWord() {
        val beforeCaret = textBuffer.text.take(textBuffer.selection.first)
        val count = previousWordLength(beforeCaret)
        if (count > 0) applyDelta(TextDelta.Backspace(count))
    }

    /** Commits a selected suggestion [candidate], replacing active T9 digits or the current word. */
    fun commitCandidate(candidate: String) {
        val state = _sessionState.value
        if (!state.isActive || state.metadata.isPassword ||
            state.metadata.isNumeric || state.metadata.isPhoneNumber
        ) {
            Log.w(TAG, "Candidate commit unavailable for this field")
            return
        }
        if (candidate.isBlank()) {
            Log.w(TAG, "Empty candidate rejected")
            return
        }
        if (state.t9PredictiveDigits.isNotEmpty()) {
            applyDelta(TextDelta.Commit(candidate))
            _sessionState.value = _sessionState.value.copy(t9PredictiveDigits = "")
        } else {
            applyDelta(TextDelta.CommitCurrentWord(candidate))
        }
    }

    /**
     * Moves the caret by [delta] characters in both the target editor (via `InputConnection` or
     * `ACTION_SET_SELECTION`) and our local [textBuffer], holding [injectionLock].
     */
    fun moveCursor(delta: Int) {
        injectionLock.withLock {
            val state = _sessionState.value
            if (!state.isActive || delta == 0) return@withLock
            val next = (textBuffer.selection.first + delta).coerceIn(0, textBuffer.text.length)
            if (next == textBuffer.selection.first) return@withLock
            val service = activeAccessibilityService
            if (service != null && !service.moveCursorInFocusedField(next)) {
                _sessionState.value = state.copy(
                    lastStatusMessage = "This field cannot move the cursor",
                    isSuccessFeedback = false
                )
                return@withLock
            }
            textBuffer = textBuffer.withCaret(next)
            _sessionState.value = state.copy(cursorPosition = next)
        }
    }

    /** Replaces [range] with [text] and positions the caret at [newCursor] via delta operations. */
    fun replaceText(range: IntRange, text: String, newCursor: Int) {
        val before = textBuffer
        val currentText = before.text
        val safeStart = range.first.coerceIn(0, currentText.length)
        val safeEnd = (range.last + 1).coerceIn(safeStart, currentText.length)
        val deleteCount = safeEnd - safeStart
        if (deleteCount > 0) {
            applyDelta(TextDelta.Backspace(deleteCount))
        }
        if (text.isNotEmpty()) {
            applyDelta(TextDelta.Insert(text))
        }
        val currentPos = textBuffer.selection.first
        if (newCursor != currentPos) {
            moveCursor(newCursor - currentPos)
        }
    }

    /** Creates the [CoverOverlayInputCallbacks] adapter passed into Floris-based Compose keyboards. */
    fun asOverlayInputCallbacks(): CoverOverlayInputCallbacks = object : CoverOverlayInputCallbacks {
        override fun onAppend(text: String) {
            appendText(text)
        }
        override fun onDeleteBackward() {
            deleteBackward()
        }
        override fun onDeletePreviousWord() {
            deletePreviousWord()
        }
        override fun onMoveCursor(delta: Int) {
            moveCursor(delta)
        }
        override fun onCommitCandidate(candidate: String) {
            commitCandidate(candidate)
        }
        override fun onClear() {
            clearBuffer()
        }
        override fun onDone() {
            commitAndFinish()
        }
        override fun getTextBeforeCursor(maxChars: Int): String {
            val state = _sessionState.value
            val cursor = state.cursorPosition.coerceIn(0, state.buffer.length)
            val start = (cursor - maxChars).coerceAtLeast(0)
            return state.buffer.substring(start, cursor)
        }
        override fun getTextAfterCursor(maxChars: Int): String {
            val state = _sessionState.value
            val cursor = state.cursorPosition.coerceIn(0, state.buffer.length)
            val end = (cursor + maxChars).coerceAtMost(state.buffer.length)
            return state.buffer.substring(cursor, end)
        }
        override fun getFullText(): String = _sessionState.value.buffer
        override fun getCursorPosition(): Int = _sessionState.value.cursorPosition
        override fun setCursorPosition(newPos: Int) {
            val current = _sessionState.value.cursorPosition
            moveCursor(newPos - current)
        }
        override fun replaceText(range: IntRange, text: String, newCursor: Int) {
            this@CoverInputSessionManager.replaceText(range, text, newCursor)
        }
    }

    /**
     * Single sink for every text mutation coming out of the keypads. Reads
     * the current [TextBuffer], applies [delta] to produce a new buffer,
     * computes the minimal [BufferDelta] against the old buffer, and forwards
     * that delta to the injection layer.
     *
     * Serialized by [injectionLock] so rapid keypad taps queue rather than
     * race. The lock is a plain [ReentrantLock] rather than a coroutine
     * mutex because every call site is a synchronous Compose callback and
     * we do not want to introduce a coroutine boundary between key-up and
     * text visibility.
     */
    fun applyDelta(delta: TextDelta) {
        injectionLock.withLock {
            val before = textBuffer
            val after = when (delta) {
                is TextDelta.Insert -> before.applyDelta(delta.text)
                is TextDelta.ReplacePreviousChar -> {
                    val caret = before.selection.first
                    if (caret == 0 || before.hasSelection) {
                        before.applyDelta(delta.char.toString())
                    } else {
                        before
                            .withSelection(caret - 1, caret)
                            .applyDelta(delta.char.toString())
                    }
                }
                is TextDelta.Backspace -> before.backspace(delta.count)
                TextDelta.Clear -> TextBuffer.Empty
                is TextDelta.Commit -> before.commit(delta.text)
                is TextDelta.CommitCurrentWord -> before.commitCurrentWord(delta.text)
            }

            // Fast path: buffer unchanged (e.g. backspace at position 0).
            if (after === before || (after.text == before.text && after.selection == before.selection && after.composing == before.composing)) {
                return@withLock
            }

            textBuffer = after
            val newCaret = after.selection.first
            _sessionState.value = _sessionState.value.copy(
                buffer = after.text,
                cursorPosition = newCaret,
                lastStatusMessage = "Typing...",
                isSuccessFeedback = false
            )

            // Defer live injection for password fields so intermediate characters are neither
            // echoed by accessibility events nor stored anywhere outside the local buffer until submit.
            if (!_sessionState.value.metadata.isPassword) {
                performDeltaInjection(before.text, after.text, newCaret)
            }
        }
    }

    /**
     * Switches the active keypad layout to [mode] (constrained by [compatibleKeyboardMode])
     * and persists the user's preference per package in DataStore for general text fields.
     */
    fun switchMode(mode: CoverKeyboardMode) {
        val current = _sessionState.value
        if (!current.isActive) return
        val compatible = compatibleKeyboardMode(current.metadata, mode)
        val discardedPrediction = current.t9PredictiveDigits.isNotEmpty() &&
            compatible != CoverKeyboardMode.T9_MULTITAP
        if (discardedPrediction) Log.w(TAG, "Discarding uncommitted predictive digits on mode switch")
        _sessionState.value = current.copy(
            keyboardMode = compatible,
            isT9Predictive = current.isT9Predictive && compatible == CoverKeyboardMode.T9_MULTITAP,
            t9PredictiveDigits = if (compatible == CoverKeyboardMode.T9_MULTITAP) {
                current.t9PredictiveDigits
            } else {
                ""
            },
            lastStatusMessage = if (discardedPrediction) "Uncommitted prediction discarded"
                else current.lastStatusMessage
        )
        modeChosenThisSession = true
        val packageName = current.metadata.packageName
        if (packageName.isNotBlank() && mode == compatible &&
            !current.metadata.isNumeric && !current.metadata.isPhoneNumber &&
            !current.metadata.isPassword
        ) {
            savedModes = savedModes + (packageName to compatible)
            val store = settingsStore
            if (store != null) persistenceScope.launch {
                runCatching { store.setKeyboardModeForPackage(packageName, compatible) }
                    .onFailure { Log.w(TAG, "Failed to save keyboard preference", it) }
            }
        }
    }

    /**
     * Called when the user taps the "SYS" mode chip to hand the focused field
     * back to their preferred system IME (Samsung Keyboard, Gboard, etc.).
     *
     * The cover overlay is dismissed, the accessibility service's soft-keyboard
     * suppression is lifted (via [dismissOverlay] -> [restoreSoftKeyboardMode]),
     * and we remember the current package so subsequent focus events inside
     * the same app don't immediately re-attach the cover keyboard.
     */
    fun relinquishToSystemKeyboard() {
        val currentPackage = _sessionState.value.metadata.packageName
        relinquishedPackage = currentPackage.ifBlank { null }
        Log.d(TAG, "User relinquished cover keyboard to system IME for '$currentPackage'")
        dismissOverlay(reason = "User chose system keyboard")
    }

    /**
     * Finalizes the active input session, flushes any remaining buffer (or deferred password
     * payload) to the target editor, dispatches the field's IME action (`GO`/`SEARCH`/`SEND`/
     * `DONE`), displays brief confirmation feedback, and schedules overlay dismissal on the
     * main [Looper].
     */
    fun commitAndFinish() {
        val state = _sessionState.value
        if (!state.isActive) return
        if (state.t9PredictiveDigits.isNotEmpty()) {
            _sessionState.value = state.copy(
                lastStatusMessage = "Choose a prediction before finishing",
                isSuccessFeedback = false
            )
            return
        }
        if (state.metadata.isPassword) {
            // Password buffers are injected all at once at commit time; clipboard paste is
            // explicitly treated as a failure so secrets are never placed on the system clipboard.
            val delta = BufferDelta.compute(
                state.metadata.initialText, state.buffer, state.cursorPosition
            )
            val method = if (delta.isNoOp) InjectionMethod.ACTION_SET_TEXT
                else injectionLock.withLock {
                    injectionTarget?.applyDelta(delta) ?: InjectionMethod.NONE
                }
            if (method == InjectionMethod.NONE || method == InjectionMethod.ACTION_PASTE_CLIPBOARD) {
                _sessionState.value = state.copy(
                    lastStatusMessage = "Secure input failed; text was not submitted",
                    isSuccessFeedback = false
                )
                Log.w(TAG, "Secure input failed for ${state.metadata.packageName}")
                return
            }
        } else {
            // The last delta reconciles an interrupted non-secure session.
            val result = injectionLock.withLock {
                performDeltaInjection(
                    oldText = lastInjectedText ?: state.buffer,
                    newText = state.buffer,
                    newCaret = state.cursorPosition
                )
            }
            if (result == InjectionMethod.NONE) return
        }
        injectionTarget?.dispatchDone() ?: activeAccessibilityService?.dispatchActionDone()

        val latest = _sessionState.value
        if (!latest.isActive || latest.metadata != state.metadata || latest.buffer != state.buffer) {
            return
        }
        val completedState = latest.copy(
            lastStatusMessage = "Injected successfully",
            isSuccessFeedback = true
        )
        _sessionState.value = completedState

        // Briefly surface the green "Injected successfully" indicator before detaching; reference
        // equality (===) prevents dismissing a newer session if focus moved during the 250 ms delay.
        Handler(Looper.getMainLooper()).postDelayed({
            if (_sessionState.value === completedState) dismissOverlay(reason = "Input finished")
        }, 250L)
    }

    /**
     * Cancels pending focus-loss verification, detaches the overlay surface, resets local buffer
     * state, and restores system soft-keyboard visibility (`SHOW_MODE_AUTO`).
     */
    fun dismissOverlay(reason: String) {
        // Guard against repeated teardown. Several independent signals (window churn,
        // outside touches, service callbacks) can all report "gone" for the same session;
        // without this the logs fill with duplicate dismissals and we needlessly reset
        // soft-keyboard mode over and over.
        if (!_sessionState.value.isActive && overlayManager?.isAttached() != true) return

        Log.d(TAG, "Dismissing cover overlay: $reason")
        activeAccessibilityService?.cancelPendingFocusLossVerification()
        overlayManager?.hideOverlay()
        _sessionState.value = CoverInputSessionState(isActive = false)
        textBuffer = TextBuffer.Empty
        activeAccessibilityService?.restoreSoftKeyboardMode()
    }

    /**
     * Returns `true` when an incoming `TYPE_VIEW_TEXT_CHANGED` payload matches our most recent
     * write within [INJECTION_ECHO_IGNORE_WINDOW_MS], identifying it as a self-generated echo.
     */
    fun isRecentSelfEcho(text: String): Boolean {
        val elapsed = SystemClock.elapsedRealtime() - lastInjectionTimestamp
        return lastInjectionTimestamp > 0L &&
            elapsed < INJECTION_ECHO_IGNORE_WINDOW_MS && text == lastInjectedText
    }

    /**
     * Diffs [newText] against [lastInjectedText] (falling back to [oldText]), pushes the resulting
     * [BufferDelta] to [injectionTarget], and updates status feedback. Caller must hold [injectionLock].
     */
    private fun performDeltaInjection(oldText: String, newText: String, newCaret: Int): InjectionMethod? {
        val delta = BufferDelta.compute(old = lastInjectedText ?: oldText, new = newText, newCaret = newCaret)
        if (delta.isNoOp) {
            // Caret-only movement, or no real content change. No injection required.
            return null
        }
        val target = injectionTarget
        if (target == null) {
            Log.w(TAG, "Cannot inject text: injection target not bound")
            _sessionState.value = _sessionState.value.copy(
                lastStatusMessage = "This field blocks external input",
                isSuccessFeedback = false
            )
            return InjectionMethod.NONE
        }
        val method = target.applyDelta(delta)
        if (method != InjectionMethod.NONE) {
            lastInjectedText = newText
            lastInjectionTimestamp = SystemClock.elapsedRealtime()
        }
        _sessionState.value = _sessionState.value.copy(
            isSuccessFeedback = method != InjectionMethod.NONE,
            lastStatusMessage = when (method) {
                InjectionMethod.IME_INPUT_CONNECTION -> "Synced"
                InjectionMethod.ACTION_SET_TEXT -> "Synced"
                InjectionMethod.ACTION_PASTE_CLIPBOARD -> "Pasted"
                InjectionMethod.NONE -> "This field blocks external input"
            }
        )
        if (method == InjectionMethod.NONE) {
            Log.w(
                TAG,
                "All injection channels failed for '${_sessionState.value.metadata.packageName}'"
            )
        }
        return method
    }

    /** Formats common cover-app package names into a concise display label for the status banner. */
    private fun cleanAppLabel(packageName: String): String {
        return when {
            packageName.contains("mpesa", ignoreCase = true) -> "M-Pesa"
            packageName.contains("safaricom", ignoreCase = true) -> "M-Pesa / Safaricom"
            packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
            packageName.contains("chrome", ignoreCase = true) -> "Chrome"
            packageName.isEmpty() -> "Cover App"
            else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
}

/**
 * Production [TextInjectionTarget] backed by a live
 * [CoverInputAccessibilityService]. Prefers the delta-aware
 * [CoverInputAccessibilityService.applyDeltaToFocusedField] path so most
 * keystrokes travel through `InputConnection.commitText` on the injected
 * substring only — avoiding the full-buffer `ACTION_SET_TEXT` rewrite that
 * used to fire onchange handlers in Chrome / WebView / React Native /
 * M-Pesa on every keystroke and break password-manager autofill mid-type.
 */
internal class AccessibilityServiceInjectionTarget(
    private val service: CoverInputAccessibilityService
) : TextInjectionTarget {
    override fun applyDelta(delta: BufferDelta): InjectionMethod =
        service.applyDeltaToFocusedField(delta)

    override fun dispatchDone() {
        service.dispatchActionDone()
    }
}
