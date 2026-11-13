package com.tyejaedon.coverscreenos.keyboard.floris

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Layout modes supported on the cover screen.
 */
enum class CoverLayoutMode {
    QWERTY,
    T9_PREDICTIVE,
    SYMBOLS,
    NUMERIC
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
        SINGLE_SHIFT -> if (nowMs - lastTapTimeMs < 350L) CAPS_LOCK else LOWERCASE
        LOWERCASE -> SINGLE_SHIFT
    }
}

/**
 * Functional key action descriptors.
 */
sealed interface CoverKeyAction {
    data class InputChar(val char: Char) : CoverKeyAction
    data class InputText(val text: String) : CoverKeyAction
    object Backspace : CoverKeyAction
    object DeleteWord : CoverKeyAction
    object Space : CoverKeyAction
    object Enter : CoverKeyAction
    object Shift : CoverKeyAction
    object SwitchMode : CoverKeyAction
    object SwitchToSymbols : CoverKeyAction
    object SwitchToLetters : CoverKeyAction
    object ToggleT9 : CoverKeyAction
    object Dismiss : CoverKeyAction
}

/**
 * Representation of a key within the cover screen layout.
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
    val tag: String? = null
)

/**
 * Factory providing pre-computed key rows for QWERTY, T9, and Symbols.
 */
object CoverLayoutFactory {

    fun createQwertyRows(shiftState: CoverShiftState): List<List<CoverKeyItem>> {
        val isShifted = shiftState.isShifted
        var idCounter = 1

        val row1Letters = if (isShifted) "QWERTYUIOP" else "qwertyuiop"
        val row1Digits = "1234567890"
        val row1 = row1Letters.mapIndexed { i, char ->
            CoverKeyItem(
                id = idCounter++,
                primaryLabel = char.toString(),
                secondaryLabel = row1Digits[i].toString(),
                action = CoverKeyAction.InputChar(char),
                tag = "keyboard_top_$i",
                popupOptions = when (char.lowercaseChar()) {
                    'e' -> listOf("é", "è", "ê", "ë")
                    'a' -> listOf("á", "à", "â", "ä", "ã")
                    'u' -> listOf("ú", "ù", "û", "ü")
                    'i' -> listOf("í", "ì", "î", "ï")
                    'o' -> listOf("ó", "ò", "ô", "ö", "õ")
                    else -> emptyList()
                }
            )
        }

        val row2Letters = if (isShifted) "ASDFGHJKL" else "asdfghjkl"
        val row2 = row2Letters.mapIndexed { i, char ->
            CoverKeyItem(
                id = idCounter++,
                primaryLabel = char.toString(),
                action = CoverKeyAction.InputChar(char),
                tag = "keyboard_middle_$i",
                popupOptions = if (char.lowercaseChar() == 's') listOf("ß") else emptyList()
            )
        }

        val row3Letters = if (isShifted) "ZXCVBNM" else "zxcvbnm"
        val row3Keys = mutableListOf<CoverKeyItem>()
        // Shift key
        row3Keys.add(
            CoverKeyItem(
                id = idCounter++,
                primaryLabel = when (shiftState) {
                    CoverShiftState.CAPS_LOCK -> "▲▲"
                    CoverShiftState.SINGLE_SHIFT -> "▲"
                    CoverShiftState.LOWERCASE -> "⇧"
                },
                weight = 1.35f,
                isFunctional = true,
                action = CoverKeyAction.Shift,
                tag = "keyboard_shift"
            )
        )
        row3Letters.forEachIndexed { i, char ->
            row3Keys.add(
                CoverKeyItem(
                    id = idCounter++,
                    primaryLabel = char.toString(),
                    action = CoverKeyAction.InputChar(char),
                    tag = "keyboard_lower_$i"
                )
            )
        }
        // Backspace key
        row3Keys.add(
            CoverKeyItem(
                id = idCounter++,
                primaryLabel = "⌫",
                weight = 1.35f,
                isFunctional = true,
                action = CoverKeyAction.Backspace,
                tag = "keyboard_backspace"
            )
        )

        // Bottom row
        val row4Keys = listOf(
            CoverKeyItem(
                id = idCounter++,
                primaryLabel = "?123",
                weight = 1.25f,
                isFunctional = true,
                action = CoverKeyAction.SwitchToSymbols,
                tag = "keyboard_symbols"
            ),
            CoverKeyItem(
                id = idCounter++,
                primaryLabel = ",",
                weight = 0.9f,
                action = CoverKeyAction.InputChar(',')
            ),
            CoverKeyItem(
                id = idCounter++,
                primaryLabel = "Space",
                weight = 4.2f,
                action = CoverKeyAction.Space,
                tag = "keyboard_space"
            ),
            CoverKeyItem(
                id = idCounter++,
                primaryLabel = ".",
                weight = 0.9f,
                action = CoverKeyAction.InputChar('.')
            ),
            CoverKeyItem(
                id = idCounter++,
                primaryLabel = "↵",
                weight = 1.35f,
                isFunctional = true,
                action = CoverKeyAction.Enter,
                tag = "keyboard_enter"
            )
        )

        return listOf(row1, row2, row3Keys, row4Keys)
    }

    fun createT9Rows(): List<List<CoverKeyItem>> {
        var idCounter = 100
        val row1 = listOf(
            CoverKeyItem(idCounter++, "1", "@.-", t9Chars = "1", action = CoverKeyAction.InputChar('1')),
            CoverKeyItem(idCounter++, "2", "ABC", t9Chars = "abc", action = CoverKeyAction.InputChar('2')),
            CoverKeyItem(idCounter++, "3", "DEF", t9Chars = "def", action = CoverKeyAction.InputChar('3'))
        )
        val row2 = listOf(
            CoverKeyItem(idCounter++, "4", "GHI", t9Chars = "ghi", action = CoverKeyAction.InputChar('4')),
            CoverKeyItem(idCounter++, "5", "JKL", t9Chars = "jkl", action = CoverKeyAction.InputChar('5')),
            CoverKeyItem(idCounter++, "6", "MNO", t9Chars = "mno", action = CoverKeyAction.InputChar('6'))
        )
        val row3 = listOf(
            CoverKeyItem(idCounter++, "7", "PQRS", t9Chars = "pqrs", action = CoverKeyAction.InputChar('7')),
            CoverKeyItem(idCounter++, "8", "TUV", t9Chars = "tuv", action = CoverKeyAction.InputChar('8')),
            CoverKeyItem(idCounter++, "9", "WXYZ", t9Chars = "wxyz", action = CoverKeyAction.InputChar('9'))
        )
        val row4 = listOf(
            CoverKeyItem(idCounter++, "ABC", "", isFunctional = true, action = CoverKeyAction.SwitchToLetters),
            CoverKeyItem(idCounter++, "0", "␣", t9Chars = " ", action = CoverKeyAction.Space),
            CoverKeyItem(idCounter++, "⌫", "", isFunctional = true, action = CoverKeyAction.Backspace)
        )
        return listOf(row1, row2, row3, row4)
    }

    fun createSymbolsRows(): List<List<CoverKeyItem>> {
        var idCounter = 200
        val row1 = "1234567890".map { char ->
            CoverKeyItem(idCounter++, char.toString(), action = CoverKeyAction.InputChar(char))
        }
        val row2 = "@#$%&-+()*".map { char ->
            CoverKeyItem(idCounter++, char.toString(), action = CoverKeyAction.InputChar(char))
        }
        val row3Keys = mutableListOf<CoverKeyItem>()
        row3Keys.add(CoverKeyItem(idCounter++, "=/<", weight = 1.2f, isFunctional = true, action = CoverKeyAction.SwitchToSymbols))
        "!\"':;/?".forEach { char ->
            row3Keys.add(CoverKeyItem(idCounter++, char.toString(), action = CoverKeyAction.InputChar(char)))
        }
        row3Keys.add(CoverKeyItem(idCounter++, "⌫", weight = 1.2f, isFunctional = true, action = CoverKeyAction.Backspace))

        val row4 = listOf(
            CoverKeyItem(idCounter++, "ABC", weight = 1.4f, isFunctional = true, action = CoverKeyAction.SwitchToLetters),
            CoverKeyItem(idCounter++, ",", weight = 1.0f, action = CoverKeyAction.InputChar(',')),
            CoverKeyItem(idCounter++, "Space", weight = 4.0f, action = CoverKeyAction.Space),
            CoverKeyItem(idCounter++, ".", weight = 1.0f, action = CoverKeyAction.InputChar('.')),
            CoverKeyItem(idCounter++, "↵", weight = 1.4f, isFunctional = true, action = CoverKeyAction.Enter)
        )
        return listOf(row1, row2, row3Keys, row4)
    }
}
