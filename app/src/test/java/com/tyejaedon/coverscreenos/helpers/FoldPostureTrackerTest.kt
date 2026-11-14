package com.tyejaedon.coverscreenos.helpers

import android.content.Context
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldPostureTrackerTest {

    private val context: Context = mockk(relaxed = true)

    @Test
    fun `default posture is CLOSED`() {
        val tracker = FoldPostureTracker(context)
        assertEquals(CoverFoldPosture.CLOSED, tracker.posture.value)
    }

    @Test
    fun `computePosture returns CLOSED when no folding features are present`() {
        val tracker = FoldPostureTracker(context)
        val layoutInfo = WindowLayoutInfo(emptyList())

        val posture = tracker.computePosture(layoutInfo)
        assertEquals(CoverFoldPosture.CLOSED, posture)
    }

    @Test
    fun `computePosture returns HALF_FOLDED when folding feature is HALF_OPENED`() {
        val tracker = FoldPostureTracker(context)
        val foldingFeature = mockk<FoldingFeature>()
        every { foldingFeature.state } returns FoldingFeature.State.HALF_OPENED

        val layoutInfo = WindowLayoutInfo(listOf(foldingFeature))
        val posture = tracker.computePosture(layoutInfo)

        assertEquals(CoverFoldPosture.HALF_FOLDED, posture)
    }

    @Test
    fun `computePosture returns FLAT when folding feature is FLAT`() {
        val tracker = FoldPostureTracker(context)
        val foldingFeature = mockk<FoldingFeature>()
        every { foldingFeature.state } returns FoldingFeature.State.FLAT

        val layoutInfo = WindowLayoutInfo(listOf(foldingFeature))
        val posture = tracker.computePosture(layoutInfo)

        assertEquals(CoverFoldPosture.FLAT, posture)
    }

    @Test
    fun `coverDisplayHelper posture permission check respects posture states`() {
        val helper = CoverDisplayHelper(context)

        helper.setPostureForTest(CoverFoldPosture.CLOSED)
        assertTrue(helper.isPosturePermittedForCover())

        helper.setPostureForTest(CoverFoldPosture.HALF_FOLDED)
        assertFalse(helper.isPosturePermittedForCover())

        helper.setPostureForTest(CoverFoldPosture.FLAT)
        assertFalse(helper.isPosturePermittedForCover())
    }
}
