package com.tyejaedon.coverscreenos.services.overlay

import com.tyejaedon.coverscreenos.datastore.KeyboardStrategy
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Immutable data holder handed from [ForegroundService] to
 * [CoverAccessibilityService] so the accessibility service can host the
 * launcher `CoverAppGridOverlay` composition without having to reach back
 * into the foreground service for anything.
 *
 * This is a **Phase 2** plumbing artefact: the AS only stores the reference
 * today. Phase 3 flips a feature flag and the AS uses this host to actually
 * mount a [com.tyejaedon.coverscreenos.overlay.surface.CoverComposeSurface].
 *
 * All fields are read-only. Mutation happens only through the two callbacks
 * ([onAppSelected], [persistKeyboardStrategy]), whose implementations live
 * inside the foreground service so the AS never learns about
 * `CoverLaunchCoordinator` or `CoverAppLauncher` internals.
 *
 * @property appRepository app-list source used by the launcher grid.
 * @property launcherSettingsStore backing store for launcher persistence
 *   (dock, wallpaper, theme, keyboard strategy). Exposed so the composition
 *   can `.collectAsStateWithLifecycle` on [settings] directly.
 * @property launchCoordinator single-flight coordinator that gates
 *   overlapping launch dispatches.
 * @property deviceLockState device-lock flow driven by `CoverDisplayHelper`;
 *   the launcher grid consumes this to gate secure launches.
 * @property settings snapshot flow of all launcher settings — theme, dock
 *   layout, wallpaper, keyboard strategy — as one immutable
 *   [LauncherSettings] value per emission.
 * @property onAppSelected launch dispatcher invoked when a grid entry is
 *   tapped. Implementation coordinates with [launchCoordinator] and
 *   `CoverAppLauncher.launchAppOnCoverScreen`. Returns `true` when the
 *   launch was dispatched (Activity/Task fired), `false` otherwise.
 * @property persistKeyboardStrategy `suspend` callback that persists a
 *   newly-picked [KeyboardStrategy] through [launcherSettingsStore].
 */
class LauncherOverlayHost internal constructor(
    val appRepository: PackageManagerAppScannerRepository,
    val launcherSettingsStore: LauncherSettingsStore,
    internal val launchCoordinator: CoverLaunchCoordinator,
    val deviceLockState: StateFlow<Boolean>,
    val onAppSelected: (AppModel) -> Boolean,
    val persistKeyboardStrategy: suspend (KeyboardStrategy) -> Unit
) {
    /** Convenience alias for `launcherSettingsStore.settings`. */
    val settings: Flow<LauncherSettings> get() = launcherSettingsStore.settings
}

