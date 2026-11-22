package com.tyejaedon.coverscreenos.datastore

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import com.tyejaedon.coverscreenos.overlay.input.CoverKeyboardMode
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.math.max

/** Fixed number of quick-launch slots rendered in the cover-screen dock. */
const val COVER_DOCK_SLOT_COUNT = 4
const val MIN_PANEL_CORNER_RADIUS_DP = 0f
const val MAX_PANEL_CORNER_RADIUS_DP = 32f
const val DEFAULT_PANEL_CORNER_RADIUS_DP = 16f
/** Clamps [radiusDp] into `[MIN_PANEL_CORNER_RADIUS_DP, MAX_PANEL_CORNER_RADIUS_DP]`, falling back to default for non-finite values. */
internal fun normalizePanelCornerRadius(radiusDp: Float): Float =
	if (radiusDp.isFinite()) radiusDp.coerceIn(MIN_PANEL_CORNER_RADIUS_DP, MAX_PANEL_CORNER_RADIUS_DP)
	else DEFAULT_PANEL_CORNER_RADIUS_DP
const val MIN_WALLPAPER_DIM_AMOUNT = 0f
const val MAX_WALLPAPER_DIM_AMOUNT = 0.8f
const val DEFAULT_WALLPAPER_DIM_AMOUNT = 0.45f
const val MIN_WALLPAPER_BLUR_RADIUS_DP = 0f
const val MAX_WALLPAPER_BLUR_RADIUS_DP = 18f
const val DEFAULT_WALLPAPER_BLUR_RADIUS_DP = 0f
const val MIN_WALLPAPER_CROP_OFFSET = -1f
const val MAX_WALLPAPER_CROP_OFFSET = 1f
const val DEFAULT_WALLPAPER_CROP_OFFSET = 0f
const val MIN_WALLPAPER_ZOOM = 1f
const val MAX_WALLPAPER_ZOOM = 3f
const val DEFAULT_WALLPAPER_ZOOM = 1f
const val DEFAULT_WALLPAPER_ROTATION = 0
val DEFAULT_WALLPAPER_SCALE_MODE: WallpaperScaleMode = WallpaperScaleMode.CROP

/** Clamps a normalized horizontal or vertical wallpaper crop offset to `[-1f, 1f]`. */
internal fun normalizeWallpaperCropOffset(value: Float): Float =
	if (value.isFinite()) value.coerceIn(MIN_WALLPAPER_CROP_OFFSET, MAX_WALLPAPER_CROP_OFFSET)
	else DEFAULT_WALLPAPER_CROP_OFFSET

/** Clamps wallpaper zoom factor to `[MIN_WALLPAPER_ZOOM, MAX_WALLPAPER_ZOOM]`. */
internal fun normalizeWallpaperZoom(value: Float): Float =
	if (value.isFinite()) value.coerceIn(MIN_WALLPAPER_ZOOM, MAX_WALLPAPER_ZOOM)
	else DEFAULT_WALLPAPER_ZOOM

/** Normalizes any signed degree angle into a 90-degree clockwise step in `{0, 90, 180, 270}`. */
internal fun normalizeWallpaperRotation(value: Int): Int = ((value % 360) + 360) % 360 / 90 * 90

/** Controls how custom wallpaper media fills the cover-screen viewport bounds. */
enum class WallpaperScaleMode {
	/** Center-crop to fill the display without letterboxing. */
	CROP,
	/** Scale to fit completely inside the viewport bounds. */
	FIT
}

/** Cover-screen UI color-theme mode preference. */
enum class ThemePreference {
	SYSTEM,
	LIGHT,
	DARK
}

/** Accent color palette applied across Cover-Screen-OS UI surfaces and controls. */
enum class AccentColor {
	DEFAULT,
	MINT,
	AMBER,
	ROSE
}

/** User-facing haptic feedback intensity for the cover keyboard surfaces. */
enum class HapticStrength {
	Off,
	Light,
	Standard,
	Strong
}

/** Which side a one-handed-mode keyboard is anchored to. */
enum class KeyboardAnchor {
	Left,
	Right
}

/** Minimum/maximum extra key-height (on top of the auto-computed base height) a user can
 * drag-resize the keyboard to, in dp. The auto-computed `[44, 64]` range becomes the
 * floor; `MAX_EXTRA_KEY_HEIGHT_DP` on top of a 64dp base reaches the 88dp ceiling. */
const val MIN_EXTRA_KEY_HEIGHT_DP = 0f
const val MAX_EXTRA_KEY_HEIGHT_DP = 24f
const val COMPACT_EXTRA_KEY_HEIGHT_DP = 0f
const val STANDARD_EXTRA_KEY_HEIGHT_DP = 12f
const val TALL_EXTRA_KEY_HEIGHT_DP = 24f
/** Clamps extra keyboard key height in dp into `[MIN_EXTRA_KEY_HEIGHT_DP, MAX_EXTRA_KEY_HEIGHT_DP]`. */
internal fun normalizeExtraKeyHeightDp(extraDp: Float): Float =
	if (extraDp.isFinite()) extraDp.coerceIn(MIN_EXTRA_KEY_HEIGHT_DP, MAX_EXTRA_KEY_HEIGHT_DP)
	else COMPACT_EXTRA_KEY_HEIGHT_DP

/**
 * Process-wide Jetpack `preferencesDataStore` delegate persisted to
 * `files/datastore/cover_launcher_settings.preferences_pb`.
 */
private val Context.coverLauncherSettingsDataStore by preferencesDataStore(name = "cover_launcher_settings")

/**
 * Strongly-typed Jetpack DataStore [Preferences.Key] registry for launcher settings.
 *
 * Complex structures are encoded cleanly within flat primitive preferences:
 * - [dockSlots]: four indexed string keys (`dock_slot_0`..`dock_slot_3`).
 * - [keyboardModeByPackage]: JSON object mapping package names to [CoverKeyboardMode] names.
 * - [widgetIds]: comma-separated integer AppWidget IDs preserving user ordering.
 */
private object LauncherPreferencesKeys {
	val welcomeTourSeen = booleanPreferencesKey("welcome_tour_seen")
	val wallpaperUri = stringPreferencesKey("wallpaper_uri")
	val wallpaperScaleMode = stringPreferencesKey("wallpaper_scale_mode")
	val wallpaperDimAmount = floatPreferencesKey("wallpaper_dim_amount")
	val wallpaperBlurRadiusDp = floatPreferencesKey("wallpaper_blur_radius_dp")
	val wallpaperCropX = floatPreferencesKey("wallpaper_crop_x")
	val wallpaperCropY = floatPreferencesKey("wallpaper_crop_y")
	val wallpaperZoom = floatPreferencesKey("wallpaper_zoom")
	val wallpaperRotation = androidx.datastore.preferences.core.intPreferencesKey("wallpaper_rotation")
	val dockVisible = booleanPreferencesKey("dock_visible")
	val themePreference = stringPreferencesKey("theme_preference")
	val accentColor = stringPreferencesKey("accent_color")
	val panelCornerRadiusDp = floatPreferencesKey("panel_corner_radius_dp")
	val dockSlotFourAllApps = booleanPreferencesKey("dock_slot_four_all_apps")
	val keyboardStrategy = stringPreferencesKey("keyboard_strategy")
	val keyboardModeByPackage = stringPreferencesKey("keyboard_mode_by_package")
	val hapticStrength = stringPreferencesKey("haptic_strength")
	val keyPressSoundEnabled = booleanPreferencesKey("key_press_sound_enabled")
	val suggestionStripVisible = booleanPreferencesKey("suggestion_strip_visible")
	val autocorrectEnabled = booleanPreferencesKey("autocorrect_enabled")
	val popupKeyPreviewEnabled = booleanPreferencesKey("popup_key_preview_enabled")
	val keyboardExtraHeightDp = floatPreferencesKey("keyboard_extra_height_dp")
	val keyboardOneHandedEnabled = booleanPreferencesKey("keyboard_one_handed_enabled")
	val keyboardOneHandedAnchor = stringPreferencesKey("keyboard_one_handed_anchor")
	val widgetIds = stringPreferencesKey("cover_widget_ids")
	val dockSlots: List<Preferences.Key<String>> = List(COVER_DOCK_SLOT_COUNT) { slot ->
		stringPreferencesKey("dock_slot_$slot")
	}
}

/**
 * Immutable snapshot of all user-configurable cover-screen launcher, dock, wallpaper, appearance,
 * widget, and built-in keyboard preferences.
 */
data class LauncherSettings(
	val dockPackages: List<String?> = List(COVER_DOCK_SLOT_COUNT) { null },
	val wallpaperUri: String? = null,
	val wallpaperScaleMode: WallpaperScaleMode = DEFAULT_WALLPAPER_SCALE_MODE,
	val wallpaperDimAmount: Float = DEFAULT_WALLPAPER_DIM_AMOUNT,
	val wallpaperBlurRadiusDp: Float = DEFAULT_WALLPAPER_BLUR_RADIUS_DP,
	val wallpaperCropX: Float = DEFAULT_WALLPAPER_CROP_OFFSET,
	val wallpaperCropY: Float = DEFAULT_WALLPAPER_CROP_OFFSET,
	val wallpaperZoom: Float = DEFAULT_WALLPAPER_ZOOM,
	val wallpaperRotation: Int = DEFAULT_WALLPAPER_ROTATION,
	val isDockVisible: Boolean = true,
	val themePreference: ThemePreference = ThemePreference.SYSTEM,
	val accentColor: AccentColor = AccentColor.DEFAULT,
	val panelCornerRadiusDp: Float = DEFAULT_PANEL_CORNER_RADIUS_DP,
	val dockSlotFourAllApps: Boolean = false,
	val keyboardStrategy: KeyboardStrategy = DEFAULT_KEYBOARD_STRATEGY,
	val keyboardModeByPackage: Map<String, CoverKeyboardMode> = emptyMap(),
	val hapticStrength: HapticStrength = HapticStrength.Standard,
	val keyPressSoundEnabled: Boolean = false,
	val suggestionStripVisible: Boolean = true,
	val autocorrectEnabled: Boolean = true,
	val popupKeyPreviewEnabled: Boolean = true,
	val keyboardExtraHeightDp: Float = COMPACT_EXTRA_KEY_HEIGHT_DP,
	val keyboardOneHandedEnabled: Boolean = false,
	val keyboardOneHandedAnchor: KeyboardAnchor = KeyboardAnchor.Left,
	val widgetIds: List<Int> = emptyList()
)

private const val SETTINGS_STORE_LOG_TAG = "LauncherSettingsStore"
private const val MANAGED_WALLPAPER_DIR = "cover_wallpaper"
private const val MANAGED_WALLPAPER_FILE_PREFIX = "selected_wallpaper_"
private const val MANAGED_WALLPAPER_FILE_LEGACY = "selected_wallpaper"
private const val MANAGED_WALLPAPER_FALLBACK_EXTENSION = ".img"
private const val WALLPAPER_VALIDATION_TARGET_SIZE_PX = 512
/** Process-wide mutex serializing wallpaper file storage mutations and DataStore URI updates. */
private val wallpaperMutationMutex = Mutex()

/**
 * Atomically commits a validated wallpaper URI and runs post-commit cleanup inside [NonCancellable].
 *
 * Checks coroutine cancellation beforehand so a cancelled import aborts cleanly, then prevents
 * mid-commit cancellation from leaving DataStore pointing at an uncommitted file or skipping cleanup.
 */
internal suspend fun finalizeWallpaperImport(
	commit: suspend () -> Unit,
	afterCommit: () -> Unit
) {
	currentCoroutineContext().ensureActive()
	withContext(NonCancellable) {
		commit()
		afterCommit()
	}
}

/**
 * Best-effort `org.json` parser for per-package [CoverKeyboardMode] overrides, silently dropping
 * unrecognized enum values and returning an empty map if the stored JSON is missing or malformed.
 */
private fun decodeKeyboardModes(raw: String?): Map<String, CoverKeyboardMode> {
	if (raw.isNullOrBlank()) return emptyMap()
	return runCatching {
		val json = JSONObject(raw)
		json.keys().asSequence().mapNotNull { packageName ->
			CoverKeyboardMode.entries.firstOrNull { it.name == json.optString(packageName) }
				?.let { packageName to it }
		}.toMap()
	}.getOrDefault(emptyMap())
}

/**
 * Primary Jetpack DataStore `<Preferences>` repository for Cover-Screen-OS settings and managed
 * wallpaper media.
 *
 * Exposes reactive [Flow] streams ([settings], [welcomeTourSeen]) mapped from DataStore preferences,
 * JSON backup import/export via [LauncherSettingsJson], and mutex-serialized wallpaper file import,
 * binary/media validation, and legacy file migration on [ioDispatcher].
 *
 * @param context Android context; only [Context.getApplicationContext] is retained.
 * @param ioDispatcher Coroutine dispatcher used for DataStore mutations and disk/media I/O.
 */
class LauncherSettingsStore(
	context: Context,
	private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
	private val appContext = context.applicationContext

	/** Reactive stream indicating whether the user has completed the initial onboarding tour. */
	val welcomeTourSeen: Flow<Boolean> = appContext.coverLauncherSettingsDataStore.data
		.map { preferences -> preferences[LauncherPreferencesKeys.welcomeTourSeen] ?: false }

	/** Persists completion of the onboarding welcome tour in DataStore. */
	suspend fun markWelcomeTourSeen() {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				preferences[LauncherPreferencesKeys.welcomeTourSeen] = true
			}
		}
	}

	/**
	 * Reactive stream of validated [LauncherSettings] snapshots from Jetpack DataStore.
	 *
	 * On [IOException] or unexpected read errors, logs a warning and emits `emptyPreferences()`
	 * so downstream UI receives safe defaults; [CancellationException] is always re-thrown.
	 */
	val settings: Flow<LauncherSettings> = appContext.coverLauncherSettingsDataStore.data
		.catch { throwable ->
			if (throwable is CancellationException) throw throwable

			if (throwable is IOException) {
				Log.w(SETTINGS_STORE_LOG_TAG, "DataStore read failed. Falling back to defaults.", throwable)
			} else {
				Log.w(SETTINGS_STORE_LOG_TAG, "Unexpected DataStore error. Falling back to defaults.", throwable)
			}
			emit(emptyPreferences())
		}
		.map(::mapStoredPreferences)

	/**
	 * Maps raw DataStore [Preferences] into a sanitized [LauncherSettings] domain model,
	 * clamping out-of-range numbers, deduplicating dock slots, migrating legacy managed wallpaper
	 * paths, and falling back to default [LauncherSettings] on unexpected mapping failures.
	 */
	internal fun mapStoredPreferences(preferences: Preferences): LauncherSettings {
		return runCatching {
				val storedWallpaperUri = preferences[LauncherPreferencesKeys.wallpaperUri]
					?.trim()
					.takeUnless { it.isNullOrEmpty() }

				LauncherSettings(
					dockPackages = normalizeDockPackages(
						LauncherPreferencesKeys.dockSlots.map { key ->
							preferences[key]?.trim().takeUnless { it.isNullOrEmpty() }
						}
					),
					wallpaperUri = sanitizeWallpaperUri(storedWallpaperUri),
					wallpaperScaleMode = preferences[LauncherPreferencesKeys.wallpaperScaleMode]
						?.let { value ->
							WallpaperScaleMode.entries.firstOrNull { it.name == value }
						}
						?: DEFAULT_WALLPAPER_SCALE_MODE,
					wallpaperDimAmount = (preferences[LauncherPreferencesKeys.wallpaperDimAmount]
						?: DEFAULT_WALLPAPER_DIM_AMOUNT)
						.coerceIn(MIN_WALLPAPER_DIM_AMOUNT, MAX_WALLPAPER_DIM_AMOUNT),
					wallpaperBlurRadiusDp = (preferences[LauncherPreferencesKeys.wallpaperBlurRadiusDp]
						?: DEFAULT_WALLPAPER_BLUR_RADIUS_DP)
						.coerceIn(MIN_WALLPAPER_BLUR_RADIUS_DP, MAX_WALLPAPER_BLUR_RADIUS_DP),
					wallpaperCropX = normalizeWallpaperCropOffset(
						preferences[LauncherPreferencesKeys.wallpaperCropX] ?: DEFAULT_WALLPAPER_CROP_OFFSET
					),
					wallpaperCropY = normalizeWallpaperCropOffset(
						preferences[LauncherPreferencesKeys.wallpaperCropY] ?: DEFAULT_WALLPAPER_CROP_OFFSET
					),
					wallpaperZoom = normalizeWallpaperZoom(
						preferences[LauncherPreferencesKeys.wallpaperZoom] ?: DEFAULT_WALLPAPER_ZOOM
					),
					wallpaperRotation = normalizeWallpaperRotation(
						preferences[LauncherPreferencesKeys.wallpaperRotation] ?: DEFAULT_WALLPAPER_ROTATION
					),
					isDockVisible = preferences[LauncherPreferencesKeys.dockVisible] ?: true,
					themePreference = preferences[LauncherPreferencesKeys.themePreference]
						?.let { value -> ThemePreference.entries.firstOrNull { it.name == value } }
						?: ThemePreference.SYSTEM,
					accentColor = preferences[LauncherPreferencesKeys.accentColor]
						?.let { value -> AccentColor.entries.firstOrNull { it.name == value } }
						?: AccentColor.DEFAULT,
					panelCornerRadiusDp = preferences[LauncherPreferencesKeys.panelCornerRadiusDp]
						?.takeIf { it.isFinite() }
						?.coerceIn(MIN_PANEL_CORNER_RADIUS_DP, MAX_PANEL_CORNER_RADIUS_DP)
						?: DEFAULT_PANEL_CORNER_RADIUS_DP,
					dockSlotFourAllApps = preferences[LauncherPreferencesKeys.dockSlotFourAllApps] ?: false,
					keyboardStrategy = KeyboardStrategy.fromStorageValue(
						preferences[LauncherPreferencesKeys.keyboardStrategy]
					),
					keyboardModeByPackage = decodeKeyboardModes(
						preferences[LauncherPreferencesKeys.keyboardModeByPackage]
					),
					hapticStrength = preferences[LauncherPreferencesKeys.hapticStrength]
						?.let { value -> HapticStrength.entries.firstOrNull { it.name == value } }
						?: HapticStrength.Standard,
					keyPressSoundEnabled = preferences[LauncherPreferencesKeys.keyPressSoundEnabled] ?: false,
					suggestionStripVisible = preferences[LauncherPreferencesKeys.suggestionStripVisible] ?: true,
					autocorrectEnabled = preferences[LauncherPreferencesKeys.autocorrectEnabled] ?: true,
					popupKeyPreviewEnabled = preferences[LauncherPreferencesKeys.popupKeyPreviewEnabled] ?: true,
					keyboardExtraHeightDp = normalizeExtraKeyHeightDp(
						preferences[LauncherPreferencesKeys.keyboardExtraHeightDp] ?: COMPACT_EXTRA_KEY_HEIGHT_DP
					),
					keyboardOneHandedEnabled = preferences[LauncherPreferencesKeys.keyboardOneHandedEnabled] ?: false,
					keyboardOneHandedAnchor = preferences[LauncherPreferencesKeys.keyboardOneHandedAnchor]
						?.let { value -> KeyboardAnchor.entries.firstOrNull { it.name == value } }
						?: KeyboardAnchor.Left,
					widgetIds = preferences[LauncherPreferencesKeys.widgetIds]
						?.split(',')
						?.mapNotNull { it.trim().toIntOrNull() }
						?: emptyList()
				)
		}.getOrElse { error ->
			Log.w(SETTINGS_STORE_LOG_TAG, "Failed to map settings. Falling back to defaults.", error)
			LauncherSettings()
		}
	}

	/** Appends an Android AppWidget ID to the persisted cover widget list if not already present. */
	suspend fun addWidgetId(widgetId: Int) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				val existing = preferences[LauncherPreferencesKeys.widgetIds]
					?.split(',')
					?.mapNotNull { it.trim().toIntOrNull() }
					?.toMutableList() ?: mutableListOf()
				if (widgetId !in existing) {
					existing.add(widgetId)
					preferences[LauncherPreferencesKeys.widgetIds] = existing.joinToString(",")
				}
			}
		}
	}

	/** Removes an AppWidget ID from the persisted cover widget list if present. */
	suspend fun removeWidgetId(widgetId: Int) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				val existing = preferences[LauncherPreferencesKeys.widgetIds]
					?.split(',')
					?.mapNotNull { it.trim().toIntOrNull() }
					?.toMutableList() ?: mutableListOf()
				if (existing.remove(widgetId)) {
					preferences[LauncherPreferencesKeys.widgetIds] = existing.joinToString(",")
				}
			}
		}
	}

	/** Replaces the ordered list of persisted cover AppWidget IDs. */
	suspend fun setWidgetIds(widgetIds: List<Int>) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				preferences[LauncherPreferencesKeys.widgetIds] = widgetIds.joinToString(",")
			}
		}
	}

	/**
	 * Assigns or clears a dock slot (`0 until COVER_DOCK_SLOT_COUNT`), removing any duplicate
	 * occurrence of [packageName] from other slots so every docked app appears at most once.
	 */
	suspend fun setDockPackage(slotIndex: Int, packageName: String?) {
		require(slotIndex in 0 until COVER_DOCK_SLOT_COUNT) {
			"slotIndex must be between 0 and ${COVER_DOCK_SLOT_COUNT - 1}"
		}

		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				val normalizedPackage = packageName?.trim().takeUnless { it.isNullOrEmpty() }
				if (normalizedPackage != null) {
					LauncherPreferencesKeys.dockSlots.forEachIndexed { index, key ->
						if (index != slotIndex && preferences[key] == normalizedPackage) {
							preferences.remove(key)
						}
					}
				}

				val targetKey = LauncherPreferencesKeys.dockSlots[slotIndex]
				if (normalizedPackage == null) {
					preferences.remove(targetKey)
				} else {
					preferences[targetKey] = normalizedPackage
				}
			}
		}
	}

	/** Replaces all dock slot assignments atomically after normalizing length and uniqueness. */
	suspend fun setDockPackages(dockPackages: List<String?>) {
		withContext(ioDispatcher) {
			val normalized = normalizeDockPackages(dockPackages)
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				writeDockSlots(preferences = preferences, dockPackages = normalized)
			}
		}
	}

	/**
	 * Applies a full [LauncherSettings] snapshot atomically under [wallpaperMutationMutex].
	 *
	 * @param restoreKeyboardModes When `true` (e.g. JSON backup restore), overwrites persisted
	 * per-package keyboard mode overrides in addition to layout/appearance fields.
	 */
	suspend fun setLauncherLayout(settings: LauncherSettings, restoreKeyboardModes: Boolean = false) {
		wallpaperMutationMutex.withLock {
			withContext(ioDispatcher) {
				val normalizedDockPackages = normalizeDockPackages(settings.dockPackages)
				val normalizedWallpaperUri = resolveWallpaperUriForPersistedLayout(
					settings.wallpaperUri?.trim().takeUnless { it.isNullOrEmpty() }
				)
				val normalizedWallpaperDim = settings.wallpaperDimAmount
					.coerceIn(MIN_WALLPAPER_DIM_AMOUNT, MAX_WALLPAPER_DIM_AMOUNT)
				val normalizedWallpaperBlur = settings.wallpaperBlurRadiusDp
					.coerceIn(MIN_WALLPAPER_BLUR_RADIUS_DP, MAX_WALLPAPER_BLUR_RADIUS_DP)

				appContext.coverLauncherSettingsDataStore.edit { preferences ->
					writeDockSlots(preferences = preferences, dockPackages = normalizedDockPackages)

					if (normalizedWallpaperUri == null) {
						preferences.remove(LauncherPreferencesKeys.wallpaperUri)
					} else {
						preferences[LauncherPreferencesKeys.wallpaperUri] = normalizedWallpaperUri
					}

					preferences[LauncherPreferencesKeys.wallpaperScaleMode] = settings.wallpaperScaleMode.name
					preferences[LauncherPreferencesKeys.wallpaperDimAmount] = normalizedWallpaperDim
					preferences[LauncherPreferencesKeys.wallpaperBlurRadiusDp] = normalizedWallpaperBlur
					preferences[LauncherPreferencesKeys.wallpaperCropX] =
						normalizeWallpaperCropOffset(settings.wallpaperCropX)
					preferences[LauncherPreferencesKeys.wallpaperCropY] =
						normalizeWallpaperCropOffset(settings.wallpaperCropY)
					preferences[LauncherPreferencesKeys.wallpaperZoom] =
						normalizeWallpaperZoom(settings.wallpaperZoom)
					preferences[LauncherPreferencesKeys.wallpaperRotation] =
						normalizeWallpaperRotation(settings.wallpaperRotation)
					preferences[LauncherPreferencesKeys.dockVisible] = settings.isDockVisible
					preferences[LauncherPreferencesKeys.themePreference] = settings.themePreference.name
					preferences[LauncherPreferencesKeys.accentColor] = settings.accentColor.name
					preferences[LauncherPreferencesKeys.panelCornerRadiusDp] =
						normalizePanelCornerRadius(settings.panelCornerRadiusDp)
					preferences[LauncherPreferencesKeys.dockSlotFourAllApps] = settings.dockSlotFourAllApps
					preferences[LauncherPreferencesKeys.keyboardStrategy] = settings.keyboardStrategy.name
					if (restoreKeyboardModes) {
						preferences[LauncherPreferencesKeys.keyboardModeByPackage] =
							JSONObject(settings.keyboardModeByPackage.mapValues { it.value.name }).toString()
					}
				}
			}
		}
	}

	/** Serializes the current [settings] snapshot into a versioned JSON export string. */
	suspend fun exportSettingsJson(): String = LauncherSettingsJson.encode(settings.first())

	/**
	 * Validates and restores a JSON backup created by [exportSettingsJson].
	 *
	 * Verifies that any referenced wallpaper URI is readable and decodable on the current device
	 * before committing the restored layout to DataStore.
	 */
	suspend fun importSettingsJson(raw: String) {
		val imported = LauncherSettingsJson.decode(raw)
		val wallpaperUri = imported.wallpaperUri
		if (wallpaperUri != null) {
			require(wallpaperUri.isNotBlank()) { "Wallpaper URI is empty" }
			val uri = wallpaperUri.toUri()
			val readable = withContext(ioDispatcher) {
				if (isManagedWallpaperUri(uri)) {
					isManagedWallpaperFileReadableAndDecodable(uri)
				} else {
					isWallpaperUriReadableAndDecodable(uri)
				}
			}
			require(readable) { "Wallpaper image is unavailable on this device" }
		}
		setLauncherLayout(imported, restoreKeyboardModes = true)
	}

	/**
	 * Resolves and validates a wallpaper URI during layout restore, migrating legacy file locations
	 * and falling back to the latest managed wallpaper file (or `null`) if stale.
	 */
	private fun resolveWallpaperUriForPersistedLayout(rawWallpaperUri: String?): String? {
		if (rawWallpaperUri.isNullOrBlank()) return null

		val parsedUri = runCatching { rawWallpaperUri.toUri() }.getOrNull() ?: return null
		val migratedWallpaperUri = migrateLegacyManagedWallpaperUriIfNeeded(parsedUri) ?: rawWallpaperUri
		val migratedParsedUri = runCatching { migratedWallpaperUri.toUri() }.getOrNull() ?: return null

		if (isManagedWallpaperUri(migratedParsedUri)) {
			if (isManagedWallpaperFileReadableAndDecodable(migratedParsedUri)) {
				return migratedWallpaperUri
			}

			val recoveredWallpaperUri = resolveLatestManagedWallpaperUri()
			if (recoveredWallpaperUri != null) {
				Log.w(
					SETTINGS_STORE_LOG_TAG,
					"Launcher layout restore replaced stale wallpaper URI with latest managed wallpaper."
				)
				return recoveredWallpaperUri
			}

			Log.w(
				SETTINGS_STORE_LOG_TAG,
				"Launcher layout restore dropped stale managed wallpaper URI=$migratedWallpaperUri"
			)
			return null
		}

		return if (isWallpaperUriReadableAndDecodable(migratedParsedUri)) {
			migratedWallpaperUri
		} else {
			Log.w(
				SETTINGS_STORE_LOG_TAG,
				"Launcher layout restore dropped unreadable wallpaper URI=$migratedWallpaperUri"
			)
			null
		}
	}

	/** Shows or hides the bottom quick-launch dock on the cover homescreen. */
	suspend fun setDockVisible(isVisible: Boolean) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				preferences[LauncherPreferencesKeys.dockVisible] = isVisible
			}
		}
	}

	/** Updates the cover UI color-theme preference (`SYSTEM`, `LIGHT`, or `DARK`). */
	suspend fun setThemePreference(themePreference: ThemePreference) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				preferences[LauncherPreferencesKeys.themePreference] = themePreference.name
			}
		}
	}

	/** Updates the accent color palette applied across Cover-Screen-OS surfaces. */
	suspend fun setAccentColor(accentColor: AccentColor) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { it[LauncherPreferencesKeys.accentColor] = accentColor.name }
		}
	}

	/** Persists normalized corner rounding in dp for cover panels and cards. */
	suspend fun setPanelCornerRadiusDp(radiusDp: Float) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit {
				it[LauncherPreferencesKeys.panelCornerRadiusDp] = normalizePanelCornerRadius(radiusDp)
			}
		}
	}

	/** Toggles whether the 4th dock slot acts as an All Apps launcher button. */
	suspend fun setDockSlotFourAllApps(enabled: Boolean) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit {
				it[LauncherPreferencesKeys.dockSlotFourAllApps] = enabled
			}
		}
	}

	/** Selects between the built-in T9 keypad and the Android system IME for drawer search. */
	suspend fun setKeyboardStrategy(keyboardStrategy: KeyboardStrategy) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				preferences[LauncherPreferencesKeys.keyboardStrategy] = keyboardStrategy.name
			}
		}
	}

	/** Persists haptic feedback strength for keys on the cover keyboard. */
	suspend fun setHapticStrength(hapticStrength: HapticStrength) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				preferences[LauncherPreferencesKeys.hapticStrength] = hapticStrength.name
			}
		}
	}

	/** Enables or disables audible key-press feedback on the cover keyboard. */
	suspend fun setKeyPressSoundEnabled(enabled: Boolean) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit {
				it[LauncherPreferencesKeys.keyPressSoundEnabled] = enabled
			}
		}
	}

	/** Shows or hides the word suggestion strip above the built-in cover keyboard. */
	suspend fun setSuggestionStripVisible(visible: Boolean) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit {
				it[LauncherPreferencesKeys.suggestionStripVisible] = visible
			}
		}
	}

	/** Enables or disables automatic word correction on the built-in cover keyboard. */
	suspend fun setAutocorrectEnabled(enabled: Boolean) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit {
				it[LauncherPreferencesKeys.autocorrectEnabled] = enabled
			}
		}
	}

	/** Enables or disables enlarged key pop-up previews on touch. */
	suspend fun setPopupKeyPreviewEnabled(enabled: Boolean) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit {
				it[LauncherPreferencesKeys.popupKeyPreviewEnabled] = enabled
			}
		}
	}

	/** Persists clamped additional key height in dp above the automatic base height. */
	suspend fun setKeyboardExtraHeightDp(extraDp: Float) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit {
				it[LauncherPreferencesKeys.keyboardExtraHeightDp] = normalizeExtraKeyHeightDp(extraDp)
			}
		}
	}

	/** Enables or disables compact one-handed layout mode for the built-in cover keyboard. */
	suspend fun setKeyboardOneHandedEnabled(enabled: Boolean) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit {
				it[LauncherPreferencesKeys.keyboardOneHandedEnabled] = enabled
			}
		}
	}

	/** Anchors the one-handed keyboard layout to the [KeyboardAnchor.Left] or [KeyboardAnchor.Right] edge. */
	suspend fun setKeyboardOneHandedAnchor(anchor: KeyboardAnchor) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit {
				it[LauncherPreferencesKeys.keyboardOneHandedAnchor] = anchor.name
			}
		}
	}

	/**
	 * Remembers the user's preferred [CoverKeyboardMode] for a specific foreground [packageName]
	 * by updating the serialized JSON map stored in DataStore.
	 */
	suspend fun setKeyboardModeForPackage(packageName: String, mode: CoverKeyboardMode) {
		if (packageName.isBlank()) return
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				val modes = decodeKeyboardModes(preferences[LauncherPreferencesKeys.keyboardModeByPackage])
					.toMutableMap()
				modes[packageName] = mode
				preferences[LauncherPreferencesKeys.keyboardModeByPackage] =
					JSONObject(modes.mapValues { it.value.name }).toString()
			}
		}
	}

	/** Clears all remembered per-package cover keyboard mode overrides. */
	suspend fun clearSavedKeyboardModes() {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				preferences.remove(LauncherPreferencesKeys.keyboardModeByPackage)
			}
		}
	}

	/** Reorders a dock slot from [fromIndex] to [toIndex] while preserving slot uniqueness. */
	suspend fun moveDockPackage(fromIndex: Int, toIndex: Int) {
		require(fromIndex in 0 until COVER_DOCK_SLOT_COUNT) {
			"fromIndex must be between 0 and ${COVER_DOCK_SLOT_COUNT - 1}"
		}
		require(toIndex in 0 until COVER_DOCK_SLOT_COUNT) {
			"toIndex must be between 0 and ${COVER_DOCK_SLOT_COUNT - 1}"
		}
		if (fromIndex == toIndex) return

		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				val current = LauncherPreferencesKeys.dockSlots.map { key ->
					preferences[key]?.trim().takeUnless { it.isNullOrEmpty() }
				}.toMutableList()

				val movedItem = current.removeAt(fromIndex)
				current.add(toIndex, movedItem)

				val normalized = normalizeDockPackages(current)
				LauncherPreferencesKeys.dockSlots.forEachIndexed { index, key ->
					val packageName = normalized[index]
					if (packageName == null) {
						preferences.remove(key)
					} else {
						preferences[key] = packageName
					}
				}
			}
		}
	}

	/** Clears any app shortcut assigned to [slotIndex]. */
	suspend fun clearDockPackage(slotIndex: Int) {
		setDockPackage(slotIndex = slotIndex, packageName = null)
	}

	/**
	 * Validates and persists a wallpaper [uri] (or clears the wallpaper when `null`/blank).
	 *
	 * @return `true` if saved or cleared, `false` if the URI was unreadable or failed media decoding.
	 */
	suspend fun setWallpaperUri(uri: String?): Boolean {
		return wallpaperMutationMutex.withLock { setWallpaperUriUnlocked(uri) }
	}

	/** Internal unlocked implementation; callers must hold [wallpaperMutationMutex]. */
	private suspend fun setWallpaperUriUnlocked(uri: String?): Boolean {
		return withContext(ioDispatcher) {
			val normalizedUri = uri?.trim().takeUnless { it.isNullOrEmpty() }
			if (normalizedUri != null) {
				val parsedUri = normalizedUri.toUri()
				val uriReadableAndDecodable = if (isManagedWallpaperUri(parsedUri)) {
					isManagedWallpaperFileReadableAndDecodable(parsedUri)
				} else {
					isWallpaperUriReadableAndDecodable(parsedUri)
				}
				if (!uriReadableAndDecodable) {
					logWallpaperDiagnostics(stage = "set_wallpaper_uri_rejected", uri = parsedUri)
					Log.w(
						SETTINGS_STORE_LOG_TAG,
						"Wallpaper URI is not readable/decodable and was not saved: $normalizedUri"
					)
					return@withContext false
				}
			}

			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				if (normalizedUri == null) {
					preferences.remove(LauncherPreferencesKeys.wallpaperUri)
				} else {
					preferences[LauncherPreferencesKeys.wallpaperUri] = normalizedUri
				}
			}

			true
		}
	}

	/**
	 * Copies, validates, and activates a user-picked wallpaper (image, GIF, or video) from [sourceUri]
	 * into app-private storage so future restarts never depend on transient picker grants.
	 */
	suspend fun importWallpaperFromUri(sourceUri: Uri) {
		importWallpaper(sourceUri) { openUriInputStream(sourceUri) }
	}

	/** Test seam for importing a wallpaper directly from a caller-provided [InputStream]. */
	internal suspend fun importWallpaperFromStream(sourceUri: Uri, input: InputStream) {
		importWallpaper(sourceUri) { input }
	}

	/**
	 * Transactional wallpaper import pipeline guarded by [wallpaperMutationMutex]:
	 * 1. Requests persistable URI permission when applicable and clears stale `.part` staging files.
	 * 2. Streams into a `.part` temp file using bounded copy and `fd.sync()` durability flush.
	 * 3. Validates magic bytes, dimensions, frame rate/duration, and hardware/software decodability.
	 * 4. Renames atomically to a versioned file, commits DataStore inside [finalizeWallpaperImport],
	 *    and prunes superseded managed wallpaper files.
	 */
	private suspend fun importWallpaper(sourceUri: Uri, openInput: () -> InputStream?) {
		wallpaperMutationMutex.withLock {
			withContext(ioDispatcher) {
				takeSourceUriReadPermission(sourceUri)
				val wallpaperDirectory = File(appContext.filesDir, MANAGED_WALLPAPER_DIR)
				if (!wallpaperDirectory.exists() && !wallpaperDirectory.mkdirs()) {
					throw IOException("Could not create wallpaper storage.")
				}
				cleanInterruptedWallpaperImports(wallpaperDirectory)

				val wallpaperFile = buildVersionedManagedWallpaperFile(wallpaperDirectory, sourceUri)
				val tempWallpaperFile = File(wallpaperDirectory, "${wallpaperFile.name}.part")
				val input = openInput()
					?: throw IOException("Could not read the selected wallpaper. Choose a readable file.")
				var wallpaperCommitted = false
				try {
					input.use { source -> copyWallpaperImportStream(source, tempWallpaperFile) }

					validateWallpaperFile(tempWallpaperFile)
					currentCoroutineContext().ensureActive()
					if (!tempWallpaperFile.renameTo(wallpaperFile)) {
						throw IOException("Could not save the validated wallpaper.")
					}
					finalizeWallpaperImport(
						commit = {
							if (!setWallpaperUriUnlocked(Uri.fromFile(wallpaperFile).toString())) {
								throw IOException("Wallpaper validation failed. The previous wallpaper was kept.")
							}
						},
						afterCommit = {
							wallpaperCommitted = true
							pruneManagedWallpaperFiles(wallpaperDirectory, keepFileName = wallpaperFile.name)
						}
					)
					Log.d(SETTINGS_STORE_LOG_TAG, "Imported validated wallpaper (${wallpaperFile.length()} bytes)")
				} catch (error: Exception) {
					tempWallpaperFile.delete()
					if (!wallpaperCommitted) wallpaperFile.delete()
					if (error is CancellationException) throw error
					if (error is IOException || error is IllegalArgumentException) throw error
					throw IOException("Could not import this wallpaper. Choose a supported image, GIF, or video.", error)
				}
			}
		}
	}

	/**
	 * Streams [source] into [tempWallpaperFile] within [WallpaperMediaLimits.MAX_FILE_BYTES],
	 * flushing OS buffers via `fd.sync()` and deleting the partial file if copying fails or cancels.
	 */
	internal suspend fun copyWallpaperImportStream(source: InputStream, tempWallpaperFile: File) {
		try {
			FileOutputStream(tempWallpaperFile, false).use { output ->
				try {
					WallpaperMediaLimits.copyBounded(source, output)
				} catch (error: IllegalArgumentException) {
					val message = if (error.message == "Media file is empty") {
						"The selected wallpaper file is empty."
					} else {
						"Wallpaper is larger than ${WallpaperMediaLimits.MAX_FILE_BYTES / (1024 * 1024)} MB."
					}
					throw IOException(message, error)
				}
				output.flush()
				output.fd.sync()
			}
		} catch (error: Exception) {
			tempWallpaperFile.delete()
			if (error is CancellationException) throw error
			throw error
		}
	}

	/** Deletes leftover `.part` staging files from previously interrupted wallpaper imports. */
	private fun cleanInterruptedWallpaperImports(wallpaperDirectory: File) {
		val managedFiles = wallpaperDirectory.listFiles()
			?: throw IOException("Could not inspect wallpaper storage.")
		managedFiles
			.filter { it.isFile && it.name.startsWith(MANAGED_WALLPAPER_FILE_PREFIX) && it.name.endsWith(".part") }
			.forEach { partial ->
				if (!partial.delete()) throw IOException("Could not remove an interrupted wallpaper import.")
			}
	}

	/**
	 * Performs full format-specific verification on a staged wallpaper [file]:
	 * - [WallpaperMediaType.GIF]: binary block validation + [ImageDecoder.decodeDrawable] check.
	 * - [WallpaperMediaType.IMAGE]: two-pass [BitmapFactory] bounds check + downsampled decode check.
	 * - [WallpaperMediaType.VIDEO]: container/track/codec/timestamp/frame decode checks via [validateVideoFile].
	 */
	private fun validateWallpaperFile(file: File): WallpaperMediaType {
		require(file.isFile && file.canRead() && file.length() in 1..WallpaperMediaLimits.MAX_FILE_BYTES) {
			"Wallpaper must be a readable file no larger than ${WallpaperMediaLimits.MAX_FILE_BYTES / (1024 * 1024)} MB."
		}
		val type = detectWallpaperType(file)
		when (type) {
			WallpaperMediaType.GIF -> {
				file.inputStream().use { WallpaperMediaLimits.validateGif(it) }
				val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
				if (drawable is android.graphics.drawable.AnimatedImageDrawable) {
					drawable.stop()
				} else {
					throw IllegalArgumentException("GIF animation could not be decoded by this device.")
				}
			}
			WallpaperMediaType.IMAGE -> {
				// Pass 1: inspect dimensions with inJustDecodeBounds=true before allocating pixel memory.
				val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
				BitmapFactory.decodeFile(file.absolutePath, bounds)
				require(WallpaperMediaLimits.hasValidDimensions(bounds.outWidth, bounds.outHeight, animated = false)) {
					"Image dimensions exceed ${WallpaperMediaLimits.MAX_WIDTH}x${WallpaperMediaLimits.MAX_HEIGHT} or ${WallpaperMediaLimits.MAX_IMAGE_PIXELS / 1_000_000} megapixels."
				}
				// Pass 2: decode a subsampled ARGB_8888 thumbnail to confirm pixel data is intact.
				val options = BitmapFactory.Options().apply {
					inSampleSize = calculateValidationInSampleSize(
						bounds.outWidth,
						bounds.outHeight,
						WALLPAPER_VALIDATION_TARGET_SIZE_PX,
						WALLPAPER_VALIDATION_TARGET_SIZE_PX
					)
					inPreferredConfig = Bitmap.Config.ARGB_8888
				}
				val validationBitmap = BitmapFactory.decodeFile(file.absolutePath, options)
				require(validationBitmap != null) {
					"Image data is corrupt or unsupported."
				}
				validationBitmap.recycle()
			}
			WallpaperMediaType.VIDEO -> validateVideoFile(file)
		}
		return type
	}

	/** Sniffs the first 4 KB of [file] to classify it via [WallpaperMediaLimits.detectMediaType]. */
	private fun detectWallpaperType(file: File): WallpaperMediaType {
		return file.inputStream().use { stream ->
			WallpaperMediaLimits.detectMediaType(stream.readNBytes(4096))
		}
	}

	/**
	 * Validates video duration, resolution, codec, frame rate, frame count, and decodability using
	 * Android's [MediaMetadataRetriever] and [MediaExtractor], releasing both in `finally` blocks.
	 */
	private fun validateVideoFile(file: File) {
		val retriever = MediaMetadataRetriever()
		try {
			retriever.setDataSource(file.absolutePath)
			val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
				?: throw IllegalArgumentException("Video duration could not be read.")
			val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
			val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
			WallpaperMediaLimits.validateVideoDuration(duration)
			require(WallpaperMediaLimits.hasValidDimensions(width, height, animated = true)) {
				"Video dimensions exceed ${WallpaperMediaLimits.MAX_ANIMATED_WIDTH}x${WallpaperMediaLimits.MAX_ANIMATED_HEIGHT} or ${WallpaperMediaLimits.MAX_ANIMATED_PIXELS / 1_000_000} megapixels."
			}

			val extractor = MediaExtractor()
			try {
				extractor.setDataSource(file.absolutePath)
				val videoTrackIndex = (0 until extractor.trackCount)
					.firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
					?: throw IllegalArgumentException("Video has no supported video track.")
				val videoTrack = extractor.getTrackFormat(videoTrackIndex)
				val mime = videoTrack.getString(MediaFormat.KEY_MIME)
				WallpaperMediaLimits.validateVideoCodec(mime)
				val fps = if (videoTrack.containsKey(MediaFormat.KEY_FRAME_RATE))
					videoTrack.getInteger(MediaFormat.KEY_FRAME_RATE) else 0
				WallpaperMediaLimits.validateVideoFrameRate(fps)
				extractor.selectTrack(videoTrackIndex)
				// Walk sample timestamps (sorting to handle B-frame presentation reordering) to verify frame count & cadence.
				val frameTimestampsUs = ArrayList<Long>(WallpaperMediaLimits.MAX_FRAME_COUNT + 1)
				while (extractor.sampleTrackIndex >= 0) {
					frameTimestampsUs.add(extractor.sampleTime)
					require(frameTimestampsUs.size <= WallpaperMediaLimits.MAX_FRAME_COUNT) {
						"Video contains more than ${WallpaperMediaLimits.MAX_FRAME_COUNT} frames."
					}
					if (!extractor.advance()) break
				}
				WallpaperMediaLimits.validateFrameCount(frameTimestampsUs.size)
				val sortedTimestamps = frameTimestampsUs.sorted()
				sortedTimestamps.zipWithNext().forEach { (earlier, later) ->
					require(later - earlier >= 1_000_000L / WallpaperMediaLimits.MAX_FRAME_RATE) {
						"Video frame rate must be ${WallpaperMediaLimits.MAX_FRAME_RATE} fps or lower."
					}
				}
			} finally {
				extractor.release()
			}
			val validationFrame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
				?: throw IllegalArgumentException("Video cannot be decoded by this device.")
			validationFrame.recycle()
		} catch (error: IllegalArgumentException) {
			throw error
		} catch (error: Exception) {
			throw IllegalArgumentException("Video is corrupt or uses an unsupported codec.", error)
		} finally {
			retriever.release()
		}
	}

	/** Decoded pixel dimensions from a lightweight `inJustDecodeBounds` probe. */
	private data class BitmapBoundsProbe(
		val width: Int,
		val height: Int
	) {
		val isDecodable: Boolean
			get() = width > 0 && height > 0
	}

	/** Logs structured URI and dimension diagnostics when wallpaper validation fails. */
	private fun logWallpaperDiagnostics(
		stage: String,
		uri: Uri,
		boundsProbe: BitmapBoundsProbe? = null
	) {
		val boundsSummary = boundsProbe?.let { probe ->
			"bounds=${probe.width}x${probe.height} decodable=${probe.isDecodable}"
		} ?: "bounds=<unavailable>"
		Log.w(
			SETTINGS_STORE_LOG_TAG,
			"Wallpaper diagnostics stage=$stage ${wallpaperUriDiagnostics(uri)} $boundsSummary"
		)
	}

	/** Formats URI scheme and local file existence/readability metadata for log diagnostics. */
	private fun wallpaperUriDiagnostics(uri: Uri): String {
		val scheme = uri.scheme ?: "<none>"
		if (scheme != "file") {
			return "uri=$uri scheme=$scheme"
		}

		val path = uri.path
		if (path.isNullOrBlank()) {
			return "uri=$uri scheme=file path=<empty>"
		}

		val file = File(path)
		return "uri=$uri scheme=file exists=${file.exists()} isFile=${file.isFile} canRead=${file.canRead()} length=${file.length()}"
	}

	/** Probes bitmap dimensions via [BitmapFactory.Options.inJustDecodeBounds] without allocating pixels. */
	private fun probeBitmapBounds(openInputStream: () -> InputStream?): BitmapBoundsProbe? {
		return runCatching {
			openInputStream()?.use { stream ->
				val options = BitmapFactory.Options().apply {
					inJustDecodeBounds = true
				}
				BitmapFactory.decodeStream(stream, null, options)
				BitmapBoundsProbe(
					width = options.outWidth,
					height = options.outHeight
				)
			}
		}.getOrNull()
	}

	/** Updates how custom wallpaper media scales to the cover viewport (`CROP` vs `FIT`). */
	suspend fun setWallpaperScaleMode(scaleMode: WallpaperScaleMode) {
		wallpaperMutationMutex.withLock {
			withContext(ioDispatcher) {
				appContext.coverLauncherSettingsDataStore.edit { preferences ->
					preferences[LauncherPreferencesKeys.wallpaperScaleMode] = scaleMode.name
				}
			}
		}
	}

	/** Persists normalized pan offsets (`cropX`/`cropY`), zoom scale, and 90-degree rotation. */
	suspend fun setWallpaperCrop(cropX: Float, cropY: Float, zoom: Float, rotation: Int) {
		wallpaperMutationMutex.withLock {
			withContext(ioDispatcher) {
				appContext.coverLauncherSettingsDataStore.edit { preferences ->
					preferences[LauncherPreferencesKeys.wallpaperCropX] = normalizeWallpaperCropOffset(cropX)
					preferences[LauncherPreferencesKeys.wallpaperCropY] = normalizeWallpaperCropOffset(cropY)
					preferences[LauncherPreferencesKeys.wallpaperZoom] = normalizeWallpaperZoom(zoom)
					preferences[LauncherPreferencesKeys.wallpaperRotation] = normalizeWallpaperRotation(rotation)
				}
			}
		}
	}

	/** Persists normalized wallpaper rotation snapped to 90-degree steps (`0`, `90`, `180`, `270`). */
	suspend fun setWallpaperRotation(rotation: Int) {
		wallpaperMutationMutex.withLock {
			withContext(ioDispatcher) {
				appContext.coverLauncherSettingsDataStore.edit { preferences ->
					preferences[LauncherPreferencesKeys.wallpaperRotation] = normalizeWallpaperRotation(rotation)
				}
			}
		}
	}

	/** Sets the scrim dimming opacity overlaid on top of the wallpaper (`0.0f`..`0.8f`). */
	suspend fun setWallpaperDimAmount(dimAmount: Float) {
		val normalized = dimAmount.coerceIn(MIN_WALLPAPER_DIM_AMOUNT, MAX_WALLPAPER_DIM_AMOUNT)
		wallpaperMutationMutex.withLock {
			withContext(ioDispatcher) {
				appContext.coverLauncherSettingsDataStore.edit { preferences ->
					preferences[LauncherPreferencesKeys.wallpaperDimAmount] = normalized
				}
			}
		}
	}

	/** Sets the Gaussian blur radius in dp applied behind cover launcher panels (`0f`..`18f`). */
	suspend fun setWallpaperBlurRadiusDp(blurRadiusDp: Float) {
		val normalized = blurRadiusDp.coerceIn(MIN_WALLPAPER_BLUR_RADIUS_DP, MAX_WALLPAPER_BLUR_RADIUS_DP)
		wallpaperMutationMutex.withLock {
			withContext(ioDispatcher) {
				appContext.coverLauncherSettingsDataStore.edit { preferences ->
					preferences[LauncherPreferencesKeys.wallpaperBlurRadiusDp] = normalized
				}
			}
		}
	}

	/** Clears the active wallpaper URI preference and deletes managed wallpaper files from disk. */
	suspend fun clearWallpaper() {
		wallpaperMutationMutex.withLock {
			setWallpaperUriUnlocked(uri = null)
			withContext(ioDispatcher) {
				deleteManagedWallpaperFiles()
			}
		}
	}

	/**
	 * Resets dock shortcuts, wallpaper state, and keyboard strategy back to defaults while
	 * intentionally preserving theme preference.
	 */
	suspend fun resetLauncherLayout() {
		wallpaperMutationMutex.withLock {
			withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				writeDockSlots(
					preferences = preferences,
					dockPackages = List(COVER_DOCK_SLOT_COUNT) { null }
				)

				preferences.remove(LauncherPreferencesKeys.wallpaperUri)
				preferences[LauncherPreferencesKeys.wallpaperScaleMode] = DEFAULT_WALLPAPER_SCALE_MODE.name
				preferences[LauncherPreferencesKeys.wallpaperDimAmount] = DEFAULT_WALLPAPER_DIM_AMOUNT
				preferences[LauncherPreferencesKeys.wallpaperBlurRadiusDp] = DEFAULT_WALLPAPER_BLUR_RADIUS_DP
				preferences[LauncherPreferencesKeys.wallpaperCropX] = DEFAULT_WALLPAPER_CROP_OFFSET
				preferences[LauncherPreferencesKeys.wallpaperCropY] = DEFAULT_WALLPAPER_CROP_OFFSET
				preferences[LauncherPreferencesKeys.wallpaperZoom] = DEFAULT_WALLPAPER_ZOOM
				preferences[LauncherPreferencesKeys.wallpaperRotation] = DEFAULT_WALLPAPER_ROTATION
				preferences[LauncherPreferencesKeys.dockVisible] = true
				preferences.remove(LauncherPreferencesKeys.dockSlotFourAllApps)
				// Theme preference is intentionally preserved during layout reset.
				preferences[LauncherPreferencesKeys.keyboardStrategy] = DEFAULT_KEYBOARD_STRATEGY.name
				// Overlay host mode is a debug/developer toggle — intentionally
				// preserved across layout resets so QA doesn't get bounced back
				// onto the legacy path mid-dogfood.
			}

			deleteManagedWallpaperFiles()
		}
	}
	}

	/** Resets wallpaper URI, transform settings, and managed disk files without modifying dock or theme. */
	suspend fun resetWallpaperCustomization() {
		wallpaperMutationMutex.withLock {
			withContext(ioDispatcher) {
				appContext.coverLauncherSettingsDataStore.edit { preferences ->
					preferences.remove(LauncherPreferencesKeys.wallpaperUri)
					preferences[LauncherPreferencesKeys.wallpaperScaleMode] = DEFAULT_WALLPAPER_SCALE_MODE.name
					preferences[LauncherPreferencesKeys.wallpaperDimAmount] = DEFAULT_WALLPAPER_DIM_AMOUNT
					preferences[LauncherPreferencesKeys.wallpaperBlurRadiusDp] = DEFAULT_WALLPAPER_BLUR_RADIUS_DP
					preferences[LauncherPreferencesKeys.wallpaperCropX] = DEFAULT_WALLPAPER_CROP_OFFSET
					preferences[LauncherPreferencesKeys.wallpaperCropY] = DEFAULT_WALLPAPER_CROP_OFFSET
					preferences[LauncherPreferencesKeys.wallpaperZoom] = DEFAULT_WALLPAPER_ZOOM
					preferences[LauncherPreferencesKeys.wallpaperRotation] = DEFAULT_WALLPAPER_ROTATION
				}
				deleteManagedWallpaperFiles()
			}
		}
	}

	/** Clears all dock slots, restores dock visibility, and resets slot-4 All Apps mode. */
	suspend fun resetDockCustomization() {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				writeDockSlots(preferences, List(COVER_DOCK_SLOT_COUNT) { null })
				preferences[LauncherPreferencesKeys.dockVisible] = true
				preferences.remove(LauncherPreferencesKeys.dockSlotFourAllApps)
			}
		}
	}

	/** Resets theme mode, accent color, and panel corner radius back to defaults. */
	suspend fun resetAppearanceCustomization() {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				preferences.remove(LauncherPreferencesKeys.themePreference)
				preferences.remove(LauncherPreferencesKeys.accentColor)
				preferences.remove(LauncherPreferencesKeys.panelCornerRadiusDp)
			}
		}
	}

	/** Resets keyboard input strategy to [DEFAULT_KEYBOARD_STRATEGY] and clears per-package mode memory. */
	suspend fun resetInputCustomization() {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				preferences[LauncherPreferencesKeys.keyboardStrategy] = DEFAULT_KEYBOARD_STRATEGY.name
				preferences.remove(LauncherPreferencesKeys.keyboardModeByPackage)
			}
		}
	}

	/** Writes all [COVER_DOCK_SLOT_COUNT] dock slot keys into [preferences], removing cleared slots. */
	private fun writeDockSlots(
		preferences: MutablePreferences,
		dockPackages: List<String?>
	) {
		LauncherPreferencesKeys.dockSlots.forEachIndexed { index, key ->
			val packageName = dockPackages.getOrNull(index)
			if (packageName == null) {
				preferences.remove(key)
			} else {
				preferences[key] = packageName
			}
		}
	}

	/** Produces a fixed-size [COVER_DOCK_SLOT_COUNT] list with trimmed, non-blank, unique packages. */
	private fun normalizeDockPackages(raw: List<String?>): List<String?> {
		val normalized = MutableList<String?>(COVER_DOCK_SLOT_COUNT) { null }
		val seenPackages = linkedSetOf<String>()

		repeat(COVER_DOCK_SLOT_COUNT) { index ->
			val packageName = raw.getOrNull(index)?.trim().takeUnless { it.isNullOrEmpty() }
			if (packageName != null && seenPackages.add(packageName)) {
				normalized[index] = packageName
			}
		}

		return normalized
	}

	/**
	 * Validates a persisted wallpaper URI on read, migrating legacy app-files paths and recovering
	 * the newest managed file (or clearing to `null`) if the referenced file is missing or empty.
	 */
	private fun sanitizeWallpaperUri(storedUri: String?): String? {
		if (storedUri.isNullOrBlank()) return null

		val parsedUri = runCatching { storedUri.toUri() }.getOrNull() ?: return null
		val migratedUri = migrateLegacyManagedWallpaperUriIfNeeded(parsedUri)
		if (migratedUri != null) {
			return migratedUri
		}

		val effectiveUri = migratedUri ?: storedUri
		val effectiveParsedUri = runCatching { effectiveUri.toUri() }.getOrNull() ?: return null
		if (parsedUri.scheme != "file") return storedUri

		val path = effectiveParsedUri.path ?: return effectiveUri
		val file = File(path)
		if (file.exists() && file.isFile && file.length() > 0L) return effectiveUri

		if (isManagedWallpaperUri(effectiveParsedUri)) {
			val recoveredWallpaperUri = resolveLatestManagedWallpaperUri()
			if (recoveredWallpaperUri != null) {
				Log.w(
					SETTINGS_STORE_LOG_TAG,
					"Stored wallpaper URI is missing, recovered latest managed wallpaper file."
				)
				return recoveredWallpaperUri
			}

			Log.w(
				SETTINGS_STORE_LOG_TAG,
				"Managed wallpaper URI points to missing data and was cleared."
			)
			return null
		}

		Log.w(SETTINGS_STORE_LOG_TAG, "Stored wallpaper URI points to a missing file and was cleared.")
		return null
	}

	/** Generates a timestamped, UUID-suffixed destination file inside [wallpaperDirectory]. */
	private fun buildVersionedManagedWallpaperFile(
		wallpaperDirectory: File,
		sourceUri: Uri
	): File {
		val extension = extractWallpaperFileExtension(sourceUri)
		val timestamp = System.currentTimeMillis()
		val suffix = UUID.randomUUID().toString().take(8)
		return File(
			wallpaperDirectory,
			"${MANAGED_WALLPAPER_FILE_PREFIX}${timestamp}_$suffix$extension"
		)
	}

	/** Extracts a short alphanumeric file extension from [sourceUri], falling back to `.img`. */
	private fun extractWallpaperFileExtension(sourceUri: Uri): String {
		val segment = sourceUri.lastPathSegment ?: return MANAGED_WALLPAPER_FALLBACK_EXTENSION
		val dotIndex = segment.lastIndexOf('.')
		if (dotIndex < 0 || dotIndex == segment.lastIndex) {
			return MANAGED_WALLPAPER_FALLBACK_EXTENSION
		}

		val extension = segment.substring(dotIndex)
		return if (
			extension.length <= 10 &&
			extension.drop(1).all { it.isLetterOrDigit() }
		) {
			extension
		} else {
			MANAGED_WALLPAPER_FALLBACK_EXTENSION
		}
	}

	/** Deletes previously imported managed wallpaper files, optionally keeping [keepFileName]. */
	private fun pruneManagedWallpaperFiles(
		wallpaperDirectory: File,
		keepFileName: String?
	) {
		wallpaperDirectory.listFiles()?.forEach { file ->
			if (!file.isFile || !isManagedWallpaperFile(file)) return@forEach
			if (keepFileName != null && file.name == keepFileName) return@forEach

			runCatching { file.delete() }
				.onFailure { error ->
					Log.w(SETTINGS_STORE_LOG_TAG, "Unable to delete old wallpaper file ${file.name}: ${error.message}")
				}
		}
	}

	/** Removes all managed wallpaper files and deletes the directory if it becomes empty. */
	private fun deleteManagedWallpaperFiles() {
		val wallpaperDirectory = File(appContext.filesDir, MANAGED_WALLPAPER_DIR)
		if (!wallpaperDirectory.exists()) return

		pruneManagedWallpaperFiles(wallpaperDirectory = wallpaperDirectory, keepFileName = null)

		if (wallpaperDirectory.listFiles().isNullOrEmpty()) {
			runCatching { wallpaperDirectory.delete() }
		}
	}

	/** Returns true when [file] matches either the legacy or versioned managed wallpaper naming scheme. */
	private fun isManagedWallpaperFile(file: File): Boolean {
		return file.name == MANAGED_WALLPAPER_FILE_LEGACY ||
			file.name.startsWith(MANAGED_WALLPAPER_FILE_PREFIX)
	}

	/** Returns true if [uri] is a `file://` URI residing inside `filesDir/cover_wallpaper`. */
	private fun isManagedWallpaperUri(uri: Uri): Boolean {
		if (uri.scheme != "file") return false
		val path = uri.path ?: return false
		val file = File(path)
		val wallpaperDirectory = File(appContext.filesDir, MANAGED_WALLPAPER_DIR)
		return file.parentFile?.absolutePath == wallpaperDirectory.absolutePath
	}

	/** Finds the most recently modified non-empty managed wallpaper file on disk, if any. */
	private fun resolveLatestManagedWallpaperUri(): String? {
		val wallpaperDirectory = File(appContext.filesDir, MANAGED_WALLPAPER_DIR)
		if (!wallpaperDirectory.exists()) return null

		val latestManagedFile = wallpaperDirectory.listFiles()
			?.asSequence()
			?.filter { file -> file.isFile && file.length() > 0L && isManagedWallpaperFile(file) }
			?.maxByOrNull { file -> file.lastModified() }
			?: return null

		return Uri.fromFile(latestManagedFile).toString()
	}

	/**
	 * Migrates legacy managed wallpaper files previously saved directly under `appContext.filesDir`
	 * into the dedicated `filesDir/cover_wallpaper/` subdirectory after verifying decodability.
	 */
	private fun migrateLegacyManagedWallpaperUriIfNeeded(uri: Uri): String? {
		if (uri.scheme != "file") return null
		if (isManagedWallpaperUri(uri)) return null

		val path = uri.path ?: return null
		val legacyFile = File(path)
		if (!legacyFile.exists() || !legacyFile.isFile || !legacyFile.canRead() || legacyFile.length() <= 0L) {
			return null
		}

		if (!isManagedWallpaperFile(legacyFile)) {
			return null
		}

		val appFilesDir = appContext.filesDir
		if (legacyFile.parentFile?.absolutePath != appFilesDir.absolutePath) {
			return null
		}

		val wallpaperDirectory = File(appFilesDir, MANAGED_WALLPAPER_DIR)
		if (!wallpaperDirectory.exists() && !wallpaperDirectory.mkdirs()) {
			Log.w(SETTINGS_STORE_LOG_TAG, "Unable to create managed wallpaper directory for legacy migration.")
			return null
		}

		val migratedFile = buildVersionedManagedWallpaperFile(wallpaperDirectory, uri)
		val migrated = runCatching {
			legacyFile.copyTo(migratedFile, overwrite = true)
			if (!isManagedWallpaperFileReadableAndDecodable(migratedFile)) {
				migratedFile.delete()
				return@runCatching null
			}
			legacyFile.delete()
			Uri.fromFile(migratedFile).toString()
		}.getOrElse { error ->
			Log.w(
				SETTINGS_STORE_LOG_TAG,
				"Legacy wallpaper migration failed from ${legacyFile.absolutePath}: ${error.message}"
			)
			null
		}

		if (migrated != null) {
			Log.d(
				SETTINGS_STORE_LOG_TAG,
				"Migrated legacy wallpaper URI to managed directory: $migrated"
			)
		}

		return migrated
	}

	private fun isManagedWallpaperFileReadableAndDecodable(uri: Uri): Boolean {
		if (!isManagedWallpaperUri(uri)) return false
		val path = uri.path ?: return false
		return isManagedWallpaperFileReadableAndDecodable(File(path))
	}

	private fun isManagedWallpaperFileReadableAndDecodable(file: File): Boolean {
		return runCatching { validateWallpaperFile(file); true }
			.onFailure { error -> Log.w(SETTINGS_STORE_LOG_TAG, "Managed wallpaper failed media validation: ${error.message}") }
			.getOrDefault(false)
	}

	/** Attempts to persist read access across reboots for `content://` picker URIs. */
	private fun takeSourceUriReadPermission(sourceUri: Uri) {
		if (sourceUri.scheme != "content") return

		runCatching {
			appContext.contentResolver.takePersistableUriPermission(
				sourceUri,
				Intent.FLAG_GRANT_READ_URI_PERMISSION
			)
		}.onFailure {
			// Some providers do not expose persistable grants; import still proceeds with transient access.
		}
	}

	/** Verifies that an external `content://` or `file://` image URI has valid bounds and decodable pixels. */
	private fun isWallpaperUriReadableAndDecodable(uri: Uri): Boolean {
		return isBitmapDecodable {
			openUriInputStream(uri)
		} && canDecodeBitmapPixels(uri)
	}

	/**
	 * Performs a downsampled pixel decode check via [BitmapFactory] and falls back to Android's
	 * [ImageDecoder] (handling newer formats like HEIF/AVIF when [BitmapFactory] cannot decode them).
	 */
	private fun canDecodeBitmapPixels(uri: Uri): Boolean {
		val bounds = probeBitmapBounds {
			openUriInputStream(uri)
		} ?: return false
		if (!bounds.isDecodable) return false

		val sampleSize = calculateValidationInSampleSize(
			outWidth = bounds.width,
			outHeight = bounds.height,
			requestedWidthPx = WALLPAPER_VALIDATION_TARGET_SIZE_PX,
			requestedHeightPx = WALLPAPER_VALIDATION_TARGET_SIZE_PX
		)

		val bitmapFactoryDecoded = runCatching {
			openUriInputStream(uri)?.use { stream ->
				val decodeOptions = BitmapFactory.Options().apply {
					inSampleSize = sampleSize
					inPreferredConfig = Bitmap.Config.ARGB_8888
				}
				BitmapFactory.decodeStream(stream, null, decodeOptions) != null
			} ?: false
		}.getOrDefault(false)

		if (bitmapFactoryDecoded) {
			return true
		}

		val imageDecoderDecoded = decodeBitmapWithImageDecoder(uri)
		if (!imageDecoderDecoded) {
			Log.w(
				SETTINGS_STORE_LOG_TAG,
				"Wallpaper pixel decode validation failed uri=${wallpaperUriDiagnostics(uri)} bounds=${bounds.width}x${bounds.height} sampleSize=$sampleSize"
			)
		}
		return imageDecoderDecoded
	}

	/** Subsampled software-bitmap decode fallback using [ImageDecoder]. */
	private fun decodeBitmapWithImageDecoder(uri: Uri): Boolean {
		val source = when (uri.scheme) {
			"file" -> {
				val path = uri.path ?: return false
				val file = File(path)
				if (!file.exists() || !file.isFile || !file.canRead()) {
					return false
				}
				ImageDecoder.createSource(file)
			}

			else -> ImageDecoder.createSource(appContext.contentResolver, uri)
		}

		return runCatching {
			ImageDecoder.decodeBitmap(source) { decoder, imageInfo, _ ->
				decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
				val sampleSize = calculateValidationInSampleSize(
					outWidth = imageInfo.size.width,
					outHeight = imageInfo.size.height,
					requestedWidthPx = WALLPAPER_VALIDATION_TARGET_SIZE_PX,
					requestedHeightPx = WALLPAPER_VALIDATION_TARGET_SIZE_PX
				)
				if (sampleSize > 1) {
					decoder.setTargetSampleSize(sampleSize)
				}
			}
			true
		}.getOrDefault(false)
	}

	/** Computes an integer subsampling factor (`>= 1`) targeting [requestedWidthPx] x [requestedHeightPx]. */
	private fun calculateValidationInSampleSize(
		outWidth: Int,
		outHeight: Int,
		requestedWidthPx: Int,
		requestedHeightPx: Int
	): Int {
		if (outWidth <= 0 || outHeight <= 0) return 1

		val widthRatio = outWidth.toFloat() / requestedWidthPx.coerceAtLeast(1)
		val heightRatio = outHeight.toFloat() / requestedHeightPx.coerceAtLeast(1)
		return max(widthRatio, heightRatio).toInt().coerceAtLeast(1)
	}

	private fun isBitmapDecodable(openInputStream: () -> InputStream?): Boolean {
		return runCatching {
			openInputStream()?.use { stream ->
				val options = BitmapFactory.Options().apply {
					inJustDecodeBounds = true
				}
				BitmapFactory.decodeStream(stream, null, options)
				options.outWidth > 0 && options.outHeight > 0
			} ?: false
		}.getOrDefault(false)
	}

	/**
	 * Opens an [InputStream] for either a local `file://` path or a `content://` URI, falling
	 * back to [ParcelFileDescriptor.AutoCloseInputStream] if `openInputStream` fails.
	 */
	private fun openUriInputStream(uri: Uri): InputStream? {
		return when (uri.scheme) {
			"file" -> uri.path?.let { path ->
				runCatching { File(path).inputStream() }.getOrNull()
			}
			else -> {
				runCatching { appContext.contentResolver.openInputStream(uri) }.getOrNull()
					?: runCatching {
						appContext.contentResolver.openFileDescriptor(uri, "r")
							?.let { descriptor ->
								ParcelFileDescriptor.AutoCloseInputStream(descriptor)
							}
					}.getOrNull()
			}
		}
	}
}
