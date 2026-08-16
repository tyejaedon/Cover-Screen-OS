package com.tyejaedon.coverscreenos.service

import com.tyejaedon.coverscreenos.services.ForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundServiceTest {

	@Test
	fun `service actions are unique and stable`() {
		assertEquals("com.tyejaedon.coverscreenos.action.START", ForegroundService.ACTION_START)
		assertEquals("com.tyejaedon.coverscreenos.action.STOP", ForegroundService.ACTION_STOP)
		assertEquals("com.tyejaedon.coverscreenos.action.LAUNCH_APP", ForegroundService.ACTION_LAUNCH_APP)
		assertNotEquals(ForegroundService.ACTION_START, ForegroundService.ACTION_STOP)
		assertNotEquals(ForegroundService.ACTION_START, ForegroundService.ACTION_LAUNCH_APP)
		assertNotEquals(ForegroundService.ACTION_STOP, ForegroundService.ACTION_LAUNCH_APP)
	}

	@Test
	fun `notification constants are valid`() {
		assertEquals("foreground_service_channel", ForegroundService.CHANNEL_ID)
		assertTrue(true)
	}
}

