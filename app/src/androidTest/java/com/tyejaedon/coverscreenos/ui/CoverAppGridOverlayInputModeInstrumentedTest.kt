package com.tyejaedon.coverscreenos.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tyejaedon.coverscreenos.datastore.SearchInputMode
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
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
        launchOverlay(initialMode = SearchInputMode.T9)

        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_INPUT_MODE_LABEL)
            .assertExists()
            .assertTextContains("T9")
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
        val inputModeState = launchOverlay(initialMode = SearchInputMode.T9)

        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_INPUT_MODE_TOGGLE_BUTTON)
            .assertExists()
            .performClick()

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(SearchInputMode.SYSTEM_IME, inputModeState.value)
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
        val inputModeState = launchOverlay(initialMode = SearchInputMode.T9)

        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_INPUT_MODE_TOGGLE_BUTTON).performClick()
        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_INPUT_MODE_TOGGLE_BUTTON).performClick()

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(SearchInputMode.T9, inputModeState.value)
        }

        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_INPUT_MODE_LABEL)
            .assertTextContains("T9")
        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_T9_KEYPAD_ROOT)
            .assertExists()
        composeRule.onNodeWithTag(
            CoverSearchUiTestTags.SEARCH_SYSTEM_IME_FIELD,
            useUnmergedTree = true
        ).assertDoesNotExist()
        composeRule.onNodeWithTag(CoverSearchUiTestTags.SEARCH_CONTENT_CONTAINER_NO_IME_PADDING)
            .assertExists()
    }

    private fun launchOverlay(initialMode: SearchInputMode): MutableState<SearchInputMode> {
        val inputModeStateRef = AtomicReference<MutableState<SearchInputMode>>()
        val repository = PackageManagerAppScannerRepository(composeRule.activity.applicationContext)

        composeRule.setContent {
            val inputModeState = remember { mutableStateOf(initialMode) }
            inputModeStateRef.set(inputModeState)

            CoverOSTheme {
                CoverAppGridOverlay(
                    repository = repository,
                    onAppSelected = {},
                    isDeviceLocked = false,
                    searchInputMode = inputModeState.value,
                    onSearchInputModeChanged = { nextMode ->
                        inputModeState.value = nextMode
                    }
                )
            }
        }

        composeRule.waitForIdle()
        return inputModeStateRef.get()
    }
}



