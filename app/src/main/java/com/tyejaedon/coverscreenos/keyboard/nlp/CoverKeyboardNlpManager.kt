package com.tyejaedon.coverscreenos.keyboard.nlp

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.tyejaedon.coverscreenos.keyboard.floris.CoverLayoutMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * High-performance application-wide NLP coordination manager powered by `:keyboard-floris`.
 *
 * Coordinates [CompactTrieDictionary], [BinaryTrieDictionary], [SymSpellAutocorrect],
 * [T9PredictiveEngine], and [DTWGlideDecoder], enriched with:
 * - Bundled lexicon (`english_words.bin`) and bigram next-word corpus (`english_bigrams.bin`)
 * - Installed application label indexing
 * - Opt-in contact name indexing (`READ_CONTACTS` + user consent)
 * - User learned and suppressed word persistence (`keyboard_words` SharedPreferences)
 */
object CoverKeyboardNlpManager {

    private const val TAG = "CoverKeyboardNlp"
    private const val PREF_FILE = "keyboard_words"
    private const val KEY_LEARNED = "learned"
    private const val KEY_SUPPRESSED = "suppressed"
    private const val KEY_INCLUDE_CONTACTS = "include_contact_names"

    // Dedicated background scope so corpus decompression and ContactProvider queries never block IME startup.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()

    /** Guards asynchronous asset initialization so it executes once per process. */
    @Volatile
    private var isInitialized = false

    // Minimal high-frequency English seed used immediately before async asset loading finishes.
    private val defaultCorpus: Map<String, Long> = mapOf(
        "the" to 1000000L, "and" to 800000L, "to" to 700000L, "of" to 600000L,
        "a" to 550000L, "in" to 500000L, "is" to 450000L, "that" to 400000L,
        "for" to 380000L, "it" to 350000L, "you" to 340000L, "was" to 320000L,
        "with" to 300000L, "on" to 290000L, "as" to 280000L, "have" to 270000L,
        "be" to 260000L, "at" to 250000L, "this" to 240000L, "from" to 230000L,
        "i" to 220000L, "they" to 210000L, "not" to 200000L, "hello" to 180000L,
        "help" to 170000L, "good" to 160000L, "ok" to 150000L, "yes" to 140000L,
        "no" to 130000L, "thanks" to 120000L, "sure" to 110000L, "home" to 105000L,
        "call" to 100000L, "message" to 95000L, "work" to 90000L, "time" to 85000L
    )

    /** In-memory prefix Trie supporting dynamic insertion of learned and contact tokens. */
    var trieDictionary: CompactTrieDictionary = CompactTrieDictionary(defaultCorpus)
        private set

    // Memory-mapped read-only Trie cache (`trie_mmap_v1.bin`) used to reduce Java heap overhead.
    private var binaryTrie: BinaryTrieDictionary? = null

    /** Symmetric-delete fuzzy spelling corrector used for QWERTY autocorrection. */
    var symSpellAutocorrect: SymSpellAutocorrect = SymSpellAutocorrect(defaultCorpus)
        private set

    /** Numeric keypad (2-9) predictive candidate engine. */
    var t9Engine: T9PredictiveEngine = T9PredictiveEngine(trieDictionary)
        private set

    /** Dynamic Time Warping gesture decoder for continuous swipe typing on QWERTY. */
    var glideDecoder: DTWGlideDecoder = DTWGlideDecoder(trieDictionary)
        private set

    // Maps each lowercase antecedent word to up to 5 highest-frequency follower words.
    private var bigramFollowers: Map<String, List<String>> = emptyMap()
    private val baseCorpusWords = mutableSetOf<String>()
    // Tracks tokens introduced exclusively via Contacts so revoking consent hides them immediately.
    private val contactOnlyWords = mutableSetOf<String>()
    private var contactsAllowedProvider: () -> Boolean = { true }
    // Also acts as the mutex protecting `learnedWords`, `suppressedWords`, and `contactOnlyWords`.
    private val learnedWords = mutableSetOf<String>()
    private val suppressedWords = mutableSetOf<String>()
    private var sharedPreferences: SharedPreferences? = null

    /**
     * Validates `READ_CONTACTS` permission and user consent (`include_contact_names`) before
     * indexing contact names into the NLP dictionary.
     */
    fun refreshContactWords(context: Context, includeContactNames: Boolean? = null) {
        val preferences = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        sharedPreferences = preferences
        val savedConsent = preferences.getBoolean(KEY_INCLUDE_CONTACTS, false)
        val hasPermission = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (includeContactNames == true && !hasPermission) {
            throw IllegalStateException("Contact suggestions require READ_CONTACTS permission")
        }
        val shouldIncludeContacts = (includeContactNames ?: true) && savedConsent && hasPermission
        // Re-check both preference and runtime permission dynamically at suggestion query time.
        contactsAllowedProvider = {
            preferences.getBoolean(KEY_INCLUDE_CONTACTS, false) &&
                context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        }
        synchronized(learnedWords) {
            contactOnlyWords.clear()
            if (shouldIncludeContacts) {
                runCatching {
                    context.contentResolver.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                        null, null, null
                    )?.use { cursor ->
                        while (cursor.moveToNext()) {
                            cursor.getString(0).orEmpty().split(Regex("[^A-Za-z]+")).forEach { token ->
                                val normalized = token.lowercase(Locale.ROOT)
                                if (normalized.length > 1) {
                                    trieDictionary.insert(normalized, 120_000L)
                                    if (normalized !in baseCorpusWords && !defaultCorpus.containsKey(normalized)) {
                                        contactOnlyWords.add(normalized)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Asynchronously loads the bundled compressed corpus from assets or memory-maps
     * the cached binary Trie on [Dispatchers.IO] for zero-heap startup, plus bigrams,
     * app labels, and user preferences.
     */
    fun initializeFromContext(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        sharedPreferences = prefs
        synchronized(learnedWords) {
            learnedWords.clear()
            learnedWords.addAll(prefs.getStringSet(KEY_LEARNED, emptySet()).orEmpty())
            suppressedWords.clear()
            suppressedWords.addAll(prefs.getStringSet(KEY_SUPPRESSED, emptySet()).orEmpty())
        }

        if (isInitialized) return
        scope.launch {
            initMutex.withLock {
                if (isInitialized) return@withLock
                try {
                    loadBigramCorpus(appContext)

                    val cacheFile = java.io.File(appContext.cacheDir, "trie_mmap_v1.bin")
                    val frequencies = HashMap<String, Long>(20000)
                    frequencies.putAll(defaultCorpus)

                    // 1. Load the GZIP-compressed base English frequency lexicon (`word freq` lines).
                    runCatching {
                        appContext.assets.open("english_words.bin").use { stream ->
                            GZIPInputStream(stream).bufferedReader().forEachLine { line ->
                                val parts = line.split(' ')
                                val word = parts.getOrNull(0)?.trim()?.lowercase(Locale.ROOT)
                                val freq = parts.getOrNull(1)?.toLongOrNull() ?: 1L
                                if (!word.isNullOrEmpty() && word.all { it in 'a'..'z' }) {
                                    frequencies[word] = freq
                                }
                            }
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "Bundled english_words.bin not loaded, using fallback default vocabulary", e)
                    }

                    // 2. Enrich with installed application labels so launcher/app names rank well.
                    runCatching {
                        appContext.packageManager.getInstalledApplications(0).forEach { info ->
                            val label = appContext.packageManager.getApplicationLabel(info).toString()
                            label.split(Regex("[^A-Za-z]+")).forEach { token ->
                                val normalized = token.lowercase(Locale.ROOT)
                                if (normalized.length > 1 && normalized !in frequencies) {
                                    frequencies[normalized] = 50_000L
                                }
                            }
                        }
                    }

                    // 3. Enrich with opt-in contact names if consented & permitted.
                    runCatching {
                        val consented = prefs.getBoolean(KEY_INCLUDE_CONTACTS, false)
                        val permitted = appContext.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                            PackageManager.PERMISSION_GRANTED
                        if (consented && permitted) {
                            appContext.contentResolver.query(
                                ContactsContract.Contacts.CONTENT_URI,
                                arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                                null, null, null
                            )?.use { cursor ->
                                while (cursor.moveToNext()) {
                                    cursor.getString(0).orEmpty().split(Regex("[^A-Za-z]+")).forEach { token ->
                                        val normalized = token.lowercase(Locale.ROOT)
                                        if (normalized.length > 1) {
                                            frequencies[normalized] = maxOf(frequencies[normalized] ?: 0L, 120_000L)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Boost user-learned words above typical dictionary frequencies.
                    val currentLearned = synchronized(learnedWords) { learnedWords.toSet() }
                    val currentSuppressed = synchronized(learnedWords) { suppressedWords.toSet() }
                    currentLearned.forEach { word ->
                        frequencies[word] = maxOf(frequencies[word] ?: 0L, 500_000L)
                    }

                    val newTrie = CompactTrieDictionary(frequencies)
                    val newSymSpell = SymSpellAutocorrect(frequencies)
                    val newT9 = T9PredictiveEngine(newTrie, currentLearned, currentSuppressed)
                    val newGlide = DTWGlideDecoder(newTrie)

                    // Persist and memory-map the compiled Trie for fast lookups across IME restarts.
                    runCatching {
                        if (!cacheFile.exists() || cacheFile.length() < BinaryTrieDictionary.HEADER_SIZE) {
                            BinaryTrieDictionary.writeToFile(newTrie, cacheFile)
                        }
                        binaryTrie = BinaryTrieDictionary.mapFromFile(cacheFile)
                    }.onFailure { e ->
                        Log.w(TAG, "Could not map binary trie cache file", e)
                    }

                    trieDictionary = newTrie
                    symSpellAutocorrect = newSymSpell
                    t9Engine = newT9
                    glideDecoder = newGlide
                    isInitialized = true
                    Log.d(TAG, "CoverKeyboardNlpManager initialized with ${frequencies.size} words")
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed initializing CoverKeyboardNlpManager", t)
                }
            }
        }
    }

    /**
     * Parses GZIP-compressed `word1 word2 freq` bigram records from `english_bigrams.bin`
     * and retains the top 5 followers per antecedent word.
     */
    private fun loadBigramCorpus(context: Context) {
        val bigramCandidates = mutableMapOf<String, MutableList<Pair<String, Long>>>()
        runCatching {
            context.assets.open("english_bigrams.bin").use { stream ->
                GZIPInputStream(stream).bufferedReader().forEachLine { line ->
                    val parts = line.split(' ')
                    if (parts.size != 3) return@forEachLine
                    val freq = parts[2].toLongOrNull() ?: return@forEachLine
                    bigramCandidates.getOrPut(parts[0].lowercase(Locale.ROOT)) { mutableListOf() } +=
                        parts[1].lowercase(Locale.ROOT) to freq
                }
            }
            bigramFollowers = bigramCandidates.mapValues { (_, list) ->
                list.sortedByDescending { it.second }.take(5).map { it.first }
            }
        }.onFailure {
            Log.w(TAG, "Bigram corpus unavailable, using default next-word fallbacks", it)
        }
    }

    /**
     * Provides suggestions for QWERTY input using prefix lookup, learned words, and fuzzy autocorrect.
     */
    fun getQwertySuggestions(prefix: String, limit: Int = 3): List<String> {
        val query = prefix.trim().lowercase(Locale.ROOT)
        if (query.isEmpty() || !query.all { it in 'a'..'z' } || limit <= 0) return emptyList()

        val learned = synchronized(learnedWords) { learnedWords.toSet() }
        val results = LinkedHashSet<String>()

        // Stage 1: Prioritize personal learned words matching the typed prefix.
        for (word in learned) {
            if (isWordAvailable(word) && word.startsWith(query)) {
                results.add(word)
                if (results.size >= limit) return results.toList()
            }
        }

        // Stage 2: Query Trie prefix completions (preferring the memory-mapped cache once ready).
        val completions = binaryTrie?.predictForPrefix(query, limit = limit + 4)
            ?: trieDictionary.predictForPrefix(query, limit = limit + 4)
        for (c in completions) {
            if (isWordAvailable(c.word)) {
                results.add(c.word)
                if (results.size >= limit) break
            }
        }

        // Stage 3: Backfill remaining slots with SymSpell edit-distance corrections for typos.
        if (results.size < limit) {
            val corrections = symSpellAutocorrect.suggest(query, limit = limit + 4)
            for (c in corrections) {
                if (isWordAvailable(c.word)) {
                    results.add(c.word)
                    if (results.size >= limit) break
                }
            }
        }

        return results.take(limit)
    }

    /**
     * Context-aware next-word prediction from the bigram corpus when the user finishes a word.
     */
    fun getNextWordSuggestions(previousWord: String, limit: Int = 3): List<String> {
        val key = previousWord.trim().lowercase(Locale.ROOT)
        val fromBigrams = bigramFollowers[key].orEmpty().filter(::isWordAvailable)
        if (fromBigrams.isNotEmpty()) return fromBigrams.take(limit)

        // Fallback conversational followers when bigram data lacks the previous word.
        return when (key) {
            "hello", "hi", "hey" -> listOf("there", "world", "everyone")
            "thank" -> listOf("you", "them", "god")
            "i" -> listOf("am", "have", "will")
            "how" -> listOf("are", "to", "much")
            else -> listOf("the", "and", "to")
        }.filter(::isWordAvailable).take(limit)
    }

    /**
     * Autocorrects a mistyped word on Space press, preserving initial capitalization.
     */
    fun autocorrectWord(input: String): String {
        val normalized = input.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty() || !normalized.all { it in 'a'..'z' }) return input
        val learned = synchronized(learnedWords) { learnedWords.toSet() }
        if (!isWordAvailable(normalized)) return input
        // Leave valid dictionary or user-learned words untouched.
        if ((normalized in learned || trieDictionary.contains(normalized)) && isWordAvailable(normalized)) {
            return input
        }
        val topSuggestion = symSpellAutocorrect.suggest(normalized, limit = 5)
            .firstOrNull { isWordAvailable(it.word) }
            ?.word
            ?: getQwertySuggestions(normalized, limit = 1).firstOrNull()
            ?: return input
        return if (input.firstOrNull()?.isUpperCase() == true) {
            topSuggestion.replaceFirstChar { it.uppercase() }
        } else {
            topSuggestion
        }
    }

    /**
     * Provides candidate completions for a T9 digit sequence (e.g. "43556" or "44-33-55-55-666" -> "hello").
     */
    fun getT9Suggestions(digits: String, limit: Int = 3): List<String> {
        if (digits.isEmpty() || limit <= 0) return emptyList()

        // Collapse hyphen-delimited multi-tap key sequences (e.g. "44-33-55") into base T9 digits ("435").
        if ('-' in digits) {
            val groups = digits.split('-')
            if (groups.any { group ->
                    group.isEmpty() || group.first() !in '2'..'9' || group.any { it != group.first() }
                }
            ) return emptyList()
            val collapsedDigits = groups.map { it.first() }.joinToString("")
            return getT9Suggestions(collapsedDigits, limit)
        }

        val cleanDigits = digits.filter { it in '2'..'9' }
        if (cleanDigits.isEmpty()) return emptyList()

        val binaryCompletions = binaryTrie?.predictForDigits(cleanDigits, limit = limit + 4)
        if (binaryCompletions != null && binaryCompletions.isNotEmpty()) {
            val filtered = binaryCompletions.map { it.word }.filter(::isWordAvailable)
            if (filtered.isNotEmpty()) return filtered.take(limit)
        }

        return t9Engine.predictForDigits(cleanDigits, limit = limit + 4)
            .map { it.word }
            .filter(::isWordAvailable)
            .take(limit)
    }

    /**
     * Decodes a continuous swipe/glide gesture across the QWERTY keyboard surface.
     */
    fun decodeGlidePath(points: List<Offset>, limit: Int = 3): List<String> {
        return glideDecoder.decodeGlidePath(points, limit = limit + 4)
            .map { it.word }
            .filter(::isWordAvailable)
            .take(limit)
    }

    /**
     * High-level candidate retrieval dispatching to either T9 or QWERTY engines based on mode.
     */
    fun getCandidates(mode: CoverLayoutMode, query: String, limit: Int = 3): List<String> {
        return when (mode) {
            CoverLayoutMode.T9_PREDICTIVE -> {
                if ('-' in query || query.all { it in '2'..'9' }) {
                    getT9Suggestions(query, limit)
                } else {
                    getQwertySuggestions(query, limit)
                }
            }
            CoverLayoutMode.QWERTY -> getQwertySuggestions(query, limit)
            CoverLayoutMode.SYMBOLS,
            CoverLayoutMode.SYMBOLS_SECONDARY,
            CoverLayoutMode.NUMERIC -> emptyList()
        }
    }

    /**
     * Learns a user word and persists it to `keyboard_words` SharedPreferences.
     */
    fun learnWord(word: String, context: Context? = null) {
        val normalized = word.lowercase(Locale.ROOT).trim()
        if (normalized.isEmpty() || normalized.any { it !in 'a'..'z' }) return
        synchronized(learnedWords) {
            suppressedWords.remove(normalized)
            learnedWords.add(normalized)
            trieDictionary.insert(normalized, 500_000L)
            persistPreferences(context)
        }
    }

    /**
     * Suppresses an unwanted candidate word and persists it to `keyboard_words` SharedPreferences.
     */
    fun suppressWord(word: String, context: Context? = null) {
        val normalized = word.lowercase(Locale.ROOT).trim()
        if (normalized.isEmpty()) return
        synchronized(learnedWords) {
            learnedWords.remove(normalized)
            suppressedWords.add(normalized)
            persistPreferences(context)
        }
    }

    /**
     * Returns `true` if [word] is not user-suppressed and, when it comes exclusively from
     * Contacts, contact-name suggestions are still consented and permitted.
     */
    fun isWordAvailable(word: String): Boolean {
        val normalized = word.lowercase(Locale.ROOT).trim()
        return synchronized(learnedWords) {
            normalized !in suppressedWords &&
                (normalized !in contactOnlyWords || contactsAllowedProvider())
        }
    }

    /** Writes learned and suppressed word sets asynchronously to `keyboard_words` SharedPreferences. */
    private fun persistPreferences(context: Context?) {
        val prefs = sharedPreferences
            ?: context?.applicationContext?.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                ?.also { sharedPreferences = it }
            ?: return
        prefs.edit()
            .putStringSet(KEY_LEARNED, learnedWords.toSet())
            .putStringSet(KEY_SUPPRESSED, suppressedWords.toSet())
            .apply()
    }

    /**
     * Sets dictionary instances directly for unit testing.
     */
    internal fun setEnginesForTest(
        trie: CompactTrieDictionary,
        symSpell: SymSpellAutocorrect,
        t9: T9PredictiveEngine,
        binary: BinaryTrieDictionary? = null,
        bigrams: Map<String, List<String>> = emptyMap(),
        contactWords: Set<String> = emptySet(),
        contactsAllowed: () -> Boolean = { true }
    ) {
        synchronized(learnedWords) {
            learnedWords.clear()
            suppressedWords.clear()
            contactOnlyWords.clear()
            contactOnlyWords.addAll(contactWords.map { it.lowercase(Locale.ROOT) })
        }
        bigramFollowers = bigrams
        contactsAllowedProvider = contactsAllowed
        trieDictionary = trie
        binaryTrie = binary
        symSpellAutocorrect = symSpell
        t9Engine = t9
        glideDecoder = DTWGlideDecoder(trie)
        isInitialized = true
    }
}
