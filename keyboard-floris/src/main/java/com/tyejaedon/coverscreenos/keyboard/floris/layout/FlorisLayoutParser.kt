package com.tyejaedon.coverscreenos.keyboard.floris.layout

import android.content.Context
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Upstream FlorisBoard JSON layout parser and cache (`org.florisboard.layouts`).
 *
 * Loads layout arrangement descriptors from bundled assets under
 * `ime/keyboard/org.florisboard.layouts/<type>/<name>.json` with fallback to
 * embedded JSON definitions for pure-JVM unit tests without an Android [Context].
 */
object FlorisLayoutParser {

    /** Asset root directory containing bundled FlorisBoard JSON layout files. */
    private const val ASSET_BASE_PATH = "ime/keyboard/org.florisboard.layouts"

    /** Lenient Kotlinx JSON decoder tolerating optional/extra fields in upstream layout JSONs. */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /** Thread-safe cache keyed by `"<type>/<name>"` so each layout JSON is parsed only once. */
    private val layoutCache = ConcurrentHashMap<String, FlorisLayoutArrangement>()

    /**
     * Loads and parses a [FlorisLayoutArrangement] by [type] and [name].
     */
    fun loadArrangement(
        type: FlorisLayoutType,
        name: String = defaultLayoutName(type),
        context: Context? = null
    ): FlorisLayoutArrangement {
        val cacheKey = "${type.dirName}/$name" // Cache lookup key matching asset subfolder/name
        layoutCache[cacheKey]?.let { return it }

        val rawJson = readAssetOrFallback(type, name, context) // JSON text from assets, classpath, or embedded fallback
        val parsed = json.decodeFromString<FlorisLayoutArrangement>(rawJson)
        layoutCache[cacheKey] = parsed
        return parsed
    }

    /**
     * Parses a raw FlorisBoard JSON layout string directly into a [FlorisLayoutArrangement].
     */
    fun parseRawJson(rawJson: String): FlorisLayoutArrangement =
        json.decodeFromString<FlorisLayoutArrangement>(rawJson)

    /** Resolves the default variant filename when callers omit an explicit layout name. */
    private fun defaultLayoutName(type: FlorisLayoutType): String = when (type) {
        FlorisLayoutType.CHARACTERS -> "qwerty"
        FlorisLayoutType.SYMBOLS, FlorisLayoutType.SYMBOLS2 -> "western"
        else -> "default"
    }

    /**
     * Reads layout JSON in priority order: Android `AssetManager` -> JVM classpath resource
     * (for Robolectric/unit tests) -> compile-time embedded JSON constants.
     */
    private fun readAssetOrFallback(
        type: FlorisLayoutType,
        name: String,
        context: Context?
    ): String {
        val assetPath = "$ASSET_BASE_PATH/${type.dirName}/$name.json" // Relative path inside assets/
        if (context != null) {
            runCatching {
                return context.assets.open(assetPath).bufferedReader().use { it.readText() }
            }
        }
        runCatching {
            val stream = FlorisLayoutParser::class.java.classLoader
                ?.getResourceAsStream("assets/$assetPath")
                ?: FlorisLayoutParser::class.java.classLoader
                    ?.getResourceAsStream(assetPath) // Classpath fallback when running without Android Context
            if (stream != null) {
                return stream.bufferedReader().use { it.readText() }
            }
        }
        return when (type) {
            FlorisLayoutType.CHARACTERS -> EMBEDDED_QWERTY_JSON
            FlorisLayoutType.SYMBOLS -> if (name == "compact") EMBEDDED_COMPACT_SYMBOLS_JSON else EMBEDDED_SYMBOLS_WESTERN_JSON
            FlorisLayoutType.SYMBOLS2 -> EMBEDDED_SYMBOLS2_WESTERN_JSON
            FlorisLayoutType.T9 -> EMBEDDED_T9_JSON
            FlorisLayoutType.NUMERIC -> EMBEDDED_NUMERIC_JSON
            else -> EMBEDDED_QWERTY_JSON
        }
    }

    private const val EMBEDDED_QWERTY_JSON = """
    {
      "type": "characters",
      "name": "qwerty",
      "direction": "ltr",
      "arrangement": [
        [
          { "code": 113, "label": "q", "popup": { "main": { "code": 49, "label": "1" } }, "testTag": "keyboard_top_0" },
          { "code": 119, "label": "w", "popup": { "main": { "code": 50, "label": "2" } }, "testTag": "keyboard_top_1" },
          { "code": 101, "label": "e", "popup": { "main": { "code": 51, "label": "3" }, "relevant": [{ "code": 233, "label": "é" }, { "code": 232, "label": "è" }, { "code": 234, "label": "ê" }, { "code": 235, "label": "ë" }] }, "testTag": "keyboard_top_2" },
          { "code": 114, "label": "r", "popup": { "main": { "code": 52, "label": "4" } }, "testTag": "keyboard_top_3" },
          { "code": 116, "label": "t", "popup": { "main": { "code": 53, "label": "5" } }, "testTag": "keyboard_top_4" },
          { "code": 121, "label": "y", "popup": { "main": { "code": 54, "label": "6" } }, "testTag": "keyboard_top_5" },
          { "code": 117, "label": "u", "popup": { "main": { "code": 55, "label": "7" }, "relevant": [{ "code": 250, "label": "ú" }, { "code": 249, "label": "ù" }, { "code": 251, "label": "û" }, { "code": 252, "label": "ü" }] }, "testTag": "keyboard_top_6" },
          { "code": 105, "label": "i", "popup": { "main": { "code": 56, "label": "8" }, "relevant": [{ "code": 237, "label": "í" }, { "code": 236, "label": "ì" }, { "code": 238, "label": "î" }, { "code": 239, "label": "ï" }] }, "testTag": "keyboard_top_7" },
          { "code": 111, "label": "o", "popup": { "main": { "code": 57, "label": "9" }, "relevant": [{ "code": 243, "label": "ó" }, { "code": 242, "label": "ò" }, { "code": 244, "label": "ô" }, { "code": 246, "label": "ö" }, { "code": 245, "label": "õ" }] }, "testTag": "keyboard_top_8" },
          { "code": 112, "label": "p", "popup": { "main": { "code": 48, "label": "0" } }, "testTag": "keyboard_top_9" }
        ],
        [
          { "code": 97, "label": "a", "popup": { "relevant": [{ "code": 225, "label": "á" }, { "code": 224, "label": "à" }, { "code": 226, "label": "â" }, { "code": 228, "label": "ä" }, { "code": 227, "label": "ã" }] }, "testTag": "keyboard_middle_0" },
          { "code": 115, "label": "s", "popup": { "relevant": [{ "code": 223, "label": "ß" }] }, "testTag": "keyboard_middle_1" },
          { "code": 100, "label": "d", "testTag": "keyboard_middle_2" },
          { "code": 102, "label": "f", "testTag": "keyboard_middle_3" },
          { "code": 103, "label": "g", "testTag": "keyboard_middle_4" },
          { "code": 104, "label": "h", "testTag": "keyboard_middle_5" },
          { "code": 106, "label": "j", "testTag": "keyboard_middle_6" },
          { "code": 107, "label": "k", "testTag": "keyboard_middle_7" },
          { "code": 108, "label": "l", "testTag": "keyboard_middle_8" }
        ],
        [
          { "type": "modifier", "code": -11, "label": "⇧", "widthFactor": 1.35, "testTag": "keyboard_shift" },
          { "code": 122, "label": "z", "testTag": "keyboard_lower_0" },
          { "code": 120, "label": "x", "testTag": "keyboard_lower_1" },
          { "code": 99, "label": "c", "testTag": "keyboard_lower_2" },
          { "code": 118, "label": "v", "testTag": "keyboard_lower_3" },
          { "code": 98, "label": "b", "testTag": "keyboard_lower_4" },
          { "code": 110, "label": "n", "testTag": "keyboard_lower_5" },
          { "code": 109, "label": "m", "testTag": "keyboard_lower_6" },
          { "type": "enter_editing", "code": -5, "label": "⌫", "widthFactor": 1.35, "testTag": "keyboard_backspace" }
        ],
        [
          { "type": "system_gui", "code": -202, "label": "?123", "widthFactor": 1.25, "testTag": "keyboard_symbols" },
          { "code": 44, "label": ",", "widthFactor": 0.9, "testTag": "keyboard_comma" },
          { "code": 32, "label": "Space", "widthFactor": 4.2, "testTag": "keyboard_space" },
          { "code": 46, "label": ".", "widthFactor": 0.9, "popup": { "relevant": [{ "code": 44, "label": "," }, { "code": 59, "label": ";" }, { "code": 58, "label": ":" }, { "code": 33, "label": "!" }, { "code": 63, "label": "?" }, { "code": 64, "label": "@" }] }, "testTag": "keyboard_period" },
          { "type": "enter_editing", "code": 10, "label": "↵", "widthFactor": 1.35, "testTag": "keyboard_enter" }
        ]
      ]
    }
    """

    private const val EMBEDDED_SYMBOLS_WESTERN_JSON = """
    {
      "type": "symbols",
      "name": "western",
      "direction": "ltr",
      "arrangement": [
        [
          { "code": 49, "label": "1", "testTag": "keyboard_top_0" },
          { "code": 50, "label": "2", "testTag": "keyboard_top_1" },
          { "code": 51, "label": "3", "testTag": "keyboard_top_2" },
          { "code": 52, "label": "4", "testTag": "keyboard_top_3" },
          { "code": 53, "label": "5", "testTag": "keyboard_top_4" },
          { "code": 54, "label": "6", "testTag": "keyboard_top_5" },
          { "code": 55, "label": "7", "testTag": "keyboard_top_6" },
          { "code": 56, "label": "8", "testTag": "keyboard_top_7" },
          { "code": 57, "label": "9", "testTag": "keyboard_top_8" },
          { "code": 48, "label": "0", "testTag": "keyboard_top_9" }
        ],
        [
          { "code": 64, "label": "@", "testTag": "keyboard_middle_0" },
          { "code": 35, "label": "#", "testTag": "keyboard_middle_1" },
          { "code": 36, "label": "$", "testTag": "keyboard_middle_2" },
          { "code": 37, "label": "%", "testTag": "keyboard_middle_3" },
          { "code": 38, "label": "&", "testTag": "keyboard_middle_4" },
          { "code": 45, "label": "-", "testTag": "keyboard_middle_5" },
          { "code": 43, "label": "+", "testTag": "keyboard_middle_6" },
          { "code": 40, "label": "(", "testTag": "keyboard_middle_7" },
          { "code": 41, "label": ")", "testTag": "keyboard_middle_8" },
          { "code": 42, "label": "*", "testTag": "keyboard_middle_9" }
        ],
        [
          { "type": "system_gui", "code": -203, "label": "=/<", "widthFactor": 1.2, "testTag": "keyboard_symbols_secondary" },
          { "code": 33, "label": "!", "testTag": "keyboard_symbol_0" },
          { "code": 34, "label": "\"", "testTag": "keyboard_symbol_1" },
          { "code": 39, "label": "'", "testTag": "keyboard_symbol_2" },
          { "code": 58, "label": ":", "testTag": "keyboard_symbol_3" },
          { "code": 59, "label": ";", "testTag": "keyboard_symbol_4" },
          { "code": 47, "label": "/", "testTag": "keyboard_symbol_5" },
          { "code": 63, "label": "?", "testTag": "keyboard_symbol_6" },
          { "type": "enter_editing", "code": -5, "label": "⌫", "widthFactor": 1.2, "testTag": "keyboard_backspace" }
        ],
        [
          { "type": "system_gui", "code": -201, "label": "ABC", "widthFactor": 1.4, "testTag": "keyboard_symbols" },
          { "code": 44, "label": ",", "widthFactor": 1.0, "testTag": "keyboard_comma" },
          { "code": 32, "label": "Space", "widthFactor": 4.0, "testTag": "keyboard_space" },
          { "code": 46, "label": ".", "widthFactor": 1.0, "testTag": "keyboard_period" },
          { "type": "enter_editing", "code": 10, "label": "↵", "widthFactor": 1.4, "testTag": "keyboard_enter" }
        ]
      ]
    }
    """

    private const val EMBEDDED_COMPACT_SYMBOLS_JSON = """
    {
      "type": "symbols",
      "name": "compact",
      "direction": "ltr",
      "arrangement": [
        [
          { "code": 49, "label": "1", "testTag": "keyboard_top_0" },
          { "code": 50, "label": "2", "testTag": "keyboard_top_1" },
          { "code": 51, "label": "3", "testTag": "keyboard_top_2" },
          { "code": 52, "label": "4", "testTag": "keyboard_top_3" },
          { "code": 53, "label": "5", "testTag": "keyboard_top_4" },
          { "code": 54, "label": "6", "testTag": "keyboard_top_5" },
          { "code": 55, "label": "7", "testTag": "keyboard_top_6" },
          { "code": 56, "label": "8", "testTag": "keyboard_top_7" },
          { "code": 57, "label": "9", "testTag": "keyboard_top_8" },
          { "code": 48, "label": "0", "testTag": "keyboard_top_9" }
        ],
        [
          { "code": 45, "label": "-", "testTag": "keyboard_middle_0" },
          { "code": 47, "label": "/", "testTag": "keyboard_middle_1" },
          { "code": 58, "label": ":", "testTag": "keyboard_middle_2" },
          { "code": 59, "label": ";", "testTag": "keyboard_middle_3" },
          { "code": 40, "label": "(", "testTag": "keyboard_middle_4" },
          { "code": 41, "label": ")", "testTag": "keyboard_middle_5" },
          { "code": 36, "label": "$", "testTag": "keyboard_middle_6" },
          { "code": 38, "label": "&", "testTag": "keyboard_middle_7" },
          { "code": 64, "label": "@", "testTag": "keyboard_middle_8" },
          { "code": 34, "label": "\"", "testTag": "keyboard_middle_9" }
        ],
        [
          { "code": 35, "label": "#", "testTag": "keyboard_symbol_0" },
          { "code": 37, "label": "%", "testTag": "keyboard_symbol_1" },
          { "code": 42, "label": "*", "testTag": "keyboard_symbol_2" },
          { "code": 43, "label": "+", "testTag": "keyboard_symbol_3" },
          { "code": 61, "label": "=", "testTag": "keyboard_symbol_4" },
          { "code": 33, "label": "!", "testTag": "keyboard_symbol_5" },
          { "code": 63, "label": "?", "testTag": "keyboard_symbol_6" },
          { "code": 39, "label": "'", "testTag": "keyboard_symbol_7" },
          { "type": "enter_editing", "code": -5, "label": "⌫", "widthFactor": 1.3, "testTag": "keyboard_backspace" }
        ],
        [
          { "type": "system_gui", "code": -201, "label": "ABC", "widthFactor": 1.5, "testTag": "keyboard_symbols" },
          { "type": "function", "code": -207, "label": "✕", "widthFactor": 0.9, "testTag": "keyboard_clear" },
          { "code": 44, "label": ",", "widthFactor": 0.8, "testTag": "keyboard_comma" },
          { "code": 32, "label": "Space", "widthFactor": 2.6, "testTag": "keyboard_space" },
          { "code": 46, "label": ".", "widthFactor": 0.8, "testTag": "keyboard_period" },
          { "type": "enter_editing", "code": 10, "label": "↵", "widthFactor": 1.1, "testTag": "keyboard_enter" }
        ]
      ]
    }
    """

    private const val EMBEDDED_SYMBOLS2_WESTERN_JSON = """
    {
      "type": "symbols2",
      "name": "western",
      "direction": "ltr",
      "arrangement": [
        [
          { "code": 126, "label": "~" },
          { "code": 96, "label": "`" },
          { "code": 124, "label": "|" },
          { "code": 8226, "label": "•" },
          { "code": 8730, "label": "√" },
          { "code": 960, "label": "π" },
          { "code": 247, "label": "÷" },
          { "code": 215, "label": "×" },
          { "code": 182, "label": "¶" },
          { "code": 8710, "label": "∆" }
        ],
        [
          { "code": 163, "label": "£" },
          { "code": 8364, "label": "€" },
          { "code": 165, "label": "¥" },
          { "code": 162, "label": "¢" },
          { "code": 94, "label": "^" },
          { "code": 176, "label": "°" },
          { "code": 61, "label": "=" },
          { "code": 123, "label": "{" },
          { "code": 125, "label": "}" },
          { "code": 92, "label": "\\" }
        ],
        [
          { "type": "system_gui", "code": -202, "label": "?123", "widthFactor": 1.2, "testTag": "keyboard_symbols_primary" },
          { "code": 37, "label": "%" },
          { "code": 95, "label": "_" },
          { "code": 169, "label": "©" },
          { "code": 174, "label": "®" },
          { "code": 8482, "label": "™" },
          { "code": 91, "label": "[" },
          { "code": 93, "label": "]" },
          { "type": "enter_editing", "code": -5, "label": "⌫", "widthFactor": 1.2, "testTag": "keyboard_backspace" }
        ],
        [
          { "type": "system_gui", "code": -201, "label": "ABC", "widthFactor": 1.4, "testTag": "keyboard_symbols" },
          { "code": 60, "label": "<", "widthFactor": 1.0 },
          { "code": 32, "label": "Space", "widthFactor": 4.0, "testTag": "keyboard_space" },
          { "code": 62, "label": ">", "widthFactor": 1.0 },
          { "type": "enter_editing", "code": 10, "label": "↵", "widthFactor": 1.4, "testTag": "keyboard_enter" }
        ]
      ]
    }
    """

    private const val EMBEDDED_T9_JSON = """
    {
      "type": "t9",
      "name": "default",
      "direction": "ltr",
      "arrangement": [
        [
          { "code": 49, "label": "1", "t9Chars": "1", "popup": { "main": { "code": 46, "label": "@.-" } } },
          { "code": 50, "label": "2", "t9Chars": "abc", "popup": { "main": { "code": 50, "label": "ABC" } } },
          { "code": 51, "label": "3", "t9Chars": "def", "popup": { "main": { "code": 51, "label": "DEF" } } }
        ],
        [
          { "code": 52, "label": "4", "t9Chars": "ghi", "popup": { "main": { "code": 52, "label": "GHI" } } },
          { "code": 53, "label": "5", "t9Chars": "jkl", "popup": { "main": { "code": 53, "label": "JKL" } } },
          { "code": 54, "label": "6", "t9Chars": "mno", "popup": { "main": { "code": 54, "label": "MNO" } } }
        ],
        [
          { "code": 55, "label": "7", "t9Chars": "pqrs", "popup": { "main": { "code": 55, "label": "PQRS" } } },
          { "code": 56, "label": "8", "t9Chars": "tuv", "popup": { "main": { "code": 56, "label": "TUV" } } },
          { "code": 57, "label": "9", "t9Chars": "wxyz", "popup": { "main": { "code": 57, "label": "WXYZ" } } }
        ],
        [
          { "type": "system_gui", "code": -201, "label": "ABC" },
          { "code": 32, "label": "0", "t9Chars": " ", "popup": { "main": { "code": 32, "label": "␣" } } },
          { "type": "enter_editing", "code": -5, "label": "⌫" }
        ]
      ]
    }
    """

    private const val EMBEDDED_NUMERIC_JSON = """
    {
      "type": "numeric",
      "name": "default",
      "direction": "ltr",
      "arrangement": [
        [
          { "type": "numeric", "code": 49, "label": "1" },
          { "type": "numeric", "code": 50, "label": "2" },
          { "type": "numeric", "code": 51, "label": "3" }
        ],
        [
          { "type": "numeric", "code": 52, "label": "4" },
          { "type": "numeric", "code": 53, "label": "5" },
          { "type": "numeric", "code": 54, "label": "6" }
        ],
        [
          { "type": "numeric", "code": 55, "label": "7" },
          { "type": "numeric", "code": 56, "label": "8" },
          { "type": "numeric", "code": 57, "label": "9" }
        ],
        [
          { "type": "function", "code": -207, "label": "CLR", "widthFactor": 0.85, "testTag": "keyboard_clear" },
          { "type": "numeric", "code": 48, "label": "0", "widthFactor": 1.0 },
          { "type": "enter_editing", "code": -5, "label": "⌫", "widthFactor": 0.85, "testTag": "keyboard_backspace" },
          { "type": "enter_editing", "code": 10, "label": "DONE", "widthFactor": 1.1, "testTag": "keyboard_enter" }
        ]
      ]
    }
    """
}
