package com.tyejaedon.coverscreenos.overlay.input

import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.tyejaedon.coverscreenos.overlay.surface.CoverSurfaceWindowConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FieldActionAndFocusLossTest {
    @Test
    fun `URI variation is not confused with numeric password variation`() {
        assertFalse(isPasswordInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI))
        assertTrue(isPasswordInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertTrue(isPasswordInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
    }

    @Test
    fun `imeOptions are masked and inference respects URI and multiline fields`() {
        val extras = Bundle().apply {
            putInt("imeOptions", EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI)
        }
        assertEquals(
            EditorInfo.IME_ACTION_SEARCH,
            resolveImeAction(extras, InputType.TYPE_CLASS_TEXT, false)
        )
        assertEquals(
            EditorInfo.IME_ACTION_GO,
            resolveImeAction(null, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, false)
        )
        assertEquals(
            EditorInfo.IME_ACTION_SEND,
            resolveImeAction(
                null, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE, false
            )
        )
        assertEquals(
            EditorInfo.IME_ACTION_NONE,
            resolveImeAction(null, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, true)
        )
    }

    @Test
    fun `dismiss requires noneditable focus and 400ms continuous absence`() {
        val gate = FocusLossGate()
        assertFalse(gate.shouldDismiss(500, true))
        gate.onNonEditableFocus(1000)
        assertFalse(gate.shouldDismiss(1000, true))
        assertFalse(gate.shouldDismiss(1399, true))
        assertTrue(gate.shouldDismiss(1400, true))
        gate.onEditableFocus()
        assertFalse(gate.shouldDismiss(2000, true))
    }

    @Test
    fun `editable focus and content churn reset loss hold`() {
        val gate = FocusLossGate()
        gate.onNonEditableFocus(1000)
        assertFalse(gate.shouldDismiss(1000, true))
        gate.onContentChurn(1250)
        assertFalse(gate.shouldDismiss(1500, true))
        assertFalse(gate.shouldDismiss(1501, false))
        assertFalse(gate.shouldDismiss(1900, true))
        assertTrue(gate.shouldDismiss(2300, true))
    }

    @Test
    fun `secure window flag applies only to password overlays`() {
        val flags = CoverSurfaceWindowConfig(
            extraFlags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        assertEquals(
            WindowManager.LayoutParams.FLAG_SECURE,
            flags.flags(true) and WindowManager.LayoutParams.FLAG_SECURE
        )
        assertEquals(0, flags.flags(false) and WindowManager.LayoutParams.FLAG_SECURE)
    }

    @Test
    fun `IME action dispatch maps submit and next to accessibility actions`() {
        listOf(
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_DONE
        ).forEach { action ->
            assertEquals(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id,
                accessibilityActionForIme(action)
            )
        }
        assertEquals(
            AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT,
            accessibilityActionForIme(EditorInfo.IME_ACTION_NEXT)
        )
        assertEquals(
            AccessibilityNodeInfo.ACTION_CLICK,
            accessibilityActionForIme(EditorInfo.IME_ACTION_UNSPECIFIED)
        )
    }
}
