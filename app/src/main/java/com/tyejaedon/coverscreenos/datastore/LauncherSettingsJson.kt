package com.tyejaedon.coverscreenos.datastore

import com.tyejaedon.coverscreenos.overlay.input.CoverKeyboardMode
import org.json.JSONArray
import org.json.JSONObject

internal object LauncherSettingsJson {
    fun encode(settings: LauncherSettings): String = JSONObject().apply {
        put("version", 1)
        put("dockPackages", JSONArray(settings.dockPackages))
        put("wallpaperUri", settings.wallpaperUri ?: JSONObject.NULL)
        put("wallpaperScaleMode", settings.wallpaperScaleMode.name)
        put("wallpaperDimAmount", settings.wallpaperDimAmount)
        put("wallpaperBlurRadiusDp", settings.wallpaperBlurRadiusDp)
        put("isDockVisible", settings.isDockVisible)
        put("themePreference", settings.themePreference.name)
        put("accentColor", settings.accentColor.name)
        put("panelCornerRadiusDp", settings.panelCornerRadiusDp)
        put("dockSlotFourAllApps", settings.dockSlotFourAllApps)
        put("keyboardStrategy", settings.keyboardStrategy.name)
        put("keyboardModeByPackage", JSONObject(settings.keyboardModeByPackage.mapValues { it.value.name }))
    }.toString(2)

    fun decode(raw: String): LauncherSettings {
        val json = JSONObject(raw)
        require(json.getInt("version") == 1) { "Unsupported settings version" }
        require(json.has("wallpaperUri")) { "Missing wallpaper URI field" }
        val slots = json.getJSONArray("dockPackages")
        require(slots.length() == COVER_DOCK_SLOT_COUNT) { "Expected four dock slots" }
        val dock = List(COVER_DOCK_SLOT_COUNT) { index ->
            if (slots.isNull(index)) null else slots.getString(index).also {
                require(it.isNotBlank() && it == it.trim()) { "Invalid dock package" }
            }
        }
        require(dock.filterNotNull().distinct().size == dock.count { it != null }) {
            "Duplicate dock package"
        }
        val dim = json.getDouble("wallpaperDimAmount").toFloat()
        val blur = json.getDouble("wallpaperBlurRadiusDp").toFloat()
        require(dim.isFinite() && dim in MIN_WALLPAPER_DIM_AMOUNT..MAX_WALLPAPER_DIM_AMOUNT) {
            "Invalid wallpaper dim"
        }
        require(blur.isFinite() && blur in MIN_WALLPAPER_BLUR_RADIUS_DP..MAX_WALLPAPER_BLUR_RADIUS_DP) {
            "Invalid wallpaper blur"
        }
        val radius = if (json.has("panelCornerRadiusDp")) json.getDouble("panelCornerRadiusDp").toFloat()
            else DEFAULT_PANEL_CORNER_RADIUS_DP
        require(radius.isFinite() && radius in MIN_PANEL_CORNER_RADIUS_DP..MAX_PANEL_CORNER_RADIUS_DP) {
            "Invalid panel corner radius"
        }
        val modes = json.getJSONObject("keyboardModeByPackage")
        val savedModes = modes.keys().asSequence().associateWith { packageName ->
            require(packageName.isNotBlank()) { "Empty keyboard package" }
            enumValue<CoverKeyboardMode>(modes.getString(packageName))
        }
        return LauncherSettings(
            dockPackages = dock,
            wallpaperUri = if (json.isNull("wallpaperUri")) null else json.getString("wallpaperUri"),
            wallpaperScaleMode = enumValue(json.getString("wallpaperScaleMode")),
            wallpaperDimAmount = dim,
            wallpaperBlurRadiusDp = blur,
            isDockVisible = json.getBoolean("isDockVisible"),
            themePreference = enumValue(json.getString("themePreference")),
            accentColor = if (json.has("accentColor")) enumValue(json.getString("accentColor")) else AccentColor.DEFAULT,
            panelCornerRadiusDp = radius,
            dockSlotFourAllApps = if (json.has("dockSlotFourAllApps")) json.getBoolean("dockSlotFourAllApps") else false,
            keyboardStrategy = enumValue(json.getString("keyboardStrategy")),
            keyboardModeByPackage = savedModes
        )
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String): T =
        enumValues<T>().firstOrNull { it.name == value }
            ?: throw IllegalArgumentException("Unknown ${T::class.java.simpleName}: $value")
}
