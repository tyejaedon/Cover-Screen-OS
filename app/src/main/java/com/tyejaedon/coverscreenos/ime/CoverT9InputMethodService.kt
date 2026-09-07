package com.tyejaedon.coverscreenos.ime

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme

class CoverT9InputMethodService : InputMethodService() {

    private val t9Engine = T9Engine()
    private var fieldProfile: EditorFieldProfile = EditorFieldProfile(isNumeric = false, isSensitive = false)
    private var activeEditorInfo: EditorInfo? = null
    private var uiState by mutableStateOf(CoverT9ImeUiState())

    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                CoverOSTheme {
                    CoverT9Keyboard(
                        uiState = uiState,
                        onDigitPressed = ::handleDigitPressed,
                        onZeroOrSpacePressed = ::handleZeroOrSpacePressed,
                        onBackspacePressed = ::handleBackspacePressed,
                        onClearPressed = ::handleClearPressed,
                        onEnterPressed = ::handleEnterPressed,
                        onHidePressed = { requestHideSelf(0) }
                    )
                }
            }
        }
    }

    // Cover displays have limited vertical space; keep IME compact instead of extract/fullscreen mode.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        activeEditorInfo = attribute
        fieldProfile = EditorFieldProfile.from(attribute)
        uiState = uiState.copy(
            isNumeric = fieldProfile.isNumeric,
            isSensitive = fieldProfile.isSensitive
        )

        if (!restarting) {
            t9Engine.reset()
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        t9Engine.reset()
        activeEditorInfo = null
        fieldProfile = EditorFieldProfile(isNumeric = false, isSensitive = false)
        uiState = CoverT9ImeUiState()
    }

    private fun handleDigitPressed(digit: Char, letters: String) {
        val action = t9Engine.onTap(
            digit = digit,
            letters = letters,
            nowElapsedMs = SystemClock.elapsedRealtime(),
            forceDigit = fieldProfile.isNumeric
        )
        applyCommitAction(action)
    }

    private fun handleZeroOrSpacePressed() {
        t9Engine.reset()
        val inputConnection = currentInputConnection ?: return
        val text = if (fieldProfile.isNumeric) "0" else " "
        inputConnection.commitText(text, 1)
    }

    private fun handleBackspacePressed() {
        t9Engine.reset()
        val inputConnection = currentInputConnection ?: return
        val selectedText = inputConnection.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            inputConnection.commitText("", 1)
            return
        }
        inputConnection.deleteSurroundingText(1, 0)
    }

    private fun handleClearPressed() {
        t9Engine.reset()
        val inputConnection = currentInputConnection ?: return
        val selectedText = inputConnection.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            inputConnection.commitText("", 1)
            return
        }
        inputConnection.deleteSurroundingText(CLEAR_BEFORE_CURSOR_LIMIT_CHARS, 0)
    }

    private fun handleEnterPressed() {
        t9Engine.reset()
        val inputConnection = currentInputConnection ?: return
        val imeAction = activeEditorInfo
            ?.imeOptions
            ?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_UNSPECIFIED

        val performedEditorAction = when (imeAction) {
            EditorInfo.IME_ACTION_NONE,
            EditorInfo.IME_ACTION_UNSPECIFIED -> false
            else -> inputConnection.performEditorAction(imeAction)
        }

        if (!performedEditorAction) {
            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun applyCommitAction(action: T9CommitAction) {
        val inputConnection = currentInputConnection
        if (inputConnection == null) {
            Log.w(IME_LOG_TAG, "No active input connection while committing T9 action.")
            return
        }

        when (action) {
            is T9CommitAction.Append -> {
                inputConnection.commitText(action.text, 1)
            }

            is T9CommitAction.ReplacePrevious -> {
                val selectedText = inputConnection.getSelectedText(0)
                if (selectedText.isNullOrEmpty()) {
                    inputConnection.deleteSurroundingText(1, 0)
                }
                inputConnection.commitText(action.text, 1)
            }
        }
    }
}

private data class CoverT9ImeUiState(
    val isNumeric: Boolean = false,
    val isSensitive: Boolean = false
)

private const val IME_LOG_TAG = "CoverT9IME"
private const val CLEAR_BEFORE_CURSOR_LIMIT_CHARS = 4096

@Composable
private fun CoverT9Keyboard(
    uiState: CoverT9ImeUiState,
    onDigitPressed: (digit: Char, letters: String) -> Unit,
    onZeroOrSpacePressed: () -> Unit,
    onBackspacePressed: () -> Unit,
    onClearPressed: () -> Unit,
    onEnterPressed: () -> Unit,
    onHidePressed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "CoverScreenOS T9",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
        )
        Text(
            text = when {
                uiState.isNumeric -> "Numeric field"
                uiState.isSensitive -> "Sensitive field"
                else -> "Text field"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        T9_KEY_LAYOUT_ROWS.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { key ->
                    Button(
                        onClick = { onDigitPressed(key.digit, key.letters) },
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(key.digit.toString())
                            if (key.letters.isNotEmpty() && !uiState.isNumeric) {
                                Text(
                                    text = key.letters,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onClearPressed,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
            ) {
                Text("CLR")
            }

            Button(
                onClick = onZeroOrSpacePressed,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
            ) {
                Text(if (uiState.isNumeric) "0" else "SPACE")
            }

            OutlinedButton(
                onClick = onBackspacePressed,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace"
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = onEnterPressed,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text("ENTER")
            }

            OutlinedButton(
                onClick = onHidePressed,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardHide,
                    contentDescription = "Hide keyboard"
                )
            }
        }
    }
}

