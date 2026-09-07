# Plan — Redesign the Application Home Screen (Main-Display UI)

> Status: DRAFT — 2026-09-07
> Owner: cover-screen-os / main app UI
> Related: `plan-overlayOneUI8Restyle.md`, `plan-currentApplicationSetup.prompt.md`.

## 1. Goal

The current main-display UI (`ui/homescreen/HomeScreen.kt`) is a single
vertically scrolling `Column` that mixes readiness status, runtime controls,
service banners, and a customization hub. It scales poorly, leaks unrelated
concerns into one surface, and violates several core HCI principles (see §3).

This plan replaces it with a **tab-first, icon-anchored, Scaffold-based**
architecture built on `androidx.navigation:navigation-compose`, structured
around four top-level destinations with clear jobs-to-be-done. Every screen
follows the same HCI guardrails: Fitts's law for touch targets, Miller's rule
for on-screen items per group, Hick's law for choice minimization, and
progressive disclosure for advanced controls.

## 2. Non-Goals

- Not restyling the **cover-screen overlay**. That is a separate plan
  (`plan-overlayOneUI8Restyle.md`). The main-display UI can share design
  tokens introduced there but keeps its own layout patterns.
- Not changing service lifecycle (`ForegroundService`, `CoverAccessibility-
  Service`). This is a UI-only refactor.
- Not touching the permission gating flow contents — only its **wrapper**
  (see §5.5 Onboarding tab).
- Not localizing yet. Strings live in `res/values/strings.xml`; the redesign
  audits and re-groups keys but does not add new locales.

## 3. Problems With the Current Design

| # | Problem                                                                                       | Symptom                                                                   | HCI principle violated                                               |
|---|-----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------|----------------------------------------------------------------------|
| 1 | Single scroll column mixes 4 unrelated concerns (readiness, service, banners, customization). | User can't build a stable mental model of "where do I do X".              | **Gestalt proximity** — unrelated items visually adjacent.           |
| 2 | Every action is a full-width Material `Button` label.                                         | 12+ identical buttons scroll under thumb.                                 | **Hick's law** — too many equally-weighted choices.                  |
| 3 | Readiness card takes ~40 % of first viewport.                                                 | Returning users scroll past every session.                                | **Recognition over recall** — no persistent status indicator.        |
| 4 | Customization is nested inside a boolean-toggled sub-view (`showCustomizationHub`).           | Deep-linking a specific setting requires composable state gymnastics.     | **Visibility of system state / addressability**.                     |
| 5 | No icons — every affordance is text.                                                          | Dense wall of labels, slow scan.                                          | **Recognition** — icons accelerate visual parsing by ~30 % (Norman). |
| 6 | Permissions request UI overtakes the whole activity until every permission granted.           | Users who deferred one permission cannot see anything else.               | **User control & freedom**.                                          |
| 7 | Navigation is composable booleans + Intents.                                                  | No back-stack, no deep-links, no restore-instance-state on config change. | **Consistency & standards** — Android nav conventions.               |

## 4. Design Principles

Every screen in the redesign must satisfy:

1. **Above-the-fold answers "what is the app doing right now?"** — persistent
   compact status chip at top of every screen.
2. **Primary action lives in the bottom-right / bottom-center** — thumb reach
   on 6.7"–8" screens (Fitts).
3. **≤ 5 top-level destinations** — Miller's rule for concurrent choice items.
4. **≤ 7 primary items per section** — group with headers if more.
5. **Icons + label** — not icons alone; not text alone. Both.
6. **Progressive disclosure** — advanced settings collapse behind
   "More options" affordances, not always-on.
7. **Every destination is deep-linkable** — even from a debug notification.

## 5. Target Architecture

### 5.1 Navigation graph

Introduce `androidx.navigation:navigation-compose`. Version target: latest
stable at implementation time; add to `libs.versions.toml` under
`navigationCompose`.

```
NavHost (startDestination = HomeRoutes.Dashboard)
├── Dashboard          (icon: DashboardCustomize)
├── Customize          (icon: Palette)         nested graph:
│   ├── Wallpaper                              (Wallpaper)
│   ├── Dock                                   (GridView)
│   ├── Appearance                             (Brush)
│   └── Input                                  (Keyboard)
├── Permissions        (icon: VerifiedUser)    replaces gate-blocking flow
└── About              (icon: Info)            settings, diagnostics, version
```

- **Four** top-level destinations. Miller-compliant.
- Nested `Customize` graph gives `Wallpaper`, `Dock`, etc. their own
  back-stack entries and deep-link URIs
  (`coverscreenos://customize/wallpaper`).

### 5.2 Scaffold + navigation surface

`AppShell` composable in `ui/appshell/AppShell.kt`:

```kotlin
@Composable
fun AppShell(...) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        topBar    = { AppShellTopBar(currentRoute, onBack = navController::navigateUp) },
        bottomBar = { AppShellNavBar(currentRoute, onNavigate = navController::navigateTopLevel) },
        floatingActionButton = { AppShellFab(currentRoute) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { inner ->
        NavHost(
            navController    = navController,
            startDestination = HomeRoutes.Dashboard,
            modifier         = Modifier.padding(inner)
        ) { … }
    }
}
```

- **Bottom navigation** for the 4 top-level destinations.
  `NavigationBarItem(icon = Icon, label = ...)` — icons ALWAYS visible,
  labels shown for the selected item to preserve context (Material 3
  guideline; also HCI recognition).
- **Top app bar** is small (`SmallTopAppBar`), shows the current
  destination's title + a persistent **runtime status chip** on the
  trailing side that opens a bottom sheet with detailed service state.
- **FAB** shown only where a single "hero" action makes sense
  (e.g. Dashboard: "Start service" or "Stop service" depending on state).

### 5.3 Dashboard — replaces `HomeScreen.kt`

New file: `ui/dashboard/DashboardScreen.kt`

Layout (top → bottom):

1. **Hero status card** (`DashboardHeroCard`) — one card, answers
   "is the cover screen active?". Traffic-light iconography:
   - Green + `CheckCircle` icon: "Cover screen active"
   - Amber + `PendingActions`: "Setup needed" (1+ permission missing)
   - Red + `Error`: "Service stopped"
   - Tap → expands to show details (permissions missing, last event, uptime).
2. **Quick actions row** — 3 `FilledTonalIconButton`s with labels:
   - Play/Stop toggle
   - "Grant permissions" (only if amber)
   - "Preview overlay" (mock the cover surface on the phone screen)
3. **Recent activity card** — collapsible LazyColumn of last N
   overlay events (attach, suppress, reclaim). Debug-only in release.
4. **Tips carousel** — dismissible; 3–5 rotating tips ("Set the dock",
   "Change wallpaper", "Enable haptics").

Only one screen owns readiness. `HomeReadinessCard.kt` is deleted; its
content moves to the `Permissions` tab (§5.5).

### 5.4 Customize — nested tabs / list

New file: `ui/customize/CustomizeScreen.kt` hosts the nested `Customize`
sub-graph. Two possible layouts based on posture:

- **Compact width** (< 600dp): `PrimaryTabRow` at top, one page per category.
- **Expanded width** (≥ 600dp foldable / tablet): List-detail with 2-pane
  `NavigationSuiteScaffold`-style layout — categories on left, detail on
  right.

Category pages (each in `ui/customize/<category>/`):

- **Wallpaper**: picker card + scale/blur/dim sliders. Live preview at the
  top, controls below (recognition-based feedback).
- **Dock**: 4 slot cards with app icons; drag-to-reorder; long-press to
  clear. Add an "All apps" affordance in slot 4 by default per the overlay
  restyle.
- **Appearance**: theme (System/Light/Dark) as SegmentedButton, accent
  swatches, corner-radius slider (only affects overlay panels — with live
  mini preview).
- **Input**: keyboard strategy toggle (T9 / compose), a link to IME picker,
  a link to the new keyboard primitives preview for QA builds.

Every category screen carries a `TopAppBar` with an overflow menu:
- "Reset to defaults"
- "Export settings" / "Import settings" (JSON, for pro users; behind
  a debug flag initially)

### 5.5 Permissions — was blocking, now a tab

New file: `ui/permissions/PermissionsScreen.kt` replaces
`permissions/PermissionScreen.kt` as the entry surface (the request
composables move here but stop being modal).

Layout:

- Header: **permission health ring** (0–100 %) computed from granted count.
- LazyColumn of `PermissionRow`s grouped in sections:
  - "Required" (overlay, accessibility, notification listener,
    foreground service, battery exemption)
  - "Recommended" (POST_NOTIFICATIONS, ignore-battery, biometric)
  - "Optional" (media, microphone, gallery)
- Each row: leading icon, name + one-line rationale, trailing status chip
  and action button.
- Sticky footer: "Continue with limited features" if ≥ 1 required is
  denied. Never traps the user.

First-run behaviour:
- The nav bar starts with `Permissions` as the initial destination if any
  required permission is missing. Otherwise starts on `Dashboard`. This is
  a **soft** gate — the user can still swipe to other tabs; the tabs
  themselves surface an inline notice when a feature they rely on is
  denied.

### 5.6 About / Diagnostics

`ui/about/AboutScreen.kt`:
- App name + version.
- "How it works" 3-tile carousel.
- Diagnostics section: last crash timestamp, active service, AS host state.
- Legal (open-source licenses via Google's `oss-licenses-plugin` or a
  hand-rolled screen).
- Developer options (debug builds): overlay host mode toggle, dry-run
  launcher intent, force reclaim, etc.

## 6. Design System Reuse

Home screens use **standard Material 3** components — NOT the overlay's
frosted-glass tier modifiers. The cover screen and the phone screen serve
different jobs; conflating them makes each look wrong on the other:

- `Card` with `CardDefaults.cardColors(containerColor = surfaceContainerLow)`
  for grouping.
- `FilledTonalButton` for secondary, `Button` for primary.
- `SegmentedButton` for exclusive choices (theme, scale mode).
- `Slider` with tick marks for numeric ranges.
- Material Icons Extended for every affordance icon.

Shared **tokens** with the overlay theme:
- Corner radii from `CoverOneUITokens.radiusChip / radiusPanel` (introduced
  in the overlay plan) — reused so the phone UI feels part of the same
  product family without inheriting the frosted-glass aesthetic.
- Motion primitives (`oneUiSpring`, `oneUiFadeIn`) reused where the phone
  UI has meaningful transitions (destination changes, expand/collapse).
- Typography: use `MaterialTheme.typography` on the phone side, NOT
  `CoverOneUIType` (which is calibrated for the tiny cover surface).

## 7. Rollout Phases

Each phase is a shippable increment. Old and new UIs coexist behind a
`BuildConfig.NEW_HOME_UI` flag until Phase E.

### Phase A — Nav shell scaffolding
- Add `navigation-compose` dependency.
- Create `AppShell`, `HomeRoutes`, empty destination composables.
- MainActivity hosts `AppShell` behind the flag; existing HomeScreen used
  when flag is off.
- No behavior visible to users.

### Phase B — Dashboard + status chip
- Implement `DashboardScreen`, `DashboardHeroCard`, `TopBarStatusChip`.
- Wire live state (`ForegroundService.isServiceRuntimeActive`,
  `CoverAccessibilityService.currentLauncherHost()`).
- Existing HomeScreen contents partially reused (readiness state,
  runtime start/stop callbacks) via extracted `use-cases`.

### Phase C — Customize nested graph
- Migrate the four category screens from `ui/settings/*` into
  `ui/customize/<category>/`.
- Replace boolean-toggled `showCustomizationHub` with nav routes.
- Add list-detail expansion for foldable posture.

### Phase D — Permissions tab
- Move `PermissionScreen` contents into `PermissionsScreen`.
- Remove the modal gate in `MainActivity`.
- Add "Continue with limited features" sticky footer.

### Phase E — Flip the flag
- Default `NEW_HOME_UI = true`.
- Old files (`HomeScreen.kt`, `HomeReadinessCard.kt`,
  `HomeRuntimeControls.kt`, `HomeRuntimeBanner.kt`,
  `HomeCustomizationHub.kt`, `HomeCustomizationMenu.kt`,
  `HomeCustomizationPanelContent.kt`, `permissions/PermissionScreen.kt`)
  moved to `deprecated/` with `@Deprecated` markers.

### Phase F — Delete legacy
- Two releases after Phase E, delete `deprecated/` files, the
  `NEW_HOME_UI` flag, and the old `ui/settings/*` files superseded by
  `ui/customize/*`.

## 8. HCI Alignment — Checklist Per Screen

For each new screen, code review must confirm:

- [ ] Primary destination reachable in ≤ 2 taps from any other screen.
- [ ] Every touch target is ≥ 48dp (Fitts).
- [ ] No group contains > 7 items without a section header (Miller).
- [ ] Icon + label on every affordance (recognition).
- [ ] No modal blocker except a genuinely destructive confirmation
      (e.g. "Reset settings?") — soft affordances everywhere else
      (user control).
- [ ] Above-the-fold answers the primary question the user came for.
- [ ] Motion uses `oneUiSpring()` — no linear tween.
- [ ] All destination routes deep-linkable via `deepLinks { … }`.
- [ ] Configuration change (rotate / fold) preserves scroll position and
      active tab via `rememberSaveable`.
- [ ] TalkBack: every interactive node has `contentDescription`, groups
      collapse via `Modifier.semantics(mergeDescendants = true)`.

## 9. Accessibility

- Support dynamic type up to `fontScale = 2.0` — no clipping, no truncation
  on top-bar titles. Use `androidx.compose.foundation.text.autoSize` where
  available on M3 1.4+, or manual `AutoSizeText`.
- WCAG AA contrast ratios on the main-display UI (`CoverOSTheme`'s current
  Material 3 color scheme already meets this; verify with each new
  container tint).
- All bottom-nav destinations announceable — labels visible on the
  selected item; unselected items use `Modifier.semantics { contentDescription = label }`.
- Reduce-motion setting: when the system-level "Remove animations" is on,
  substitute `oneUiSpring` with `snap()`.
- Color-blind palette check on status chip (green/amber/red) — pair with
  distinctive icons, don't rely on hue alone.

## 10. Testing Strategy

### 10.1 Compose UI tests
- `AppShellTest`: nav bar renders 4 items, tapping each routes correctly,
  back-stack preserved.
- `DashboardScreenTest`: hero card reflects service state, quick actions
  fire the right callbacks.
- `PermissionsScreenTest`: row rendering per grant state, "Continue
  with limited features" appears when required perms denied.

### 10.2 Screenshot tests
- Roborazzi baselines per screen × theme × fontScale (1.0 and 2.0).

### 10.3 Deep-link tests
- Instrumented test firing `coverscreenos://customize/wallpaper` from a
  fake intent and asserting the correct destination.

### 10.4 Manual QA matrix

| Device                 | Portrait | Landscape | Fold-open | fontScale 2.0 | TalkBack |
|------------------------|:--------:|:---------:|:---------:|:-------------:|:--------:|
| Pixel 8                |          |           |     n/a   |               |          |
| Fold 6 outer / inner   |          |           |           |               |          |
| Flip 6 (main display)  |          |           |     n/a   |               |          |
| Tablet (Tab S9 FE)     |          |           |           |               |          |

## 11. Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| navigation-compose upgrade collides with existing Compose BOM. | Pick a compatible version at Phase A; blocked-by CI matrix build. |
| Users who previously bookmarked `MainActivity` land on Dashboard instead of Permissions and think the app broke. | First-run heuristic: if any required permission missing, seed the start destination as Permissions (§5.5). |
| Bottom nav on Fold-inner wastes vertical space. | On expanded width, switch to `NavigationRail`. Both are drop-in replacements for `NavigationBar`. |
| Customize deep-link URI conflicts with future launcher intents. | Namespace to `coverscreenos://app/customize/...`; document as internal only. |
| Screenshot suite noise from Compose typography changes. | Pin Compose BOM in `libs.versions.toml`; regenerate baselines only on intentional visual diffs. |
| Users depend on the old modal permission flow ("if I saw the screen, all permissions must be granted"). | Add a one-time "Welcome to the new home screen" bottom sheet on first launch after upgrade with a Tour of the four tabs. |

## 12. Definition of Done

- MainActivity contains only `AppShell` — no direct composable of home
  content.
- `androidx.navigation:navigation-compose` is a first-class dependency;
  every top-level destination is a `NavHost` entry.
- `ui/homescreen/` deleted (or empty).
- `permissions/PermissionScreen.kt` deleted; contents live at
  `ui/permissions/PermissionsScreen.kt`.
- Every screen passes the HCI checklist (§8), verified in code review.
- Screenshot suite green on all 4 devices × 2 fontScales.
- Deep links function from an `adb shell am start -a android.intent.action.VIEW -d "coverscreenos://customize/wallpaper"`.
- No composable in `ui/dashboard`, `ui/customize`, `ui/permissions`,
  `ui/about` uses `androidx.compose.foundation.background(Color(...))` —
  everything goes through `MaterialTheme.colorScheme` for themability.

## 13. Estimated Effort

|                     Phase                      | Eng-days |
|:----------------------------------------------:|:--------:|
|                 A — nav shell                  |    2     |
|          B — dashboard + status chip           |    3     |
|           C — customize nested graph           |    4     |
|              D — permissions tab               |    2     |
|        E — flag flip + deprecation move        |    1     |
| F — delete legacy (deferred, 2 releases later) |    1     |
|          **Total (through Phase E)**           | **~12**  |

Excludes buffer for screenshot baseline curation and TalkBack QA.

## 14. Follow-ups Enabled By This Change

- **Widget-style Dashboard cards** — third-party overlay integrations (e.g.
  weather, calendar) can register `DashboardCardProvider` and their card
  appears in the Dashboard's second row without touching this app's
  code.
- **In-app tutorial overlay** — the Scaffold surface makes a full-screen
  `Coach Mark` layer trivial to bolt on.
- **App-wide search** — new top destination behind an experimentation flag,
  built on the primitives already in `ui/keyboard/primitives/`.
- **Companion Wear OS UI** — deep-link URIs act as a canonical action
  registry; a Wear tile can dispatch them via `RemoteAction`.

