package com.tyejaedon.coverscreenos.overlay.surface

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
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
 * Window layout configuration for [CoverComposeSurface] instances attached via `WindowManager`.
 *
 * Defaults target full-screen secondary-display overlays extending into display cutouts
 * without stealing IME/key focus ([WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE]).
 */
internal data class CoverSurfaceWindowConfig(
    // Desired window width in pixels or LayoutParams constant (defaults to full cover-screen width).
    val width: Int = WindowManager.LayoutParams.MATCH_PARENT,
    // Desired window height in pixels or LayoutParams constant (defaults to full cover-screen height).
    val height: Int = WindowManager.LayoutParams.MATCH_PARENT,
    // Anchor corner/alignment inside the target display (top-left by default).
    val gravity: Int = Gravity.TOP or Gravity.START,
    // Additional WindowManager flags supplied by callers (e.g., FLAG_WATCH_OUTSIDE_TOUCH).
    val extraFlags: Int = 0,
    // Optional window transition animation style resource ID (`0` disables custom animations).
    val windowAnimations: Int = 0,
    // Cutout policy so the cover UI renders around hinge/camera cutouts without black bars.
    val cutoutMode: Int = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
) {
    /**
     * Builds the combined `WindowManager.LayoutParams.flags` bitmask for this surface,
     * optionally adding [WindowManager.LayoutParams.FLAG_SECURE] when [secure] is `true`.
     */
    fun flags(secure: Boolean): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            extraFlags or
            (if (secure) WindowManager.LayoutParams.FLAG_SECURE else 0)
}

/**
 * Host-agnostic Compose overlay surface. Owns exactly one [ComposeView],
 * one [LifecycleOwner] / [SavedStateRegistryOwner] / [ViewModelStoreOwner],
 * and one [WindowManager.LayoutParams].
 *
 * Because a `Service` or `AccessibilityService` has no `Activity` or `Fragment` host,
 * Jetpack Compose needs custom view-tree owners and a display-scoped window context to
 * compose, collect lifecycle-aware flows (`collectAsStateWithLifecycle`), and tear down
 * cleanly. [CoverSurfaceLifecycleOwner] supplies [LifecycleOwner], [SavedStateRegistryOwner],
 * and [ViewModelStoreOwner] directly on the root [ComposeView]; back/gesture navigation for
 * the overlay is routed externally by [com.tyejaedon.coverscreenos.overlay.CoverAccessibilityService].
 *
 * Constructed once per host (e.g. [android.app.Service] or
 * [android.accessibilityservice.AccessibilityService]) and reused across
 * attach/detach cycles.
 *
 * @param hostContext the context of the owning host (Service / AccessibilityService).
 * @param windowType [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY]
 * @param logTag tag used for diagnostic logging.
 */
internal class CoverComposeSurface(
    private val hostContext: Context,
    private val windowType: Int,
    private val logTag: String = "CoverComposeSurface"
) {

    // Active Jetpack Compose root view hosted inside the overlay window (`null` when detached).
    private var composeView: ComposeView? = null
    // Display-scoped WindowManager bound to the target cover screen via [windowContext].
    private var windowManager: WindowManager? = null
    // Window-scoped Context created from `hostContext.createDisplayContext(display).createWindowContext(...)`.
    private var windowContext: Context? = null
    // Custom AndroidX Lifecycle/SavedState/ViewModelStore owner driving Compose state without an Activity.
    private var lifecycleOwner: CoverSurfaceLifecycleOwner? = null
    // Live WindowManager.LayoutParams mutated in place when toggling `FLAG_NOT_TOUCHABLE` or `FLAG_SECURE`.
    private var layoutParams: WindowManager.LayoutParams? = null
    // Android Display ID where [composeView] is currently attached (`null` when detached).
    private var activeDisplayId: Int? = null

    // Tracks whether touches are currently enabled (`false` when suppressed behind a launched app).
    private var isTouchable: Boolean = true
    // Pending touchable state requested before `onViewAttachedToWindow` fired; applied upon attachment.
    private var deferredTouchable: Boolean? = null
    // Desired `FLAG_SECURE` state preserved across re-attach calls and applied in [WindowManager.LayoutParams].
    private var secureRequested: Boolean = false

    /**
     * Attaches a Compose subtree to the given secondary [display].
     *
     * Builds a display-scoped [Context] via [Context.createDisplayContext] followed by a
     * window-scoped [Context] via [Context.createWindowContext] for [windowType] so the
     * resolved [WindowManager] targets [display] rather than `DEFAULT_DISPLAY`. Returns `false`
     * and logs when the underlying window context cannot be created or the platform rejects
     * the token — the caller should treat this as a hard failure with no silent fallback.
     */
    fun attach(
        display: Display,
        windowConfig: CoverSurfaceWindowConfig = CoverSurfaceWindowConfig(),
        onOutsideTouch: (() -> Unit)? = null,
        content: @Composable () -> Unit
    ): Boolean {
        if (composeView != null) {
            Log.w(logTag, "attach called while already attached; detaching first")
            val preserveSecure = secureRequested
            detach()
            secureRequested = preserveSecure
        }

        // Bind Context -> Display -> WindowContext so WindowManager resolves to the cover display.
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

        val owner = CoverSurfaceLifecycleOwner()

        val view = ComposeView(builtWindowContext).apply {
            // Dispose the composition when WindowManager removes the root view from the display.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            // Wire AndroidX view-tree owners required by Compose and lifecycle-aware collectors.
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
            if (onOutsideTouch != null) {
                setOnTouchListener { touchedView, event ->
                    if (event.action == MotionEvent.ACTION_OUTSIDE) {
                        touchedView.performClick()
                        onOutsideTouch()
                        true
                    } else {
                        false
                    }
                }
            }
            setContent(content)
        }

        // WindowManager layout specification binding this ComposeView onto the cover display as an overlay.
        val params = WindowManager.LayoutParams(
            windowConfig.width,
            windowConfig.height,
            windowType,
            windowConfig.flags(secureRequested),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = windowConfig.gravity
            windowAnimations = windowConfig.windowAnimations
            layoutInDisplayCutoutMode = windowConfig.cutoutMode
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

    /**
     * Disposes the Compose composition, removes the view from [WindowManager], destroys
     * [CoverSurfaceLifecycleOwner] (clearing its [ViewModelStore]), and resets internal state.
     */
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

    /**
     * Toggles whether the overlay intercepts touch events and is visible on screen.
     *
     * If called before the [ComposeView] finishes attaching to `WindowManager`, the
     * requested state is stored in [deferredTouchable] and applied once attached.
     */
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

    /**
     * Updates [WindowManager.LayoutParams.FLAG_SECURE] on the overlay window.
     *
     * If the surface is not yet attached, caches [secure] in [secureRequested] for the
     * next [attach] call; when attached, applies the flag live via [WindowManager.updateViewLayout].
     */
    fun setSecure(secure: Boolean): Boolean {
        val view = composeView
        if (view == null) {
            secureRequested = secure
            return true
        }
        val manager = windowManager
        val params = layoutParams
        if (manager == null || params == null) {
            Log.e(logTag, "Unable to update secure flag: attached view has no window state")
            return false
        }
        val previousFlags = params.flags
        val updatedFlags = if (secure) {
            previousFlags or WindowManager.LayoutParams.FLAG_SECURE
        } else {
            previousFlags and WindowManager.LayoutParams.FLAG_SECURE.inv()
        }
        if (previousFlags == updatedFlags) return true

        params.flags = updatedFlags
        return try {
            manager.updateViewLayout(view, params)
            secureRequested = secure
            true
        } catch (error: RuntimeException) {
            params.flags = previousFlags
            Log.e(logTag, "Unable to update secure window flag", error)
            false
        }
    }

    /** Returns the [Display] ID to which this surface is currently attached, or `null`. */
    fun activeDisplayId(): Int? = activeDisplayId

    /** Returns `true` when the underlying [ComposeView] is actively attached to a window. */
    fun isAttached(): Boolean = composeView?.isAttachedToWindow == true

    /** Exposes the internal [CoverSurfaceLifecycleOwner] for unit and Robolectric tests. */
    @VisibleForTesting
    internal fun lifecycleOwnerForTest(): LifecycleOwner? = lifecycleOwner

    /** Exposes the pending or active `FLAG_SECURE` state for unit and Robolectric tests. */
    @VisibleForTesting
    internal fun secureRequestedForTest(): Boolean = secureRequested

    /**
     * Applies touch suppression without detaching the Compose tree.
     *
     * Toggles [WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE] and `view.alpha` (`1f` vs `0f`)
     * while pausing/resuming [lifecycleOwner] so lifecycle-aware flows stop unnecessary work
     * while a cover app owns the foreground.
     */
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

        // Keep View.VISIBLE and toggle alpha so WindowManager preserves the window layout.
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

    /** Clears view, window, and lifecycle references after [detach] or failed [attach]. */
    private fun resetInternalState() {
        composeView = null
        windowManager = null
        windowContext = null
        lifecycleOwner = null
        layoutParams = null
        activeDisplayId = null
        isTouchable = true
        deferredTouchable = null
        secureRequested = false
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
    internal class CoverSurfaceLifecycleOwner :
        LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

        // Drives Compose lifecycle events (CREATE/START/RESUME/PAUSE/STOP/DESTROY) without an Activity.
        private val registry = LifecycleRegistry(this)
        // Supplies AndroidX SavedState support required by Compose's rememberSaveable and view-tree owners.
        private val savedStateController = SavedStateRegistryController.create(this)
        // Retains ViewModels scoped to this overlay surface until [destroy] clears them.
        private val store = ViewModelStore()

        init {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
        }

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store

        /** Advances the surface lifecycle to [Lifecycle.State.RESUMED] after `WindowManager.addView`. */
        fun start() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        /** Moves the surface to [Lifecycle.State.CREATED] while suppressed behind another app. */
        fun pause() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }

        /** Restores the surface to [Lifecycle.State.RESUMED] when the launcher becomes touchable again. */
        fun resume() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        /** Moves the surface to [Lifecycle.State.DESTROYED] and clears its [ViewModelStore]. */
        fun destroy() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }
}
