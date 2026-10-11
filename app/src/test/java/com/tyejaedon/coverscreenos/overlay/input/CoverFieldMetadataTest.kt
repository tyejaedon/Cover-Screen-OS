package com.tyejaedon.coverscreenos.overlay.input

import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression coverage for the hint/placeholder-text buffer leak: Android's
 * `AccessibilityNodeInfo.getText()` returns the field's *hint* (not its real
 * content) while the field is empty and showing a placeholder on many apps.
 * [CoverFieldMetadata.fromNode] must treat that as an empty buffer, not seed
 * it as if the user had already typed the placeholder.
 */
class CoverFieldMetadataTest {

    private fun fakeNode(
        text: CharSequence?,
        isShowingHintText: Boolean,
        hintText: CharSequence? = "Search apps"
    ): AccessibilityNodeInfo {
        val node = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { node.text } returns text
        every { node.isShowingHintText } returns isShowingHintText
        every { node.hintText } returns hintText
        every { node.textSelectionStart } returns -1
        every { node.textSelectionEnd } returns -1
        return node
    }

    @Test
    fun `node showing hint text yields an empty initial buffer`() {
        val node = fakeNode(text = "Search apps", isShowingHintText = true)

        val metadata = CoverFieldMetadata.fromNode(node)

        assertEquals("", metadata.initialText)
        assertEquals("Search apps", metadata.hintText)
    }

    @Test
    fun `node with real typed content keeps its initial buffer`() {
        val node = fakeNode(text = "hello world", isShowingHintText = false)

        val metadata = CoverFieldMetadata.fromNode(node)

        assertEquals("hello world", metadata.initialText)
    }

    @Test
    fun `empty non-hint text yields an empty buffer`() {
        val node = fakeNode(text = "", isShowingHintText = false)

        val metadata = CoverFieldMetadata.fromNode(node)

        assertEquals("", metadata.initialText)
    }
}
