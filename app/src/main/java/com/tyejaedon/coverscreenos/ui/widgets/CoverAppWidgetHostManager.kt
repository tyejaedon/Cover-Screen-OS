package com.tyejaedon.coverscreenos.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.util.SizeF
import android.view.View
import kotlin.math.roundToInt

/**
 * Manages [AppWidgetHost] lifecycle and widget view allocation scoped to the cover screen.
 *
 * BATTERY & RESOURCE GOVERNANCE:
 * Listening is strictly paired with overlay visibility:
 * - [startListening] is called ONLY when the cover screen overlay is attached and active.
 * - [stopListening] is called immediately when the overlay is suppressed (unfolded, app launched, incoming call)
 *   or detached, completely pausing RemoteViews updates and background broadcast loops.
 */
class CoverAppWidgetHostManager(
    private val context: Context,
    private val hostId: Int = COVER_WIDGET_HOST_ID,
    private val appWidgetHost: AppWidgetHost = AppWidgetHost(context.applicationContext, hostId),
    private val appWidgetManager: AppWidgetManager? = runCatching { AppWidgetManager.getInstance(context.applicationContext) }.getOrNull()
) {

    @Volatile
    private var isListening: Boolean = false

    /**
     * Starts listening for widget updates. Only called while overlay is visible.
     */
    fun startListening() {
        if (isListening) return
        try {
            appWidgetHost.startListening()
            isListening = true
            Log.d(TAG, "CoverAppWidgetHost started listening")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to start listening on AppWidgetHost", t)
        }
    }

    /**
     * Stops listening for widget updates to eliminate background CPU and battery drain.
     */
    fun stopListening() {
        if (!isListening) return
        try {
            appWidgetHost.stopListening()
            isListening = false
            Log.d(TAG, "CoverAppWidgetHost stopped listening")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to stop listening on AppWidgetHost", t)
        }
    }

    /**
     * Allocates a new widget ID for placement on the cover display.
     */
    fun allocateAppWidgetId(): Int {
        return try {
            appWidgetHost.allocateAppWidgetId()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to allocate appWidgetId", t)
            -1
        }
    }

    /**
     * Deletes a widget ID and unregisters its view from the host.
     */
    fun deleteAppWidgetId(appWidgetId: Int) {
        try {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
        } catch (t: Throwable) {
            Log.w(TAG, "Error deleting appWidgetId=$appWidgetId", t)
        }
    }

    /**
     * Returns all installed widget providers sorted by display label.
     */
    fun getInstalledProviders(): List<AppWidgetProviderInfo> {
        val pm = context.packageManager
        return runCatching {
            appWidgetManager?.installedProviders.orEmpty()
                .sortedBy { it.loadLabel(pm).lowercase() }
        }.getOrDefault(emptyList())
    }

    /**
     * Attempts to bind [appWidgetId] to [provider] without prompting the user.
     */
    fun bindAppWidgetIdIfAllowed(
        appWidgetId: Int,
        provider: ComponentName,
        profile: UserHandle? = null
    ): Boolean {
        val manager = appWidgetManager ?: return false
        return runCatching {
            if (profile != null) {
                manager.bindAppWidgetIdIfAllowed(appWidgetId, profile, provider, null)
            } else {
                manager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
            }
        }.getOrDefault(false)
    }

    /**
     * Builds the system consent intent for binding a widget when [bindAppWidgetIdIfAllowed] is false.
     */
    fun createBindIntent(
        appWidgetId: Int,
        provider: ComponentName,
        profile: UserHandle? = null
    ): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            profile?.let { putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, it) }
        }
    }

    /**
     * Launches the widget provider's configuration activity for [appWidgetId].
     */
    fun launchConfigureActivity(
        activity: Activity,
        appWidgetId: Int,
        requestCode: Int
    ): Boolean {
        return try {
            appWidgetHost.startAppWidgetConfigureActivityForResult(
                activity,
                appWidgetId,
                0,
                requestCode,
                null
            )
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to start configure activity for id=$appWidgetId", t)
            false
        }
    }

    /**
     * Creates and configures an [AppWidgetHostView] for a given [appWidgetId].
     * Returns null if provider info is missing or binding failed.
     */
    fun createWidgetView(
        context: Context,
        appWidgetId: Int,
        widthDp: Int? = null,
        heightDp: Int? = null
    ): View? {
        val appWidgetInfo = appWidgetManager?.getAppWidgetInfo(appWidgetId) ?: return null
        return try {
            val hostView = appWidgetHost.createView(context, appWidgetId, appWidgetInfo)
            if (widthDp != null && heightDp != null && widthDp > 0 && heightDp > 0) {
                applyWidgetSize(hostView, widthDp, heightDp)
            }
            hostView
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to create AppWidgetHostView for id=$appWidgetId", t)
            null
        }
    }

    fun applyWidgetSize(hostView: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        if (widthDp <= 0 || heightDp <= 0) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hostView.updateAppWidgetSize(
                    Bundle(),
                    listOf(SizeF(widthDp.toFloat(), heightDp.toFloat()))
                )
            } else {
                @Suppress("DEPRECATION")
                hostView.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
            }
        }.onFailure { t ->
            Log.w(TAG, "Failed to updateAppWidgetSize(${widthDp}x${heightDp}dp)", t)
        }
    }

    fun getAppWidgetInfo(appWidgetId: Int): AppWidgetProviderInfo? {
        return appWidgetManager?.getAppWidgetInfo(appWidgetId)
    }

    fun isHostListening(): Boolean = isListening

    companion object {
        private const val TAG = "CoverAppWidgetHost"
        const val COVER_WIDGET_HOST_ID = 1042
        const val MIN_WIDGET_HEIGHT_DP = 110
        const val MAX_WIDGET_HEIGHT_DP = 300
        private const val APPROX_CELL_HEIGHT_DP = 76f

        /**
         * Computes a cover-friendly widget height in dp from [info], clamped to
         * [[MIN_WIDGET_HEIGHT_DP], [MAX_WIDGET_HEIGHT_DP]].
         */
        fun resolveWidgetHeightDp(info: AppWidgetProviderInfo?, density: Float): Int {
            if (info == null) return 160
            val safeDensity = density.coerceAtLeast(1f)
            val targetCellHeightDp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellHeight > 0) {
                (info.targetCellHeight * APPROX_CELL_HEIGHT_DP).roundToInt()
            } else {
                0
            }
            val minHeightDp = if (info.minHeight > 0) {
                (info.minHeight / safeDensity).roundToInt()
            } else {
                0
            }
            val rawDp = maxOf(targetCellHeightDp, minHeightDp)
            return if (rawDp <= 0) 160 else rawDp.coerceIn(MIN_WIDGET_HEIGHT_DP, MAX_WIDGET_HEIGHT_DP)
        }

        fun isReconfigurable(info: AppWidgetProviderInfo?): Boolean {
            if (info?.configure == null) return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0
            } else {
                false
            }
        }
    }
}
