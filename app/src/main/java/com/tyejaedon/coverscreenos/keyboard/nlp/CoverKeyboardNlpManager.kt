package com.tyejaedon.coverscreenos.keyboard.nlp

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.tyejaedon.coverscreenos.keyboard.core.contracts.CandidateWord
import com.tyejaedon.coverscreenos.keyboard.floris.CoverLayoutMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
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
    // SharedPreferences file storing learned words, suppressed words, and contact opt-in consent.
    private const val PREF_FILE = "keyboard_words"
    private const val KEY_LEARNED = "learned"
    private const val KEY_SUPPRESSED = "suppressed"
    private const val KEY_INCLUDE_CONTACTS = "include_contact_names"
    private const val MMAP_CACHE_FILE = "trie_mmap_v1.bin"
    private const val MMAP_META_FILE = "trie_mmap_v1.meta"
    private const val MMAP_REBUILD_DEBOUNCE_MS = 400L

    // Dedicated background scope so corpus decompression and ContactProvider queries never block IME startup.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Mutual-exclusion lock ensuring concurrent callers wait rather than initializing assets twice.
    private val initMutex = Mutex()
    // Serializes background mmap trie rebuilds after dynamic learned-word or contact updates.
    private val mmapWriteMutex = Mutex()

    /** Guards asynchronous asset initialization so it executes once per process. */
    @Volatile
    private var isInitialized = false

    /**
     * Set whenever [trieDictionary] receives dynamic words (via [learnWord] or
     * [refreshContactWords]) that are not yet reflected in [binaryTrie]. Queries merge dynamic
     * trie hits over mmap hits until the background cache rebuild completes.
     */
    @Volatile
    private var hasUnmappedDynamicMutations = false

    // Cached application context used to schedule debounced mmap cache rebuilds after word mutations.
    @Volatile
    private var cachedAppContext: Context? = null

    // Debounced background rebuild job for `trie_mmap_v1.bin`.
    @Volatile
    private var pendingRebuildJob: Job? = null

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
    // Words originating from the core English vocabulary (never hidden when contacts consent is revoked).
    private val baseCorpusWords = mutableSetOf<String>()
    // Tracks tokens introduced exclusively via Contacts so revoking consent hides them immediately.
    private val contactOnlyWords = mutableSetOf<String>()
    // Live gate queried per suggestion to verify both user consent and READ_CONTACTS permission remain active.
    private var contactsAllowedProvider: () -> Boolean = { true }
    // User-taught custom words; also serves as the synchronization lock for suppressed and contact sets.
    private val learnedWords = mutableSetOf<String>()
    // Words explicitly hidden by the user so they never appear in suggestions or autocorrect.
    private val suppressedWords = mutableSetOf<String>()
    // Cached SharedPreferences handle for reading/writing learned and suppressed words without needing a Context.
    private var sharedPreferences: SharedPreferences? = null

    /**
     * Validates `READ_CONTACTS` permission and user consent (`include_contact_names`) before
     * indexing contact names into the NLP dictionary.
     */
    fun refreshContactWords(context: Context, includeContactNames: Boolean? = null) {
        val appContext = context.applicationContext
        cachedAppContext = appContext
        val preferences = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        sharedPreferences = preferences
        val savedConsent = preferences.getBoolean(KEY_INCLUDE_CONTACTS, false)
        val hasPermission = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (includeContactNames == true && !hasPermission) {
            throw IllegalStateException("Contact suggestions require READ_CONTACTS permission")
        }
        // Only query ContactsProvider when caller enabled it, user opted in, and OS permission is granted.
        val shouldIncludeContacts = (includeContactNames ?: true) && savedConsent && hasPermission
        // Re-check both preference and runtime permission dynamically at suggestion query time.
        contactsAllowedProvider = {
            preferences.getBoolean(KEY_INCLUDE_CONTACTS, false) &&
                context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        }
        var contactsMutated = false
        synchronized(learnedWords) {
            val previousContacts = contactOnlyWords.toSet()
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
            contactsMutated = contactOnlyWords != previousContacts
            if (contactsMutated) {
                hasUnmappedDynamicMutations = true
                t9Engine = T9PredictiveEngine(trieDictionary, learnedWords.toSet(), suppressedWords.toSet())
            }
        }
        if (contactsMutated && isInitialized) {
            scheduleMmapRebuild(appContext)
        }
    }

    /**
     * Asynchronously loads the bundled compressed corpus from assets or memory-maps
     * the cached binary Trie on [Dispatchers.IO] for zero-heap startup, plus bigrams,
     * app labels, and user preferences.
     */
    fun initializeFromContext(context: Context) {
        val appContext = context.applicationContext
        cachedAppContext = appContext
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

                    // On-disk memory-mapped Trie cache built from the combined vocabulary and
                    // validated against a deterministic vocabulary fingerprint sidecar.
                    val cacheFile = File(appContext.cacheDir, MMAP_CACHE_FILE)
                    val metaFile = File(appContext.cacheDir, MMAP_META_FILE)
                    // Combined word -> frequency table merged across assets, installed apps, contacts, and learned words.
                    val frequencies = HashMap<String, Long>(20000)
                    frequencies.putAll(defaultCorpus)
                    val loadedBaseWords = HashSet<String>(20000)
                    val loadedAppTokens = HashSet<String>()
                    val loadedContactOnlyWords = HashSet<String>()

                    // 1. Load the GZIP-compressed base English frequency lexicon (`word freq` lines).
                    runCatching {
                        appContext.assets.open("english_words.bin").use { stream ->
                            GZIPInputStream(stream).bufferedReader().forEachLine { line ->
                                val parts = line.split(' ')
                                val word = parts.getOrNull(0)?.trim()?.lowercase(Locale.ROOT)
                                val freq = parts.getOrNull(1)?.toLongOrNull() ?: 1L
                                if (!word.isNullOrEmpty() && word.all { it in 'a'..'z' }) {
                                    frequencies[word] = freq
                                    loadedBaseWords.add(word)
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
                                if (normalized.length > 1) {
                                    loadedAppTokens.add(normalized)
                                    if (normalized !in frequencies) {
                                        frequencies[normalized] = 50_000L
                                    }
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
                                            if (normalized !in loadedBaseWords && !defaultCorpus.containsKey(normalized)) {
                                                loadedContactOnlyWords.add(normalized)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Boost user-learned words above typical dictionary frequencies.
                    val currentLearned: Set<String>
                    val currentSuppressed: Set<String>
                    synchronized(learnedWords) {
                        baseCorpusWords.clear()
                        baseCorpusWords.addAll(loadedBaseWords)
                        contactOnlyWords.clear()
                        contactOnlyWords.addAll(loadedContactOnlyWords)
                        currentLearned = learnedWords.toSet()
                        currentSuppressed = suppressedWords.toSet()
                    }
                    currentLearned.forEach { word ->
                        frequencies[word] = maxOf(frequencies[word] ?: 0L, 500_000L)
                    }

                    val newTrie = CompactTrieDictionary(frequencies)
                    val newSymSpell = SymSpellAutocorrect(frequencies)
                    val newT9 = T9PredictiveEngine(newTrie, currentLearned, currentSuppressed)
                    val newGlide = DTWGlideDecoder(newTrie)

                    // Rebuild the memory-mapped Trie whenever missing, truncated, or stale relative
                    // to the current learned words, suppressed words, installed apps, or contacts.
                    val fingerprint = computeVocabularyFingerprint(
                        learned = currentLearned,
                        suppressed = currentSuppressed,
                        appTokens = loadedAppTokens,
                        contactTokens = loadedContactOnlyWords
                    )
                    runCatching {
                        val storedFingerprint = if (metaFile.exists()) {
                            runCatching { metaFile.readText() }.getOrNull()
                        } else null
                        val needsRebuild = !cacheFile.exists() ||
                            cacheFile.length() < BinaryTrieDictionary.HEADER_SIZE ||
                            storedFingerprint != fingerprint
                        if (needsRebuild) {
                            writeBinaryTrieAtomically(newTrie, cacheFile)
                            runCatching { metaFile.writeText(fingerprint) }
                        }
                        binaryTrie = BinaryTrieDictionary.mapFromFile(cacheFile)
                        hasUnmappedDynamicMutations = false
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
        // Normalized lowercase alphabetic prefix typed by the user.
        val query = prefix.trim().lowercase(Locale.ROOT)
        if (query.isEmpty() || !query.all { it in 'a'..'z' } || limit <= 0) return emptyList()

        // Snapshot of user-learned words under the lock so iteration is thread-safe.
        val learned = synchronized(learnedWords) { learnedWords.toSet() }
        // LinkedHashSet preserves stage-priority order while deduplicating across learned, Trie, and SymSpell hits.
        val results = LinkedHashSet<String>()

        // Stage 1: Prioritize personal learned words matching the typed prefix.
        for (word in learned) {
            if (isWordAvailable(word) && word.startsWith(query)) {
                results.add(word)
                if (results.size >= limit) return results.toList()
            }
        }

        // Stage 2: Query Trie prefix completions (merging live in-memory trie when unmapped mutations exist).
        val mmapCompletions = binaryTrie?.predictForPrefix(query, limit = limit + 4).orEmpty()
        val dynamicCompletions = if (hasUnmappedDynamicMutations || binaryTrie == null) {
            trieDictionary.predictForPrefix(query, limit = limit + 4)
        } else {
            emptyList()
        }
        val completions = (dynamicCompletions + mmapCompletions)
            .distinctBy { it.word }
            .sortedByDescending { it.score }
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
        // Ranked followers from english_bigrams.bin filtered against suppressed/revoked-contact rules.
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
        // Best fuzzy edit-distance fix (or prefix completion fallback) that passes availability filtering.
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

        // Only digits 2..9 map to alphabet letters on a T9 keypad; strip any non-letter digits.
        val cleanDigits = digits.filter { it in '2'..'9' }
        if (cleanDigits.isEmpty()) return emptyList()

        // Query memory-mapped Trie, merging live dynamic T9 hits whenever unmapped words,
        // learned overrides, or contact tokens are present so new additions rank immediately.
        val binaryCompletions = binaryTrie?.predictForDigits(cleanDigits, limit = limit + 4).orEmpty()
        val learnedSnapshot: Set<String>
        val needsDynamicMerge: Boolean
        synchronized(learnedWords) {
            learnedSnapshot = learnedWords.toSet()
            needsDynamicMerge = hasUnmappedDynamicMutations ||
                binaryTrie == null ||
                learnedWords.isNotEmpty() ||
                contactOnlyWords.isNotEmpty()
        }
        val dynamicCompletions = if (needsDynamicMerge) {
            t9Engine.predictForDigits(cleanDigits, limit = limit + 4)
        } else {
            emptyList()
        }
        val combined = if (binaryCompletions.isEmpty()) {
            dynamicCompletions
        } else if (dynamicCompletions.isEmpty()) {
            binaryCompletions
        } else {
            (dynamicCompletions + binaryCompletions)
                .distinctBy { it.word }
                .sortedWith(
                    compareByDescending<CandidateWord> { it.word in learnedSnapshot }
                        .thenByDescending { it.word.length == cleanDigits.length }
                        .thenByDescending { it.score }
                )
        }

        return combined
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
        context?.applicationContext?.let { cachedAppContext = it }
        val normalized = word.lowercase(Locale.ROOT).trim()
        if (normalized.isEmpty() || normalized.any { it !in 'a'..'z' }) return
        synchronized(learnedWords) {
            suppressedWords.remove(normalized)
            learnedWords.add(normalized)
            trieDictionary.insert(normalized, 500_000L)
            t9Engine = T9PredictiveEngine(trieDictionary, learnedWords.toSet(), suppressedWords.toSet())
            hasUnmappedDynamicMutations = true
            persistPreferences(context)
        }
        if (isInitialized) {
            scheduleMmapRebuild(context?.applicationContext ?: cachedAppContext)
        }
    }

    /**
     * Suppresses an unwanted candidate word and persists it to `keyboard_words` SharedPreferences.
     */
    fun suppressWord(word: String, context: Context? = null) {
        context?.applicationContext?.let { cachedAppContext = it }
        val normalized = word.lowercase(Locale.ROOT).trim()
        if (normalized.isEmpty()) return
        synchronized(learnedWords) {
            learnedWords.remove(normalized)
            suppressedWords.add(normalized)
            t9Engine = T9PredictiveEngine(trieDictionary, learnedWords.toSet(), suppressedWords.toSet())
            hasUnmappedDynamicMutations = true
            persistPreferences(context)
        }
        if (isInitialized) {
            scheduleMmapRebuild(context?.applicationContext ?: cachedAppContext)
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

    /** Computes an order-independent fingerprint across dynamic vocabularies for cache invalidation. */
    private fun computeVocabularyFingerprint(
        learned: Set<String>,
        suppressed: Set<String>,
        appTokens: Set<String> = emptySet(),
        contactTokens: Set<String> = emptySet()
    ): String {
        fun digestSet(words: Set<String>): String {
            var sum = 0L
            for (w in words) sum += w.hashCode().toLong() * 31L
            return "${words.size}:$sum"
        }
        return "v1|l=${digestSet(learned)}|s=${digestSet(suppressed)}|a=${digestSet(appTokens)}|c=${digestSet(contactTokens)}"
    }

    /** Writes the compiled [BinaryTrieDictionary] via a temp file rename to avoid partial reads/trailing bytes. */
    private fun writeBinaryTrieAtomically(trie: CompactTrieDictionary, targetFile: File) {
        val tempFile = File(targetFile.parentFile, "$MMAP_CACHE_FILE.tmp")
        if (tempFile.exists()) tempFile.delete()
        BinaryTrieDictionary.writeToFile(trie, tempFile)
        if (!tempFile.renameTo(targetFile)) {
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                BinaryTrieDictionary.writeToFile(trie, targetFile)
                tempFile.delete()
            }
        }
    }

    /** Debounces and runs a background mmap cache rebuild after dynamic word or contact updates. */
    private fun scheduleMmapRebuild(context: Context?) {
        val appContext = context?.applicationContext ?: return
        pendingRebuildJob?.cancel()
        pendingRebuildJob = scope.launch {
            delay(MMAP_REBUILD_DEBOUNCE_MS)
            mmapWriteMutex.withLock {
                runCatching {
                    val cacheFile = File(appContext.cacheDir, MMAP_CACHE_FILE)
                    val metaFile = File(appContext.cacheDir, MMAP_META_FILE)
                    val fingerprint = synchronized(learnedWords) {
                        computeVocabularyFingerprint(
                            learned = learnedWords.toSet(),
                            suppressed = suppressedWords.toSet(),
                            contactTokens = contactOnlyWords.toSet()
                        )
                    }
                    writeBinaryTrieAtomically(trieDictionary, cacheFile)
                    runCatching { metaFile.writeText(fingerprint) }
                    binaryTrie = BinaryTrieDictionary.mapFromFile(cacheFile)
                    hasUnmappedDynamicMutations = false
                }.onFailure { e ->
                    Log.w(TAG, "Background binary trie rebuild failed", e)
                }
            }
        }
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
        pendingRebuildJob?.cancel()
        pendingRebuildJob = null
        cachedAppContext = null
        synchronized(learnedWords) {
            learnedWords.clear()
            suppressedWords.clear()
            contactOnlyWords.clear()
            contactOnlyWords.addAll(contactWords.map { it.lowercase(Locale.ROOT) })
        }
        hasUnmappedDynamicMutations = false
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
