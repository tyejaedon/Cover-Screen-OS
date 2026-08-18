package com.tyejaedon.coverscreenos.ui.controllers

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.services.overlay.ForegroundService

object CoverAppLauncher {

    private const val LOG_TAG = "CoverAppLauncher"

    internal fun interface ActivityLaunchExecutor {
        fun launch(context: Context, launchIntent: Intent, launchDisplayId: Int)
    }

    private val defaultActivityLaunchExecutor = ActivityLaunchExecutor { context, launchIntent, launchDisplayId ->
        val launchOptions = ActivityOptions.makeBasic().apply {
            this.launchDisplayId = launchDisplayId
        }.toBundle()
        context.startActivity(launchIntent, launchOptions)
    }

    /**
     * Primary entry point to launch an app directly onto the secondary/cover display.
     */
    fun launchAppOnCoverScreen(context: Context, appModel: AppModel): Boolean {
        val coverDisplayId = resolveLaunchDisplayId(context = context, displayId = null)
        return launchPackageOnDisplay(
            context = context,
            packageName = appModel.packageName,
            displayId = coverDisplayId
        )
    }

    fun launchPackageOnDisplay(
        context: Context,
        packageName: String,
        displayId: Int?
    ): Boolean {
        return launchPackageOnDisplay(
            context = context,
            packageName = packageName,
            displayId = displayId,
            activityLaunchExecutor = defaultActivityLaunchExecutor
        )
    }

    internal fun launchPackageOnDisplayAfterUnlock(
        context: Context,
        packageName: String,
        displayId: Int?
    ): Boolean {
        return launchPackageOnDisplay(
            context = context,
            packageName = packageName,
            displayId = displayId,
            activityLaunchExecutor = defaultActivityLaunchExecutor,
            skipUnlockChallenge = true
        )
    }

    internal fun launchPackageOnDisplay(
        context: Context,
        packageName: String,
        displayId: Int?,
        activityLaunchExecutor: ActivityLaunchExecutor,
        skipUnlockChallenge: Boolean = false
    ): Boolean {
        logDebug("Launch requested package=$packageName displayId=$displayId")
        if (!skipUnlockChallenge && isDeviceLocked(context)) {
            logDebug("Device locked; delegating to unlock bridge package=$packageName")
            val bridgeStarted = UnlockBridgeActivity.startUnlockRequest(
                context = context,
                packageName = packageName,
                displayId = displayId
            )
            if (!bridgeStarted) {
                logWarning("Unlock bridge unavailable; launch request deferred package=$packageName")
            }
            return false
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            logWarning("No launch intent found for package $packageName")
            return false
        }

        val launchActivityInfo = resolveLaunchActivityInfo(
            context = context,
            packageName = packageName,
            launchIntent = launchIntent
        ) ?: return false

        if (!launchActivityInfo.enabled) {
            logWarning("Launch blocked because activity is disabled for package $packageName")
            return false
        }
        if (!launchActivityInfo.exported) {
            logWarning("Launch blocked because activity is not exported for package $packageName")
            return false
        }

        // Apply task stack isolation flags:
        // - FLAG_ACTIVITY_NEW_TASK is required when launching from non-activity contexts.
        // - FLAG_ACTIVITY_MULTIPLE_TASK isolates the task to the target display.
        // - FLAG_ACTIVITY_CLEAR_TOP ensures a fresh launch state.
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        )

        val launchDisplayId = resolveLaunchDisplayId(context = context, displayId = displayId)

        return try {
            // Suppress the overlay UI so the launched cover app receives input focus immediately
            val hideIntent = ForegroundService.createHideOverlayIntent(context, packageName)
            context.startService(hideIntent)

            activityLaunchExecutor.launch(context, launchIntent, launchDisplayId)
            true
        } catch (error: ActivityNotFoundException) {
            logWarning("Launch target missing for $packageName on display $launchDisplayId: ${error.message}")
            false
        } catch (error: SecurityException) {
            logWarning("Launch blocked by security policy for $packageName on display $launchDisplayId: ${error.message}")
            false
        } catch (error: RuntimeException) {
            logWarning("Unexpected launch failure for $packageName on display $launchDisplayId: ${error.message}")
            false
        }
    }

    private fun resolveLaunchDisplayId(context: Context, displayId: Int?): Int {
        if (displayId != null && displayId != Display.INVALID_DISPLAY) {
            return displayId
        }

        val contextDisplayId = runCatching {
            context.display.displayId
        }.getOrNull()

        if (contextDisplayId != null && contextDisplayId != Display.INVALID_DISPLAY) {
            return contextDisplayId
        }

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        return defaultDisplay?.displayId ?: Display.DEFAULT_DISPLAY
    }

    private fun resolveLaunchActivityInfo(
        context: Context,
        packageName: String,
        launchIntent: Intent
    ): android.content.pm.ActivityInfo? {
        return runCatching {
            launchIntent.resolveActivityInfo(context.packageManager, PackageManager.MATCH_DEFAULT_ONLY)
        }.onFailure { error ->
            logWarning("ResolveActivityInfo failed for $packageName: ${error.message}")
        }.getOrNull()
    }

    private fun isDeviceLocked(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceLocked
            ?: keyguardManager?.isKeyguardLocked
            ?: false
    }


    private fun logWarning(message: String) {
        runCatching {
            Log.w(LOG_TAG, message)
        }
    }

    private fun logDebug(message: String) {
        runCatching {
            Log.d(LOG_TAG, message)
        }
    }
}