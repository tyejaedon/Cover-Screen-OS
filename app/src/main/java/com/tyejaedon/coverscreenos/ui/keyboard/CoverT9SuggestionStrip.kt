package com.tyejaedon.coverscreenos.ui.keyboard

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.tyejaedon.coverscreenos.keyboard.floris.FlorisSmartbar
import com.tyejaedon.coverscreenos.keyboard.floris.FlorisThemePalette
import com.tyejaedon.coverscreenos.keyboard.nlp.CoverKeyboardNlpManager
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.HapticTier
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.rememberHapticPerformer

/**
 * Predictions for the T9 keypad, backed by `:keyboard-floris` [FlorisSmartbar] and
 * [CoverKeyboardNlpManager].
 * [onCandidateCommitted] replaces the current word at the host's caret.
 */
@Composable
internal fun CoverT9SuggestionStrip(
    textBeforeCursor: String,
    onCandidateCommitted: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    predictiveDigits: String? = null
) {
    if (!enabled) return

    val context = LocalContext.current
    val contactRevision = rememberContactSuggestionRevision(enabled)
    LaunchedEffect(context, contactRevision) {
        CoverKeyboardNlpManager.initializeFromContext(context)
        runCatching { CoverKeyboardNlpManager.refreshContactWords(context) }
    }

    val haptic = rememberHapticPerformer(HapticTier.Light)
    var revision by remember { mutableIntStateOf(0) }
    val word = textBeforeCursor.takeLastWhile { it.isLetter() }
    val suggestions = remember(textBeforeCursor, predictiveDigits, revision, contactRevision) {
        when {
            !predictiveDigits.isNullOrEmpty() -> CoverKeyboardNlpManager.getT9Suggestions(predictiveDigits)
            word.isNotEmpty() -> CoverKeyboardNlpManager.getQwertySuggestions(word)
            else -> CoverKeyboardNlpManager.getNextWordSuggestions(
                textBeforeCursor.trimEnd().takeLastWhile { it.isLetter() }
            )
        }
    }

    FlorisSmartbar(
        candidates = suggestions,
        selectedIndex = if (word.isNotEmpty()) 0 else -1,
        onCandidateSelected = { _, candidate ->
            if (CoverKeyboardNlpManager.isWordAvailable(candidate)) {
                onCandidateCommitted(candidate)
            } else {
                Log.w("CoverKeyboard", "Selected suggestion is no longer available")
                revision++
            }
        },
        onLearnCandidate = { _, candidate ->
            if (CoverKeyboardNlpManager.isWordAvailable(candidate)) {
                CoverKeyboardNlpManager.learnWord(candidate, context)
            }
            revision++
        },
        onSuppressCandidate = { _, candidate ->
            if (CoverKeyboardNlpManager.isWordAvailable(candidate)) {
                CoverKeyboardNlpManager.suppressWord(candidate, context)
            }
            revision++
        },
        onHaptic = haptic,
        themePalette = FlorisThemePalette.AmoledCover,
        modifier = modifier.testTag("keyboard_t9_suggestions")
    )
}
