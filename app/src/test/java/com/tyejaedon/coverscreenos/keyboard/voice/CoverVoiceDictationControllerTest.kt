package com.tyejaedon.coverscreenos.keyboard.voice

import com.tyejaedon.coverscreenos.overlay.input.BufferDelta
import com.tyejaedon.coverscreenos.overlay.input.CoverFieldMetadata
import com.tyejaedon.coverscreenos.overlay.input.CoverInputSessionManager
import com.tyejaedon.coverscreenos.overlay.input.InjectionMethod
import com.tyejaedon.coverscreenos.overlay.input.TextInjectionTarget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoverVoiceDictationControllerTest {

    private val recordedDeltas = mutableListOf<BufferDelta>()

    @Before
    fun setUp() {
        recordedDeltas.clear()
        CoverInputSessionManager.setInjectionTargetForTest(object : TextInjectionTarget {
            override fun applyDelta(delta: BufferDelta): InjectionMethod {
                recordedDeltas += delta
                return InjectionMethod.IME_INPUT_CONNECTION
            }

            override fun dispatchDone() = Unit
        })
        CoverInputSessionManager.onFieldFocused(
            CoverFieldMetadata(packageName = "com.example.notes")
        )
    }

    @After
    fun tearDown() {
        CoverInputSessionManager.clearInjectionTargetForTest()
    }

    @Test
    fun `formatDictatedPhrase handles empty buffer, trailing space, and word separation`() {
        assertEquals("hello world", formatDictatedPhrase("", "  hello world  "))
        assertEquals("hello", formatDictatedPhrase("hi ", "hello"))
        assertEquals(" world", formatDictatedPhrase("hello", "world"))
        assertEquals("", formatDictatedPhrase("hello", "   "))
    }

    @Test
    fun `commitDictatedText appends space-separated voice phrase cleanly`() {
        CoverInputSessionManager.appendText("Running")
        recordedDeltas.clear()

        CoverInputSessionManager.commitDictatedText("late today")

        assertEquals("Running late today", CoverInputSessionManager.sessionState.value.buffer)
        assertEquals(1, recordedDeltas.size)
        assertEquals(" late today", recordedDeltas.single().insert)
    }

    @Test
    fun `commitDictatedText is ignored for password fields`() {
        CoverInputSessionManager.onFieldFocused(
            CoverFieldMetadata(packageName = "com.example.bank", isPassword = true)
        )

        CoverInputSessionManager.commitDictatedText("secret phrase")

        assertEquals("", CoverInputSessionManager.sessionState.value.buffer)
        assertTrue(recordedDeltas.isEmpty())
    }
}
