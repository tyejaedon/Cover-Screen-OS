package com.tyejaedon.coverscreenos.ui.controllers

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.receivers.LockStatusReceiver
import com.tyejaedon.coverscreenos.services.ForegroundService

object CoverAppLauncher {

    private const val LOG_TAG = "CoverAppLauncher"
    private const val UNLOCK_PROMPT_TITLE = "Unlock to continue"
    private const val UNLOCK_PROMPT_DESCRIPTION = "Authenticate with your device security to open the app"

    private data class PendingUnlockLaunch(
        val packageName: String,
        val displayId: Int?
    )

    @Volatile
    private var pendingUnlockLaunch: PendingUnlockLaunch? = null

    @Volatile
    private var unlockReceiverRegistered = false

    private val unlockCompletionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            context ?: return
            val action = intent?.action ?: return
            if (action != Intent.ACTION_USER_PRESENT && action != Intent.ACTION_USER_UNLOCKED) return

            val pendingLaunch = synchronized(this@CoverAppLauncher) {
                pendingUnlockLaunch.also { pendingUnlockLaunch = null }
            } ?: return

            unregisterUnlockReceiverIfNeeded(context.applicationContext)

            launchPackageOnDisplay(
                context = context.applicationContext,
                packageName = pendingLaunch.packageName,
                displayId = pendingLaunch.displayId,
                activityLaunchExecutor = defaultActivityLaunchExecutor
            )
        }
    }

    internal fun interface ActivityLaunchExecutor {
        fun launch(context: Context, launchIntent: Intent, launchDisplayId: Int)
    }

    private val defaultActivityLaunchExecutor = ActivityLaunchExecutor { context, launchIntent, launchDisplayId ->
        val launchOptions = ActivityOptions.makeBasic().apply {
            this.launchDisplayId = launchDisplayId
        }.toBundle()
        context.startActivity(launchIntent, launchOptions)
    }

    /**
     * Primary entry point to launch an app directly onto the secondary/cover display.
     */
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
        if (isDeviceLocked(context)) {
            delegateUnlockToSystemAndQueueLaunch(
                context = context,
                packageName = packageName,
                displayId = displayId
            )
            return false
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            logWarning("No launch intent found for package $packageName")
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

        // Apply task stack isolation flags:
        // - FLAG_ACTIVITY_NEW_TASK is required when launching from non-activity contexts.
        // - FLAG_ACTIVITY_MULTIPLE_TASK isolates the task to the target display.
        // - FLAG_ACTIVITY_CLEAR_TOP ensures a fresh launch state.
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        )

        val launchDisplayId = resolveLaunchDisplayId(context = context, displayId = displayId)

        return try {
            // Suppress the overlay UI so the launched cover app receives input focus immediately
            val hideIntent = ForegroundService.createHideOverlayIntent(context, packageName)
            context.startService(hideIntent)

            activityLaunchExecutor.launch(context, launchIntent, launchDisplayId)
            true
        } catch (error: ActivityNotFoundException) {
            logWarning("Launch target missing for $packageName on display $launchDisplayId: ${error.message}")
            false
        } catch (error: SecurityException) {
            logWarning("Launch blocked by security policy for $packageName on display $launchDisplayId: ${error.message}")
            false
        } catch (error: RuntimeException) {
            logWarning("Unexpected launch failure for $packageName on display $launchDisplayId: ${error.message}")
            false
        }
    }

    private fun resolveLaunchDisplayId(context: Context, displayId: Int?): Int {
        if (displayId != null && displayId != Display.INVALID_DISPLAY) {
            return displayId
        }

        val contextDisplayId = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.displayId
            } else {
                null
            }
        }.getOrNull()

        if (contextDisplayId != null && contextDisplayId != Display.INVALID_DISPLAY) {
            return contextDisplayId
        }

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        return defaultDisplay?.displayId ?: Display.DEFAULT_DISPLAY
    }

    private fun resolveLaunchActivityInfo(
        context: Context,
        packageName: String,
        launchIntent: Intent
    ): ActivityInfo? {
        val packageManager = context.packageManager
        val component = launchIntent.component

        return if (component != null) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getActivityInfo(
                        component,
                        PackageManager.ComponentInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getActivityInfo(component, 0)
                }
            }.onFailure { error ->
                logWarning("ActivityInfo lookup failed for $packageName ($component): ${error.message}")
            }.getOrNull()
        } else {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.resolveActivity(
                        launchIntent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                    )?.activityInfo
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.resolveActivity(launchIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
                }
            }.onFailure { error ->
                logWarning("ResolveActivity failed for $packageName: ${error.message}")
            }.getOrNull()
        }
    }

    private fun isDeviceLocked(context: Context): Boolean {
        if (LockStatusReceiver.currentLockStatus(context)) return true
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceLocked
            ?: keyguardManager?.isKeyguardLocked
            ?: false
    }

    private fun delegateUnlockToSystemAndQueueLaunch(
        context: Context,
        packageName: String,
        displayId: Int?
    ) {
        val appContext = context.applicationContext
        val keyguardManager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val unlockIntent = runCatching {
            keyguardManager?.createConfirmDeviceCredentialIntent(
                UNLOCK_PROMPT_TITLE,
                UNLOCK_PROMPT_DESCRIPTION
            )
        }.getOrNull()

        if (unlockIntent == null) {
            logWarning("Unable to request OS unlock challenge for package $packageName")
            return
        }

        synchronized(this) {
            pendingUnlockLaunch = PendingUnlockLaunch(
                packageName = packageName,
                displayId = displayId
            )
        }

        registerUnlockReceiverIfNeeded(appContext)

        val started = runCatching {
            unlockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(unlockIntent)
            true
        }.getOrElse { error ->
            logWarning("Failed to start OS unlock challenge for package $packageName: ${error.message}")
            false
        }

        if (!started) {
            synchronized(this) { pendingUnlockLaunch = null }
            unregisterUnlockReceiverIfNeeded(appContext)
        }
    }

    @Synchronized
    private fun registerUnlockReceiverIfNeeded(context: Context) {
        if (unlockReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_USER_UNLOCKED)
        }
        runCatching {
            context.registerReceiver(unlockCompletionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            unlockReceiverRegistered = true
        }.onFailure { error ->
            logWarning("Unable to register unlock completion receiver: ${error.message}")
        }
    }

    @Synchronized
    private fun unregisterUnlockReceiverIfNeeded(context: Context) {
        if (!unlockReceiverRegistered) return
        runCatching { context.unregisterReceiver(unlockCompletionReceiver) }
        unlockReceiverRegistered = false
    }

    private fun logWarning(message: String) {
        runCatching {
            Log.w(LOG_TAG, message)
        }
    }
}