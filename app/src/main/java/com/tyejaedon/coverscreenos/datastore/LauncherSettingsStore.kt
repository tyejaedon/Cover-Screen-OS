package com.tyejaedon.coverscreenos.datastore

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
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
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.math.max

const val COVER_DOCK_SLOT_COUNT = 4
const val MIN_WALLPAPER_DIM_AMOUNT = 0f
const val MAX_WALLPAPER_DIM_AMOUNT = 0.8f
const val DEFAULT_WALLPAPER_DIM_AMOUNT = 0.45f
const val MIN_WALLPAPER_BLUR_RADIUS_DP = 0f
const val MAX_WALLPAPER_BLUR_RADIUS_DP = 18f
const val DEFAULT_WALLPAPER_BLUR_RADIUS_DP = 0f
val DEFAULT_WALLPAPER_SCALE_MODE: WallpaperScaleMode = WallpaperScaleMode.CROP

enum class WallpaperScaleMode {
	CROP,
	FIT
}

enum class ThemePreference {
	SYSTEM,
	LIGHT,
	DARK
}

enum class SearchInputMode {
	T9,
	SYSTEM_IME
}

private val Context.coverLauncherSettingsDataStore by preferencesDataStore(name = "cover_launcher_settings")

private object LauncherPreferencesKeys {
	val wallpaperUri = stringPreferencesKey("wallpaper_uri")
	val wallpaperScaleMode = stringPreferencesKey("wallpaper_scale_mode")
	val wallpaperDimAmount = floatPreferencesKey("wallpaper_dim_amount")
	val wallpaperBlurRadiusDp = floatPreferencesKey("wallpaper_blur_radius_dp")
	val dockVisible = booleanPreferencesKey("dock_visible")
	val themePreference = stringPreferencesKey("theme_preference")
	val searchInputMode = stringPreferencesKey("search_input_mode")
	val dockSlots: List<Preferences.Key<String>> = List(COVER_DOCK_SLOT_COUNT) { slot ->
		stringPreferencesKey("dock_slot_$slot")
	}
}

data class LauncherSettings(
	val dockPackages: List<String?> = List(COVER_DOCK_SLOT_COUNT) { null },
	val wallpaperUri: String? = null,
	val wallpaperScaleMode: WallpaperScaleMode = DEFAULT_WALLPAPER_SCALE_MODE,
	val wallpaperDimAmount: Float = DEFAULT_WALLPAPER_DIM_AMOUNT,
	val wallpaperBlurRadiusDp: Float = DEFAULT_WALLPAPER_BLUR_RADIUS_DP,
	val isDockVisible: Boolean = true,
	val themePreference: ThemePreference = ThemePreference.SYSTEM,
	val searchInputMode: SearchInputMode = SearchInputMode.T9
)

private const val SETTINGS_STORE_LOG_TAG = "LauncherSettingsStore"
private const val MANAGED_WALLPAPER_DIR = "cover_wallpaper"
private const val MANAGED_WALLPAPER_FILE_PREFIX = "selected_wallpaper_"
private const val MANAGED_WALLPAPER_FILE_LEGACY = "selected_wallpaper"
private const val MANAGED_WALLPAPER_FALLBACK_EXTENSION = ".img"
private const val WALLPAPER_VALIDATION_TARGET_SIZE_PX = 512

class LauncherSettingsStore(
	context: Context,
	private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
	private val appContext = context.applicationContext

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
		.map { preferences ->
			runCatching {
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
					isDockVisible = preferences[LauncherPreferencesKeys.dockVisible] ?: true,
					themePreference = preferences[LauncherPreferencesKeys.themePreference]
						?.let { value -> ThemePreference.entries.firstOrNull { it.name == value } }
						?: ThemePreference.SYSTEM,
					searchInputMode = preferences[LauncherPreferencesKeys.searchInputMode]
						?.let { value -> SearchInputMode.entries.firstOrNull { it.name == value } }
						?: SearchInputMode.T9
				)
			}.getOrElse { error ->
				Log.w(SETTINGS_STORE_LOG_TAG, "Failed to map settings. Falling back to defaults.", error)
				LauncherSettings()
			}
		}

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

	suspend fun setDockPackages(dockPackages: List<String?>) {
		withContext(ioDispatcher) {
			val normalized = normalizeDockPackages(dockPackages)
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				writeDockSlots(preferences = preferences, dockPackages = normalized)
			}
		}
	}

	suspend fun setLauncherLayout(settings: LauncherSettings) {
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
				preferences[LauncherPreferencesKeys.dockVisible] = settings.isDockVisible
				preferences[LauncherPreferencesKeys.themePreference] = settings.themePreference.name
				preferences[LauncherPreferencesKeys.searchInputMode] = settings.searchInputMode.name
			}
		}
	}

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

	suspend fun setDockVisible(isVisible: Boolean) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				preferences[LauncherPreferencesKeys.dockVisible] = isVisible
			}
		}
	}

	suspend fun setThemePreference(themePreference: ThemePreference) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				preferences[LauncherPreferencesKeys.themePreference] = themePreference.name
			}
		}
	}

	suspend fun setSearchInputMode(searchInputMode: SearchInputMode) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				preferences[LauncherPreferencesKeys.searchInputMode] = searchInputMode.name
			}
		}
	}

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

	suspend fun clearDockPackage(slotIndex: Int) {
		setDockPackage(slotIndex = slotIndex, packageName = null)
	}

	suspend fun setWallpaperUri(uri: String?): Boolean {
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

	suspend fun importWallpaperFromUri(sourceUri: Uri): Boolean {
		return withContext(ioDispatcher) {
			takeSourceUriReadPermission(sourceUri)

			val sourceBounds = probeBitmapBounds {
				openUriInputStream(sourceUri)
			}
			val isDecodableImage = sourceBounds?.isDecodable == true
			if (!isDecodableImage) {
				logWallpaperDiagnostics(
					stage = "source_not_decodable",
					uri = sourceUri,
					boundsProbe = sourceBounds
				)
				Log.w(SETTINGS_STORE_LOG_TAG, "Wallpaper import failed: source is not a decodable image URI $sourceUri")
				return@withContext false
			}

			val sourceStream = openUriInputStream(sourceUri)
			if (sourceStream == null) {
				logWallpaperDiagnostics(
					stage = "source_stream_unavailable",
					uri = sourceUri,
					boundsProbe = sourceBounds
				)
				Log.w(SETTINGS_STORE_LOG_TAG, "Wallpaper import failed: unable to open source URI $sourceUri")
				return@withContext false
			}

			val wallpaperDirectory = File(appContext.filesDir, MANAGED_WALLPAPER_DIR)
			if (!wallpaperDirectory.exists() && !wallpaperDirectory.mkdirs()) {
				Log.w(SETTINGS_STORE_LOG_TAG, "Wallpaper import failed: unable to create wallpaper directory")
				return@withContext false
			}

			val wallpaperFile = buildVersionedManagedWallpaperFile(wallpaperDirectory, sourceUri)
			val tempWallpaperFile = File(wallpaperDirectory, "${wallpaperFile.name}.part")
			val copied = runCatching {
				sourceStream.use { input ->
					FileOutputStream(tempWallpaperFile, false).use { output ->
						input.copyTo(output)
						output.flush()
					}
				}

				if (!tempWallpaperFile.exists() || tempWallpaperFile.length() <= 0L) {
					Log.w(SETTINGS_STORE_LOG_TAG, "Wallpaper import failed: copied file is empty")
					tempWallpaperFile.delete()
					return@runCatching false
				}

				if (!tempWallpaperFile.renameTo(wallpaperFile)) {
					tempWallpaperFile.copyTo(wallpaperFile, overwrite = true)
					tempWallpaperFile.delete()
				}

				if (!wallpaperFile.exists() || wallpaperFile.length() <= 0L) {
					Log.w(SETTINGS_STORE_LOG_TAG, "Wallpaper import failed: destination file is empty")
					wallpaperFile.delete()
					return@runCatching false
				}

				if (!isManagedWallpaperFileReadableAndDecodable(wallpaperFile)) {
					logWallpaperDiagnostics(
						stage = "destination_not_decodable",
						uri = Uri.fromFile(wallpaperFile),
						boundsProbe = probeBitmapBounds {
							runCatching { wallpaperFile.inputStream() }.getOrNull()
						}
					)
					Log.w(
						SETTINGS_STORE_LOG_TAG,
						"Wallpaper import failed: destination file is not decodable ${wallpaperFile.absolutePath}"
					)
					wallpaperFile.delete()
					return@runCatching false
				}
				true
			}.getOrElse { error ->
				Log.w(SETTINGS_STORE_LOG_TAG, "Wallpaper import failed while copying bytes: ${error.message}")
				runCatching { tempWallpaperFile.delete() }
				runCatching { wallpaperFile.delete() }
				false
			}

			if (!copied) {
				return@withContext false
			}

			val wallpaperSaved = setWallpaperUri(Uri.fromFile(wallpaperFile).toString())
			if (!wallpaperSaved) {
				runCatching { wallpaperFile.delete() }
				return@withContext false
			}

			pruneManagedWallpaperFiles(wallpaperDirectory, keepFileName = wallpaperFile.name)
			Log.d(
				SETTINGS_STORE_LOG_TAG,
				"Wallpaper imported successfully as ${wallpaperFile.name} (${wallpaperFile.length()} bytes)"
			)
			true
		}
	}

	private data class BitmapBoundsProbe(
		val width: Int,
		val height: Int
	) {
		val isDecodable: Boolean
			get() = width > 0 && height > 0
	}

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

	suspend fun setWallpaperScaleMode(scaleMode: WallpaperScaleMode) {
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				preferences[LauncherPreferencesKeys.wallpaperScaleMode] = scaleMode.name
			}
		}
	}

	suspend fun setWallpaperDimAmount(dimAmount: Float) {
		val normalized = dimAmount.coerceIn(MIN_WALLPAPER_DIM_AMOUNT, MAX_WALLPAPER_DIM_AMOUNT)
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				preferences[LauncherPreferencesKeys.wallpaperDimAmount] = normalized
			}
		}
	}

	suspend fun setWallpaperBlurRadiusDp(blurRadiusDp: Float) {
		val normalized = blurRadiusDp.coerceIn(MIN_WALLPAPER_BLUR_RADIUS_DP, MAX_WALLPAPER_BLUR_RADIUS_DP)
		withContext(ioDispatcher) {
			appContext.coverLauncherSettingsDataStore.edit { preferences ->
				preferences[LauncherPreferencesKeys.wallpaperBlurRadiusDp] = normalized
			}
		}
	}

	suspend fun clearWallpaper() {
		setWallpaperUri(uri = null)
		withContext(ioDispatcher) {
			deleteManagedWallpaperFiles()
		}
	}

	suspend fun resetLauncherLayout() {
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
				preferences[LauncherPreferencesKeys.dockVisible] = true
				// Theme preference is intentionally preserved during layout reset.
			}

			deleteManagedWallpaperFiles()
		}
	}

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

	private fun deleteManagedWallpaperFiles() {
		val wallpaperDirectory = File(appContext.filesDir, MANAGED_WALLPAPER_DIR)
		if (!wallpaperDirectory.exists()) return

		pruneManagedWallpaperFiles(wallpaperDirectory = wallpaperDirectory, keepFileName = null)

		if (wallpaperDirectory.listFiles().isNullOrEmpty()) {
			runCatching { wallpaperDirectory.delete() }
		}
	}

	private fun isManagedWallpaperFile(file: File): Boolean {
		return file.name == MANAGED_WALLPAPER_FILE_LEGACY ||
			file.name.startsWith(MANAGED_WALLPAPER_FILE_PREFIX)
	}

	private fun isManagedWallpaperUri(uri: Uri): Boolean {
		if (uri.scheme != "file") return false
		val path = uri.path ?: return false
		val file = File(path)
		val wallpaperDirectory = File(appContext.filesDir, MANAGED_WALLPAPER_DIR)
		return file.parentFile?.absolutePath == wallpaperDirectory.absolutePath
	}

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
		if (!file.exists() || !file.isFile || !file.canRead() || file.length() <= 0L) {
			return false
		}

		val fileUri = Uri.fromFile(file)
		return isBitmapDecodable {
			runCatching { file.inputStream() }.getOrNull()
		} && canDecodeBitmapPixels(fileUri)
	}

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

	private fun isWallpaperUriReadableAndDecodable(uri: Uri): Boolean {
		return isBitmapDecodable {
			openUriInputStream(uri)
		} && canDecodeBitmapPixels(uri)
	}

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

