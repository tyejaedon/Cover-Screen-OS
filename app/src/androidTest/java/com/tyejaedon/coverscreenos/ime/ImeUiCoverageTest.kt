package com.tyejaedon.coverscreenos.ime

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImeUiCoverageTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun qwertyT9NumberAndSymbolsCanBeSelectedWithoutLeavingIme() {
        val state = mutableStateOf(CoverImeUiState())
        composeRule.setContent {
            CoverOSTheme {
                CoverKeyboardHost(
                    state = state.value,
                    onLayoutSelected = { state.value = state.value.copy(layout = it) },
                    onShiftPressed = {},
                    onCharacterPressed = {},
                    onDigitPressed = { _, _ -> },
                    onBackspacePressed = {},
                    onClearPressed = {},
                    onEnterPressed = {},
                    onHidePressed = {}
                )
            }
        }

        composeRule.onNodeWithTag("ime_qwerty_row_0").assertExists()
        composeRule.onNodeWithTag("ime_layout_t9").performTouchInput { click() }
        composeRule.onNodeWithTag("ime_t9_row_0").assertExists()
        composeRule.onNodeWithTag("ime_qwerty_row_0").assertDoesNotExist()
        composeRule.onNodeWithTag("ime_layout_number").performTouchInput { click() }
        composeRule.onNodeWithTag("ime_number_row_0").assertExists()
        composeRule.onNodeWithTag("ime_layout_symbols").performTouchInput { click() }
        composeRule.onNodeWithTag("ime_symbols_row_0").assertExists()
        composeRule.onNodeWithTag("ime_layout_qwerty").performTouchInput { click() }
        composeRule.onNodeWithTag("ime_qwerty_row_0").assertExists()
    }
}
