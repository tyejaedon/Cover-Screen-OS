package com.tyejaedon.coverscreenos.keyboard.floris

import android.content.Context
import com.tyejaedon.coverscreenos.keyboard.core.contracts.KeyModel
import com.tyejaedon.coverscreenos.keyboard.core.contracts.LayoutMode
import com.tyejaedon.coverscreenos.keyboard.core.contracts.ShiftState
import com.tyejaedon.coverscreenos.keyboard.floris.layout.FlorisLayoutArrangement
import com.tyejaedon.coverscreenos.keyboard.floris.layout.FlorisLayoutParser
import com.tyejaedon.coverscreenos.keyboard.floris.layout.FlorisLayoutType
import com.tyejaedon.coverscreenos.keyboard.floris.layout.KeyCode
import com.tyejaedon.coverscreenos.keyboard.floris.layout.KeyType
import com.tyejaedon.coverscreenos.keyboard.floris.layout.PopupSet
import com.tyejaedon.coverscreenos.keyboard.floris.layout.TextKeyData

/**
 * Layout modes supported on the cover screen.
 */
enum class CoverLayoutMode {
    /** Full alphabetic QWERTY letter grid. */
    QWERTY,
    /** Classic 3×4 phone keypad supporting both predictive T9 and multi-tap entry. */
    T9_PREDICTIVE,
    /** Primary numbers and common punctuation symbols page. */
    SYMBOLS,
    /** Secondary extended symbols page. */
    SYMBOLS_SECONDARY,
    /** Pure numeric PIN/dialpad grid. */
    NUMERIC;

    /** Converts this Floris layout mode into the shared `keyboard.core.contracts` [LayoutMode]. */
    fun toContractMode(): LayoutMode = when (this) {
        QWERTY -> LayoutMode.QWERTY_COMPACT
        T9_PREDICTIVE -> LayoutMode.T9_PREDICTIVE
        SYMBOLS -> LayoutMode.SYMBOLS_PRIMARY
        SYMBOLS_SECONDARY -> LayoutMode.SYMBOLS_SECONDARY
        NUMERIC -> LayoutMode.NUMBER_PAD
    }

    /** Conversion helpers between shared contract modes and [CoverLayoutMode]. */
    companion object {
        /** Maps a shared `keyboard.core.contracts` [LayoutMode] back to [CoverLayoutMode]. */
        fun fromContractMode(mode: LayoutMode): CoverLayoutMode = when (mode) {
            LayoutMode.QWERTY_COMPACT -> QWERTY
            LayoutMode.T9_PREDICTIVE -> T9_PREDICTIVE
            LayoutMode.SYMBOLS_PRIMARY -> SYMBOLS
            LayoutMode.SYMBOLS_SECONDARY -> SYMBOLS_SECONDARY
            LayoutMode.NUMBER_PAD -> NUMERIC
        }
    }
}

/**
 * Shift state for alphabetic layout.
 */
enum class CoverShiftState {
    /** Standard lowercase typing. */
    LOWERCASE,
    /** Uppercase for the next typed letter only, then reverts to [LOWERCASE]. */
    SINGLE_SHIFT,
    /** Sticky uppercase lock until tapped again. */
    CAPS_LOCK;

    /** True whenever characters should render and insert in uppercase ([SINGLE_SHIFT] or [CAPS_LOCK]). */
    val isShifted: Boolean
        get() = this != LOWERCASE

    /** Advances shift state on Shift tap; double-tapping within 350ms promotes [SINGLE_SHIFT] to [CAPS_LOCK]. */
    fun nextState(lastTapTimeMs: Long, nowMs: Long): CoverShiftState = when (this) {
        CAPS_LOCK -> LOWERCASE
        SINGLE_SHIFT -> if (nowMs - lastTapTimeMs in 0L..350L) CAPS_LOCK else LOWERCASE
        LOWERCASE -> SINGLE_SHIFT
    }

    /** Converts this UI shift state to the shared [ShiftState] contract enum. */
    fun toContractShiftState(): ShiftState = when (this) {
        LOWERCASE -> ShiftState.LOWERCASE
        SINGLE_SHIFT -> ShiftState.SINGLE_SHIFT
        CAPS_LOCK -> ShiftState.CAPS_LOCK
    }
}

/**
 * Functional key action descriptors emitted when a [CoverKeyItem] is tapped or triggered.
 */
sealed interface CoverKeyAction {
    /** Inserts a single character (for example `'a'` or `'.'`). */
    data class InputChar(val char: Char) : CoverKeyAction
    /** Inserts a multi-character literal string (such as `".com"`). */
    data class InputText(val text: String) : CoverKeyAction
    /** T9 keypad press carrying the physical [digit] and its mapped [letters]. */
    data class T9Tap(val digit: Char, val letters: String) : CoverKeyAction
    /** Deletes one character before the cursor. */
    object Backspace : CoverKeyAction
    /** Deletes the full word before the cursor (e.g. swipe-left on Backspace). */
    object DeleteWord : CoverKeyAction
    /** Long-press clears the buffer; single tap restores it if the buffer is still empty. */
    object ClearOrUndo : CoverKeyAction
    /** Inserts a space (and applies autocorrect on QWERTY if enabled). */
    object Space : CoverKeyAction
    /** Submits the active field or inserts a newline. */
    object Enter : CoverKeyAction
    /** Cycles shift / Caps Lock state. */
    object Shift : CoverKeyAction
    /** Toggles between QWERTY and T9 layouts. */
    object SwitchMode : CoverKeyAction
    /** Switches to or from the primary symbols layout. */
    object SwitchToSymbols : CoverKeyAction
    /** Switches to the secondary extended symbols layout. */
    object SwitchToSymbolsSecondary : CoverKeyAction
    /** Returns to the primary QWERTY letter layout. */
    object SwitchToLetters : CoverKeyAction
    /** Switches directly into T9 predictive mode. */
    object ToggleT9 : CoverKeyAction
    /** Hides/dismisses the keyboard surface. */
    object Dismiss : CoverKeyAction
}

/**
 * Representation of a key within the cover screen layout, backed by FlorisBoard [TextKeyData]
 * and [PopupSet].
 *
 * @property id Unique integer ID across the active keyboard grid (used as Compose `pointerInput` key).
 * @property primaryLabel Main text rendered in the center of the keycap.
 * @property secondaryLabel Optional small corner hint showing the primary long-press character.
 * @property t9Chars Letter group displayed under a T9 digit key (e.g. `"abc"` on `'2'`).
 * @property weight Relative horizontal row weight controlling key width.
 * @property isFunctional True for modifier/action keys (Shift, Backspace, Enter) so they receive modifier styling.
 * @property action Semantic action triggered when this key is pressed.
 * @property popupOptions List of alternate characters shown in [FlorisExtendedPopupMenu] on long-press.
 * @property popupSet Raw FlorisBoard popup metadata including the primary long-press character (`main`).
 * @property florisKeyData Upstream parsed [TextKeyData] record backing this key.
 * @property tag Optional Compose `testTag` for automated UI testing.
 */
data class CoverKeyItem(
    val id: Int,
    val primaryLabel: String,
    val secondaryLabel: String? = null,
    val t9Chars: String = "",
    val weight: Float = 1f,
    val isFunctional: Boolean = false,
    val action: CoverKeyAction,
    val popupOptions: List<String> = emptyList(),
    val popupSet: PopupSet<TextKeyData> = PopupSet(),
    val florisKeyData: TextKeyData = TextKeyData(label = primaryLabel),
    val tag: String? = null
) {
    /** Projects this Floris key item down to the lightweight [KeyModel] contract. */
    fun toKeyModel(): KeyModel = KeyModel(
        id = id,
        primaryLabel = primaryLabel,
        secondaryLabel = secondaryLabel,
        t9Chars = t9Chars,
        isFunctional = isFunctional
    )
}

/**
 * Factory providing key rows parsed from upstream FlorisBoard JSON layout files
 * (`characters/qwerty.json`, `symbols/western.json`, `symbols2/western.json`,
 * `t9/default.json`, `numeric/default.json`).
 */
object CoverLayoutFactory {

    /** Loads and maps the 4-row QWERTY alphabetic layout with the requested [shiftState]. */
    fun createQwertyRows(
        shiftState: CoverShiftState,
        context: Context? = null,
        includeClearKey: Boolean = false
    ): List<List<CoverKeyItem>> {
        val arrangement = FlorisLayoutParser.loadArrangement(
            type = FlorisLayoutType.CHARACTERS,
            name = "qwerty",
            context = context
        )
        return buildFromArrangement(
            arrangement = arrangement,
            shiftState = shiftState,
            startId = 1,
            includeClearKeyOnBottomRow = includeClearKey
        )
    }

    /** Loads and maps the 3×4 T9 phone keypad layout (`t9/default.json`). */
    fun createT9Rows(
        context: Context? = null,
        includeClearKey: Boolean = false
    ): List<List<CoverKeyItem>> {
        val arrangement = FlorisLayoutParser.loadArrangement(
            type = FlorisLayoutType.T9,
            name = "default",
            context = context
        )
        return buildFromArrangement(
            arrangement = arrangement,
            shiftState = CoverShiftState.LOWERCASE,
            startId = 100,
            includeClearKeyOnBottomRow = includeClearKey
        )
    }

    /** Loads and maps the primary symbols/punctuation grid (standard or compact cover variant). */
    fun createSymbolsRows(
        context: Context? = null,
        compactVariant: Boolean = false
    ): List<List<CoverKeyItem>> {
        val arrangement = FlorisLayoutParser.loadArrangement(
            type = FlorisLayoutType.SYMBOLS,
            name = if (compactVariant) "compact" else "western",
            context = context
        )
        return buildFromArrangement(
            arrangement = arrangement,
            shiftState = CoverShiftState.LOWERCASE,
            startId = 200
        )
    }

    /** Loads and maps the secondary extended symbols page (`symbols2/western.json`). */
    fun createSymbolsSecondaryRows(context: Context? = null): List<List<CoverKeyItem>> {
        val arrangement = FlorisLayoutParser.loadArrangement(
            type = FlorisLayoutType.SYMBOLS2,
            name = "western",
            context = context
        )
        return buildFromArrangement(
            arrangement = arrangement,
            shiftState = CoverShiftState.LOWERCASE,
            startId = 300
        )
    }

    /** Loads and maps the numeric dialpad/PIN grid (`numeric/default.json`). */
    fun createNumericRows(
        context: Context? = null,
        includeClearKey: Boolean = false
    ): List<List<CoverKeyItem>> {
        val arrangement = FlorisLayoutParser.loadArrangement(
            type = FlorisLayoutType.NUMERIC,
            name = "default",
            context = context
        )
        return buildFromArrangement(
            arrangement = arrangement,
            shiftState = CoverShiftState.LOWERCASE,
            startId = 400,
            includeClearKeyOnBottomRow = includeClearKey
        )
    }

    /** Converts a parsed [FlorisLayoutArrangement] into UI-ready rows of [CoverKeyItem]s. */
    fun buildFromArrangement(
        arrangement: FlorisLayoutArrangement,
        shiftState: CoverShiftState = CoverShiftState.LOWERCASE,
        startId: Int = 1,
        includeClearKeyOnBottomRow: Boolean = false
    ): List<List<CoverKeyItem>> {
        // Precompute shift boolean once for all keys in this grid.
        val isShifted = shiftState.isShifted
        // Monotonic key ID counter ensuring each rendered key has a distinct Compose pointerInput key.
        var idCounter = startId
        return arrangement.arrangement.mapIndexed { rowIndex, row ->
            val mappedRow = row.map { keyData ->
                mapTextKeyDataToCoverKeyItem(
                    id = idCounter++,
                    keyData = keyData,
                    shiftState = shiftState,
                    isShifted = isShifted
                )
            }.toMutableList()

            if (includeClearKeyOnBottomRow &&
                rowIndex == arrangement.arrangement.lastIndex &&
                mappedRow.none { it.action == CoverKeyAction.ClearOrUndo }
            ) {
                // Place Clear right next to the mode-switch key on the bottom row when enabled.
                val insertIdx = mappedRow.indexOfFirst { it.action == CoverKeyAction.SwitchToSymbols || it.action == CoverKeyAction.SwitchToLetters }
                    .let { if (it >= 0) it + 1 else 1 }
                    .coerceAtMost(mappedRow.size)
                mappedRow.add(
                    insertIdx,
                    CoverKeyItem(
                        id = idCounter++,
                        primaryLabel = "✕",
                        weight = 0.9f,
                        isFunctional = true,
                        action = CoverKeyAction.ClearOrUndo,
                        florisKeyData = TextKeyData(
                            type = KeyType.FUNCTION,
                            code = KeyCode.CLEAR_INPUT,
                            label = "✕",
                            widthFactor = 0.9f,
                            testTag = "keyboard_clear"
                        ),
                        tag = "keyboard_clear"
                    )
                )
            }
            mappedRow
        }
    }

    /** Translates a single upstream [TextKeyData] entry into a styled, action-bound [CoverKeyItem]. */
    private fun mapTextKeyDataToCoverKeyItem(
        id: Int,
        keyData: TextKeyData,
        shiftState: CoverShiftState,
        isShifted: Boolean
    ): CoverKeyItem {
        val rawLabel = keyData.label
        // Resolve the visible keycap label according to shift state and special Shift glyphs.
        val displayLabel = when {
            keyData.code == KeyCode.SHIFT -> when (shiftState) {
                CoverShiftState.CAPS_LOCK -> "▲▲"
                CoverShiftState.SINGLE_SHIFT -> "▲"
                CoverShiftState.LOWERCASE -> "⇧"
            }
            keyData.type == KeyType.CHARACTER && rawLabel.length == 1 && rawLabel[0].isLetter() -> {
                if (isShifted) rawLabel.uppercase() else rawLabel.lowercase()
            }
            else -> rawLabel
        }

        // Top-right corner hint shown on keys with a primary long-press symbol/number.
        val secondaryLabel = keyData.popup.main?.label
        // Ordered long-press accent/symbol options matching current letter casing.
        val popupOptions = buildList {
            keyData.popup.relevant.forEach { rel ->
                if (rel.label.isNotEmpty()) {
                    add(if (isShifted && rel.label.length == 1 && rel.label[0].isLetter()) rel.label.uppercase() else rel.label)
                }
            }
        }

        // Distinguish modifier/navigation keys from character/number keys for surface coloring.
        val isFunctional = keyData.type != KeyType.CHARACTER && keyData.type != KeyType.NUMERIC
        // Map FlorisBoard numeric KeyCode constants to typed CoverKeyAction sealed objects.
        val action: CoverKeyAction = when (keyData.code) {
            KeyCode.SHIFT, KeyCode.CAPS_LOCK -> CoverKeyAction.Shift
            KeyCode.DELETE -> CoverKeyAction.Backspace
            KeyCode.DELETE_WORD -> CoverKeyAction.DeleteWord
            KeyCode.CLEAR_INPUT -> CoverKeyAction.ClearOrUndo
            KeyCode.SPACE -> CoverKeyAction.Space
            KeyCode.ENTER -> CoverKeyAction.Enter
            KeyCode.VIEW_SYMBOLS -> CoverKeyAction.SwitchToSymbols
            KeyCode.VIEW_SYMBOLS2 -> CoverKeyAction.SwitchToSymbolsSecondary
            KeyCode.VIEW_CHARACTERS -> CoverKeyAction.SwitchToLetters
            KeyCode.VIEW_T9 -> CoverKeyAction.ToggleT9
            KeyCode.IME_HIDE_UI -> CoverKeyAction.Dismiss
            else -> {
                if (displayLabel.length == 1) {
                    CoverKeyAction.InputChar(displayLabel[0])
                } else {
                    CoverKeyAction.InputText(displayLabel)
                }
            }
        }

        return CoverKeyItem(
            id = id,
            primaryLabel = displayLabel,
            secondaryLabel = secondaryLabel,
            t9Chars = keyData.t9Chars,
            weight = keyData.widthFactor,
            isFunctional = isFunctional,
            action = action,
            popupOptions = popupOptions,
            popupSet = keyData.popup,
            florisKeyData = keyData,
            tag = keyData.testTag
        )
    }
}
