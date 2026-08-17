package com.tyejaedon.coverscreenos.ui.controllers

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverAppLauncherInstrumentedTest {

    private companion object {
        private const val LAUNCH_DISPLAY_ID_KEY = "android.activity.launchDisplayId"
    }

    @Test
    fun `launchPackageOnDisplay applies launchDisplayId in activity options`() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val targetDisplayId = appContext.display.displayId
        val capturingContext = CapturingLaunchContext(appContext)

        val launched = CoverAppLauncher.launchPackageOnDisplay(
            context = capturingContext,
            packageName = appContext.packageName,
            displayId = targetDisplayId
        )

        assertTrue(launched)
        assertNotNull(capturingContext.capturedIntent)
        assertNotNull(capturingContext.capturedOptions)

        val requiredFlags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        assertEquals(
            requiredFlags,
            capturingContext.capturedIntent!!.flags and requiredFlags
        )

        val launchOptionsBundle = capturingContext.capturedOptions!!
        assertEquals(targetDisplayId, launchOptionsBundle.getInt(LAUNCH_DISPLAY_ID_KEY))
    }

    private class CapturingLaunchContext(base: Context) : ContextWrapper(base) {
        var capturedIntent: Intent? = null
        var capturedOptions: Bundle? = null

        override fun startActivity(intent: Intent, options: Bundle?) {
            capturedIntent = intent
            capturedOptions = options
        }
    }
}


