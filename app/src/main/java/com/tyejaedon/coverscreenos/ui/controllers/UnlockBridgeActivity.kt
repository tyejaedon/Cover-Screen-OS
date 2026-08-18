package com.tyejaedon.coverscreenos.ui.controllers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.util.Log
import android.view.Display
import androidx.fragment.app.FragmentActivity
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class UnlockBridgeActivity : FragmentActivity() {

    private data class UnlockLaunchRequest(
        val packageName: String,
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

        @Volatile
        private var requestInProgress: Boolean = false

        @Synchronized
        fun startUnlockRequest(context: Context, packageName: String, displayId: Int?): Boolean {
            if (requestInProgress) {
                Log.d(LOG_TAG, "Unlock request already in progress; skipping duplicate package=$packageName")
                return true
            }

            requestInProgress = true
            val launchIntent = Intent(context, UnlockBridgeActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_DISPLAY_ID, displayId ?: Display.INVALID_DISPLAY)
                putExtra(EXTRA_REQUEST_ID, UUID.randomUUID().toString())
                putExtra(EXTRA_REQUEST_ELAPSED_MS, SystemClock.elapsedRealtime())
            }

            val started = runCatching {
                context.startActivity(launchIntent)
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
    }

    private val completionGuard = AtomicBoolean(false)
    private var unlockRequest: UnlockLaunchRequest? = null
    private var authenticationCancellationSignal: CancellationSignal? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val request = parseRequest(intent)
        if (request == null) {
            completeAndFinish(reason = "invalid_request")
            return
        }

        unlockRequest = request

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

        val prompt = BiometricPrompt.Builder(this)
            .setTitle(UNLOCK_PROMPT_TITLE)
            .setSubtitle(UNLOCK_PROMPT_SUBTITLE)
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
                completeAndFinish(reason = "auth_succeeded") {
                    dispatchLaunchAfterUnlock(request)
                }
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
        val launched = CoverAppLauncher.launchPackageOnDisplayAfterUnlock(
            context = applicationContext,
            packageName = request.packageName,
            displayId = request.displayId
        )

        if (launched) {
            Log.d(LOG_TAG, "Launch dispatched after unlock requestId=${request.requestId} package=${request.packageName}")
            return
        }
    }

    private fun parseRequest(intent: Intent?): UnlockLaunchRequest? {
        val sourceIntent = intent ?: return null
        val packageName = sourceIntent.getStringExtra(EXTRA_PACKAGE_NAME)
            ?.trim()
            ?.takeUnless { it.isEmpty() }
            ?: return null
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

