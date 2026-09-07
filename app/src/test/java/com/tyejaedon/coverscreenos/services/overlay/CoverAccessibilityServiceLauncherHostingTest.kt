package com.tyejaedon.coverscreenos.services.overlay

import com.tyejaedon.coverscreenos.datastore.KeyboardStrategy
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Phase 2 host-handoff plumbing tests for [CoverAccessibilityService].
 *
 * These tests exercise the static [CoverAccessibilityService.attachLauncherHost] /
 * [CoverAccessibilityService.detachLauncherHost] pair against the four
 * lifecycle transitions that matter in production:
 *
 *  1. Normal path: host attached while the AS instance is live.
 *  2. Late-arrival path: host attached before `onServiceConnected` fires.
 *  3. AS disable/enable: instance unbinds and a fresh instance connects
 *     later — the pending host must survive and be re-installed.
 *  4. App update / service kill: `ForegroundService` releases the host
 *     explicitly, then a new AS instance connects clean with no host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoverAccessibilityServiceLauncherHostingTest {

    private lateinit var controllers: MutableList<ServiceController<CoverAccessibilityService>>

    @Before
    fun setUp() {
        controllers = mutableListOf()
        // Ensure clean static state per test.
        CoverAccessibilityService.detachLauncherHost()
    }

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.destroy() } }
        controllers.clear()
        CoverAccessibilityService.detachLauncherHost()
    }

    // -------------------------------------------------------------------

    @Test
    fun `attach after connect installs host on live AS instance`() {
        val (_, service) = connectService()
        val host = fakeHost()

        CoverAccessibilityService.attachLauncherHost(host)

        assertSame(host, service.launcherHost)
        assertSame(host, CoverAccessibilityService.currentLauncherHost())
    }

    @Test
    fun `attach before connect stashes host until onServiceConnected`() {
        val host = fakeHost()

        CoverAccessibilityService.attachLauncherHost(host)

        // No AS live yet — currentLauncherHost is null but the pending
        // handoff is retained.
        assertNull(CoverAccessibilityService.currentLauncherHost())

        val (_, service) = connectService()

        assertSame(host, service.launcherHost)
        assertSame(host, CoverAccessibilityService.currentLauncherHost())
    }

    @Test
    fun `detachLauncherHost clears live instance and pending handoff`() {
        val (_, service) = connectService()
        val host = fakeHost()
        CoverAccessibilityService.attachLauncherHost(host)
        assertSame(host, service.launcherHost)

        CoverAccessibilityService.detachLauncherHost()

        assertNull(service.launcherHost)
        assertNull(CoverAccessibilityService.currentLauncherHost())

        // Reconnecting a fresh instance must NOT resurrect the detached host.
        val (_, nextService) = connectService()
        assertNull(nextService.launcherHost)
    }

    @Test
    fun `AS disable then enable re-installs the pending host on next connect`() {
        val host = fakeHost()
        val (originalCtrl, original) = connectService()
        CoverAccessibilityService.attachLauncherHost(host)
        assertSame(host, original.launcherHost)

        // Simulate AS disable in system settings: onUnbind + onDestroy.
        original.onUnbind(null)
        originalCtrl.destroy()
        controllers.remove(originalCtrl)

        // Between instances, currentLauncherHost is null but the pending
        // reference is retained by the companion.
        assertNull(CoverAccessibilityService.currentLauncherHost())

        // Simulate AS re-enable: the platform binds a fresh instance and
        // calls onServiceConnected. The pending host must be re-installed
        // WITHOUT any additional ForegroundService activity.
        val (_, revived) = connectService()

        assertSame(host, revived.launcherHost)
        assertSame(host, CoverAccessibilityService.currentLauncherHost())
    }

    @Test
    fun `service kill without detach preserves pending host for the next instance`() {
        val host = fakeHost()
        val (firstCtrl, first) = connectService()
        CoverAccessibilityService.attachLauncherHost(host)
        assertSame(host, first.launcherHost)

        // Simulate abrupt process kill: onDestroy without onUnbind.
        firstCtrl.destroy()
        controllers.remove(firstCtrl)

        // Fresh AS instance boots (post-restart). It must pick up the
        // pending host on connect.
        val (_, second) = connectService()
        assertSame(host, second.launcherHost)
    }

    @Test
    fun `app update cycle - detach then re-attach works with new host instance`() {
        // First install: attach a host and let it settle.
        val hostA = fakeHost()
        val (firstCtrl, first) = connectService()
        CoverAccessibilityService.attachLauncherHost(hostA)
        assertSame(hostA, first.launcherHost)

        // ForegroundService.onDestroy on app upgrade tears down the host.
        CoverAccessibilityService.detachLauncherHost()
        assertNull(first.launcherHost)

        first.onUnbind(null)
        firstCtrl.destroy()
        controllers.remove(firstCtrl)

        // Post-upgrade ForegroundService.onCreate constructs a NEW host and
        // attaches it. A fresh AS instance must expose only the new host.
        val hostB = fakeHost()
        CoverAccessibilityService.attachLauncherHost(hostB)
        val (_, second) = connectService()

        assertSame(hostB, second.launcherHost)
        assertTrue(hostA !== hostB)
        assertNotNull(CoverAccessibilityService.currentLauncherHost())
    }

    @Test
    fun `attaching a new host replaces the previous one in place`() {
        val hostA = fakeHost()
        val hostB = fakeHost()
        val (_, service) = connectService()

        CoverAccessibilityService.attachLauncherHost(hostA)
        assertSame(hostA, service.launcherHost)

        CoverAccessibilityService.attachLauncherHost(hostB)
        assertSame(hostB, service.launcherHost)
        assertSame(hostB, CoverAccessibilityService.currentLauncherHost())
    }

    // -------------------------------------------------------------------

    /**
     * Build a `CoverAccessibilityService` instance and dispatch
     * `onServiceConnected` on it via reflection (the base method is
     * `protected`) — mirroring what the platform does when an
     * accessibility service is bound.
     */
    private fun connectService(): Pair<ServiceController<CoverAccessibilityService>, CoverAccessibilityService> {
        val controller = Robolectric.buildService(CoverAccessibilityService::class.java)
            .create()
        controllers += controller
        val service = controller.get()
        invokeOnServiceConnected(service)
        return controller to service
    }

    private fun invokeOnServiceConnected(service: CoverAccessibilityService) {
        val method = service.javaClass.getDeclaredMethod("onServiceConnected")
        method.isAccessible = true
        method.invoke(service)
    }

    private fun fakeHost(): LauncherOverlayHost {
        val settingsStore = mockk<LauncherSettingsStore>(relaxed = true)
        every { settingsStore.settings } returns kotlinx.coroutines.flow.flowOf(LauncherSettings())
        return LauncherOverlayHost(
            appRepository = mockk<PackageManagerAppScannerRepository>(relaxed = true),
            launcherSettingsStore = settingsStore,
            launchCoordinator = mockk<CoverLaunchCoordinator>(relaxed = true),
            deviceLockState = MutableStateFlow(false),
            onAppSelected = { _: AppModel -> true },
            persistKeyboardStrategy = { _: KeyboardStrategy -> /* no-op */ }
        )
    }
}

