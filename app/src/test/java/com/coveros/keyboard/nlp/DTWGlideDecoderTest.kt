package com.coveros.keyboard.nlp

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DTWGlideDecoderTest {

    @Test
    fun testResampleFixedCount() {
        val rawPoints = listOf(
            Offset(0f, 0f),
            Offset(10f, 0f),
            Offset(20f, 10f),
            Offset(30f, 30f)
        )

        val resampled = DTWGlideDecoder.resample(rawPoints, 32)
        assertEquals(32, resampled.size)
        // First and last points should match
        assertEquals(rawPoints.first(), resampled.first())
        assertEquals(rawPoints.last(), resampled.last())
    }

    @Test
    fun testResampleSingleOrEmptyPointList() {
        val empty = DTWGlideDecoder.resample(emptyList(), 32)
        assertTrue(empty.isEmpty())

        val single = listOf(Offset(5f, 5f))
        val resampledSingle = DTWGlideDecoder.resample(single, 32)
        assertEquals(32, resampledSingle.size)
        assertTrue(resampledSingle.all { it == Offset(5f, 5f) })
    }

    @Test
    fun testDecodeGlidePathQuick() {
        val dictionary = CompactTrieDictionary(
            mapOf(
                "quick" to 5000L,
                "quiet" to 3000L,
                "queen" to 2000L,
                "hello" to 8000L,
                "world" to 6000L
            )
        )

        val decoder = DTWGlideDecoder(dictionary)

        // Synthesize a gesture path for "quick" ('q' -> 'u' -> 'i' -> 'c' -> 'k')
        val keyQ = QwertyGeometry.getCentroid('q')!!
        val keyU = QwertyGeometry.getCentroid('u')!!
        val keyI = QwertyGeometry.getCentroid('i')!!
        val keyC = QwertyGeometry.getCentroid('c')!!
        val keyK = QwertyGeometry.getCentroid('k')!!

        val path = listOf(
            keyQ,
            Offset((keyQ.x + keyU.x) / 2f, (keyQ.y + keyU.y) / 2f),
            keyU,
            keyI,
            Offset((keyI.x + keyC.x) / 2f, (keyI.y + keyC.y) / 2f),
            keyC,
            keyK
        )

        val candidates = decoder.decodeGlidePath(path, limit = 3)
        assertTrue("Candidates should not be empty", candidates.isNotEmpty())
        assertEquals("quick", candidates[0].word)
        assertTrue("Top candidate should be autocorrect default", candidates[0].isAutocorrectDefault)
    }

    @Test
    fun testDecodeGlidePathHello() {
        val dictionary = CompactTrieDictionary(
            mapOf(
                "hello" to 10000L,
                "help" to 4000L,
                "hero" to 2000L,
                "quick" to 5000L
            )
        )

        val decoder = DTWGlideDecoder(dictionary)

        // Synthesize path for "hello" ('h' -> 'e' -> 'l' -> 'o')
        val keyH = QwertyGeometry.getCentroid('h')!!
        val keyE = QwertyGeometry.getCentroid('e')!!
        val keyL = QwertyGeometry.getCentroid('l')!!
        val keyO = QwertyGeometry.getCentroid('o')!!

        val path = listOf(keyH, keyE, keyL, keyO)
        val candidates = decoder.decodeGlidePath(path, limit = 3)

        assertTrue("Candidates should not be empty", candidates.isNotEmpty())
        assertEquals("hello", candidates[0].word)
    }

    @Test
    fun testDecodeGlidePathShortPathReturnsEmpty() {
        val dictionary = CompactTrieDictionary(mapOf("test" to 100L))
        val decoder = DTWGlideDecoder(dictionary)

        val results = decoder.decodeGlidePath(listOf(Offset(1f, 1f)))
        assertTrue(results.isEmpty())
    }
}
