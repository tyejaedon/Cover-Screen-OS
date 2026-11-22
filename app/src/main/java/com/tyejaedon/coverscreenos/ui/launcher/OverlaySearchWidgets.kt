package com.tyejaedon.coverscreenos.ui.launcher

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.tyejaedon.coverscreenos.ui.theme.LocalCoverPanelCornerRadius
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.datastore.KeyboardStrategy
import com.tyejaedon.coverscreenos.ui.CoverSearchUiTestTags
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.ComposingTextPreview
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget
import com.tyejaedon.coverscreenos.ui.theme.coverScreenContentPadding
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding
import com.tyejaedon.coverscreenos.ui.theme.oneui.CoverOneUITokens
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiChip
import com.tyejaedon.coverscreenos.ui.theme.oneui.oneUiPanel
import dev.chrisbanes.haze.HazeState
import java.util.Locale

private val SEARCH_GRID_TILE_GAP = 6.dp
private val SEARCH_GRID_CONTENT_PADDING = coverScreenContentPadding(horizontal = 6.dp, vertical = 2.dp)

/**
 * Handle exposing Android `SpeechRecognizer` availability and listening controls to the
 * cover-screen search UI.
 */
internal data class OverlayVoiceInputHandle(
    val isAvailable: Boolean,
    val startListening: () -> Unit,
    val stopListening: () -> Unit
)

/**
 * Full-page search and widget container inside the cover overlay (`SEARCH_WIDGET_GRID_PAGE`).
 *
 * Hosts [SearchWidgetTile] with bottom `WindowInsets.safeDrawing` padding (intentionally
 * omitting IME insets because typing is handled by the embedded in-overlay keyboard), supports
 * tapping the background scrim to dismiss keyboard focus, and detects upward drags exceeding
 * [swipeThresholdPx] to return to the lock page.
 */
@Composable
internal fun WidgetGridPageTile(
    searchQuery: String,
    onQueryChanged: (String) -> Unit,
    onSearchFieldTapped: () -> Unit,
    onVoiceInputTap: () -> Unit,
    isVoiceListening: Boolean,
    voiceHintMessage: String?,
    focusRequester: FocusRequester,
    onDismissInputTap: () -> Unit,
    onDismissTileTap: () -> Unit,
    onSwipeUpToReturn: () -> Unit,
    swipeThresholdPx: Float,
    keyboardStrategy: KeyboardStrategy,
    onKeyboardStrategyToggle: () -> Unit,
    onOpenImePicker: () -> Unit,
    isCoverKeyboardVisible: Boolean,
    onDonePressed: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val insetsModifier = Modifier
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        .testTag(CoverSearchUiTestTags.SEARCH_CONTENT_CONTAINER_NO_IME_PADDING)

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(CoverSearchUiTestTags.SEARCH_WIDGET_GRID_PAGE)
            .pointerInput(onSwipeUpToReturn, swipeThresholdPx) {
                var cumulativeDrag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        cumulativeDrag += dragAmount
                    },
                    onDragEnd = {
                        if (cumulativeDrag <= -swipeThresholdPx) {
                            onSwipeUpToReturn()
                        }
                        cumulativeDrag = 0f
                    },
                    onDragCancel = { cumulativeDrag = 0f }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissInputTap
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .then(insetsModifier)
        ) {
            SearchWidgetTile(
                searchQuery = searchQuery,
                onQueryChanged = onQueryChanged,
                onSearchFieldTapped = onSearchFieldTapped,
                onVoiceInputTap = onVoiceInputTap,
                isVoiceListening = isVoiceListening,
                voiceHintMessage = voiceHintMessage,
                focusRequester = focusRequester,
                onDismissInputTap = onDismissInputTap,
                onDismissTileTap = onDismissTileTap,
                keyboardStrategy = keyboardStrategy,
                onKeyboardStrategyToggle = onKeyboardStrategyToggle,
                onOpenImePicker = onOpenImePicker,
                isCoverKeyboardVisible = isCoverKeyboardVisible,
                onDonePressed = onDonePressed,
                modifier = Modifier.fillMaxWidth(),
                hazeState = hazeState
            )
        }
    }
}

/**
 * Frosted-glass search container (`oneUiPanel` backed by [hazeState]) housing the search input
 * row and the embedded Floris keyboard (`SearchFlorisKeyboardHost`).
 *
 * Uses `Color.Transparent` on the outer `Surface` so Haze's blur effect supplies the translucent
 * panel wash, and wraps contents in `verticalScroll` so keyboard keys stay reachable on small
 * cover screens.
 */
@Composable
private fun SearchWidgetTile(
    searchQuery: String,
    onQueryChanged: (String) -> Unit,
    onSearchFieldTapped: () -> Unit,
    onVoiceInputTap: () -> Unit,
    isVoiceListening: Boolean,
    voiceHintMessage: String?,
    focusRequester: FocusRequester,
    onDismissInputTap: () -> Unit,
    onDismissTileTap: () -> Unit,
    keyboardStrategy: KeyboardStrategy,
    onKeyboardStrategyToggle: () -> Unit,
    onOpenImePicker: () -> Unit,
    isCoverKeyboardVisible: Boolean,
    onDonePressed: () -> Unit,
    hazeState: HazeState, // Pass HazeState from the parent screen
    modifier: Modifier = Modifier
) {
    val tileShape = RoundedCornerShape(CoverOneUITokens.radiusHero)

    Surface(
        modifier = modifier
            .oneUiPanel(shape = tileShape, hazeState = hazeState, blurRadius = 24.dp),
        color = Color.Transparent, // Let oneUiPanel's hazeEffect handle the background wash
        shape = tileShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Scrollable so the keyboard keys are never clipped when the tile is short.
                .verticalScroll(rememberScrollState())
                .coverScreenPadding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CoverSearchInputRow(
                query = searchQuery,
                onSearchFieldTapped = onSearchFieldTapped,
                onVoiceInputTap = onVoiceInputTap,
                isVoiceListening = isVoiceListening,
                voiceHintMessage = voiceHintMessage,
                focusRequester = focusRequester,
                onDismissTileTap = onDismissTileTap,
                modifier = Modifier.fillMaxWidth()
            )

            if (isCoverKeyboardVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CoverSearchUiTestTags.SEARCH_T9_KEYPAD_ROOT)
                ) {
                    SearchFlorisKeyboardHost(
                        query = searchQuery,
                        onQueryChanged = onQueryChanged,
                        onDonePressed = onDonePressed,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Text(
                text = "Swipe up to return to lock screen",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
        }
    }
}

/**
 * Search bar row displaying either the `"Search apps"` placeholder or a tail-scrolled
 * [ComposingTextPreview] alongside the voice-search microphone chip and keyboard dismiss button.
 */
@Composable
private fun CoverSearchInputRow(
    query: String,
    onSearchFieldTapped: () -> Unit,
    onVoiceInputTap: () -> Unit,
    isVoiceListening: Boolean,
    voiceHintMessage: String?,
    focusRequester: FocusRequester,
    onDismissTileTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inputFieldShape = RoundedCornerShape(LocalCoverPanelCornerRadius.current)

    Column(
        modifier = modifier.testTag(CoverSearchUiTestTags.SEARCH_INPUT_ROW),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .oneUiPanel(shape = inputFieldShape)
                    .clickable(onClick = onSearchFieldTapped)
                    .focusRequester(focusRequester)
                    .coverMinimumTouchTarget()
                    .padding(horizontal = 2.dp, vertical = 1.dp)
                    .testTag(CoverSearchUiTestTags.SEARCH_T9_FIELD)
            ) {
                if (query.isBlank()) {
                    Text(
                        text = "Search apps",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .coverScreenPadding(horizontal = 12.dp, vertical = 10.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    // Scrolled to the end by default so the most recently typed
                    // characters of a long query stay visible, with a tap-to-expand
                    // affordance instead of silently ellipsizing the query's start.
                    ComposingTextPreview(
                        text = query,
                        maxCollapsedLines = 1,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        counterColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        showCharCount = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .coverScreenPadding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .coverMinimumTouchTarget()
                    .oneUiChip(selected = isVoiceListening)
                    .clickable(onClick = onVoiceInputTap),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isVoiceListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = if (isVoiceListening) "Stop voice input" else "Start voice input",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = onDismissTileTap,
                modifier = Modifier
                    .coverMinimumTouchTarget()
                    .testTag(CoverSearchUiTestTags.SEARCH_DISMISS_INPUT_BUTTON)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss keypad"
                )
            }
        }

        voiceHintMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


/**
 * Creates and lifecycle-manages an Android [SpeechRecognizer] bound to Compose, returning an
 * [OverlayVoiceInputHandle] for starting and stopping free-form voice search inside the overlay.
 *
 * Uses `rememberUpdatedState` so the registered [RecognitionListener] delivers partial and final
 * transcripts to the latest callbacks without tearing down the recognizer on recomposition, and
 * calls `stopListening()` + `destroy()` when leaving composition.
 */
@Composable
internal fun rememberVoiceInputHandle(
    onPartialResult: (String) -> Unit,
    onFinalResult: (String) -> Unit,
    onListeningStateChanged: (Boolean) -> Unit,
    onError: (String) -> Unit
): OverlayVoiceInputHandle {
    val context = LocalContext.current
    val latestOnPartialResult = rememberUpdatedState(onPartialResult)
    val latestOnFinalResult = rememberUpdatedState(onFinalResult)
    val latestOnListeningStateChanged = rememberUpdatedState(onListeningStateChanged)
    val latestOnError = rememberUpdatedState(onError)

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        } else {
            null
        }
    }

    // Attach RecognitionListener and destroy the native SpeechRecognizer when leaving composition.
    DisposableEffect(speechRecognizer) {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            latestOnListeningStateChanged.value(false)
            onDispose { }
        } else {
            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    latestOnListeningStateChanged.value(true)
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    latestOnListeningStateChanged.value(false)
                }

                override fun onError(error: Int) {
                    latestOnListeningStateChanged.value(false)
                    latestOnError.value(mapSpeechRecognizerError(error))
                }

                override fun onResults(results: Bundle?) {
                    latestOnListeningStateChanged.value(false)
                    extractBestSpeechMatch(results)?.let { transcript ->
                        latestOnFinalResult.value(transcript)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    extractBestSpeechMatch(partialResults)?.let { transcript ->
                        latestOnPartialResult.value(transcript)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            }

            recognizer.setRecognitionListener(listener)
            onDispose {
                runCatching { recognizer.stopListening() }
                runCatching { recognizer.destroy() }
            }
        }
    }

    val languageTag = remember { Locale.getDefault().toLanguageTag() }
    return remember(speechRecognizer, languageTag) {
        OverlayVoiceInputHandle(
            isAvailable = speechRecognizer != null,
            startListening = {
                val recognizer = speechRecognizer
                if (recognizer == null) {
                    latestOnListeningStateChanged.value(false)
                    latestOnError.value("Voice recognition unavailable on this device.")
                } else {
                    // Request live partial transcripts in the device's default locale.
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    runCatching { recognizer.startListening(intent) }
                        .onFailure { error ->
                            latestOnListeningStateChanged.value(false)
                            latestOnError.value("Unable to start voice input: ${error.message ?: "unknown error"}")
                        }
                }
            },
            stopListening = {
                speechRecognizer?.let { recognizer ->
                    runCatching { recognizer.stopListening() }
                    latestOnListeningStateChanged.value(false)
                }
            }
        )
    }
}

/** Extracts and trims the highest-confidence hypothesis from `SpeechRecognizer.RESULTS_RECOGNITION`. */
private fun extractBestSpeechMatch(results: Bundle?): String? {
    val firstResult = results
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.trim()
        .orEmpty()
    return firstResult.takeUnless { it.isEmpty() }
}

/** Maps Android `SpeechRecognizer.ERROR_*` codes to concise user-facing status hints. */
private fun mapSpeechRecognizerError(errorCode: Int): String {
    return when (errorCode) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice search."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice search failed due to a network issue."
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy. Try again in a moment."
        else -> "Voice search failed (error=$errorCode)."
    }
}
