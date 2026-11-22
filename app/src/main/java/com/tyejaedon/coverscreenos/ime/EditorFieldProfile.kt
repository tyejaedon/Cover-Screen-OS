package com.tyejaedon.coverscreenos.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Classified capabilities and privacy constraints derived from a target editor's [EditorInfo].
 *
 * @property isNumeric True when the field expects numeric, phone, or date/time input.
 * @property isSensitive True when the field accepts a password or PIN, disabling suggestions and key popups.
 */
internal data class EditorFieldProfile(
    val isNumeric: Boolean,
    val isSensitive: Boolean
) {
    companion object {
        /**
         * Parses [EditorInfo.inputType] using Android's [InputType] bitmasks to extract both the
         * base input class ([InputType.TYPE_MASK_CLASS]) and subtype variation
         * ([InputType.TYPE_MASK_VARIATION]).
         */
        fun from(editorInfo: EditorInfo?): EditorFieldProfile {
            val inputType = editorInfo?.inputType ?: 0
            // InputType packs base class, variation, and behavioral flags into one bitmask int.
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION

            val isNumericClass = inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME

            // Cover all standard text and numeric password variations across classic and web views.
            val isPasswordField = variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD

            return EditorFieldProfile(
                isNumeric = isNumericClass,
                isSensitive = isPasswordField
            )
        }
    }
}

