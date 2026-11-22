package com.tyejaedon.coverscreenos.ui.controllers

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.FragmentActivity
import com.tyejaedon.coverscreenos.overlay.ForegroundService
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Transparent bridge [FragmentActivity] responsible for unlocking a secure keyguard from the
 * cover screen before resuming a deferred app launch.
 *
 * Uses [setShowWhenLocked] and [setTurnScreenOn] to appear over the lockscreen on the cover
 * display, authenticates the user via [BiometricPrompt] (`BIOMETRIC_STRONG` or `DEVICE_CREDENTIAL`),
 * explicitly requests keyguard dismissal with [KeyguardManager.requestDismissKeyguard], and then
 * dispatches the pending app using this Activity's foreground launch privilege before tearing down
 * via [finishAndRemoveTask].
 */
class UnlockBridgeActivity : FragmentActivity() {

    /**
     * Launch payload carried in the bridge [Intent].
     *
     * [packageName] is null for an unlock-only request (lock icon tap).
     */
    private data class UnlockLaunchRequest(
        val packageName: String?,
        val displayId: Int?,
        val requestId: String,
        val requestedAtElapsedMs: Long
    )

    companion object {
        private const val LOG_TAG = "UnlockBridgeActivity"

        // Intent extra keys passing the target app, display ID, unique trace ID, and request timestamp.
        private const val EXTRA_PACKAGE_NAME = "com.tyejaedon.coverscreenos.extra.UNLOCK_PACKAGE_NAME"
        private const val EXTRA_DISPLAY_ID = "com.tyejaedon.coverscreenos.extra.UNLOCK_DISPLAY_ID"
        private const val EXTRA_REQUEST_ID = "com.tyejaedon.coverscreenos.extra.UNLOCK_REQUEST_ID"
        private const val EXTRA_REQUEST_ELAPSED_MS = "com.tyejaedon.coverscreenos.extra.UNLOCK_REQUEST_ELAPSED_MS"

        // Requests older than 15s are treated as abandoned so stale back-stack intents never launch old apps later.
        private const val REQUEST_STALE_TIMEOUT_MS = 15_000L

        // Window (600ms) and delay (180ms) to auto-retry once if Samsung's keyguard transition cancels BiometricPrompt on startup.
        private const val EARLY_BIOMETRIC_CANCEL_RETRY_WINDOW_MS = 600L
        private const val BIOMETRIC_RETRY_DELAY_MS = 180L

        // User-facing prompt copy shown during app-launch unlocks vs direct lock-icon unlocks.
        private const val UNLOCK_PROMPT_TITLE = "Unlock to continue"
        private const val UNLOCK_PROMPT_SUBTITLE = "Authenticate with your device security to open the app"
        private const val UNLOCK_ONLY_PROMPT_TITLE = "Unlock your phone"
        private const val UNLOCK_ONLY_PROMPT_SUBTITLE = "Authenticate with your device security"

        // Cross-thread deduplication flag so double-taps don't stack duplicate unlock prompts.
        @Volatile
        private var requestInProgress: Boolean = false

        // Monotonic elapsedRealtime timestamp when the active unlock request started, used to expire stuck guards.
        @Volatile
        private var requestStartedAtElapsedMs: Long = 0L

        /**
         * Shows the system unlock prompt on the resolved cover display. When [packageName] is set,
         * that app is opened after a successful unlock; when it is null, the device is only unlocked.
         *
         * Deduplicates concurrent requests within [REQUEST_STALE_TIMEOUT_MS], targets [displayId]
         * via [ActivityOptions.makeBasic], and dispatches through [CoverAppLauncher.dispatchActivityStartBalExempt].
         */
        @Synchronized
        fun startUnlockRequest(context: Context, packageName: String?, displayId: Int?): Boolean {
            val nowElapsedMs = SystemClock.elapsedRealtime()
            if (requestInProgress) {
                val elapsedSinceStartMs = (nowElapsedMs - requestStartedAtElapsedMs).coerceAtLeast(0L)
                if (elapsedSinceStartMs < REQUEST_STALE_TIMEOUT_MS) {
                    Log.d(LOG_TAG, "Unlock request already in progress; skipping duplicate package=$packageName")
                    return true
                }
                Log.w(LOG_TAG, "Resetting stale in-progress unlock request after ${elapsedSinceStartMs}ms")
                requestInProgress = false
            }

            requestInProgress = true
            requestStartedAtElapsedMs = nowElapsedMs
            // Resolve onto the same display the launch was requested for (the cover display,
            // not necessarily the context's own display) so the biometric prompt surfaces where
            // the user actually tapped.
            val launchDisplayId = CoverAppLauncher.resolveLaunchDisplayId(context = context, displayId = displayId)
            val launchIntent = Intent(context, UnlockBridgeActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                packageName?.let { putExtra(EXTRA_PACKAGE_NAME, it) }
                putExtra(EXTRA_DISPLAY_ID, displayId ?: Display.INVALID_DISPLAY)
                putExtra(EXTRA_REQUEST_ID, UUID.randomUUID().toString())
                putExtra(EXTRA_REQUEST_ELAPSED_MS, SystemClock.elapsedRealtime())
            }
            val launchOptions = ActivityOptions.makeBasic().apply {
                this.launchDisplayId = launchDisplayId
            }.toBundle()

            val started = runCatching {
                // `context` here is frequently a Service's applicationContext (ForegroundService,
                // CoverNotificationListenerService), which is background-activity-launch (BAL)
                // restricted on modern Android: a plain startActivity call can be silently
                // dropped while the device is locked and the app has no visible foreground UI.
                // Route through the same BAL-exempt accessibility-service channel CoverAppLauncher
                // uses for ordinary app launches so the unlock prompt reliably appears.
                CoverAppLauncher.dispatchActivityStartBalExempt(
                    context = context,
                    launchIntent = launchIntent,
                    launchOptions = launchOptions,
                    launchDisplayId = launchDisplayId
                )
                true
            }.getOrElse { error ->
                Log.w(LOG_TAG, "Unable to start unlock bridge package=$packageName error=${error.message}")
                false
            }

            if (!started) {
                clearRequestInProgress()
            }

            return started
        }

        /** Clears the in-flight request deduplication guard once the bridge finishes or fails to start. */
        @Synchronized
        private fun clearRequestInProgress() {
            requestInProgress = false
            requestStartedAtElapsedMs = 0L
        }

        /** Test-only hook to reset the in-progress dedup guard between test cases. */
        @VisibleForTesting
        internal fun resetForTesting() {
            clearRequestInProgress()
        }
    }

    // One-shot latch ensuring completeAndFinish() runs its launch/teardown action at most once.
    private val completionGuard = AtomicBoolean(false)

    // One-shot latch ensuring ForegroundService.setUnlockPromptActive(false) is signaled only once.
    private val promptReleasedGuard = AtomicBoolean(false)

    // One-shot latch ensuring focus callbacks start BiometricPrompt authentication only once.
    private val authStartedGuard = AtomicBoolean(false)

    // Parsed payload describing which package/display (or unlock-only action) triggered this bridge.
    private var unlockRequest: UnlockLaunchRequest? = null

    // Handle used to cancel an in-flight BiometricPrompt cleanly if this Activity is destroyed.
    private var authenticationCancellationSignal: CancellationSignal? = null

    // Elapsed timestamp when prompt.authenticate() started, used to detect immediate preemption cancels.
    private var biometricAuthStartedElapsedMs: Long = 0L

    // Guards against retrying more than once after a premature BIOMETRIC_ERROR_CANCELED.
    private var hasRetriedEarlyCancel: Boolean = false

    // Scheduled retry runnable posted on decorView, retained so onDestroy() can cancel it cleanly.
    private var pendingRetryRunnable: Runnable? = null

    /**
     * Configures lockscreen window flags ([setShowWhenLocked], [setTurnScreenOn]), parses and
     * validates the [UnlockLaunchRequest], short-circuits if already unlocked, or waits for window
     * focus before launching [BiometricPrompt].
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Render above the keyguard and wake the display if the screen was dimming/asleep.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // FLAG_DISMISS_KEYGUARD is a legacy no-op on modern Android for secure keyguards, but
        // pairs with requestDismissKeyguard() below to cover older code paths and non-secure
        // keyguard configurations. requestDismissKeyguard() is the authoritative call.
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)

        val request = parseRequest(intent)
        if (request == null) {
            completeAndFinish(reason = "invalid_request")
            return
        }

        unlockRequest = request
        // Inform ForegroundService so the cover overlay stays hidden while the unlock UI is shown.
        ForegroundService.setUnlockPromptActive(true)

        if (isStaleRequest(request)) {
            Log.w(LOG_TAG, "Ignoring stale unlock requestId=${request.requestId} package=${request.packageName}")
            completeAndFinish(reason = "stale_request")
            return
        }

        // Android's lockscreen manager; checked early so already-unlocked phones skip BiometricPrompt.
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (keyguardManager != null && !keyguardManager.isKeyguardLocked) {
            Log.d(LOG_TAG, "Device already unlocked on bridge start requestId=${request.requestId}")
            completeAndFinish(reason = "already_unlocked_on_create") {
                dispatchLaunchAfterUnlock(request)
            }
            return
        }

        // Wait for the bridge window to gain focus (after keyguard occlusion settles) before
        // starting BiometricPrompt so Samsung Keyguard's occlusion transition does not preempt
        // and cancel the prompt with ERROR_CANCELED.
        if (hasWindowFocus()) {
            startAuthenticationIfPending()
        }
    }

    /** Triggers authentication once the bridge window wins focus over the occluding keyguard. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            startAuthenticationIfPending()
        }
    }

    /** Idempotently starts [BiometricPrompt] authentication if not yet started or completed. */
    private fun startAuthenticationIfPending() {
        val request = unlockRequest ?: return
        if (completionGuard.get()) return
        if (!authStartedGuard.compareAndSet(false, true)) return
        startBiometricAuthentication(request)
    }

    /** Cleans up pending biometric retries, cancellation signals, and overlay suppression state. */
    override fun onDestroy() {
        pendingRetryRunnable?.let { window.decorView.removeCallbacks(it) }
        pendingRetryRunnable = null
        authenticationCancellationSignal?.cancel()
        authenticationCancellationSignal = null
        clearRequestInProgress()
        releaseUnlockPromptIfNeeded()
        super.onDestroy()
    }

    /** Ensures `ForegroundService.setUnlockPromptActive(false)` is signaled at most once. */
    private fun releaseUnlockPromptIfNeeded() {
        if (unlockRequest != null && promptReleasedGuard.compareAndSet(false, true)) {
            ForegroundService.setUnlockPromptActive(false)
        }
    }

    /**
     * Checks [BiometricManager.canAuthenticate] for strong biometrics or device credentials and
     * presents [BiometricPrompt], falling back directly to [dismissKeyguardAndLaunch] when
     * biometric/credential authentication is unavailable.
     */
    private fun startBiometricAuthentication(request: UnlockLaunchRequest) {
        // Accept either Class 3 biometrics (fingerprint/face) or device PIN/pattern/password.
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        // System biometric capability service queried before building BiometricPrompt.
        val biometricManager = getSystemService(BiometricManager::class.java)
        val biometricStatus = biometricManager?.canAuthenticate(authenticators)
            ?: BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        if (biometricStatus != BiometricManager.BIOMETRIC_SUCCESS) {
            Log.i(
                LOG_TAG,
                "BiometricPrompt unavailable status=$biometricStatus; falling back to KeyguardManager requestDismissKeyguard requestId=${request.requestId}"
            )
            dismissKeyguardAndLaunch(request)
            return
        }

        val isUnlockOnly = request.packageName == null

        // Native system authentication dialog configured for either app-unlock or phone-unlock copy.
        val prompt = BiometricPrompt.Builder(this)
            .setTitle(if (isUnlockOnly) UNLOCK_ONLY_PROMPT_TITLE else UNLOCK_PROMPT_TITLE)
            .setSubtitle(if (isUnlockOnly) UNLOCK_ONLY_PROMPT_SUBTITLE else UNLOCK_PROMPT_SUBTITLE)
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false)
            .build()

        // Listener receiving user authentication success, failure, or keyguard-preemption cancellation.
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val elapsedSinceStartMs = (SystemClock.elapsedRealtime() - biometricAuthStartedElapsedMs).coerceAtLeast(0L)
                // Retry once if the keyguard transition canceled the prompt immediately after startup.
                if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED &&
                    !hasRetriedEarlyCancel &&
                    elapsedSinceStartMs <= EARLY_BIOMETRIC_CANCEL_RETRY_WINDOW_MS &&
                    !completionGuard.get()
                ) {
                    hasRetriedEarlyCancel = true
                    Log.i(
                        LOG_TAG,
                        "BiometricPrompt preempted after ${elapsedSinceStartMs}ms; retrying requestId=${request.requestId}"
                    )
                    val retryRunnable = Runnable {
                        pendingRetryRunnable = null
                        if (!completionGuard.get() && !isFinishing && !isDestroyed) {
                            startBiometricAuthentication(request)
                        }
                    }
                    pendingRetryRunnable = retryRunnable
                    window.decorView.postDelayed(retryRunnable, BIOMETRIC_RETRY_DELAY_MS)
                    return
                }
                val reason = "auth_error code=$errorCode message=${errString.toString()}"
                Log.d(LOG_TAG, "Unlock canceled requestId=${request.requestId} reason=$reason")
                completeAndFinish(reason = reason)
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // Biometric success alone does not guarantee the keyguard is dismissed
                // (particularly for launches routed through the cover display). We must
                // explicitly ask KeyguardManager to dismiss the keyguard before dispatching
                // the target activity, otherwise the direct startActivity fallback is silently
                // suppressed by background-activity-launch policy behind the lockscreen and
                // the caller loops on the same tap forever.
                dismissKeyguardAndLaunch(request)
            }

            override fun onAuthenticationFailed() {
                Log.d(LOG_TAG, "Authentication attempt failed requestId=${request.requestId}")
            }
        }

        // Cancellation handle bound to this prompt session so onDestroy() can dismiss it safely.
        val cancellationSignal = CancellationSignal().also {
            authenticationCancellationSignal = it
        }
        biometricAuthStartedElapsedMs = SystemClock.elapsedRealtime()

        runCatching {
            prompt.authenticate(cancellationSignal, mainExecutor, callback)
        }.onFailure { error ->
            Log.w(LOG_TAG, "Failed to start biometric prompt requestId=${request.requestId}: ${error.message}")
            completeAndFinish(reason = "prompt_start_failed")
        }
    }

    /** Launches [UnlockLaunchRequest.packageName] onto its target display once unlocked. */
    private fun dispatchLaunchAfterUnlock(request: UnlockLaunchRequest) {
        val packageName = request.packageName
        if (packageName == null) {
            Log.d(LOG_TAG, "Unlock-only request completed requestId=${request.requestId}")
            return
        }
        // Deliberately use the Activity context (not applicationContext). This Activity is still
        // focused and resumed at this point, which grants it the privilege to start activities.
        // Using applicationContext here would discard that privilege and the launch would be
        // silently dropped by background-activity-launch enforcement.
        val launched = CoverAppLauncher.launchPackageOnDisplayAfterUnlock(
            context = this,
            packageName = packageName,
            displayId = request.displayId
        )

        if (launched) {
            Log.d(LOG_TAG, "Launch dispatched after unlock requestId=${request.requestId} package=${request.packageName}")
        } else {
            Log.w(LOG_TAG, "Launch failed after unlock requestId=${request.requestId} package=${request.packageName}")
        }
    }

    /**
     * Calls [KeyguardManager.requestDismissKeyguard] if the keyguard remains locked and waits for
     * [KeyguardManager.KeyguardDismissCallback] before dispatching the deferred launch and closing
     * the bridge activity.
     */
    private fun dismissKeyguardAndLaunch(request: UnlockLaunchRequest) {
        // Android's keyguard controller used to drop the lockscreen curtain after authentication.
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (keyguardManager == null) {
            Log.w(LOG_TAG, "KeyguardManager unavailable; dispatching launch without explicit dismiss requestId=${request.requestId}")
            completeAndFinish(reason = "auth_succeeded_no_keyguard_manager") {
                dispatchLaunchAfterUnlock(request)
            }
            return
        }

        if (!keyguardManager.isKeyguardLocked) {
            // Keyguard was already down (biometric may have dismissed it) — proceed directly.
            completeAndFinish(reason = "auth_succeeded_already_unlocked") {
                dispatchLaunchAfterUnlock(request)
            }
            return
        }

        // Callback notified once the system finishes sliding the lockscreen out of the way.
        val dismissCallback = object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                Log.d(LOG_TAG, "Keyguard dismissed after biometric success requestId=${request.requestId}")
                completeAndFinish(reason = "auth_succeeded_keyguard_dismissed") {
                    dispatchLaunchAfterUnlock(request)
                }
            }

            override fun onDismissCancelled() {
                Log.d(LOG_TAG, "Keyguard dismiss cancelled requestId=${request.requestId}")
                completeAndFinish(reason = "keyguard_dismiss_cancelled")
            }

            override fun onDismissError() {
                Log.w(LOG_TAG, "Keyguard dismiss error requestId=${request.requestId}")
                completeAndFinish(reason = "keyguard_dismiss_error")
            }
        }

        runCatching {
            keyguardManager.requestDismissKeyguard(this, dismissCallback)
        }.onFailure { error ->
            Log.w(LOG_TAG, "requestDismissKeyguard threw requestId=${request.requestId}: ${error.message}")
            completeAndFinish(reason = "keyguard_dismiss_threw") {
                // Best-effort: still try the launch — worst case it no-ops and the user retries.
                dispatchLaunchAfterUnlock(request)
            }
        }
    }

    /** Extracts [UnlockLaunchRequest] metadata from the bridge activity's launch [Intent]. */
    private fun parseRequest(intent: Intent?): UnlockLaunchRequest? {
        val sourceIntent = intent ?: return null
        val packageName = sourceIntent.getStringExtra(EXTRA_PACKAGE_NAME)
            ?.trim()
            ?.takeUnless { it.isEmpty() }
        val displayId = sourceIntent.getIntExtra(EXTRA_DISPLAY_ID, Display.INVALID_DISPLAY)
            .takeUnless { it == Display.INVALID_DISPLAY }
        val requestId = sourceIntent.getStringExtra(EXTRA_REQUEST_ID)
            ?.trim()
            ?.takeUnless { it.isEmpty() }
            ?: UUID.randomUUID().toString()
        val requestedAtElapsedMs = sourceIntent.getLongExtra(EXTRA_REQUEST_ELAPSED_MS, 0L)

        return UnlockLaunchRequest(
            packageName = packageName,
            displayId = displayId,
            requestId = requestId,
            requestedAtElapsedMs = requestedAtElapsedMs
        )
    }

    /** Returns true when [request] is older than [REQUEST_STALE_TIMEOUT_MS] (for example an orphaned task). */
    private fun isStaleRequest(request: UnlockLaunchRequest): Boolean {
        if (request.requestedAtElapsedMs <= 0L) return false
        val ageMs = (SystemClock.elapsedRealtime() - request.requestedAtElapsedMs).coerceAtLeast(0L)
        return ageMs > REQUEST_STALE_TIMEOUT_MS
    }


    /**
     * Atomically executes [action] at most once, clears unlock-prompt state when applicable,
     * and removes the bridge task from recents via [finishAndRemoveTask].
     */
    private inline fun completeAndFinish(reason: String, action: () -> Unit = {}) {
        if (!completionGuard.compareAndSet(false, true)) {
            return
        }

        action()
        Log.d(LOG_TAG, "Unlock flow completed reason=$reason requestId=${unlockRequest?.requestId}")

        // For unlock-only flows, release the overlay suppression before tearing down the bridge
        // task so the cover launcher overlay reclaims Display 1 immediately rather than exposing
        // any stale activity underneath.
        if (unlockRequest?.packageName == null) {
            releaseUnlockPromptIfNeeded()
        }

        runCatching { finishAndRemoveTask() }
            .onFailure { finish() }
    }
}

