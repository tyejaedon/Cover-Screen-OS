package com.tyejaedon.coverscreenos.ui.keyboard

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.VibrationEffect
import com.tyejaedon.coverscreenos.ui.keyboard.primitives.HapticTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WordDictionaryTest {
    @Test fun bundledLexiconContainsTwentyThousandWordsAndPredictsHello() {
        val context: Context = RuntimeEnvironment.getApplication()
        val words = java.util.zip.GZIPInputStream(context.assets.open("english_words.bin"))
            .bufferedReader().use { it.readLines() }
        assertEquals(20_000, words.size)
        assertEquals("hello", WordDictionary(words).suggestT9("43556").first())
        assertEquals("hello", WordDictionary(words).suggestT9("44-33-55-55-666").first())
        assertTrue(java.io.File("src/main/assets/english_words.bin").length() < 300_000)
    }

    @Test fun correctsHeloOnSpaceAndPreservesKnownWords() {
        val dictionary = WordDictionary(listOf("hello", "help", "world", "the"))
        assertEquals("hello", dictionary.correct("helo"))
        assertEquals("Hello", dictionary.correct("Helo"))
        assertEquals("help", dictionary.correct("help"))
        assertEquals(listOf("hello"), dictionary.suggestT9("43556"))
        assertEquals(listOf("hello"), dictionary.suggestT9("44-33-55-55-666"))
        assertTrue(dictionary.suggestT9("43-33-55").isEmpty())
        assertTrue(dictionary.suggestT9("44-1-55").isEmpty())
        assertEquals(listOf("there", "world", "everyone"), dictionary.nextWords("hello"))
    }

    @Test fun learnedAndSuppressedWordsChangeCandidateList() {
        val dictionary = WordDictionary(listOf("hello", "help"))
        dictionary.learn("helo")
        assertEquals("helo", dictionary.suggest("helo").first())
        dictionary.suppress("helo")
        assertFalse(dictionary.suggest("helo").contains("helo"))
    }

    @Test fun personalNamesArePrioritizedWhenProvided() {
        val dictionary = WordDictionary(listOf("ale", "alice", "alex", "hello"))
        assertEquals("alice", dictionary.suggest("alice").first())
        assertEquals("alex", dictionary.suggest("alex").first())
    }

    @Test fun contactsRequireExplicitPermissionAndOptIn() {
        val context: Context = RuntimeEnvironment.getApplication()
        val denied = object : ContextWrapper(context) {
            override fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_DENIED
        }
        try {
            WordDictionary.fromContext(denied, includeContactNames = true)
            org.junit.Assert.fail("Contact lookup must require READ_CONTACTS")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("READ_CONTACTS"))
        }
    }

    @Test fun savedContactOptInDoesNotReadWhenPermissionRevoked() {
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
            assertEquals("hello", WordDictionary.fromContext(denied).suggestT9("43556").first())
        } finally {
            val edit = preferences.edit()
            if (wasSet) edit.putBoolean("include_contact_names", previousOptIn)
            else edit.remove("include_contact_names")
            edit.commit()
        }
    }

    @Test fun explicitFlagCannotBypassSavedConsent() {
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
            assertEquals("hello", WordDictionary.fromContext(noConsent, includeContactNames = true)
                .suggestT9("43556").first())
        } finally {
            val edit = preferences.edit()
            if (wasSet) edit.putBoolean("include_contact_names", previousOptIn)
            else edit.remove("include_contact_names")
            edit.commit()
        }
    }

    @Test fun revokingConsentOrPermissionRemovesOnlyCachedContactWords() {
        var consented = true
        var permitted = true
        val dictionary = WordDictionary(
            words = listOf("hello", "telegram"),
            contactWords = listOf("zelda", "hello"),
            contactsAllowed = { consented && permitted }
        )
        assertEquals("zelda", dictionary.suggest("zeld").first())
        assertEquals("zelda", dictionary.suggestT9("93532").first())
        assertEquals("zelda", dictionary.correct("zeldo"))
        dictionary.learn("zelda")

        consented = false
        assertFalse(dictionary.suggest("zeld").contains("zelda"))
        assertFalse(dictionary.suggestT9("93532").contains("zelda"))
        assertFalse(dictionary.correct("zeldo").equals("zelda", ignoreCase = true))
        assertEquals("hello", dictionary.suggest("hell").first())
        assertEquals("telegram", dictionary.suggest("tele").first())

        consented = true
        permitted = false
        assertFalse(dictionary.suggest("zeld").contains("zelda"))
        assertFalse(dictionary.suggestT9("93532").contains("zelda"))
        assertFalse(dictionary.correct("zeldo").equals("zelda", ignoreCase = true))

        permitted = true
        assertEquals("zelda", dictionary.suggest("zeld").first())
    }

    @Test fun shiftDoubleTapAndSentenceCaps() {
        assertEquals(ShiftMode.Once, nextShiftMode(ShiftMode.Off, -1000L, 1000L))
        assertEquals(ShiftMode.Locked, nextShiftMode(ShiftMode.Once, 1000L, 1200L))
        assertEquals(ShiftMode.Off, nextShiftMode(ShiftMode.Locked, 1200L, 1300L))
        assertEquals(ShiftMode.Off, nextShiftMode(ShiftMode.Once, 1000L, 1500L))
        assertTrue(shouldAutoCap("Hello. "))
        assertFalse(shouldAutoCap("Hello "))
        assertEquals(6, previousWordLength("hello "))
        assertEquals(5, previousWordLength("hello world"))
        assertEquals(VibrationEffect.EFFECT_TICK, HapticTier.Light.toVibrationEffect())
        assertEquals(VibrationEffect.EFFECT_CLICK, HapticTier.Standard.toVibrationEffect())
        assertEquals(VibrationEffect.EFFECT_HEAVY_CLICK, HapticTier.Strong.toVibrationEffect())
    }
}
