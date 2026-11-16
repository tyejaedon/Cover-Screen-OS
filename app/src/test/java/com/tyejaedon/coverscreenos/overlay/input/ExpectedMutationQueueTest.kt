package com.tyejaedon.coverscreenos.overlay.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpectedMutationQueueTest {

    @Test
    fun `consumes matching echo and supersedes older entries`() {
        val queue = ExpectedMutationQueue(staleThresholdMs = 1200L)
        queue.enqueue(text = "h", caret = 1, nowMs = 100L)
        queue.enqueue(text = "he", caret = 2, nowMs = 150L)

        // Matching "he" consumes both "h" and "he"
        assertTrue(queue.consumeIfExpected("he", nowMs = 200L))
        // Subsequent "he" is no longer in the queue
        assertFalse(queue.consumeIfExpected("he", nowMs = 210L))
        // "h" was superseded and should not match
        assertFalse(queue.consumeIfExpected("h", nowMs = 220L))
    }

    @Test
    fun `external mismatch returns false without consuming valid pending echoes`() {
        val queue = ExpectedMutationQueue(staleThresholdMs = 1200L)
        queue.enqueue(text = "123", caret = 3, nowMs = 100L)

        // App auto-formats into "(123) " -> mismatch
        assertFalse(queue.consumeIfExpected("(123) ", nowMs = 120L))
        // Original pending echo is still valid if it arrives slightly later
        assertTrue(queue.consumeIfExpected("123", nowMs = 150L))
    }

    @Test
    fun `prunes stale entries past threshold`() {
        val queue = ExpectedMutationQueue(staleThresholdMs = 1000L)
        queue.enqueue(text = "old", caret = 3, nowMs = 100L)

        // 1500ms later, entry should be pruned
        assertFalse(queue.consumeIfExpected("old", nowMs = 1600L))
    }
}
