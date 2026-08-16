package com.tyejaedon.coverscreenos.services

import android.content.Context
import android.hardware.display.DisplayManager
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import com.tyejaedon.coverscreenos.ui.CoverAppGridOverlay
import com.tyejaedon.coverscreenos.ui.controllers.CoverAppLauncher
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import kotlinx.coroutines.flow.StateFlow

class OverlayWindowController(private val context: Context) {

    private val appRepository = PackageManagerAppScannerRepository(context.applicationContext)
    private val launcherSettingsStore = LauncherSettingsStore(context.applicationContext)
    private var overlayView: View? = null
    private var overlayWindowManager: WindowManager? = null
    private var overlayWindowContext: Context? = null
    private var activeDisplayId: Int? = null
    private var overlayLifecycleOwner: OverlayViewLifecycleOwner? = null

    fun showOverlay(
        targetDisplay: Display? = null,
        forceReattach: Boolean = false,
        deviceLockState: StateFlow<Boolean>? = null
    ): Boolean {
        val desiredDisplayId = targetDisplay?.displayId ?: Display.DEFAULT_DISPLAY
        if (overlayView != null && !forceReattach && activeDisplayId == desiredDisplayId) {
            return true
        }
        if (overlayView != null) {
            removeOverlay()
        }

        val preparedOverlay = runCatching {
            val windowContext = createWindowContext(targetDisplay)
            val windowManager = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val lifecycleOwner = OverlayViewLifecycleOwner()
            val composeView = ComposeView(windowContext).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setContent {
                    val context = LocalContext.current
                    val isDeviceLocked = deviceLockState?.collectAsState()?.value ?: false
                    val launcherSettings = launcherSettingsStore.settings
                        .collectAsState(initial = LauncherSettings())
                        .value
                    CoverOSTheme(themePreference = launcherSettings.themePreference) {
                        CoverAppGridOverlay(
                            repository = appRepository,
                            onAppSelected = { appModel ->
                                val launchIntent = ForegroundService.createLaunchAppIntent(
                                    context = context,
                                    packageName = appModel.packageName
                                )
                                runCatching { context.startService(launchIntent) }
                                    .onFailure { error ->
                                        Log.w(
                                            "OverlayWindowController",
                                            "Service launch routing failed for ${appModel.packageName}: ${error.message}"
                                        )
                                        // Fallback keeps launch functional even if service command delivery fails.
                                        CoverAppLauncher.launchAppOnCoverScreen(context, appModel)
                                    }
                            },
                            isDeviceLocked = isDeviceLocked,
                            dockPackageSlots = launcherSettings.dockPackages,
                            isDockVisible = launcherSettings.isDockVisible,
                            wallpaperUri = launcherSettings.wallpaperUri,
                            wallpaperScaleMode = launcherSettings.wallpaperScaleMode,
                            wallpaperDimAmount = launcherSettings.wallpaperDimAmount,
                            wallpaperBlurRadiusDp = launcherSettings.wallpaperBlurRadiusDp
                        )
                    }
                }
            }

            Triple(windowContext, windowManager, composeView) to lifecycleOwner
        }.getOrElse { error ->
            Log.w("OverlayWindowController", "Overlay preparation failed: ${error.message}")
            return false
        }

        val (overlayParts, lifecycleOwner) = preparedOverlay
        val (windowContext, windowManager, composeView) = overlayParts

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        val added = runCatching {
            windowManager.addView(composeView, layoutParams)
            overlayView = composeView
            overlayWindowManager = windowManager
            overlayWindowContext = windowContext
            overlayLifecycleOwner = lifecycleOwner
            activeDisplayId = runCatching { windowContext.display.displayId }.getOrNull()
                ?: targetDisplay?.displayId
                ?: Display.DEFAULT_DISPLAY
            true
        }.getOrDefault(false)

        if (!added) {
            overlayView = null
            overlayWindowManager = null
            overlayWindowContext = null
            overlayLifecycleOwner = null
            activeDisplayId = null
        }

        return added
    }

    fun removeOverlay() {
        val view = overlayView ?: return
        runCatching { overlayWindowManager?.removeView(view) }
        overlayLifecycleOwner?.onDestroy()
        overlayView = null
        overlayWindowManager = null
        overlayWindowContext = null
        overlayLifecycleOwner = null
        activeDisplayId = null
    }

    fun getActiveDisplayId(): Int? {
        return activeDisplayId
    }

    fun isOverlayAttached(): Boolean {
        return overlayView != null
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
            // Some OEM builds reject createWindowContext from service-derived contexts.
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

private class OverlayViewLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle
        get() = registry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    fun onDestroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

