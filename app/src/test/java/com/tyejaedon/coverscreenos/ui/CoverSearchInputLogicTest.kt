package com.tyejaedon.coverscreenos.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverSearchInputLogicTest {

    @Test
    fun `matchesSearchQuery returns true for empty query`() {
        assertTrue(matchesSearchQuery(appName = "Spotify", packageName = "com.spotify.music", query = ""))
        assertTrue(matchesSearchQuery(appName = "Spotify", packageName = "com.spotify.music", query = "   "))
    }

    @Test
    fun `matchesSearchQuery checks name and package case-insensitively`() {
        assertTrue(matchesSearchQuery(appName = "YouTube Music", packageName = "com.google.android.apps.youtube.music", query = "music"))
        assertTrue(matchesSearchQuery(appName = "YouTube Music", packageName = "com.google.android.apps.youtube.music", query = "YOUTUBE"))
        assertTrue(matchesSearchQuery(appName = "YouTube Music", packageName = "com.google.android.apps.youtube.music", query = "google.android"))
        assertFalse(matchesSearchQuery(appName = "YouTube Music", packageName = "com.google.android.apps.youtube.music", query = "calendar"))
    }

    @Test
    fun `applyT9CharacterTap starts with first character for digit group`() {
        val result = applyT9CharacterTap(
            currentQuery = "",
            digit = '2',
            letters = "ABC",
            cycleState = T9CycleState(),
            nowElapsedMs = 1_000L
        )

        assertEquals("a", result.query)
        assertEquals('2', result.cycleState.lastDigit)
        assertEquals(0, result.cycleState.cycleIndex)
    }

    @Test
    fun `applyT9CharacterTap cycles same key within timeout`() {
        val first = applyT9CharacterTap(
            currentQuery = "",
            digit = '2',
            letters = "ABC",
            cycleState = T9CycleState(),
            nowElapsedMs = 1_000L
        )
        val second = applyT9CharacterTap(
            currentQuery = first.query,
            digit = '2',
            letters = "ABC",
            cycleState = first.cycleState,
            nowElapsedMs = 1_400L
        )
        val third = applyT9CharacterTap(
            currentQuery = second.query,
            digit = '2',
            letters = "ABC",
            cycleState = second.cycleState,
            nowElapsedMs = 1_700L
        )

        assertEquals("b", second.query)
        assertEquals("c", third.query)
        assertEquals(2, third.cycleState.cycleIndex)
    }

    @Test
    fun `applyT9CharacterTap appends a new character after timeout`() {
        val first = applyT9CharacterTap(
            currentQuery = "",
            digit = '7',
            letters = "PQRS",
            cycleState = T9CycleState(),
            nowElapsedMs = 2_000L
        )
        val second = applyT9CharacterTap(
            currentQuery = first.query,
            digit = '7',
            letters = "PQRS",
            cycleState = first.cycleState,
            nowElapsedMs = 3_200L
        )

        assertEquals("pp", second.query)
        assertEquals(0, second.cycleState.cycleIndex)
    }

    @Test
    fun `applyT9CharacterTap appends digit when no letters are provided`() {
        val result = applyT9CharacterTap(
            currentQuery = "ab",
            digit = '1',
            letters = "",
            cycleState = T9CycleState(lastDigit = '2', lastTapElapsedMs = 100L, cycleIndex = 1),
            nowElapsedMs = 300L
        )

        assertEquals("ab1", result.query)
        assertEquals('1', result.cycleState.lastDigit)
    }
}

