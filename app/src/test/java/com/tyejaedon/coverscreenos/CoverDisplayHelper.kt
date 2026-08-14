package com.tyejaedon.coverscreenos


import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.tyejaedon.coverscreenos.helpers.CoverDisplayHelper
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class CoverDisplayHelperTest {

    private lateinit var mockContext: Context
    private lateinit var mockDisplayManager: DisplayManager
    private lateinit var helper: CoverDisplayHelper

    @Before
    fun setup() {
        mockContext = mockk()
        mockDisplayManager = mockk()

        every {
            mockContext.getSystemService(Context.DISPLAY_SERVICE)
        } returns mockDisplayManager

        helper = CoverDisplayHelper(mockContext)
    }

    @Test
    fun `getCoverDisplay returns the specific presentation cover display object`() {
        val mockCoverDisplay = createDisplay(id = 2)

        every {
            mockDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        } returns arrayOf(mockCoverDisplay)

        val result = helper.getCoverDisplay()

        assertSame(mockCoverDisplay, result)
        assertEquals(2, result?.displayId)
    }

    @Test
    fun `getCoverDisplay skips sleeping secondary display and returns active one`() {
        val sleepingCoverDisplay = createDisplay(id = 2, state = Display.STATE_OFF)
        val activeCoverDisplay = createDisplay(id = 3, state = Display.STATE_ON)

        every {
            mockDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        } returns arrayOf(sleepingCoverDisplay)

        every {
            mockDisplayManager.displays
        } returns arrayOf(createDisplay(id = Display.DEFAULT_DISPLAY), sleepingCoverDisplay, activeCoverDisplay)

        val result = helper.getCoverDisplay()

        assertSame(activeCoverDisplay, result)
        assertEquals(3, result?.displayId)
    }

    @Test
    fun `getCoverDisplay returns null when secondary display is unavailable`() {
        val unavailableCoverDisplay = createDisplay(id = 4, isValid = false)

        every {
            mockDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        } returns arrayOf(unavailableCoverDisplay)

        every {
            mockDisplayManager.displays
        } returns arrayOf(createDisplay(id = Display.DEFAULT_DISPLAY), unavailableCoverDisplay)

        val result = helper.getCoverDisplay()

        assertNull(result)
    }

    @Test
    fun `getCoverDisplay falls back to non-default display if presentation is empty`() {
        val mockMainDisplay = createDisplay(id = Display.DEFAULT_DISPLAY)
        val mockCoverDisplay = createDisplay(id = 3)

        every {
            mockDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        } returns emptyArray()

        every {
            mockDisplayManager.displays
        } returns arrayOf(mockMainDisplay, mockCoverDisplay)

        val result = helper.getCoverDisplay()

        assertEquals(3, result?.displayId)
    }

    @Test
    fun `getCoverDisplay returns null if only the main internal screen exists`() {
        val mockMainDisplay = createDisplay(id = Display.DEFAULT_DISPLAY)

        every {
            mockDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        } returns emptyArray()

        every {
            mockDisplayManager.displays
        } returns arrayOf(mockMainDisplay)

        val result = helper.getCoverDisplay()

        assertNull(result)
    }

    @Test
    fun `getCoverDisplayId returns id of active cover display`() {
        val coverDisplay = createDisplay(id = 7)

        every {
            mockDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        } returns arrayOf(coverDisplay)

        val result = helper.getCoverDisplayId()

        assertEquals(7, result)
    }

    @Test
    fun `getCoverDisplayId returns null when only unavailable secondary exists`() {
        val unavailableCoverDisplay = createDisplay(id = 8, isValid = false)

        every {
            mockDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        } returns arrayOf(unavailableCoverDisplay)

        every {
            mockDisplayManager.displays
        } returns arrayOf(createDisplay(id = Display.DEFAULT_DISPLAY), unavailableCoverDisplay)

        val result = helper.getCoverDisplayId()

        assertNull(result)
    }

    @Test
    fun `getCoverDisplayId skips sleeping presentation and returns fallback secondary id`() {
        val sleepingPresentationDisplay = createDisplay(id = 9, state = Display.STATE_OFF)
        val activeSecondaryDisplay = createDisplay(id = 10, state = Display.STATE_ON)

        every {
            mockDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        } returns arrayOf(sleepingPresentationDisplay)

        every {
            mockDisplayManager.displays
        } returns arrayOf(
            createDisplay(id = Display.DEFAULT_DISPLAY),
            sleepingPresentationDisplay,
            activeSecondaryDisplay
        )

        val result = helper.getCoverDisplayId()

        assertEquals(10, result)
    }

    private fun createDisplay(
        id: Int,
        isValid: Boolean = true,
        state: Int = Display.STATE_ON
    ): Display {
        val display = mockk<Display>()
        every { display.displayId } returns id
        every { display.isValid } returns isValid
        every { display.state } returns state
        return display
    }
}