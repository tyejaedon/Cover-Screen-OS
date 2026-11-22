package com.tyejaedon.coverscreenos.overlay.input

import android.R
import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.tyejaedon.coverscreenos.datastore.KeyboardAnchor
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.keyboard.floris.ClamshellDockAnchor
import com.tyejaedon.coverscreenos.keyboard.floris.CoverEditorBridge
import com.tyejaedon.coverscreenos.keyboard.floris.CoverFlorisKeyboardSurface
import com.tyejaedon.coverscreenos.keyboard.floris.CoverLayoutMode
import com.tyejaedon.coverscreenos.keyboard.floris.CoverOverlayInputCallbacks
import com.tyejaedon.coverscreenos.keyboard.floris.FlorisKeyboardCustomization
import com.tyejaedon.coverscreenos.keyboard.floris.FlorisThemePalette
import com.tyejaedon.coverscreenos.keyboard.nlp.CoverKeyboardNlpManager
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.ClipboardHistory
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.ClipboardActionIcons
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.ClipboardHistoryRow
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.ComposingTextPreview
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.HapticTier
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.rememberHapticPerformer
import com.tyejaedon.coverscreenos.helpers.CoverDisplayHelper
import com.tyejaedon.coverscreenos.overlay.surface.CoverComposeSurface
import com.tyejaedon.coverscreenos.overlay.surface.CoverSurfaceWindowConfig
import com.tyejaedon.coverscreenos.notifications.CallPackageMatchers
import com.tyejaedon.coverscreenos.overlay.CoverHomeSurfaceMatcher
import com.tyejaedon.coverscreenos.overlay.ForegroundService
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.TextBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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

private const val TAG = "CoverInputInjection"
private const val OVERLAY_RECLAIM_LOG_TAG = "CoverOverlayReclaim"
/** Secondary hardware cover display ID on clamshell foldables (0 is the inner/default display). */
private const val COVER_DISPLAY_ID = 1
private const val MULTI_TAP_CYCLE_TIMEOUT_MS = 850L
/** Window during which a `TYPE_VIEW_TEXT_CHANGED` echo matching `lastInjectedText` is ignored. */
private const val INJECTION_ECHO_IGNORE_WINDOW_MS = 600L
/** Minimum interval between identical global navigation gestures (`onGesture`). */
private const val GESTURE_DEBOUNCE_MS = 550L
/** Cooldown throttling rapid repeated global actions (Back/Home/Recents/Notifications). */
private const val ACTION_THROTTLE_MS = 300L
/** Minimum cadence for refreshing `latestForegroundEventElapsedMs` for the same package/class. */
private const val FOREGROUND_EVENT_REFRESH_MIN_INTERVAL_MS = 250L
/** Continuous dwell duration on a cover-home/system-launcher surface before reclaiming the overlay. */
private const val RECLAIM_STABILITY_DEBOUNCE_MS = 1_500L
/** Suppress overlay reclaim shortly after a real user app was observed in the foreground. */
private const val RECENT_USER_APP_GUARD_MS = 2_000L
// Content changes during the first 1.5 s after attach extend a pending focus-loss hold.
private const val FIELD_FOCUS_GRACE_MS = 1_500L
// Require the target editor to be absent continuously for this long after a non-editable
// focus event from its own package.
private const val FOCUS_LOSS_CONFIRM_DELAY_MS = 400L
/** Polling interval used by [CoverInputAccessibilityService.scheduleFocusLossVerification]. */
private const val FOCUS_LOSS_POLL_MS = 50L
// Upper bound used when clearing an editor through the InputConnection and the
// extracted-text length is unavailable. Comfortably exceeds any realistic field.
private const val INPUT_CONNECTION_CLEAR_SPAN = 5_000

/** System launcher prefixes indicating the user returned to the stock cover home screen. */
private val OVERLAY_RECLAIM_PACKAGE_PREFIXES = arrayOf(
    "com.sec.android.app.launcher"
)
/** Transient System UI / AOD packages that should not prematurely reclaim or hijack tracking. */
private val TRANSIENT_SYSTEM_UI_PREFIXES = arrayOf(
    "com.android.systemui",
    "com.samsung.systemui",
    "com.samsung.android.app.aodservice"
)
/** Packages excluded when tracking the most recent third-party user app on the cover screen. */
private val NON_USER_APP_PREFIXES = arrayOf(
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
    val packageName: String = "",
    val viewIdResourceName: String? = null,
    val hintText: String = "",
    val isNumeric: Boolean = false,
    val isPassword: Boolean = false,
    val isPhoneNumber: Boolean = false,
    val isMultiLine: Boolean = false,
    val imeAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
    val initialText: String = "",
    val selectionStart: Int = -1,
    val selectionEnd: Int = -1,
    val boundsInScreen: Rect = Rect()
) {
    companion object {
        /**
         * Inspects [node] and converts its Android `InputType`, hint/content text, selection span,
         * screen bounds, and `extras` [Bundle] into a [CoverFieldMetadata] snapshot.
         */
        fun fromNode(node: AccessibilityNodeInfo?): CoverFieldMetadata {
            if (node == null) return CoverFieldMetadata()

            val inputType = node.inputType
            val inputClass = inputType and InputType.TYPE_MASK_CLASS

            val isNumericClass = inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME

            val isPhoneClass = inputClass == InputType.TYPE_CLASS_PHONE

            val isPasswordField = isPasswordInputType(inputType) || node.isPassword

            val bounds = Rect()
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
    private var candidateSinceMs: Long? = null
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
    val isActive: Boolean = false,
    val buffer: String = "",
    val cursorPosition: Int = 0,
    val keyboardMode: CoverKeyboardMode = CoverKeyboardMode.NUMERIC_PIN,
    val isT9Predictive: Boolean = false,
    val t9PredictiveDigits: String = "",
    val metadata: CoverFieldMetadata = CoverFieldMetadata(),
    val isInjecting: Boolean = false,
    val lastStatusMessage: String = "Ready",
    val isSuccessFeedback: Boolean = false
)

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

    private val clipboardHistoryBuffer = ClipboardHistory()
    private val _clipboardHistory = MutableStateFlow<List<String>>(emptyList())
    /** Most-recent-first clipboard snippets for the Phase H clipboard-history strip. */
    val clipboardHistory: StateFlow<List<String>> = _clipboardHistory.asStateFlow()

    private var activeAccessibilityService: CoverInputAccessibilityService? = null
    private var overlayManager: CoverKeyboardOverlayManager? = null
    private var settingsStore: LauncherSettingsStore? = null
    /** Main-thread coroutine scope hosting the DataStore settings collector while bound. */
    private var settingsScope: CoroutineScope? = null
    private var settingsJob: Job? = null
    /** Dedicated IO coroutine scope for non-blocking per-package keyboard mode writes. */
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var savedModes: Map<String, CoverKeyboardMode> = emptyMap()
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

    private var lastInjectedText: String? = null
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

/**
 * Cover-screen [AccessibilityService] responsible for:
 * - Observing focus, click, text, and window events on the secondary cover display (`displayId == 1`).
 * - Tracking the active cover foreground package and requesting overlay reclaim or call passthrough
 *   when appropriate.
 * - Suppressing the stock system IME (`SHOW_MODE_HIDDEN`) while our cover keyboard overlay is shown.
 * - Injecting keystrokes and editor actions via `InputConnection` (API 33+ `FLAG_INPUT_METHOD_EDITOR`)
 *   with `AccessibilityNodeInfo` (`ACTION_SET_TEXT`, `ACTION_PASTE`, `ACTION_SET_SELECTION`) fallbacks.
 * - Intercepting system navigation gestures and `KEYCODE_BACK` when the cover overlay is active.
 */
class CoverInputAccessibilityService : AccessibilityService() {

    companion object {
        /** Most recently observed non-self foreground package on the cover display. */
        @Volatile
        private var latestForegroundPackage: String? = null

        /** Class name associated with [latestForegroundPackage], if supplied by the event. */
        @Volatile
        private var latestForegroundClassName: String? = null

        /** `SystemClock.elapsedRealtime()` timestamp when [latestForegroundPackage] was refreshed. */
        @Volatile
        private var latestForegroundEventElapsedMs: Long = 0L

        /**
         * The currently connected service instance, used as a background-activity-launch (BAL)
         * exempt channel for starting apps on the cover display.
         *
         * A running AccessibilityService is one of the few contexts still allowed to call
         * [Context.startActivity] from the background on modern Android. The overlay window is
         * owned by a Service, so launches issued from it are silently discarded by the system
         * (no exception is thrown) unless routed through here.
         */
        @Volatile
        private var connectedService: CoverInputAccessibilityService? = null

        /** Returns the latest non-self package observed on the cover display, or `null`. */
        fun currentForegroundPackage(): String? = latestForegroundPackage

        /** Returns the latest foreground window class name observed on the cover display, or `null`. */
        fun currentForegroundClassName(): String? = latestForegroundClassName

        /**
         * Elapsed milliseconds since the last foreground package update on the cover display,
         * or [Long.MAX_VALUE] if no foreground event has been recorded yet.
         */
        fun currentForegroundPackageEventAgeMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long {
            val eventElapsedMs = latestForegroundEventElapsedMs
            if (eventElapsedMs <= 0L) return Long.MAX_VALUE
            return (nowElapsedMs - eventElapsedMs).coerceAtLeast(0L)
        }

        /** True when the accessibility service is connected and can dispatch launches. */
        fun isConnected(): Boolean = connectedService != null

        /**
         * Starts [launchIntent] using the accessibility service context, bypassing BAL
         * restrictions. Returns false when the service is not connected or the launch failed,
         * so callers can fall back to a normal [Context.startActivity].
         */
        fun startActivityFromAccessibilityService(
            launchIntent: Intent,
            launchOptions: Bundle?
        ): Boolean {
            val service = connectedService ?: return false

            return runCatching {
                service.startActivity(launchIntent, launchOptions)
                true
            }.getOrElse { error ->
                Log.w(TAG, "Accessibility-service launch failed: ${error.message}")
                false
            }
        }
    }

    /** Cached handle to the focused editable node; refreshed before use and recycled on replace/destroy. */
    private var targetFocusedNode: AccessibilityNodeInfo? = null
    private var lastWindowPackage: String? = null
    private var lastGestureId: Int? = null
    private var lastGestureAtMs: Long = 0L
    private var lastActionAtMs: Long = 0L
    private var reclaimCandidatePackage: String? = null
    private var reclaimCandidateSinceElapsedMs: Long = 0L
    private var lastKnownUserAppPackage: String? = null
    private var lastKnownUserAppElapsedMs: Long = 0L
    // Guard window used to swallow the WINDOW_STATE_CHANGED event that our own
    // cover keyboard overlay emits immediately after WindowManager#addView.
    // Without this guard, showing the keyboard would trigger a "lost focus"
    // dismissal ~300 ms later because rootInActiveWindow briefly resolves to
    // the newly-attached overlay (which has no editable focused node).
    private var fieldFocusClaimedAtMs: Long = 0L
    /** Main-looper handler driving the [FOCUS_LOSS_POLL_MS] verification checks. */
    private val focusLossHandler = Handler(Looper.getMainLooper())
    private var pendingFocusLossCheck: Runnable? = null
    private val focusLossGate = FocusLossGate()

    /**
     * Defers a dismissal until we can confirm, across interactive windows, that
     * no editable field holds input focus any more.
     *
     * Any subsequent focus event cancels the pending check, so ordinary window churn inside
     * the target app (dialogs, ripples, keyboard-driven relayouts) no longer yanks the cover
     * keyboard away mid-session.
     */
    fun scheduleFocusLossVerification(reason: String) {
        if (!CoverInputSessionManager.sessionState.value.isActive || !focusLossGate.isArmed) return
        pendingFocusLossCheck?.let { focusLossHandler.removeCallbacks(it) }
        pendingFocusLossCheck = null
        val check = object : Runnable {
            override fun run() {
                pendingFocusLossCheck = null
                val now = SystemClock.elapsedRealtime()
                // Require active input focus within the target package so stale non-focused
                // nodes do not keep the keyboard alive after navigation.
                val targetMissing = resolveTargetInputNode(requireInputFocus = true) == null
                if (focusLossGate.shouldDismiss(now, targetMissing)) {
                    Log.d(TAG, "focus_loss result=dismiss reason=$reason targetMissing=true holdMs=$FOCUS_LOSS_CONFIRM_DELAY_MS")
                    focusLossGate.onEditableFocus()
                    CoverInputSessionManager.onFieldLostFocus(reason)
                } else if (focusLossGate.isArmed && CoverInputSessionManager.sessionState.value.isActive) {
                    Log.d(TAG, "focus_loss result=retain reason=$reason targetMissing=$targetMissing")
                    focusLossHandler.postDelayed(this, FOCUS_LOSS_POLL_MS)
                    pendingFocusLossCheck = this
                }
            }
        }
        pendingFocusLossCheck = check
        focusLossHandler.post(check)
    }

    /** Cancels any queued focus-loss poll and resets [focusLossGate] to editable-focused state. */
    fun cancelPendingFocusLossVerification() {
        pendingFocusLossCheck?.let { focusLossHandler.removeCallbacks(it) }
        pendingFocusLossCheck = null
        focusLossGate.onEditableFocus()
    }

    /**
     * Configures the service's event mask and flags (`FLAG_REPORT_VIEW_IDS`,
     * `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`, `FLAG_REQUEST_FILTER_KEY_EVENTS`,
     * `FLAG_INPUT_METHOD_EDITOR`), registers this instance with [CoverInputSessionManager],
     * and initializes the keyboard NLP engine.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "CoverInputAccessibilityService connected")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                // Grants access to the focused editor's InputConnection so the cover
                // keyboard can commit text the same way a system IME does. Without this
                // flag getInputMethod() stays null and we are limited to accessibility
                // actions, which virtual-hierarchy apps do not implement.
                AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR
            notificationTimeout = 50L
        }
        serviceInfo = info

        connectedService = this
        CoverInputSessionManager.bindService(this)
        CoverKeyboardNlpManager.initializeFromContext(applicationContext)
    }

    /**
     * Processes cover-display accessibility events (`displayId == 1`) for foreground tracking,
     * overlay reclaim, editable focus attach, text-change echo filtering, and focus-loss grace.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Strictly isolate cover-screen handling from the main inner display (display 0).
        if (!isCoverScreenEvent(event)) return

        val packageName = event.packageName
            ?.toString()
            ?.trim()
            .takeUnless { it.isNullOrEmpty() }

        if (packageName != null) {
            processForegroundTrackingAndReclaim(event, packageName)
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handleFocusOrClickEvent(event)

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleTextChangedEvent(event)

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (packageName == CoverInputSessionManager.sessionState.value.metadata.packageName &&
                    fieldFocusClaimedAtMs != 0L &&
                    SystemClock.elapsedRealtime() - fieldFocusClaimedAtMs < FIELD_FOCUS_GRACE_MS
                ) {
                    focusLossGate.onContentChurn(SystemClock.elapsedRealtime())
                    Log.d(TAG, "focus_loss result=grace_reset package=$packageName")
                }
            }

            else -> Unit // Other AccessibilityEvent types are not consumed by this service.
        }
    }

    /** Translates accessibility swipe gestures into global navigation actions while the cover overlay is active. */
    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean {
        if (!ForegroundService.isOverlayActive) return false

        val gestureId = gestureEvent.gestureId
        if (shouldDebounceGesture(gestureId)) return false

        val globalAction = when (gestureId) {
            GESTURE_SWIPE_LEFT -> GLOBAL_ACTION_BACK
            GESTURE_SWIPE_UP -> GLOBAL_ACTION_HOME
            GESTURE_SWIPE_RIGHT -> GLOBAL_ACTION_RECENTS
            GESTURE_SWIPE_DOWN -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return false
        }
        if (shouldThrottleGlobalAction()) return false

        return performGlobalAction(globalAction)
    }

    /** Routes hardware/navigation `KEYCODE_BACK` presses to `GLOBAL_ACTION_BACK` while the overlay is active. */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!ForegroundService.isOverlayActive) return super.onKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (shouldThrottleGlobalAction()) return true
            return performGlobalAction(GLOBAL_ACTION_BACK)
        }

        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {
        // onInterrupt is the framework asking us to stop any *feedback* in progress
        // (spoken/haptic), e.g. when another accessibility service takes over
        // announcements. It is NOT a teardown signal and does not mean the service or
        // the focused field went away, so tearing down an in-progress typing session
        // here loses the user's buffer mid-word. Log only.
        Log.w(TAG, "CoverInputAccessibilityService interrupted (session preserved)")
    }

    /** Unbinds [CoverInputSessionManager], cancels focus-loss timers, and recycles cached nodes. */
    override fun onDestroy() {
        super.onDestroy()
        cancelPendingFocusLossVerification()
        if (connectedService === this) {
            connectedService = null
        }
        CoverInputSessionManager.unbindService()
        targetFocusedNode?.recycle()
        targetFocusedNode = null
    }

    /** Hides Samsung HoneyBoard / system soft keyboard via [AccessibilityService.SoftKeyboardController]. */
    fun suppressHoneyBoardSoftKeyboard() {
        softKeyboardController.showMode = SHOW_MODE_HIDDEN
    }

    /** Restores normal system soft keyboard visibility (`SHOW_MODE_AUTO`) after overlay dismissal. */
    fun restoreSoftKeyboardMode() {
        softKeyboardController.showMode = SHOW_MODE_AUTO
    }

    /**
     * Moves the cursor to [position] in the focused editor. Prefers `InputConnection.setSelection`
     * when the active editor belongs to the session's target package, falling back to
     * `AccessibilityNodeInfo.ACTION_SET_SELECTION` with `ACTION_ARGUMENT_SELECTION_START_INT`
     * and `ACTION_ARGUMENT_SELECTION_END_INT`.
     */
    fun moveCursorInFocusedField(position: Int): Boolean {
        val targetPackage = CoverInputSessionManager.sessionState.value.metadata.packageName
        val editorInfo = runCatching { inputMethod?.currentInputEditorInfo }.getOrNull()
        val connection = runCatching { inputMethod?.currentInputConnection }.getOrNull()
        if (connection != null && editorInfo?.packageName == targetPackage &&
            runCatching { connection.setSelection(position, position) }.isSuccess
        ) return true

        val node = resolveTargetInputNode(requireInputFocus = true)
        if (node == null) {
            Log.w(TAG, "moveCursorInFocusedField: target editor unavailable")
            return false
        }
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, position)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, position)
        }
        val performed = node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
        if (!performed) Log.w(TAG, "moveCursorInFocusedField: target rejected selection")
        return performed
    }

    /**
     * Replaces the focused node's text via `AccessibilityNodeInfo.ACTION_SET_TEXT` using
     * `ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE`, then places the caret at the end with
     * `ACTION_SET_SELECTION`.
     *
     * Issues best-effort `ACTION_FOCUS` and `ACTION_ACCESSIBILITY_FOCUS` first because several
     * apps ignore `ACTION_SET_TEXT` if focus was momentarily unsettled.
     */
    fun injectTextIntoFocusedNode(text: String): Boolean {
        val node = resolveTargetInputNode(
            requireInputFocus = CoverInputSessionManager.sessionState.value.metadata.isPassword
        ) ?: run {
            Log.w(TAG, "injectTextIntoFocusedNode: no editable target node resolved")
            return false
        }
        // Nodes belonging to virtual hierarchies (Flutter/RN/WebView) frequently do not
        // advertise ACTION_SET_TEXT at all; performAction then returns false without any
        // further signal. Log the advertised action set so failures are self-diagnosing.
        val supportsSetText = node.actionList.any {
            it.id == AccessibilityNodeInfo.ACTION_SET_TEXT
        }
        if (!supportsSetText) {
            Log.w(
                TAG,
                "injectTextIntoFocusedNode: node does not advertise ACTION_SET_TEXT " +
                    "pkg=${node.packageName} class=${node.className} " +
                    "actions=${describeNodeActions(node)}"
            )
            return false
        }
        // Some apps (M-Pesa, Chrome inputs) refuse ACTION_SET_TEXT unless the node
        // currently holds accessibility/input focus. Our keyboard overlay is
        // FLAG_NOT_FOCUSABLE, but the target window may still have relinquished
        // input focus. Best-effort re-focus before writing.
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) }

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        if (ok) {
            val selArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length)
            }
            runCatching { node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs) }
        }
        Log.d(TAG, "injectTextIntoFocusedNode result=$ok len=${text.length} pkg=${node.packageName}")
        return ok
    }

    /**
     * Apply a minimal [BufferDelta] to the focused editor. Preferred over
     * [syncTextToFocusedField] because it avoids the full-buffer rewrite
     * that fires onchange handlers on every keystroke.
     *
     * Injection channels tried in order:
     *
     *  1. **InputConnection** (`IME_INPUT_CONNECTION`): reads the target's
     *     live selection via [android.view.inputmethod.InputConnection.getSurroundingText]
     *     to reconcile any drift the app made under us, then issues a
     *     `setSelection` + range replace (`commitText("", 1, null)` / `commitText(delta.insert, 1, null)`)
     *     for the delta only. Zero full-buffer traffic when the target and overlay agree.
     *  2. **ACTION_SET_TEXT** (`ACTION_SET_TEXT`): accessibility-node fallback.
     *     Writes the full new buffer (accessibility offers no delta primitive) via
     *     [injectTextIntoFocusedNode] and calls [setSelectionOnCachedNode] to sync the caret.
     *  3. **Clipboard paste** (`ACTION_PASTE_CLIPBOARD`): last resort via [injectViaClipboard].
     *
     * Callers must hold [CoverInputSessionManager.injectionLock].
     */
    internal fun applyDeltaToFocusedField(delta: BufferDelta): InjectionMethod {
        if (delta.isNoOp) return InjectionMethod.NONE
        // Secure/password fields take the dedicated full-sync path (which verifies input focus,
        // matches package name on EditorInfo, and skips clipboard fallback).
        if (CoverInputSessionManager.sessionState.value.metadata.isPassword) {
            return syncTextToFocusedField(delta.new)
        }

        if (injectDeltaViaInputConnection(delta)) return InjectionMethod.IME_INPUT_CONNECTION

        // Fallback for virtual hierarchies (Flutter/RN/WebView/M-Pesa) that
        // don't expose a working InputConnection but do accept ACTION_SET_TEXT.
        // Skip the write if the target already reflects the new text — this
        // saves a full ACTION_SET_TEXT round trip when the delta was already
        // applied via a11y echo (rare but observable on Samsung One UI).
        val cachedNode = targetFocusedNode
        val currentTargetText = runCatching {
            if (cachedNode != null && cachedNode.refresh()) cachedNode.text?.toString() else null
        }.getOrNull()
        if (currentTargetText != null && currentTargetText == delta.new) {
            // Text already matches; just move caret.
            setSelectionOnCachedNode(delta.newCaret)
            return InjectionMethod.ACTION_SET_TEXT
        }
        if (injectTextIntoFocusedNode(delta.new)) {
            setSelectionOnCachedNode(delta.newCaret)
            return InjectionMethod.ACTION_SET_TEXT
        }
        if (injectViaClipboard(delta.new)) return InjectionMethod.ACTION_PASTE_CLIPBOARD
        return InjectionMethod.NONE
    }

    /**
     * Push [delta] through the target's [android.view.inputmethod.InputConnection]
     * using `setSelection` + `commitText` on the delta substring only.
     *
     * Reconciles overlay expectations with the target's live selection by
     * reading `getSurroundingText` first: if the app or user has moved the
     * caret since our last write, we anchor the delta to the *target's*
     * current position rather than blindly assuming our cached one is
     * still authoritative.
     */
    private fun injectDeltaViaInputConnection(delta: BufferDelta): Boolean {
        val connection = runCatching { inputMethod?.currentInputConnection }
            .getOrNull() ?: return false
        return runCatching {
            // Try to align the target's caret to `delta.replaceStart`. Use the
            // live surrounding-text extent when available so we don't fight
            // the app over cursor position.
            val surrounding = runCatching {
                connection.getSurroundingText(
                    INPUT_CONNECTION_CLEAR_SPAN,
                    INPUT_CONNECTION_CLEAR_SPAN,
                    0
                )
            }.getOrNull()

            val targetLen = surrounding?.text?.length
            val start = delta.replaceStart.coerceAtLeast(0)
                .let { s -> if (targetLen != null) s.coerceAtMost(targetLen) else s }
            val end = delta.replaceEnd.coerceAtLeast(start)
                .let { e -> if (targetLen != null) e.coerceAtMost(targetLen) else e }

            // Place caret at the start of the range, then delete the range,
            // then commit the insert. commitText with newCursorPosition = 1
            // leaves the caret after the inserted substring — matching
            // delta.newCaret when insert.length == end - start + (delta.insert.length).
            connection.setSelection(start, end)
            if (end > start) {
                // deleteSurroundingText deletes chars *around* the caret; since
                // we just selected [start, end], deleting the selection by
                // committing an empty string first is safer than trying to
                // interpret surrounding semantics with a range selection.
                connection.commitText("", 1, null)
            }
            if (delta.insert.isNotEmpty()) {
                connection.commitText(delta.insert, 1, null)
            }
            // Fine-tune caret to exactly the requested position — commitText
            // above lands us at `start + insert.length` which may not equal
            // delta.newCaret when the delta represents a caret-only move
            // baked into an edit.
            if (delta.newCaret != start + delta.insert.length) {
                runCatching { connection.setSelection(delta.newCaret, delta.newCaret) }
            }
            true
        }.getOrElse { err ->
            Log.w(TAG, "injectDeltaViaInputConnection failed: ${err.message}")
            false
        }.also { ok ->
            Log.d(
                TAG,
                "injectDeltaViaInputConnection result=$ok removed=${delta.removedLength} " +
                    "insertLen=${delta.insert.length} newCaret=${delta.newCaret}"
            )
        }
    }

    /** Updates the selection caret on [targetFocusedNode] via `ACTION_SET_SELECTION`. */
    private fun setSelectionOnCachedNode(caret: Int) {
        val node = targetFocusedNode ?: return
        runCatching {
            val args = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, caret)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, caret)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
        }
    }

    /**
     * Replaces the focused editor's contents by writing through its real
     * [InputConnection], which is the same channel a system keyboard uses.
     *
     * This is the primary sync path because it does not depend on the target exposing an
     * accessibility node that implements `ACTION_SET_TEXT`. Apps built on virtual view
     * hierarchies (the Safaricom M-Pesa app among them) expose read-only mirror nodes, so
     * `ACTION_SET_TEXT` and `ACTION_PASTE` both return false there while the input connection
     * still accepts text normally.
     *
     * Requires [AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR]; the connection is only
     * non-null while an editor is actually focused.
     */
    fun injectViaInputConnection(text: String): Boolean {
        if (CoverInputSessionManager.sessionState.value.metadata.isPassword) {
            val targetPackage = CoverInputSessionManager.sessionState.value.metadata.packageName
            if (resolveTargetInputNode(requireInputFocus = true) == null ||
                runCatching { inputMethod?.currentInputEditorInfo?.packageName }.getOrNull() != targetPackage
            ) return false
        }
        val connection = runCatching { inputMethod?.currentInputConnection }
            .getOrNull()
            ?: run {
                Log.d(TAG, "injectViaInputConnection: no active input connection")
                return false
            }

        return runCatching {
            // Clear the existing contents so the commit below is a full replace of the cover
            // keyboard buffer rather than an append. AccessibilityInputConnection exposes no
            // batch-edit or extracted-text APIs, so we size the delete from the surrounding
            // text when the editor reports it and fall back to a generous fixed span.
            val surrounding = runCatching {
                connection.getSurroundingText(
                    INPUT_CONNECTION_CLEAR_SPAN,
                    INPUT_CONNECTION_CLEAR_SPAN,
                    0
                )
            }.getOrNull()

            val beforeLength = surrounding?.selectionStart ?: INPUT_CONNECTION_CLEAR_SPAN
            val afterLength = surrounding
                ?.let { (it.text.length - it.selectionEnd).coerceAtLeast(0) }
                ?: INPUT_CONNECTION_CLEAR_SPAN

            connection.deleteSurroundingText(beforeLength, afterLength)
            connection.commitText(text, 1, null)
            true
        }.getOrElse { error ->
            Log.w(TAG, "injectViaInputConnection failed: ${error.message}")
            false
        }.also { ok ->
            Log.d(TAG, "injectViaInputConnection result=$ok len=${text.length}")
        }
    }

    /**
     * Pushes [text] into the focused editor using the first channel that succeeds and
     * reports which one was used, so a failure to sync is always attributable.
     */
    fun syncTextToFocusedField(text: String): InjectionMethod {
        if (CoverInputSessionManager.sessionState.value.metadata.isPassword &&
            resolveTargetInputNode(requireInputFocus = true) == null
        ) return InjectionMethod.NONE
        if (injectViaInputConnection(text)) return InjectionMethod.IME_INPUT_CONNECTION
        if (injectTextIntoFocusedNode(text)) return InjectionMethod.ACTION_SET_TEXT
        if (injectViaClipboard(text)) return InjectionMethod.ACTION_PASTE_CLIPBOARD
        return InjectionMethod.NONE
    }

    /**
     * Last-resort text injection fallback using [ClipboardManager] and
     * [AccessibilityNodeInfo.ACTION_PASTE].
     *
     * Refuses password/secure fields (both via session metadata and a fresh [CoverFieldMetadata]
     * check on the resolved node) so sensitive input is never written to the shared system
     * clipboard. Because [ClipboardManager.setPrimaryClip] replaces the current primary clip
     * without saving/restoring any prior clipboard item, this channel is only attempted when
     * `InputConnection` and `ACTION_SET_TEXT` both fail, or when the user explicitly pastes.
     */
    fun injectViaClipboard(text: String): Boolean {
        if (CoverInputSessionManager.sessionState.value.metadata.isPassword) {
            Log.w(TAG, "Clipboard fallback refused for secure field")
            return false
        }
        val node = resolveTargetInputNode() ?: return false
        if (CoverFieldMetadata.fromNode(node).isPassword) {
            Log.w(TAG, "Clipboard fallback refused for secure target node")
            return false
        }
        val supportsPaste = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_PASTE }
        if (!supportsPaste) {
            Log.w(
                TAG,
                "injectViaClipboard: node does not advertise ACTION_PASTE " +
                    "pkg=${node.packageName} actions=${describeNodeActions(node)}"
            )
            return false
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("cover_input", text))
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        Log.d(TAG, "injectViaClipboard result=$ok len=${text.length} pkg=${node.packageName}")
        return ok
    }

    /**
     * Phase H: copies the focused field's current selection via the accessibility
     * `ACTION_COPY` action, then reads back whatever landed on the system clipboard so the
     * cover keyboard's clipboard-history strip can show it. Returns `null` (and does nothing)
     * for secure fields or nodes that don't advertise copy support (no active selection).
     */
    fun copySelectionFromFocusedNode(): String? {
        val node = resolveTargetInputNode() ?: return null
        if (CoverFieldMetadata.fromNode(node).isPassword) {
            Log.w(TAG, "copySelectionFromFocusedNode: refused for secure target node")
            return null
        }
        if (node.actionList.none { it.id == AccessibilityNodeInfo.ACTION_COPY }) {
            Log.w(
                TAG,
                "copySelectionFromFocusedNode: node does not advertise ACTION_COPY " +
                    "pkg=${node.packageName} actions=${describeNodeActions(node)}"
            )
            return null
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_COPY)) return null
        return readClipboardText()
    }

    /**
     * Phase H: cuts the focused field's current selection via `ACTION_CUT`, mirroring
     * [copySelectionFromFocusedNode]. Refuses secure fields the same way.
     */
    fun cutSelectionFromFocusedNode(): String? {
        val node = resolveTargetInputNode() ?: return null
        if (CoverFieldMetadata.fromNode(node).isPassword) {
            Log.w(TAG, "cutSelectionFromFocusedNode: refused for secure target node")
            return null
        }
        if (node.actionList.none { it.id == AccessibilityNodeInfo.ACTION_CUT }) {
            Log.w(
                TAG,
                "cutSelectionFromFocusedNode: node does not advertise ACTION_CUT " +
                    "pkg=${node.packageName} actions=${describeNodeActions(node)}"
            )
            return null
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_CUT)) return null
        return readClipboardText()
    }

    /** Pastes [text] into the focused field by staging it on the clipboard then dispatching
     * `ACTION_PASTE` — used by the clipboard-history strip to re-paste an older snippet. */
    fun pasteTextIntoFocusedNode(text: String): Boolean = injectViaClipboard(text)

    /** Current system-clipboard text, or null if empty/unavailable — used by the toolbar's
     * primary Paste button when pasting without picking a specific history entry. */
    fun currentClipboardText(): String? = readClipboardText()

    /** Reads item 0 from [ClipboardManager.getPrimaryClip] coerced to plain text, or `null`. */
    private fun readClipboardText(): String? {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return runCatching { clip.getItemAt(0).coerceToText(this)?.toString() }.getOrNull()
    }

    /** Formats a node's advertised `AccessibilityAction` IDs into a readable log string. */
    private fun describeNodeActions(node: AccessibilityNodeInfo): String =
        runCatching {
            node.actionList.joinToString(",") { action ->
                when (action.id) {
                    AccessibilityNodeInfo.ACTION_SET_TEXT -> "SET_TEXT"
                    AccessibilityNodeInfo.ACTION_PASTE -> "PASTE"
                    AccessibilityNodeInfo.ACTION_COPY -> "COPY"
                    AccessibilityNodeInfo.ACTION_CUT -> "CUT"
                    AccessibilityNodeInfo.ACTION_FOCUS -> "FOCUS"
                    AccessibilityNodeInfo.ACTION_CLICK -> "CLICK"
                    AccessibilityNodeInfo.ACTION_SET_SELECTION -> "SET_SELECTION"
                    else -> "0x${Integer.toHexString(action.id)}"
                }
            }.ifEmpty { "<none>" }
        }.getOrDefault("<unavailable>")


    /**
     * Locates the editable target field the user tapped on. On multi-display Samsung
     * devices, once our `TYPE_ACCESSIBILITY_OVERLAY` keyboard is attached on display 1,
     * `rootInActiveWindow` frequently resolves to our own Compose overlay instead of
     * the underlying app window, causing `ACTION_SET_TEXT` to silently fail. To work
     * around that we:
     *  1. Trust the cached node captured at focus time ([targetFocusedNode]) if [AccessibilityNodeInfo.refresh]
     *     succeeds and the node remains editable (and focused in `targetPackage` when [requireInputFocus] is true).
     *  2. Walk interactive windows returned by `AccessibilityService.getWindows()` (enabled by
     *     `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`, distinct from API 30+ `getWindowsOnAllDisplays()`),
     *     skipping any window owned by our own package, and pick the first editable `FOCUS_INPUT`
     *     node (or any editable node via [findEditableNode] when [requireInputFocus] is false).
     *  3. Fall back to `rootInActiveWindow` as a last resort.
     */
    private fun resolveTargetInputNode(requireInputFocus: Boolean = false): AccessibilityNodeInfo? {
        val targetPackage = CoverInputSessionManager.sessionState.value.metadata.packageName
        val cached = targetFocusedNode
        if (cached != null &&
            runCatching { cached.refresh() }.getOrDefault(false) &&
            cached.isEditable &&
            (!requireInputFocus || (cached.isFocused && cached.packageName?.toString() == targetPackage))
        ) {
            return cached
        }

        val ownPackage = this.packageName
        // Enumerate interactive AccessibilityWindowInfo entries rather than relying solely on
        // rootInActiveWindow, which is often shadowed by our own overlay window.
        val windowList = runCatching { windows }.getOrNull().orEmpty()
        for (window in windowList) {
            val root = runCatching { window.root }.getOrNull() ?: continue
            val pkg = root.packageName?.toString()
            if (pkg == ownPackage) continue
            if (requireInputFocus && pkg != targetPackage) continue

            val focused = runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
            if (focused != null && focused.isEditable) {
                targetFocusedNode?.recycle()
                targetFocusedNode = AccessibilityNodeInfo.obtain(focused)
                return targetFocusedNode
            }
            val editable = if (requireInputFocus) null else findEditableNode(root)
            if (editable != null) {
                targetFocusedNode?.recycle()
                targetFocusedNode = AccessibilityNodeInfo.obtain(editable)
                return targetFocusedNode
            }
        }

        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() == ownPackage) return null
        if (requireInputFocus && root.packageName?.toString() != targetPackage) return null
        val fallbackFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (fallbackFocus != null && fallbackFocus.isEditable) {
            targetFocusedNode?.recycle()
            targetFocusedNode = AccessibilityNodeInfo.obtain(fallbackFocus)
            return targetFocusedNode
        }
        return null
    }

    /** Dispatches the active field's resolved IME action (`imeAction` from session metadata). */
    fun dispatchActionDone() {
        dispatchImeAction(CoverInputSessionManager.sessionState.value.metadata.imeAction)
    }

    /**
     * Triggers the editor's IME action (`GO`, `SEARCH`, `SEND`, `NEXT`, `DONE`).
     *
     * Prefers `InputConnection.performEditorAction`; if unavailable, falls back to
     * [accessibilityActionForIme] (`ACTION_IME_ENTER`, `focusSearch(View.FOCUS_FORWARD)` +
     * `ACTION_FOCUS` / `ACTION_NEXT_HTML_ELEMENT` with `ACTION_ARGUMENT_HTML_ELEMENT_STRING`,
     * or `ACTION_CLICK`).
     */
    fun dispatchImeAction(action: Int) {
        val resolved = action and EditorInfo.IME_MASK_ACTION
        val actionName = when (resolved) {
            EditorInfo.IME_ACTION_GO -> "GO"
            EditorInfo.IME_ACTION_SEARCH -> "SEARCH"
            EditorInfo.IME_ACTION_SEND -> "SEND"
            EditorInfo.IME_ACTION_NEXT -> "NEXT"
            EditorInfo.IME_ACTION_DONE -> "DONE"
            else -> "UNSPECIFIED"
        }
        Log.d(TAG, "dispatchImeAction resolved=$actionName")
        val connection = runCatching { inputMethod?.currentInputConnection }.getOrNull()
        if (connection != null && resolved != EditorInfo.IME_ACTION_NONE &&
            resolved != EditorInfo.IME_ACTION_UNSPECIFIED &&
            runCatching { connection.performEditorAction(resolved) }.isSuccess
        ) {
            Log.d(TAG, "dispatchImeAction via input connection resolved=$actionName")
            return
        }

        val node = resolveTargetInputNode() ?: return
        val fallbackAction = accessibilityActionForIme(resolved)
        val performed = when (fallbackAction) {
            AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT -> {
                val args = Bundle().apply {
                    putString(AccessibilityNodeInfo.ACTION_ARGUMENT_HTML_ELEMENT_STRING, "INPUT")
                }
                (node.focusSearch(View.FOCUS_FORWARD)?.performAction(
                        AccessibilityNodeInfo.ACTION_FOCUS
                    ) == true) ||
                    node.performAction(AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT, args)
            }
            else -> node.performAction(fallbackAction)
        }
        Log.d(TAG, "dispatchImeAction resolved=$actionName performed=$performed")
    }

    /** Returns `true` only for events originating on the outer cover display ([COVER_DISPLAY_ID]). */
    private fun isCoverScreenEvent(event: AccessibilityEvent): Boolean {
        return event.displayId == COVER_DISPLAY_ID
    }

    /**
     * Handles `TYPE_VIEW_FOCUSED` and `TYPE_VIEW_CLICKED` events on the cover display.
     *
     * - Non-editable focus events inside the active session's package arm [focusLossGate] and
     *   kick off [scheduleFocusLossVerification] instead of dismissing immediately.
     * - Editable nodes cancel pending focus-loss verification, refresh and cache
     *   [targetFocusedNode], open the [fieldFocusClaimedAtMs] grace window before suppressing
     *   HoneyBoard, and attach the cover keyboard via [CoverInputSessionManager.onFieldFocused].
     */
    private fun handleFocusOrClickEvent(event: AccessibilityEvent) {
        val targetPackage = CoverInputSessionManager.sessionState.value.metadata.packageName
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED &&
            CoverInputSessionManager.sessionState.value.isActive &&
            event.packageName?.toString() == targetPackage &&
            event.source?.isEditable == false
        ) {
            val now = SystemClock.elapsedRealtime()
            focusLossGate.onNonEditableFocus(now)
            Log.d(TAG, "focus_loss result=candidate package=$targetPackage atMs=$now")
            scheduleFocusLossVerification("non-editable focus")
            return
        }
        val source = event.source ?: run {
            val root = rootInActiveWindow
            root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } ?: return

        val editableNode = findEditableNode(source)
        if (editableNode != null && editableNode.isEditable) {
            editableNode.refresh()
            // A real editable field just took focus, so any in-flight dismissal is stale.
            cancelPendingFocusLossVerification()

            targetFocusedNode?.recycle()
            targetFocusedNode = AccessibilityNodeInfo.obtain(editableNode)

            // Open the "self-inflicted window churn" grace window BEFORE we
            // touch the soft-keyboard mode or ask the session manager to show
            // the cover keyboard overlay. Both of those actions typically
            // generate WINDOW_STATE_CHANGED / WINDOWS_CHANGED events on this
            // same display, and we do not want the resulting a11y events to
            // race with our own attach and dismiss the overlay we just asked
            // for.
            fieldFocusClaimedAtMs = SystemClock.elapsedRealtime()

            suppressHoneyBoardSoftKeyboard()

            val metadata = CoverFieldMetadata.fromNode(editableNode).let { fromNode ->
                // Supplement node extras with live EditorInfo.imeOptions when available from
                // FLAG_INPUT_METHOD_EDITOR so the keyboard's action key matches the editor.
                val editorInfo = runCatching { inputMethod?.currentInputEditorInfo }.getOrNull()
                val editorAction = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                if (editorInfo?.packageName == fromNode.packageName &&
                    editorAction != null &&
                    editorAction in EditorInfo.IME_ACTION_GO..EditorInfo.IME_ACTION_DONE &&
                    editableNode.extras?.containsKey("imeOptions") != true
                ) fromNode.copy(imeAction = editorAction) else fromNode
            }
            CoverInputSessionManager.onFieldFocused(metadata)
        }
    }

    /** Observes `TYPE_VIEW_TEXT_CHANGED` events and filters echoes produced by our own injections. */
    private fun handleTextChangedEvent(event: AccessibilityEvent) {
        val eventText = event.text?.joinToString("") ?: return
        if (CoverInputSessionManager.isRecentSelfEcho(eventText)) return
    }

    /** Depth-first search for the first editable descendant starting from [node]. */
    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    /** Returns the refreshed cached [targetFocusedNode] or queries `rootInActiveWindow` for `FOCUS_INPUT`. */
    private fun getOrRefreshFocusedNode(): AccessibilityNodeInfo? {
        val cached = targetFocusedNode
        if (cached != null && cached.refresh() && cached.isEditable) {
            return cached
        }

        val root = rootInActiveWindow ?: return null
        val freshFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (freshFocus != null && freshFocus.isEditable) {
            targetFocusedNode?.recycle()
            targetFocusedNode = AccessibilityNodeInfo.obtain(freshFocus)
            return targetFocusedNode
        }
        return null
    }

    /**
     * Tracks foreground window changes on the cover display, triggers incoming-call overlay
     * passthrough, and debounces cover-home/launcher surface detection before reclaiming the
     * Cover-Screen-OS launcher overlay.
     */
    private fun processForegroundTrackingAndReclaim(event: AccessibilityEvent, foregroundPackage: String) {
        val isWindowEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (!isWindowEvent) return

        // Never let window events emitted by our own TYPE_ACCESSIBILITY_OVERLAY
        // surfaces (cover launcher overlay, cover keyboard overlay, expanded
        // media panel, etc.) count as a foreground-app signal. Otherwise the
        // moment we addView() the cover keyboard on display 1 we would refresh
        // latestForegroundPackage to our own package name; the ForegroundService
        // resume poller (60 ms cadence) would then observe that as "user
        // returned to our launcher", call completeSuppressionAndRetargetOnce(),
        // un-suppress the launcher overlay (alpha 0 -> 1, FLAG_NOT_TOUCHABLE
        // cleared) and paint it back on top of the app the user just launched.
        // That is what caused the "app disappears, cover launcher redraws"
        // symptom the moment the keyboard appeared.
        if (foregroundPackage == this.packageName) return

        val foregroundClassName = event.className
            ?.toString()
            ?.trim()
            ?.takeUnless { it.isEmpty() }

        val nowElapsedMs = SystemClock.elapsedRealtime()
        val isSameForegroundPackage = latestForegroundPackage == foregroundPackage &&
            (foregroundClassName == null || latestForegroundClassName == foregroundClassName)
        val shouldRefreshForegroundTimestamp = !isSameForegroundPackage ||
            (nowElapsedMs - latestForegroundEventElapsedMs) >= FOREGROUND_EVENT_REFRESH_MIN_INTERVAL_MS
        if (shouldRefreshForegroundTimestamp) {
            latestForegroundPackage = foregroundPackage
            latestForegroundClassName = foregroundClassName
            latestForegroundEventElapsedMs = nowElapsedMs
        }

        if (shouldTrackAsUserForegroundApp(foregroundPackage, foregroundClassName)) {
            lastKnownUserAppPackage = foregroundPackage
            lastKnownUserAppElapsedMs = nowElapsedMs
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        if (foregroundPackage != lastWindowPackage) {
            lastWindowPackage = foregroundPackage
        }

        if (shouldAllowIncomingCallSurface(foregroundPackage)) {
            ForegroundService.requestIncomingCallPassthrough(foregroundPackage)
            return
        }

        if (!shouldRequestOverlayReclaim(foregroundPackage, foregroundClassName)) {
            resetReclaimCandidate()
            return
        }

        if (reclaimCandidatePackage != foregroundPackage) {
            reclaimCandidatePackage = foregroundPackage
            reclaimCandidateSinceElapsedMs = nowElapsedMs
            return
        }

        val stableForMs = nowElapsedMs - reclaimCandidateSinceElapsedMs
        if (stableForMs < RECLAIM_STABILITY_DEBOUNCE_MS) {
            return
        }

        val userAppAgeMs = if (lastKnownUserAppElapsedMs > 0L) {
            (nowElapsedMs - lastKnownUserAppElapsedMs).coerceAtLeast(0L)
        } else {
            Long.MAX_VALUE
        }
        // Guard against reclaiming the overlay immediately after a user app was active, avoiding
        // flicker during brief transition windows back from an app.
        if (lastKnownUserAppPackage != null && userAppAgeMs < RECENT_USER_APP_GUARD_MS) {
            return
        }

        if (Log.isLoggable(OVERLAY_RECLAIM_LOG_TAG, Log.DEBUG)) {
            Log.d(OVERLAY_RECLAIM_LOG_TAG, "trigger_reclaim package=$foregroundPackage stableForMs=$stableForMs")
        }
        ForegroundService.requestOverlayReclaim(reason = foregroundPackage)
    }

    /** Suppresses duplicate gesture IDs arriving within [GESTURE_DEBOUNCE_MS]. */
    private fun shouldDebounceGesture(gestureId: Int): Boolean {
        val now = SystemClock.elapsedRealtime()
        val isRepeated = lastGestureId == gestureId && (now - lastGestureAtMs) < GESTURE_DEBOUNCE_MS
        lastGestureId = gestureId
        lastGestureAtMs = now
        return isRepeated
    }

    /** Throttles repeated global accessibility actions within [ACTION_THROTTLE_MS]. */
    private fun shouldThrottleGlobalAction(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val throttled = (now - lastActionAtMs) < ACTION_THROTTLE_MS
        if (!throttled) {
            lastActionAtMs = now
        }
        return throttled
    }

    /** Returns `true` when [packageName]/[className] represents the stock cover home surface. */
    private fun shouldRequestOverlayReclaim(packageName: String, className: String? = null): Boolean {
        return CoverHomeSurfaceMatcher.isCoverHomeDestination(packageName, className) ||
            OVERLAY_RECLAIM_PACKAGE_PREFIXES.any { prefix -> packageName.startsWith(prefix) }
    }

    /** Returns `true` when [packageName] represents a third-party user app rather than system/cover UI. */
    private fun shouldTrackAsUserForegroundApp(packageName: String, className: String? = null): Boolean {
        if (CoverHomeSurfaceMatcher.isCoverHomeDestination(packageName, className)) {
            return false
        }
        return NON_USER_APP_PREFIXES.none { prefix -> packageName.startsWith(prefix) }
    }

    private fun resetReclaimCandidate() {
        reclaimCandidatePackage = null
        reclaimCandidateSinceElapsedMs = 0L
    }

    /** Returns `true` when [packageName] belongs to an incoming call / dialer UI that needs passthrough. */
    private fun shouldAllowIncomingCallSurface(packageName: String): Boolean {
        return CallPackageMatchers.isIncomingCallPackage(packageName)
    }
}

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

    private val surface = CoverComposeSurface(
        hostContext = context,
        windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        logTag = TAG
    )
    private val displayHelper = CoverDisplayHelper(context)
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
    val sessionState by CoverInputSessionManager.sessionState.collectAsState()
    val modeHaptic = rememberHapticPerformer(HapticTier.Strong)
    var t9DigitTrace by remember(
        sessionState.metadata.packageName,
        sessionState.metadata.viewIdResourceName,
        sessionState.metadata.boundsInScreen
    ) { mutableStateOf("") }
    var t9CandidateCommitCount by remember(
        sessionState.metadata.packageName,
        sessionState.metadata.viewIdResourceName,
        sessionState.metadata.boundsInScreen
    ) { mutableIntStateOf(0) }

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

            val context = LocalContext.current
            val settingsStore = remember(context) { LauncherSettingsStore(context.applicationContext) }
            val settings by settingsStore.settings.collectAsState(initial = LauncherSettings())
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
                                themePalette = FlorisThemePalette.AmoledCover
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                CoverKeyboardMode.QWERTY -> {
                    val clipboardHistory by CoverInputSessionManager.clipboardHistory.collectAsState()
                    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
                    var canPasteFromClipboard by remember { mutableStateOf(false) }
                    LaunchedEffect(sessionState.isActive) {
                        canPasteFromClipboard = clipboardManager?.hasPrimaryClip() == true
                    }

                    val suggestionsAllowed = !sessionState.metadata.isPassword &&
                        !sessionState.metadata.isNumeric &&
                        !sessionState.metadata.isPhoneNumber
                    val currentWord = beforeCursor.takeLastWhile { it.isLetterOrDigit() }

                    var candidates by remember { mutableStateOf<List<String>>(emptyList()) }
                    var nlpRevision by remember { mutableIntStateOf(0) }
                    LaunchedEffect(sessionState.buffer, sessionState.cursorPosition, nlpRevision) {
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

