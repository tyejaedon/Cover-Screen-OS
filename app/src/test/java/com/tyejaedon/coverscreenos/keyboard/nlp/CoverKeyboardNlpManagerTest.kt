package com.tyejaedon.coverscreenos.keyboard.nlp

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import com.tyejaedon.coverscreenos.keyboard.floris.CoverLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.zip.GZIPInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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
            "world" to 600L,
            "because" to 850L,
            "friend" to 820L,
            "people" to 810L,
            "thank" to 790L,
            "you" to 780L,
            "god" to 700L
        )
        val trie = CompactTrieDictionary(testCorpus)
        val symSpell = SymSpellAutocorrect(testCorpus)
        val t9 = T9PredictiveEngine(trie)
        CoverKeyboardNlpManager.setEnginesForTest(
            trie = trie,
            symSpell = symSpell,
            t9 = t9,
            bigrams = mapOf("thank" to listOf("you", "god"))
        )
    }

    @Test
    fun `bundled lexicon contains eighty two thousand words`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val words = GZIPInputStream(context.assets.open("english_words.bin"))
            .bufferedReader().lineSequence().mapNotNull { it.split(' ').getOrNull(0) }.toList()
        assertEquals(82_761, words.size)
        assertTrue(java.io.File("src/main/assets/english_words.bin").length() < 700_000)
    }

    @Test
    fun `getQwertySuggestions returns prefix completions in ranked order`() {
        val suggestions = CoverKeyboardNlpManager.getQwertySuggestions("hel", limit = 3)
        assertTrue("Expected suggestions to contain hello", suggestions.contains("hello"))
        assertTrue("Expected suggestions to contain help", suggestions.contains("help"))
    }

    @Test
    fun `getQwertySuggestions returns fuzzy correction on mistype`() {
        val suggestions = CoverKeyboardNlpManager.getQwertySuggestions("helo", limit = 2)
        assertTrue("Expected suggestions to autocorrect to hello", suggestions.contains("hello"))
    }

    @Test
    fun `autocorrectWord fixes mistyped and transposed words and preserves capitalization`() {
        assertEquals("hello", CoverKeyboardNlpManager.autocorrectWord("helo"))
        assertEquals("Hello", CoverKeyboardNlpManager.autocorrectWord("Helo"))
        assertEquals("help", CoverKeyboardNlpManager.autocorrectWord("help"))
        assertEquals("because", CoverKeyboardNlpManager.autocorrectWord("becuase"))
        assertEquals("friend", CoverKeyboardNlpManager.autocorrectWord("firend"))
        assertEquals("people", CoverKeyboardNlpManager.autocorrectWord("poeple"))
    }

    @Test
    fun `learnWord and suppressWord update suggestions and availability`() {
        CoverKeyboardNlpManager.learnWord("floris")
        assertTrue(CoverKeyboardNlpManager.getQwertySuggestions("flo", limit = 3).contains("floris"))

        CoverKeyboardNlpManager.suppressWord("floris")
        assertFalse(CoverKeyboardNlpManager.isWordAvailable("floris"))
        assertFalse(CoverKeyboardNlpManager.getQwertySuggestions("flo", limit = 3).contains("floris"))
    }

    @Test
    fun `suppressed words are excluded from bigram next-word suggestions`() {
        assertEquals(listOf("you", "god"), CoverKeyboardNlpManager.getNextWordSuggestions("thank"))
        CoverKeyboardNlpManager.suppressWord("god")
        assertEquals(listOf("you"), CoverKeyboardNlpManager.getNextWordSuggestions("thank"))
        assertEquals(listOf("the", "and", "to"), CoverKeyboardNlpManager.getNextWordSuggestions("xyzzy"))
    }

    @Test
    fun `getT9Suggestions decodes digits sequence and grouped multi-tap accurately`() {
        val suggestions = CoverKeyboardNlpManager.getT9Suggestions("43556", limit = 3)
        assertTrue("Expected T9 to decode 43556 to hello", suggestions.contains("hello"))

        val groupedSuggestions = CoverKeyboardNlpManager.getT9Suggestions("44-33-55-55-666", limit = 3)
        assertTrue("Expected grouped T9 to decode to hello", groupedSuggestions.contains("hello"))

        assertTrue(CoverKeyboardNlpManager.getT9Suggestions("43-33-55").isEmpty())
        assertTrue(CoverKeyboardNlpManager.getT9Suggestions("44-1-55").isEmpty())
    }

    @Test
    fun `getNextWordSuggestions and decodeGlidePath return valid words`() {
        val nextWords = CoverKeyboardNlpManager.getNextWordSuggestions("hello", limit = 3)
        assertEquals(listOf("there", "world", "everyone"), nextWords)

        val centroids = QwertyGeometry.getDefaultCentroidMap()
        val path = listOf('h', 'e', 'l', 'p').mapNotNull { centroids[it] }
        val glideWords = CoverKeyboardNlpManager.decodeGlidePath(path, limit = 3)
        assertTrue(glideWords.contains("help"))
    }

    @Test
    fun `getCandidates delegates to appropriate mode`() {
        val qwertyResults = CoverKeyboardNlpManager.getCandidates(CoverLayoutMode.QWERTY, "hel", 2)
        assertTrue(qwertyResults.isNotEmpty())

        val t9Results = CoverKeyboardNlpManager.getCandidates(CoverLayoutMode.T9_PREDICTIVE, "43556", 2)
        assertTrue(t9Results.contains("hello"))

        val symbolsResults = CoverKeyboardNlpManager.getCandidates(CoverLayoutMode.SYMBOLS, "hel", 2)
        assertTrue(symbolsResults.isEmpty())

        val symbolsSecondaryResults = CoverKeyboardNlpManager.getCandidates(CoverLayoutMode.SYMBOLS_SECONDARY, "hel", 2)
        assertTrue(symbolsSecondaryResults.isEmpty())
    }

    @Test
    fun `contacts require explicit permission and opt-in consent`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val denied = object : ContextWrapper(context) {
            override fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_DENIED
        }
        try {
            CoverKeyboardNlpManager.refreshContactWords(denied, includeContactNames = true)
            fail("Contact lookup must require READ_CONTACTS")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("READ_CONTACTS"))
        }
    }

    @Test
    fun `saved contact opt-in does not read contacts when permission revoked`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("keyboard_words", Context.MODE_PRIVATE)
        val previousOptIn = preferences.getBoolean("include_contact_names", false)
        val wasSet = preferences.contains("include_contact_names")
        val denied = object : ContextWrapper(context) {
            override fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_DENIED
            override fun getContentResolver(): android.content.ContentResolver =
                error("Contact provider should not be accessed")
        }
        preferences.edit().putBoolean("include_contact_names", true).commit()
        try {
            CoverKeyboardNlpManager.refreshContactWords(denied)
            assertEquals("hello", CoverKeyboardNlpManager.getT9Suggestions("43556").first())
        } finally {
            val edit = preferences.edit()
            if (wasSet) edit.putBoolean("include_contact_names", previousOptIn)
            else edit.remove("include_contact_names")
            edit.commit()
        }
    }

    @Test
    fun `explicit flag cannot bypass saved consent`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("keyboard_words", Context.MODE_PRIVATE)
        val previousOptIn = preferences.getBoolean("include_contact_names", false)
        val wasSet = preferences.contains("include_contact_names")
        val noConsent = object : ContextWrapper(context) {
            override fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED
            override fun getContentResolver(): android.content.ContentResolver =
                error("Contact lookup must require saved consent")
        }
        preferences.edit().putBoolean("include_contact_names", false).commit()
        try {
            CoverKeyboardNlpManager.refreshContactWords(noConsent, includeContactNames = true)
            assertEquals("hello", CoverKeyboardNlpManager.getT9Suggestions("43556").first())
        } finally {
            val edit = preferences.edit()
            if (wasSet) edit.putBoolean("include_contact_names", previousOptIn)
            else edit.remove("include_contact_names")
            edit.commit()
        }
    }

    @Test
    fun `revoking consent or permission removes only cached contact words`() {
        var consented = true
        var permitted = true
        val corpus = mapOf(
            "hello" to 1000L,
            "telegram" to 900L,
            "zelda" to 1200L
        )
        val trie = CompactTrieDictionary(corpus)
        val symSpell = SymSpellAutocorrect(corpus)
        val t9 = T9PredictiveEngine(trie)
        CoverKeyboardNlpManager.setEnginesForTest(
            trie = trie,
            symSpell = symSpell,
            t9 = t9,
            contactWords = setOf("zelda"),
            contactsAllowed = { consented && permitted }
        )

        assertEquals("zelda", CoverKeyboardNlpManager.getQwertySuggestions("zeld").first())
        assertEquals("zelda", CoverKeyboardNlpManager.getT9Suggestions("93532").first())
        assertEquals("zelda", CoverKeyboardNlpManager.autocorrectWord("zeldo"))

        consented = false
        assertFalse(CoverKeyboardNlpManager.getQwertySuggestions("zeld").contains("zelda"))
        assertFalse(CoverKeyboardNlpManager.getT9Suggestions("93532").contains("zelda"))
        assertFalse(CoverKeyboardNlpManager.autocorrectWord("zeldo").equals("zelda", ignoreCase = true))
        assertEquals("hello", CoverKeyboardNlpManager.getQwertySuggestions("hell").first())
        assertEquals("telegram", CoverKeyboardNlpManager.getQwertySuggestions("tele").first())

        consented = true
        permitted = false
        assertFalse(CoverKeyboardNlpManager.getQwertySuggestions("zeld").contains("zelda"))
        assertFalse(CoverKeyboardNlpManager.getT9Suggestions("93532").contains("zelda"))

        permitted = true
        assertEquals("zelda", CoverKeyboardNlpManager.getQwertySuggestions("zeld").first())
    }
}
