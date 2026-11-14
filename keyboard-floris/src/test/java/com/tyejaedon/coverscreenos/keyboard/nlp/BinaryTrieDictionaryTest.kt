package com.tyejaedon.coverscreenos.keyboard.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer

class BinaryTrieDictionaryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val sampleVocabulary = mapOf(
        "hello" to 1000L,
        "help" to 800L,
        "helicopter" to 200L,
        "hero" to 500L,
        "world" to 900L,
        "work" to 700L,
        "worker" to 400L,
        "the" to 10000L,
        "there" to 5000L,
        "their" to 4000L
    )

    @Test
    fun `compile and predict returns identical results to CompactTrieDictionary`() {
        val compactTrie = CompactTrieDictionary(sampleVocabulary)
        val compiledBuffer = BinaryTrieDictionary.compile(compactTrie)
        val binaryTrie = BinaryTrieDictionary(compiledBuffer)

        assertEquals(sampleVocabulary.size, binaryTrie.wordCount)

        // Exact matches
        assertTrue(binaryTrie.contains("hello"))
        assertTrue(binaryTrie.contains("hero"))
        assertTrue(binaryTrie.contains("world"))
        assertFalse(binaryTrie.contains("unknown"))

        assertEquals(1000L, binaryTrie.getFrequency("hello"))
        assertEquals(800L, binaryTrie.getFrequency("help"))
        assertEquals(0L, binaryTrie.getFrequency("missing"))

        // Prefix predictions
        val compactCompletions = compactTrie.predictForPrefix("hel", 5).map { it.word }
        val binaryCompletions = binaryTrie.predictForPrefix("hel", 5).map { it.word }
        assertEquals(compactCompletions, binaryCompletions)
    }

    @Test
    fun `memory map from file matches in-memory compiled trie`() {
        val compactTrie = CompactTrieDictionary(sampleVocabulary)
        val trieFile = File(tempFolder.root, "test_trie.bin")
        BinaryTrieDictionary.writeToFile(compactTrie, trieFile)

        assertTrue(trieFile.exists())
        assertTrue(trieFile.length() > BinaryTrieDictionary.HEADER_SIZE)

        val mappedTrie = BinaryTrieDictionary.mapFromFile(trieFile)
        assertEquals(sampleVocabulary.size, mappedTrie.wordCount)
        assertTrue(mappedTrie.contains("helicopter"))
        assertEquals(200L, mappedTrie.getFrequency("helicopter"))

        val completions = mappedTrie.predictForPrefix("wor", 3).map { it.word }
        assertEquals(listOf("world", "work", "worker"), completions)
    }

    @Test
    fun `predictForDigits successfully decodes T9 sequences`() {
        val compactTrie = CompactTrieDictionary(sampleVocabulary)
        val compiledBuffer = BinaryTrieDictionary.compile(compactTrie)
        val binaryTrie = BinaryTrieDictionary(compiledBuffer)

        // "43556" -> "hello"
        val helloCandidates = binaryTrie.predictForDigits("43556", 5).map { it.word }
        assertTrue("hello" in helloCandidates)

        // "4357" -> "help" ('4'->ghi, '3'->def, '5'->jkl, '7'->pqrs)
        val candidates4357 = binaryTrie.predictForDigits("4357", 5).map { it.word }
        assertTrue("help" in candidates4357)

        // "4376" -> "hero" ('4'->ghi, '3'->def, '7'->pqrs, '6'->mno)
        val candidates4376 = binaryTrie.predictForDigits("4376", 5).map { it.word }
        assertTrue("hero" in candidates4376)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid magic header`() {
        val corruptBuffer = ByteBuffer.allocate(32)
        corruptBuffer.putInt(0x12345678) // Bad magic
        corruptBuffer.position(0)
        BinaryTrieDictionary(corruptBuffer)
    }
}
