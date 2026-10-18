package com.coveros.keyboard.nlp

import com.coveros.keyboard.core.contracts.CandidateWord
import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.max

/**
 * High-performance, memory-efficient prefix Trie for word completion, frequency-ranked
 * candidate generation, and spatial hitbox prior calculation.
 */
class CompactTrieDictionary(
    initialWords: Map<String, Long> = emptyMap()
) {

    class TrieNode(val char: Char = '\u0000') {
        var isTerminal: Boolean = false
        var frequency: Long = 0L
        var maxSubtreeFrequency: Long = 0L
        var terminalWord: String? = null

        // Fixed-size array for direct O(1) indexed child access ('a'..'z')
        private val children: Array<TrieNode?> = arrayOfNulls(26)

        fun getChild(c: Char): TrieNode? {
            val idx = c - 'a'
            return if (idx in 0..25) children[idx] else null
        }

        fun getOrCreateChild(c: Char): TrieNode {
            val idx = c - 'a'
            require(idx in 0..25) { "Character '$c' outside 'a'..'z'" }
            var child = children[idx]
            if (child == null) {
                child = TrieNode(c)
                children[idx] = child
            }
            return child
        }

        fun forEachChild(action: (Char, TrieNode) -> Unit) {
            for (i in 0..25) {
                val child = children[i] ?: continue
                action(('a' + i), child)
            }
        }

        val childCount: Int
            get() {
                var count = 0
                for (i in 0..25) {
                    if (children[i] != null) count++
                }
                return count
            }
    }

    val root = TrieNode()
    var size: Int = 0
        private set

    init {
        for ((word, freq) in initialWords) {
            insert(word, freq)
        }
    }

    /**
     * Inserts a word with the specified frequency into the Trie.
     */
    fun insert(word: String, frequency: Long = 1L) {
        val normalized = normalizeWord(word)
        if (normalized.isEmpty()) return

        val clampedFreq = max(1L, frequency)
        var current = root
        current.maxSubtreeFrequency = max(current.maxSubtreeFrequency, clampedFreq)

        for (i in 0 until normalized.length) {
            val c = normalized[i]
            current = current.getOrCreateChild(c)
            current.maxSubtreeFrequency = max(current.maxSubtreeFrequency, clampedFreq)
        }

        if (!current.isTerminal) {
            current.isTerminal = true
            size++
        }
        current.frequency = max(current.frequency, clampedFreq)
        current.terminalWord = word
    }

    /**
     * Checks if the exact word exists in the dictionary.
     */
    fun contains(word: String): Boolean {
        val node = findNode(word)
        return node?.isTerminal == true
    }

    /**
     * Returns the frequency of the word, or 0 if absent.
     */
    fun getFrequency(word: String): Long {
        val node = findNode(word)
        return if (node?.isTerminal == true) node.frequency else 0L
    }

    /**
     * Traverses the Trie to locate the node matching the given prefix.
     */
    fun findNode(prefix: String): TrieNode? {
        val normalized = normalizeWord(prefix)
        var current = root
        for (i in 0 until normalized.length) {
            current = current.getChild(normalized[i]) ?: return null
        }
        return current
    }

    /**
     * Predicts completions starting with the given [prefix], sorted descending by frequency.
     * Uses branch-and-bound pruning based on [TrieNode.maxSubtreeFrequency] to guarantee
     * sub-millisecond retrieval without searching irrelevant subtrees.
     */
    fun predictForPrefix(prefix: String, limit: Int = 10): List<CandidateWord> {
        if (limit <= 0) return emptyList()
        val normalized = normalizeWord(prefix)
        val prefixNode = findNode(normalized) ?: return emptyList()

        val results = mutableListOf<CandidateWord>()

        // Check if prefix itself is an exact word
        if (prefixNode.isTerminal && prefixNode.terminalWord != null) {
            results.add(
                CandidateWord(
                    word = prefixNode.terminalWord!!,
                    score = prefixNode.frequency.toFloat(),
                    isAutocorrectDefault = true
                )
            )
        }

        // Branch-and-bound exploration using a priority queue sorted by maxSubtreeFrequency descending
        val queue = PriorityQueue<TrieNode>(compareByDescending { it.maxSubtreeFrequency })

        prefixNode.forEachChild { _, child ->
            queue.add(child)
        }

        val collectedWords = mutableListOf<TrieNode>()

        while (!queue.isEmpty()) {
            val node = queue.poll() ?: break

            if (node.isTerminal && node.terminalWord != null) {
                // Avoid duplicating exact prefix if already added
                if (node.terminalWord != prefixNode.terminalWord) {
                    collectedWords.add(node)
                }
            }

            // Pruning condition: if we already have enough candidates and the best remaining
            // node's maxSubtreeFrequency cannot beat what we have, we can stop.
            if (results.size + collectedWords.size >= limit * 2) {
                val minCollected = collectedWords.minOfOrNull { it.frequency } ?: 0L
                if (node.maxSubtreeFrequency <= minCollected) {
                    break
                }
            }

            node.forEachChild { _, child ->
                queue.add(child)
            }
        }

        // Sort collected completions by frequency descending
        collectedWords.sortByDescending { it.frequency }
        for (node in collectedWords) {
            if (results.size >= limit) break
            val word = node.terminalWord ?: continue
            results.add(
                CandidateWord(
                    word = word,
                    score = node.frequency.toFloat(),
                    isAutocorrectDefault = false
                )
            )
        }

        return results.take(limit)
    }

    /**
     * Computes the probability distribution over the next possible characters given [prefix].
     * Used by [SpatialHitboxResolver] to dynamically expand the touch boundaries of high-probability keys.
     *
     * The resulting map contains probabilities for all 26 alphabetic characters ('a'..'z')
     * summing to 1.0f.
     */
    fun getNextCharPriors(prefix: String): Map<Char, Float> {
        val priors = HashMap<Char, Float>(26)
        val node = if (prefix.isEmpty()) root else findNode(prefix)

        if (node == null) {
            // Uniform fallback prior (1/26) when prefix is not found in dictionary
            val uniform = 1.0f / 26.0f
            for (c in 'a'..'z') {
                priors[c] = uniform
            }
            return priors
        }

        var totalWeight = 0.0
        val weights = FloatArray(26)
        val smoothingEpsilon = 0.01f // Base smoothing so adjacent keys never have 0 prior

        for (i in 0..25) {
            val c = ('a' + i)
            val child = node.getChild(c)
            val weight = if (child != null) {
                max(1.0f, child.maxSubtreeFrequency.toFloat()) + smoothingEpsilon
            } else {
                smoothingEpsilon
            }
            weights[i] = weight
            totalWeight += weight
        }

        val invTotal = if (totalWeight > 0.0) (1.0 / totalWeight).toFloat() else (1.0f / 26.0f)
        for (i in 0..25) {
            val c = ('a' + i)
            priors[c] = weights[i] * invTotal
        }

        return priors
    }

    companion object {
        fun normalizeWord(input: String): String {
            val sb = StringBuilder(input.length)
            for (i in 0 until input.length) {
                val c = input[i].lowercaseChar()
                if (c in 'a'..'z') {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        /**
         * Convenience factory from a list of words.
         */
        fun fromWords(words: Collection<String>): CompactTrieDictionary {
            val trie = CompactTrieDictionary()
            val total = words.size
            words.forEachIndexed { index, word ->
                // Higher rank for earlier words in frequency-ordered lists
                val syntheticFreq = (total - index).toLong()
                trie.insert(word, syntheticFreq)
            }
            return trie
        }
    }
}
