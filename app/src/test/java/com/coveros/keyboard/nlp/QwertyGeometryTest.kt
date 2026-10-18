package com.coveros.keyboard.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QwertyGeometryTest {

    @Test
    fun testKeyDistanceSelfIsZero() {
        assertEquals(0.0f, QwertyGeometry.keyDistance('e', 'e'), 0.001f)
        assertEquals(0.0f, QwertyGeometry.keyDistance('A', 'a'), 0.001f)
    }

    @Test
    fun testAdjacentKeysDistance() {
        // 'w' and 'e' are horizontal neighbors on row 0
        val distWE = QwertyGeometry.keyDistance('w', 'e')
        assertEquals(1.0f, distWE, 0.01f)
        assertTrue(QwertyGeometry.isAdjacent('w', 'e'))

        // 'e' and 'd' are diagonal neighbors
        val distED = QwertyGeometry.keyDistance('e', 'd')
        assertTrue(distED < 1.45f)
        assertTrue(QwertyGeometry.isAdjacent('e', 'd'))

        // 'q' and 'p' are on opposite ends of row 0
        val distQP = QwertyGeometry.keyDistance('q', 'p')
        assertEquals(9.0f, distQP, 0.01f)
        assertFalse(QwertyGeometry.isAdjacent('q', 'p'))
    }

    @Test
    fun testWeightedEditDistanceAdjacentSlipVsDistant() {
        // "thw" where 'w' is adjacent to 'e'
        val distAdjacent = QwertyGeometry.weightedEditDistance("thw", "the")

        // "thm" where 'm' is far away from 'e'
        val distFar = QwertyGeometry.weightedEditDistance("thm", "the")

        assertTrue("Adjacent typo should have lower weighted distance than far typo", distAdjacent < distFar)
    }

    @Test
    fun testWeightedEditDistanceTransposition() {
        // "teh" -> "the" transposition cost is ~0.65
        val distTransposition = QwertyGeometry.weightedEditDistance("teh", "the")
        assertTrue(distTransposition < 0.70f)
        assertTrue(distTransposition > 0.0f)
    }
}
