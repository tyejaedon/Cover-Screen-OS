package com.coveros.keyboard.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactTrieDictionaryTest {

    @Test
    fun testInsertAndContains() {
        val trie = CompactTrieDictionary()
        assertFalse(trie.contains("hello"))
        assertEquals(0, trie.size)

        trie.insert("hello", 500L)
        assertTrue(trie.contains("hello"))
        assertTrue(trie.contains("HELLO")) // case insensitivity
        assertEquals(500L, trie.getFrequency("hello"))
        assertEquals(1, trie.size)
    }

    @Test
    fun testPunctuationStrippingInNormalization() {
        val trie = CompactTrieDictionary()
        trie.insert("don't", 200L)

        assertTrue(trie.contains("dont"))
        assertTrue(trie.contains("don't"))
        assertEquals(200L, trie.getFrequency("dont"))
    }

    @Test
    fun testPredictForPrefixOrdering() {
        val trie = CompactTrieDictionary(
            mapOf(
                "the" to 10000L,
                "there" to 5000L,
                "their" to 4000L,
                "them" to 3000L,
                "theory" to 1000L,
                "apple" to 8000L
            )
        )

        val predictions = trie.predictForPrefix("the", limit = 4)
        assertEquals(4, predictions.size)
        // Exact prefix "the" should be first and marked default
        assertEquals("the", predictions[0].word)
        assertTrue(predictions[0].isAutocorrectDefault)

        // Following should be frequency sorted: there (5000), their (4000), them (3000)
        assertEquals("there", predictions[1].word)
        assertEquals("their", predictions[2].word)
        assertEquals("them", predictions[3].word)

        // "apple" should not appear in "the" predictions
        assertFalse(predictions.any { it.word == "apple" })
    }

    @Test
    fun testPredictForPrefixEmptyOrInvalid() {
        val trie = CompactTrieDictionary(mapOf("test" to 10L))
        assertTrue(trie.predictForPrefix("xyz", limit = 5).isEmpty())
        assertTrue(trie.predictForPrefix("test", limit = 0).isEmpty())
    }

    @Test
    fun testGetNextCharPriorsSumsToOne() {
        val trie = CompactTrieDictionary(
            mapOf(
                "cat" to 100L,
                "car" to 80L,
                "can" to 50L,
                "dog" to 70L
            )
        )

        val priors = trie.getNextCharPriors("ca")
        assertEquals(26, priors.size)

        val sum = priors.values.sum()
        assertEquals(1.0f, sum, 0.005f)

        // 't', 'r', 'n' should have significantly higher priors than 'z' or 'q'
        val priorT = priors['t'] ?: 0f
        val priorR = priors['r'] ?: 0f
        val priorN = priors['n'] ?: 0f
        val priorZ = priors['z'] ?: 0f

        assertTrue(priorT > priorZ * 5)
        assertTrue(priorR > priorZ * 5)
        assertTrue(priorN > priorZ * 5)
        assertTrue(priorT > priorR) // cat(100) > car(80)
    }

    @Test
    fun testGetNextCharPriorsEmptyPrefix() {
        val trie = CompactTrieDictionary(
            mapOf(
                "apple" to 100L,
                "banana" to 50L
            )
        )

        val priors = trie.getNextCharPriors("")
        assertEquals(26, priors.size)
        val sum = priors.values.sum()
        assertEquals(1.0f, sum, 0.005f)

        // 'a' should be highest
        assertTrue((priors['a'] ?: 0f) > (priors['b'] ?: 0f))
        assertTrue((priors['b'] ?: 0f) > (priors['z'] ?: 0f))
    }

    @Test
    fun testGetNextCharPriorsUnknownPrefixFallback() {
        val trie = CompactTrieDictionary(mapOf("test" to 10L))
        val priors = trie.getNextCharPriors("zzz")
        assertEquals(26, priors.size)

        val expectedUniform = 1.0f / 26.0f
        for (value in priors.values) {
            assertEquals(expectedUniform, value, 0.001f)
        }
    }
}
