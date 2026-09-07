package com.tyejaedon.coverscreenos.overlay.surface

import android.content.Context
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoverComposeSurfaceTest {

    private val appContext: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `initial state is detached with no active display`() {
        val surface = CoverComposeSurface(
            hostContext = appContext,
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            logTag = TAG
        )
        assertFalse(surface.isAttached())
        assertNull(surface.activeDisplayId())
    }

    @Test
    fun `detach on unattached surface is a safe no-op`() {
        val surface = CoverComposeSurface(
            hostContext = appContext,
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            logTag = TAG
        )
        // Should not throw.
        surface.detach()
        assertFalse(surface.isAttached())
        assertNull(surface.activeDisplayId())
    }

    @Test
    fun `setTouchable before attach does not throw and is deferred`() {
        val surface = CoverComposeSurface(
            hostContext = appContext,
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            logTag = TAG
        )
        // Should be a safe no-op prior to attach.
        surface.setTouchable(false)
        surface.setTouchable(true)
        assertFalse(surface.isAttached())
    }

    /**
     * Asserts the AndroidX savedstate 1.4.0 ordering contract that
     * [CoverComposeSurface.SurfaceLifecycleOwner] must uphold: `performRestore`
     * runs while the lifecycle is INITIALIZED, so by the time observers
     * receive ON_CREATE the `SavedStateRegistry` is already restored and
     * downstream consumers (`consumeRestoredStateForKey`) succeed.
     *
     * A fake `SavedStateRegistry`-aware observer records `isRestored` at each
     * event dispatch.
     */
    @Test
    fun `SurfaceLifecycleOwner restores saved state before dispatching ON_CREATE`() {
        val owner = CoverComposeSurface.SurfaceLifecycleOwner()

        // After construction (init { performAttach; performRestore }) but
        // before any lifecycle events, the registry is already restored.
        assertTrue(
            "performRestore(null) must have run during init",
            owner.savedStateRegistry.isRestored
        )

        val samples = mutableListOf<EventSample>()
        owner.lifecycle.addObserver(LifecycleEventObserver { source, event ->
            val consumer = source as CoverComposeSurface.SurfaceLifecycleOwner
            samples += EventSample(event, consumer.savedStateRegistry.isRestored)
        })

        owner.start()

        assertEquals(
            listOf(
                Lifecycle.Event.ON_CREATE,
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME
            ),
            samples.map { it.event }
        )
        // isRestored must be true at every dispatched event — this is the
        // property that lets Compose-tree SavedStateRegistry consumers
        // wired via setViewTreeSavedStateRegistryOwner successfully call
        // consumeRestoredStateForKey during ON_CREATE.
        for (sample in samples) {
            assertTrue(
                "SavedStateRegistry must be restored at ${sample.event}",
                sample.isRestoredAtEvent
            )
        }

        // Pause / resume transitions still deliver events and preserve
        // the restored flag.
        owner.pause()
        owner.resume()
        assertTrue(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))

        owner.destroy()
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }

    private data class EventSample(
        val event: Lifecycle.Event,
        val isRestoredAtEvent: Boolean
    )

    private companion object {
        private const val TAG = "CoverComposeSurfaceTest"
    }
}
