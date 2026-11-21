package com.tyejaedon.coverscreenos.repository

import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
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

    suspend fun scanInstalledApplications(includeIcons: Boolean = false): List<AppModel> = withContext(ioDispatcher) {
        val now = elapsedRealtimeProvider()
        if (!includeIcons) {
            synchronized(cacheLock) {
                val apps = cachedApps
                if (apps != null && (now - cachedAtElapsedMs) <= APP_SCAN_CACHE_TTL_MS) {
                    return@withContext apps
                }
            }
        }

        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launcherActivities = packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.ResolveInfoFlags.of(0)
        )

        val defaultIcon = if (includeIcons) packageManager.defaultActivityIcon else null

        val scannedApps = launcherActivities
            .asSequence()
            .mapNotNull { resolveInfo ->
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                val packageName = appInfo.packageName
                val resolvedIcon = if (includeIcons) {
                    runCatching {
                        packageManager.getApplicationIcon(packageName)
                    }.getOrDefault(defaultIcon)
                } else {
                    null
                }

                AppModel(
                    name = packageManager.getApplicationLabel(appInfo).toString(),
                    packageName = packageName,
                    iconDrawable = resolvedIcon
                )
            }
            .distinctBy { model -> model.packageName }
            .sortedWith(
                compareBy<AppModel, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.name }
            )
            .toList()

        if (!includeIcons) {
            synchronized(cacheLock) {
                cachedApps = scannedApps
                cachedAtElapsedMs = elapsedRealtimeProvider()
            }
        }

        scannedApps
    }

}

