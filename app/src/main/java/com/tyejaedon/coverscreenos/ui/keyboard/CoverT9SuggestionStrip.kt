package com.tyejaedon.coverscreenos.ui.keyboard

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.SuggestionStrip

/**
 * Predictions for the existing multi-tap T9 keypad. [onCandidateCommitted]
 * must replace the current word at the host's caret, not append a second word.
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
    val dictionary = remember(context, contactRevision) { WordDictionary.fromContext(context) }
    var revision by remember { mutableIntStateOf(0) }
    val word = textBeforeCursor.takeLastWhile { it.isLetter() }
    val suggestions = remember(dictionary, textBeforeCursor, predictiveDigits, revision, contactRevision) {
        if (!predictiveDigits.isNullOrEmpty()) dictionary.suggestT9(predictiveDigits)
        else if (word.isNotEmpty()) dictionary.suggest(word)
        else dictionary.nextWords(textBeforeCursor.trimEnd().takeLastWhile { it.isLetter() })
    }
    SuggestionStrip(
        suggestions = suggestions,
        onSelected = { _, candidate ->
            if (dictionary.isAvailable(candidate)) onCandidateCommitted(candidate)
            else {
                Log.w("CoverKeyboard", "Selected suggestion is no longer available")
                revision++
            }
        },
        onLearn = { _, candidate ->
            if (dictionary.isAvailable(candidate)) dictionary.learn(candidate)
            revision++
        },
        onSuppress = { _, candidate ->
            if (dictionary.isAvailable(candidate)) dictionary.suppress(candidate)
            revision++
        },
        selectedIndex = if (word.isNotEmpty()) 0 else -1,
        modifier = modifier.testTag("keyboard_t9_suggestions")
    )
}
