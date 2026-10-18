package com.coveros.keyboard.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9PredictiveEngineTest {

    @Test
    fun testCharToDigitAndWordToDigits() {
        assertEquals('2', T9PredictiveEngine.charToDigit('a'))
        assertEquals('2', T9PredictiveEngine.charToDigit('B'))
        assertEquals('2', T9PredictiveEngine.charToDigit('c'))
        assertEquals('3', T9PredictiveEngine.charToDigit('d'))
        assertEquals('9', T9PredictiveEngine.charToDigit('z'))

        assertEquals("43556", T9PredictiveEngine.wordToDigits("hello"))
        assertEquals("96753", T9PredictiveEngine.wordToDigits("world"))
    }

    @Test
    fun testDecodeMultiTapGrouped() {
        // "44" -> h, "33" -> e, "555" -> l, "555" -> l, "666" -> o
        val decoded = T9PredictiveEngine.decodeMultiTapGrouped("44-33-555-555-666")
        assertEquals("hello", decoded)

        // Single taps: "2-3-4" -> "adg"
        val single = T9PredictiveEngine.decodeMultiTapGrouped("2-3-4")
        assertEquals("adg", single)
    }

    @Test
    fun testPredictForDigitsHello() {
        val dictionary = CompactTrieDictionary(
            mapOf(
                "hello" to 10000L,
                "hells" to 1000L,
                "world" to 8000L,
                "help" to 5000L
            )
        )

        val engine = T9PredictiveEngine(dictionary)
        val candidates = engine.predictForDigits("43556", limit = 3)

        assertTrue(candidates.isNotEmpty())
        assertEquals("hello", candidates[0].word)
        assertTrue(candidates[0].isAutocorrectDefault)
    }

    @Test
    fun testPredictForDigitsPrefixCompletions() {
        val dictionary = CompactTrieDictionary(
            mapOf(
                "hello" to 10000L,
                "help" to 5000L,
                "helmet" to 2000L
            )
        )

        val engine = T9PredictiveEngine(dictionary)
        // 435 corresponds to "hel"
        val candidates = engine.predictForDigits("435", limit = 3)

        assertTrue(candidates.isNotEmpty())
        // Should suggest completions like hello, help, helmet
        val words = candidates.map { it.word }
        assertTrue(words.contains("hello"))
        assertTrue(words.contains("help"))
    }

    @Test
    fun testLearnedWordsBoost() {
        val dictionary = CompactTrieDictionary(
            mapOf(
                "good" to 5000L,
                "home" to 1000L // 4663
            )
        )

        // "home" is learned, even though "good" (4663) has higher corpus frequency
        val engine = T9PredictiveEngine(
            dictionary = dictionary,
            learnedWords = setOf("home")
        )

        val candidates = engine.predictForDigits("4663", limit = 2)
        assertEquals("home", candidates[0].word)
    }

    @Test
    fun testSuppressedWordsExcluded() {
        val dictionary = CompactTrieDictionary(
            mapOf(
                "hello" to 10000L,
                "gekko" to 1000L
            )
        )

        val engine = T9PredictiveEngine(
            dictionary = dictionary,
            suppressedWords = setOf("hello")
        )

        val candidates = engine.predictForDigits("43556", limit = 2)
        assertEquals("gekko", candidates[0].word)
        assertFalse(candidates.any { it.word == "hello" })
    }

    @Test
    fun testEmptyOrInvalidDigitsReturnEmpty() {
        val dictionary = CompactTrieDictionary(mapOf("test" to 100L))
        val engine = T9PredictiveEngine(dictionary)

        assertTrue(engine.predictForDigits("").isEmpty())
        assertTrue(engine.predictForDigits("000").isEmpty()) // 0 not in '2'..'9'
        assertTrue(engine.predictForDigits("43556", limit = 0).isEmpty())
    }
}
