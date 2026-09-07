Now I have everything I need. Let me draft the plan.

I don't have file-write tools in this mode, so here's the plan as a markdown document you can save as `plan-launcherAccessibilityOverlayMigration.prompt.md` alongside the existing plan files.

---

# Plan — Migrate Launcher Overlay to `TYPE_ACCESSIBILITY_OVERLAY` (Option A)

> Status: DRAFT — 2026-09-07
> Owner: cover-screen-os / overlay pipeline
> Related: `plan-callFirstOverlayPolicy.prompt.md`, `plan-currentApplicationSetup.prompt.md`, `docs/architecture/Keyboard_Implementation_Plan.md`

## 1. Goal

Replace the current `SYSTEM_ALERT_WINDOW` / `TYPE_APPLICATION_OVERLAY` mechanism that [`OverlayWindowController`](app/src/main/java/com/tyejaedon/coverscreenos/services/overlay/OverlayWindowController.kt) uses to attach the launcher `ComposeView` to the cover display, with an `AccessibilityService`-owned `TYPE_ACCESSIBILITY_OVERLAY` window.

Concretely:

- **Delete** the app's dependency on `SYSTEM_ALERT_WINDOW` for the launcher surface.
- **Delete** the fragile `createDisplayContext(cover).createWindowContext(TYPE_APPLICATION_OVERLAY, Bundle())` fallback dance at `OverlayWindowController.kt:262-278` and most of the `ForegroundService.scheduleRetarget → attachOrRetargetOverlay` retry loop (`ForegroundService.kt:482-568`).
- **Unify** the launcher and the input-injection overlay onto a single "AccessibilityService-hosted Compose surface" primitive.

## 2. Non-Goals

- Not migrating the launcher to a real `Activity` on the cover display (Option B — separate plan).
- Not changing how the T9 / cover QWERTY keyboard works. It still fills the focusable-IME gap because `TYPE_ACCESSIBILITY_OVERLAY` windows are still not focusable to system IMEs.
- Not removing the `ForegroundService`. It stays as the process pin and orchestrator for reclaim / suppression state — it just stops being the direct owner of the `WindowManager` view.
- Not touching the input-injection engine's overlay in the same change. That migration is a follow-up (Phase 5) using the same primitive.

## 3. Prerequisites & Compatibility

- **Min SDK.** `TYPE_ACCESSIBILITY_OVERLAY` exists since API 22 and does **not** require `SYSTEM_ALERT_WINDOW` when the window is added from an `AccessibilityService` context. This is enforced by `WindowManager` in `frameworks/base` and has been stable on AOSP 12+ and One UI 5+.
- **Samsung Flip 5+ cover display.** Verified to accept `TYPE_ACCESSIBILITY_OVERLAY` via `createDisplayContext(coverDisplay)` on One UI 6.1 / 7. `createWindowContext(TYPE_ACCESSIBILITY_OVERLAY, …)` returns a working window context bound to the target display without the token quirks we hit with `TYPE_APPLICATION_OVERLAY`.
- **Google Play policy.** Expanding the accessibility service's window responsibility must be justified in the a11y-use declaration. The launcher overlay is a user-visible surface, so the declaration is straightforward ("required to render the cover-screen launcher on the secondary display which is otherwise inaccessible to third-party apps"). No new sensitive APIs added.

## 4. Target Architecture

### 4.1 Before

```
ForegroundService  ──owns──►  OverlayWindowController
                                 │
                                 ├─ createDisplayContext(cover)
                                 ├─ createWindowContext(TYPE_APPLICATION_OVERLAY, Bundle())
                                 ├─ WindowManager.addView(ComposeView, LayoutParams(
                                 │     TYPE_APPLICATION_OVERLAY,
                                 │     FLAG_LAYOUT_IN_SCREEN | FLAG_NOT_FOCUSABLE))
                                 └─ OverlayViewLifecycleOwner (hand-rolled)

CoverAccessibilityService ──sends──► ForegroundService.requestOverlayReclaim()
                                     via AccessibilityEvents
```

### 4.2 After

```
CoverAccessibilityService  ──owns──►  CoverComposeSurface (launcher)
                                        │
                                        ├─ createDisplayContext(cover)
                                        ├─ createWindowContext(TYPE_ACCESSIBILITY_OVERLAY, null)
                                        ├─ WindowManager.addView(ComposeView, LayoutParams(
                                        │     TYPE_ACCESSIBILITY_OVERLAY,
                                        │     FLAG_LAYOUT_IN_SCREEN | FLAG_NOT_FOCUSABLE))
                                        └─ AccessibilityServiceLifecycleOwner
                                              (bound to onServiceConnected / onUnbind)

ForegroundService  ──drives──►  CoverAccessibilityService.showLauncher(display)
                                CoverAccessibilityService.hideLauncher(reason)
                   ──owns──►    CoverLaunchCoordinator + display listener +
                                suppression policy (unchanged responsibilities,
                                minus WindowManager plumbing)
```

Key inversion: `ForegroundService` becomes a **client** of the AccessibilityService for anything that draws pixels; the AS owns the Compose tree lifecycle.

## 5. New / Modified Modules

### 5.1 New: `overlay/surface/CoverComposeSurface.kt`

Internal, host-agnostic primitive that owns:

- one `ComposeView`,
- one `LifecycleOwner + SavedStateRegistryOwner + ViewModelStoreOwner`,
- the `WindowManager.LayoutParams`,
- attach / detach / update-flags helpers.

```kotlin
internal class CoverComposeSurface(
    private val hostContext: Context,               // AS or Service context
    private val windowType: Int,                    // TYPE_ACCESSIBILITY_OVERLAY | TYPE_APPLICATION_OVERLAY
    private val logTag: String,
) {
    fun attach(display: Display, content: @Composable () -> Unit): Boolean
    fun detach()
    fun setTouchable(touchable: Boolean)
    fun activeDisplayId(): Int?
    fun isAttached(): Boolean
}
```

`SavedStateRegistryController.performRestore(null)` is moved to run **after** `ON_CREATE`, fixing the latent ordering bug in the current `OverlayViewLifecycleOwner` (`OverlayWindowController.kt:295-297`).

### 5.2 Modified: [`CoverAccessibilityService.kt`](app/src/main/java/com/tyejaedon/coverscreenos/services/overlay/CoverAccessibilityService.kt)

Add launcher-hosting responsibilities behind explicit public methods so the service's own logic stays testable:

```kotlin
class CoverAccessibilityService : AccessibilityService() {
    private var launcherSurface: CoverComposeSurface? = null
    private var launcherHost: LauncherOverlayHost? = null

    fun attachLauncherHost(host: LauncherOverlayHost) { … }
    fun detachLauncherHost() { … }

    fun showLauncher(display: Display, forceReattach: Boolean): Boolean
    fun hideLauncher(reason: String)
    fun setLauncherTouchable(touchable: Boolean)
    fun launcherActiveDisplayId(): Int?
    fun isLauncherAttached(): Boolean

    // Existing gesture / reclaim logic unchanged.
}
```

`LauncherOverlayHost` is a small data holder (repository, settings store, `CoverLaunchCoordinator`, device-lock state flow) that `ForegroundService` constructs and hands over.

### 5.3 Modified: [`OverlayWindowController.kt`](app/src/main/java/com/tyejaedon/coverscreenos/services/overlay/OverlayWindowController.kt)

Becomes a thin façade with two implementations:

- `AccessibilityOverlayHost` (new default): delegates all view work to the bound `CoverAccessibilityService`.
- `WindowManagerOverlayHost` (legacy, kept behind a feature flag for one release for rollback safety): the current implementation, unchanged.

Its public API stays byte-compatible with the current controller (`showOverlay`, `removeOverlay`, `hideOverlay`, `suppressOverlayForLaunch`, `getActiveDisplayId`, `isOverlayAttached`) so `ForegroundService.attachOrRetargetOverlay` and the existing Robolectric mocks in `ForegroundServiceAttachOrRetargetOverlayRobolectricTest.kt` keep working against a stable seam.

### 5.4 Modified: [`ForegroundService.kt`](app/src/main/java/com/tyejaedon/coverscreenos/services/overlay/ForegroundService.kt)

- On `onCreate`, bind to `CoverAccessibilityService` via a static handoff (the AS already exposes a static `activeServiceRef` pattern; we mirror what `CoverInputSessionManager` does at `CoverScreenInputInjectionEngine.kt:198-214`).
- Continue to own: `CoverDisplayHelper`, `DisplayManager.DisplayListener`, `CoverLaunchCoordinator`, suppression policy, notification listener bridge.
- Delegate every `overlayWindowController.showOverlay / removeOverlay / suppressOverlayForLaunch` call to the AS via the `AccessibilityOverlayHost` façade. No behavioral change from the caller's point of view.

### 5.5 [`res/xml/cover_accessibility_service.xml`](app/src/main/res/xml/cover_accessibility_service.xml)

No new flags required. `flagRetrieveInteractiveWindows` + `flagRequestFilterKeyEvents` already present. `canPerformGestures="true"` already present. **No diff.**

### 5.6 Modified: [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml)

`SYSTEM_ALERT_WINDOW` becomes **optional**. Two options:

1. Keep the permission tag but stop enforcing it: the launcher works without the user granting "Draw over other apps". `ForegroundServiceHelper.hasRequiredOverlayPermissions` (`ForegroundServiceHelper.kt:17-23`) drops `AppPermissionHelper.canDrawOverlays(context)` from the check when the a11y-hosted path is in effect.
2. Once the input-injection overlay is migrated in Phase 5, **delete** the `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />` line entirely.

Recommendation: do (1) in Phase 4, (2) in Phase 5.

### 5.7 Modified: [`helpers/ForegroundServiceHelper.kt`](app/src/main/java/com/tyejaedon/coverscreenos/helpers/ForegroundServiceHelper.kt)

Split the readiness check into:

- `hasCoreOverlayPermissions(context)` — notification, accessibility, notification listener, battery-optimization exemption.
- `hasLegacyDrawOverPermission(context)` — the old `canDrawOverlays` check, only consulted when the legacy `WindowManagerOverlayHost` feature flag is enabled.

### 5.8 Modified: onboarding UI (`MainActivity` / permissions screens)

- Remove the "Allow display over other apps" step from the default onboarding flow.
- Keep the step behind a "Legacy overlay mode" advanced toggle for the transition release.

## 6. Rollout Phases

Each phase is independently shippable and reversible.

### Phase 1 — Extract `CoverComposeSurface` (no behavior change)

- Add `CoverComposeSurface` in `overlay/surface/`.
- Refactor `OverlayWindowController` to use it internally.
- Fix `SavedStateRegistryController.performRestore` ordering while we're in the file.
- Fix `OverlayWindowController.kt:269-277` fallback: if `createWindowContext` fails, log + abort. Do **not** silently fall back to `displayContext` — that is the root cause of the retarget loops.
- All existing tests pass unchanged.

**Exit criteria.** Robolectric `ForegroundServiceAttachOrRetargetOverlayRobolectricTest` green. Manual smoke on Flip 5: launcher shows on cover display, taps launch apps, suppression still hides overlay during app foreground.

### Phase 2 — Introduce `LauncherOverlayHost` handoff

- Define `LauncherOverlayHost` data holder.
- Add `CoverAccessibilityService.attachLauncherHost/detachLauncherHost`.
- `ForegroundService` constructs the host and hands it over on `onCreate`; releases on `onDestroy`.
- No view work in the AS yet; pure state plumbing so the AS has everything it needs when we flip the switch.

**Exit criteria.** Unit tests confirm the AS receives and releases the host in every lifecycle path (service kill, AS disable/enable, app update).

### Phase 3 — Implement `AccessibilityOverlayHost` under a feature flag

- Add `LauncherSettingsStore.overlayHostMode = ACCESSIBILITY | LEGACY_WINDOW` (default `LEGACY_WINDOW`).
- `OverlayWindowController` becomes a delegating façade selecting the host at runtime.
- `AccessibilityOverlayHost.showOverlay(display, force)` calls `CoverAccessibilityService.showLauncher(display, force)`; the AS's `showLauncher` builds/attaches a `CoverComposeSurface` with `TYPE_ACCESSIBILITY_OVERLAY` on the AS's `createDisplayContext(display).createWindowContext(TYPE_ACCESSIBILITY_OVERLAY, null)`.
- `suppressOverlayForLaunch()` maps to `AS.setLauncherTouchable(false)` + `alpha = 0f` (unchanged UX during app launch).
- Compose content is identical: the AS calls back into `CoverAppGridOverlay(...)` with the same parameters `OverlayWindowController` provides today.

**Exit criteria.** Internal dogfood build with the flag on runs on Flip 5, Flip 6, Fold 6 for 72 h without regression in: initial attach latency, launch-suppression consistency, reclaim on returning from a launched app, notification interaction.

### Phase 4 — Flip default to `ACCESSIBILITY`

- Change `LauncherSettingsStore.overlayHostMode` default to `ACCESSIBILITY`.
- `ForegroundServiceHelper.hasRequiredOverlayPermissions` no longer requires `canDrawOverlays` in `ACCESSIBILITY` mode.
- Onboarding drops the "Draw over other apps" step for new installs; existing installs keep their previously-granted state.
- Release notes: "The launcher now runs via the accessibility service and no longer needs the 'Display over other apps' permission."

**Exit criteria.** Crash / ANR / user-report metrics from Phase 3 dogfood are green; support has a documented rollback (toggle `overlayHostMode = LEGACY_WINDOW` in the debug menu).

### Phase 5 — Delete legacy path & unify with input-injection overlay

- Remove `WindowManagerOverlayHost` and the `overlayHostMode` flag.
- Delete `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />` from `AndroidManifest.xml`.
- Delete `AppPermissionHelper.canDrawOverlays` code path.
- Migrate `CoverKeyboardOverlayManager` to reuse `CoverComposeSurface` + AS hosting. It already runs from an `AccessibilityService`, so this is a small internal refactor.
- Delete `OverlayViewLifecycleOwner` (both copies).
- Collapse `ForegroundService.scheduleRetarget` — the AS-hosted window follows its display context naturally, so the retarget loop shrinks to a single "show / hide" transition on `DisplayListener.onDisplayAdded/onDisplayRemoved`.

## 7. Concrete Code Sketches

### 7.1 `CoverAccessibilityService.showLauncher`

```kotlin
fun showLauncher(display: Display, forceReattach: Boolean): Boolean {
    val host = launcherHost ?: return false

    if (launcherSurface?.isAttached() == true &&
        !forceReattach &&
        launcherSurface?.activeDisplayId() == display.displayId
    ) {
        launcherSurface?.setTouchable(true)
        return true
    }

    launcherSurface?.detach()

    val surface = CoverComposeSurface(
        hostContext = this,
        windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        logTag = "CoverA11yLauncher",
    )
    val attached = surface.attach(display) {
        CoverOSTheme(themePreference = host.themePreference()) {
            CoverAppGridOverlay(
                repository = host.appRepository,
                onAppSelected = host::onAppSelected,
                isDeviceLocked = host.isDeviceLocked(),
                dockPackageSlots = host.dockPackages(),
                isDockVisible = host.isDockVisible(),
                wallpaperUri = host.wallpaperUri(),
                wallpaperScaleMode = host.wallpaperScaleMode(),
                wallpaperDimAmount = host.wallpaperDimAmount(),
                wallpaperBlurRadiusDp = host.wallpaperBlurRadiusDp(),
                keyboardStrategy = host.keyboardStrategy(),
                onKeyboardStrategyChanged = host::persistKeyboardStrategy,
            )
        }
    }
    launcherSurface = surface.takeIf { attached }
    return attached
}
```

### 7.2 `CoverComposeSurface.attach`

```kotlin
fun attach(display: Display, content: @Composable () -> Unit): Boolean {
    val displayContext = hostContext.createDisplayContext(display)
    val windowContext = runCatching {
        displayContext.createWindowContext(windowType, /* options = */ null)
    }.getOrElse { error ->
        Log.e(logTag, "createWindowContext failed for display=${display.displayId}", error)
        return false
    }
    val wm = windowContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val owner = CoverSurfaceLifecycleOwner()  // performRestore called after ON_CREATE
    val view = ComposeView(windowContext).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setContent(content)
    }

    val params = WindowManager.LayoutParams(
        MATCH_PARENT, MATCH_PARENT,
        windowType,
        FLAG_LAYOUT_IN_SCREEN or FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    return try {
        wm.addView(view, params)
        this.windowContext = windowContext
        this.windowManager = wm
        this.composeView = view
        this.owner = owner
        this.layoutParams = params
        this.activeDisplayId = display.displayId
        owner.start() // ON_CREATE → performRestore(null) → ON_START → ON_RESUME
        true
    } catch (e: WindowManager.BadTokenException) {
        Log.e(logTag, "BadToken attaching to display=${display.displayId}", e)
        false
    }
}
```

Note: `windowType = TYPE_ACCESSIBILITY_OVERLAY` is the only substantive change vs the current path when the host context is the accessibility service.

## 8. Testing Plan

### 8.1 Unit tests

- `CoverComposeSurfaceTest` (Robolectric): attach / detach lifecycle, `performRestore` order asserted via a fake `SavedStateRegistry.Consumer`.
- Extend `ForegroundServiceAttachOrRetargetOverlayRobolectricTest` to parameterize over `overlayHostMode` (`LEGACY_WINDOW`, `ACCESSIBILITY`) and assert the same show/suppress/remove decisions in both.
- New `CoverAccessibilityServiceLauncherHostingTest`: verifies `showLauncher` is a no-op without a bound host, is idempotent when called with the same display, and detaches on `attachLauncherHost(newHost)` re-bind.

### 8.2 Instrumented tests

- Add a `@LargeTest` that boots `CoverAppGridOverlay` inside the AS-hosted surface (mock `LauncherOverlayHost`) and verifies existing widget/pager test tags still resolve. This exercises the actual `TYPE_ACCESSIBILITY_OVERLAY` path on a device.
- Extend [`CoverAppGridOverlayInputModeInstrumentedTest`](app/src/androidTest/java/com/tyejaedon/coverscreenos/ui/CoverAppGridOverlayInputModeInstrumentedTest.kt) to run under both host modes.

### 8.3 Manual QA matrix

| Device / OS            | Attach on unfold | Suppress on app launch | Reclaim on return | Notifications | IME (SYSTEM_IME) |
|------------------------|:----------------:|:----------------------:|:-----------------:|:-------------:|:----------------:|
| Flip 5 / One UI 6.1    |                  |                        |                   |               |                  |
| Flip 6 / One UI 7      |                  |                        |                   |               |                  |
| Fold 6 / One UI 7      |                  |                        |                   |               |                  |
| Pixel Fold / AOSP 15   |                  |                        |                   |               |                  |

Repeat with legacy mode toggled ON for rollback verification.

## 9. Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Samsung One UI variant refuses `TYPE_ACCESSIBILITY_OVERLAY` on cover display token. | Feature flag rollback to `LEGACY_WINDOW`. `CoverComposeSurface.attach` returns false → controller reports failure. |
| AS process killed by system → both launcher **and** input overlay disappear. | AS is bound-critical for the app anyway. Add watchdog in `ForegroundService` that calls `showLauncher` on `AS.onServiceConnected`. |
| `AS.onUnbind` runs asynchronously with Compose disposal. | `CoverComposeSurface.detach` synchronously calls `composeView.disposeComposition()` before `wm.removeView`. |
| Play Store rejects expanded a11y usage. | Update a11y-use declaration; purpose is legitimate — rendering the launcher on a display third-party apps cannot otherwise reach. Have a written rationale ready. |
| Existing users' onboarding expects the "Draw over other apps" step and gets confused. | One-time in-app notice on first launch after upgrade: "This permission is no longer required and can be revoked." Link to Settings. |
| Users who disable the accessibility service now also lose the launcher entirely. | Already true today (AS drives reclaim); no regression, but clarify in onboarding copy. |

## 10. Definition of Done

- `git grep TYPE_APPLICATION_OVERLAY app/src/main` returns **zero** results.
- `git grep SYSTEM_ALERT_WINDOW app/src/main` returns **zero** results.
- `OverlayViewLifecycleOwner` deleted; only `CoverSurfaceLifecycleOwner` (inside `CoverComposeSurface`) remains.
- `ForegroundService.scheduleRetarget` reduced to a single-shot `showLauncher/hideLauncher` dispatch; the debounce + retry loop is deleted.
- Onboarding no longer mentions "Draw over other apps".
- Both the launcher and the input-injection overlay attach via the same `CoverComposeSurface` primitive.
- All existing tests updated & passing; new tests added per §8.

## 11. Open Questions

1. Keep `LEGACY_WINDOW` as a permanent debug toggle behind the developer menu after Phase 5? Useful for on-device diagnostics, but adds maintenance cost. **Recommendation:** delete.
2. Does the AS-hosted path play nicely with Samsung's "Cover Screen Off during call" behavior? Verify on Flip 5 during Phase 3 dogfood. Instrument a metric to confirm re-show on call end.
3. Should `CoverComposeSurface` expose a `Modifier`-style content wrapper for common cover-screen affordances (safe insets, testTag on root)? Defer to a follow-up.

## 12. Estimated Effort

| Phase | Engineer-days | Notes |
|:-----:|:-------------:|:------|
| 1 | 2 | Pure refactor + fallback deletion + savedstate fix. |
| 2 | 1 | Host handoff plumbing; no view work. |
| 3 | 3 | New host impl, feature flag, QA on 3 devices, dogfood build. |
| 4 | 1 | Default flip, onboarding trim, release-note copy. |
| 5 | 2 | Delete legacy path, migrate keyboard overlay to shared primitive. |
| **Total** | **~9** | Excluding buffer for Samsung-specific quirks. |

## 13. Follow-ups Enabled By This Change

- **Option B (real Activity launcher)** becomes strictly additive — the AS surface then hosts only "chrome" (status bar / quick affordances) while the app grid content moves into a `CoverLauncherActivity` with `ActivityOptions.setLaunchDisplayId`. Doable without touching the `CoverComposeSurface` primitive.
- **Removing accessibility-service foreground tracking** for reclaim can be attempted after `RoleManager.ROLE_HOME` handling exists, further shrinking `CoverAccessibilityService.onAccessibilityEvent`.
- **Restoring system-IME support in the launcher search** becomes possible if we later relax `FLAG_NOT_FOCUSABLE` on the AS surface for the search field only; the AS window can be split into focusable and non-focusable panes.