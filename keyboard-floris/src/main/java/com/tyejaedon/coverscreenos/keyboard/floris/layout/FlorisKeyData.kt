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
    val main: T? = null,
    val relevant: List<T> = emptyList()
) {
    fun isEmpty(): Boolean = main == null && relevant.isEmpty()

    fun isNotEmpty(): Boolean = !isEmpty()

    fun all(): List<T> = buildList {
        if (main != null) add(main)
        addAll(relevant)
    }

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
    val type: KeyType = KeyType.CHARACTER,
    val code: Int = KeyCode.UNSPECIFIED,
    val label: String = "",
    val groupId: Int = GROUP_DEFAULT,
    val popup: PopupSet<TextKeyData> = PopupSet(),
    val widthFactor: Float = 1.0f,
    val t9Chars: String = "",
    val testTag: String? = null
) {
    companion object {
        const val GROUP_DEFAULT = 0
        const val GROUP_LEFT = 1
        const val GROUP_RIGHT = 2
        const val GROUP_ENTER = 3
    }
}

/**
 * Serializable representation of a FlorisBoard JSON layout arrangement file.
 */
@Serializable
data class FlorisLayoutArrangement(
    val type: String = "characters",
    val name: String = "qwerty",
    val direction: String = "ltr",
    val arrangement: List<List<TextKeyData>> = emptyList()
)
