package com.tyejaedon.coverscreenos.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe, most-recent-first ring history of user apps opened on the cover display,
 * paired with visibility state for the fallback Cover Recents Task Switcher carousel.
 *
 * Filters out system chrome (`com.android.systemui`, `com.samsung.systemui`, AOD), stock
 * launchers, and Cover-Screen-OS itself so only launchable cover apps appear in Recents.
 */
internal class CoverRecentAppsTracker(
    private val selfPackageName: String = "",
    private val maxEntries: Int = MAX_RECENT_APPS
) {
    private val lock = Any()

    // Backing most-recent-first package list rendered by CoverRecentsPanelTile.
    private val mutableRecentPackages = MutableStateFlow<List<String>>(emptyList())

    /** Ordered list of recently opened user-app packages (newest at index 0). */
    val recentPackages: StateFlow<List<String>> = mutableRecentPackages.asStateFlow()

    // Controls whether the in-overlay Cover Recents carousel is currently expanded.
    private val mutableIsRecentsSheetVisible = MutableStateFlow(false)

    /** Observable visibility flag for the fallback Cover Recents sheet inside CoverAppGridOverlay. */
    val isRecentsSheetVisible: StateFlow<Boolean> = mutableIsRecentsSheetVisible.asStateFlow()

    /**
     * Records [packageName] as the most recently active cover app (moving any prior occurrence
     * to index 0 and trimming to [maxEntries]). No-op for blank or excluded system/launcher packages.
     */
    fun recordAppActive(packageName: String) {
        val normalized = packageName.trim()
        if (!isTrackableRecentApp(normalized)) return

        synchronized(lock) {
            val updated = buildList(maxEntries) {
                add(normalized)
                for (existing in mutableRecentPackages.value) {
                    if (existing != normalized && size < maxEntries) {
                        add(existing)
                    }
                }
            }
            mutableRecentPackages.value = updated
        }
    }

    /** Removes [packageName] from the recent-apps history (e.g., when swiped away in Recents). */
    fun removeApp(packageName: String) {
        val normalized = packageName.trim()
        if (normalized.isEmpty()) return

        synchronized(lock) {
            val current = mutableRecentPackages.value
            if (normalized !in current) return
            mutableRecentPackages.value = current.filterNot { it == normalized }
        }
    }

    /** Clears all recorded recent cover apps ("Close All" / "Clear All" action). */
    fun clearAll() {
        synchronized(lock) {
            mutableRecentPackages.value = emptyList()
        }
    }

    /** Shows or hides the in-overlay Cover Recents sheet. */
    fun setRecentsSheetVisible(visible: Boolean) {
        mutableIsRecentsSheetVisible.value = visible
    }

    private fun isTrackableRecentApp(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        if (selfPackageName.isNotEmpty() && packageName == selfPackageName) return false
        if (EXCLUDED_PACKAGE_PREFIXES.any { packageName.startsWith(it) }) return false
        return true
    }

    companion object {
        internal const val MAX_RECENT_APPS = 10

        // SystemUI, AOD, dialer-free launcher chrome, and self prefix filtered from user Recents.
        private val EXCLUDED_PACKAGE_PREFIXES = arrayOf(
            "com.android.systemui",
            "com.samsung.systemui",
            "com.samsung.android.app.aodservice",
            "com.sec.android.app.launcher",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.tyejaedon.coverscreenos"
        )
    }
}
