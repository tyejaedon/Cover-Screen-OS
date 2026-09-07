package com.tyejaedon.coverscreenos.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

internal data class EditorFieldProfile(
    val isNumeric: Boolean,
    val isSensitive: Boolean
) {
    companion object {
        fun from(editorInfo: EditorInfo?): EditorFieldProfile {
            val inputType = editorInfo?.inputType ?: 0
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION

            val isNumericClass = inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME

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

