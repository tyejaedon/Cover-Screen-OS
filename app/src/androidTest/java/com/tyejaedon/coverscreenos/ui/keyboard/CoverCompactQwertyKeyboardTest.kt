package com.tyejaedon.coverscreenos.ui.keyboard

import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.KeyboardKey
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.TextBuffer
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverCompactQwertyKeyboardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun shiftAndSymbolsCanBeToggled() {
        val typed = mutableStateOf("")
        rule.setContent {
            CoverOSTheme {
                CoverCompactQwertyKeyboard(
                    onChar = { typed.value += it }, onBackspace = {},
                    onDone = {}, onClear = {}, textBeforeCursor = typed.value
                )
            }
        }
        rule.onNodeWithTag("keyboard_shift").performTouchInput { click() }
        rule.onNodeWithTag("keyboard_top_0").assertTextEquals("Q")
        rule.onNodeWithTag("keyboard_top_0").performTouchInput { click() }
        rule.runOnIdle { assertEquals("Q", typed.value) }
        rule.onNodeWithTag("keyboard_top_0").assertTextEquals("q")
        rule.onNodeWithTag("keyboard_symbols").performTouchInput { click() }
        rule.onNodeWithTag("keyboard_top_0").assertTextEquals("1")
        rule.onNodeWithTag("keyboard_middle_9").assertTextEquals("\"")
        rule.onNodeWithTag("keyboard_symbols").performTouchInput { click() }
        rule.onNodeWithTag("keyboard_top_0").assertTextEquals("q")
    }

    @Test fun enterGlyphReflectsEditorAction() {
        val action = mutableStateOf(EditorInfo.IME_ACTION_SEARCH)
        rule.setContent {
            CoverOSTheme {
                CoverCompactQwertyKeyboard(
                    onChar = {}, onBackspace = {}, onDone = {}, onClear = {},
                    imeOptions = action.value, suggestionsEnabled = false
                )
            }
        }
        rule.onNodeWithTag("keyboard_enter").assertContentDescriptionEquals("Search")
        rule.runOnIdle { action.value = EditorInfo.IME_ACTION_SEND }
        rule.onNodeWithTag("keyboard_enter").assertContentDescriptionEquals("Send")
    }

    @Test fun longPressTopRowEmitsDigitAndNotLetter() {
        val typed = mutableStateOf("")
        rule.setContent {
            CoverOSTheme {
                CoverCompactQwertyKeyboard(
                    onChar = { typed.value += it }, onBackspace = {},
                    onDone = {}, onClear = {}, suggestionsEnabled = false
                )
            }
        }
        rule.onNodeWithTag("keyboard_top_0").performTouchInput { longClick(durationMillis = 500) }
        rule.runOnIdle { assertEquals("1", typed.value) }
    }

    @Test fun spaceCorrectsHelo() {
        val typed = mutableStateOf("helo")
        rule.setContent {
            CoverOSTheme {
                CoverCompactQwertyKeyboard(
                    onChar = { typed.value += it },
                    onBackspace = { typed.value = typed.value.dropLast(1) },
                    onDone = {}, onClear = {}, textBeforeCursor = typed.value
                )
            }
        }
        rule.onNodeWithTag("keyboard_space").performTouchInput { click() }
        rule.runOnIdle { assertEquals("hello ", typed.value) }
    }

    @Test fun t9SuggestionTapCommitsCandidate() {
        val text = mutableStateOf("helo")
        rule.setContent {
            CoverOSTheme {
                CoverT9SuggestionStrip(
                    textBeforeCursor = text.value,
                    onCandidateCommitted = { candidate ->
                        text.value = TextBuffer(text.value, text.value.length..text.value.length)
                            .commitCurrentWord(candidate).text
                    }
                )
            }
        }
        rule.onNodeWithTag("keyboard_t9_suggestions").assertExists()
        rule.onNodeWithText("hello").performTouchInput { click() }
        rule.runOnIdle { assertEquals("hello", text.value) }
    }

    @Test fun groupedT9DigitsShowHelloAsCandidate() {
        var committed: String? = null
        rule.setContent {
            CoverOSTheme {
                CoverT9SuggestionStrip(
                    textBeforeCursor = "",
                    predictiveDigits = "44-33-55-55-666",
                    onCandidateCommitted = { committed = it }
                )
            }
        }
        rule.onNodeWithText("hello").performTouchInput { click() }
        rule.runOnIdle { assertEquals("hello", committed) }
    }

    @Test fun t9PredictionToggleKeepsModeStateInHost() {
        val predictive = mutableStateOf(false)
        rule.setContent {
            CoverOSTheme {
                CoverT9PredictionToggle(
                    isPredictive = predictive.value,
                    onModeChanged = { predictive.value = it }
                )
            }
        }
        rule.onNodeWithTag("keyboard_t9_prediction_toggle")
            .assertContentDescriptionEquals("Predictive T9 off")
            .performTouchInput { click() }
        rule.runOnIdle { assertEquals(true, predictive.value) }
        rule.onNodeWithTag("keyboard_t9_prediction_toggle")
            .assertContentDescriptionEquals("Predictive T9 on")
    }

    @Test fun qwertySuggestionTapCommitsCandidate() {
        val text = mutableStateOf("helo")
        rule.setContent {
            CoverOSTheme {
                CoverCompactQwertyKeyboard(
                    onChar = { text.value += it },
                    onBackspace = { text.value = text.value.dropLast(1) },
                    onDone = {}, onClear = {}, textBeforeCursor = text.value,
                    onCandidateCommitted = { candidate ->
                        text.value = TextBuffer(text.value, text.value.length..text.value.length)
                            .commitCurrentWord(candidate).text
                    }
                )
            }
        }
        rule.onNodeWithText("hello").performTouchInput { click() }
        rule.runOnIdle { assertEquals("hello", text.value) }
    }

    @Test fun repeatingKeyFiresOnShortTap() {
        var presses = 0
        rule.setContent {
            CoverOSTheme {
                KeyboardKey(
                    label = "Delete", onClick = { presses++ }, repeatOnHold = true,
                    modifier = Modifier.testTag("repeat_key")
                )
            }
        }
        rule.onNodeWithTag("repeat_key").performTouchInput { click() }
        rule.runOnIdle { assertEquals(1, presses) }
    }

    @Test fun longPressDoesNotAlsoTypeLetter() {
        var taps = 0
        var longPresses = 0
        rule.setContent {
            CoverOSTheme {
                KeyboardKey(
                    label = "Q", onClick = { taps++ }, onLongPress = { longPresses++ },
                    modifier = Modifier.testTag("long_press_key")
                )
            }
        }
        rule.onNodeWithTag("long_press_key").performTouchInput { longClick(durationMillis = 500) }
        rule.runOnIdle {
            assertEquals(0, taps)
            assertEquals(1, longPresses)
        }
    }
}
