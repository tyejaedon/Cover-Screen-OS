package com.tyejaedon.coverscreenos.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoverAppWidgetHostManagerTest {

    private val context: Context = mockk(relaxed = true)
    private val appWidgetHost: AppWidgetHost = mockk(relaxed = true)
    private val appWidgetManager: AppWidgetManager = mockk(relaxed = true)

    @Test
    fun `startListening starts host only when not already listening`() {
        val manager = CoverAppWidgetHostManager(
            context = context,
            appWidgetHost = appWidgetHost,
            appWidgetManager = appWidgetManager
        )

        assertFalse(manager.isHostListening())

        manager.startListening()
        assertTrue(manager.isHostListening())
        verify(exactly = 1) { appWidgetHost.startListening() }

        // Second call should be a no-op
        manager.startListening()
        verify(exactly = 1) { appWidgetHost.startListening() }
    }

    @Test
    fun `stopListening stops host to eliminate background battery drain`() {
        val manager = CoverAppWidgetHostManager(
            context = context,
            appWidgetHost = appWidgetHost,
            appWidgetManager = appWidgetManager
        )

        manager.startListening()
        assertTrue(manager.isHostListening())

        manager.stopListening()
        assertFalse(manager.isHostListening())
        verify(exactly = 1) { appWidgetHost.stopListening() }

        // Redundant stop call is a no-op
        manager.stopListening()
        verify(exactly = 1) { appWidgetHost.stopListening() }
    }

    @Test
    fun `allocateAppWidgetId delegates to host`() {
        every { appWidgetHost.allocateAppWidgetId() } returns 101

        val manager = CoverAppWidgetHostManager(
            context = context,
            appWidgetHost = appWidgetHost,
            appWidgetManager = appWidgetManager
        )

        assertEquals(101, manager.allocateAppWidgetId())
        verify(exactly = 1) { appWidgetHost.allocateAppWidgetId() }
    }

    @Test
    fun `deleteAppWidgetId delegates to host`() {
        val manager = CoverAppWidgetHostManager(
            context = context,
            appWidgetHost = appWidgetHost,
            appWidgetManager = appWidgetManager
        )

        manager.deleteAppWidgetId(101)
        verify(exactly = 1) { appWidgetHost.deleteAppWidgetId(101) }
    }

    @Test
    fun `createWidgetView returns view when provider info exists`() {
        val widgetInfo = mockk<AppWidgetProviderInfo>()
        val mockView = mockk<android.appwidget.AppWidgetHostView>()
        every { appWidgetManager.getAppWidgetInfo(101) } returns widgetInfo
        every { appWidgetHost.createView(any(), 101, widgetInfo) } returns mockView

        val manager = CoverAppWidgetHostManager(
            context = context,
            appWidgetHost = appWidgetHost,
            appWidgetManager = appWidgetManager
        )

        val result = manager.createWidgetView(context, 101)
        assertNotNull(result)
        assertEquals(mockView, result)
    }

    @Test
    fun `createWidgetView returns null when provider info is absent`() {
        every { appWidgetManager.getAppWidgetInfo(999) } returns null

        val manager = CoverAppWidgetHostManager(
            context = context,
            appWidgetHost = appWidgetHost,
            appWidgetManager = appWidgetManager
        )

        val result = manager.createWidgetView(context, 999)
        assertNull(result)
    }

    @Test
    fun `getInstalledProviders returns providers from AppWidgetManager`() {
        val info = mockk<AppWidgetProviderInfo>()
        every { appWidgetManager.installedProviders } returns listOf(info)

        val manager = CoverAppWidgetHostManager(
            context = context,
            appWidgetHost = appWidgetHost,
            appWidgetManager = appWidgetManager
        )

        assertEquals(listOf(info), manager.getInstalledProviders())
    }

    @Test
    fun `bindAppWidgetIdIfAllowed delegates to AppWidgetManager`() {
        val component = android.content.ComponentName("com.example", "com.example.WidgetProvider")
        val profile = android.os.Process.myUserHandle()
        val info = mockk<AppWidgetProviderInfo>(relaxed = true) {
            provider = component
            every { this@mockk.profile } returns profile
        }
        every {
            appWidgetManager.bindAppWidgetIdIfAllowed(101, profile, component, null)
        } returns true

        val manager = CoverAppWidgetHostManager(
            context = context,
            appWidgetHost = appWidgetHost,
            appWidgetManager = appWidgetManager
        )

        assertTrue(manager.bindAppWidgetIdIfAllowed(101, info))
    }

    @Test
    fun `createBindIntent populates widget id and provider extras`() {
        val component = android.content.ComponentName("com.example", "com.example.WidgetProvider")
        val profile = android.os.Process.myUserHandle()
        val info = mockk<AppWidgetProviderInfo>(relaxed = true) {
            provider = component
            every { this@mockk.profile } returns profile
        }
        val manager = CoverAppWidgetHostManager(
            context = context,
            appWidgetHost = appWidgetHost,
            appWidgetManager = appWidgetManager
        )

        val intent = manager.createBindIntent(202, info)
        assertEquals(AppWidgetManager.ACTION_APPWIDGET_BIND, intent.action)
        assertEquals(202, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        @Suppress("DEPRECATION")
        assertEquals(
            component,
            intent.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)
        )
    }
}
