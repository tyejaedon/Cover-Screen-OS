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
    QWERTY,
    T9_PREDICTIVE,
    SYMBOLS,
    SYMBOLS_SECONDARY,
    NUMERIC;

    fun toContractMode(): LayoutMode = when (this) {
        QWERTY -> LayoutMode.QWERTY_COMPACT
        T9_PREDICTIVE -> LayoutMode.T9_PREDICTIVE
        SYMBOLS -> LayoutMode.SYMBOLS_PRIMARY
        SYMBOLS_SECONDARY -> LayoutMode.SYMBOLS_SECONDARY
        NUMERIC -> LayoutMode.NUMBER_PAD
    }

    companion object {
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
    LOWERCASE,
    SINGLE_SHIFT,
    CAPS_LOCK;

    val isShifted: Boolean
        get() = this != LOWERCASE

    fun nextState(lastTapTimeMs: Long, nowMs: Long): CoverShiftState = when (this) {
        CAPS_LOCK -> LOWERCASE
        SINGLE_SHIFT -> if (nowMs - lastTapTimeMs in 0L..350L) CAPS_LOCK else LOWERCASE
        LOWERCASE -> SINGLE_SHIFT
    }

    fun toContractShiftState(): ShiftState = when (this) {
        LOWERCASE -> ShiftState.LOWERCASE
        SINGLE_SHIFT -> ShiftState.SINGLE_SHIFT
        CAPS_LOCK -> ShiftState.CAPS_LOCK
    }
}

/**
 * Functional key action descriptors.
 */
sealed interface CoverKeyAction {
    data class InputChar(val char: Char) : CoverKeyAction
    data class InputText(val text: String) : CoverKeyAction
    data class T9Tap(val digit: Char, val letters: String) : CoverKeyAction
    object Backspace : CoverKeyAction
    object DeleteWord : CoverKeyAction
    object ClearOrUndo : CoverKeyAction
    object Space : CoverKeyAction
    object Enter : CoverKeyAction
    object Shift : CoverKeyAction
    object SwitchMode : CoverKeyAction
    object SwitchToSymbols : CoverKeyAction
    object SwitchToSymbolsSecondary : CoverKeyAction
    object SwitchToLetters : CoverKeyAction
    object ToggleT9 : CoverKeyAction
    object Dismiss : CoverKeyAction
}

/**
 * Representation of a key within the cover screen layout, backed by FlorisBoard [TextKeyData]
 * and [PopupSet].
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

    fun buildFromArrangement(
        arrangement: FlorisLayoutArrangement,
        shiftState: CoverShiftState = CoverShiftState.LOWERCASE,
        startId: Int = 1,
        includeClearKeyOnBottomRow: Boolean = false
    ): List<List<CoverKeyItem>> {
        val isShifted = shiftState.isShifted
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

    private fun mapTextKeyDataToCoverKeyItem(
        id: Int,
        keyData: TextKeyData,
        shiftState: CoverShiftState,
        isShifted: Boolean
    ): CoverKeyItem {
        val rawLabel = keyData.label
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

        val secondaryLabel = keyData.popup.main?.label
        val popupOptions = buildList {
            keyData.popup.relevant.forEach { rel ->
                if (rel.label.isNotEmpty()) {
                    add(if (isShifted && rel.label.length == 1 && rel.label[0].isLetter()) rel.label.uppercase() else rel.label)
                }
            }
        }

        val isFunctional = keyData.type != KeyType.CHARACTER && keyData.type != KeyType.NUMERIC
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
