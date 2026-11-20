package com.tyejaedon.coverscreenos.ui.keyboard

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tyejaedon.coverscreenos.keyboard.floris.CoverEditorBridge
import com.tyejaedon.coverscreenos.keyboard.floris.CoverFlorisKeyboardSurface
import com.tyejaedon.coverscreenos.keyboard.floris.CoverLayoutMode
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.KeyboardKey
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.SuggestionStrip
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.TextBuffer
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                val bridge = remember {
                    CoverEditorBridge(
                        overlayCallbacks = CoverBufferOverlayCallbacks(
                            getText = { typed.value },
                            setText = { typed.value = it }
                        )
                    )
                }
                CoverFlorisKeyboardSurface(bridge = bridge)
            }
        }
        rule.onNodeWithTag("keyboard_shift").performTouchInput { click() }
        rule.onNodeWithTag("keyboard_top_0").assertTextEquals("Q")
        rule.onNodeWithTag("keyboard_top_0").performTouchInput { click() }
        rule.runOnIdle { assertEquals("Q", typed.value) }
        rule.onNodeWithTag("keyboard_top_0").assertTextEquals("q")
        rule.onNodeWithTag("keyboard_symbols").performTouchInput { click() }
        rule.onNodeWithText("@").assertExists()
        rule.onNodeWithText("ABC").performTouchInput { click() }
        rule.onNodeWithTag("keyboard_top_0").assertTextEquals("q")
    }

    @Test fun enterTriggersDoneCallback() {
        var doneCount = 0
        rule.setContent {
            CoverOSTheme {
                val bridge = remember {
                    CoverEditorBridge(
                        overlayCallbacks = CoverBufferOverlayCallbacks(
                            getText = { "" },
                            setText = {},
                            onDoneAction = { doneCount++ }
                        )
                    )
                }
                CoverFlorisKeyboardSurface(bridge = bridge)
            }
        }
        rule.onNodeWithTag("keyboard_enter").performTouchInput { click() }
        rule.runOnIdle { assertEquals(1, doneCount) }
    }

    @Test fun t9ModeToggleNotifiesHost() {
        var currentMode = CoverLayoutMode.QWERTY
        rule.setContent {
            CoverOSTheme {
                val bridge = remember {
                    CoverEditorBridge(
                        overlayCallbacks = CoverBufferOverlayCallbacks(
                            getText = { "" },
                            setText = {}
                        )
                    )
                }
                CoverFlorisKeyboardSurface(
                    bridge = bridge,
                    onModeChanged = { currentMode = it }
                )
            }
        }
        rule.onNodeWithText("QWERTY").performTouchInput { click() }
        rule.runOnIdle { assertEquals(CoverLayoutMode.T9_PREDICTIVE, currentMode) }
        rule.onNodeWithText("T9").assertExists()
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
                val callbacks = remember {
                    CoverBufferOverlayCallbacks(
                        getText = { text.value },
                        setText = { text.value = it }
                    )
                }
                val bridge = remember(callbacks) {
                    CoverEditorBridge(overlayCallbacks = callbacks)
                }
                CoverFlorisKeyboardSurface(
                    bridge = bridge,
                    candidates = listOf("hello"),
                    onCandidateSelected = { callbacks.onCommitCandidate(it) }
                )
            }
        }
        rule.onNodeWithText("hello").performTouchInput { click() }
        rule.runOnIdle { assertEquals("hello ", text.value) }
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

    @Test fun slidingFingerOffKeyCancelsClick() {
        var taps = 0
        rule.setContent {
            CoverOSTheme {
                KeyboardKey(
                    label = "A", onClick = { taps++ },
                    modifier = Modifier.testTag("slide_key")
                )
            }
        }
        rule.onNodeWithTag("slide_key").performTouchInput {
            down(center)
            moveTo(Offset(center.x + 300f, center.y + 300f))
            up()
        }
        rule.runOnIdle {
            assertEquals(0, taps)
        }
    }

    @Test fun symbolRowGridIncludesCommonSymbols() {
        rule.setContent {
            CoverOSTheme {
                val bridge = remember {
                    CoverEditorBridge(
                        overlayCallbacks = CoverBufferOverlayCallbacks(
                            getText = { "" },
                            setText = {}
                        )
                    )
                }
                CoverFlorisKeyboardSurface(bridge = bridge)
            }
        }
        rule.onNodeWithTag("keyboard_symbols").performTouchInput { click() }
        val expectedSymbols = listOf("#", "%", "*", "+", "!", "?", "'", "@")
        expectedSymbols.forEach { symbol ->
            rule.onNodeWithText(symbol).assertExists()
        }
    }

    @Test fun suggestionStripSwipeDoesNotCommitCandidate() {
        var committed: String? = null
        rule.setContent {
            CoverOSTheme {
                SuggestionStrip(
                    suggestions = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh"),
                    onSelected = { _, word -> committed = word }
                )
            }
        }
        rule.onNodeWithText("first").performTouchInput {
            swipeLeft()
        }
        rule.runOnIdle {
            assertNull(committed)
        }
    }
}
