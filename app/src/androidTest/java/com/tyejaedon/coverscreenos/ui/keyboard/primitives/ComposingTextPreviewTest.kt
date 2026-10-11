package com.tyejaedon.coverscreenos.ui.keyboard.primitives

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase C: expandable/scrollable typed-text visibility. Pins the collapsed/expanded
 * tap-to-toggle affordance and the "no regression for short input" acceptance bar.
 */
@RunWith(AndroidJUnit4::class)
class ComposingTextPreviewTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val longMessage = (1..40).joinToString(" ") { "word$it" }

    @Test fun shortTextShowsNoExpandAffordanceOrLineCounter() {
        rule.setContent {
            CoverOSTheme {
                ComposingTextPreview(
                    text = "hello world",
                    modifier = Modifier.testTag("preview")
                )
            }
        }
        rule.onNodeWithText("hello world").assertExists()
        rule.onNodeWithText("tap to expand", substring = true).assertDoesNotExist()
    }

    @Test fun longTextShowsExpandAffordanceAndTogglesOnTap() {
        rule.setContent {
            CoverOSTheme {
                ComposingTextPreview(
                    text = longMessage,
                    modifier = Modifier.testTag("preview")
                )
            }
        }
        rule.onNodeWithText("tap to expand", substring = true).assertExists()
        rule.onNode(hasClickAction()).performClick()
        rule.onNodeWithText("tap to collapse", substring = true).assertExists()
        rule.onNode(hasClickAction()).performClick()
        rule.onNodeWithText("tap to expand", substring = true).assertExists()
    }

    @Test fun charCounterReflectsTypedLength() {
        rule.setContent {
            CoverOSTheme {
                ComposingTextPreview(text = "hola", modifier = Modifier.testTag("preview"))
            }
        }
        rule.onNodeWithText("4 chars", substring = true).assertExists()
    }

    @Test fun emptyTextShowsPlaceholderAndNoCounter() {
        rule.setContent {
            CoverOSTheme {
                ComposingTextPreview(
                    text = "",
                    placeholder = "Type or tap keys...",
                    modifier = Modifier.testTag("preview")
                )
            }
        }
        rule.onNodeWithText("Type or tap keys...").assertExists()
        rule.onNodeWithText("chars", substring = true).assertDoesNotExist()
    }
}
