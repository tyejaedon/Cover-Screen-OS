package com.tyejaedon.coverscreenos.ui.controllers

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tyejaedon.coverscreenos.models.AppModel

object CoverAppLauncher {

    private const val LOG_TAG = "CoverAppLauncher"

    fun launchAppOnCoverScreen(context: Context, appModel: AppModel): Boolean {
        val displayId = runCatching { context.display.displayId }.getOrNull()
        return launchPackageOnDisplay(
            context = context,
            packageName = appModel.packageName,
            displayId = displayId
        )
    }

    fun launchPackageOnDisplay(
        context: Context,
        packageName: String,
        displayId: Int?
    ): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Log.w(LOG_TAG, "No launch intent for package $packageName")
            return false
        }

        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        )

        val launchOptions = displayId?.let { launchDisplayId ->
            ActivityOptions.makeBasic().apply {
                this.launchDisplayId = launchDisplayId
            }.toBundle()
        }

        val launched = runCatching {
            context.startActivity(launchIntent, launchOptions)
            true
        }.onFailure { error ->
            Log.w(LOG_TAG, "Failed launching $packageName on cover display: ${error.message}")
        }.getOrDefault(false)

        return launched
    }
}

