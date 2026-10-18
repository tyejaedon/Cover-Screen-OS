package com.coveros.keyboard.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SymSpellAutocorrectTest {

    private val vocabulary = mapOf(
        "the" to 100000L,
        "there" to 20000L,
        "their" to 15000L,
        "hello" to 8000L,
        "help" to 5000L,
        "world" to 7000L,
        "quick" to 3000L,
        "brown" to 3000L,
        "fox" to 2000L
    )

    @Test
    fun testExactMatchReturnsHighConfidence() {
        val autocorrect = SymSpellAutocorrect(vocabulary)
        val results = autocorrect.suggest("hello", limit = 3)

        assertTrue(results.isNotEmpty())
        assertEquals("hello", results[0].word)
        assertTrue(results[0].isAutocorrectDefault)
    }

    @Test
    fun testAdjacentKeyTypoCorrection() {
        val autocorrect = SymSpellAutocorrect(vocabulary)
        // User typed "thw" (adjacent 'w' instead of 'e')
        val results = autocorrect.suggest("thw", limit = 3)

        assertTrue(results.isNotEmpty())
        assertEquals("the", results[0].word)
        assertTrue(results[0].isAutocorrectDefault)
    }

    @Test
    fun testTranspositionTypoCorrection() {
        val autocorrect = SymSpellAutocorrect(vocabulary)
        // User typed "teh" instead of "the"
        val results = autocorrect.suggest("teh", limit = 3)

        assertTrue(results.isNotEmpty())
        assertEquals("the", results[0].word)
        assertTrue(results[0].isAutocorrectDefault)
    }

    @Test
    fun testGetBestCorrectionSingleHelper() {
        val autocorrect = SymSpellAutocorrect(vocabulary)
        val best = autocorrect.correct("thw")

        assertNotNull(best)
        assertEquals("the", best?.word)
        assertTrue(best?.isAutocorrectDefault == true)
    }
}
