package com.tyejaedon.coverscreenos.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
 import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tyejaedon.coverscreenos.datastore.KeyboardStrategy
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import com.tyejaedon.coverscreenos.ui.launcher.CoverAppGridOverlay
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverAppGridOverlayInputModeInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `default T9 mode shows keypad and no IME-padding container`() {
        launchOverlay(initialMode = KeyboardStrategy.T9)
        navigateToSearchWidgetPage()

        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_INPUT_MODE_LABEL)
            .assertExists()
            .assertTextContains("cover keyboard")
        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_IME_PICKER_BUTTON)
            .assertExists()
        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_T9_KEYPAD_ROOT)
            .assertExists()
        composeRule.onNodeWithTag(
            CoverSearchUiTestTags.SEARCH_SYSTEM_IME_FIELD,
            useUnmergedTree = true
        ).assertDoesNotExist()
        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_CONTENT_CONTAINER_NO_IME_PADDING)
            .assertExists()
        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_CONTENT_CONTAINER_WITH_IME_PADDING)
            .assertDoesNotExist()
    }

    @Test
    fun `switching to system IME updates mode and enables IME-padding container`() {
        val inputModeState = launchOverlay(initialMode = KeyboardStrategy.T9)
        navigateToSearchWidgetPage()

        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_INPUT_MODE_TOGGLE_BUTTON)
            .assertExists()
            .performClick()

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(KeyboardStrategy.SYSTEM_IME, inputModeState.value)
        }

        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_INPUT_MODE_LABEL)
            .assertTextContains("system keyboard")
        composeRule.onNodeWithTag(
            CoverSearchUiTestTags.SEARCH_SYSTEM_IME_FIELD,
            useUnmergedTree = true
        ).assertExists()
        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_T9_KEYPAD_ROOT)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_CONTENT_CONTAINER_WITH_IME_PADDING)
            .assertExists()
        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_CONTENT_CONTAINER_NO_IME_PADDING)
            .assertDoesNotExist()
    }

    @Test
    fun `switching back to T9 restores keypad and removes IME-padding container`() {
        val inputModeState = launchOverlay(initialMode = KeyboardStrategy.T9)
        navigateToSearchWidgetPage()

        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_INPUT_MODE_TOGGLE_BUTTON).performClick()
        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_INPUT_MODE_TOGGLE_BUTTON).performClick()

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(KeyboardStrategy.T9, inputModeState.value)
        }

        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_INPUT_MODE_LABEL)
            .assertTextContains("cover keyboard")
        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_T9_KEYPAD_ROOT)
            .assertExists()
        composeRule.onNodeWithTag(
            CoverSearchUiTestTags.SEARCH_SYSTEM_IME_FIELD,
            useUnmergedTree = true
        ).assertDoesNotExist()
        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_CONTENT_CONTAINER_NO_IME_PADDING)
            .assertExists()
    }

    @Test
    fun `widget tile blocks horizontal navigation until returning to lockscreen`() {
        launchOverlay(initialMode = KeyboardStrategy.T9)
        navigateToSearchWidgetPage()

        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_WIDGET_GRID_PAGE)
            .performTouchInput { swipeLeft() }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_WIDGET_GRID_PAGE)
            .assertExists()

        returnToLockscreenFromWidgetPage()

        composeRule.onNodeWithTag(CoverSearchUiTestTags.OVERLAY_PAGER)
            .performTouchInput { swipeRight() }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CoverSearchUiTestTags.LOCKSCREEN_TILE_PAGE)
            .assertDoesNotExist()
    }

    @Test
    fun `dismiss button hides T9 keypad so other widgets can be interacted with`() {
        launchOverlay(initialMode = KeyboardStrategy.T9)
        navigateToSearchWidgetPage()

        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_T9_KEYPAD_ROOT)
            .assertExists()

        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_DISMISS_INPUT_BUTTON)
            .assertExists()
            .performClick()

        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_T9_KEYPAD_ROOT)
            .assertDoesNotExist()
    }

    @Test
    fun `dismiss button clears system IME field focus`() {
        launchOverlay(initialMode = KeyboardStrategy.SYSTEM_IME)
        navigateToSearchWidgetPage()

        composeRule.onNodeWithTag(
            CoverSearchUiTestTags.SEARCH_SYSTEM_IME_FIELD,
            useUnmergedTree = true
        )
            .assertExists()
            .performClick()
            .assertIsFocused()

        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_DISMISS_INPUT_BUTTON)
            .assertExists()
            .performClick()

        composeRule.waitForIdle()

        composeRule.onNodeWithTag(
            CoverSearchUiTestTags.SEARCH_SYSTEM_IME_FIELD,
            useUnmergedTree = true
        ).assertIsNotFocused()
    }

    private fun launchOverlay(initialMode: KeyboardStrategy): MutableState<KeyboardStrategy> {
        val inputModeStateRef = AtomicReference<MutableState<KeyboardStrategy>>()
        val repository = PackageManagerAppScannerRepository(composeRule.activity.applicationContext)

        composeRule.setContent {
            val inputModeState = remember { mutableStateOf(initialMode) }
            inputModeStateRef.set(inputModeState)

            CoverOSTheme {
                CoverAppGridOverlay(
                    repository = repository,
                    onAppSelected = {},
                    isDeviceLocked = false,
                    keyboardStrategy = inputModeState.value,
                    onKeyboardStrategyChanged = { nextMode ->
                        inputModeState.value = nextMode
                    }
                )
            }
        }

        composeRule.waitForIdle()
        return inputModeStateRef.get()
    }

    private fun navigateToSearchWidgetPage() {
        composeRule.onNodeWithTag(CoverSearchUiTestTags.LOCKSCREEN_TILE_PAGE)
            .performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_WIDGET_GRID_PAGE)
            .assertExists()
    }

    private fun returnToLockscreenFromWidgetPage() {
        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_WIDGET_GRID_PAGE)
            .performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CoverSearchUiTestTags.LOCKSCREEN_TILE_PAGE)
            .assertExists()
    }
}



