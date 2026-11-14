package com.tyejaedon.coverscreenos.keyboard.nlp

import com.tyejaedon.coverscreenos.keyboard.core.contracts.CandidateWord
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.max

/**
 * Zero-deserialization, memory-mapped binary Trie dictionary.
 *
 * Backed by a direct [ByteBuffer] (or memory-mapped file via [FileChannel.map]).
 * Enables sub-millisecond QWERTY prefix prediction and T9 decoding with zero JVM heap
 * node allocation and instantaneous cold starts.
 *
 * Binary Layout:
 * - Header (24 bytes):
 *     Magic (4 bytes): 0x43545249 ("CTRI")
 *     Version (4 bytes): 1
 *     Node Count (4 bytes)
 *     Word Count (4 bytes)
 *     Root Offset (4 bytes)
 *     Reserved (4 bytes)
 * - Node format (variable size):
 *     Flags (1 byte): bit 0 = isTerminal
 *     ChildMask (4 bytes): 26 bits ('a'..'z')
 *     MaxSubtreeFrequency (8 bytes)
 *     [If isTerminal]:
 *         Frequency (8 bytes)
 *         WordLength (2 bytes)
 *         WordBytes (WordLength bytes, UTF-8)
 *     ChildOffsets (4 bytes * childCount): offsets to child nodes from buffer origin
 */
class BinaryTrieDictionary(
    private val buffer: ByteBuffer
) {

    init {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        validateHeader()
    }

    val wordCount: Int = buffer.getInt(12)
    val nodeCount: Int = buffer.getInt(8)
    private val rootOffset: Int = buffer.getInt(16)

    private fun validateHeader() {
        require(buffer.capacity() >= HEADER_SIZE) { "Buffer too small for Trie header: ${buffer.capacity()}" }
        val magic = buffer.getInt(0)
        require(magic == MAGIC) { "Invalid Trie magic: 0x${Integer.toHexString(magic)}" }
        val version = buffer.getInt(4)
        require(version == VERSION) { "Unsupported Trie version: $version (expected $VERSION)" }
    }

    /**
     * Node pointer representation pointing into the underlying [buffer].
     */
    data class BinaryNode(
        val offset: Int,
        val isTerminal: Boolean,
        val childMask: Int,
        val maxSubtreeFrequency: Long,
        val frequency: Long,
        val terminalWord: String?
    )

    fun readNode(offset: Int): BinaryNode {
        var cursor = offset
        val flags = buffer.get(cursor).toInt()
        cursor += 1
        val isTerminal = (flags and FLAG_TERMINAL) != 0

        val childMask = buffer.getInt(cursor)
        cursor += 4

        val maxSubtreeFreq = buffer.getLong(cursor)
        cursor += 8

        var freq = 0L
        var word: String? = null
        if (isTerminal) {
            freq = buffer.getLong(cursor)
            cursor += 8
            val wordLen = buffer.getShort(cursor).toInt() and 0xFFFF
            cursor += 2
            val bytes = ByteArray(wordLen)
            val dup = buffer.duplicate()
            dup.position(cursor)
            dup.get(bytes)
            word = String(bytes, StandardCharsets.UTF_8)
        }

        return BinaryNode(
            offset = offset,
            isTerminal = isTerminal,
            childMask = childMask,
            maxSubtreeFrequency = maxSubtreeFreq,
            frequency = freq,
            terminalWord = word
        )
    }

    fun getChildOffset(parentOffset: Int, char: Char): Int? {
        val idx = char - 'a'
        if (idx !in 0..25) return null
        val bit = 1 shl idx

        val flags = buffer.get(parentOffset).toInt()
        val isTerminal = (flags and FLAG_TERMINAL) != 0

        val childMask = buffer.getInt(parentOffset + 1)
        if ((childMask and bit) == 0) return null

        val rank = Integer.bitCount(childMask and (bit - 1))

        var childrenTableOffset = parentOffset + 1 + 4 + 8
        if (isTerminal) {
            val wordLen = buffer.getShort(childrenTableOffset + 8).toInt() and 0xFFFF
            childrenTableOffset += 8 + 2 + wordLen
        }

        return buffer.getInt(childrenTableOffset + (rank * 4))
    }

    fun findNode(prefix: String): BinaryNode? {
        val normalized = prefix.lowercase(Locale.ROOT).trim()
        var currentOffset = rootOffset
        for (i in 0 until normalized.length) {
            val c = normalized[i]
            val childOffset = getChildOffset(currentOffset, c) ?: return null
            currentOffset = childOffset
        }
        return readNode(currentOffset)
    }

    fun contains(word: String): Boolean {
        val node = findNode(word)
        return node?.isTerminal == true
    }

    fun getFrequency(word: String): Long {
        val node = findNode(word)
        return if (node?.isTerminal == true) node.frequency else 0L
    }

    /**
     * Fast branch-and-bound prefix prediction returning up to [limit] candidate words.
     */
    fun predictForPrefix(prefix: String, limit: Int = 10): List<CandidateWord> {
        if (limit <= 0) return emptyList()
        val normalized = prefix.lowercase(Locale.ROOT).trim()
        var currentOffset = rootOffset
        for (i in 0 until normalized.length) {
            val childOffset = getChildOffset(currentOffset, normalized[i]) ?: return emptyList()
            currentOffset = childOffset
        }

        val queue = PriorityQueue<Pair<Int, Long>> { a, b ->
            b.second.compareTo(a.second)
        }
        val prefixNode = readNode(currentOffset)
        queue.add(currentOffset to prefixNode.maxSubtreeFrequency)

        val results = mutableListOf<CandidateWord>()

        while (!queue.isEmpty() && results.size < limit) {
            val (nodeOffset, _) = queue.poll() ?: break
            val node = readNode(nodeOffset)

            if (node.isTerminal && node.terminalWord != null) {
                results.add(
                    CandidateWord(
                        word = node.terminalWord,
                        score = node.frequency.toFloat(),
                        isAutocorrectDefault = results.isEmpty() && node.terminalWord == normalized
                    )
                )
                if (results.size >= limit) break
            }

            // Enqueue all existing children
            var mask = node.childMask
            while (mask != 0) {
                val lowestBit = mask and -mask
                val charIndex = Integer.numberOfTrailingZeros(lowestBit)
                val c = ('a' + charIndex)
                val childOffset = getChildOffset(nodeOffset, c)
                if (childOffset != null) {
                    val childMaxFreq = buffer.getLong(childOffset + 1 + 4)
                    queue.add(childOffset to childMaxFreq)
                }
                mask = mask and (mask - 1)
            }
        }

        return results
    }

    /**
     * High-speed T9 candidate decoding traversing the binary buffer directly.
     */
    fun predictForDigits(digits: String, limit: Int = 10): List<CandidateWord> {
        if (digits.isEmpty() || limit <= 0) return emptyList()
        val cleanDigits = digits.filter { it in '2'..'9' }
        if (cleanDigits.isEmpty()) return emptyList()

        val exactMatches = mutableListOf<BinaryNode>()
        searchT9(rootOffset, cleanDigits, 0, exactMatches)

        exactMatches.sortByDescending { it.frequency }

        val results = mutableListOf<CandidateWord>()
        for ((idx, node) in exactMatches.take(limit).withIndex()) {
            val word = node.terminalWord ?: continue
            results.add(
                CandidateWord(
                    word = word,
                    score = node.frequency.toFloat(),
                    isAutocorrectDefault = idx == 0
                )
            )
        }
        return results
    }

    private fun searchT9(
        currentOffset: Int,
        digits: String,
        digitIndex: Int,
        exactMatches: MutableList<BinaryNode>
    ) {
        if (digitIndex == digits.length) {
            val node = readNode(currentOffset)
            if (node.isTerminal && node.terminalWord != null) {
                exactMatches.add(node)
            }
            return
        }

        val letters = T9_DIGIT_MAP[digits[digitIndex]] ?: return
        for (i in 0 until letters.length) {
            val childOffset = getChildOffset(currentOffset, letters[i]) ?: continue
            searchT9(childOffset, digits, digitIndex + 1, exactMatches)
        }
    }

    companion object {
        const val MAGIC = 0x43545249 // "CTRI"
        const val VERSION = 1
        const val HEADER_SIZE = 24
        private const val FLAG_TERMINAL = 0x01

        private val T9_DIGIT_MAP: Map<Char, String> = mapOf(
            '2' to "abc",
            '3' to "def",
            '4' to "ghi",
            '5' to "jkl",
            '6' to "mno",
            '7' to "pqrs",
            '8' to "tuv",
            '9' to "wxyz"
        )

        /**
         * Compiles an in-memory [CompactTrieDictionary] into a serialized [ByteBuffer].
         */
        fun compile(trie: CompactTrieDictionary): ByteBuffer {
            // First pass: collect nodes in post-order or breadth-first to compute offsets
            class NodeInfo(
                val trieNode: CompactTrieDictionary.TrieNode,
                var offset: Int = 0,
                var serializedBytes: ByteArray = ByteArray(0)
            )

            val allNodes = mutableListOf<NodeInfo>()
            fun collect(node: CompactTrieDictionary.TrieNode): NodeInfo {
                val info = NodeInfo(node)
                allNodes.add(info)
                node.forEachChild { _, child ->
                    collect(child)
                }
                return info
            }

            val rootInfo = collect(trie.root)

            // Two-pass serialization:
            // Pass 1: compute size of each node without children table, then allocate layout
            var currentOffset = HEADER_SIZE
            val nodeOffsetMap = HashMap<CompactTrieDictionary.TrieNode, Int>()

            // We do BFS or layout so all child offsets are known
            for (info in allNodes) {
                info.offset = currentOffset
                nodeOffsetMap[info.trieNode] = currentOffset

                var childMask = 0
                info.trieNode.forEachChild { c, _ ->
                    val idx = c - 'a'
                    childMask = childMask or (1 shl idx)
                }
                val childCount = Integer.bitCount(childMask)

                var nodeSize = 1 + 4 + 8 // flags(1) + childMask(4) + maxSubtreeFreq(8)
                if (info.trieNode.isTerminal) {
                    val wordBytes = (info.trieNode.terminalWord ?: "").toByteArray(StandardCharsets.UTF_8)
                    nodeSize += 8 + 2 + wordBytes.size // freq(8) + wordLen(2) + wordBytes
                }
                nodeSize += childCount * 4 // child offsets table
                currentOffset += nodeSize
            }

            val totalSize = currentOffset
            val buffer = ByteBuffer.allocateDirect(totalSize).order(ByteOrder.LITTLE_ENDIAN)

            // Write header
            buffer.putInt(MAGIC)
            buffer.putInt(VERSION)
            buffer.putInt(allNodes.size)
            buffer.putInt(trie.size)
            buffer.putInt(rootInfo.offset)
            buffer.putInt(0) // reserved

            // Write nodes
            for (info in allNodes) {
                val node = info.trieNode
                var flags = 0
                if (node.isTerminal) flags = flags or FLAG_TERMINAL
                buffer.put(flags.toByte())

                var childMask = 0
                val childrenList = mutableListOf<CompactTrieDictionary.TrieNode>()
                node.forEachChild { c, child ->
                    val idx = c - 'a'
                    childMask = childMask or (1 shl idx)
                    childrenList.add(child)
                }
                buffer.putInt(childMask)
                buffer.putLong(node.maxSubtreeFrequency)

                if (node.isTerminal) {
                    buffer.putLong(node.frequency)
                    val wordBytes = (node.terminalWord ?: "").toByteArray(StandardCharsets.UTF_8)
                    buffer.putShort(wordBytes.size.toShort())
                    buffer.put(wordBytes)
                }

                for (child in childrenList) {
                    val childOff = nodeOffsetMap[child] ?: 0
                    buffer.putInt(childOff)
                }
            }

            buffer.position(0)
            return buffer
        }

        /**
         * Writes a compiled [BinaryTrieDictionary] to disk.
         */
        fun writeToFile(trie: CompactTrieDictionary, file: File) {
            val byteBuffer = compile(trie)
            RandomAccessFile(file, "rw").use { raf ->
                val channel = raf.channel
                channel.write(byteBuffer)
            }
        }

        /**
         * Memory-maps a compiled Trie file from disk using [FileChannel.MapMode.READ_ONLY].
         */
        fun mapFromFile(file: File): BinaryTrieDictionary {
            require(file.exists() && file.length() >= HEADER_SIZE) { "Invalid trie file: ${file.absolutePath}" }
            RandomAccessFile(file, "r").use { raf ->
                val channel = raf.channel
                val mmapBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                return BinaryTrieDictionary(mmapBuffer)
            }
        }
    }
}
