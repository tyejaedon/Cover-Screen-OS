package com.tyejaedon.coverscreenos.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import com.tyejaedon.coverscreenos.models.AppModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PackageManagerAppScannerRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private companion object {
        private const val APP_SCAN_CACHE_TTL_MS = 60_000L
    }

    private val cacheLock = Any()
    private var cachedApps: List<AppModel>? = null
    private var cachedAtElapsedMs: Long = 0L

    suspend fun scanInstalledApplications(): List<AppModel> = withContext(ioDispatcher) {
        val now = SystemClock.elapsedRealtime()
        synchronized(cacheLock) {
            val apps = cachedApps
            if (apps != null && (now - cachedAtElapsedMs) <= APP_SCAN_CACHE_TTL_MS) {
                return@withContext apps
            }
        }

        val packageManager = context.packageManager
        @Suppress("DEPRECATION")
        val installedApps = packageManager.getInstalledApplications(0)

        val scannedApps = installedApps
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

        synchronized(cacheLock) {
            cachedApps = scannedApps
            cachedAtElapsedMs = SystemClock.elapsedRealtime()
        }

        scannedApps
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

