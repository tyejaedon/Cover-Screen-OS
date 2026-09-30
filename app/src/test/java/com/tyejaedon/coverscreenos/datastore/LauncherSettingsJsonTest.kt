package com.tyejaedon.coverscreenos.datastore

import com.tyejaedon.coverscreenos.overlay.input.CoverKeyboardMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherSettingsJsonTest {
    @Test
    fun `all supported settings round trip through JSON`() {
        val settings = LauncherSettings(
            dockPackages = listOf("com.example.one", null, "com.example.two", null),
            wallpaperScaleMode = WallpaperScaleMode.FIT,
            wallpaperDimAmount = 0.3f,
            wallpaperBlurRadiusDp = 4f,
            isDockVisible = false,
            themePreference = ThemePreference.DARK,
            accentColor = AccentColor.ROSE,
            panelCornerRadiusDp = 28f,
            dockSlotFourAllApps = true,
            keyboardStrategy = KeyboardStrategy.SYSTEM_IME,
            keyboardModeByPackage = mapOf("com.example.one" to CoverKeyboardMode.QWERTY)
        )
        assertEquals(settings, LauncherSettingsJson.decode(LauncherSettingsJson.encode(settings)))
    }

    @Test
    fun `invalid JSON rejects bad values instead of applying defaults`() {
        val valid = LauncherSettingsJson.encode(LauncherSettings())
        assertThrows(IllegalArgumentException::class.java) {
            LauncherSettingsJson.decode(JSONObject(valid).put("version", 2).toString())
        }
        assertThrows(IllegalArgumentException::class.java) {
            LauncherSettingsJson.decode(JSONObject(valid).put("wallpaperDimAmount", -1).toString())
        }
        assertThrows(IllegalArgumentException::class.java) {
            LauncherSettingsJson.decode(JSONObject(valid).put("panelCornerRadiusDp", 33).toString())
        }
        assertThrows(IllegalArgumentException::class.java) {
            LauncherSettingsJson.decode(JSONObject(valid).put("accentColor", "UNKNOWN").toString())
        }
        assertThrows(IllegalArgumentException::class.java) {
            LauncherSettingsJson.decode(
                JSONObject(valid).put("dockPackages", org.json.JSONArray(listOf("a", "a", null, null))).toString()
            )
        }
    }

    @Test
    fun `older JSON preserves legacy appearance and dock defaults`() {
        val legacy = JSONObject(LauncherSettingsJson.encode(LauncherSettings()))
            .apply {
                remove("accentColor")
                remove("panelCornerRadiusDp")
                remove("dockSlotFourAllApps")
            }
        assertEquals(LauncherSettings(), LauncherSettingsJson.decode(legacy.toString()))
    }

    @Test
    fun `category resets affect only their own settings and JSON import restores saved modes`() = runBlocking {
        val store = LauncherSettingsStore(RuntimeEnvironment.getApplication())
        val original = store.settings.first()
        try {
            store.setLauncherLayout(
                LauncherSettings(
                    dockPackages = listOf("com.example.one", null, null, null),
                    wallpaperScaleMode = WallpaperScaleMode.FIT,
                    wallpaperDimAmount = 0.2f,
                    wallpaperBlurRadiusDp = 3f,
                    isDockVisible = false,
                    themePreference = ThemePreference.DARK,
                    accentColor = AccentColor.MINT,
                    panelCornerRadiusDp = 22f,
                    dockSlotFourAllApps = true,
                    keyboardStrategy = KeyboardStrategy.SYSTEM_IME
                )
            )
            store.setKeyboardModeForPackage("com.example.one", CoverKeyboardMode.QWERTY)
            val exported = store.exportSettingsJson()
            store.resetDockCustomization()
            val afterDock = store.settings.first()
            assertEquals(List(COVER_DOCK_SLOT_COUNT) { null }, afterDock.dockPackages)
            assertEquals(true, afterDock.isDockVisible)
            assertEquals(false, afterDock.dockSlotFourAllApps)
            assertEquals(ThemePreference.DARK, afterDock.themePreference)
            assertEquals(AccentColor.MINT, afterDock.accentColor)
            assertEquals(0.2f, afterDock.wallpaperDimAmount)
            store.resetAppearanceCustomization()
            assertEquals(ThemePreference.SYSTEM, store.settings.first().themePreference)
            assertEquals(AccentColor.DEFAULT, store.settings.first().accentColor)
            assertEquals(DEFAULT_PANEL_CORNER_RADIUS_DP, store.settings.first().panelCornerRadiusDp)
            store.resetInputCustomization()
            assertEquals(DEFAULT_KEYBOARD_STRATEGY, store.settings.first().keyboardStrategy)
            assertEquals(emptyMap<String, CoverKeyboardMode>(), store.settings.first().keyboardModeByPackage)
            store.resetWallpaperCustomization()
            assertEquals(DEFAULT_WALLPAPER_SCALE_MODE, store.settings.first().wallpaperScaleMode)
            assertEquals(DEFAULT_WALLPAPER_DIM_AMOUNT, store.settings.first().wallpaperDimAmount)
            store.importSettingsJson(exported)
            assertEquals(LauncherSettingsJson.decode(exported), store.settings.first())
        } finally {
            store.setLauncherLayout(original, restoreKeyboardModes = true)
        }

        @Test
        fun `appearance setters persist and invalid radius uses safe default`() = runBlocking {
            val store = LauncherSettingsStore(RuntimeEnvironment.getApplication())
            val original = store.settings.first()
            try {
                store.setAccentColor(AccentColor.AMBER)
                store.setPanelCornerRadiusDp(30f)
                store.setDockPackage(3, "com.example.saved")
                store.setDockSlotFourAllApps(true)
                assertEquals(AccentColor.AMBER, store.settings.first().accentColor)
                assertEquals(30f, store.settings.first().panelCornerRadiusDp)
                assertEquals(true, store.settings.first().dockSlotFourAllApps)
                assertEquals("com.example.saved", store.settings.first().dockPackages[3])
                store.setDockSlotFourAllApps(false)
                assertEquals("com.example.saved", store.settings.first().dockPackages[3])
                store.setPanelCornerRadiusDp(Float.NaN)
                assertEquals(DEFAULT_PANEL_CORNER_RADIUS_DP, store.settings.first().panelCornerRadiusDp)
            } finally {
                store.setLauncherLayout(original, restoreKeyboardModes = true)
            }
        }
    }
}
