package com.tyejaedon.coverscreenos.ui.launcher

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.datastore.KeyboardStrategy
import com.tyejaedon.coverscreenos.ui.CoverSearchUiTestTags
import com.tyejaedon.coverscreenos.ui.keyboard.CoverCompactQwertyKeyboard
import com.tyejaedon.coverscreenos.ui.theme.coverMinimumTouchTarget
import com.tyejaedon.coverscreenos.ui.theme.coverScreenContentPadding
import com.tyejaedon.coverscreenos.ui.theme.coverScreenPadding
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import java.util.Locale

private val SEARCH_GRID_TILE_GAP = 6.dp
private val SEARCH_GRID_CONTENT_PADDING = coverScreenContentPadding(horizontal = 6.dp, vertical = 2.dp)

internal data class OverlayVoiceInputHandle(
    val isAvailable: Boolean,
    val startListening: () -> Unit,
    val stopListening: () -> Unit
)

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
    onCharTyped: (Char) -> Unit,
    onBackspacePressed: () -> Unit,
    onDonePressed: () -> Unit,
    onClearPressed: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    // Only the system-IME strategy consumes IME insets; the shared cover keyboard is
    // drawn inline and must never be pushed by a (possibly mis-reported) soft-keyboard
    // inset.
    val insetsModifier = if (keyboardStrategy == KeyboardStrategy.SYSTEM_IME) {
        Modifier
            .imePadding()
            .testTag(CoverSearchUiTestTags.SEARCH_CONTENT_CONTAINER_WITH_IME_PADDING)
    } else {
        Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .testTag(CoverSearchUiTestTags.SEARCH_CONTENT_CONTAINER_NO_IME_PADDING)
    }

    val widgetGridModifier = Modifier
        .fillMaxSize()
        .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp)
        .then(insetsModifier)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .then(widgetGridModifier)
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
            ),
        contentPadding = SEARCH_GRID_CONTENT_PADDING,
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(SEARCH_GRID_TILE_GAP),
        verticalArrangement = Arrangement.spacedBy(SEARCH_GRID_TILE_GAP)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
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
                onCharTyped = onCharTyped,
                onBackspacePressed = onBackspacePressed,
                onDonePressed = onDonePressed,
                modifier = Modifier.fillMaxWidth(),
                hazeState = hazeState,
                onClearPressed = onClearPressed
            )
        }
    }
}

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
    onCharTyped: (Char) -> Unit,
    onBackspacePressed: () -> Unit,
    onClearPressed: () -> Unit,
    onDonePressed: () -> Unit,
    hazeState: HazeState, // Pass HazeState from the parent screen
    modifier: Modifier = Modifier
) {
    val tileShape = RoundedCornerShape(24.dp)

    // Contrast wash: Use a higher alpha (0.65f - 0.80f) so key labels pop
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Surface(
        modifier = modifier
            // 1. Shape clipping first so the blur and borders don't bleed
            .clip(tileShape)
            // 2. Hardware backdrop blur sampled from the background behind it
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    tints = listOf(HazeTint(surfaceColor)),
                    blurRadius = 24.dp,
                    noiseFactor = 0.05f
                )
            )
            // 3. Subtle glass stroke outline for depth separation
            .border(
                width = 1.dp,
                color = borderColor,
                shape = tileShape
            )
            // 4. Click handling inside clipped bounds
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissInputTap
            ),
        color = Color.Transparent, // Let the hazeEffect handle the background wash
        shape = tileShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .coverScreenPadding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CoverSearchInputRow(
                query = searchQuery,
                onQueryChanged = onQueryChanged,
                onSearchFieldTapped = onSearchFieldTapped,
                onVoiceInputTap = onVoiceInputTap,
                isVoiceListening = isVoiceListening,
                voiceHintMessage = voiceHintMessage,
                focusRequester = focusRequester,
                onDismissTileTap = onDismissTileTap,
                keyboardStrategy = keyboardStrategy,
                onKeyboardStrategyToggle = onKeyboardStrategyToggle,
                onOpenImePicker = onOpenImePicker,
                modifier = Modifier.fillMaxWidth()
            )

            if (keyboardStrategy == KeyboardStrategy.T9 && isCoverKeyboardVisible) {
                CoverSearchKeyboardHost(
                    onCharTyped = onCharTyped,
                    onBackspacePressed = onBackspacePressed,
                    onDonePressed = onDonePressed,
                    onClearPressed = onClearPressed,
                    modifier = Modifier.fillMaxWidth()
                )
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

@Composable
private fun CoverSearchInputRow(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearchFieldTapped: () -> Unit,
    onVoiceInputTap: () -> Unit,
    isVoiceListening: Boolean,
    voiceHintMessage: String?,
    focusRequester: FocusRequester,
    onDismissTileTap: () -> Unit,
    keyboardStrategy: KeyboardStrategy,
    onKeyboardStrategyToggle: () -> Unit,
    onOpenImePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inputFieldShape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier.testTag(CoverSearchUiTestTags.SEARCH_INPUT_ROW),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (keyboardStrategy) {
                KeyboardStrategy.T9 -> {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(inputFieldShape)
                            .clickable(onClick = onSearchFieldTapped)
                            .focusRequester(focusRequester)
                            .coverMinimumTouchTarget()
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                            .testTag(CoverSearchUiTestTags.SEARCH_T9_FIELD),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        shape = inputFieldShape
                    ) {
                        Text(
                            text = if (query.isBlank()) "Tap to search apps" else query,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (query.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .coverScreenPadding(horizontal = 12.dp, vertical = 10.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                KeyboardStrategy.SYSTEM_IME -> {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChanged,
                        singleLine = true,
                        shape = inputFieldShape,
                        placeholder = { Text("Search apps") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .coverMinimumTouchTarget()
                            .testTag(CoverSearchUiTestTags.SEARCH_SYSTEM_IME_FIELD)
                    )
                }
            }

            IconButton(
                onClick = onVoiceInputTap,
                modifier = Modifier.coverMinimumTouchTarget()
            ) {
                Icon(
                    imageVector = if (isVoiceListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = if (isVoiceListening) "Stop voice input" else "Start voice input"
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { }
                    .testTag(CoverSearchUiTestTags.SEARCH_INPUT_MODE_LABEL),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Input:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when (keyboardStrategy) {
                        KeyboardStrategy.T9 -> "T9"
                        KeyboardStrategy.SYSTEM_IME -> "system keyboard"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onKeyboardStrategyToggle,
                modifier = Modifier
                    .coverMinimumTouchTarget()
                    .testTag(CoverSearchUiTestTags.SEARCH_INPUT_MODE_TOGGLE_BUTTON)
            ) {
                Icon(
                    imageVector = when (keyboardStrategy) {
                        KeyboardStrategy.T9 -> Icons.Filled.Keyboard
                        KeyboardStrategy.SYSTEM_IME -> Icons.Filled.Dialpad
                    },
                    contentDescription = when (keyboardStrategy) {
                        KeyboardStrategy.T9 -> "Switch to system keyboard"
                        KeyboardStrategy.SYSTEM_IME -> "Switch to cover keyboard"
                    }
                )
            }

            IconButton(
                onClick = onOpenImePicker,
                modifier = Modifier
                    .coverMinimumTouchTarget()
                    .testTag(CoverSearchUiTestTags.SEARCH_IME_PICKER_BUTTON)
            ) {
                Icon(
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = "Choose input method"
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

private fun extractBestSpeechMatch(results: Bundle?): String? {
    val firstResult = results
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.trim()
        .orEmpty()
    return firstResult.takeUnless { it.isEmpty() }
}

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

@Composable
private fun CoverSearchKeyboardHost(
    onCharTyped: (Char) -> Unit,
    onBackspacePressed: () -> Unit,
    onDonePressed: () -> Unit,
    onClearPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag(CoverSearchUiTestTags.SEARCH_T9_KEYPAD_ROOT),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .coverScreenPadding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            CoverCompactQwertyKeyboard(
                onChar = onCharTyped,
                onBackspace = onBackspacePressed,
                onDone = onDonePressed,
                onClear = onClearPressed,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}