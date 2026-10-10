package com.tyejaedon.coverscreenos.ui.theme

import com.tyejaedon.coverscreenos.datastore.AccentColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppearanceThemeTest {
    @Test
    fun `default palette stays identical in both modes`() {
        assertEquals(CoverOSPrimary, coverAccentScheme(AccentColor.DEFAULT, true).primary)
        assertEquals(CoverOSLightPrimary, coverAccentScheme(AccentColor.DEFAULT, false).primary)
    }

    @Test
    fun `swatches change the primary palette in both modes`() {
        for (dark in listOf(true, false)) {
            val original = coverAccentScheme(AccentColor.DEFAULT, dark)
            for (accent in AccentColor.entries.filterNot { it == AccentColor.DEFAULT }) {
                val changed = coverAccentScheme(accent, dark)
                assertNotEquals(original.primary, changed.primary)
                assertEquals(original.surface, changed.surface)
            }
        }
    }
}
