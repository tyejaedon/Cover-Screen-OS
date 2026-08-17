package com.tyejaedon.coverscreenos.ui.controllers

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.util.Log
import android.view.Display
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.services.ForegroundService

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

    internal fun launchPackageOnDisplay(
        context: Context,
        packageName: String,
        displayId: Int?,
        activityLaunchExecutor: ActivityLaunchExecutor
    ): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            logWarning("No launch intent for package $packageName")
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

        // Apply mandatory flags to ensure task stack isolation.
        // FLAG_ACTIVITY_NEW_TASK is required from a service context.
        // FLAG_ACTIVITY_MULTIPLE_TASK forces a new instance specifically for the cover display, leaving the main screen untouched.
        // FLAG_ACTIVITY_CLEAR_TOP ensures a clean back-stack initialization.
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        val launchDisplayId = resolveLaunchDisplayId(context = context, displayId = displayId)

        return try {
            // Relinquish window focus to the newly launched app by hiding the overlay.
            val hideIntent = ForegroundService.createHideOverlayIntent(context, packageName)
            context.startService(hideIntent)

            activityLaunchExecutor.launch(context, launchIntent, launchDisplayId)
            true
        } catch (error: ActivityNotFoundException) {
            logWarning(
                "Launch target missing for $packageName on display $launchDisplayId: ${error.message}"
            )
            false
        } catch (error: SecurityException) {
            logWarning(
                "Launch blocked by security policy for $packageName on display $launchDisplayId: ${error.message}"
            )
            false
        } catch (error: RuntimeException) {
            logWarning(
                "Unexpected launch failure for $packageName on display $launchDisplayId: ${error.message}"
            )
            false
        }
    }

    private fun resolveLaunchDisplayId(context: Context, displayId: Int?): Int {
        return displayId
            ?: runCatching { context.display.displayId }.getOrNull()
            ?: Display.DEFAULT_DISPLAY
    }

    private fun resolveLaunchActivityInfo(
        context: Context,
        packageName: String,
        launchIntent: Intent
    ): ActivityInfo? {
        val packageManager = context.packageManager
        val component = launchIntent.component
        if (component != null) {
            return runCatching {
                @Suppress("DEPRECATION")
                packageManager.getActivityInfo(component, 0)
            }.onFailure { error ->
                logWarning(
                    "Launch validation failed for $packageName (component=${component.className}): ${error.message}"
                )
            }.getOrNull()
        }

        return runCatching {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(launchIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        }.onFailure { error ->
            logWarning("Launch validation failed for $packageName: ${error.message}")
        }.getOrNull()
    }

    private fun logWarning(message: String) {
        runCatching {
            Log.w(LOG_TAG, message)
        }
    }
}

