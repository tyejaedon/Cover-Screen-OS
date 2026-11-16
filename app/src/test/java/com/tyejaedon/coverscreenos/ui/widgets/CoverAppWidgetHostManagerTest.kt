package com.tyejaedon.coverscreenos.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class CoverAppWidgetHostManagerTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `resolveWidgetHeightDp clamps to min and max bounds`() {
        assertEquals(160, CoverAppWidgetHostManager.resolveWidgetHeightDp(null, 2f))

        val tiny = AppWidgetProviderInfo().apply { minHeight = 40 }
        assertEquals(
            CoverAppWidgetHostManager.MIN_WIDGET_HEIGHT_DP,
            CoverAppWidgetHostManager.resolveWidgetHeightDp(tiny, 2f)
        )

        val huge = AppWidgetProviderInfo().apply { minHeight = 1600 }
        assertEquals(
            CoverAppWidgetHostManager.MAX_WIDGET_HEIGHT_DP,
            CoverAppWidgetHostManager.resolveWidgetHeightDp(huge, 2f)
        )
    }

    @Test
    fun `startListening and stopListening are idempotent`() {
        val host = mockk<AppWidgetHost>(relaxed = true)
        val manager = mockk<AppWidgetManager>(relaxed = true)
        val sut = CoverAppWidgetHostManager(context, appWidgetHost = host, appWidgetManager = manager)

        sut.startListening()
        sut.startListening()
        assertTrue(sut.isHostListening())
        verify(exactly = 1) { host.startListening() }

        sut.stopListening()
        sut.stopListening()
        assertFalse(sut.isHostListening())
        verify(exactly = 1) { host.stopListening() }
    }

    @Test
    fun `createBindIntent populates widget id and provider`() {
        val host = mockk<AppWidgetHost>(relaxed = true)
        val manager = mockk<AppWidgetManager>(relaxed = true)
        val sut = CoverAppWidgetHostManager(context, appWidgetHost = host, appWidgetManager = manager)
        val provider = ComponentName("com.example.pkg", "com.example.pkg.WidgetProvider")

        val intent = sut.createBindIntent(appWidgetId = 42, provider = provider)

        assertEquals(AppWidgetManager.ACTION_APPWIDGET_BIND, intent.action)
        assertEquals(42, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        val extraProvider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)
        }
        assertEquals(provider, extraProvider)
    }

    @Test
    fun `deleteAppWidgetId and bindAppWidgetIdIfAllowed delegate to host and manager`() {
        val host = mockk<AppWidgetHost>(relaxed = true)
        val manager = mockk<AppWidgetManager>(relaxed = true)
        val provider = ComponentName("com.example.pkg", "com.example.pkg.WidgetProvider")
        every { manager.bindAppWidgetIdIfAllowed(77, provider) } returns true
        every { host.deleteAppWidgetId(77) } just runs

        val sut = CoverAppWidgetHostManager(context, appWidgetHost = host, appWidgetManager = manager)

        assertTrue(sut.bindAppWidgetIdIfAllowed(77, provider))
        sut.deleteAppWidgetId(77)

        verify(exactly = 1) { manager.bindAppWidgetIdIfAllowed(77, provider) }
        verify(exactly = 1) { host.deleteAppWidgetId(77) }
    }
}
