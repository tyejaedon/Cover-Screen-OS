package com.tyejaedon.coverscreenos.ui.keyboard

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyRowsHeightTest {

    @Test
    fun `key rows height covers four rows plus three gaps`() {
        assertEquals(188.dp, keyRowsHeight(44.dp))
        assertEquals(268.dp, keyRowsHeight(64.dp))
    }
}
