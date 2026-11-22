package com.tyejaedon.coverscreenos.keyboard.floris.layout

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Upstream FlorisBoard key codes for character, modifier, navigation, and view-switching keys.
 */
object KeyCode {
    const val UNSPECIFIED = 0

    // Standard ASCII / control codes
    const val SPACE = 32
    const val ENTER = 10
    const val TAB = 9

    // Editing & deletion
    const val DELETE = -5
    const val DELETE_WORD = -7
    const val FORWARD_DELETE = -8
    const val CLEAR_INPUT = -207

    // Modifiers & locks
    const val SHIFT = -11
    const val CAPS_LOCK = -12

    // Cursor navigation
    const val ARROW_LEFT = -21
    const val ARROW_RIGHT = -22
    const val ARROW_UP = -23
    const val ARROW_DOWN = -24

    // View / layout mode switches
    const val VIEW_CHARACTERS = -201
    const val VIEW_SYMBOLS = -202
    const val VIEW_SYMBOLS2 = -203
    const val VIEW_NUMERIC = -204
    const val VIEW_NUMERIC_ADVANCED = -205
    const val VIEW_PHONE = -206
    const val VIEW_T9 = -210

    // System & IME actions
    const val IME_HIDE_UI = -208
    const val T9_PREDICTION_TOGGLE = -211
}

/**
 * Upstream FlorisBoard key classification type.
 */
@Serializable
enum class KeyType {
    @SerialName("character")
    CHARACTER,

    @SerialName("enter_editing")
    ENTER_EDITING,

    @SerialName("function")
    FUNCTION,

    @SerialName("lock")
    LOCK,

    @SerialName("modifier")
    MODIFIER,

    @SerialName("navigation")
    NAVIGATION,

    @SerialName("numeric")
    NUMERIC,

    @SerialName("placeholder")
    PLACEHOLDER,

    @SerialName("system_gui")
    SYSTEM_GUI,

    @SerialName("unspecified")
    UNSPECIFIED
}

/**
 * Upstream FlorisBoard layout category types.
 */
@Serializable
enum class FlorisLayoutType(val dirName: String) {
    CHARACTERS("characters"),
    CHARACTERS_MOD("characters_mod"),
    SYMBOLS("symbols"),
    SYMBOLS_MOD("symbols_mod"),
    SYMBOLS2("symbols2"),
    SYMBOLS2_MOD("symbols2_mod"),
    NUMERIC("numeric"),
    T9("t9")
}

/**
 * Upstream FlorisBoard [PopupSet] holding a primary long-press key ([main]) and
 * additional extended popup keys ([relevant]).
 */
@Serializable
data class PopupSet<T>(
    /** Default character/action shown immediately on long-press (e.g. `"1"` on key `"q"`). */
    val main: T? = null,
    /** Secondary popup options fanned out next to [main] (e.g. accented vowels `"é"`, `"è"`). */
    val relevant: List<T> = emptyList()
) {
    /** Returns `true` when this key defines no long-press popup options at all. */
    fun isEmpty(): Boolean = main == null && relevant.isEmpty()

    /** Returns `true` when at least one primary or extended popup item exists. */
    fun isNotEmpty(): Boolean = !isEmpty()

    /** Returns a flat list starting with [main] (if present) followed by [relevant]. */
    fun all(): List<T> = buildList {
        if (main != null) add(main)
        addAll(relevant)
    }

    /** Combines two popup sets without duplicate entries, preferring this set's [main]. */
    fun merge(other: PopupSet<T>): PopupSet<T> {
        val mergedMain = this.main ?: other.main
        val mergedRelevant = buildList {
            if (this@PopupSet.main != null && other.main != null && this@PopupSet.main != other.main) {
                add(other.main)
            }
            addAll(this@PopupSet.relevant)
            for (item in other.relevant) {
                if (item != mergedMain && item !in this) {
                    add(item)
                }
            }
        }
        return PopupSet(main = mergedMain, relevant = mergedRelevant)
    }
}

/**
 * Upstream FlorisBoard serializable key descriptor (`TextKeyData`).
 */
@Serializable
data class TextKeyData(
    /** Functional category of the key controlling visual styling and tap behavior. */
    val type: KeyType = KeyType.CHARACTER,
    /** Positive Unicode code point for characters, or negative [KeyCode] constant for actions. */
    val code: Int = KeyCode.UNSPECIFIED,
    /** Text or glyph displayed on the keycap face. */
    val label: String = "",
    /** Layout group identifier (`GROUP_*`) used to align left/right edge or enter-key clusters. */
    val groupId: Int = GROUP_DEFAULT,
    /** Long-press popup characters/digits attached to this key. */
    val popup: PopupSet<TextKeyData> = PopupSet(),
    /** Relative horizontal width multiplier (`1.0f` = standard key width, `4.2f` = spacebar). */
    val widthFactor: Float = 1.0f,
    /** Alphabetic characters cycled/predicted by this key in T9 mode (e.g. `"abc"`). */
    val t9Chars: String = "",
    /** Optional Compose test tag exposed in semantics for automated UI tests. */
    val testTag: String? = null
) {
    companion object {
        /** Standard ungrouped key. */
        const val GROUP_DEFAULT = 0
        /** Key anchored to the left edge group (e.g. Shift / layout switch). */
        const val GROUP_LEFT = 1
        /** Key anchored to the right edge group (e.g. Backspace). */
        const val GROUP_RIGHT = 2
        /** Dedicated Enter/Done action group. */
        const val GROUP_ENTER = 3
    }
}

/**
 * Serializable representation of a FlorisBoard JSON layout arrangement file.
 */
@Serializable
data class FlorisLayoutArrangement(
    /** Layout category name matching [FlorisLayoutType.dirName] (e.g. `"characters"`, `"t9"`). */
    val type: String = "characters",
    /** Specific layout variant filename without `.json` (e.g. `"qwerty"`, `"western"`, `"compact"`). */
    val name: String = "qwerty",
    /** Reading/layout direction (`"ltr"` or `"rtl"`). */
    val direction: String = "ltr",
    /** Grid of keyboard rows from top to bottom, each holding ordered [TextKeyData] descriptors. */
    val arrangement: List<List<TextKeyData>> = emptyList()
)
