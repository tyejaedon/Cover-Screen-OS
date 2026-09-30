package com.tyejaedon.coverscreenos.ui.appshell

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.net.toUri
import com.tyejaedon.coverscreenos.BuildConfig
import org.junit.Rule
import org.junit.Test

class AppShellDeepLinksTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun open(uri: String) {
        rule.activity.intent = Intent(Intent.ACTION_VIEW, uri.toUri())
        rule.setContent { AppShell() }
    }

    @Test fun customizeLinkOpensWallpaper() {
        open(HomeRoutes.deepLink(HomeRoutes.Customize))
        rule.onNodeWithText("Wallpaper customization").assertExists()
    }

    @Test fun wallpaperLinkOpensWallpaper() {
        open(CustomizeCategory.WALLPAPER.deepLink)
        rule.onNodeWithText("Wallpaper customization").assertExists()
    }

    @Test fun dockLinkOpensDock() {
        open(CustomizeCategory.DOCK.deepLink)
        rule.onNodeWithText("Show dock on cover screen").assertExists()
    }

    @Test fun appearanceLinkOpensAppearance() {
        open(CustomizeCategory.APPEARANCE.deepLink)
        rule.onNodeWithText("Accent color").assertExists()
    }

    @Test fun inputLinkOpensInput() {
        open(CustomizeCategory.INPUT.deepLink)
        rule.onNodeWithText("Choose how app drawer search accepts text on the cover display.").assertExists()
    }

    @Test fun aboutLinkOpensLiveAboutScreenAndBundledLicense() {
        open(HomeRoutes.deepLink(HomeRoutes.About))
        rule.onNodeWithText("How it works").assertExists()
        rule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertExists()
        rule.onNodeWithText("Diagnostics").assertExists()
        rule.onNodeWithText("CMU Pronouncing Dictionary license", substring = true).performClick()
        rule.onNodeWithText("Copyright (C) 1993-2015", substring = true).assertExists()
    }
}
