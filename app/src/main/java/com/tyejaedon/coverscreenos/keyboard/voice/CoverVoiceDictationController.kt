package com.tyejaedon.coverscreenos.keyboard.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

private const val TAG = "CoverVoiceDictation"

/**
 * Formats a speech-recognized [rawPhrase] so it can be inserted cleanly at the cursor right
 * after [existingBeforeCursor]. Trims outer whitespace and prepends a leading space whenever
 * the preceding text is non-empty and does not already end in whitespace.
 */
internal fun formatDictatedPhrase(existingBeforeCursor: String, rawPhrase: String): String {
    val trimmed = rawPhrase.trim()
    if (trimmed.isEmpty()) return ""
    val needsLeadingSpace = existingBeforeCursor.isNotEmpty() &&
        !existingBeforeCursor.last().isWhitespace()
    return if (needsLeadingSpace) " $trimmed" else trimmed
}

/**
 * Immutable Compose UI handle exposing live dictation state and the toggle callback wired into
 * `FlorisKeyboardCustomization`. When `onToggle` is `null`, the keyboard top bar hides the mic icon
 * (for example on numeric PIN or password fields).
 */
@Stable
internal data class VoiceDictationUiHandle(
    /** True while [SpeechRecognizer] is actively capturing microphone audio. */
    val isListening: Boolean,
    /** Toggles dictation on/off, or `null` when voice input is disabled for the current field. */
    val onToggle: (() -> Unit)?
)

/**
 * Lifecycle-safe wrapper around Android's [SpeechRecognizer] designed for secondary cover-screen
 * keyboard hosts (System IME service, Accessibility keyboard overlay, and Launcher search).
 *
 * Ensures recognition operations run on the main thread, enforces [Manifest.permission.RECORD_AUDIO]
 * before creating a recognizer session, and releases native binder resources in [destroy].
 */
internal class CoverVoiceDictationController(
    context: Context,
    private val onPhraseRecognized: (String) -> Unit,
    private val onStatusChanged: ((String) -> Unit)? = null
) {
    // Application context prevents leaking temporary window/Compose contexts across sessions.
    private val appContext = context.applicationContext
    // Main-thread handler because SpeechRecognizer requires all API calls on the main Looper.
    private val mainHandler = Handler(Looper.getMainLooper())
    // Lazily created recognizer instance, destroyed cleanly on release or error cleanup.
    private var recognizer: SpeechRecognizer? = null

    /** Compose-observed flag indicating whether voice capture is currently active. */
    var isListening: Boolean by mutableStateOf(false)
        private set

    /** Toggles speech recognition between active listening and stopped states. */
    fun toggleListening() {
        if (isListening) {
            stopListening()
        } else {
            startListening()
        }
    }

    /**
     * Verifies `RECORD_AUDIO` permission and recognizer availability, then starts listening for
     * a single free-form phrase on the main thread.
     */
    fun startListening() {
        if (isListening) return

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAudioPermission) {
            notifyStatus("Grant Microphone permission in app settings")
            return
        }

        val available = runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }
            .getOrDefault(false)
        if (!available) {
            notifyStatus("Speech recognition unavailable on device")
            return
        }

        mainHandler.post {
            runCatching {
                ensureRecognizer()?.let { sr ->
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    }
                    isListening = true
                    notifyStatus("Listening...")
                    sr.startListening(intent)
                }
            }.onFailure { error ->
                isListening = false
                Log.w(TAG, "Failed to start voice dictation", error)
                notifyStatus("Voice input failed to start")
            }
        }
    }

    /** Stops an active capture session so finalized audio can still finish decoding. */
    fun stopListening() {
        if (!isListening) return
        mainHandler.post {
            isListening = false
            runCatching { recognizer?.stopListening() }
        }
    }

    /** Cancels active capture and tears down the [SpeechRecognizer] binder connection. */
    fun destroy() {
        isListening = false
        mainHandler.removeCallbacksAndMessages(null)
        val instance = recognizer
        recognizer = null
        if (instance != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                runCatching { instance.destroy() }
            } else {
                mainHandler.post { runCatching { instance.destroy() } }
            }
        }
    }

    /** Creates the [SpeechRecognizer] and binds a single-shot [RecognitionListener] on first use. */
    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        return runCatching {
            SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) = Unit

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that"
                            SpeechRecognizer.ERROR_AUDIO -> "Microphone busy"
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech network error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Grant Microphone permission in app settings"
                            else -> "Voice input unavailable"
                        }
                        notifyStatus(message)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val topPhrase = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (topPhrase.isNotEmpty()) {
                            onPhraseRecognized(topPhrase)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) = Unit

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }.also { recognizer = it }
        }.onFailure { error ->
            Log.w(TAG, "Unable to create SpeechRecognizer", error)
            notifyStatus("Speech recognition unavailable")
        }.getOrNull()
    }

    /** Relays dictation status via [onStatusChanged] or a brief fallback Toast if unset. */
    private fun notifyStatus(message: String) {
        val listener = onStatusChanged
        if (listener != null) {
            listener(message)
        } else {
            mainHandler.post {
                runCatching {
                    Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/**
 * Remembers a [CoverVoiceDictationController] scoped to the active keyboard composition and
 * guarantees [CoverVoiceDictationController.destroy] on disposal or when [enabled] turns `false`.
 */
@Composable
internal fun rememberVoiceDictationController(
    enabled: Boolean,
    onPhraseRecognized: (String) -> Unit,
    onStatusChanged: ((String) -> Unit)? = null
): VoiceDictationUiHandle {
    val context = LocalContext.current
    val latestOnPhrase by rememberUpdatedState(onPhraseRecognized)
    val latestOnStatus by rememberUpdatedState(onStatusChanged)

    val controller = remember(context) {
        CoverVoiceDictationController(
            context = context,
            onPhraseRecognized = { phrase -> latestOnPhrase(phrase) },
            onStatusChanged = onStatusChanged?.let { { msg -> latestOnStatus?.invoke(msg) } }
        )
    }

    DisposableEffect(controller, enabled) {
        if (!enabled) {
            controller.destroy()
        }
        onDispose {
            controller.destroy()
        }
    }

    return VoiceDictationUiHandle(
        isListening = enabled && controller.isListening,
        onToggle = if (enabled) controller::toggleListening else null
    )
}
