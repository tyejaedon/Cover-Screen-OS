package com.tyejaedon.coverscreenos.keyboard.floris

import androidx.compose.ui.geometry.Offset
import com.tyejaedon.coverscreenos.keyboard.core.contracts.CandidateWord
import com.tyejaedon.coverscreenos.keyboard.core.contracts.InputAction
import com.tyejaedon.coverscreenos.keyboard.nlp.DTWGlideDecoder
import com.tyejaedon.coverscreenos.keyboard.nlp.T9PredictiveEngine

/**
 * Upstream FlorisBoard `TextInputManager` / `KeyEventDispatcher` equivalent for Cover-Screen-OS.
 *
 * Coordinates key action dispatching, shift & auto-capitalization state, T9 predictive digit
 * buffering vs. 650ms multi-tap cycling, clear-with-undo recovery, autocorrect-on-space,
 * and DTW glide typing across [CoverEditorBridge].
 *
 * @property bridge Editor connection/callback multiplexer receiving all text and cursor mutations.
 * @property t9EngineProvider Lazy provider for the dictionary-backed T9 predictor.
 * @property glideDecoderProvider Lazy provider for the Dynamic Time Warping swipe-typing decoder.
 * @property autocorrectProvider Optional callback returning a corrected word for Space-key autocorrect.
 * @property onT9TraceUpdated Listener notified whenever T9 digit sequence or multi-tap trace changes.
 */
class FlorisTextInputManager(
    val bridge: CoverEditorBridge,
    initialMode: CoverLayoutMode = CoverLayoutMode.QWERTY,
    private val t9EngineProvider: (() -> T9PredictiveEngine?)? = null,
    private val glideDecoderProvider: (() -> DTWGlideDecoder?)? = null,
    private val autocorrectProvider: ((String) -> String)? = null,
    var onT9TraceUpdated: ((predictiveDigits: String, multiTapTrace: String) -> Unit)? = null
) {
    /** Shared timing constants, classic keypad mappings, and auto-capitalization helpers. */
    companion object {
        /** Max milliseconds between consecutive taps on the same T9 key to cycle letters instead of starting a new letter. */
        const val MULTI_TAP_TIMEOUT_MS = 650L

        // Standard ITU E.161 / 3x4 keypad digit-to-letter mapping used when no layout hint is passed.
        private val T9_DIGIT_TO_LETTERS = mapOf(
            '1' to ".,!",
            '2' to "abc",
            '3' to "def",
            '4' to "ghi",
            '5' to "jkl",
            '6' to "mno",
            '7' to "pqrs",
            '8' to "tuv",
            '9' to "wxyz"
        )

        /** Returns true at the start of input or right after sentence-ending punctuation (`.`, `!`, `?`). */
        fun shouldAutoCap(textBeforeCursor: String): Boolean =
            textBeforeCursor.isBlank() || Regex("[.!?]\\s*$").containsMatchIn(textBeforeCursor)
    }

    /** Currently active keyboard layout mode (QWERTY, T9, Symbols, Numeric). */
    var activeMode: CoverLayoutMode = initialMode
        private set

    /** Current alphabetic casing state (lowercase, single-shift, or Caps Lock). */
    var shiftState: CoverShiftState = CoverShiftState.LOWERCASE
        private set

    /** Uptime timestamp of the previous Shift key tap, used to detect double-tap Caps Lock. */
    var lastShiftTapMs: Long = -1000L
        private set

    /** True when T9 keys predict whole words by digit sequence; false for multi-tap ABC letter cycling. */
    var isT9Predictive: Boolean = true

    /** Sequence of T9 digits buffered for the current word (for example `"4663"` -> `"good"`/`"home"`). */
    var t9PredictiveDigits: String = ""
        private set

    /** Human-readable multi-tap digit trail shown in diagnostics or preview overlays (e.g. `"4-4-6"`). */
    var t9MultiTapTrace: String = ""
        private set

    // Last digit tapped in multi-tap ABC mode, plus its timestamp and current letter offset.
    private var lastTapDigit: Char? = null
    private var lastTapTimeMs: Long = 0L
    private var multiTapCycleIndex: Int = 0

    /** Text saved before a Clear action so a quick tap on Clear can restore it while the buffer is empty. */
    var lastClearedText: String? = null
        private set

    /** Switches [activeMode] to [mode], clearing any partial T9 word buffer if the mode changed. */
    fun setLayoutMode(mode: CoverLayoutMode) {
        if (activeMode != mode) {
            resetT9Composing()
            activeMode = mode
        }
    }

    /** Explicitly overrides [shiftState] (used by hosts syncing external auto-cap or selection changes). */
    fun setShift(state: CoverShiftState) {
        shiftState = state
    }

    /** Advances [shiftState] using the double-tap Caps Lock window and returns the new state. */
    fun toggleShift(nowMs: Long): CoverShiftState {
        shiftState = shiftState.nextState(lastShiftTapMs, nowMs)
        lastShiftTapMs = nowMs
        return shiftState
    }

    /** Clears pending T9 predictive digits and multi-tap state, notifying [onT9TraceUpdated]. */
    fun resetT9Composing() {
        t9PredictiveDigits = ""
        t9MultiTapTrace = ""
        lastTapDigit = null
        multiTapCycleIndex = 0
        onT9TraceUpdated?.invoke(t9PredictiveDigits, t9MultiTapTrace)
    }

    /**
     * Dispatches a [KeyboardContracts] [InputAction] through the input manager.
     */
    fun dispatchInputAction(
        action: InputAction,
        nowMs: Long = System.currentTimeMillis()
    ) {
        when (action) {
            is InputAction.KeyTap -> {
                val label = action.key.primaryLabel
                if (activeMode == CoverLayoutMode.T9_PREDICTIVE && label.length == 1 && label[0] in '1'..'9') {
                    handleT9DigitTap(label[0], action.key.t9Chars, nowMs)
                } else if (label.length == 1) {
                    handleCharInput(label[0])
                } else {
                    bridge.insertText(label)
                }
            }
            is InputAction.LongPressSelect -> {
                lastClearedText = null
                resetT9Composing()
                bridge.insertText(action.selectedPopupChar)
                if (shiftState == CoverShiftState.SINGLE_SHIFT) {
                    shiftState = CoverShiftState.LOWERCASE
                }
            }
            is InputAction.GlideComplete -> {
                handleGlideGesture(action.sampledPoints)
            }
            is InputAction.SpacebarCursorDelta -> {
                if (action.charDelta != 0) {
                    resetT9Composing()
                    bridge.moveCursor(action.charDelta)
                }
            }
            is InputAction.GestureDeleteUpdate -> {
                if (action.isFinalCommit && action.wordsToDelete > 0) {
                    resetT9Composing()
                    repeat(action.wordsToDelete) {
                        bridge.deletePreviousWord()
                    }
                }
            }
            is InputAction.ModeSwitch -> {
                setLayoutMode(CoverLayoutMode.fromContractMode(action.targetMode))
            }
        }
    }

    /**
     * Dispatches a [CoverKeyAction] from [CoverFlorisKeyboardSurface].
     */
    fun handleKeyAction(
        action: CoverKeyAction,
        nowMs: Long = System.currentTimeMillis(),
        autocorrectEnabled: Boolean = true,
        onDismiss: (() -> Unit)? = null
    ) {
        when (action) {
            is CoverKeyAction.InputChar -> {
                if (activeMode == CoverLayoutMode.T9_PREDICTIVE && action.char in '1'..'9') {
                    val letters = T9_DIGIT_TO_LETTERS[action.char].orEmpty()
                    handleT9DigitTap(action.char, letters, nowMs)
                } else {
                    handleCharInput(action.char)
                }
            }
            is CoverKeyAction.InputText -> {
                lastClearedText = null
                resetT9Composing()
                bridge.insertText(action.text)
                if (shiftState == CoverShiftState.SINGLE_SHIFT) {
                    shiftState = CoverShiftState.LOWERCASE
                }
            }
            is CoverKeyAction.T9Tap -> {
                handleT9DigitTap(action.digit, action.letters, nowMs)
            }
            CoverKeyAction.Backspace -> {
                handleBackspace()
            }
            CoverKeyAction.DeleteWord -> {
                lastClearedText = null
                resetT9Composing()
                bridge.deletePreviousWord()
            }
            CoverKeyAction.ClearOrUndo -> {
                handleClearTapUndo()
            }
            CoverKeyAction.Space -> {
                handleSpace(autocorrectEnabled = autocorrectEnabled)
            }
            CoverKeyAction.Enter -> {
                resetT9Composing()
                bridge.finishInput()
            }
            CoverKeyAction.Shift -> {
                toggleShift(nowMs)
            }
            CoverKeyAction.SwitchToSymbols -> {
                setLayoutMode(CoverLayoutMode.SYMBOLS)
            }
            CoverKeyAction.SwitchToSymbolsSecondary -> {
                setLayoutMode(CoverLayoutMode.SYMBOLS_SECONDARY)
            }
            CoverKeyAction.SwitchToLetters -> {
                setLayoutMode(CoverLayoutMode.QWERTY)
            }
            CoverKeyAction.SwitchMode -> {
                val next = if (activeMode == CoverLayoutMode.QWERTY) {
                    CoverLayoutMode.T9_PREDICTIVE
                } else {
                    CoverLayoutMode.QWERTY
                }
                setLayoutMode(next)
            }
            CoverKeyAction.ToggleT9 -> {
                setLayoutMode(CoverLayoutMode.T9_PREDICTIVE)
            }
            CoverKeyAction.Dismiss -> {
                onDismiss?.invoke()
            }
        }
    }

    /** Inserts a single character [char], resets T9 composing, and drops single-shot Shift. */
    fun handleCharInput(char: Char) {
        lastClearedText = null
        resetT9Composing()
        bridge.insertText(char.toString())
        if (char.isLetter() && shiftState == CoverShiftState.SINGLE_SHIFT) {
            shiftState = CoverShiftState.LOWERCASE
        }
    }

    /**
     * Handles a T9 keypad press (`1`..`9`) in either predictive T9 mode or 650ms multi-tap mode.
     */
    fun handleT9DigitTap(digit: Char, lettersHint: String = "", nowMs: Long = System.currentTimeMillis()) {
        lastClearedText = null
        // Lowercase letter group associated with this key (e.g. "abc" for '2').
        val letters = lettersHint.ifEmpty { T9_DIGIT_TO_LETTERS[digit].orEmpty() }.lowercase()

        if (isT9Predictive && digit in '2'..'9') {
            t9PredictiveDigits += digit
            // Pick the highest-ranked dictionary match for the digit sequence, or fall back to its first letter.
            val topCandidate = t9EngineProvider?.invoke()
                ?.predictForDigits(t9PredictiveDigits, limit = 1)
                ?.firstOrNull()
                ?.word
                ?: letters.firstOrNull()?.toString()
                ?: digit.toString()

            if (t9PredictiveDigits.length == 1) {
                bridge.insertText(topCandidate)
            } else {
                // Replace the previously previewed word in the editor with the updated prediction.
                val before = bridge.getSurroundingText(50, 0).textBefore
                val prevWordLen = before.takeLastWhile { it.isLetterOrDigit() }.length
                if (prevWordLen > 0) {
                    bridge.deleteSurroundingText(prevWordLen, 0)
                }
                bridge.insertText(topCandidate)
            }
            onT9TraceUpdated?.invoke(t9PredictiveDigits, t9MultiTapTrace)
            return
        }

        // Multi-tap cycling mode (or '1' punctuation key)
        val effectiveLetters = if (digit == '1') ".,!" else letters
        if (lastTapDigit == digit &&
            (nowMs - lastTapTimeMs) < MULTI_TAP_TIMEOUT_MS &&
            effectiveLetters.isNotEmpty()
        ) {
            // Same digit pressed within 650ms: advance cycle index and replace the prior character.
            multiTapCycleIndex = (multiTapCycleIndex + 1) % effectiveLetters.length
            lastTapTimeMs = nowMs
            t9MultiTapTrace = if (digit == '1') "" else t9MultiTapTrace + digit
            val nextChar = effectiveLetters[multiTapCycleIndex]
            bridge.replacePreviousChar(if (shiftState.isShifted) nextChar.uppercaseChar() else nextChar)
        } else {
            // New digit or timeout expired: start a fresh character cycle and append its first letter.
            lastTapDigit = digit
            lastTapTimeMs = nowMs
            multiTapCycleIndex = 0
            t9MultiTapTrace = when {
                digit == '1' -> ""
                t9MultiTapTrace.isEmpty() -> digit.toString()
                else -> "$t9MultiTapTrace-$digit"
            }
            val firstChar = if (effectiveLetters.isNotEmpty()) effectiveLetters.first() else digit
            val outChar = if (shiftState.isShifted) firstChar.uppercaseChar() else firstChar
            bridge.insertText(outChar.toString())
            if (shiftState == CoverShiftState.SINGLE_SHIFT && outChar.isLetter()) {
                shiftState = CoverShiftState.LOWERCASE
            }
        }
        onT9TraceUpdated?.invoke(t9PredictiveDigits, t9MultiTapTrace)
    }

    /** Deletes one character and trims the trailing digit from active T9 buffers. */
    fun handleBackspace() {
        lastClearedText = null
        if (t9PredictiveDigits.isNotEmpty()) {
            t9PredictiveDigits = t9PredictiveDigits.dropLast(1)
        }
        if (t9MultiTapTrace.isNotEmpty()) {
            t9MultiTapTrace = t9MultiTapTrace.substringBeforeLast('-', missingDelimiterValue = "")
        }
        lastTapDigit = null
        onT9TraceUpdated?.invoke(t9PredictiveDigits, t9MultiTapTrace)
        bridge.deleteSurroundingText(1, 0)
    }

    /** Inserts a space, running QWERTY autocorrect on the preceding word when enabled. */
    fun handleSpace(
        autocorrectEnabled: Boolean = true,
        currentTextBeforeCursor: String? = null,
        onCommitAutocorrect: ((String) -> Unit)? = null
    ) {
        lastClearedText = null
        resetT9Composing()
        if (autocorrectEnabled && activeMode == CoverLayoutMode.QWERTY) {
            val before = currentTextBeforeCursor ?: bridge.getSurroundingText(50, 0).textBefore
            // Trailing alphabetic word immediately preceding the cursor.
            val word = before.takeLastWhile { it.isLetter() }
            if (word.isNotEmpty()) {
                val corrected = autocorrectProvider?.invoke(word) ?: word
                if (corrected != word) {
                    if (onCommitAutocorrect != null) {
                        onCommitAutocorrect(corrected)
                    } else {
                        bridge.commitCandidate(corrected, trailingSpace = true)
                    }
                    return
                }
            }
        }
        bridge.insertText(" ")
    }

    /**
     * Single tap on Clear: only restores [lastClearedText] if the current buffer is empty.
     * Prevents accidental taps on Clear from wiping the user's input.
     */
    fun handleClearTapUndo(currentTextBeforeCursor: String? = null): Boolean {
        val before = currentTextBeforeCursor ?: bridge.getSurroundingText(500, 0).textBefore
        // Snapshot to restore if Clear was just long-pressed and the field is still empty.
        val saved = lastClearedText
        if (before.isEmpty() && !saved.isNullOrEmpty()) {
            lastClearedText = null
            resetT9Composing()
            bridge.insertText(saved)
            return true
        }
        return false
    }

    /**
     * Long press on Clear: saves the current text to [lastClearedText] for single-tap Undo and
     * clears the editor buffer.
     */
    fun handleClearLongPress(currentTextBeforeCursor: String? = null): Boolean {
        val before = currentTextBeforeCursor ?: bridge.getSurroundingText(500, 0).textBefore
        if (before.isNotEmpty()) {
            lastClearedText = before
            resetT9Composing()
            bridge.clearAll()
            return true
        }
        return false
    }

    /**
     * Decodes a continuous swipe/glide gesture across keys via [DTWGlideDecoder] and commits
     * the top candidate word.
     */
    fun handleGlideGesture(normalizedPoints: List<Offset>): List<CandidateWord> {
        // Skip decoding gracefully when no glide engine is wired (e.g. PIN or non-QWERTY modes).
        val decoder = glideDecoderProvider?.invoke() ?: return emptyList()
        val candidates = decoder.decodeGlidePath(normalizedPoints, limit = 5)
        val top = candidates.firstOrNull()
        if (top != null) {
            lastClearedText = null
            resetT9Composing()
            val before = bridge.getSurroundingText(50, 0).textBefore
            // Automatically insert a separating space when gliding right after an existing word.
            val needsLeadingSpace = before.isNotEmpty() && !before.last().isWhitespace()
            if (needsLeadingSpace) {
                bridge.insertText(" ${top.word}")
            } else {
                bridge.insertText(top.word)
            }
        }
        return candidates
    }
}
