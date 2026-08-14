package com.tyejaedon.coverscreenos.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.tyejaedon.coverscreenos.models.AppModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PackageManagerAppScannerRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun scanInstalledApplications(): List<AppModel> = withContext(ioDispatcher) {
        val packageManager = context.packageManager
        @Suppress("DEPRECATION")
        val installedApps = packageManager.getInstalledApplications(0)

        installedApps
            .asSequence()
            .filter { appInfo -> shouldIncludeApplication(packageManager, appInfo) }
            .map { appInfo ->
                AppModel(
                    name = packageManager.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    iconDrawable = packageManager.getApplicationIcon(appInfo)
                )
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    private fun shouldIncludeApplication(
        packageManager: PackageManager,
        appInfo: ApplicationInfo
    ): Boolean {
        val hasLauncherActivity = packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

        // Keep launchable apps and filter non-launchable background system components.
        return hasLauncherActivity || !isSystemApp
    }
}

