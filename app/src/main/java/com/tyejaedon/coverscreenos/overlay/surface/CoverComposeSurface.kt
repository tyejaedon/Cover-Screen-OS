package com.tyejaedon.coverscreenos.overlay.surface

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Host-agnostic Compose overlay surface. Owns exactly one [ComposeView],
 * one [LifecycleOwner] / [SavedStateRegistryOwner] / [ViewModelStoreOwner],
 * and one [WindowManager.LayoutParams].
 *
 * Constructed once per host (e.g. [android.app.Service] or
 * [android.accessibilityservice.AccessibilityService]) and reused across
 * attach/detach cycles.
 *
 * @param hostContext the context of the owning host (Service / AccessibilityService).
 * @param windowType either [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]
 *   (legacy) or [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] (target).
 * @param logTag tag used for diagnostic logging.
 */
internal class CoverComposeSurface(
    private val hostContext: Context,
    private val windowType: Int,
    private val logTag: String = "CoverComposeSurface"
) {

    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var windowContext: Context? = null
    private var lifecycleOwner: SurfaceLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var activeDisplayId: Int? = null

    private var isTouchable: Boolean = true
    private var deferredTouchable: Boolean? = null

    /**
     * Attach a Compose subtree to the given [display]. Returns false and
     * logs when the underlying window context cannot be created or the
     * platform rejects the token — the caller should treat this as a
     * hard failure. No silent fallback.
     */
    fun attach(display: Display, content: @Composable () -> Unit): Boolean {
        if (composeView != null) {
            Log.w(logTag, "attach called while already attached; detaching first")
            detach()
        }

        val displayContext = hostContext.createDisplayContext(display)
        val builtWindowContext = runCatching {
            displayContext.createWindowContext(windowType, /* options = */ null)
        }.getOrElse { error ->
            Log.e(
                logTag,
                "createWindowContext failed for display=${display.displayId} type=$windowType — aborting",
                error
            )
            return false
        }

        val wm = builtWindowContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm == null) {
            Log.e(logTag, "WINDOW_SERVICE unavailable for display=${display.displayId} — aborting")
            return false
        }

        val owner = SurfaceLifecycleOwner()

        val view = ComposeView(builtWindowContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    deferredTouchable?.let { pending ->
                        applyTouchable(pending)
                    }
                }
                override fun onViewDetachedFromWindow(v: View) = Unit
            })
            setContent(content)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        return try {
            wm.addView(view, params)
            this.windowContext = builtWindowContext
            this.windowManager = wm
            this.composeView = view
            this.lifecycleOwner = owner
            this.layoutParams = params
            @Suppress("UnnecessarySafeCall")
            this.activeDisplayId = builtWindowContext.display.displayId
                ?: display.displayId
            this.isTouchable = true
            this.deferredTouchable = null
            // ON_CREATE first, then performRestore(null), then ON_START / ON_RESUME.
            owner.start()
            true
        } catch (e: WindowManager.BadTokenException) {
            Log.e(logTag, "BadToken attaching to display=${display.displayId}", e)
            resetInternalState()
            false
        } catch (e: Exception) {
            Log.e(logTag, "Failed to attach surface to display=${display.displayId}", e)
            resetInternalState()
            false
        }
    }

    fun detach() {
        val view = composeView
        val wm = windowManager
        try {
            view?.disposeComposition()
            if (view != null && wm != null) {
                wm.removeView(view)
            }
        } catch (e: IllegalArgumentException) {
            Log.w(logTag, "View was not attached to window manager", e)
        } catch (e: Exception) {
            Log.w(logTag, "Failed to detach surface cleanly: ${e.message}")
        } finally {
            lifecycleOwner?.destroy()
            resetInternalState()
        }
    }

    fun setTouchable(touchable: Boolean) {
        val view = composeView
        if (view == null) {
            // Not attached yet; nothing to do.
            deferredTouchable = touchable
            return
        }
        if (!view.isAttachedToWindow) {
            deferredTouchable = touchable
            return
        }
        applyTouchable(touchable)
    }

    fun activeDisplayId(): Int? = activeDisplayId

    fun isAttached(): Boolean = composeView?.isAttachedToWindow == true

    @VisibleForTesting
    internal fun lifecycleOwnerForTest(): LifecycleOwner? = lifecycleOwner

    private fun applyTouchable(touchable: Boolean) {
        val view = composeView ?: return
        val manager = windowManager ?: return
        val params = layoutParams ?: return

        deferredTouchable = null
        if (isTouchable == touchable) return

        val updatedFlags = if (!touchable) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        val shouldUpdateLayout = params.flags != updatedFlags
        params.flags = updatedFlags

        view.visibility = View.VISIBLE
        view.alpha = if (touchable) 1f else 0f
        isTouchable = touchable

        if (!touchable) {
            lifecycleOwner?.pause()
        } else {
            lifecycleOwner?.resume()
        }

        if (shouldUpdateLayout) {
            try {
                manager.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.w(logTag, "Unable to update touchable layout: ${e.message}")
            }
        }
    }

    private fun resetInternalState() {
        composeView = null
        windowManager = null
        windowContext = null
        lifecycleOwner = null
        layoutParams = null
        activeDisplayId = null
        isTouchable = true
        deferredTouchable = null
    }

    /**
     * Lifecycle owner backing the surface.
     *
     * Ordering contract (androidx.savedstate 1.4.0):
     *   1. [SavedStateRegistryController.performAttach] — while state is INITIALIZED.
     *   2. [SavedStateRegistryController.performRestore] — **also** while state is
     *      INITIALIZED. The library asserts `currentState == INITIALIZED` inside
     *      `performRestore`; calling it after ON_CREATE throws
     *      "Restarter must be created only during owner's initialization stage".
     *   3. Dispatch [Lifecycle.Event.ON_CREATE] → observers registered via
     *      `setViewTreeSavedStateRegistryOwner` may now `consumeRestoredStateForKey`
     *      because `isRestored` is already true.
     *   4. Dispatch ON_START / ON_RESUME.
     *
     * (Note: an earlier design memo suggested moving `performRestore` to *after*
     * ON_CREATE. That is incorrect for AndroidX savedstate ≥ 1.2 — the check
     * shipped there enforces the ordering above.)
     */
    internal class SurfaceLifecycleOwner :
        LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

        private val registry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        init {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
        }

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry
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
}

