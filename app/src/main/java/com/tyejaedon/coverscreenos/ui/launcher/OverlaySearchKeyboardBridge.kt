package com.tyejaedon.coverscreenos.ui.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tyejaedon.coverscreenos.keyboard.floris.CoverEditorBridge
import com.tyejaedon.coverscreenos.keyboard.floris.CoverFlorisKeyboardSurface
import com.tyejaedon.coverscreenos.keyboard.floris.CoverLayoutMode
import com.tyejaedon.coverscreenos.keyboard.floris.CoverOverlayInputCallbacks
import com.tyejaedon.coverscreenos.keyboard.nlp.CoverKeyboardNlpManager

@Composable
internal fun SearchFlorisKeyboardHost(
    query: String,
    onQueryChanged: (String) -> Unit,
    onDonePressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var cursorPosition by remember(query) { mutableIntStateOf(query.length) }

    val bridge = remember(query, onQueryChanged, onDonePressed) {
        CoverEditorBridge(
            overlayCallbacks = object : CoverOverlayInputCallbacks {
                override fun onAppend(text: String) {
                    val newQuery = StringBuilder(query).insert(cursorPosition, text).toString()
                    cursorPosition += text.length
                    onQueryChanged(newQuery)
                }
                override fun onDeleteBackward() {
                    if (cursorPosition > 0) {
                        val newQuery = query.removeRange(cursorPosition - 1, cursorPosition)
                        cursorPosition -= 1
                        onQueryChanged(newQuery)
                    }
                }
                override fun onDeletePreviousWord() {
                    val beforeCursor = query.take(cursorPosition)
                    val lastSpaceIndex = beforeCursor.trimEnd().lastIndexOf(' ')
                    val deleteStart = if (lastSpaceIndex == -1) 0 else lastSpaceIndex + 1
                    val newQuery = query.removeRange(deleteStart, cursorPosition)
                    cursorPosition = deleteStart
                    onQueryChanged(newQuery)
                }
                override fun onMoveCursor(delta: Int) {
                    cursorPosition = (cursorPosition + delta).coerceIn(0, query.length)
                }
                override fun onCommitCandidate(candidate: String) {
                    val before = query.take(cursorPosition)
                    val wordLength = before.takeLastWhile { it.isLetterOrDigit() }.length
                    val replaceStart = cursorPosition - wordLength
                    val newQuery = query.replaceRange(replaceStart, cursorPosition, "$candidate ")
                    cursorPosition = replaceStart + candidate.length + 1
                    onQueryChanged(newQuery)
                }
                override fun onClear() {
                    cursorPosition = 0
                    onQueryChanged("")
                }
                override fun onDone() = onDonePressed()
                override fun getTextBeforeCursor(maxChars: Int) = query.take(cursorPosition).takeLast(maxChars)
                override fun getTextAfterCursor(maxChars: Int) = query.drop(cursorPosition).take(maxChars)
                override fun getFullText() = query
                override fun getCursorPosition() = cursorPosition
                override fun setCursorPosition(newPos: Int) {
                    cursorPosition = newPos.coerceIn(0, query.length)
                }
                override fun replaceText(range: IntRange, text: String, newCursor: Int) {
                    val safeStart = range.first.coerceIn(0, query.length)
                    val safeEnd = (range.last + 1).coerceIn(0, query.length)
                    if (safeStart <= safeEnd) {
                        val newQuery = query.replaceRange(safeStart, safeEnd, text)
                        cursorPosition = newCursor.coerceIn(0, newQuery.length)
                        onQueryChanged(newQuery)
                    }
                }
            }
        )
    }

    var activeLayoutMode by remember { mutableStateOf(CoverLayoutMode.QWERTY) }
    var candidates by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(query, cursorPosition, activeLayoutMode) {
        val word = bridge.getSurroundingText(30, 0).textBefore.takeLastWhile { it.isLetterOrDigit() }
        candidates = if (word.isNotBlank()) {
            CoverKeyboardNlpManager.getCandidates(activeLayoutMode, word)
        } else emptyList()
    }

    CoverFlorisKeyboardSurface(
        bridge = bridge,
        initialMode = activeLayoutMode,
        candidates = candidates,
        onCandidateSelected = { candidate ->
            bridge.commitCandidate(candidate)
            candidates = emptyList()
        },
        onDismissRequest = onDonePressed,
        onModeChanged = { activeLayoutMode = it },
        modifier = modifier
    )
}
