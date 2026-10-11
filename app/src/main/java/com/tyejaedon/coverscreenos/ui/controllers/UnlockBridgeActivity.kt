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
import com.tyejaedon.coverscreenos.services.overlay.ForegroundService
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class UnlockBridgeActivity : FragmentActivity() {

    /** [packageName] is null for an unlock-only request (lock icon tap). */
    private data class UnlockLaunchRequest(
        val packageName: String?,
        val displayId: Int?,
        val requestId: String,
        val requestedAtElapsedMs: Long
    )

    companion object {
        private const val LOG_TAG = "UnlockBridgeActivity"
        private const val EXTRA_PACKAGE_NAME = "com.tyejaedon.coverscreenos.extra.UNLOCK_PACKAGE_NAME"
        private const val EXTRA_DISPLAY_ID = "com.tyejaedon.coverscreenos.extra.UNLOCK_DISPLAY_ID"
        private const val EXTRA_REQUEST_ID = "com.tyejaedon.coverscreenos.extra.UNLOCK_REQUEST_ID"
        private const val EXTRA_REQUEST_ELAPSED_MS = "com.tyejaedon.coverscreenos.extra.UNLOCK_REQUEST_ELAPSED_MS"

        private const val REQUEST_STALE_TIMEOUT_MS = 15_000L
        private const val UNLOCK_PROMPT_TITLE = "Unlock to continue"
        private const val UNLOCK_PROMPT_SUBTITLE = "Authenticate with your device security to open the app"
        private const val UNLOCK_ONLY_PROMPT_TITLE = "Unlock your phone"
        private const val UNLOCK_ONLY_PROMPT_SUBTITLE = "Authenticate with your device security"

        @Volatile
        private var requestInProgress: Boolean = false

        @Synchronized
        /**
         * Shows the system unlock prompt. When [packageName] is set, that app is opened after a
         * successful unlock. When it is null, the device is only unlocked.
         */
        fun startUnlockRequest(context: Context, packageName: String?, displayId: Int?): Boolean {
            if (requestInProgress) {
                Log.d(LOG_TAG, "Unlock request already in progress; skipping duplicate package=$packageName")
                return true
            }

            requestInProgress = true
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
                requestInProgress = false
            }

            return started
        }

        @Synchronized
        private fun clearRequestInProgress() {
            requestInProgress = false
        }

        /** Test-only hook to reset the in-progress dedup guard between test cases. */
        @VisibleForTesting
        internal fun resetForTesting() {
            requestInProgress = false
        }
    }

    private val completionGuard = AtomicBoolean(false)
    private var unlockRequest: UnlockLaunchRequest? = null
    private var authenticationCancellationSignal: CancellationSignal? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        ForegroundService.setUnlockPromptActive(true)

        if (isStaleRequest(request)) {
            Log.w(LOG_TAG, "Ignoring stale unlock requestId=${request.requestId} package=${request.packageName}")
            completeAndFinish(reason = "stale_request")
            return
        }

        startBiometricAuthentication(request)
    }

    override fun onDestroy() {
        authenticationCancellationSignal?.cancel()
        authenticationCancellationSignal = null
        clearRequestInProgress()
        if (unlockRequest != null) ForegroundService.setUnlockPromptActive(false)
        super.onDestroy()
    }

    private fun startBiometricAuthentication(request: UnlockLaunchRequest) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val biometricManager = getSystemService(BiometricManager::class.java)
        val biometricStatus = biometricManager?.canAuthenticate(authenticators)
            ?: BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        if (biometricStatus != BiometricManager.BIOMETRIC_SUCCESS) {
            Log.w(LOG_TAG, "BiometricPrompt unavailable status=$biometricStatus requestId=${request.requestId}")
            completeAndFinish(reason = "biometric_unavailable")
            return
        }

        val isUnlockOnly = request.packageName == null
        val prompt = BiometricPrompt.Builder(this)
            .setTitle(if (isUnlockOnly) UNLOCK_ONLY_PROMPT_TITLE else UNLOCK_PROMPT_TITLE)
            .setSubtitle(if (isUnlockOnly) UNLOCK_ONLY_PROMPT_SUBTITLE else UNLOCK_PROMPT_SUBTITLE)
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false)
            .build()

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
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

        val cancellationSignal = CancellationSignal().also {
            authenticationCancellationSignal = it
        }

        runCatching {
            prompt.authenticate(cancellationSignal, mainExecutor, callback)
        }.onFailure { error ->
            Log.w(LOG_TAG, "Failed to start biometric prompt requestId=${request.requestId}: ${error.message}")
            completeAndFinish(reason = "prompt_start_failed")
        }
    }

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

    private fun dismissKeyguardAndLaunch(request: UnlockLaunchRequest) {
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

    private fun isStaleRequest(request: UnlockLaunchRequest): Boolean {
        if (request.requestedAtElapsedMs <= 0L) return false
        val ageMs = (SystemClock.elapsedRealtime() - request.requestedAtElapsedMs).coerceAtLeast(0L)
        return ageMs > REQUEST_STALE_TIMEOUT_MS
    }


    private inline fun completeAndFinish(reason: String, action: () -> Unit = {}) {
        if (!completionGuard.compareAndSet(false, true)) {
            return
        }

        action()
        Log.d(LOG_TAG, "Unlock flow completed reason=$reason requestId=${unlockRequest?.requestId}")

        runCatching { finishAndRemoveTask() }
            .onFailure { finish() }
    }
}

