package com.coveros.keyboard.nlp

import com.coveros.keyboard.core.contracts.CandidateWord
import java.util.Locale
import kotlin.math.max

/**
 * T9 predictive and multi-tap decoder for the 3x4 cover screen keypad layout.
 * Evaluates digit sequences against [CompactTrieDictionary] via pruned tree traversal.
 */
class T9PredictiveEngine(
    private val dictionary: CompactTrieDictionary,
    private val learnedWords: Set<String> = emptySet(),
    private val suppressedWords: Set<String> = emptySet()
) {

    companion object {
        private val DIGIT_TO_LETTERS: Map<Char, String> = mapOf(
            '2' to "abc",
            '3' to "def",
            '4' to "ghi",
            '5' to "jkl",
            '6' to "mno",
            '7' to "pqrs",
            '8' to "tuv",
            '9' to "wxyz"
        )

        private val CHAR_TO_DIGIT: Map<Char, Char> = buildMap {
            for ((digit, letters) in DIGIT_TO_LETTERS) {
                for (c in letters) {
                    put(c, digit)
                }
            }
        }

        /**
         * Maps an alphabetic character to its corresponding T9 digit ('2'..'9').
         */
        fun charToDigit(c: Char): Char? = CHAR_TO_DIGIT[c.lowercaseChar()]

        /**
         * Converts an entire word string into its T9 digit sequence (e.g. "hello" -> "43556").
         */
        fun wordToDigits(word: String): String {
            val sb = StringBuilder(word.length)
            for (i in 0 until word.length) {
                val digit = charToDigit(word[i])
                if (digit != null) {
                    sb.append(digit)
                }
            }
            return sb.toString()
        }

        /**
         * Decodes grouped multi-tap presses into characters (e.g. "44-33-55-55-666" -> "hello").
         */
        fun decodeMultiTapGrouped(groupedString: String): String {
            if (groupedString.isEmpty()) return ""
            val groups = groupedString.split('-')
            val sb = StringBuilder(groups.size)

            for (group in groups) {
                if (group.isEmpty()) continue
                val digit = group[0]
                val letters = DIGIT_TO_LETTERS[digit]
                if (letters != null) {
                    val idx = (group.length - 1) % letters.length
                    sb.append(letters[idx])
                } else if (digit in '0'..'9') {
                    sb.append(digit)
                }
            }

            return sb.toString()
        }
    }

    /**
     * Decodes predictive digit sequences (e.g. "43556") into ranked dictionary words.
     * Also accepts grouped multi-tap strings containing dashes (e.g. "44-33-55-55-666").
     *
     * @param digits Input string of digits '2'..'9' or grouped presses.
     * @param limit Maximum number of candidate suggestions to return.
     * @return Ranked list of [CandidateWord] candidates.
     */
    fun predictForDigits(digits: String, limit: Int = 10): List<CandidateWord> {
        if (digits.isEmpty() || limit <= 0) return emptyList()

        // Handle multi-tap format if grouped by dashes
        if ('-' in digits) {
            val decodedWord = decodeMultiTapGrouped(digits)
            if (decodedWord.isNotEmpty()) {
                val prefixCompletions = dictionary.predictForPrefix(decodedWord, limit)
                if (prefixCompletions.isNotEmpty()) return prefixCompletions
                return listOf(
                    CandidateWord(
                        word = decodedWord,
                        score = 100.0f,
                        isAutocorrectDefault = true
                    )
                )
            }
            return emptyList()
        }

        // Clean digits: filter to '2'..'9'
        val cleanDigits = digits.filter { it in '2'..'9' }
        if (cleanDigits.isEmpty()) return emptyList()

        val exactMatches = mutableListOf<CompactTrieDictionary.TrieNode>()
        val prefixCompletions = mutableListOf<CompactTrieDictionary.TrieNode>()

        // DFS through Trie following allowed letters for each digit position
        searchT9(
            node = dictionary.root,
            digits = cleanDigits,
            digitIndex = 0,
            exactMatches = exactMatches,
            prefixCompletions = prefixCompletions
        )

        val candidateMap = LinkedHashMap<String, CandidateWord>()

        // Process exact length matches first (highest priority)
        val sortedExact = exactMatches
            .filter { it.terminalWord != null && it.terminalWord !in suppressedWords }
            .sortedWith(
                compareByDescending<CompactTrieDictionary.TrieNode> { it.terminalWord in learnedWords }
                    .thenByDescending { it.frequency }
            )

        for ((idx, node) in sortedExact.withIndex()) {
            if (candidateMap.size >= limit) break
            val word = node.terminalWord ?: continue
            val isLearned = word in learnedWords
            val score = if (isLearned) node.frequency.toFloat() * 10.0f else node.frequency.toFloat()
            candidateMap[word] = CandidateWord(
                word = word,
                score = score,
                isAutocorrectDefault = (idx == 0)
            )
        }

        // Fill remaining slots with prefix completions
        val sortedCompletions = prefixCompletions
            .filter { it.terminalWord != null && it.terminalWord !in suppressedWords && it.terminalWord !in candidateMap }
            .sortedWith(
                compareByDescending<CompactTrieDictionary.TrieNode> { it.terminalWord in learnedWords }
                    .thenByDescending { it.frequency }
            )

        for (node in sortedCompletions) {
            if (candidateMap.size >= limit) break
            val word = node.terminalWord ?: continue
            val isLearned = word in learnedWords
            val score = if (isLearned) node.frequency.toFloat() * 5.0f else node.frequency.toFloat() * 0.8f
            candidateMap[word] = CandidateWord(
                word = word,
                score = score,
                isAutocorrectDefault = false
            )
        }

        return candidateMap.values.take(limit)
    }

    private fun searchT9(
        node: CompactTrieDictionary.TrieNode,
        digits: String,
        digitIndex: Int,
        exactMatches: MutableList<CompactTrieDictionary.TrieNode>,
        prefixCompletions: MutableList<CompactTrieDictionary.TrieNode>
    ) {
        if (digitIndex == digits.length) {
            // Reached exact length
            if (node.isTerminal) {
                exactMatches.add(node)
            }
            // Collect prefix completions under this node
            collectCompletions(node, prefixCompletions, maxCollect = 30)
            return
        }

        val digit = digits[digitIndex]
        val letters = DIGIT_TO_LETTERS[digit] ?: return

        for (i in 0 until letters.length) {
            val char = letters[i]
            val child = node.getChild(char) ?: continue
            searchT9(child, digits, digitIndex + 1, exactMatches, prefixCompletions)
        }
    }

    private fun collectCompletions(
        node: CompactTrieDictionary.TrieNode,
        output: MutableList<CompactTrieDictionary.TrieNode>,
        maxCollect: Int
    ) {
        if (output.size >= maxCollect) return

        node.forEachChild { _, child ->
            if (child.isTerminal) {
                output.add(child)
            }
            collectCompletions(child, output, maxCollect)
        }
    }
}
