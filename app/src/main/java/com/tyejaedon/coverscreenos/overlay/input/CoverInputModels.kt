package com.tyejaedon.coverscreenos.overlay.input

import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo

/*
 * Cover-Screen Input Injection Engine Architecture
 * ------------------------------------------------
 * OEMs (such as Samsung One UI on Galaxy Z Flip devices) restrict third-party apps from acting as
 * the default cover-screen IME or Home app, and the system keyboard (HoneyBoard) often obscures or
 * refuses to render properly over cover-screen apps. This file provides an end-to-end virtual input
 * subsystem hosted inside an AccessibilityService:
 *
 * 1. Focus Discovery & Window Tracking ([CoverInputAccessibilityService]):
 *    - Filters AccessibilityEvents to the secondary cover hardware display (displayId == 1).
 *    - Resolves the focused editable [AccessibilityNodeInfo] across interactive windows via
 *      `AccessibilityService.getWindows()` (enabled by `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`) so that
 *      attaching our own `TYPE_ACCESSIBILITY_OVERLAY` window does not shadow the underlying app.
 *    - Suppresses the stock soft keyboard via `SoftKeyboardController.SHOW_MODE_HIDDEN` while active
 *      and verifies genuine focus loss with [FocusLossGate] + a main-Looper [Handler] polling loop.
 *
 * 2. Session & Buffer Synchronization ([CoverInputSessionManager]):
 *    - Maintains a local composing-aware [TextBuffer] and emits [CoverInputSessionState] over a
 *      `StateFlow`.
 *    - Serializes keystrokes and echo reconciliation with a synchronous [ReentrantLock] (`injectionLock`)
 *      and computes minimal [BufferDelta] patches (`TextDelta` -> `BufferDelta.compute`).
 *    - Holds password fields locally until [CoverInputSessionManager.commitAndFinish] so secrets are
 *      never injected mid-typing, logged to suggestions, or copied onto the clipboard.
 *
 * 3. Tiered Text Injection Channels ([InjectionMethod]):
 *    - Tier 1 (`IME_INPUT_CONNECTION`): Uses `AccessibilityService.getInputMethod()` (API 33+ via
 *      `FLAG_INPUT_METHOD_EDITOR`) to write minimal deltas through `InputConnection.commitText` /
 *      `setSelection` — essential for Flutter, React Native, WebViews, and banking apps.
 *    - Tier 2 (`ACTION_SET_TEXT`): Accessibility node fallback replacing the full text via
 *      `AccessibilityNodeInfo.ACTION_SET_TEXT` + `ACTION_SET_SELECTION` after best-effort focus.
 *    - Tier 3 (`ACTION_PASTE_CLIPBOARD`): Last-resort fallback staging text on [ClipboardManager]
 *      and invoking `ACTION_PASTE` (explicitly blocked for password fields).
 *
 * 4. Overlay Hosting & Compose UI ([CoverKeyboardOverlayManager], [CoverKeyboardOverlayRoot]):
 *    - Attaches a non-focusable `TYPE_ACCESSIBILITY_OVERLAY` Compose surface onto the cover display
 *      with `FLAG_WATCH_OUTSIDE_TOUCH` and `FLAG_SECURE` when typing passwords.
 */

internal const val TAG = "CoverInputInjection"
internal const val OVERLAY_RECLAIM_LOG_TAG = "CoverOverlayReclaim"
/** Default fallback secondary hardware cover display ID on clamshell foldables (`0` is inner display). */
internal const val COVER_DISPLAY_ID = 1
/** Package-private clipboard label marking temporary Tier-3 injection clips eligible for auto-restore. */
internal const val INTERNAL_CLIP_LABEL = "cover_input_internal"
/** Delay (ms) before restoring the user's previous clipboard after `ACTION_PASTE` completes. */
internal const val CLIPBOARD_RESTORE_DELAY_MS = 150L
internal const val MULTI_TAP_CYCLE_TIMEOUT_MS = 850L
/** Window during which a `TYPE_VIEW_TEXT_CHANGED` echo matching `lastInjectedText` is ignored. */
internal const val INJECTION_ECHO_IGNORE_WINDOW_MS = 600L
/** Minimum interval between identical global navigation gestures (`onGesture`). */
internal const val GESTURE_DEBOUNCE_MS = 550L
/** Cooldown throttling rapid repeated global actions (Back/Home/Recents/Notifications). */
internal const val ACTION_THROTTLE_MS = 300L
/** Minimum cadence for refreshing `latestForegroundEventElapsedMs` for the same package/class. */
internal const val FOREGROUND_EVENT_REFRESH_MIN_INTERVAL_MS = 250L
/** Continuous dwell duration on a cover-home/system-launcher surface before reclaiming the overlay. */
internal const val RECLAIM_STABILITY_DEBOUNCE_MS = 1_500L
/** Suppress overlay reclaim shortly after a real user app was observed in the foreground. */
internal const val RECENT_USER_APP_GUARD_MS = 2_000L
// Content changes during the first 1.5 s after attach extend a pending focus-loss hold.
internal const val FIELD_FOCUS_GRACE_MS = 1_500L
// Require the target editor to be absent continuously for this long after a non-editable
// focus event from its own package.
internal const val FOCUS_LOSS_CONFIRM_DELAY_MS = 400L
/** Polling interval used by [CoverInputAccessibilityService.scheduleFocusLossVerification]. */
internal const val FOCUS_LOSS_POLL_MS = 50L
// Upper bound used when clearing an editor through the InputConnection and the
// extracted-text length is unavailable. Comfortably exceeds any realistic field.
internal const val INPUT_CONNECTION_CLEAR_SPAN = 5_000

/** System launcher prefixes indicating the user returned to the stock cover home screen. */
internal val OVERLAY_RECLAIM_PACKAGE_PREFIXES = arrayOf(
    "com.sec.android.app.launcher"
)
/** Transient System UI / AOD packages that should not prematurely reclaim or hijack tracking. */
internal val TRANSIENT_SYSTEM_UI_PREFIXES = arrayOf(
    "com.android.systemui",
    "com.samsung.systemui",
    "com.samsung.android.app.aodservice"
)
/** Packages excluded when tracking the most recent third-party user app on the cover screen. */
internal val NON_USER_APP_PREFIXES = arrayOf(
    "com.android.systemui",
    "com.samsung.systemui",
    "com.samsung.android.app.aodservice",
    "com.sec.android.app.launcher",
    "com.tyejaedon.coverscreenos"
)

/**
 * Input layout modes offered by the cover keyboard overlay.
 */
enum class CoverKeyboardMode {
    /** Compact 10-digit keypad used for PINs, phone numbers, numeric inputs, and passwords. */
    NUMERIC_PIN,
    /** Classic 3x4 keypad supporting both multi-tap cycling and predictive T9 digit sequences. */
    T9_MULTITAP,
    /** Full compact QWERTY keyboard with NLP suggestions, glide typing, and clipboard toolbar. */
    QWERTY
}

/**
 * Channel used to push the cover keyboard buffer into the focused editor, in the order
 * they are attempted by [CoverInputAccessibilityService.syncTextToFocusedField].
 */
enum class InjectionMethod {
    /**
     * Writes through the real [android.view.inputmethod.InputConnection] owned by the focused
     * editor, exactly like a normal soft keyboard. This is the only channel that works for apps
     * that expose a *virtual* view hierarchy (Flutter, React Native surfaces, WebViews) — those
     * nodes never advertise `ACTION_SET_TEXT`/`ACTION_PASTE`, so accessibility actions silently
     * return false. Requires `flagInputMethodEditor` (API 33+).
     */
    IME_INPUT_CONNECTION,
    /** Accessibility node fallback via [AccessibilityNodeInfo.ACTION_SET_TEXT] (full-buffer write). */
    ACTION_SET_TEXT,
    /** Last-resort fallback staging text on [ClipboardManager] and firing `ACTION_PASTE`. */
    ACTION_PASTE_CLIPBOARD,
    /** Indicates that no injection channel succeeded or was eligible. */
    NONE
}

/**
 * Snapshot of metadata extracted from the currently focused [AccessibilityNodeInfo] so the
 * cover keyboard can select an appropriate layout, IME action button, and secure display flags
 * without retaining a mutable reference to the underlying node.
 */
data class CoverFieldMetadata(
    /** Android package name of the app that owns the focused text field (e.g. `"com.whatsapp"`). */
    val packageName: String = "",
    /** Optional XML resource ID of the view, used to tell distinct fields apart in the same screen. */
    val viewIdResourceName: String? = null,
    /** Placeholder hint text shown by the app before the user starts typing. */
    val hintText: String = "",
    /** True for number, phone, or date/time fields where a compact digit keypad fits best. */
    val isNumeric: Boolean = false,
    /** True for PIN or password fields that require `FLAG_SECURE` and deferred injection. */
    val isPassword: Boolean = false,
    /** True specifically for telephone-number input fields. */
    val isPhoneNumber: Boolean = false,
    /** True when the field accepts Enter/newlines instead of submitting a single-line form. */
    val isMultiLine: Boolean = false,
    /** Resolved [EditorInfo] action (`GO`, `SEARCH`, `SEND`, `NEXT`, `DONE`, or `NONE`). */
    val imeAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
    /** Existing text already inside the field when the user tapped it (always empty for passwords). */
    val initialText: String = "",
    /** Starting index of the field's cursor or text selection (`-1` if unknown). */
    val selectionStart: Int = -1,
    /** Ending index of the field's cursor or text selection (`-1` if unknown). */
    val selectionEnd: Int = -1,
    /** Screen-space bounding rectangle of the target input box, used to detect field switches. */
    val boundsInScreen: Rect = Rect()
) {
    companion object {
        /**
         * Inspects [node] and converts its Android `InputType`, hint/content text, selection span,
         * screen bounds, and `extras` [Bundle] into a [CoverFieldMetadata] snapshot.
         */
        fun fromNode(node: AccessibilityNodeInfo?): CoverFieldMetadata {
            if (node == null) return CoverFieldMetadata()

            val inputType = node.inputType // Raw Android InputType bitmask from the target EditText
            val inputClass = inputType and InputType.TYPE_MASK_CLASS // Base class (text, number, phone, datetime)

            val isNumericClass = inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME

            val isPhoneClass = inputClass == InputType.TYPE_CLASS_PHONE

            val isPasswordField = isPasswordInputType(inputType) || node.isPassword

            val bounds = Rect() // Screen coordinates populated by AccessibilityNodeInfo
            node.getBoundsInScreen(bounds)

            // AccessibilityNodeInfo.getText() returns the *hint* text (not the actual
            // field content) while the field is empty and showing its placeholder on
            // many apps. Without this guard, that placeholder gets seeded into the
            // cover keyboard's buffer as if the user had already typed it, forcing a
            // confusing manual delete before composing. isShowingHintText() is the
            // authoritative signal that node.text is hint, not content.
            val isShowingHint = node.isShowingHintText
            val rawText = node.text?.toString() ?: ""

            return CoverFieldMetadata(
                packageName = node.packageName?.toString() ?: "",
                viewIdResourceName = node.viewIdResourceName,
                hintText = node.hintText?.toString() ?: "",
                isNumeric = isNumericClass,
                isPassword = isPasswordField,
                isPhoneNumber = isPhoneClass,
                isMultiLine = node.isMultiLine,
                imeAction = resolveImeAction(node.extras, inputType, node.isMultiLine),
                initialText = if (isShowingHint) "" else rawText,
                selectionStart = node.textSelectionStart,
                selectionEnd = node.textSelectionEnd,
                boundsInScreen = bounds
            )
        }
    }
}

/**
 * Returns `true` when [inputType] encodes a numeric PIN or text/web/visible password variation
 * according to [InputType.TYPE_MASK_CLASS] and [InputType.TYPE_MASK_VARIATION].
 */
internal fun isPasswordInputType(inputType: Int): Boolean {
    val inputClass = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return (inputClass == InputType.TYPE_CLASS_NUMBER &&
        variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) ||
        (inputClass == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        ))
}

/**
 * Resolves the effective [EditorInfo] IME action (`IME_ACTION_GO`, `SEARCH`, `SEND`, `NEXT`,
 * `DONE`, or `NONE`) for a focused node.
 *
 * Android accessibility nodes do not always expose `EditorInfo.imeOptions` in a standardized field,
 * so this probes several known `Bundle` extra keys used across framework versions and OEMs before
 * falling back to sensible defaults derived from [inputType] and [isMultiLine].
 */
internal fun resolveImeAction(extras: Bundle?, inputType: Int, isMultiLine: Boolean): Int {
    // Different Android API levels, WebViews, and toolkits populate slightly different keys in
    // AccessibilityNodeInfo.getExtras().
    val keys = arrayOf(
        "imeOptions",
        "android.view.inputmethod.EditorInfo.IME_OPTIONS",
        "android.view.accessibility.AccessibilityNodeInfo.IME_OPTIONS",
        "android.view.accessibility.AccessibilityNodeInfo.IME_ACTION"
    )
    for (key in keys) {
        val value = extras?.get(key)
        val option = when (value) {
            is Int -> value
            is String -> value.toIntOrNull()
            else -> null
        } ?: continue
        // Extract only the action bits; upper bits hold flags such as IME_FLAG_NO_EXTRACT_UI.
        val action = option and EditorInfo.IME_MASK_ACTION
        if (action in EditorInfo.IME_ACTION_GO..EditorInfo.IME_ACTION_DONE) return action
    }
    return when {
        isMultiLine || inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0 ->
            EditorInfo.IME_ACTION_NONE
        inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_URI ->
            EditorInfo.IME_ACTION_GO
        inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE ->
            EditorInfo.IME_ACTION_SEND
        else -> EditorInfo.IME_ACTION_DONE
    }
}

/**
 * Chooses a keyboard mode compatible with [metadata], forcing [CoverKeyboardMode.NUMERIC_PIN] for
 * numeric, phone, and password fields and honoring [saved] (defaulting to
 * [CoverKeyboardMode.T9_MULTITAP]) for general text fields.
 */
internal fun compatibleKeyboardMode(
    metadata: CoverFieldMetadata,
    saved: CoverKeyboardMode?
): CoverKeyboardMode = when {
    metadata.isNumeric || metadata.isPhoneNumber || metadata.isPassword -> CoverKeyboardMode.NUMERIC_PIN
    else -> saved ?: CoverKeyboardMode.T9_MULTITAP
}

/**
 * Hysteresis state machine that guards against false "field lost focus" dismissals caused by
 * transient UI transitions on the cover display (touch ripples, autocomplete popups, or our own
 * keyboard overlay attaching).
 *
 * Flow:
 * 1. A non-editable `TYPE_VIEW_FOCUSED` event from the target package arms the gate via
 *    [onNonEditableFocus] and starts the [FIELD_FOCUS_GRACE_MS] window.
 * 2. Window content churn during that grace window resets the absence timer ([onContentChurn]).
 * 3. Polling via [shouldDismiss] confirms dismissal only after the editable target has remained
 *    continuously missing for [FOCUS_LOSS_CONFIRM_DELAY_MS].
 * 4. Any valid editable focus resets the gate via [onEditableFocus].
 */
internal class FocusLossGate {
    /** Elapsed-realtime ms when focus loss was first suspected via a non-editable focus event. */
    private var candidateSinceMs: Long? = null
    /** Elapsed-realtime ms when the target input node was first observed continuously missing. */
    private var absentSinceMs: Long? = null
    /** True while a potential focus loss is being verified by the polling loop. */
    var isArmed: Boolean = false
        private set

    /** Disarms the gate and clears any pending loss timers when an editable node is confirmed. */
    fun onEditableFocus() {
        isArmed = false
        candidateSinceMs = null
        absentSinceMs = null
    }

    /** Arms focus-loss verification upon observing a non-editable focus event in the target app. */
    fun onNonEditableFocus(nowMs: Long) {
        if (isArmed) return
        isArmed = true
        candidateSinceMs = nowMs
        absentSinceMs = null
    }

    /**
     * Resets the absence countdown when layout/content churn arrives within [FIELD_FOCUS_GRACE_MS]
     * of arming, preventing attach-time relayouts from dismissing the keyboard.
     */
    fun onContentChurn(nowMs: Long) {
        if (isArmed && candidateSinceMs?.let { nowMs - it < FIELD_FOCUS_GRACE_MS } == true) {
            absentSinceMs = null
        }
    }

    /**
     * Evaluates whether focus loss is confirmed at [nowMs]. Returns `true` only once the target
     * editor has been continuously unreachable (`targetMissing == true`) for
     * [FOCUS_LOSS_CONFIRM_DELAY_MS].
     */
    fun shouldDismiss(nowMs: Long, targetMissing: Boolean): Boolean {
        if (!isArmed) return false
        if (!targetMissing) {
            absentSinceMs = null
            return false
        }
        val since = absentSinceMs ?: nowMs.also { absentSinceMs = it }
        return nowMs - since >= FOCUS_LOSS_CONFIRM_DELAY_MS
    }
}

/**
 * Maps an [EditorInfo] IME action code to the best-matching [AccessibilityNodeInfo] fallback
 * action ID used when [android.view.inputmethod.InputConnection.performEditorAction] is unavailable.
 */
internal fun accessibilityActionForIme(action: Int): Int = when (action and EditorInfo.IME_MASK_ACTION) {
    EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEARCH,
    EditorInfo.IME_ACTION_SEND, EditorInfo.IME_ACTION_DONE ->
        AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
    EditorInfo.IME_ACTION_NEXT -> AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT
    else -> AccessibilityNodeInfo.ACTION_CLICK
}

/**
 * Immutable UI and session snapshot exposed by [CoverInputSessionManager.sessionState].
 */
data class CoverInputSessionState(
    /** True while the cover keyboard is attached to a focused field on display 1. */
    val isActive: Boolean = false,
    /** Current plain-text contents held by the cover keyboard's local scratchpad. */
    val buffer: String = "",
    /** Current caret index inside [buffer]. */
    val cursorPosition: Int = 0,
    /** Currently visible keypad layout mode (`NUMERIC_PIN`, `T9_MULTITAP`, or `QWERTY`). */
    val keyboardMode: CoverKeyboardMode = CoverKeyboardMode.NUMERIC_PIN,
    /** True when the T9 keypad collects raw `'2'..'9'` digits for dictionary prediction instead of multi-tap. */
    val isT9Predictive: Boolean = false,
    /** Uncommitted T9 keypad digits waiting for the user to pick a predicted word. */
    val t9PredictiveDigits: String = "",
    /** Field properties captured from the target [AccessibilityNodeInfo] when the session started. */
    val metadata: CoverFieldMetadata = CoverFieldMetadata(),
    /** Flag reserved for indicating in-flight asynchronous injection work. */
    val isInjecting: Boolean = false,
    /** Short status label shown in the overlay header bar (e.g. `"Synced"`, `"Typing..."`). */
    val lastStatusMessage: String = "Ready",
    /** True when [lastStatusMessage] represents a successful sync/commit (turns status dot green). */
    val isSuccessFeedback: Boolean = false
)

