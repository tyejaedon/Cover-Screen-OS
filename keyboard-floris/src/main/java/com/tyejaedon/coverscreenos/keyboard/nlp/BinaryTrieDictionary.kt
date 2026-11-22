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
    /** Direct or memory-mapped little-endian buffer containing the serialized CTRI trie. */
    private val buffer: ByteBuffer
) {

    init {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        validateHeader()
    }

    /** Total number of terminal dictionary words stored in the binary trie (header offset 12). */
    val wordCount: Int = buffer.getInt(12)
    /** Total number of trie nodes serialized in the buffer (header offset 8). */
    val nodeCount: Int = buffer.getInt(8)
    /** Byte offset inside [buffer] where the root node record begins (header offset 16). */
    private val rootOffset: Int = buffer.getInt(16)

    /** Verifies buffer size, `"CTRI"` magic header, and format version before any reads. */
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
        /** Byte offset of this node from the start of [buffer]. */
        val offset: Int,
        /** True when the path from root to this node forms a complete dictionary word. */
        val isTerminal: Boolean,
        /** 26-bit bitmask (`bit 0` = `'a'` .. `bit 25` = `'z'`) indicating which child edges exist. */
        val childMask: Int,
        /** Highest word frequency anywhere in this node's subtree (used for branch-and-bound pruning). */
        val maxSubtreeFrequency: Long,
        /** Word frequency if [isTerminal] is true, or `0L` for intermediate prefix-only nodes. */
        val frequency: Long,
        /** UTF-8 decoded word stored at this terminal node, or `null` if non-terminal. */
        val terminalWord: String?
    )

    /** Decodes a single node record from [buffer] starting at byte [offset]. */
    fun readNode(offset: Int): BinaryNode {
        var cursor = offset // Running byte position inside the packed node record
        val flags = buffer.get(cursor).toInt() // Bit flags (currently bit 0 = FLAG_TERMINAL)
        cursor += 1
        val isTerminal = (flags and FLAG_TERMINAL) != 0

        val childMask = buffer.getInt(cursor) // 26-bit presence mask for 'a'..'z' children
        cursor += 4

        val maxSubtreeFreq = buffer.getLong(cursor) // Upper bound on frequency in this subtree
        cursor += 8

        var freq = 0L
        var word: String? = null
        if (isTerminal) {
            freq = buffer.getLong(cursor)
            cursor += 8
            val wordLen = buffer.getShort(cursor).toInt() and 0xFFFF // Unsigned 16-bit UTF-8 byte length
            cursor += 2
            val bytes = ByteArray(wordLen)
            val dup = buffer.duplicate() // Thread-safe view so shared buffer position isn't mutated
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

    /**
     * Looks up the byte offset of child edge [char] (`'a'..'z'`) from [parentOffset] in O(1)
     * without allocating objects, using popcount over [BinaryNode.childMask] to index the
     * packed 4-byte child-offset table.
     */
    fun getChildOffset(parentOffset: Int, char: Char): Int? {
        val idx = char - 'a'
        if (idx !in 0..25) return null
        val bit = 1 shl idx // Single-bit flag corresponding to letter `char`

        val flags = buffer.get(parentOffset).toInt()
        val isTerminal = (flags and FLAG_TERMINAL) != 0

        val childMask = buffer.getInt(parentOffset + 1)
        if ((childMask and bit) == 0) return null

        // Number of present children before `char` determines its slot in the dense offset array.
        val rank = Integer.bitCount(childMask and (bit - 1))

        var childrenTableOffset = parentOffset + 1 + 4 + 8 // Skip flags(1) + childMask(4) + maxSubtreeFreq(8)
        if (isTerminal) {
            val wordLen = buffer.getShort(childrenTableOffset + 8).toInt() and 0xFFFF
            childrenTableOffset += 8 + 2 + wordLen // Skip frequency(8) + wordLen(2) + UTF-8 word bytes
        }

        return buffer.getInt(childrenTableOffset + (rank * 4))
    }

    /** Walks from [rootOffset] along [prefix] and returns the terminal/prefix [BinaryNode], or `null`. */
    fun findNode(prefix: String): BinaryNode? {
        val normalized = prefix.lowercase(Locale.ROOT).trim()
        var currentOffset = rootOffset // Cursor tracking current node byte offset during traversal
        for (i in 0 until normalized.length) {
            val c = normalized[i]
            val childOffset = getChildOffset(currentOffset, c) ?: return null
            currentOffset = childOffset
        }
        return readNode(currentOffset)
    }

    /** Returns `true` if [word] exists as a complete terminal word in the binary trie. */
    fun contains(word: String): Boolean {
        val node = findNode(word)
        return node?.isTerminal == true
    }

    /** Returns the stored corpus frequency for [word], or `0L` if absent. */
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

        // Max-heap of (nodeOffset, maxSubtreeFrequency) exploring highest-potential branches first.
        val queue = PriorityQueue<Pair<Int, Long>> { a, b ->
            b.second.compareTo(a.second)
        }
        val prefixNode = readNode(currentOffset)
        queue.add(currentOffset to prefixNode.maxSubtreeFrequency)

        val results = mutableListOf<CandidateWord>() // Top predicted completions collected in priority order

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

            // Enqueue all existing children by iterating set bits in `node.childMask`
            var mask = node.childMask
            while (mask != 0) {
                val lowestBit = mask and -mask // Isolate least-significant set bit
                val charIndex = Integer.numberOfTrailingZeros(lowestBit)
                val c = ('a' + charIndex)
                val childOffset = getChildOffset(nodeOffset, c)
                if (childOffset != null) {
                    val childMaxFreq = buffer.getLong(childOffset + 1 + 4) // Peek maxSubtreeFreq without full decode
                    queue.add(childOffset to childMaxFreq)
                }
                mask = mask and (mask - 1) // Clear processed lowest bit
            }
        }

        return results
    }

    /**
     * High-speed T9 candidate decoding traversing the binary buffer directly.
     */
    fun predictForDigits(digits: String, limit: Int = 10): List<CandidateWord> {
        if (digits.isEmpty() || limit <= 0) return emptyList()
        val cleanDigits = digits.filter { it in '2'..'9' } // Keep only T9 letter-bearing digits
        if (cleanDigits.isEmpty()) return emptyList()

        val exactMatches = mutableListOf<BinaryNode>() // Terminal nodes whose path matches `cleanDigits`
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

    /** Recursively follows only valid T9 letter edges for `digits[digitIndex]` in [buffer]. */
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
        /** 4-byte ASCII magic number `"CTRI"` identifying valid Cover Trie binary files. */
        const val MAGIC = 0x43545249 // "CTRI"
        /** Binary format version expected by [validateHeader]. */
        const val VERSION = 1
        /** Fixed byte size of the file header before the first node record. */
        const val HEADER_SIZE = 24
        /** Node flag bit indicating the node stores a terminal word and frequency. */
        private const val FLAG_TERMINAL = 0x01

        /** Standard phone keypad mapping from digits `'2'..'9'` to lowercase letters. */
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
            // First pass: collect nodes to assign deterministic byte offsets before writing pointers
            class NodeInfo(
                val trieNode: CompactTrieDictionary.TrieNode,
                var offset: Int = 0,
                var serializedBytes: ByteArray = ByteArray(0)
            )

            val allNodes = mutableListOf<NodeInfo>() // Flattened list of every reachable trie node
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
            // Pass 1: compute byte offset and record size for every node so child offsets are known
            var currentOffset = HEADER_SIZE
            val nodeOffsetMap = HashMap<CompactTrieDictionary.TrieNode, Int>() // Maps in-memory node -> file byte offset

            // Compute each node's byte size and advance currentOffset
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

            val totalSize = currentOffset // Exact byte size required for header + all node records
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
