package com.tyejaedon.coverscreenos.ui.launcher

import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.tyejaedon.coverscreenos.datastore.KeyboardAnchor
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.keyboard.floris.ClamshellDockAnchor
import com.tyejaedon.coverscreenos.keyboard.floris.CoverEditorBridge
import com.tyejaedon.coverscreenos.keyboard.floris.CoverFlorisKeyboardSurface
import com.tyejaedon.coverscreenos.keyboard.floris.CoverLayoutMode
import com.tyejaedon.coverscreenos.keyboard.floris.CoverOverlayInputCallbacks
import com.tyejaedon.coverscreenos.keyboard.floris.FlorisKeyboardCustomization
import com.tyejaedon.coverscreenos.keyboard.floris.FlorisThemePalette
import com.tyejaedon.coverscreenos.keyboard.nlp.CoverKeyboardNlpManager
import kotlinx.coroutines.launch

@Composable
internal fun SearchFlorisKeyboardHost(
    query: String,
    onQueryChanged: (String) -> Unit,
    onDonePressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsStore = remember(context) { LauncherSettingsStore(context.applicationContext) }
    val settings by settingsStore.settings.collectAsState(initial = LauncherSettings())
    val coroutineScope = rememberCoroutineScope()

    var cursorPosition by remember(query) { mutableIntStateOf(query.length) }

    val bridge = remember(query, onQueryChanged, onDonePressed) {
        CoverEditorBridge(
            overlayCallbacks = object : CoverOverlayInputCallbacks {
                override fun onAppend(text: String) {
                    val safeCursor = cursorPosition.coerceIn(0, query.length)
                    val newQuery = StringBuilder(query).insert(safeCursor, text).toString()
                    cursorPosition = safeCursor + text.length
                    onQueryChanged(newQuery)
                }

                override fun onDeleteBackward() {
                    val safeCursor = cursorPosition.coerceIn(0, query.length)
                    if (safeCursor > 0) {
                        val newQuery = query.removeRange(safeCursor - 1, safeCursor)
                        cursorPosition = safeCursor - 1
                        onQueryChanged(newQuery)
                    }
                }

                override fun onDeletePreviousWord() {
                    val safeCursor = cursorPosition.coerceIn(0, query.length)
                    val beforeCursor = query.take(safeCursor)
                    val lastSpaceIndex = beforeCursor.trimEnd().lastIndexOf(' ')
                    val deleteStart = if (lastSpaceIndex == -1) 0 else lastSpaceIndex + 1
                    val newQuery = query.removeRange(deleteStart, safeCursor)
                    cursorPosition = deleteStart
                    onQueryChanged(newQuery)
                }

                override fun onMoveCursor(delta: Int) {
                    cursorPosition = (cursorPosition + delta).coerceIn(0, query.length)
                }

                override fun onCommitCandidate(candidate: String) {
                    val safeCursor = cursorPosition.coerceIn(0, query.length)
                    val before = query.take(safeCursor)
                    val wordLength = before.takeLastWhile { it.isLetterOrDigit() }.length
                    val replaceStart = (safeCursor - wordLength).coerceAtLeast(0)
                    val formatted = if (candidate.endsWith(" ")) candidate else "$candidate "
                    val newQuery = query.replaceRange(replaceStart, safeCursor, formatted)
                    cursorPosition = replaceStart + formatted.length
                    onQueryChanged(newQuery)
                }

                override fun onClear() {
                    cursorPosition = 0
                    onQueryChanged("")
                }

                override fun onDone() = onDonePressed()
                override fun getTextBeforeCursor(maxChars: Int) =
                    query.take(cursorPosition.coerceIn(0, query.length)).takeLast(maxChars)

                override fun getTextAfterCursor(maxChars: Int) =
                    query.drop(cursorPosition.coerceIn(0, query.length)).take(maxChars)

                override fun getFullText() = query
                override fun getCursorPosition() = cursorPosition.coerceIn(0, query.length)
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
    var nlpRevision by remember { mutableIntStateOf(0) }

    val beforeCursor = query.take(cursorPosition.coerceIn(0, query.length))
    val currentWord = beforeCursor.takeLastWhile { it.isLetterOrDigit() }

    LaunchedEffect(query, cursorPosition, activeLayoutMode, nlpRevision) {
        candidates = when {
            currentWord.isNotBlank() -> CoverKeyboardNlpManager.getCandidates(activeLayoutMode, currentWord)
            beforeCursor.isNotBlank() -> {
                val prev = beforeCursor.trimEnd().takeLastWhile { it.isLetter() }
                if (prev.isNotEmpty()) {
                    CoverKeyboardNlpManager.getNextWordSuggestions(prev)
                } else emptyList()
            }
            else -> emptyList()
        }
    }

    val dockAnchor = if (settings.keyboardOneHandedAnchor == KeyboardAnchor.Right) {
        ClamshellDockAnchor.RIGHT
    } else {
        ClamshellDockAnchor.LEFT
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
        customization = FlorisKeyboardCustomization(
            extraHeightDp = settings.keyboardExtraHeightDp,
            onExtraHeightChanged = { newDp ->
                coroutineScope.launch { settingsStore.setKeyboardExtraHeightDp(newDp) }
            },
            oneHandedEnabled = settings.keyboardOneHandedEnabled,
            onOneHandedToggle = {
                coroutineScope.launch {
                    settingsStore.setKeyboardOneHandedEnabled(!settings.keyboardOneHandedEnabled)
                }
            },
            dockAnchor = dockAnchor,
            onAnchorToggle = {
                val next = if (settings.keyboardOneHandedAnchor == KeyboardAnchor.Left) {
                    KeyboardAnchor.Right
                } else {
                    KeyboardAnchor.Left
                }
                coroutineScope.launch { settingsStore.setKeyboardOneHandedAnchor(next) }
            },
            suggestionStripVisible = settings.suggestionStripVisible,
            suggestionsEnabled = settings.suggestionStripVisible,
            autocorrectEnabled = settings.autocorrectEnabled,
            popupKeyPreviewEnabled = settings.popupKeyPreviewEnabled,
            imeOptions = EditorInfo.IME_ACTION_SEARCH,
            compactSymbolsLayout = true,
            includeClearKey = true,
            themePalette = FlorisThemePalette.AmoledCover
        ),
        textBeforeCursor = beforeCursor,
        selectedCandidateIndex = if (currentWord.isNotEmpty()) 0 else -1,
        onLearnCandidate = { _, word ->
            CoverKeyboardNlpManager.learnWord(word, context)
            nlpRevision++
        },
        onSuppressCandidate = { _, word ->
            CoverKeyboardNlpManager.suppressWord(word, context)
            nlpRevision++
        },
        onAutocorrectWord = CoverKeyboardNlpManager::autocorrectWord,
        t9Engine = CoverKeyboardNlpManager.t9Engine,
        glideDecoder = CoverKeyboardNlpManager.glideDecoder,
        modifier = modifier
    )
}
