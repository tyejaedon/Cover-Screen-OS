package com.tyejaedon.coverscreenos.ui.keyboard

import android.view.inputmethod.EditorInfo
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tyejaedon.coverscreenos.datastore.KeyboardAnchor
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.HapticTier
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.KeyboardKey
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.KeyboardKeyColors
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.OneHandedKeyboardBox
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.ResizableKeyboardSurfaceControls
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.SuggestionStrip
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.rememberHapticPerformer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal enum class ShiftMode { Off, Once, Locked }

internal fun nextShiftMode(current: ShiftMode, lastTap: Long, now: Long): ShiftMode =
    when {
        current == ShiftMode.Locked -> ShiftMode.Off
        current == ShiftMode.Once && now - lastTap in 0..300 -> ShiftMode.Locked
        current == ShiftMode.Once -> ShiftMode.Off
        else -> ShiftMode.Once
    }

internal fun shouldAutoCap(text: String): Boolean =
    text.isBlank() || Regex("[.!?]\\s*$").containsMatchIn(text)

private const val KEY_ROW_COUNT = 4
private val KEY_ROW_SPACING = 4.dp

/** Height the four key rows want before they shrink to fit the screen. */
internal fun keyRowsHeight(keyHeight: Dp): Dp =
    keyHeight * KEY_ROW_COUNT + KEY_ROW_SPACING * (KEY_ROW_COUNT - 1)

internal fun previousWordLength(textBeforeCursor: String): Int =
    Regex("\\S+\\s*$").find(textBeforeCursor)?.value?.length ?: 0

/**
 * Shared QWERTY for launcher search and the cover overlay. Hosts with an
 * editable buffer should supply [textBeforeCursor] and [onMoveCursor] to enable
 * accurate predictions and space-drag cursor motion.
 */
@Composable
internal fun CoverCompactQwertyKeyboard(
    onChar: (Char) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    imeOptions: Int = EditorInfo.IME_ACTION_DONE,
    textBeforeCursor: String? = null,
    onMoveCursor: ((Int) -> Unit)? = null,
    onDeleteWord: (() -> Unit)? = null,
    suggestionsEnabled: Boolean = true,
    onCandidateCommitted: ((String) -> Unit)? = null,
    predictiveDigits: String? = null,
    // Phase H: optional slots so hosts with accessibility-node clipboard access (the cover
    // overlay path) can add copy/cut/paste without this shared primitive needing to know
    // about accessibility services. [clipboardActions] goes at the end of the top control
    // row (keep it small); [clipboardHistory] goes just below that row.
    clipboardActions: (@Composable () -> Unit)? = null,
    clipboardHistory: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val contactRevision = rememberContactSuggestionRevision(suggestionsEnabled)
    val dictionary = remember(context, suggestionsEnabled, contactRevision) {
        if (suggestionsEnabled) WordDictionary.fromContext(context) else null
    }
    // Phase G: user-facing customization toggles (sound is wired at the KeyboardKey
    // primitive level; suggestion-strip visibility, autocorrect, and popup preview are
    // applied here).
    val settingsStore = remember(context) { LauncherSettingsStore(context.applicationContext) }
    val customization by settingsStore.settings.collectAsState(initial = LauncherSettings())
    val coroutineScope = rememberCoroutineScope()
    // Phase D: resizable surface. The auto-computed [44, 64] range becomes the floor;
    // the persisted extra-height preference (0-24dp) stacks on top, up to an 88dp ceiling.
    val baseKeyHeight = (LocalConfiguration.current.screenHeightDp / 8f).coerceIn(44f, 64f)
    val keyHeight = (baseKeyHeight + customization.keyboardExtraHeightDp).coerceIn(44f, 88f).dp
    val density = LocalDensity.current
    val resizeHapticPerformer = rememberHapticPerformer(HapticTier.Standard)
    var internalText by remember { mutableStateOf("") }
    var shift by remember { mutableStateOf(ShiftMode.Off) }
    var lastShiftTap by remember { mutableLongStateOf(-1000L) }
    var symbols by remember { mutableStateOf(false) }
    var punctuationMenu by remember { mutableStateOf(false) }
    var cursorDragPixels by remember { mutableStateOf(0f) }
    var dictionaryRevision by remember { mutableIntStateOf(0) }
    val before = textBeforeCursor ?: internalText
    val word = before.takeLastWhile { it.isLetter() }
    val candidates = remember(dictionary, before, predictiveDigits, dictionaryRevision, contactRevision) {
        if (dictionary == null) emptyList()
        else if (!predictiveDigits.isNullOrEmpty()) dictionary.suggestT9(predictiveDigits)
        else if (word.isNotEmpty()) dictionary.suggest(word)
        else dictionary.nextWords(before.trimEnd().takeLastWhile { it.isLetter() })
    }

    fun emit(char: Char) {
        internalText += char
        onChar(char)
        if (char.isLetter() && shift == ShiftMode.Once) shift = ShiftMode.Off
    }

    fun backspace() {
        if (internalText.isNotEmpty()) internalText = internalText.dropLast(1)
        onBackspace()
    }

    fun commit(candidate: String, trailingSpace: Boolean = false) {
        if (onCandidateCommitted != null) {
            onCandidateCommitted(candidate + if (trailingSpace) " " else "")
            internalText = before.dropLast(word.length) + candidate + if (trailingSpace) " " else ""
        } else {
            repeat(word.length) { backspace() }
            (candidate + if (trailingSpace) " " else "").forEach(::emit)
        }
    }

    fun pressSpace() {
        if (customization.autocorrectEnabled && suggestionsEnabled && word.isNotEmpty()) {
            val corrected = dictionary?.correct(word) ?: word
            if (corrected != word) {
                commit(corrected, trailingSpace = true)
                return
            }
        }
        emit(' ')
    }

    @Composable
    fun RowScope.key(
        label: String,
        weight: Float = 1f,
        description: String = label,
        tier: HapticTier = HapticTier.Standard,
        tag: String = "keyboard_key_$label",
        preview: Boolean = false,
        longPress: (() -> Unit)? = null,
        repeat: Boolean = false,
        drag: ((Float) -> Unit)? = null,
        accent: Boolean = false,
        action: () -> Unit
    ) {
        KeyboardKey(
            label = label,
            onClick = action,
            onLongPress = longPress,
            onHorizontalDrag = drag,
            repeatOnHold = repeat,
            repeatInitialDelayMillis = 350,
            repeatIntervalMillis = 60,
            longPressThresholdMillis = 400,
            contentDescription = description,
            showKeyPreview = preview && customization.popupKeyPreviewEnabled,
            hapticTier = tier,
            colors = if (accent) KeyboardKeyColors.accent() else KeyboardKeyColors.default(),
            modifier = Modifier.weight(weight).fillMaxHeight().testTag(tag)
        )
    }

    OneHandedKeyboardBox(
        oneHandedEnabled = customization.keyboardOneHandedEnabled,
        anchor = customization.keyboardOneHandedAnchor,
        modifier = modifier
    ) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ResizableKeyboardSurfaceControls(
            extraHeightDp = customization.keyboardExtraHeightDp,
            onExtraHeightChanged = { newExtraDp ->
                coroutineScope.launch { settingsStore.setKeyboardExtraHeightDp(newExtraDp) }
            },
            oneHandedEnabled = customization.keyboardOneHandedEnabled,
            onOneHandedToggle = {
                coroutineScope.launch {
                    settingsStore.setKeyboardOneHandedEnabled(!customization.keyboardOneHandedEnabled)
                }
            },
            oneHandedAnchor = customization.keyboardOneHandedAnchor,
            onAnchorToggle = {
                val next = if (customization.keyboardOneHandedAnchor == KeyboardAnchor.Left) {
                    KeyboardAnchor.Right
                } else {
                    KeyboardAnchor.Left
                }
                coroutineScope.launch { settingsStore.setKeyboardOneHandedAnchor(next) }
            },
            onHaptic = resizeHapticPerformer,
            modifier = Modifier.testTag("keyboard_resize_controls"),
            trailingContent = clipboardActions?.let { actions -> { actions() } }
        )
        clipboardHistory?.invoke()
        if (suggestionsEnabled && customization.suggestionStripVisible) {
            SuggestionStrip(
                suggestions = candidates,
                selectedIndex = if (word.isNotEmpty()) 0 else -1,
                onSelected = { _, candidate ->
                    if (dictionary?.isAvailable(candidate) == true) commit(candidate)
                    else {
                        Log.w("CoverKeyboard", "Selected suggestion is no longer available")
                        dictionaryRevision++
                    }
                },
                onLearn = { _, candidate ->
                    if (dictionary?.isAvailable(candidate) == true) dictionary.learn(candidate)
                    dictionaryRevision++
                },
                onSuppress = { _, candidate ->
                    if (dictionary?.isAvailable(candidate) == true) dictionary.suppress(candidate)
                    dictionaryRevision++
                },
                modifier = Modifier.testTag("keyboard_suggestions")
            )
        }
        // All four key rows share one box. They ask for [keyHeight] each but shrink evenly
        // when the screen is short, so the bottom row is never squished or cut off.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = keyRowsHeight(keyHeight)),
            verticalArrangement = Arrangement.spacedBy(KEY_ROW_SPACING)
        ) {
        val rowModifier = Modifier.fillMaxWidth().weight(1f)
        Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (if (symbols) "1234567890" else "qwertyuiop").forEachIndexed { index, char ->
                val letter = if (!symbols && (shift != ShiftMode.Off ||
                        (shift == ShiftMode.Off && shouldAutoCap(before))))
                    char.uppercaseChar() else char
                key(letter.toString(), preview = true, tag = "keyboard_top_$index",
                    longPress = if (symbols) null else { { emit("1234567890"[index]) } }) {
                    emit(letter)
                }
            }
        }
        Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (if (symbols) "-/:;()${'$'}&@\"" else "asdfghjkl").forEachIndexed { index, char ->
                val letter = if (!symbols && (shift != ShiftMode.Off ||
                        (shift == ShiftMode.Off && shouldAutoCap(before))))
                    char.uppercaseChar() else char
                key(letter.toString(), tag = "keyboard_middle_$index", preview = true) { emit(letter) }
            }
        }
        Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (symbols) {
                ".,?!'".forEachIndexed { index, char ->
                    key(char.toString(), tag = "keyboard_symbol_$index", preview = true) { emit(char) }
                }
            } else {
                key(if (shift == ShiftMode.Locked) "⇪" else "⇧",
                    weight = 1.2f, description = "Shift",
                    tier = HapticTier.Strong, tag = "keyboard_shift", accent = shift != ShiftMode.Off
                ) {
                    val now = android.os.SystemClock.uptimeMillis()
                    shift = nextShiftMode(shift, lastShiftTap, now)
                    lastShiftTap = now
                }
                "zxcvbnm".forEachIndexed { index, char ->
                    val letter = if (shift != ShiftMode.Off ||
                        (shift == ShiftMode.Off && shouldAutoCap(before))) char.uppercaseChar() else char
                    key(letter.toString(), tag = "keyboard_lower_$index", preview = true) { emit(letter) }
                }
            }
            KeyboardKey(
                icon = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Backspace",
                onClick = ::backspace,
                repeatOnHold = true,
                repeatInitialDelayMillis = 350,
                repeatIntervalMillis = 60,
                onHorizontalDragStart = { displacement ->
                    if (displacement < 0) {
                        if (onDeleteWord != null) onDeleteWord()
                        else repeat(previousWordLength(before)) { backspace() }
                    }
                },
                hapticTier = HapticTier.Standard,
                modifier = Modifier.weight(1.3f).fillMaxHeight().testTag("keyboard_backspace")
            )
        }
        Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            key(if (symbols) "ABC" else "?123", weight = 1.5f,
                tier = HapticTier.Strong, tag = "keyboard_symbols") {
                symbols = !symbols
            }
            KeyboardKey(
                icon = Icons.Default.Clear, contentDescription = "Clear",
                onClick = { internalText = ""; onClear() },
                hapticTier = HapticTier.Strong,
                modifier = Modifier.weight(0.9f).fillMaxHeight().testTag("keyboard_clear")
            )
            key(",", weight = 0.8f, tag = "keyboard_comma") { emit(',') }
            KeyboardKey(
                icon = Icons.Default.SpaceBar, contentDescription = "Space",
                onClick = ::pressSpace,
                onHorizontalDrag = { delta ->
                    cursorDragPixels += delta
                    val step = with(density) { 24.dp.toPx() }
                    val chars = (cursorDragPixels / step).roundToInt()
                    if (chars != 0) {
                        onMoveCursor?.invoke(chars)
                        cursorDragPixels -= chars * step
                    }
                },
                hapticTier = HapticTier.Standard,
                modifier = Modifier.weight(2.6f).fillMaxHeight().testTag("keyboard_space")
            )
            key(".", weight = 0.8f, tag = "keyboard_period",
                longPress = { punctuationMenu = true }) {
                emit('.')
            }
            val action = imeOptions and EditorInfo.IME_MASK_ACTION
            val (icon, description) = when (action) {
                EditorInfo.IME_ACTION_SEARCH -> Icons.Default.Search to "Search"
                EditorInfo.IME_ACTION_SEND -> Icons.AutoMirrored.Filled.Send to "Send"
                EditorInfo.IME_ACTION_GO -> Icons.Default.ArrowForward to "Go"
                EditorInfo.IME_ACTION_NEXT -> Icons.Default.ArrowForward to "Next"
                EditorInfo.IME_ACTION_DONE -> Icons.Default.Check to "Done"
                else -> Icons.Default.KeyboardReturn to "Enter"
            }
            KeyboardKey(
                icon = icon, contentDescription = description, onClick = onDone,
                hapticTier = HapticTier.Strong, colors = KeyboardKeyColors.accent(),
                modifier = Modifier.weight(1.1f).fillMaxHeight().testTag("keyboard_enter")
            )
        }
        }
        DropdownMenu(expanded = punctuationMenu, onDismissRequest = { punctuationMenu = false }) {
            listOf(',', ';', ':', '!', '?', '@').forEach { punctuation ->
                DropdownMenuItem(
                    text = { Text(punctuation.toString()) },
                    onClick = { punctuationMenu = false; emit(punctuation) }
                )
            }
        }
    }
    }
}
