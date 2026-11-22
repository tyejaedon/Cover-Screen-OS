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
            // Raw bit-packed EditorInfo.inputType integer supplied by the focused app (0 if unavailable).
            val inputType = editorInfo?.inputType ?: 0
            // Base category (text, number, phone, datetime) extracted via the class bitmask.
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            // Subtype within that class (e.g., password vs. plain text) extracted via the variation bitmask.
            val variation = inputType and InputType.TYPE_MASK_VARIATION

            // True when the focused box only expects digits, phone symbols, or dates/times.
            val isNumericClass = inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME

            // True for numeric PINs and classic/web password fields so suggestions and key popups stay off.
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

