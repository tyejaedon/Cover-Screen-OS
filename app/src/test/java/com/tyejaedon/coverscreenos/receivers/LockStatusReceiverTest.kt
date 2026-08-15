package com.tyejaedon.coverscreenos.receivers

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class LockStatusReceiverTest {

    @Test
    fun `screen off notifies locked`() {
        val statuses = mutableListOf<Boolean>()
        val receiver = LockStatusReceiver { statuses += it }
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_SCREEN_OFF

        receiver.onReceive(mockk(relaxed = true), intent)

        assertEquals(listOf(true), statuses)
    }

    @Test
    fun `user present notifies unlocked`() {
        val statuses = mutableListOf<Boolean>()
        val receiver = LockStatusReceiver { statuses += it }
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_USER_PRESENT

        receiver.onReceive(mockk(relaxed = true), intent)

        assertEquals(listOf(false), statuses)
    }

    @Test
    fun `screen on reflects keyguard lock state`() {
        val statuses = mutableListOf<Boolean>()
        val keyguardManager = mockk<KeyguardManager>()
        every { keyguardManager.isDeviceLocked } returns true

        val context = mockk<Context>()
        every { context.getSystemService(Context.KEYGUARD_SERVICE) } returns keyguardManager
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_SCREEN_ON

        val receiver = LockStatusReceiver { statuses += it }
        receiver.onReceive(context, intent)

        assertEquals(listOf(true), statuses)
    }
}

