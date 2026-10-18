package com.coveros.keyboard.nlp

import androidx.compose.ui.geometry.Offset
import java.util.Locale
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Spatial geometry and key adjacency calculations for a standard QWERTY keyboard layout.
 * Provides normalized physical coordinates and keyboard-distance-weighted edit distance.
 */
object QwertyGeometry {

    // Normalized (x, y) coordinates for each QWERTY letter, assuming key width/height = 1.0.
    private val KEY_COORDINATES: Map<Char, Offset> = mapOf(
        'q' to Offset(0.0f, 0.0f),
        'w' to Offset(1.0f, 0.0f),
        'e' to Offset(2.0f, 0.0f),
        'r' to Offset(3.0f, 0.0f),
        't' to Offset(4.0f, 0.0f),
        'y' to Offset(5.0f, 0.0f),
        'u' to Offset(6.0f, 0.0f),
        'i' to Offset(7.0f, 0.0f),
        'o' to Offset(8.0f, 0.0f),
        'p' to Offset(9.0f, 0.0f),

        'a' to Offset(0.5f, 1.0f),
        's' to Offset(1.5f, 1.0f),
        'd' to Offset(2.5f, 1.0f),
        'f' to Offset(3.5f, 1.0f),
        'g' to Offset(4.5f, 1.0f),
        'h' to Offset(5.5f, 1.0f),
        'j' to Offset(6.5f, 1.0f),
        'k' to Offset(7.5f, 1.0f),
        'l' to Offset(8.5f, 1.0f),

        'z' to Offset(1.5f, 2.0f),
        'x' to Offset(2.5f, 2.0f),
        'c' to Offset(3.5f, 2.0f),
        'v' to Offset(4.5f, 2.0f),
        'b' to Offset(5.5f, 2.0f),
        'n' to Offset(6.5f, 2.0f),
        'm' to Offset(7.5f, 2.0f)
    )

    /**
     * Default centroid map for all lowercase alphabetic characters in normalized coordinates.
     */
    fun getDefaultCentroidMap(): Map<Char, Offset> = KEY_COORDINATES

    /**
     * Look up the normalized centroid for a given character.
     */
    fun getCentroid(char: Char): Offset? = KEY_COORDINATES[char.lowercaseChar()]

    /**
     * Euclidean distance between two keys on the QWERTY layout.
     * Returns 0.0 for identical characters, ~1.0 for directly adjacent horizontal keys,
     * and a penalty (3.5) for non-alphabetic or unknown characters.
     */
    fun keyDistance(c1: Char, c2: Char): Float {
        val ch1 = c1.lowercaseChar()
        val ch2 = c2.lowercaseChar()
        if (ch1 == ch2) return 0.0f
        val p1 = KEY_COORDINATES[ch1] ?: return 3.5f
        val p2 = KEY_COORDINATES[ch2] ?: return 3.5f
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Returns true if two keys are adjacent on the keyboard layout (distance <= 1.45 key units).
     */
    fun isAdjacent(c1: Char, c2: Char, threshold: Float = 1.45f): Boolean {
        return keyDistance(c1, c2) <= threshold
    }

    /**
     * Computes a modified Damerau-Levenshtein edit distance weighted by physical keyboard
     * proximity on substitutions. Adjacent key slips incur a significantly lower penalty
     * than distant substitutions.
     */
    fun weightedEditDistance(s1: String, s2: String): Float {
        val a = s1.lowercase(Locale.ROOT)
        val b = s2.lowercase(Locale.ROOT)
        if (a == b) return 0.0f
        if (a.isEmpty()) return b.length.toFloat()
        if (b.isEmpty()) return a.length.toFloat()

        val lenA = a.length
        val lenB = b.length
        // DP table with border rows
        val dp = Array(lenA + 1) { FloatArray(lenB + 1) }

        for (i in 0..lenA) dp[i][0] = i.toFloat()
        for (j in 0..lenB) dp[0][j] = j.toFloat()

        for (i in 1..lenA) {
            val charA = a[i - 1]
            for (j in 1..lenB) {
                val charB = b[j - 1]

                val substitutionCost = if (charA == charB) {
                    0.0f
                } else {
                    val dist = keyDistance(charA, charB)
                    // Scale substitution cost: adjacent key slip ~0.55 - 0.75, far key ~1.2 - 2.0
                    0.5f + min(1.5f, dist * 0.25f)
                }

                // Standard operations: deletion, insertion, substitution
                var minCost = min(
                    dp[i - 1][j] + 1.0f, // deletion
                    min(
                        dp[i][j - 1] + 1.0f, // insertion
                        dp[i - 1][j - 1] + substitutionCost // substitution
                    )
                )

                // Transposition check (Damerau condition)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    val transpositionCost = dp[i - 2][j - 2] + 0.65f
                    if (transpositionCost < minCost) {
                        minCost = transpositionCost
                    }
                }

                dp[i][j] = minCost
            }
        }

        return dp[lenA][lenB]
    }
}
