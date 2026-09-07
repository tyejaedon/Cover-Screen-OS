package com.tyejaedon.coverscreenos.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class InputTelemetryStoreTest {

    @Test
    fun `success rate is zero when there are no requests`() {
        assertEquals(0, imePickerSuccessRatePercent(requestCount = 0L, successCount = 0L))
        assertEquals(0, imePickerSuccessRatePercent(requestCount = 0L, successCount = 5L))
    }

    @Test
    fun `success rate is rounded and bounded`() {
        assertEquals(70, imePickerSuccessRatePercent(requestCount = 10L, successCount = 7L))
        assertEquals(67, imePickerSuccessRatePercent(requestCount = 3L, successCount = 2L))
    }

    @Test
    fun `success rate clamps to valid range when persisted data is inconsistent`() {
        assertEquals(0, imePickerSuccessRatePercent(requestCount = 10L, successCount = -2L))
        assertEquals(100, imePickerSuccessRatePercent(requestCount = 10L, successCount = 999L))
    }
}

