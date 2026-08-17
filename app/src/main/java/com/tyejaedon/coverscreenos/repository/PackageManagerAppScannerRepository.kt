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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val elapsedRealtimeProvider: () -> Long = {
        runCatching { SystemClock.elapsedRealtime() }
            .getOrElse { System.nanoTime() / 1_000_000L }
    }
) {

    private companion object {
        private const val APP_SCAN_CACHE_TTL_MS = 120_000L
        private val cacheLock = Any()
        private var cachedApps: List<AppModel>? = null
        private var cachedAtElapsedMs: Long = 0L
    }

    suspend fun scanInstalledApplications(): List<AppModel> = withContext(ioDispatcher) {
        val now = elapsedRealtimeProvider()
        synchronized(cacheLock) {
            val apps = cachedApps
            if (apps != null && (now - cachedAtElapsedMs) <= APP_SCAN_CACHE_TTL_MS) {
                return@withContext apps
            }
        }

        val packageManager = context.packageManager
        @Suppress("DEPRECATION")
        val installedApps = packageManager.getInstalledApplications(0)

        val defaultIcon = packageManager.defaultActivityIcon

        val scannedApps = installedApps
            .asSequence()
            .filter { appInfo -> shouldIncludeApplication(packageManager, appInfo) }
            .map { appInfo ->
                AppModel(
                    name = packageManager.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    // Real icons are resolved lazily for visible UI rows to avoid startup jank.
                    iconDrawable = defaultIcon
                )
            }
            .sortedWith(
                compareBy<AppModel, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.name }
            )
            .toList()

        synchronized(cacheLock) {
            cachedApps = scannedApps
            cachedAtElapsedMs = elapsedRealtimeProvider()
        }

        scannedApps
    }

    private fun shouldIncludeApplication(
        packageManager: PackageManager,
        appInfo: ApplicationInfo
    ): Boolean {
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        if (!isSystemApp) {
            // Keep third-party apps without extra PackageManager intent queries.
            return true
        }

        val hasLauncherActivity = packageManager.getLaunchIntentForPackage(appInfo.packageName) != null

        // Keep launchable apps and filter non-launchable background system components.
        return hasLauncherActivity
    }
}

