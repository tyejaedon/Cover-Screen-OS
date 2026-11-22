package com.tyejaedon.coverscreenos.overlay.input

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoverClipboardInjectionRestoreTest {

    private fun buildEditableNode(
        existingText: String = "hello",
        isPassword: Boolean = false,
        supportsPaste: Boolean = true
    ): AccessibilityNodeInfo {
        val actions = buildList {
            add(AccessibilityNodeInfo.AccessibilityAction.ACTION_FOCUS)
            add(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_SELECTION)
            if (supportsPaste) {
                add(AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE)
            }
        }
        return mockk(relaxed = true) {
            every { packageName } returns "com.example.chat"
            every { text } returns existingText
            every { isShowingHintText } returns false
            every { this@mockk.isPassword } returns isPassword
            every { inputType } returns 0
            every { actionList } returns actions
            every { getBoundsInScreen(any()) } answers {
                firstArg<Rect>().set(0, 0, 400, 80)
            }
            every { performAction(any()) } returns true
            every { performAction(any(), any()) } returns true
        }
    }

    @Test
    fun `injectDeltaViaClipboard selects delta span and restores previous primary clip`() {
        val service = Robolectric.buildService(CoverInputAccessibilityService::class.java).create().get()
        val clipboard = RuntimeEnvironment.getApplication()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("user_note", "important OTP backup"))

        val node = buildEditableNode(existingText = "helo")
        val delta = BufferDelta.compute(old = "helo", new = "hello", newCaret = 4)

        val ok = service.injectDeltaViaClipboard(delta = delta, targetNode = node, clipboard = clipboard)

        assertTrue(ok)
        val selectionSlot = slot<Bundle>()
        verify {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, capture(selectionSlot))
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
        assertEquals(delta.replaceStart, selectionSlot.captured.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT))
        assertEquals(delta.replaceEnd, selectionSlot.captured.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT))
        assertEquals(INTERNAL_CLIP_LABEL, clipboard.primaryClipDescription?.label?.toString())
        assertEquals(delta.insert, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        shadowOf(RuntimeEnvironment.getApplication().mainLooper)
            .idleFor(CLIPBOARD_RESTORE_DELAY_MS + 50L, TimeUnit.MILLISECONDS)

        assertEquals("user_note", clipboard.primaryClipDescription?.label?.toString())
        assertEquals("important OTP backup", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun `injectDeltaViaClipboard clears temporary clip when clipboard was previously empty`() {
        val service = Robolectric.buildService(CoverInputAccessibilityService::class.java).create().get()
        val clipboard = RuntimeEnvironment.getApplication()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.clearPrimaryClip()

        val node = buildEditableNode(existingText = "")
        val delta = BufferDelta.compute(old = "", new = "hi", newCaret = 2)

        assertTrue(service.injectDeltaViaClipboard(delta = delta, targetNode = node, clipboard = clipboard))
        assertEquals("hi", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        shadowOf(RuntimeEnvironment.getApplication().mainLooper)
            .idleFor(CLIPBOARD_RESTORE_DELAY_MS + 50L, TimeUnit.MILLISECONDS)

        assertNull(clipboard.primaryClip)
    }

    @Test
    fun `injectDeltaViaClipboard refuses password nodes without touching clipboard`() {
        val service = Robolectric.buildService(CoverInputAccessibilityService::class.java).create().get()
        val clipboard = RuntimeEnvironment.getApplication()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("user_note", "untouched"))

        val passwordNode = buildEditableNode(existingText = "123", isPassword = true)
        val delta = BufferDelta.compute(old = "123", new = "1234", newCaret = 4)

        assertFalse(service.injectDeltaViaClipboard(delta = delta, targetNode = passwordNode, clipboard = clipboard))
        verify(exactly = 0) { passwordNode.performAction(AccessibilityNodeInfo.ACTION_PASTE) }
        assertEquals("untouched", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }
}
