package com.tyejaedon.coverscreenos.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorFieldProfileTest {

    @Test
    fun `numeric input type is detected`() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val profile = EditorFieldProfile.from(editorInfo)

        assertTrue(profile.isNumeric)
        assertFalse(profile.isSensitive)
    }

    @Test
    fun `password variation is marked sensitive`() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val profile = EditorFieldProfile.from(editorInfo)

        assertFalse(profile.isNumeric)
        assertTrue(profile.isSensitive)
    }

    @Test
    fun `null editor info falls back to non numeric`() {
        val profile = EditorFieldProfile.from(null)

        assertFalse(profile.isNumeric)
        assertFalse(profile.isSensitive)
    }

    @Test
    fun `phone input class is treated as numeric`() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_PHONE
        }

        val profile = EditorFieldProfile.from(editorInfo)

        assertTrue(profile.isNumeric)
        assertFalse(profile.isSensitive)
    }

    @Test
    fun `number password is numeric and sensitive`() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        val profile = EditorFieldProfile.from(editorInfo)

        assertTrue(profile.isNumeric)
        assertTrue(profile.isSensitive)
    }
}

