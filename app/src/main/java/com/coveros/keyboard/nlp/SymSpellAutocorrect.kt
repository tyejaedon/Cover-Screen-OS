package com.coveros.keyboard.nlp

import com.coveros.keyboard.core.contracts.CandidateWord
import com.darkrockstudios.symspellkt.common.DamerauLevenshteinDistance
import com.darkrockstudios.symspellkt.common.Murmur3HashFunction
import com.darkrockstudios.symspellkt.common.SpellCheckSettings
import com.darkrockstudios.symspellkt.common.Verbosity
import com.darkrockstudios.symspellkt.impl.InMemoryDictionaryHolder
import com.darkrockstudios.symspellkt.impl.SymSpell
import java.util.Locale
import kotlin.math.ln
import kotlin.math.max

/**
 * High-performance spell checker and autocorrect engine.
 * Combines SymSpell's symmetric delete candidate generation with physical QWERTY key
 * distance weighting from [QwertyGeometry] to accurately resolve fat-finger slips.
 */
class SymSpellAutocorrect(
    wordsWithFrequency: Map<String, Long> = emptyMap(),
    maxEditDistance: Double = 2.0,
    topK: Int = 15
) {

    private val settings = SpellCheckSettings(maxEditDistance = maxEditDistance, topK = topK)
    private val dictionaryHolder = InMemoryDictionaryHolder(settings, Murmur3HashFunction())
    private val checker = SymSpell(settings, DamerauLevenshteinDistance(), dictionaryHolder)
    private val frequencyMap = HashMap<String, Long>(wordsWithFrequency.size)

    init {
        for ((word, freq) in wordsWithFrequency) {
            indexWord(word, freq)
        }
    }

    /**
     * Index a single word into the underlying SymSpell dictionary.
     */
    fun indexWord(word: String, frequency: Long = 1L) {
        val normalized = CompactTrieDictionary.normalizeWord(word)
        if (normalized.isEmpty()) return
        val clampedFreq = max(1L, frequency)
        frequencyMap[normalized] = clampedFreq
        try {
            checker.createDictionaryEntry(normalized, clampedFreq.toDouble())
        } catch (_: Throwable) {
            // Tolerate duplicate or edge-case entries gracefully
        }
    }

    /**
     * Suggest corrections for [input], re-ranked by physical keyboard key adjacency.
     *
     * @param input The raw or typed text string.
     * @param limit Maximum number of candidate suggestions to return.
     * @return Ranked list of [CandidateWord] candidates.
     */
    fun suggest(input: String, limit: Int = 3): List<CandidateWord> {
        if (limit <= 0) return emptyList()
        val normalized = CompactTrieDictionary.normalizeWord(input)
        if (normalized.isEmpty()) return emptyList()

        val isExactKnown = frequencyMap.containsKey(normalized)
        val exactFreq = frequencyMap[normalized] ?: 0L

        val symSpellResults = try {
            checker.lookup(normalized, Verbosity.All, editDistance = settings.maxEditDistance)
        } catch (_: Throwable) {
            emptyList()
        }

        val candidateMap = LinkedHashMap<String, ScoredCandidate>()

        // Include exact word if known
        if (isExactKnown) {
            candidateMap[normalized] = ScoredCandidate(
                word = normalized,
                distance = 0.0f,
                frequency = exactFreq,
                score = calculateScore(0.0f, exactFreq)
            )
        }

        for (item in symSpellResults) {
            val term = item.term
            if (term.isEmpty()) continue

            // Compute physical QWERTY distance between typed input and candidate term
            val physicalDistance = QwertyGeometry.weightedEditDistance(normalized, term)
            val freq = frequencyMap[term] ?: 1L
            val score = calculateScore(physicalDistance, freq)

            val existing = candidateMap[term]
            if (existing == null || score > existing.score) {
                candidateMap[term] = ScoredCandidate(term, physicalDistance, freq, score)
            }
        }

        // Sort candidates by composite score descending
        val sortedCandidates = candidateMap.values.sortedByDescending { it.score }

        if (sortedCandidates.isEmpty()) return emptyList()

        // Determine if top candidate should be marked as autocorrect default
        val top = sortedCandidates.first()
        val isDefault = when {
            // Exact word typed with no better replacement
            top.word == normalized -> true
            // Strong candidate: physical distance is small (<= 1.25) and frequency is substantial
            !isExactKnown && top.distance <= 1.5f -> true
            // Common transposition or extreme frequency imbalance (e.g. "teh" -> "the")
            isExactKnown && top.distance <= 0.8f && top.frequency > exactFreq * 10 -> true
            else -> false
        }

        val results = ArrayList<CandidateWord>(minOf(limit, sortedCandidates.size))
        for (i in 0 until minOf(limit, sortedCandidates.size)) {
            val cand = sortedCandidates[i]
            results.add(
                CandidateWord(
                    word = cand.word,
                    score = cand.score,
                    isAutocorrectDefault = (i == 0 && isDefault)
                )
            )
        }

        return results
    }

    /**
     * Corrects a single input word, returning the top correction if confident, or null.
     */
    fun correct(input: String): CandidateWord? {
        val suggestions = suggest(input, limit = 1)
        return suggestions.firstOrNull()?.takeIf { it.isAutocorrectDefault }
    }

    private fun calculateScore(physicalDistance: Float, frequency: Long): Float {
        // Frequency component: logarithmic scale
        val freqFactor = 1.0f + 0.18f * ln(max(1.0, frequency.toDouble())).toFloat()
        // Distance penalty: exponential dropoff
        val distanceFactor = 100.0f / (1.0f + physicalDistance * 2.0f)
        return distanceFactor * freqFactor
    }

    private data class ScoredCandidate(
        val word: String,
        val distance: Float,
        val frequency: Long,
        val score: Float
    )

    companion object {
        fun fromWords(words: Collection<String>): SymSpellAutocorrect {
            val total = words.size
            val map = HashMap<String, Long>(total)
            words.forEachIndexed { index, word ->
                map[word] = (total - index).toLong()
            }
            return SymSpellAutocorrect(map)
        }
    }
}
