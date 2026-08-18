package com.tyejaedon.coverscreenos.services.overlay

import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.hardware.display.DisplayManager
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.datastore.SearchInputMode
import com.tyejaedon.coverscreenos.receivers.LockStatusReceiver
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import com.tyejaedon.coverscreenos.ui.CoverAppGridOverlay
import com.tyejaedon.coverscreenos.ui.controllers.CoverAppLauncher
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class OverlayWindowController(
    private val context: Context,
    private val appRepository: PackageManagerAppScannerRepository,
    private val launcherSettingsStore: LauncherSettingsStore,
    private val launchCoordinator: CoverLaunchCoordinator? = null
) {

    private var composeView: ComposeView? = null
    private var overlayWindowManager: WindowManager? = null
    private var overlayWindowContext: Context? = null
    private var activeDisplayId: Int? = null
    private var overlayLifecycleOwner: OverlayViewLifecycleOwner? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var isLaunchSuppressed: Boolean = false
    private var deferredImeInteractionEnabled: Boolean? = null

    fun showOverlay(
        targetDisplay: Display? = null,
        forceReattach: Boolean = false,
        deviceLockState: StateFlow<Boolean>? = null
    ): Boolean {
        val desiredDisplayId = targetDisplay?.displayId ?: Display.DEFAULT_DISPLAY

        if (composeView != null && !forceReattach && activeDisplayId == desiredDisplayId) {
            setLaunchSuppressed(false)
            return true
        }

        if (composeView != null) {
            removeOverlay()
        }

        try {
            overlayWindowContext = createWindowContext(targetDisplay)
            overlayWindowManager = overlayWindowContext?.getSystemService(WINDOW_SERVICE) as WindowManager

            overlayLifecycleOwner = OverlayViewLifecycleOwner().apply {
                start() // Initialize the custom lifecycle
            }

            composeView = ComposeView(overlayWindowContext!!).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setViewTreeLifecycleOwner(overlayLifecycleOwner)
                setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
                setContent {
                    val composeContext = LocalContext.current
                    val composeScope = rememberCoroutineScope()
                    val isDeviceLocked = deviceLockState
                        ?.collectAsStateWithLifecycle(initialValue = false)
                        ?.value
                        ?: false
                    val launcherSettings by launcherSettingsStore.settings
                        .collectAsStateWithLifecycle(initialValue = LauncherSettings())

                    LaunchedEffect(launcherSettings.searchInputMode) {
                        setImeInteractionEnabled(launcherSettings.searchInputMode == SearchInputMode.SYSTEM_IME)
                    }

                    CoverOSTheme(themePreference = launcherSettings.themePreference) {
                        CoverAppGridOverlay(
                            repository = appRepository,
                            onAppSelected = { appModel ->
                                val packageName = appModel.packageName
                                Log.d(
                                    "OverlayWindowController",
                                    "App tap received package=$packageName locked=${LockStatusReceiver.currentLockStatus(composeContext)}"
                                )
                                val coordinator = launchCoordinator
                                val readyToLaunch = coordinator?.beginLaunch(packageName) ?: true

                                if (!readyToLaunch) {
                                    Log.w("OverlayWindowController", "Launch coordination rejected package=$packageName")
                                    return@CoverAppGridOverlay
                                }

                                val launched = CoverAppLauncher.launchAppOnCoverScreen(composeContext, appModel)
                                coordinator?.completeLaunch(packageName = packageName, launchDispatched = launched)
                            },
                            isDeviceLocked = isDeviceLocked,
                            dockPackageSlots = launcherSettings.dockPackages,
                            isDockVisible = launcherSettings.isDockVisible,
                            wallpaperUri = launcherSettings.wallpaperUri,
                            wallpaperScaleMode = launcherSettings.wallpaperScaleMode,
                            wallpaperDimAmount = launcherSettings.wallpaperDimAmount,
                            wallpaperBlurRadiusDp = launcherSettings.wallpaperBlurRadiusDp,
                            searchInputMode = launcherSettings.searchInputMode,
                            onSearchInputModeChanged = { searchInputMode ->
                                composeScope.launch {
                                    launcherSettingsStore.setSearchInputMode(searchInputMode)
                                }
                            }
                        )
                    }
                }
            }

            overlayLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }

            overlayWindowManager?.addView(composeView, overlayLayoutParams)
            deferredImeInteractionEnabled?.let { enabled ->
                setImeInteractionEnabled(enabled)
            }
            activeDisplayId = overlayWindowContext?.display?.displayId ?: targetDisplay?.displayId ?: Display.DEFAULT_DISPLAY
            setLaunchSuppressed(false)
            return true

        } catch (e: WindowManager.BadTokenException) {
            Log.e("OverlayWindowController", "Invalid window token for overlay attachment", e)
        } catch (e: Exception) {
            Log.e("OverlayWindowController", "Failed to attach overlay window", e)
        }

        // Cleanup on failure
        removeOverlay()
        return false
    }

    fun removeOverlay() {
        try {
            composeView?.let { view ->
                view.disposeComposition() // Force instant tear-down of the Compose tree
                overlayWindowManager?.removeView(view)
            }
        } catch (e: IllegalArgumentException) {
            Log.w("OverlayWindowController", "View was not attached to window manager", e)
        } finally {
            overlayLifecycleOwner?.destroy()
            composeView = null
            overlayWindowManager = null
            overlayWindowContext = null
            overlayLifecycleOwner = null
            overlayLayoutParams = null
            activeDisplayId = null
            isLaunchSuppressed = false
            deferredImeInteractionEnabled = null
        }
    }

    fun hideOverlay() {
        suppressOverlayForLaunch()
    }

    fun suppressOverlayForLaunch() {
        setLaunchSuppressed(true)
    }

    fun destroy() {
        removeOverlay()
    }

    fun getActiveDisplayId(): Int? = activeDisplayId

    fun isOverlayAttached(): Boolean = composeView != null

    private fun setImeInteractionEnabled(enabled: Boolean) {
        val view = composeView
        val manager = overlayWindowManager
        val params = overlayLayoutParams
        if (view == null || manager == null || params == null) {
            deferredImeInteractionEnabled = enabled
            return
        }
        deferredImeInteractionEnabled = null

        val desiredFlags = if (enabled) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        val desiredSoftInputMode = if (enabled) {
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        } else {
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val shouldUpdateLayout = params.flags != desiredFlags || params.softInputMode != desiredSoftInputMode
        if (!shouldUpdateLayout) return

        params.flags = desiredFlags
        params.softInputMode = desiredSoftInputMode
        view.isFocusable = enabled
        view.isFocusableInTouchMode = enabled

        runCatching {
            manager.updateViewLayout(view, params)
            if (enabled) {
                view.requestFocus()
            } else {
                view.clearFocus()
            }
        }.onFailure { error ->
            Log.w("OverlayWindowController", "Unable to update IME interaction mode: ${error.message}")
        }
    }

    private fun setLaunchSuppressed(suppressed: Boolean) {
        val view = composeView ?: return
        val manager = overlayWindowManager ?: return
        val params = overlayLayoutParams ?: return

        if (isLaunchSuppressed == suppressed) return

        val updatedFlags = if (suppressed) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }

        val shouldUpdateLayout = params.flags != updatedFlags
        params.flags = updatedFlags

        view.visibility = View.VISIBLE
        view.alpha = if (suppressed) 0f else 1f
        isLaunchSuppressed = suppressed

        // Optimize performance by halting Compose background recompositions while the view is suppressed
        if (suppressed) {
            overlayLifecycleOwner?.pause()
        } else {
            overlayLifecycleOwner?.resume()
        }

        if (shouldUpdateLayout) {
            try {
                manager.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.w("OverlayWindowController", "Unable to update launch suppression layout: ${e.message}")
            }
        }
    }

    private fun createWindowContext(targetDisplay: Display?): Context {
        val displayContext = if (targetDisplay != null) {
            context.createDisplayContext(targetDisplay)
        } else {
            createDefaultDisplayContext()
        }

        return runCatching {
            displayContext.createWindowContext(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                Bundle()
            )
        }.getOrElse {
            Log.w("OverlayWindowController", "Falling back to display context: ${it.message}")
            displayContext
        }
    }

    private fun createDefaultDisplayContext(): Context {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        return if (defaultDisplay != null) context.createDisplayContext(defaultDisplay) else context
    }
}

/**
 * A dynamic LifecycleOwner that allows pausing Compose recomposition when the overlay is visually suppressed.
 */
private class OverlayViewLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
    }

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun start() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun pause() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    fun resume() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
