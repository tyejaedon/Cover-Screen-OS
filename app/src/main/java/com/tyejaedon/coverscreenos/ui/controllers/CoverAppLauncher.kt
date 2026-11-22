package com.tyejaedon.coverscreenos.ui.controllers

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.overlay.input.CoverInputAccessibilityService
import com.tyejaedon.coverscreenos.overlay.CoverAccessibilityService
import com.tyejaedon.coverscreenos.overlay.ForegroundService

/**
 * Central coordinator for launching apps and intents onto the secondary cover display.
 *
 * Handles:
 * - **Display targeting**: builds [ActivityOptions.makeBasic] with `launchDisplayId` so launched
 *   activities open on the cover display rather than [Display.DEFAULT_DISPLAY].
 * - **Lockscreen gating**: checks [KeyguardManager] and delegates locked-device requests to
 *   [UnlockBridgeActivity] before opening the target app.
 * - **Background-Activity-Launch (BAL) exemption**: routes starts through bound
 *   [AccessibilityService] contexts when invoked from background services.
 * - **Overlay focus handoff**: instructs [ForegroundService] to hide the cover launcher overlay
 *   once the target activity is dispatched.
 */
object CoverAppLauncher {

    // Logcat tag for launch routing, lockscreen handoff, and BAL-fallback diagnostics.
    private const val LOG_TAG = "CoverAppLauncher"

    /** Testable abstraction over the platform activity-launch step for a resolved display ID. */
    internal fun interface ActivityLaunchExecutor {
        fun launch(context: Context, launchIntent: Intent, launchDisplayId: Int)
    }

    // Standard launch path: bundles the target display ID into ActivityOptions and dispatches via BAL-exempt channels.
    private val defaultActivityLaunchExecutor = ActivityLaunchExecutor { context, launchIntent, launchDisplayId ->
        // Pin the launched Activity window to the resolved cover display via ActivityOptions.
        val launchOptions = ActivityOptions.makeBasic().apply {
            this.launchDisplayId = launchDisplayId
        }.toBundle()
        dispatchActivityStartBalExempt(context, launchIntent, launchOptions, launchDisplayId)
    }

    /**
     * Starts [launchIntent] through a background-activity-launch (BAL) exempt channel when
     * possible, falling back to a direct [Context.startActivity] otherwise.
     *
     * Any caller whose `context` is a Service (e.g. [ForegroundService], a notification
     * listener) is subject to BAL restrictions on modern Android: a plain `startActivity` call
     * from such a context can be silently discarded by the system while the device is locked or
     * the app has no visible UI. A connected AccessibilityService is one of the few contexts
     * still allowed to start activities from the background, so we prefer dispatching through
     * it whenever one is bound.
     */
    internal fun dispatchActivityStartBalExempt(
        context: Context,
        launchIntent: Intent,
        launchOptions: android.os.Bundle?,
        launchDisplayId: Int
    ) {
        // True once our primary input accessibility service accepts the launch, skipping secondary fallbacks.
        val dispatchedViaAccessibility = CoverInputAccessibilityService.startActivityFromAccessibilityService(
            launchIntent = launchIntent,
            launchOptions = launchOptions
        )

        if (dispatchedViaAccessibility) {
            logDebug("Launch dispatched via accessibility service displayId=$launchDisplayId")
        } else if (CoverAccessibilityService.startActivityFromLauncherService(
                launchIntent = launchIntent,
                launchOptions = launchOptions
            )
        ) {
            logDebug("Launch dispatched via launcher accessibility service displayId=$launchDisplayId")
        } else {
            if (!CoverInputAccessibilityService.isConnected()) {
                logWarning(
                    "Accessibility service not connected; falling back to direct startActivity. " +
                        "Launch may be blocked by background-activity-launch policy."
                )
            }
            context.startActivity(launchIntent, launchOptions)
            logDebug("Launch dispatched via direct startActivity displayId=$launchDisplayId")
        }
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

    /**
     * Launches the main launcher activity for [packageName] onto [displayId] (or the resolved
     * cover display when null) using [defaultActivityLaunchExecutor].
     */
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

    /**
     * Launches an arbitrary [Intent] directly onto [displayId] (or resolved cover display).
     * If device is locked, delegates to the unlock bridge.
     */
    fun launchIntentOnDisplay(
        context: Context,
        intent: Intent,
        displayId: Int? = null
    ): Boolean {
        return launchIntentOnDisplay(
            context = context,
            intent = intent,
            displayId = displayId,
            activityLaunchExecutor = defaultActivityLaunchExecutor
        )
    }

    /**
     * Internal [launchIntentOnDisplay] overload allowing test injection of [activityLaunchExecutor]
     * and [skipUnlockChallenge] bypass when called immediately after unlock.
     */
    internal fun launchIntentOnDisplay(
        context: Context,
        intent: Intent,
        displayId: Int? = null,
        activityLaunchExecutor: ActivityLaunchExecutor = defaultActivityLaunchExecutor,
        skipUnlockChallenge: Boolean = false
    ): Boolean {
        val targetDisplayId = resolveLaunchDisplayId(context = context, displayId = displayId)
        val packageName = runCatching { intent.`package` ?: intent.component?.packageName }.getOrNull()

        if (!skipUnlockChallenge && isDeviceLocked(context)) {
            logDebug("Device locked; delegating to unlock bridge for intent=$intent")
            val bridgeStarted = UnlockBridgeActivity.startUnlockRequest(
                context = context,
                packageName = packageName,
                displayId = targetDisplayId
            )
            if (!bridgeStarted) {
                logWarning("Unlock bridge unavailable; intent launch deferred")
            }
            return false
        }

        val launchIntent = runCatching { Intent(intent) }.getOrDefault(intent).apply {
            runCatching { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }

        return try {
            activityLaunchExecutor.launch(context, launchIntent, targetDisplayId)
            true
        } catch (e: Exception) {
            logWarning("Failed to launch intent on display $targetDisplayId: ${e.message}")
            false
        }
    }

    /**
     * Resumes a deferred [packageName] launch on [displayId] from [UnlockBridgeActivity],
     * passing `skipUnlockChallenge = true` to avoid re-entering the unlock gate while keyguard
     * dismissal is still settling.
     */
    internal fun launchPackageOnDisplayAfterUnlock(
        context: Context,
        packageName: String,
        displayId: Int?
    ): Boolean {
        return launchPackageOnDisplay(
            context = context,
            packageName = packageName,
            displayId = displayId,
            activityLaunchExecutor = defaultActivityLaunchExecutor,
            skipUnlockChallenge = true
        )
    }

    /**
     * Validates lock state, package visibility, component enabled/exported flags, and launches
     * [packageName] onto the target display, hiding the cover overlay on success.
     */
    internal fun launchPackageOnDisplay(
        context: Context,
        packageName: String,
        displayId: Int?,
        activityLaunchExecutor: ActivityLaunchExecutor,
        skipUnlockChallenge: Boolean = false
    ): Boolean {
        logDebug(
            "Launch requested package=$packageName displayId=$displayId " +
                "accessibilityConnected=${CoverInputAccessibilityService.isConnected()}"
        )
        if (!skipUnlockChallenge && isDeviceLocked(context)) {
            logDebug("Device locked; delegating to unlock bridge package=$packageName")
            val bridgeStarted = UnlockBridgeActivity.startUnlockRequest(
                context = context,
                packageName = packageName,
                displayId = displayId
            )
            if (!bridgeStarted) {
                logWarning("Unlock bridge unavailable; launch request deferred package=$packageName")
            }
            return false
        }

        // Entry launcher Intent resolved by PackageManager for the target app.
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            logWarning("No launch intent found for package $packageName")
            return false
        }

        // Component metadata used to detect package-visibility filtering and disabled/private activities.
        val launchActivityInfo = resolveLaunchActivityInfo(
            context = context,
            packageName = packageName,
            launchIntent = launchIntent
        )
        if (launchActivityInfo == null) {
            logWarning(
                "Launch blocked because no activity info resolved for package $packageName " +
                    "(component=${launchIntent.component?.flattenToShortString()}). " +
                    "This is usually package-visibility filtering."
            )
            return false
        }

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

        // Final numeric display ID (cover display or fallback) passed to ActivityOptions.
        val launchDisplayId = resolveLaunchDisplayId(context = context, displayId = displayId)

        return try {
            // Dispatch before suppressing the launcher so the target takes focus cleanly.
            activityLaunchExecutor.launch(context, launchIntent, launchDisplayId)

            // Suppress the overlay UI so the launched cover app receives input focus.
            val hideIntent = ForegroundService.createHideOverlayIntent(context, packageName)
            context.startService(hideIntent)
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

    /**
     * Resolves the destination display ID in priority order: explicit valid [displayId],
     * [Context.getDisplay] (for display-scoped contexts), or [DisplayManager] default display.
     */
    internal fun resolveLaunchDisplayId(context: Context, displayId: Int?): Int {
        if (displayId != null && displayId != Display.INVALID_DISPLAY) {
            return displayId
        }

        // Display ID attached directly to a display-scoped Context (such as the cover overlay window).
        val contextDisplayId = runCatching {
            context.display.displayId
        }.getOrNull()

        if (contextDisplayId != null && contextDisplayId != Display.INVALID_DISPLAY) {
            return contextDisplayId
        }

        // Last-resort DisplayManager lookup for Display.DEFAULT_DISPLAY if no cover/context display is set.
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        return defaultDisplay?.displayId ?: Display.DEFAULT_DISPLAY
    }

    /** Resolves target [android.content.pm.ActivityInfo] under `MATCH_DEFAULT_ONLY` visibility rules. */
    private fun resolveLaunchActivityInfo(
        context: Context,
        packageName: String,
        launchIntent: Intent
    ): android.content.pm.ActivityInfo? {
        return runCatching {
            launchIntent.resolveActivityInfo(context.packageManager, PackageManager.MATCH_DEFAULT_ONLY)
        }.onFailure { error ->
            logWarning("ResolveActivityInfo failed for $packageName: ${error.message}")
        }.getOrNull()
    }

    /** Returns true if [KeyguardManager] reports the device or keyguard is currently locked. */
    private fun isDeviceLocked(context: Context): Boolean {
        // Android's lockscreen service, checked before launching so locked taps route via UnlockBridgeActivity.
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceLocked
            ?: keyguardManager?.isKeyguardLocked
            ?: false
    }


    private fun logWarning(message: String) {
        runCatching {
            Log.w(LOG_TAG, message)
        }
    }

    private fun logDebug(message: String) {
        runCatching {
            Log.d(LOG_TAG, message)
        }
    }
}