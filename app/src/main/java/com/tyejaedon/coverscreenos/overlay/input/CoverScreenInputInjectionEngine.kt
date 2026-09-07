package com.tyejaedon.coverscreenos.overlay.input

import android.R
import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.tyejaedon.coverscreenos.ui.keyboard.CoverCompactQwertyKeyboard
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tyejaedon.coverscreenos.services.CallPackageMatchers
import com.tyejaedon.coverscreenos.services.overlay.ForegroundService
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.TextBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val TAG = "CoverInputInjection"
private const val OVERLAY_RECLAIM_LOG_TAG = "CoverOverlayReclaim"
private const val COVER_DISPLAY_ID = 1
private const val MULTI_TAP_CYCLE_TIMEOUT_MS = 850L
private const val INJECTION_ECHO_IGNORE_WINDOW_MS = 600L
private const val GESTURE_DEBOUNCE_MS = 550L
private const val ACTION_THROTTLE_MS = 300L
private const val FOREGROUND_EVENT_REFRESH_MIN_INTERVAL_MS = 250L
private const val RECLAIM_STABILITY_DEBOUNCE_MS = 1_500L
private const val RECENT_USER_APP_GUARD_MS = 2_000L
// Window during which WINDOW_STATE_CHANGED events on the cover display are
// treated as "our own overlay attaching" instead of "focus lost". Chosen to
// comfortably cover the observed 250-350 ms tail between addView and the
// resulting a11y event.
private const val FIELD_FOCUS_GRACE_MS = 750L
// How long to wait before acting on an apparent focus loss. Gives the target app time to
// settle after transient window churn; any real focus event arriving inside this window
// cancels the dismissal outright.
private const val FOCUS_LOSS_CONFIRM_DELAY_MS = 450L
// Upper bound used when clearing an editor through the InputConnection and the
// extracted-text length is unavailable. Comfortably exceeds any realistic field.
private const val INPUT_CONNECTION_CLEAR_SPAN = 5_000

private val OVERLAY_RECLAIM_PACKAGE_PREFIXES = arrayOf(
    "com.sec.android.app.launcher"
)
private val TRANSIENT_SYSTEM_UI_PREFIXES = arrayOf(
    "com.android.systemui",
    "com.samsung.systemui",
    "com.samsung.android.app.aodservice"
)
private val NON_USER_APP_PREFIXES = arrayOf(
    "com.android.systemui",
    "com.samsung.systemui",
    "com.samsung.android.app.aodservice",
    "com.sec.android.app.launcher",
    "com.tyejaedon.coverscreenos"
)

enum class CoverKeyboardMode {
    NUMERIC_PIN,
    T9_MULTITAP,
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
    ACTION_SET_TEXT,
    ACTION_PASTE_CLIPBOARD,
    NONE
}

data class CoverFieldMetadata(
    val packageName: String = "",
    val viewIdResourceName: String? = null,
    val hintText: String = "",
    val isNumeric: Boolean = false,
    val isPassword: Boolean = false,
    val isPhoneNumber: Boolean = false,
    val isMultiLine: Boolean = false,
    val initialText: String = "",
    val boundsInScreen: Rect = Rect()
) {
    companion object {
        fun fromNode(node: AccessibilityNodeInfo?): CoverFieldMetadata {
            if (node == null) return CoverFieldMetadata()

            val inputType = node.inputType
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION

            val isNumericClass = inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME

            val isPhoneClass = inputClass == InputType.TYPE_CLASS_PHONE

            val isPasswordField = variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                node.isPassword

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            return CoverFieldMetadata(
                packageName = node.packageName?.toString() ?: "",
                viewIdResourceName = node.viewIdResourceName,
                hintText = node.hintText?.toString() ?: "",
                isNumeric = isNumericClass,
                isPassword = isPasswordField,
                isPhoneNumber = isPhoneClass,
                isMultiLine = node.isMultiLine,
                initialText = node.text?.toString() ?: "",
                boundsInScreen = bounds
            )
        }
    }
}

data class CoverInputSessionState(
    val isActive: Boolean = false,
    val buffer: String = "",
    val cursorPosition: Int = 0,
    val keyboardMode: CoverKeyboardMode = CoverKeyboardMode.NUMERIC_PIN,
    val metadata: CoverFieldMetadata = CoverFieldMetadata(),
    val isInjecting: Boolean = false,
    val lastStatusMessage: String = "Ready",
    val isSuccessFeedback: Boolean = false
)

object CoverInputSessionManager {

    private val _sessionState = MutableStateFlow(CoverInputSessionState())
    val sessionState: StateFlow<CoverInputSessionState> = _sessionState.asStateFlow()

    private var activeAccessibilityService: CoverInputAccessibilityService? = null
    private var overlayManager: CoverKeyboardOverlayManager? = null

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

    fun bindService(service: CoverInputAccessibilityService) {
        activeAccessibilityService = service
        overlayManager = CoverKeyboardOverlayManager(service.applicationContext)
        injectionTarget = AccessibilityServiceInjectionTarget(service)
    }

    fun unbindService() {
        dismissOverlay(reason = "Service unbound")
        overlayManager?.destroy()
        overlayManager = null
        activeAccessibilityService = null
        injectionTarget = null
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

    internal fun clearInjectionTargetForTest() {
        injectionTarget = null
        textBuffer = TextBuffer.Empty
        _sessionState.value = CoverInputSessionState()
    }

    fun onFieldFocused(metadata: CoverFieldMetadata) {
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

        val defaultMode = when {
            metadata.isNumeric || metadata.isPhoneNumber || metadata.isPassword -> CoverKeyboardMode.NUMERIC_PIN
            else -> CoverKeyboardMode.T9_MULTITAP
        }

        _sessionState.value = CoverInputSessionState(
            isActive = true,
            buffer = metadata.initialText,
            cursorPosition = metadata.initialText.length,
            keyboardMode = defaultMode,
            metadata = metadata,
            lastStatusMessage = "Attached to ${cleanAppLabel(metadata.packageName)}"
        )
        textBuffer = TextBuffer(
            text = metadata.initialText,
            selection = metadata.initialText.length..metadata.initialText.length,
            composing = null
        )

        overlayManager?.showOverlay()
    }

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

    fun appendText(text: String) {
        applyDelta(TextDelta.Insert(text))
    }

    fun replacePreviousChar(char: Char) {
        applyDelta(TextDelta.ReplacePreviousChar(char))
    }

    fun deleteBackward() {
        applyDelta(TextDelta.Backspace())
    }

    fun clearBuffer() {
        applyDelta(TextDelta.Clear)
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

            performDeltaInjection(before.text, after.text, newCaret)
        }
    }

    fun switchMode(mode: CoverKeyboardMode) {
        _sessionState.value = _sessionState.value.copy(keyboardMode = mode)
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

    fun commitAndFinish() {
        val state = _sessionState.value
        // A commit is the final delta of the session: it ensures the target
        // is in sync with the overlay's buffer even if some intermediate
        // deltas failed (e.g. a transient InputConnection unavailability).
        injectionLock.withLock {
            performDeltaInjection(
                oldText = lastInjectedText ?: state.buffer,
                newText = state.buffer,
                newCaret = state.cursorPosition
            )
        }
        injectionTarget?.dispatchDone() ?: activeAccessibilityService?.dispatchActionDone()

        _sessionState.value = state.copy(
            lastStatusMessage = "Injected successfully",
            isSuccessFeedback = true
        )

        Handler(Looper.getMainLooper()).postDelayed({
            dismissOverlay(reason = "Input finished")
        }, 250L)
    }

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

    fun isRecentSelfEcho(text: String): Boolean {
        val elapsed = SystemClock.elapsedRealtime() - lastInjectionTimestamp
        return elapsed < INJECTION_ECHO_IGNORE_WINDOW_MS && text == lastInjectedText
    }

    private fun performDeltaInjection(oldText: String, newText: String, newCaret: Int) {
        lastInjectedText = newText
        lastInjectionTimestamp = SystemClock.elapsedRealtime()

        val target = injectionTarget
        if (target == null) {
            Log.w(TAG, "Cannot inject text: injection target not bound")
            return
        }

        val delta = BufferDelta.compute(old = oldText, new = newText, newCaret = newCaret)
        if (delta.isNoOp) {
            // Caret-only movement, or no real content change. No injection required.
            return
        }
        val method = target.applyDelta(delta)
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
    }

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

class CoverInputAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var latestForegroundPackage: String? = null

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

        fun currentForegroundPackage(): String? = latestForegroundPackage

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
    private val focusLossHandler = Handler(Looper.getMainLooper())
    private var pendingFocusLossCheck: Runnable? = null

    /**
     * Defers a dismissal until we can confirm, across every window on every display, that
     * no editable field holds input focus any more.
     *
     * Any subsequent focus event cancels the pending check, so ordinary window churn inside
     * the target app (dialogs, ripples, keyboard-driven relayouts) no longer yanks the cover
     * keyboard away mid-session.
     */
    fun scheduleFocusLossVerification(reason: String) {
        cancelPendingFocusLossVerification()
        val check = Runnable {
            pendingFocusLossCheck = null
            if (hasEditableFocusOutsideOwnPackage()) {
                Log.d(TAG, "Focus-loss check ('$reason') cancelled: editable field still focused")
                return@Runnable
            }
            CoverInputSessionManager.onFieldLostFocus(reason)
        }
        pendingFocusLossCheck = check
        focusLossHandler.postDelayed(check, FOCUS_LOSS_CONFIRM_DELAY_MS)
    }

    fun cancelPendingFocusLossVerification() {
        pendingFocusLossCheck?.let { focusLossHandler.removeCallbacks(it) }
        pendingFocusLossCheck = null
    }

    /** True when any window not owned by us still reports an editable input-focused node. */
    private fun hasEditableFocusOutsideOwnPackage(): Boolean {
        val ownPackage = this.packageName
        val windowList = runCatching { windows }.getOrNull().orEmpty()
        for (window in windowList) {
            val root = runCatching { window.root }.getOrNull() ?: continue
            if (root.packageName?.toString() == ownPackage) continue
            val focused = runCatching {
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            }.getOrNull()
            if (focused != null && focused.isEditable) return true
        }

        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() == ownPackage) return false
        val focused = runCatching {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }.getOrNull()
        return focused != null && focused.isEditable
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "CoverInputAccessibilityService connected")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

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

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Ignore window-state events that originate from our own package
                // (the cover launcher overlay, the cover keyboard overlay, and
                // any other TYPE_APPLICATION_OVERLAY we own). Otherwise adding
                // the keyboard triggers WINDOW_STATE_CHANGED -> "no editable
                // focus in our overlay" -> onFieldLostFocus -> hideOverlay, a
                // self-inflicted feedback loop that flashes the keyboard for
                // ~300 ms and then dismisses it. See CoverInputInjection log:
                // "Dismissing cover overlay: Field lost focus".
                if (packageName == this.packageName) return
                // Also swallow the immediate post-focus tail so any third-party
                // window churn (dialog/toast/ripple) inside the target app cannot
                // yank the keyboard away in the first few hundred ms.
                if (fieldFocusClaimedAtMs != 0L &&
                    SystemClock.elapsedRealtime() - fieldFocusClaimedAtMs < FIELD_FOCUS_GRACE_MS
                ) {
                    return
                }
                val rootNode = rootInActiveWindow
                val currentFocus = rootNode?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (currentFocus == null || !currentFocus.isEditable) {
                    // Do NOT dismiss straight away. `rootInActiveWindow` is unreliable on
                    // multi-display devices: during transient window churn it frequently
                    // resolves to a window that legitimately has no editable focus (a
                    // dialog, a system window, or the other display's window) while the
                    // user's field is still very much focused. Confirm across *all*
                    // windows after a short delay instead.
                    scheduleFocusLossVerification("window state changed")
                }
            }

            else -> Unit // Other AccessibilityEvent types are not consumed by this service.
        }
    }

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

    fun suppressHoneyBoardSoftKeyboard() {
        softKeyboardController.showMode = SHOW_MODE_HIDDEN
    }

    fun restoreSoftKeyboardMode() {
        softKeyboardController.showMode = SHOW_MODE_AUTO
    }

    fun injectTextIntoFocusedNode(text: String): Boolean {
        val node = resolveTargetInputNode() ?: run {
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
     *     `setSelection` + `deleteSurroundingText` + `commitText` of the
     *     delta only. Zero full-buffer traffic when the target and overlay
     *     agree.
     *  2. **ACTION_SET_TEXT** (`ACTION_SET_TEXT`): virtual-hierarchy fallback.
     *     Reads `getTextSelectionStart/End` from the cached node to snap the
     *     caret to the delta position, then writes the full new buffer
     *     (accessibility offers no delta primitive) via
     *     [injectTextIntoFocusedNode]. This is still the "flicker" path, but
     *     it only fires when InputConnection is unavailable — no worse than
     *     today, and the caret is at least kept in sync.
     *  3. **Clipboard paste** (`ACTION_PASTE_CLIPBOARD`): last resort,
     *     unchanged.
     *
     * Callers must hold [CoverInputSessionManager.injectionLock].
     */
    internal fun applyDeltaToFocusedField(delta: BufferDelta): InjectionMethod {
        if (delta.isNoOp) return InjectionMethod.NONE

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
     * using `setSelection` + `deleteSurroundingText` + `commitText` on the
     * delta substring only.
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
                // we just selected [start, end], delete the selection by
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
        if (injectViaInputConnection(text)) return InjectionMethod.IME_INPUT_CONNECTION
        if (injectTextIntoFocusedNode(text)) return InjectionMethod.ACTION_SET_TEXT
        if (injectViaClipboard(text)) return InjectionMethod.ACTION_PASTE_CLIPBOARD
        return InjectionMethod.NONE
    }

    fun injectViaClipboard(text: String): Boolean {
        val node = resolveTargetInputNode() ?: return false
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

    private fun describeNodeActions(node: AccessibilityNodeInfo): String =
        runCatching {
            node.actionList.joinToString(",") { action ->
                when (action.id) {
                    AccessibilityNodeInfo.ACTION_SET_TEXT -> "SET_TEXT"
                    AccessibilityNodeInfo.ACTION_PASTE -> "PASTE"
                    AccessibilityNodeInfo.ACTION_FOCUS -> "FOCUS"
                    AccessibilityNodeInfo.ACTION_CLICK -> "CLICK"
                    AccessibilityNodeInfo.ACTION_SET_SELECTION -> "SET_SELECTION"
                    else -> "0x${Integer.toHexString(action.id)}"
                }
            }.ifEmpty { "<none>" }
        }.getOrDefault("<unavailable>")


    /**
     * Locates the editable target field the user tapped on. On multi-display Samsung
     * devices, once our TYPE_APPLICATION_OVERLAY keyboard is attached on display 1,
     * `rootInActiveWindow` frequently resolves to our own compose overlay instead of
     * the underlying app window, causing ACTION_SET_TEXT to silently fail. To work
     * around that we:
     *  1. Trust the cached node captured at focus time if it still refreshes and is editable.
     *  2. Walk every interactive window (FLAG_RETRIEVE_INTERACTIVE_WINDOWS) across all
     *     displays, skipping any window owned by our own package, and pick the first
     *     editable focused node (or any editable node if focus was stolen).
     *  3. Fall back to `rootInActiveWindow` as a last resort.
     */
    private fun resolveTargetInputNode(): AccessibilityNodeInfo? {
        val cached = targetFocusedNode
        if (cached != null &&
            runCatching { cached.refresh() }.getOrDefault(false) &&
            cached.isEditable
        ) {
            return cached
        }

        val ownPackage = this.packageName
        val windowList = runCatching { windows }.getOrNull().orEmpty()
        for (window in windowList) {
            val root = runCatching { window.root }.getOrNull() ?: continue
            val pkg = root.packageName?.toString()
            if (pkg == ownPackage) continue

            val focused = runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
            if (focused != null && focused.isEditable) {
                targetFocusedNode?.recycle()
                targetFocusedNode = AccessibilityNodeInfo.obtain(focused)
                return targetFocusedNode
            }
            val editable = findEditableNode(root)
            if (editable != null) {
                targetFocusedNode?.recycle()
                targetFocusedNode = AccessibilityNodeInfo.obtain(editable)
                return targetFocusedNode
            }
        }

        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() == ownPackage) return null
        val fallbackFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (fallbackFocus != null && fallbackFocus.isEditable) {
            targetFocusedNode?.recycle()
            targetFocusedNode = AccessibilityNodeInfo.obtain(fallbackFocus)
            return targetFocusedNode
        }
        return null
    }

    fun dispatchActionDone() {
        // Prefer the editor action carried by the input connection: it triggers the field's
        // real "done/search/next" handler instead of simulating a tap on the node.
        val connection = runCatching { inputMethod?.currentInputConnection }.getOrNull()
        if (connection != null) {
            val editorAction = runCatching {
                inputMethod?.currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            }.getOrNull() ?: EditorInfo.IME_ACTION_DONE
            val action = if (editorAction == EditorInfo.IME_ACTION_NONE ||
                editorAction == EditorInfo.IME_ACTION_UNSPECIFIED
            ) {
                EditorInfo.IME_ACTION_DONE
            } else {
                editorAction
            }
            if (runCatching { connection.performEditorAction(action) }.isSuccess) {
                Log.d(TAG, "dispatchActionDone via input connection action=$action")
                return
            }
        }

        val node = resolveTargetInputNode() ?: return
        val clickPerformed = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (!clickPerformed) {
            node.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
        }
    }

    private fun isCoverScreenEvent(event: AccessibilityEvent): Boolean {
        return event.displayId == COVER_DISPLAY_ID
    }

    private fun handleFocusOrClickEvent(event: AccessibilityEvent) {
        val source = event.source ?: run {
            val root = rootInActiveWindow
            root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } ?: return

        val editableNode = findEditableNode(source)
        if (editableNode != null && editableNode.isEditable) {
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

            val metadata = CoverFieldMetadata.fromNode(editableNode)
            CoverInputSessionManager.onFieldFocused(metadata)
        }
    }

    private fun handleTextChangedEvent(event: AccessibilityEvent) {
        val eventText = event.text?.joinToString("") ?: return
        if (CoverInputSessionManager.isRecentSelfEcho(eventText)) return
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) return found
        }
        return null
    }

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

    private fun processForegroundTrackingAndReclaim(event: AccessibilityEvent, foregroundPackage: String) {
        val isWindowEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (!isWindowEvent) return

        // Never let window events emitted by our own TYPE_APPLICATION_OVERLAY
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

        val nowElapsedMs = SystemClock.elapsedRealtime()
        val isSameForegroundPackage = latestForegroundPackage == foregroundPackage
        val shouldRefreshForegroundTimestamp = !isSameForegroundPackage ||
            (nowElapsedMs - latestForegroundEventElapsedMs) >= FOREGROUND_EVENT_REFRESH_MIN_INTERVAL_MS
        if (shouldRefreshForegroundTimestamp) {
            latestForegroundPackage = foregroundPackage
            latestForegroundEventElapsedMs = nowElapsedMs
        }

        if (shouldTrackAsUserForegroundApp(foregroundPackage)) {
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

        if (!shouldRequestOverlayReclaim(foregroundPackage)) {
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
        if (lastKnownUserAppPackage != null && userAppAgeMs < RECENT_USER_APP_GUARD_MS) {
            return
        }

        if (Log.isLoggable(OVERLAY_RECLAIM_LOG_TAG, Log.DEBUG)) {
            Log.d(OVERLAY_RECLAIM_LOG_TAG, "trigger_reclaim package=$foregroundPackage stableForMs=$stableForMs")
        }
        ForegroundService.requestOverlayReclaim(reason = foregroundPackage)
    }

    private fun shouldDebounceGesture(gestureId: Int): Boolean {
        val now = SystemClock.elapsedRealtime()
        val isRepeated = lastGestureId == gestureId && (now - lastGestureAtMs) < GESTURE_DEBOUNCE_MS
        lastGestureId = gestureId
        lastGestureAtMs = now
        return isRepeated
    }

    private fun shouldThrottleGlobalAction(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val throttled = (now - lastActionAtMs) < ACTION_THROTTLE_MS
        if (!throttled) {
            lastActionAtMs = now
        }
        return throttled
    }

    private fun shouldRequestOverlayReclaim(packageName: String): Boolean {
        return OVERLAY_RECLAIM_PACKAGE_PREFIXES.any { prefix -> packageName.startsWith(prefix) }
    }

    private fun shouldTrackAsUserForegroundApp(packageName: String): Boolean {
        return NON_USER_APP_PREFIXES.none { prefix -> packageName.startsWith(prefix) }
    }

    private fun resetReclaimCandidate() {
        reclaimCandidatePackage = null
        reclaimCandidateSinceElapsedMs = 0L
    }

    private fun shouldAllowIncomingCallSurface(packageName: String): Boolean {
        return CallPackageMatchers.isIncomingCallPackage(packageName)
    }
}

class CoverKeyboardOverlayManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayWindowContext: Context? = null
    private var overlayComposeView: ComposeView? = null
    private var isOverlayAttached = false

    private fun resolveCoverWindowContext(): Context {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val coverDisplay = displayManager.displays.firstOrNull { it.displayId == COVER_DISPLAY_ID }
            ?: displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)

        Log.d(
            TAG,
            "Resolving cover keyboard overlay display: id=${coverDisplay?.displayId} name=${coverDisplay?.name}"
        )

        return if (coverDisplay != null) {
            val displayContext = context.createDisplayContext(coverDisplay)
            runCatching {
                displayContext.createWindowContext(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    Bundle()
                )
            }.getOrElse {
                Log.w(TAG, "createWindowContext failed for cover display; using display context: ${it.message}")
                displayContext
            }
        } else {
            context
        }
    }

    fun showOverlay() {
        if (isOverlayAttached) return

        // Re-resolve the cover-display window context on every show so the overlay
        // follows the currently active cover display (the accessibility service is
        // bound once, but display availability may change over time).
        val coverContext = resolveCoverWindowContext()
        overlayWindowContext = coverContext
        val wm = coverContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val view = createComposeOverlayView(coverContext)
        overlayComposeView = view

        val layoutParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            windowAnimations = R.style.Animation_InputMethod
        }

        runCatching {
            wm.addView(view, layoutParams)
            isOverlayAttached = true
            Log.d(
                TAG,
                "Cover keyboard overlay attached to display=${coverContext.display?.displayId}"
            )
        }.onFailure { error ->
            Log.e(TAG, "Failed to attach cover keyboard overlay", error)
            overlayComposeView = null
            overlayWindowContext = null
            windowManager = null
        }
    }

    fun isAttached(): Boolean = isOverlayAttached

    fun hideOverlay() {
        if (!isOverlayAttached) return
        val wm = windowManager ?: return
        val view = overlayComposeView ?: return

        runCatching {
            wm.removeView(view)
        }.onFailure { error ->
            Log.e(TAG, "Error removing cover keyboard overlay", error)
        }
        isOverlayAttached = false
        overlayComposeView = null
        overlayWindowContext = null
        windowManager = null
    }

    fun destroy() {
        hideOverlay()
        windowManager = null
        overlayWindowContext = null
    }

    private fun createComposeOverlayView(viewContext: Context): ComposeView {
        val composeView = ComposeView(viewContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        }

        val lifecycleOwner = StandaloneOverlayLifecycleOwner()
        lifecycleOwner.performRestore(null)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        composeView.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CoverKeyboardOverlayRoot(
                    onDismiss = { CoverInputSessionManager.dismissOverlay("User tapped close") }
                )
            }
        }

        composeView.setOnTouchListener { view: View, event: MotionEvent ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                view.performClick()
                CoverInputSessionManager.onOutsideTouch()
                true
            } else {
                false
            }
        }

        return composeView
    }
}

private class StandaloneOverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        field = LifecycleRegistry(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    init {
        // performAttach() reads owner.lifecycle internally; the property accessors above
        // must be resolvable before this runs, otherwise SavedStateRegistryImpl throws NPE.
        savedStateRegistryController.performAttach()
    }


    fun performRestore(savedState: Bundle?) {
        savedStateRegistryController.performRestore(savedState)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycle.handleLifecycleEvent(event)
    }
}

@Composable
fun CoverKeyboardOverlayRoot(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessionState by CoverInputSessionManager.sessionState.collectAsState()
    val context = LocalContext.current

    val triggerHaptic = remember(context) {
        {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator.vibrate(
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(20L)
                }
            }
        }
    }

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
                    triggerHaptic()
                    CoverInputSessionManager.switchMode(mode)
                },
                onUseSystemKeyboard = {
                    triggerHaptic()
                   // CoverInputSessionManager.relinquishToSystemKeyboard()
                },
                onDismiss = onDismiss
            )

            CoverLiveBufferDisplay(
                buffer = sessionState.buffer,
                isPassword = sessionState.metadata.isPassword,
                hint = sessionState.metadata.hintText
            )

            when (sessionState.keyboardMode) {
                CoverKeyboardMode.NUMERIC_PIN -> {
                    CoverNumericPinKeypad(
                        onDigit = { digit ->
                            triggerHaptic()
                            CoverInputSessionManager.appendText(digit.toString())
                        },
                        onBackspace = {
                            triggerHaptic()
                            CoverInputSessionManager.deleteBackward()
                        },
                        onClear = {
                            triggerHaptic()
                            CoverInputSessionManager.clearBuffer()
                        },
                        onDone = {
                            triggerHaptic()
                            CoverInputSessionManager.commitAndFinish()
                        }
                    )
                }

                CoverKeyboardMode.T9_MULTITAP -> {
                    CoverT9MultiTapKeypad(
                        onAppend = { char ->
                            triggerHaptic()
                            CoverInputSessionManager.appendText(char.toString())
                        },
                        onReplace = { char ->
                            triggerHaptic()
                            CoverInputSessionManager.replacePreviousChar(char)
                        },
                        onBackspace = {
                            triggerHaptic()
                            CoverInputSessionManager.deleteBackward()
                        },
                        onClear = {
                            triggerHaptic()
                            CoverInputSessionManager.clearBuffer()
                        },
                        onDone = {
                            triggerHaptic()
                            CoverInputSessionManager.commitAndFinish()
                        }
                    )
                }

                CoverKeyboardMode.QWERTY -> {
                    CoverCompactQwertyKeyboard(
                        onChar = { char ->
                            triggerHaptic()
                            CoverInputSessionManager.appendText(char.toString())
                        },
                        onBackspace = {
                            triggerHaptic()
                            CoverInputSessionManager.deleteBackward()
                        },
                        onDone = {
                            triggerHaptic()
                            CoverInputSessionManager.commitAndFinish()
                        },
                        onClear ={
                            triggerHaptic()
                            CoverInputSessionManager.clearBuffer()
                        },
                        modifier = Modifier
                    )
                }
            }
        }
    }
}

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
          /*  ModeChip(
                label = "T9",
                selected = activeMode == CoverKeyboardMode.T9_MULTITAP,
                onClick = { onModeSelected(CoverKeyboardMode.T9_MULTITAP) }
            ) */
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
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1D1D21))
            .border(1.dp, Color(0xFF2C2C32), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = displayString,
                color = if (buffer.isEmpty()) Color(0xFF757580) else Color.White,
                fontSize = if (isPassword) 18.sp else 14.sp,
                fontFamily = if (isPassword) FontFamily.Monospace else FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (buffer.isNotEmpty()) {
                Text(
                    text = "${buffer.length} chars",
                    color = Color(0xFF6B7280),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun CoverNumericPinKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9')
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { digit ->
                    KeypadButton(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        onClick = { onDigit(digit) }
                    ) {
                        Text(
                            text = digit.toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeypadButton(
                modifier = Modifier
                    .weight(0.85f)
                    .height(44.dp),
                backgroundColor = Color(0xFF2A2A30),
                onClick = onClear
            ) {
                Text(
                    text = "CLR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF7043)
                )
            }

            KeypadButton(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                onClick = { onDigit('0') }
            ) {
                Text(
                    text = "0",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            KeypadButton(
                modifier = Modifier
                    .weight(0.85f)
                    .height(44.dp),
                backgroundColor = Color(0xFF2A2A30),
                onClick = onBackspace,
                repeating = true
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            KeypadButton(
                modifier = Modifier
                    .weight(1.1f)
                    .height(44.dp),
                backgroundColor = Color(0xFF10B981),
                onClick = onDone
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Submit",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "DONE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private data class T9KeyInfo(val digit: Char, val letters: String)

private val T9_KEYS = listOf(
    listOf(T9KeyInfo('1', ".,!"), T9KeyInfo('2', "ABC"), T9KeyInfo('3', "DEF")),
    listOf(T9KeyInfo('4', "GHI"), T9KeyInfo('5', "JKL"), T9KeyInfo('6', "MNO")),
    listOf(T9KeyInfo('7', "PQRS"), T9KeyInfo('8', "TUV"), T9KeyInfo('9', "WXYZ"))
)

@Composable
private fun CoverT9MultiTapKeypad(
    onAppend: (Char) -> Unit,
    onReplace: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit
) {
    var lastTapDigit by remember { mutableStateOf<Char?>(null) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var cycleIndex by remember { mutableStateOf(0) }

    val handleKeyTap: (T9KeyInfo) -> Unit = { key ->
        val now = SystemClock.elapsedRealtime()
        val letters = key.letters.lowercase()

        if (lastTapDigit == key.digit && (now - lastTapTime) < MULTI_TAP_CYCLE_TIMEOUT_MS && letters.isNotEmpty()) {
            val nextIndex = (cycleIndex + 1) % letters.length
            cycleIndex = nextIndex
            lastTapTime = now
            onReplace(letters[nextIndex])
        } else {
            lastTapDigit = key.digit
            lastTapTime = now
            cycleIndex = 0
            val initialChar = if (letters.isNotEmpty()) letters.first() else key.digit
            onAppend(initialChar)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        T9_KEYS.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { keyInfo ->
                    KeypadButton(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        onClick = { handleKeyTap(keyInfo) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = keyInfo.digit.toString(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (keyInfo.letters.isNotEmpty()) {
                                Text(
                                    text = keyInfo.letters,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFA0A0A8)
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeypadButton(
                modifier = Modifier
                    .weight(0.75f)
                    .height(42.dp),
                backgroundColor = Color(0xFF2A2A30),
                onClick = onClear
            ) {
                Text("CLR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF7043))
            }

            KeypadButton(
                modifier = Modifier
                    .weight(1.3f)
                    .height(42.dp),
                backgroundColor = Color(0xFF24242A),
                onClick = {
                    lastTapDigit = null
                    onAppend(' ')
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SpaceBar,
                        contentDescription = "Space",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("SPACE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            KeypadButton(
                modifier = Modifier
                    .weight(0.85f)
                    .height(42.dp),
                backgroundColor = Color(0xFF2A2A30),
                onClick = {
                    lastTapDigit = null
                    onBackspace()
                },
                repeating = true
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            KeypadButton(
                modifier = Modifier
                    .weight(1.1f)
                    .height(42.dp),
                backgroundColor = Color(0xFF10B981),
                onClick = onDone
            ) {
                Text("ENTER", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}


@Composable
private fun KeypadButton(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF1F1F24),
    onClick: () -> Unit,
    repeating: Boolean = false,
    repeatInitialDelayMillis: Long = 400L,
    repeatIntervalMillis: Long = 55L,
    content: @Composable () -> Unit
) {
    if (!repeating) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
        return
    }

    // Hold-to-repeat path: fires `onClick` once on down, then every
    // [repeatIntervalMillis] after an initial [repeatInitialDelayMillis]
    // delay, until the finger releases. Matches system-IME backspace
    // behaviour (Samsung Keyboard uses ~50-60 ms cadence after ~400 ms
    // hold).
    val callback = androidx.compose.runtime.rememberUpdatedState(onClick)
    var isPressed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(repeatInitialDelayMillis, repeatIntervalMillis) {
        androidx.compose.runtime.snapshotFlow { isPressed }
            .collect { pressed ->
                if (!pressed) return@collect
                kotlinx.coroutines.delay(repeatInitialDelayMillis)
                while (isPressed) {
                    callback.value.invoke()
                    kotlinx.coroutines.delay(repeatIntervalMillis)
                }
            }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    // Fire the first click immediately on down so a quick tap
                    // still deletes one character before the repeat cadence
                    // kicks in.
                    callback.value.invoke()
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { change -> change.pressed }) {
                            isPressed = false
                            break
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

