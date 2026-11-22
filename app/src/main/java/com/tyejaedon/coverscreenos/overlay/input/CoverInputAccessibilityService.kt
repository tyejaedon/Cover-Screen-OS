package com.tyejaedon.coverscreenos.overlay.input

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import com.tyejaedon.coverscreenos.helpers.CoverDisplayHelper
import com.tyejaedon.coverscreenos.keyboard.nlp.CoverKeyboardNlpManager
import com.tyejaedon.coverscreenos.notifications.CallPackageMatchers
import com.tyejaedon.coverscreenos.overlay.CoverHomeSurfaceMatcher
import com.tyejaedon.coverscreenos.overlay.ForegroundService


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
    /** Most recent package observed via `TYPE_WINDOW_STATE_CHANGED` on display 1. */
    private var lastWindowPackage: String? = null
    /** Last accessibility swipe gesture ID seen, used with [lastGestureAtMs] to drop duplicate triggers. */
    private var lastGestureId: Int? = null
    /** `SystemClock.elapsedRealtime()` timestamp of [lastGestureId]. */
    private var lastGestureAtMs: Long = 0L
    /** Timestamp of the most recent global Back/Home/Recents/Notifications action to throttle bursts. */
    private var lastActionAtMs: Long = 0L
    /** Candidate stock-launcher/cover-home package being observed for overlay reclaim stability. */
    private var reclaimCandidatePackage: String? = null
    /** Elapsed-realtime ms when [reclaimCandidatePackage] first became the foreground candidate. */
    private var reclaimCandidateSinceElapsedMs: Long = 0L
    /** Last real third-party user app observed in the foreground on the cover screen. */
    private var lastKnownUserAppPackage: String? = null
    /** Elapsed-realtime ms when [lastKnownUserAppPackage] was last observed in the foreground. */
    private var lastKnownUserAppElapsedMs: Long = 0L
    // Guard window used to swallow the WINDOW_STATE_CHANGED event that our own
    // cover keyboard overlay emits immediately after WindowManager#addView.
    // Without this guard, showing the keyboard would trigger a "lost focus"
    // dismissal ~300 ms later because rootInActiveWindow briefly resolves to
    // the newly-attached overlay (which has no editable focused node).
    private var fieldFocusClaimedAtMs: Long = 0L
    /** Main-looper handler driving the [FOCUS_LOSS_POLL_MS] verification checks. */
    private val focusLossHandler = Handler(Looper.getMainLooper())
    /** Currently scheduled polling runnable checking whether the focused editor disappeared. */
    private var pendingFocusLossCheck: Runnable? = null
    /** Hysteresis gate confirming true focus loss across transient window/content churn. */
    private val focusLossGate = FocusLossGate()

    /** Helper resolving the secondary cover hardware display via [DisplayManager]. */
    private val displayHelper by lazy { CoverDisplayHelper(this) }
    /** Cached cover display ID invalidated via [displayListener] whenever displays change. */
    @Volatile
    private var cachedCoverDisplayId: Int? = null
    /** Registered state flag so display listener teardown in [onDestroy] is idempotent. */
    private var isDisplayListenerRegistered = false
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            cachedCoverDisplayId = null
        }

        override fun onDisplayRemoved(displayId: Int) {
            cachedCoverDisplayId = null
        }

        override fun onDisplayChanged(displayId: Int) {
            if (displayId == cachedCoverDisplayId || cachedCoverDisplayId == null) {
                cachedCoverDisplayId = null
            }
        }
    }

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
        val displayManager = getSystemService(DISPLAY_SERVICE) as? DisplayManager
        if (displayManager != null && !isDisplayListenerRegistered) {
            runCatching {
                displayManager.registerDisplayListener(displayListener, focusLossHandler)
                isDisplayListenerRegistered = true
            }
        }
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

    /** Unbinds [CoverInputSessionManager], unregisters listeners, cancels timers, and recycles cached nodes. */
    override fun onDestroy() {
        super.onDestroy()
        cancelPendingFocusLossVerification()
        focusLossHandler.removeCallbacksAndMessages(null)
        if (isDisplayListenerRegistered) {
            val displayManager = getSystemService(DISPLAY_SERVICE) as? DisplayManager
            runCatching { displayManager?.unregisterDisplayListener(displayListener) }
            isDisplayListenerRegistered = false
        }
        cachedCoverDisplayId = null
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
        if (injectDeltaViaClipboard(delta)) return InjectionMethod.ACTION_PASTE_CLIPBOARD
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
        if (injectViaClipboard(text, replaceExistingAll = true)) return InjectionMethod.ACTION_PASTE_CLIPBOARD
        return InjectionMethod.NONE
    }

    /**
     * Non-destructive Tier-3 delta injection via [ClipboardManager] +
     * [AccessibilityNodeInfo.ACTION_PASTE].
     *
     * Selects `[delta.replaceStart, delta.replaceEnd]` on the resolved target node before pasting
     * `delta.insert`, snapshots any pre-existing clipboard entry beforehand, and restores it after
     * [CLIPBOARD_RESTORE_DELAY_MS] so the user's primary clipboard is never permanently overwritten.
     */
    internal fun injectDeltaViaClipboard(
        delta: BufferDelta,
        targetNode: AccessibilityNodeInfo? = resolveTargetInputNode(),
        clipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    ): Boolean {
        if (delta.isNoOp) return false
        if (CoverInputSessionManager.sessionState.value.metadata.isPassword) {
            Log.w(TAG, "Clipboard fallback refused for secure field")
            return false
        }
        // Pure backspace/clear edits cannot paste an empty substring at a range reliably across
        // all Android editors; fall back to full-selection replacement if possible.
        if (delta.insert.isEmpty()) {
            return injectViaClipboard(
                text = delta.new,
                replaceExistingAll = true,
                targetNode = targetNode,
                clipboard = clipboard
            )
        }
        val node = targetNode ?: return false
        if (CoverFieldMetadata.fromNode(node).isPassword) {
            Log.w(TAG, "Clipboard fallback refused for secure target node")
            return false
        }
        val supportsPaste = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_PASTE }
        if (!supportsPaste) {
            Log.w(
                TAG,
                "injectDeltaViaClipboard: node does not advertise ACTION_PASTE " +
                    "pkg=${node.packageName} actions=${describeNodeActions(node)}"
            )
            return false
        }

        val currentLen = if (!node.isShowingHintText) node.text?.length else null
        val start = delta.replaceStart.coerceAtLeast(0).let { s ->
            if (currentLen != null) s.coerceAtMost(currentLen) else s
        }
        val end = delta.replaceEnd.coerceAtLeast(start).let { e ->
            if (currentLen != null) e.coerceAtMost(currentLen) else e
        }
        runCatching {
            val selArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        }

        val previousClip = runCatching { clipboard.primaryClip }.getOrNull()
        clipboard.setPrimaryClip(ClipData.newPlainText(INTERNAL_CLIP_LABEL, delta.insert))
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        if (ok) {
            scheduleClipboardRestore(clipboard, previousClip)
        } else {
            restoreClipboardIfInternal(clipboard, previousClip)
        }
        Log.d(TAG, "injectDeltaViaClipboard result=$ok insertLen=${delta.insert.length} pkg=${node.packageName}")
        return ok
    }

    /**
     * Last-resort text injection fallback using [ClipboardManager] and
     * [AccessibilityNodeInfo.ACTION_PASTE].
     *
     * Refuses password/secure fields (both via session metadata and a fresh [CoverFieldMetadata]
     * check on the resolved node) so sensitive input is never written to the shared system
     * clipboard. Snapshots and restores the prior [ClipData] after pasting so the user's primary
     * clipboard is preserved.
     */
    fun injectViaClipboard(
        text: String,
        replaceExistingAll: Boolean = false,
        targetNode: AccessibilityNodeInfo? = resolveTargetInputNode(),
        clipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    ): Boolean {
        if (CoverInputSessionManager.sessionState.value.metadata.isPassword) {
            Log.w(TAG, "Clipboard fallback refused for secure field")
            return false
        }
        val node = targetNode ?: return false
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
        if (replaceExistingAll && !node.isShowingHintText) {
            val existingLen = node.text?.length ?: 0
            if (existingLen > 0) {
                runCatching {
                    val selArgs = Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, existingLen)
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
                }
            }
        }
        val previousClip = runCatching { clipboard.primaryClip }.getOrNull()
        clipboard.setPrimaryClip(ClipData.newPlainText(INTERNAL_CLIP_LABEL, text))
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        if (ok) {
            scheduleClipboardRestore(clipboard, previousClip)
        } else {
            restoreClipboardIfInternal(clipboard, previousClip)
        }
        Log.d(TAG, "injectViaClipboard result=$ok len=${text.length} pkg=${node.packageName}")
        return ok
    }

    /** Schedules restoration of [previousClip] on the main looper after [CLIPBOARD_RESTORE_DELAY_MS]. */
    private fun scheduleClipboardRestore(clipboard: ClipboardManager, previousClip: ClipData?) {
        focusLossHandler.postDelayed({
            restoreClipboardIfInternal(clipboard, previousClip)
        }, CLIPBOARD_RESTORE_DELAY_MS)
    }

    /**
     * Restores [previousClip] (or clears primary clip when null) only if the current system
     * clipboard still holds our temporary [INTERNAL_CLIP_LABEL] payload.
     */
    internal fun restoreClipboardIfInternal(clipboard: ClipboardManager, previousClip: ClipData?) {
        runCatching {
            val currentLabel = clipboard.primaryClipDescription?.label?.toString()
            if (currentLabel == INTERNAL_CLIP_LABEL) {
                if (previousClip != null) {
                    clipboard.setPrimaryClip(previousClip)
                } else {
                    clipboard.clearPrimaryClip()
                }
            }
        }
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

    /** Resolves the secondary cover display ID via [CoverDisplayHelper], falling back to [COVER_DISPLAY_ID]. */
    private fun resolveCoverDisplayId(): Int {
        cachedCoverDisplayId?.let { return it }
        return displayHelper.getCoverDisplayId()
            ?.also { cachedCoverDisplayId = it }
            ?: COVER_DISPLAY_ID
    }

    /** Returns `true` only for events originating on the outer cover display ([resolveCoverDisplayId]). */
    private fun isCoverScreenEvent(event: AccessibilityEvent): Boolean {
        return event.displayId == resolveCoverDisplayId()
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
