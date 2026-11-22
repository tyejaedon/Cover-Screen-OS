package com.tyejaedon.coverscreenos.keyboard.nlp

import androidx.compose.ui.geometry.Offset
import com.tyejaedon.coverscreenos.keyboard.core.contracts.CandidateWord
import java.util.PriorityQueue
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Glide (swipe typing) path decoder utilizing 32-point equidistant resampling,
 * start/end key pruning, and Euclidean Dynamic Time Warping (DTW) path alignment.
 */
class DTWGlideDecoder(
    /** Trie dictionary queried for candidate words matching start/end glide keys. */
    private val dictionary: CompactTrieDictionary,
    /** Normalized `(x, y)` center position for every letter key (`'a'..'z'`). */
    private val keyCentroids: Map<Char, Offset> = QwertyGeometry.getDefaultCentroidMap(),
    /** Minimum spatial radius around the swipe start/end used when picking candidate boundary letters. */
    private val candidateSearchRadius: Float = 1.5f
) {

    companion object {
        /** Fixed number of evenly spaced sample points used to compare swipe trails against word templates. */
        const val RESAMPLED_POINT_COUNT = 32
        /** Large sentinel cost initializing unreachable cells in the DTW alignment matrix. */
        private const val INFINITY_COST = 1e9f

        /**
         * Resamples any arbitrary list of continuous gesture points to [targetCount]
         * equidistant points along the polyline path.
         */
        fun resample(points: List<Offset>, targetCount: Int = RESAMPLED_POINT_COUNT): List<Offset> {
            if (points.isEmpty()) return emptyList()
            if (points.size == 1 || targetCount <= 1) {
                return List(targetCount) { points[0] }
            }

            // Step 1: Compute cumulative arc lengths along each segment of the polyline
            val distances = FloatArray(points.size) // distances[i] = total path length from points[0] to points[i]
            distances[0] = 0.0f
            for (i in 1 until points.size) {
                val p1 = points[i - 1]
                val p2 = points[i]
                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                distances[i] = distances[i - 1] + sqrt(dx * dx + dy * dy)
            }

            val totalLength = distances[distances.size - 1] // Overall length of the finger swipe path
            if (totalLength <= 1e-4f) {
                return List(targetCount) { points[0] }
            }

            val step = totalLength / (targetCount - 1) // Constant arc-length spacing between resampled points
            val resampled = ArrayList<Offset>(targetCount)
            resampled.add(points[0])

            var currentSegment = 0 // Index of the input polyline segment currently being interpolated
            for (i in 1 until targetCount - 1) {
                val targetDistance = i * step
                while (currentSegment < distances.size - 2 && distances[currentSegment + 1] < targetDistance) {
                    currentSegment++
                }

                val segStartDist = distances[currentSegment]
                val segEndDist = distances[currentSegment + 1]
                val segLen = segEndDist - segStartDist

                val t = if (segLen > 0.0f) (targetDistance - segStartDist) / segLen else 0.0f // Interpolation fraction [0..1]
                val pStart = points[currentSegment]
                val pEnd = points[currentSegment + 1]

                resampled.add(
                    Offset(
                        x = pStart.x + t * (pEnd.x - pStart.x),
                        y = pStart.y + t * (pEnd.y - pStart.y)
                    )
                )
            }

            resampled.add(points[points.size - 1])
            return resampled
        }
    }

    // Pre-allocated scratch matrix (33 x 33) storing cumulative alignment costs dtwMatrix[i][j]
    // between input point i and template point j to avoid heap allocations during swipe decoding.
    private val dtwMatrix = Array(RESAMPLED_POINT_COUNT + 1) { FloatArray(33) }

    /**
     * Decodes a raw swipe path into ranked word candidates.
     *
     * @param rawPoints Sequence of touch offsets captured during the glide gesture.
     * @param limit Maximum candidate words to return.
     * @return Ranked list of [CandidateWord] candidates.
     */
    fun decodeGlidePath(rawPoints: List<Offset>, limit: Int = 5): List<CandidateWord> {
        if (rawPoints.size < 2 || limit <= 0) return emptyList()

        val sampled = resample(rawPoints, RESAMPLED_POINT_COUNT) // Normalized 32-point user swipe curve
        val startPoint = sampled.first()
        val endPoint = sampled.last()

        val startChars = findCandidateKeys(startPoint) // Keys closest to where the finger landed
        val endChars = findCandidateKeys(endPoint) // Keys closest to where the finger lifted

        if (startChars.isEmpty() || endChars.isEmpty()) return emptyList()

        // Gather candidate words matching start and end letters from the dictionary
        val candidateWords = mutableListOf<CandidateWordWithFreq>()

        for (startChar in startChars) {
            val startNode = dictionary.findNode(startChar.toString()) ?: continue
            collectWordsEndingWith(startNode, endChars, candidateWords)
        }

        if (candidateWords.isEmpty()) return emptyList()

        // Score each candidate word using DTW
        val scoredList = mutableListOf<CandidateWord>()

        for (cand in candidateWords) {
            val word = cand.word
            val keyCentroidsList = buildWordCentroidTemplate(word) // Ideal key-to-key polyline for `word`
            if (keyCentroidsList.isEmpty()) continue

            val templatePoints = resample(keyCentroidsList, RESAMPLED_POINT_COUNT) // Resampled 32-point ideal curve
            val dtwDist = computeDtwDistance(sampled, templatePoints) // Total cumulative spatial alignment error
            val normalizedDist = dtwDist / RESAMPLED_POINT_COUNT // Mean per-point distance error

            val freqFactor = 1.0f + 0.15f * ln(max(1.0, cand.frequency.toDouble())).toFloat() // Log boost for common words
            val score = (100.0f / (1.0f + normalizedDist * 2.0f)) * freqFactor

            scoredList.add(CandidateWord(word = word, score = score, isAutocorrectDefault = false))
        }

        scoredList.sortByDescending { it.score }

        val topResults = scoredList.take(limit)
        if (topResults.isNotEmpty()) {
            // Mark top candidate as autocorrect default if it has high relative score
            val first = topResults.first()
            return topResults.mapIndexed { idx, item ->
                if (idx == 0 && item.score >= 20.0f) {
                    item.copy(isAutocorrectDefault = true)
                } else {
                    item
                }
            }
        }

        return topResults
    }

    /** Returns up to 4 letter keys nearest to [point] within [candidateSearchRadius] or 1.5x closest distance. */
    private fun findCandidateKeys(point: Offset): List<Char> {
        val keysByDistance = ArrayList<Pair<Char, Float>>(keyCentroids.size) // (letter, Euclidean distance) pairs
        for ((char, centroid) in keyCentroids) {
            val dx = point.x - centroid.x
            val dy = point.y - centroid.y
            val dist = sqrt(dx * dx + dy * dy)
            keysByDistance.add(char to dist)
        }

        keysByDistance.sortBy { it.second }
        if (keysByDistance.isEmpty()) return emptyList()

        val closestDist = keysByDistance.first().second
        val threshold = max(candidateSearchRadius, closestDist * 1.5f) // Adaptive inclusion radius

        return keysByDistance
            .filter { it.second <= threshold }
            .take(4)
            .map { it.first }
    }

    /** Collects dictionary words in [node]'s subtree whose final character appears in [endChars]. */
    private fun collectWordsEndingWith(
        node: CompactTrieDictionary.TrieNode,
        endChars: List<Char>,
        output: MutableList<CandidateWordWithFreq>
    ) {
        if (node.isTerminal && node.terminalWord != null) {
            val lastChar = node.terminalWord!!.lastOrNull()?.lowercaseChar()
            if (lastChar != null && lastChar in endChars) {
                output.add(CandidateWordWithFreq(node.terminalWord!!, node.frequency))
            }
        }

        node.forEachChild { _, child ->
            collectWordsEndingWith(child, endChars, output)
        }
    }

    /** Builds the sequence of key centroid points for [word], collapsing repeated consecutive letters. */
    private fun buildWordCentroidTemplate(word: String): List<Offset> {
        val template = ArrayList<Offset>(word.length)
        var lastChar: Char? = null
        for (i in 0 until word.length) {
            val c = word[i].lowercaseChar()
            // Collapse duplicate consecutive characters (e.g. "ll" -> one anchor point)
            if (c == lastChar) continue
            lastChar = c
            val centroid = keyCentroids[c] ?: continue
            template.add(centroid)
        }
        return template
    }

    /**
     * Computes Dynamic Time Warping distance between [inputPoints] and [templatePoints]
     * inside [dtwMatrix], allowing non-linear time stretching along the swipe path.
     */
    private fun computeDtwDistance(inputPoints: List<Offset>, templatePoints: List<Offset>): Float {
        val n = inputPoints.size // Number of resampled user gesture points (typically 32)
        val m = min(templatePoints.size, 32) // Number of template points clamped to scratch matrix width

        for (i in 0..n) {
            for (j in 0..m) {
                dtwMatrix[i][j] = INFINITY_COST
            }
        }
        dtwMatrix[0][0] = 0.0f

        for (i in 1..n) {
            val pInput = inputPoints[i - 1]
            for (j in 1..m) {
                val pTemplate = templatePoints[j - 1]
                val dx = pInput.x - pTemplate.x
                val dy = pInput.y - pTemplate.y
                val cost = sqrt(dx * dx + dy * dy) // Euclidean distance between current sample pair

                val minPrev = min(
                    dtwMatrix[i - 1][j], // insertion (finger lingered/moved extra point)
                    min(
                        dtwMatrix[i][j - 1], // deletion (finger jumped past template point)
                        dtwMatrix[i - 1][j - 1] // match (diagonal advance along both paths)
                    )
                )

                dtwMatrix[i][j] = cost + minPrev
            }
        }

        return dtwMatrix[n][m]
    }

    private data class CandidateWordWithFreq(
        val word: String,
        val frequency: Long
    )
}
