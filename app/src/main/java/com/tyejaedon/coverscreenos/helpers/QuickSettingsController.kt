package com.tyejaedon.coverscreenos.helpers

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.tyejaedon.coverscreenos.services.overlay.ForegroundService
import kotlin.math.roundToInt

/**
 * Platform plumbing behind the Quick Settings overlay (WiFi, Bluetooth, flashlight,
 * brightness). Kept free of Compose so it can be unit tested and reused by both the
 * cover overlay and, eventually, the in-app settings surfaces.
 */
object QuickSettingsController {

    private const val LOG_TAG = "QuickSettingsController"
    private const val MIN_BRIGHTNESS = 0
    private const val MAX_BRIGHTNESS = 255

    /** Outcome of a best-effort system toggle attempt. */
    sealed class ToggleOutcome {
        /** The platform applied the change directly - no UI left the overlay. */
        data object Changed : ToggleOutcome()

        /** Android blocked the direct call; the matching system settings screen was opened. */
        data object OpenedSystemSettings : ToggleOutcome()

        /** Neither the direct call nor the settings fallback could be attempted. */
        data object Failed : ToggleOutcome()
    }

    // ---------------------------------------------------------------------
    // WiFi
    // ---------------------------------------------------------------------

    fun isWifiEnabled(context: Context): Boolean {
        val wifiManager = wifiManager(context) ?: return false
        return runCatching { wifiManager.isWifiEnabled }.getOrDefault(false)
    }

    /**
     * Android 10+ (API 29+) blocks apps from silently flipping WiFi via
     * [WifiManager.setWifiEnabled] - the call simply returns `false`. We still attempt it
     * (harmless, and correct on the rare OEM/API combination where it works) and fall back
     * to the system WiFi settings screen so the user can finish the toggle themselves.
     */
    fun toggleWifi(context: Context): ToggleOutcome {
        val wifiManager = wifiManager(context) ?: return openSettingsOrFail(context, createWifiSettingsIntent())
        val targetEnabled = !isWifiEnabled(context)
        val applied = runCatching {
            @Suppress("DEPRECATION")
            wifiManager.setWifiEnabled(targetEnabled)
        }.getOrDefault(false)

        return if (applied) ToggleOutcome.Changed else openSettingsOrFail(context, createWifiSettingsIntent())
    }

    fun createWifiSettingsIntent(): Intent = Intent(Settings.ACTION_WIFI_SETTINGS)

    private fun wifiManager(context: Context): WifiManager? {
        return context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    // ---------------------------------------------------------------------
    // Bluetooth
    // ---------------------------------------------------------------------

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        if (!hasBluetoothConnectPermission(context)) return false
        val adapter = bluetoothAdapter(context) ?: return false
        return runCatching { adapter.isEnabled }.getOrDefault(false)
    }

    /**
     * Android 13+ requires BLUETOOTH_CONNECT to even query/enable the adapter, and
     * [BluetoothAdapter.enable]/[BluetoothAdapter.disable] are deprecated - still functional
     * when the permission is held, but may be refused by OEM policy. We try the direct call
     * first and fall back to system Bluetooth settings whenever it is unavailable or refused.
     */
    fun toggleBluetooth(context: Context): ToggleOutcome {
        if (!hasBluetoothConnectPermission(context)) {
            return openSettingsOrFail(context, createBluetoothSettingsIntent())
        }
        val adapter = bluetoothAdapter(context)
            ?: return openSettingsOrFail(context, createBluetoothSettingsIntent())

        val targetEnabled = !isBluetoothEnabled(context)
        val applied = runCatching {
            @Suppress("DEPRECATION", "MissingPermission")
            if (targetEnabled) adapter.enable() else adapter.disable()
        }.getOrDefault(false)

        return if (applied) ToggleOutcome.Changed else openSettingsOrFail(context, createBluetoothSettingsIntent())
    }

    fun createBluetoothSettingsIntent(): Intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)

    private fun bluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    // ---------------------------------------------------------------------
    // Flashlight / torch
    // ---------------------------------------------------------------------

    fun cameraManager(context: Context): CameraManager? {
        return context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    /** First rear-facing camera that reports a flash unit, or null if the device has none. */
    fun resolveTorchCameraId(context: Context): String? {
        val cameraManager = cameraManager(context) ?: return null
        return runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrElse { error ->
            Log.w(LOG_TAG, "Unable to resolve torch camera: ${error.message}")
            null
        }
    }

    fun setTorchEnabled(context: Context, cameraId: String, enabled: Boolean): Boolean {
        val cameraManager = cameraManager(context) ?: return false
        return runCatching {
            cameraManager.setTorchMode(cameraId, enabled)
            true
        }.getOrElse { error ->
            Log.w(LOG_TAG, "Torch toggle failed: ${error.message}")
            false
        }
    }

    // ---------------------------------------------------------------------
    // Brightness
    // ---------------------------------------------------------------------

    fun canWriteSystemSettings(context: Context): Boolean {
        return Settings.System.canWrite(context.applicationContext)
    }

    fun createWriteSettingsPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    }

    /** Current screen brightness as a 0f..1f fraction. */
    fun getBrightnessPercent(context: Context): Float {
        val raw = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(MAX_BRIGHTNESS / 2)
        return (raw.toFloat() / MAX_BRIGHTNESS).coerceIn(0f, 1f)
    }

    /** Requires [canWriteSystemSettings] to be true; returns false otherwise (no-op). */
    fun setBrightnessPercent(context: Context, percent: Float): Boolean {
        if (!canWriteSystemSettings(context)) return false
        val value = (percent.coerceIn(0f, 1f) * MAX_BRIGHTNESS).roundToInt()
            .coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)

        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
            true
        }.getOrElse { error ->
            Log.w(LOG_TAG, "Brightness write failed: ${error.message}")
            false
        }
    }

    // ---------------------------------------------------------------------
    // Settings launch (overlay runs on a non-Activity context, mirrors
    // CoverAppLauncher's suppress-then-launch pattern so the overlay hides cleanly
    // while the system settings screen is on top and resumes once it is dismissed).
    // ---------------------------------------------------------------------

    private fun openSettingsOrFail(context: Context, intent: Intent): ToggleOutcome {
        return if (launchSettingsIntent(context, intent)) {
            ToggleOutcome.OpenedSystemSettings
        } else {
            ToggleOutcome.Failed
        }
    }

    fun launchSettingsIntent(context: Context, intent: Intent): Boolean {
        return runCatching {
            val targetPackage = runCatching {
                context.packageManager
                    .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo
                    ?.packageName
            }.getOrNull() ?: "com.android.settings"

            runCatching {
                context.startService(ForegroundService.createHideOverlayIntent(context, targetPackage))
            }

            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            context.startActivity(intent)
            true
        }.getOrElse { error ->
            Log.w(LOG_TAG, "Unable to launch settings intent ${intent.action}: ${error.message}")
            false
        }
    }
}
