package com.tyejaedon.coverscreenos.ui.keyboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Offline, frequency-ordered English lexicon. The asset is derived from the
 * CMU Pronouncing Dictionary (see assets/CMU_DICT_LICENSE.txt).
 */
internal class WordDictionary(
    words: List<String>,
    private val savePreferences: android.content.SharedPreferences? = null,
    contactWords: List<String> = emptyList(),
    private val contactsAllowed: () -> Boolean = { false }
) {
    private val baseVocabulary = words.distinct().filter { it.isNotBlank() }
    private val contactOnly = contactWords.filter { it.isNotBlank() }
        .toSet() - baseVocabulary.toSet()
    private val vocabulary = (contactWords + baseVocabulary).distinct().filter { it.isNotBlank() }
    private val rank = vocabulary.withIndex().associate { (index, word) -> word to index }
    private val byDigits = vocabulary.groupBy(::t9Digits)
    private val learned = savePreferences?.getStringSet("learned", emptySet())?.toMutableSet()
        ?: mutableSetOf()
    private val suppressed = savePreferences?.getStringSet("suppressed", emptySet())?.toMutableSet()
        ?: mutableSetOf()

    fun suggest(input: String, limit: Int = 3): List<String> {
        val word = input.lowercase(Locale.ROOT).filter { it in 'a'..'z' }
        if (word.isEmpty() || limit <= 0) return emptyList()
        val allowContacts = contactsAllowed()
        val pool = vocabulary.asSequence()
            .filter {
                isAllowed(it, allowContacts) &&
                    (it.startsWith(word) || editDistanceAtMostOne(it, word))
            }
            .take(2000).toList()
        return (learned.asSequence().filter { it.startsWith(word) || editDistanceAtMostOne(it, word) } +
            pool.asSequence())
            .filter { isAllowed(it, allowContacts) }
            .distinct()
            .sortedWith(compareBy<String> { if (it == word) 0 else if (it.startsWith(word)) 1 else 2 }
                .thenBy { if (it in learned) -1 else rank[it] ?: Int.MAX_VALUE })
            .take(limit).toList()
    }

    /** Accepts predictive digits (43556) or grouped keypad presses (44-33-55-55-666). */
    fun suggestT9(digits: String, limit: Int = 3): List<String> {
        if (limit <= 0) return emptyList()
        if ('-' in digits) {
            val groups = digits.split('-')
            if (groups.any { group ->
                    group.isEmpty() || group.first() !in '2'..'9' ||
                        group.any { it != group.first() }
                }
            ) return emptyList()
            return suggestT9(groups.map { it.first() }.joinToString(""), limit)
        }
        if (digits.isEmpty() || digits.any { it !in '2'..'9' }) return emptyList()
        val allowContacts = contactsAllowed()
        return (learned.asSequence().filter { t9Digits(it) == digits } +
            byDigits[digits].orEmpty().asSequence())
            .filter { isAllowed(it, allowContacts) }.distinct()
            .sortedWith(compareBy<String> { if (it in learned) -1 else rank[it] ?: Int.MAX_VALUE })
            .take(limit).toList()
    }

    fun correct(input: String): String {
        val normalized = input.lowercase(Locale.ROOT)
        if (normalized in suppressed ||
            ((normalized in rank || normalized in learned) &&
                isAllowed(normalized, contactsAllowed()))
        ) return input
        val correction = suggest(normalized, 1).firstOrNull() ?: return input
        return if (input.firstOrNull()?.isUpperCase() == true) correction.replaceFirstChar { it.uppercase() }
        else correction
    }

    fun nextWords(previous: String): List<String> = when (previous.lowercase(Locale.ROOT)) {
        "hello", "hi", "hey" -> listOf("there", "world", "everyone")
        "thank" -> listOf("you", "them", "god")
        "i" -> listOf("am", "have", "will")
        "how" -> listOf("are", "to", "much")
        else -> listOf("the", "and", "to")
    }.filter { it !in suppressed }

    fun learn(word: String) {
        val normalized = word.lowercase(Locale.ROOT).trim()
        if (normalized.isEmpty() || normalized.any { it !in 'a'..'z' }) return
        suppressed.remove(normalized)
        learned.add(normalized)
        persist()
    }

    fun suppress(word: String) {
        suppressed.add(word.lowercase(Locale.ROOT))
        learned.remove(word.lowercase(Locale.ROOT))
        persist()
    }

    private fun isAllowed(word: String, allowContacts: Boolean): Boolean =
        word !in suppressed && (allowContacts || word !in contactOnly)

    fun isAvailable(word: String): Boolean = isAllowed(word.lowercase(Locale.ROOT), contactsAllowed())

    private fun persist() {
        savePreferences?.edit()?.putStringSet("learned", learned.toSet())
            ?.putStringSet("suppressed", suppressed.toSet())?.apply()
    }

    companion object {
        /**
         * Contact enrichment always requires both the user's persisted opt-in
         * and READ_CONTACTS. The optional argument can disable enrichment,
         * but cannot override consent. Explicit `true` without permission
         * throws; a saved opt-in with revoked permission is skipped.
         */
        fun fromContext(context: Context, includeContactNames: Boolean? = null): WordDictionary {
            val preferences = context.getSharedPreferences("keyboard_words", Context.MODE_PRIVATE)
            val consented = preferences.getBoolean("include_contact_names", false)
            val hasPermission = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
            if (includeContactNames == true) {
                check(hasPermission) {
                    "Contact-name predictions require user-granted READ_CONTACTS permission"
                }
            }
            val words = context.assets.open("english_words.bin").use { stream ->
                GZIPInputStream(stream).bufferedReader().use { it.readLines() }
            }
            val contacts = mutableListOf<String>()
            if (consented && includeContactNames != false && hasPermission) {
                val cursor = context.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                    null, null, null
                ) ?: error("Contact provider did not return a cursor")
                cursor.use {
                    while (cursor.moveToNext()) {
                        contacts.addAll(cursor.getString(0).orEmpty().split(Regex("[^A-Za-z]+")))
                    }
                }
            }
            val appLabels = mutableListOf<String>()
            context.packageManager.getInstalledApplications(0).forEach { info ->
                appLabels.addAll(context.packageManager.getApplicationLabel(info).toString()
                    .split(Regex("[^A-Za-z]+")))
            }
            val contactWords = contacts.map { it.lowercase(Locale.ROOT) }.filter { it.length > 1 }
            val baseWords = appLabels.map { it.lowercase(Locale.ROOT) }.filter { it.length > 1 } + words
            return WordDictionary(
                words = baseWords,
                savePreferences = preferences,
                contactWords = contactWords,
                contactsAllowed = {
                    includeContactNames != false &&
                        preferences.getBoolean("include_contact_names", false) &&
                        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                        PackageManager.PERMISSION_GRANTED
                }
            )
        }

        fun t9Digits(word: String): String = buildString {
            word.lowercase(Locale.ROOT).forEach { char ->
                append(when (char) {
                    in "abc" -> '2'
                    in "def" -> '3'
                    in "ghi" -> '4'
                    in "jkl" -> '5'
                    in "mno" -> '6'
                    in "pqrs" -> '7'
                    in "tuv" -> '8'
                    in "wxyz" -> '9'
                    else -> return@forEach
                })
            }
        }

        private fun editDistanceAtMostOne(a: String, b: String): Boolean {
            if (kotlin.math.abs(a.length - b.length) > 1) return false
            var left = 0
            var right = 0
            var edits = 0
            while (left < a.length && right < b.length) {
                if (a[left] == b[right]) {
                    left++
                    right++
                } else {
                    if (++edits > 1) return false
                    if (a.length >= b.length) left++
                    if (a.length <= b.length) right++
                }
            }
            return edits + (a.length - left) + (b.length - right) <= 1
        }
    }
}
