# Plan — Restyle Cover-Screen Overlay to Samsung One UI 8 Design Philosophy

> Status: DRAFT — 2026-09-07
> Owner: cover-screen-os / overlay UI
> Related: `plan-launcherAccessibilityOverlayMigration` (surface migration),
> `Keyboard_Implementation_Plan.md` (keyboard primitives).

## 1. Goal

Retire the current ad-hoc mix of translucent black tiles, hardcoded 0.45-alpha
scrims, and one-off `coverGlassSurface()` invocations across the launcher
overlay. Replace with a **coherent One UI 8 design system** implemented as
Compose primitives so every overlay page (`CoverAppGridOverlay`,
`OverlayNotificationPanel`, `OverlayMediaPanel`, `OverlaySearchWidgets`) reads
as one product.

Concretely: adopt **One UI 8's five design pillars** on a 720×1560-class cover
surface:

1. **Fluid depth** — layered translucency + continuous corner curves.
2. **Content-first typography** — SEC-inspired oversized headings, tight leading.
3. **Ambient motion** — spring-based transitions, no linear interpolators.
4. **Signal color, calm surface** — one accent per page, everything else neutral.
5. **Ergonomic reach** — every primary target sits in the bottom 60 % of the
   surface.

## 2. Non-Goals

- Not touching the input-injection overlay (`overlay/input/*`). Separate plan.
- Not migrating to `TYPE_ACCESSIBILITY_OVERLAY` — this plan sits **on top of**
  `CoverComposeSurface` and works with whichever host is bound.
- No new dependencies (Haze already provides the blur we need).
- Not shipping Samsung SEC fonts — we approximate weight & spacing with the
  existing `CoverOSTypography` family.

## 3. What Exists Today

Reference points and pain:

| File | Current styling | Pain |
|------|-----------------|------|
| `ui/launcher/CoverAppGridOverlay.kt` (~1137 LoC) | HorizontalPager, per-tile `coverGlassSurface()`, ad-hoc `padding(8.dp)` blocks. | Corner radii inconsistent (8/12/16/24 all appear); dock uses different fill alpha than notif panel. |
| `ui/launcher/OverlayNotificationPanel.kt` | `Color.Black.copy(alpha = 0.45f)` tile background, `RoundedCornerShape(16.dp)`. | Fill is opaque-ish, kills wallpaper depth. Not participating in the shared HazeState. |
| `ui/launcher/OverlayMediaPanel.kt` | Gradient `Brush` background, custom IconButton sizes (48dp). | Doesn't match notification/dock rhythm; controls too small for cover finger reach. |
| `ui/launcher/OverlaySearchWidgets.kt` | Material3 `OutlinedTextField`, `RoundedCornerShape` at multiple radii. | TextField's baseline stroke is jarring against the frosted panels around it. |
| `ui/theme/CoverScreenModifiers.kt` | `coverGlassSurface`, `coverBackdropBlur`, radii 34/24/16. | Good foundation but each caller re-parameterises alpha/border — no design tokens. |
| `ui/theme/Color.kt` | Galaxy indigo accent, AMOLED black surfaces. | Only accent scheme; no per-page "signal color" support. |

## 4. Design System — `CoverOneUISystem`

New file: `app/src/main/java/com/tyejaedon/coverscreenos/ui/theme/oneui/CoverOneUISystem.kt`

### 4.1 Design tokens

```kotlin
object CoverOneUITokens {
    // Continuous corner curves — three canonical radii, no more.
    val radiusHero   = 32.dp    // full-panel cards, media hero
    val radiusPanel  = 24.dp    // notification tile, dock, search
    val radiusChip   = 14.dp    // pills, suggestions, single icons

    // Spacing rhythm — 4dp base, One UI uses generous exhalation.
    val gutter       = 20.dp    // page horizontal padding
    val gapLarge     = 16.dp    // between vertical sections
    val gapMedium    = 12.dp    // between adjacent tiles
    val gapSmall     = 8.dp     // intra-tile

    // Depth — three tiers only.
    // Tier 0 = ambient wallpaper.
    // Tier 1 = frosted panel (dock, notif tile, media card).
    // Tier 2 = elevated actionable (pressed key, expanded media, dialog).
    val tier1FillAlpha   = 0.22f
    val tier1BorderAlpha = 0.28f
    val tier2FillAlpha   = 0.38f
    val tier2BorderAlpha = 0.55f

    // Motion — One UI's signature soft spring.
    val motionSpringDamping   = 0.82f   // ~ Samsung "gentle" curve
    val motionSpringStiffness = 380f
    val motionEnterMillis     = 260
    val motionExitMillis      = 200
}
```

### 4.2 Surface tiers

Replace the ad-hoc `coverGlassSurface()` calls with **three named tier
modifiers** so no caller ever picks alpha again:

```kotlin
fun Modifier.oneUiPanel(
    shape: Shape = RoundedCornerShape(CoverOneUITokens.radiusPanel),
    signal: Color? = null,          // optional accent halo
): Modifier
fun Modifier.oneUiElevatedPanel(
    shape: Shape = RoundedCornerShape(CoverOneUITokens.radiusHero),
): Modifier
fun Modifier.oneUiChip(
    selected: Boolean = false
): Modifier
```

Under the hood each modifier:
- Uses shared `HazeState` for backdrop blur (radius **28.dp** ± 4 depending on
  tier — samsung leans heavier than our current 20).
- Draws a hairline **1.dp** border with the tier's border-alpha on
  `MaterialTheme.colorScheme.outline`.
- Fill = `MaterialTheme.colorScheme.surface.copy(alpha = tier{N}FillAlpha)`.
- Optional `signal` param draws a **1.5.dp inner-glow ring** in the accent —
  used sparingly (e.g. current media panel, selected search result).

### 4.3 Typography

Introduce `CoverOneUIType`:

```kotlin
object CoverOneUIType {
    // Hero numeric — clock, media artist. Weight 300, letterSpacing -1.5 sp.
    val displayHero:  TextStyle
    // Section headings — "Notifications", "Media", "Apps". 22 sp, Semibold.
    val sectionTitle: TextStyle
    // Tile primary text (app name, notif title). 15 sp, Medium.
    val tileTitle:    TextStyle
    // Tile secondary text (subtitle, timestamp). 13 sp, Normal, 70 % alpha.
    val tileSubtitle: TextStyle
    // Chip / pill label. 12 sp, Semibold, uppercase, letterSpacing +0.8 sp.
    val chipLabel:    TextStyle
}
```

Wire these into `CoverOSTypography` so existing composables migrate by
swapping `MaterialTheme.typography.bodyLarge` → `CoverOneUIType.tileTitle`.

### 4.4 Signal color per page

One UI's key trick: each page has *one* dominant accent — everything else
neutral. Define:

```kotlin
enum class OverlayPageAccent(val color: Color) {
    NOTIFICATIONS(Color(0xFF7C9CFF)),   // Galaxy indigo — matches current primary
    LOCK_DOCK    (Color(0xFF6EE7C0)),   // Teal — matches current secondary
    APP_GRID     (Color(0xFFFFB870)),   // Warm orange — matches current tertiary
    MEDIA        (Color(0xFFE879F9))    // Magenta — new; media surfaces in One UI
}
```

Pager exposes `LocalOverlayAccent` provided per page so status pills, chips,
selection halos automatically pick the right hue.

### 4.5 Motion primitives

```kotlin
@Composable fun oneUiSpring(): SpringSpec<Float>
@Composable fun oneUiFadeIn():  EnterTransition
@Composable fun oneUiFadeOut(): ExitTransition
@Composable fun oneUiSlideUp(): EnterTransition
```

Every use of `animateFloatAsState`, `AnimatedVisibility`, `animateContentSize`
in the overlay switches to these. Ban `tween(300, LinearEasing)` in overlay
code — code review rule.

## 5. Per-Page Restyle

### 5.1 `CoverAppGridOverlay` (`ui/launcher/CoverAppGridOverlay.kt`)

Structure changes:
- Kill the mixed-radius world; every rounded thing becomes `radiusPanel` or
  `radiusHero`.
- Convert the outer `Surface(background = colorScheme.background)` to a
  transparent `Box` — the wallpaper layer is the background.
- Wrap each page's content in an `oneUiPanel` container so the frosted panel
  reads as one coherent card per page, not a floating grid of independent
  tiles.
- Replace status pills (lock, battery) with `oneUiChip(selected = false)`.
- Reduce dock row to **3 primary + 1 "all apps" affordance** on the far right
  — a One UI staple, and it opens the app-grid page. Adjustable in settings
  but this is the new default.

### 5.2 `OverlayNotificationPanel`

- Kill the `Color.Black.copy(alpha = 0.45f)` tile fill; use `oneUiPanel`.
- Notification tile layout follows One UI 8:
  - App icon 40dp, **rounded 12dp** (One UI icon squircle — not full circle).
  - Title: `tileTitle`, one line, ellipsize.
  - Subtitle: `tileSubtitle`, up to 2 lines.
  - Timestamp: chip in top-right using `chipLabel`.
- Swipe-to-dismiss:
  - Background reveals `oneUiChip` in accent red on trailing swipe.
  - Threshold 60 % (currently ~40 %) so accidental swipes on the cover screen
    are rarer.
- Section headers ("Now", "Earlier") with `sectionTitle`, sticky per group.

### 5.3 `OverlayMediaPanel`

- Adopt the compact/expanded pattern:
  - **Compact** card on lockscreen: `oneUiPanel(shape = radiusHero)`, 88dp
    tall, album art on left, artist/track stacked, single play/pause control.
  - **Expanded** on tap: full-page `oneUiElevatedPanel`, hero album art
    centered ~55 % of viewport, playback controls **72dp** targets bottom-
    biased.
- Replace the current custom gradient background with a `signal` halo pulled
  from the album art's dominant color (use `androidx.palette:palette-ktx`
  which is already transitively present via Material components — verify).
  If unavailable, keep tier tokens.

### 5.4 `OverlaySearchWidgets`

- Drop `OutlinedTextField`; use a **plain** `BasicTextField` inside an
  `oneUiPanel(shape = radiusPanel)` so the border stroke matches the rest of
  the surface tiers instead of Material's default outline.
- Suggestion list uses `SuggestionStrip` from `ui/keyboard/primitives/` (built
  in a prior phase) so search + keyboard share chip visuals.
- Voice / T9 / physical-keyboard mode toggles become **three
  `oneUiChip`s**, not IconButtons.

### 5.5 Wallpaper layer

- `OverlayWallpaperLayer.kt`: increase default `wallpaperDimAmount` from 0.15
  to **0.28** when *any* elevated panel is expanded (media, notification
  detail). Animate with `oneUiSpring()`.
- Blur radius bumped 20 → **28dp** to match tier defaults, override-able in
  settings.

### 5.6 Dock

- New composable `OneUiDockRow` replaces `CoverDockRow`.
- Background: `oneUiPanel(shape = radiusPanel)`, single pill-shape spanning
  90 % of viewport width, 76dp tall.
- Icons **56dp** with 14dp inner squircle mask.
- Long-press on a slot opens a bottom-anchored `oneUiElevatedPanel` chooser
  (mirrors One UI's "Change app" long-press).

## 6. Rollout Phases

### Phase A — Design system module (safe, no visible change)
- Add `ui/theme/oneui/` package with tokens, tier modifiers, motion, type.
- Existing `coverGlassSurface`, `coverBackdropBlur` remain but become thin
  aliases forwarding to `oneUiPanel` (deprecation `@Deprecated` warning).
- Add `LocalOverlayAccent` composition local wired to `OverlayPageAccent`.
- No visual diff yet. Ship.

### Phase B — Restyle notification + dock (highest legibility win)
- `OverlayNotificationPanel` and `OverlayDockRow` migrate to `oneUiPanel`.
- Icons swap to 12dp-corner squircle mask helper `oneUiIconSquircle()`.
- QA: on-device readability at 3 wallpaper luminances (dark, mid, bright).

### Phase C — Media panel
- Compact/expanded pattern rebuild.
- Optional palette-derived signal halo.
- QA: media session updates while overlay is suppressed do not thrash
  recomposition.

### Phase D — Search + status pills
- Basic-TextField swap.
- Chip-ify voice/T9/keyboard toggles.
- Status pill audit (lock, battery, network).

### Phase E — App grid page + cleanup
- Consolidate radii; delete every hardcoded `RoundedCornerShape(N.dp)` in
  `ui/launcher/`.
- Delete deprecated `coverGlassSurface` aliases.
- Add lint check that flags `Color(0xFF...)` in `ui/launcher/` outside `Color.kt`
  (via detekt custom rule or CI grep).

## 7. Testing Strategy

### 7.1 Compose previews
Every new tier modifier + type style ships with a `@Preview` gallery in
`ui/theme/oneui/Previews.kt` rendering against:
- Solid black wallpaper (AMOLED worst case)
- Bright white wallpaper (contrast worst case)
- Photo wallpaper (real usage)

### 7.2 Snapshot / screenshot tests
- Use Roborazzi or Compose's built-in `captureToImage` in an instrumented
  test on the cover display. Baseline images live in
  `app/src/androidTest/assets/oneui/`.
- Regression suite covers every page × dark/light × wallpaper preset.

### 7.3 Recomposition budget
Add `Recomposition Highlighter`-style test: capture recomposition counts
during a 3 s idle overlay and fail the test if any tile recomposes > 2
times.

### 7.4 Manual QA matrix

| Device / OS             | Notif tile legibility | Media compact | Media expanded | Dock reach | Search |
|-------------------------|:---------------------:|:-------------:|:--------------:|:----------:|:------:|
| Flip 5 / One UI 6.1     |                       |               |                |            |        |
| Flip 6 / One UI 7       |                       |               |                |            |        |
| Fold 6 outer / One UI 7 |                       |               |                |            |        |

Repeat for each of the 4 wallpaper luminances.

## 8. Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Increasing blur radius to 28dp doubles Haze's per-frame cost on the cover surface. | Benchmark before Phase B. If frame budget < 8 ms hits, drop to 24dp and re-verify legibility with tier fill alpha bumped +0.03. |
| Palette-derived signal on media panel needs bitmap decoding on the main thread if not cached. | Compute in the media session listener (already off-main) and cache in the same LRU as album art. |
| Users on very bright wallpapers lose text contrast with our lower panel alphas. | Auto-boost dim to 0.28 whenever wallpaper Y > 0.6 (compute once on wallpaper import in `WallpaperBitmapCache`). |
| Radius consolidation breaks muscle-memory for a small cohort testing daily. | Ship behind `LauncherSettings.oneUiRestyleEnabled` flag for Phase B / C — flip default at Phase E. |
| One UI 8 official visuals aren't public yet as of Sep 2026. Design drifts. | Track Samsung Developer / One UI 8 keynote assets in an internal doc; keep tokens in **one** file so a design refresh is a token diff. |

## 9. Definition of Done

- `git grep "coverGlassSurface(" app/src/main` returns zero results.
- `git grep "RoundedCornerShape(" app/src/main/java/com/tyejaedon/coverscreenos/ui/launcher`
  returns only references to the three canonical radii (`radiusHero`,
  `radiusPanel`, `radiusChip`).
- `git grep "Color.Black.copy" app/src/main/java/com/tyejaedon/coverscreenos/ui/launcher`
  returns zero.
- Every launcher composable uses `oneUiPanel` / `oneUiElevatedPanel` /
  `oneUiChip` for surfaces — no direct `background()` on colored surfaces.
- Screenshot suite green on all 3 devices × 3 wallpaper luminances.
- One UI 8 restyle flag deleted; new visuals are the only path.

## 10. Follow-ups Enabled By This Change

- Reuse the same tier modifiers on the **input-injection overlay** in a
  future phase → whole-product visual coherence.
- With `LocalOverlayAccent` in place, per-notification-channel accent color
  becomes a 20-line change (map channel → accent color).
- Dynamic theming (Material You wallpaper-derived palette) plugs into
  `OverlayPageAccent` cleanly since accents are already indirected.

## 11. Estimated Effort

|          Phase           | Eng-days |
|:------------------------:|:--------:|
| A — design system module |    3     |
|     B — notif + dock     |    2     |
|        C — media         |    3     |
|   D — search + status    |    2     |
|    E — grid + cleanup    |    2     |
|        **Total**         | **~12**  |

Excludes buffer for palette-lib evaluation and Samsung device-specific
tuning (Flip 5 vs Flip 6 wallpaper decoding path differs).

