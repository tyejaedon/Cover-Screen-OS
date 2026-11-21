package com.tyejaedon.coverscreenos.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View

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
     * Creates and configures an [android.appwidget.AppWidgetHostView] for a given [appWidgetId].
     * Returns null if provider info is missing or binding failed.
     */
    fun createWidgetView(context: Context, appWidgetId: Int): View? {
        val appWidgetInfo = appWidgetManager?.getAppWidgetInfo(appWidgetId) ?: return null
        return try {
            appWidgetHost.createView(context, appWidgetId, appWidgetInfo)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to create AppWidgetHostView for id=$appWidgetId", t)
            null
        }
    }

    fun getAppWidgetInfo(appWidgetId: Int): AppWidgetProviderInfo? {
        return appWidgetManager?.getAppWidgetInfo(appWidgetId)
    }

    fun getInstalledProviders(): List<AppWidgetProviderInfo> {
        return try {
            appWidgetManager?.installedProviders.orEmpty()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to query installed widget providers", t)
            emptyList()
        }
    }

    fun bindAppWidgetIdIfAllowed(appWidgetId: Int, info: AppWidgetProviderInfo): Boolean {
        val manager = appWidgetManager ?: return false
        return try {
            val profile = runCatching { info.profile }.getOrNull()
            if (profile != null) {
                manager.bindAppWidgetIdIfAllowed(appWidgetId, profile, info.provider, null)
            } else {
                manager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to bind appWidgetId=$appWidgetId to ${info.provider}", t)
            false
        }
    }

    fun createBindIntent(appWidgetId: Int, info: AppWidgetProviderInfo): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            runCatching { info.profile }.getOrNull()?.let { profile ->
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, profile)
            }
        }
    }

    fun startConfigureActivityForResult(
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
            Log.w(TAG, "Failed to start configure activity for appWidgetId=$appWidgetId", t)
            false
        }
    }

    fun isHostListening(): Boolean = isListening

    companion object {
        private const val TAG = "CoverAppWidgetHost"
        const val COVER_WIDGET_HOST_ID = 1042
    }
}
