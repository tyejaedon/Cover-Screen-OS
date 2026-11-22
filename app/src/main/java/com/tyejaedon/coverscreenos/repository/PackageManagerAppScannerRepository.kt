package com.tyejaedon.coverscreenos.repository

import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.SystemClock
import com.tyejaedon.coverscreenos.models.AppModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository that queries Android's [PackageManager] for user-launchable applications to populate
 * the cover-screen app drawer and dock picker.
 *
 * Metadata-only scans (`includeIcons = false`) share a process-wide in-memory TTL cache so repeated
 * opens remain fast without retaining heavy icon [android.graphics.drawable.Drawable] instances.
 *
 * @param context Android context used to access [PackageManager].
 * @param ioDispatcher Dispatcher on which IPC-heavy package queries run (defaults to [Dispatchers.IO]).
 * @param elapsedRealtimeProvider Monotonic clock supplier (injectable for deterministic unit tests).
 */
class PackageManagerAppScannerRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val elapsedRealtimeProvider: () -> Long = {
        runCatching { SystemClock.elapsedRealtime() }
            .getOrElse { System.nanoTime() / 1_000_000L }
    }
) {

    /** Process-wide short-lived cache for icon-less app metadata scans. */
    private companion object {
        // Cache freshness window (2 minutes) so repeated drawer opens avoid redundant IPC calls.
        private const val APP_SCAN_CACHE_TTL_MS = 120_000L
        // Synchronization monitor protecting cachedApps and cachedAtElapsedMs across coroutine threads.
        private val cacheLock = Any()
        // Shared icon-less app list cached across screens without pinning heavy Drawables in memory.
        private var cachedApps: List<AppModel>? = null
        // Monotonic elapsedRealtime timestamp marking when cachedApps was last refreshed.
        private var cachedAtElapsedMs: Long = 0L
    }

    /**
     * Queries installed launcher activities via [PackageManager.queryIntentActivities] and returns
     * deduplicated [AppModel] entries sorted alphabetically by display label.
     *
     * @param includeIcons When `true`, resolves each app's icon via [PackageManager.getApplicationIcon]
     * (bypassing the shared metadata cache so icon drawables are never pinned statically). When `false`,
     * returns cached results if within [APP_SCAN_CACHE_TTL_MS].
     */
    suspend fun scanInstalledApplications(includeIcons: Boolean = false): List<AppModel> = withContext(ioDispatcher) {
        // Current monotonic clock reading used to test cache expiration safely across clock changes.
        val now = elapsedRealtimeProvider()
        if (!includeIcons) {
            synchronized(cacheLock) {
                val apps = cachedApps
                if (apps != null && (now - cachedAtElapsedMs) <= APP_SCAN_CACHE_TTL_MS) {
                    return@withContext apps
                }
            }
        }

        // Android PackageManager IPC handle for inspecting installed apps and icons.
        val packageManager = context.packageManager
        // Standard launcher Intent filter matching apps that declare an entry point icon in home screens.
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        // Raw list of launchable Activity entries returned by Android's package registry.
        val launcherActivities = packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.ResolveInfoFlags.of(0)
        )

        // Generic Android fallback icon used only if loading a specific app's icon throws.
        val defaultIcon = if (includeIcons) packageManager.defaultActivityIcon else null

        // Deduplicated, alphabetically sorted app descriptors returned to the launcher/dock UI.
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
            // Collapse packages exposing multiple launcher activities into one drawer entry.
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

