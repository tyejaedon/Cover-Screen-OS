package com.tyejaedon.coverscreenos.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverBufferOverlayCallbacksTest {

    @Test
    fun `onAppend delegates single char when onCharTyped is provided`() {
        var text = "hi"
        val typed = mutableListOf<Char>()
        val callbacks = CoverBufferOverlayCallbacks(
            getText = { text },
            setText = { text = it },
            onCharTyped = {
                typed.add(it)
                text += it
            }
        )

        callbacks.onAppend("!")
        assertEquals(listOf('!'), typed)
        assertEquals("hi!", text)

        callbacks.onAppend(" there")
        assertEquals("hi! there", text)
    }

    @Test
    fun `onCommitCandidate replaces active word and appends trailing space`() {
        var text = "say hel"
        val callbacks = CoverBufferOverlayCallbacks(
            getText = { text },
            setText = { text = it }
        )

        callbacks.onCommitCandidate("hello")
        assertEquals("say hello ", text)
    }

    @Test
    fun `onDeletePreviousWord removes trailing word`() {
        var text = "hello world "
        val callbacks = CoverBufferOverlayCallbacks(
            getText = { text },
            setText = { text = it }
        )

        callbacks.onDeletePreviousWord()
        assertEquals("hello ", text)
    }

    @Test
    fun `replaceText clamps out of bounds range safely`() {
        var text = "abc"
        val callbacks = CoverBufferOverlayCallbacks(
            getText = { text },
            setText = { text = it }
        )

        callbacks.replaceText(1..10, "XYZ", 4)
        assertEquals("aXYZ", text)
    }
}
