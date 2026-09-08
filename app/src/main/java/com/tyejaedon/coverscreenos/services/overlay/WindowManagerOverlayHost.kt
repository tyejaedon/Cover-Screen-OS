package com.tyejaedon.coverscreenos.services.overlay

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.overlay.surface.CoverComposeSurface
import com.tyejaedon.coverscreenos.receivers.LockStatusReceiver
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import com.tyejaedon.coverscreenos.ui.controllers.CoverAppLauncher
import com.tyejaedon.coverscreenos.ui.launcher.CoverAppGridOverlay
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Legacy `TYPE_APPLICATION_OVERLAY` implementation of [OverlayHost].
 *
 * This is a direct extraction of the pre-Phase-3
 * [OverlayWindowController] body — the [ForegroundService] holds a
 * [Context] and adds the overlay via `SYSTEM_ALERT_WINDOW`. Kept
 * behind the [OverlayHostMode.LEGACY_WINDOW] runtime flag for one
 * release for rollback safety per the migration plan §6.
 */
internal class WindowManagerOverlayHost(
    private val context: Context,
    private val appRepository: PackageManagerAppScannerRepository,
    private val launcherSettingsStore: LauncherSettingsStore,
    private val launchCoordinator: CoverLaunchCoordinator? = null
) : OverlayHost {

    private val surface = CoverComposeSurface(
        hostContext = context,
        windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        logTag = LOG_TAG
    )

    override fun showOverlay(
        targetDisplay: Display?,
        forceReattach: Boolean,
        deviceLockState: StateFlow<Boolean>?
    ): Boolean {
        val display = targetDisplay ?: resolveDefaultDisplay() ?: run {
            Log.e(LOG_TAG, "No display available to attach overlay")
            return false
        }
        val desiredDisplayId = display.displayId

        val isExistingOverlayReusable = surface.isAttached() &&
            !forceReattach &&
            surface.activeDisplayId() == desiredDisplayId

        if (isExistingOverlayReusable) {
            surface.setTouchable(true)
            return true
        }

        if (surface.isAttached()) {
            surface.detach()
        }

        val attached = surface.attach(display) {
            val composeContext = LocalContext.current
            val overlayScope = rememberCoroutineScope()
            val isDeviceLocked = deviceLockState
                ?.collectAsStateWithLifecycle(initialValue = false)
                ?.value
                ?: false
            val launcherSettings by launcherSettingsStore.settings
                .collectAsStateWithLifecycle(initialValue = LauncherSettings())

            CoverOSTheme(themePreference = launcherSettings.themePreference) {
                CoverAppGridOverlay(
                    repository = appRepository,
                    onAppSelected = { appModel ->
                        val packageName = appModel.packageName
                        Log.d(
                            LOG_TAG,
                            "App tap received package=$packageName locked=${LockStatusReceiver.currentLockStatus(composeContext)}"
                        )
                        val coordinator = launchCoordinator
                        val readyToLaunch = coordinator?.beginLaunch(packageName) ?: true

                        if (!readyToLaunch) {
                            Log.w(LOG_TAG, "Launch coordination rejected package=$packageName")
                            return@CoverAppGridOverlay
                        }

                        val launched = CoverAppLauncher.launchAppOnCoverScreen(composeContext, appModel)
                        if (!launched) {
                            Log.w(
                                LOG_TAG,
                                "Launch dispatch failed package=$packageName; overlay remains visible"
                            )
                        }
                        coordinator?.completeLaunch(packageName = packageName, launchDispatched = launched)
                    },
                    isDeviceLocked = isDeviceLocked,
                    dockPackageSlots = launcherSettings.dockPackages,
                    isDockVisible = launcherSettings.isDockVisible,
                    wallpaperUri = launcherSettings.wallpaperUri,
                    wallpaperScaleMode = launcherSettings.wallpaperScaleMode,
                    wallpaperDimAmount = launcherSettings.wallpaperDimAmount,
                    wallpaperBlurRadiusDp = launcherSettings.wallpaperBlurRadiusDp,
                    keyboardStrategy = launcherSettings.keyboardStrategy,
                    onKeyboardStrategyChanged = { nextStrategy ->
                        overlayScope.launch {
                            runCatching {
                                launcherSettingsStore.setKeyboardStrategy(nextStrategy)
                            }.onFailure { error ->
                                Log.w(
                                    LOG_TAG,
                                    "Unable to persist keyboard strategy=$nextStrategy: ${error.message}"
                                )
                            }
                        }
                    }
                )
            }
        }

        if (attached) {
            surface.setTouchable(true)
        }
        return attached
    }

    override fun removeOverlay() {
        surface.detach()
    }

    override fun hideOverlay() {
        suppressOverlayForLaunch()
    }

    override fun suppressOverlayForLaunch() {
        surface.setTouchable(false)
    }

    override fun destroy() {
        surface.detach()
    }

    override fun getActiveDisplayId(): Int? = surface.activeDisplayId()

    override fun isOverlayAttached(): Boolean = surface.isAttached()

    private fun resolveDefaultDisplay(): Display? {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        return displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
    }

    private companion object {
        private const val LOG_TAG = "WindowManagerOverlayHost"
    }
}

