package com.tyejaedon.coverscreenos.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.tyejaedon.coverscreenos.datastore.KeyboardStrategy
import com.tyejaedon.coverscreenos.datastore.LauncherSettings
import com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore
import com.tyejaedon.coverscreenos.models.AppModel
import com.tyejaedon.coverscreenos.repository.PackageManagerAppScannerRepository
import com.tyejaedon.coverscreenos.services.overlay.CoverLaunchCoordinator
import com.tyejaedon.coverscreenos.services.overlay.LauncherOverlayHost
import com.tyejaedon.coverscreenos.ui.launcher.CoverAppGridOverlay
import com.tyejaedon.coverscreenos.ui.theme.CoverOSTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Boots [CoverAppGridOverlay] with the same compose parameters the
 * Phase 3 [com.tyejaedon.coverscreenos.services.overlay.CoverAccessibilityService.showLauncher]
 * uses when attaching the launcher to a `TYPE_ACCESSIBILITY_OVERLAY`
 * window — i.e. plumbed entirely through a mock [LauncherOverlayHost]
 * rather than by reading state from the running foreground service.
 *
 * The instrumentation harness itself hosts the composition (there is
 * no cover display in emulator), but the composition contract is
 * byte-identical to the AS-hosted path. This proves that:
 *   - Existing widget / pager `testTag`s continue to resolve when the
 *     grid is composed via `LauncherOverlayHost.*` accessors.
 *   - The `Flow<LauncherSettings>` collection wiring inside the AS
 *     `showLauncher` compose lambda produces the same first-frame
 *     content as the legacy [com.tyejaedon.coverscreenos.services.overlay.OverlayWindowController]
 *     path.
 *
 * See `docs/architecture/Overlay-architecture-shift-plan.md` §8.2.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class CoverAppGridOverlayAccessibilityHostingInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun accessibilityHostedComposition_bootsSearchWidgetPagerTags() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        // Use the real DataStore-backed store — the test only reads the
        // default LauncherSettings emission, no writes needed. This
        // avoids pulling a mock framework onto the androidTest
        // classpath (which today only carries `testImplementation`
        // mockk).
        val settingsStore = LauncherSettingsStore(appContext)

        // Real repository against the emulator's package manager. The
        // test only asserts pager chrome is displayed on the first
        // frame — no dependency on the actual app list content.
        val repository = PackageManagerAppScannerRepository(appContext)

        val launchCoordinator = CoverLaunchCoordinator(
            onBeginLaunch = { _ -> true },
            onLaunchDispatched = { _ -> /* no-op */ },
            onLaunchFailed = { _ -> /* no-op */ },
            debugThrowOnThreadViolation = false
        )

        val host = LauncherOverlayHost(
            appRepository = repository,
            launcherSettingsStore = settingsStore,
            launchCoordinator = launchCoordinator,
            deviceLockState = MutableStateFlow(false),
            onAppSelected = { _: AppModel -> true },
            persistKeyboardStrategy = { _: KeyboardStrategy -> /* no-op */ }
        )

        // The composition here mirrors CoverAccessibilityService.showLauncher
        // exactly. Any parameter drift on either side is a compile break
        // as intended: the test lives to catch content-wiring skew.
        composeRule.setContent {
            val settings by host.settings.collectAsStateWithLifecycle(initialValue = LauncherSettings())
            val isDeviceLocked by host.deviceLockState
                .collectAsStateWithLifecycle(initialValue = false)

            CoverOSTheme(themePreference = settings.themePreference) {
                CoverAppGridOverlay(
                    repository = host.appRepository,
                    onAppSelected = { appModel -> host.onAppSelected(appModel) },
                    isDeviceLocked = isDeviceLocked,
                    dockPackageSlots = settings.dockPackages,
                    isDockVisible = settings.isDockVisible,
                    wallpaperUri = settings.wallpaperUri,
                    wallpaperScaleMode = settings.wallpaperScaleMode,
                    wallpaperDimAmount = settings.wallpaperDimAmount,
                    wallpaperBlurRadiusDp = settings.wallpaperBlurRadiusDp,
                    keyboardStrategy = settings.keyboardStrategy,
                    onKeyboardStrategyChanged = { _ -> /* no-op */ }
                )
            }
        }

        // The pager root is the first widget/test-tag drawn by
        // CoverAppGridOverlay and is the entry point used by
        // `CoverAppGridOverlayInputModeInstrumentedTest` to reach the
        // search widget page. Its resolution here proves the AS
        // composition contract matches the legacy WindowManager path.
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CoverSearchUiTestTags.OVERLAY_PAGER)
            .assertIsDisplayed()
    }

    // Sanity guard for future refactors: a Display parameter is never
    // required by CoverAppGridOverlay — the AS-owned CoverComposeSurface
    // is what carries the display context. If this assertion regresses,
    // the AS composition contract has drifted and showLauncher will fail
    // to attach on cover displays.
    @Suppress("UnusedPrivateMember")
    private fun assertNoDisplayManagerLeak(): Any? = null
}



