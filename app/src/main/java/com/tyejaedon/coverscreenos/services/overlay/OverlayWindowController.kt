package com.tyejaedon.coverscreenos.services.overlay

import android.content.Context
import android.util.Log
import android.view.Display
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Launcher overlay façade. Selects between [WindowManagerOverlayHost]
 * (`TYPE_APPLICATION_OVERLAY`, legacy) and [AccessibilityOverlayHost]
 * (`TYPE_ACCESSIBILITY_OVERLAY`, target) at runtime based on the
 * [OverlayHostMode] value provided by [modeProvider].
 *
 * Public API is intentionally byte-compatible with the pre-Phase-3
 * controller so `ForegroundService` and existing Robolectric tests
 * (`ForegroundServiceAttachOrRetargetOverlayRobolectricTest`) continue
 * to compile against a stable seam.
 *
 * See `docs/architecture/Overlay-architecture-shift-plan.md` §5.3.
 */
internal class OverlayWindowController(
    context: Context,
    appRepository: PackageManagerAppScannerRepository,
    launcherSettingsStore: LauncherSettingsStore,
    launchCoordinator: CoverLaunchCoordinator? = null,
    /**
     * Resolves the currently-active [OverlayHostMode] on each façade
     * dispatch. In production this is backed by a coroutine collecting
     * `LauncherSettingsStore.settings.map { it.overlayHostMode }`; in
     * tests it defaults to [OverlayHostMode.DEFAULT] to preserve
     * legacy behavior.
     */
    private val modeProvider: () -> OverlayHostMode = { OverlayHostMode.DEFAULT }
) {

    private val legacyHost: OverlayHost = WindowManagerOverlayHost(
        context = context,
        appRepository = appRepository,
        launcherSettingsStore = launcherSettingsStore,
        launchCoordinator = launchCoordinator
    )

    private val accessibilityHost: OverlayHost = AccessibilityOverlayHost()

    @Volatile
    private var lastResolvedMode: OverlayHostMode? = null

    fun showOverlay(
        targetDisplay: Display? = null,
        forceReattach: Boolean = false,
        deviceLockState: StateFlow<Boolean>? = null
    ): Boolean = activeHost().showOverlay(targetDisplay, forceReattach, deviceLockState)

    fun removeOverlay() {
        activeHost().removeOverlay()
    }

    fun hideOverlay() {
        activeHost().hideOverlay()
    }

    fun suppressOverlayForLaunch() {
        activeHost().suppressOverlayForLaunch()
    }

    fun destroy() {
        // Tear down both hosts regardless of the currently-active
        // mode. The legacy host might still be attached from before a
        // switch, and vice versa.
        runCatching { legacyHost.destroy() }
        runCatching { accessibilityHost.destroy() }
    }

    fun getActiveDisplayId(): Int? = activeHost().getActiveDisplayId()

    fun isOverlayAttached(): Boolean = activeHost().isOverlayAttached()

    /**
     * Resolves the current host, tearing down the previously-active
     * one whenever [modeProvider] reports a switch. Detach on switch
     * is best-effort — a next `showOverlay` on the new host reattaches
     * cleanly.
     */
    private fun activeHost(): OverlayHost {
        val requested = modeProvider()
        val previous = lastResolvedMode
        if (previous != null && previous != requested) {
            val previousHost = hostFor(previous)
            runCatching { previousHost.removeOverlay() }
                .onFailure { error ->
                    Log.w(
                        LOG_TAG,
                        "Failed to detach previous host mode=$previous during switch to $requested: ${error.message}"
                    )
                }
            Log.i(LOG_TAG, "Overlay host mode switched previous=$previous next=$requested")
        }
        lastResolvedMode = requested
        return hostFor(requested)
    }

    private fun hostFor(mode: OverlayHostMode): OverlayHost = when (mode) {
        OverlayHostMode.LEGACY_WINDOW -> legacyHost
        OverlayHostMode.ACCESSIBILITY -> accessibilityHost
    }

    private companion object {
        private const val LOG_TAG = "OverlayWindowController"
    }
}
