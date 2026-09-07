package com.tyejaedon.coverscreenos.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class T9EngineTest {

    @Test
    fun `first tap appends first letter`() {
        val engine = T9Engine()

        val action = engine.onTap(
            digit = '2',
            letters = "ABC",
            nowElapsedMs = 10L,
            forceDigit = false
        )

        assertEquals(T9CommitAction.Append("a"), action)
    }

    @Test
    fun `rapid repeated tap cycles letter`() {
        val engine = T9Engine()

        val first = engine.onTap(
            digit = '2',
            letters = "ABC",
            nowElapsedMs = 100L,
            forceDigit = false
        )
        val second = engine.onTap(
            digit = '2',
            letters = "ABC",
            nowElapsedMs = 300L,
            forceDigit = false
        )

        assertEquals(T9CommitAction.Append("a"), first)
        assertEquals(T9CommitAction.ReplacePrevious("b"), second)
    }

    @Test
    fun `numeric fields force digit commit`() {
        val engine = T9Engine()

        val action = engine.onTap(
            digit = '7',
            letters = "PQRS",
            nowElapsedMs = 20L,
            forceDigit = true
        )

        assertEquals(T9CommitAction.Append("7"), action)
    }

    @Test
    fun `reset clears cycling state`() {
        val engine = T9Engine()

        engine.onTap(
            digit = '9',
            letters = "WXYZ",
            nowElapsedMs = 50L,
            forceDigit = false
        )
        engine.reset()
        val action = engine.onTap(
            digit = '9',
            letters = "WXYZ",
            nowElapsedMs = 100L,
            forceDigit = false
        )

        assertTrue(action is T9CommitAction.Append)
        assertEquals(T9CommitAction.Append("w"), action)
    }

    @Test
    fun `tap after timeout appends a new character`() {
        val engine = T9Engine()

        val first = engine.onTap(
            digit = '3',
            letters = "DEF",
            nowElapsedMs = 100L,
            forceDigit = false
        )
        val second = engine.onTap(
            digit = '3',
            letters = "DEF",
            nowElapsedMs = 1_101L,
            forceDigit = false
        )

        assertEquals(T9CommitAction.Append("d"), first)
        assertEquals(T9CommitAction.Append("d"), second)
    }

    @Test
    fun `tap exactly at timeout boundary still cycles`() {
        val engine = T9Engine()

        engine.onTap(
            digit = '6',
            letters = "MNO",
            nowElapsedMs = 100L,
            forceDigit = false
        )
        val boundaryTap = engine.onTap(
            digit = '6',
            letters = "MNO",
            nowElapsedMs = 1_000L,
            forceDigit = false
        )

        assertEquals(T9CommitAction.ReplacePrevious("n"), boundaryTap)
    }

    @Test
    fun `blank letters commit raw digit`() {
        val engine = T9Engine()

        val action = engine.onTap(
            digit = '1',
            letters = "",
            nowElapsedMs = 42L,
            forceDigit = false
        )

        assertEquals(T9CommitAction.Append("1"), action)
    }
}

