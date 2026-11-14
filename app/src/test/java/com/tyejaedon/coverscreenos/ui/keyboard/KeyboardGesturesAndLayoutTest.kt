package com.tyejaedon.coverscreenos.ui.keyboard

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.KeyboardKey
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.SuggestionStrip
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardGesturesAndLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun slidingFingerOffKeyCancelsClick() {
        var taps = 0
        rule.setContent {
            CoverOSTheme {
                KeyboardKey(
                    label = "A",
                    onClick = { taps++ },
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

    @Test
    fun normalTapOnKeyInvokesClick() {
        var taps = 0
        rule.setContent {
            CoverOSTheme {
                KeyboardKey(
                    label = "A",
                    onClick = { taps++ },
                    modifier = Modifier.testTag("tap_key")
                )
            }
        }
        rule.onNodeWithTag("tap_key").performTouchInput {
            click()
        }
        rule.runOnIdle {
            assertEquals(1, taps)
        }
    }

    @Test
    fun symbolRowGridIncludesCommonSymbols() {
        rule.setContent {
            CoverOSTheme {
                CoverCompactQwertyKeyboard(
                    onChar = {},
                    onBackspace = {},
                    onDone = {},
                    onClear = {}
                )
            }
        }
        rule.onNodeWithTag("keyboard_symbols").performTouchInput { click() }
        val expectedSymbols = listOf("#", "%", "*", "+", "=", "!", "?", "'")
        expectedSymbols.forEachIndexed { index, symbol ->
            rule.onNodeWithTag("keyboard_symbol_$index").assertTextEquals(symbol)
        }
    }

    @Test
    fun accidentalTapOnClearDoesNotWipeBufferAndLongPressWipesWithUndo() {
        val typed = mutableStateOf("hello world")
        var clearCalled = 0
        rule.setContent {
            CoverOSTheme {
                CoverCompactQwertyKeyboard(
                    onChar = { typed.value += it },
                    onBackspace = { typed.value = typed.value.dropLast(1) },
                    onDone = {},
                    onClear = { clearCalled++; typed.value = "" },
                    textBeforeCursor = typed.value
                )
            }
        }
        // Accidental tap should NOT clear
        rule.onNodeWithTag("keyboard_clear").performTouchInput { click() }
        rule.runOnIdle {
            assertEquals("hello world", typed.value)
            assertEquals(0, clearCalled)
        }

        // Long press SHOULD clear
        rule.onNodeWithTag("keyboard_clear").performTouchInput { longClick(durationMillis = 500) }
        rule.runOnIdle {
            assertEquals("", typed.value)
            assertEquals(1, clearCalled)
        }

        // Tap after clear should undo
        rule.onNodeWithTag("keyboard_clear").performTouchInput { click() }
        rule.runOnIdle {
            assertEquals("hello world", typed.value)
        }
    }

    @Test
    fun suggestionStripSwipeDoesNotCommitCandidate() {
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
