package com.tyejaedon.coverscreenos.keyboard.nlp

import android.content.Context
import android.util.Log
import com.coveros.keyboard.nlp.CompactTrieDictionary
import com.coveros.keyboard.nlp.SymSpellAutocorrect
import com.coveros.keyboard.nlp.T9PredictiveEngine
import com.tyejaedon.coverscreenos.keyboard.floris.CoverLayoutMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * High-performance application-wide NLP coordination manager.
 *
 * Coordinates [CompactTrieDictionary], [SymSpellAutocorrect], and [T9PredictiveEngine]
 * to deliver real-time predictive word completions, fuzzy fat-finger corrections,
 * and 9-key T9 candidate decoding on clamshell cover displays.
 */
object CoverKeyboardNlpManager {

    private const val TAG = "CoverKeyboardNlp"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()

    @Volatile
    private var isInitialized = false

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

    private var trieDictionary: CompactTrieDictionary = CompactTrieDictionary(defaultCorpus)
    private var symSpellAutocorrect: SymSpellAutocorrect = SymSpellAutocorrect(defaultCorpus)
    private var t9Engine: T9PredictiveEngine = T9PredictiveEngine(trieDictionary)

    /**
     * Asynchronously loads the bundled compressed corpus from assets on [Dispatchers.IO].
     */
    fun initializeFromContext(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext
        scope.launch {
            initMutex.withLock {
                if (isInitialized) return@withLock
                try {
                    val frequencies = HashMap<String, Long>(15000)
                    frequencies.putAll(defaultCorpus)

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

                    val newTrie = CompactTrieDictionary(frequencies)
                    val newSymSpell = SymSpellAutocorrect(frequencies)
                    val newT9 = T9PredictiveEngine(newTrie)

                    trieDictionary = newTrie
                    symSpellAutocorrect = newSymSpell
                    t9Engine = newT9
                    isInitialized = true
                    Log.d(TAG, "CoverKeyboardNlpManager initialized with ${frequencies.size} words")
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed initializing CoverKeyboardNlpManager", t)
                }
            }
        }
    }

    /**
     * Provides suggestions for QWERTY input using prefix lookup and fuzzy autocorrect.
     */
    fun getQwertySuggestions(prefix: String, limit: Int = 3): List<String> {
        val query = prefix.trim().lowercase(Locale.ROOT)
        if (query.isEmpty() || !query.all { it in 'a'..'z' }) return emptyList()

        val results = LinkedHashSet<String>()
        val completions = trieDictionary.predictForPrefix(query, limit = limit)
        for (c in completions) {
            results.add(c.word)
            if (results.size >= limit) break
        }

        if (results.size < limit) {
            val corrections = symSpellAutocorrect.suggest(query, limit = limit)
            for (c in corrections) {
                results.add(c.word)
                if (results.size >= limit) break
            }
        }

        return results.toList()
    }

    /**
     * Provides candidate completions for a T9 digit sequence (e.g. "43556" -> "hello").
     */
    fun getT9Suggestions(digits: String, limit: Int = 3): List<String> {
        val cleanDigits = digits.filter { it in '2'..'9' }
        if (cleanDigits.isEmpty()) return emptyList()

        return t9Engine.predictForDigits(cleanDigits, limit = limit)
            .map { it.word }
    }

    /**
     * High-level candidate retrieval dispatching to either T9 or QWERTY engines based on mode.
     */
    fun getCandidates(mode: CoverLayoutMode, query: String, limit: Int = 3): List<String> {
        return when (mode) {
            CoverLayoutMode.T9_PREDICTIVE -> {
                if (query.all { it in '2'..'9' }) {
                    getT9Suggestions(query, limit)
                } else {
                    getQwertySuggestions(query, limit)
                }
            }
            CoverLayoutMode.QWERTY -> getQwertySuggestions(query, limit)
            CoverLayoutMode.SYMBOLS, CoverLayoutMode.NUMERIC -> emptyList()
        }
    }

    /**
     * Sets dictionary instances directly for unit testing.
     */
    internal fun setEnginesForTest(
        trie: CompactTrieDictionary,
        symSpell: SymSpellAutocorrect,
        t9: T9PredictiveEngine
    ) {
        trieDictionary = trie
        symSpellAutocorrect = symSpell
        t9Engine = t9
        isInitialized = true
    }
}
