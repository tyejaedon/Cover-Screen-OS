package com.tyejaedon.coverscreenos.keyboard.nlp

import com.tyejaedon.coverscreenos.keyboard.floris.CoverLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CoverKeyboardNlpManagerTest {

    @Before
    fun setUp() {
        val testCorpus = mapOf(
            "hello" to 1000L,
            "help" to 800L,
            "held" to 500L,
            "helicopter" to 200L,
            "good" to 900L,
            "home" to 750L,
            "world" to 600L
        )
        val trie = CompactTrieDictionary(testCorpus)
        val symSpell = SymSpellAutocorrect(testCorpus)
        val t9 = T9PredictiveEngine(trie)
        CoverKeyboardNlpManager.setEnginesForTest(trie, symSpell, t9)
    }

    @Test
    fun `getQwertySuggestions returns prefix completions in ranked order`() {
        val suggestions = CoverKeyboardNlpManager.getQwertySuggestions("hel", limit = 3)
        assertTrue("Expected suggestions to contain hello", suggestions.contains("hello"))
        assertTrue("Expected suggestions to contain help", suggestions.contains("help"))
    }

    @Test
    fun `getQwertySuggestions returns fuzzy correction on mistype`() {
        // "helo" -> "hello"
        val suggestions = CoverKeyboardNlpManager.getQwertySuggestions("helo", limit = 2)
        assertTrue("Expected suggestions to autocorrect to hello", suggestions.contains("hello"))
    }

    @Test
    fun `getT9Suggestions decodes digits sequence accurately`() {
        // 43556 -> "hello"
        val suggestions = CoverKeyboardNlpManager.getT9Suggestions("43556", limit = 3)
        assertTrue("Expected T9 to decode 43556 to hello", suggestions.contains("hello"))
    }

    @Test
    fun `getCandidates delegates to appropriate mode`() {
        val qwertyResults = CoverKeyboardNlpManager.getCandidates(CoverLayoutMode.QWERTY, "hel", 2)
        assertTrue(qwertyResults.isNotEmpty())

        val t9Results = CoverKeyboardNlpManager.getCandidates(CoverLayoutMode.T9_PREDICTIVE, "43556", 2)
        assertTrue(t9Results.contains("hello"))

        val symbolsResults = CoverKeyboardNlpManager.getCandidates(CoverLayoutMode.SYMBOLS, "hel", 2)
        assertTrue(symbolsResults.isEmpty())
    }
}
