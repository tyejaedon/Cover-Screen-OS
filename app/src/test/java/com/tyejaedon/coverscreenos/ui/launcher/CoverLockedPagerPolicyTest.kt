package com.tyejaedon.coverscreenos.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverLockedPagerPolicyTest {

    @Test
    fun `locked device restricts accessible pages to notifications and lock tile only`() {
        assertEquals(2, resolveAccessiblePageCount(isDeviceLocked = true, totalPageCount = 6))
        assertEquals(2, resolveAccessiblePageCount(isDeviceLocked = true, totalPageCount = 3))
    }

    @Test
    fun `locked device clamps safely when total page count is small`() {
        assertEquals(1, resolveAccessiblePageCount(isDeviceLocked = true, totalPageCount = 0))
        assertEquals(1, resolveAccessiblePageCount(isDeviceLocked = true, totalPageCount = 1))
        assertEquals(2, resolveAccessiblePageCount(isDeviceLocked = true, totalPageCount = 2))
    }

    @Test
    fun `unlocked device restores full total page count including widgets and app grid`() {
        assertEquals(6, resolveAccessiblePageCount(isDeviceLocked = false, totalPageCount = 6))
        assertEquals(3, resolveAccessiblePageCount(isDeviceLocked = false, totalPageCount = 3))
        assertEquals(1, resolveAccessiblePageCount(isDeviceLocked = false, totalPageCount = 0))
    }
}
