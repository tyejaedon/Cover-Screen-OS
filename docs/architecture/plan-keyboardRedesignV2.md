# Plan — Keyboard Redesign V2: Feedback, Visibility & Resizability

> Status: DRAFT — 2026-10-11
> Owner: cover-screen-os / keyboard & input
> Related: `Keyboard_Implementation_Plan.md` (milestone 5 — layout primitives,
> shift state machine, suggestion strip, IME-action awareness; already shipped).
> Supersedes nothing — this plan assumes milestone 5's shared primitives
> (`KeyboardKey`, `HapticTier`, `SuggestionStrip`, `CoverCompactQwertyKeyboard`)
> are the foundation and focuses on four gaps users still hit daily.

## 1. Why this plan exists

Milestone 5 fixed layout correctness (target sizes, shift/caps, IME-action
icons, suggestion strip, per-app mode memory). What's left is **how typing
*feels*** and **how much of what you typed you can actually see**, plus a
long-promised **resizable surface**. Four concrete, code-verified gaps:

1. **The System-keyboard toggle is broken.** Switching from the T9 cover
   keypad to "System keyboard" swaps in an `OutlinedTextField` but never
   requests focus or calls `LocalSoftwareKeyboardController.show()` — the
   field renders unfocused and the user has to manually tap it before Gboard/
   Samsung Keyboard appears. (`CoverAppGridOverlay.kt`, `onKeyboardStrategyToggle`,
   ~L816-823; `keyboardController` is only ever `.hide()`d in this file, never
   shown.)
2. **Haptic feedback is inconsistent and too weak on the highest-frequency
   key.** `CoverCompactQwertyKeyboard`'s `key()` helper defaults ordinary
   letter keys to `HapticTier.Light` (`CLOCK_TICK`/`EFFECT_TICK` — the
   faintest tier), while the same `KeyboardKey` primitive defaults to
   `HapticTier.Standard` everywhere it's used un-tiered (e.g. every key in
   `CoverT9InputMethodService`). Typing a sentence on the cover QWERTY feels
   mushy compared to the system IME, and there's no user-facing control to
   adjust strength — only the OS-level `HAPTIC_FEEDBACK_ENABLED` toggle is
   respected.
3. **No visibility into long typed text.** Every live text read-out in the
   keyboard stack is hard-pinned to one line:
   `CoverScreenInputInjectionEngine.kt` header/preview `Text(...)` calls
   (~L1997-2003, ~L2106-2113) and the launcher search field in
   `OverlaySearchWidgets.kt` all use `maxLines = 1` + `TextOverflow.Ellipsis`.
   Composing a chat message or search query longer than the tile width
   silently scrolls out of view with no way to review or re-position the
   cursor.
4. **The keyboard surface is not resizable.** Key height is a fixed function
   of screen height (`keyHeight = screenHeightDp / 8` clamped to
   `[44, 64].dp` in `CoverCompactQwertyKeyboard`) with zero user control.
   Samsung Keyboard, Gboard and SwiftKey all offer drag-to-resize height,
   one-handed/side-anchored layouts, and a floating/detached mode — valuable
   on a narrow, thumb-reach-constrained cover display.
5. **The dictionary/predictive-text engine is thin and the user can't
   customize or clipboard-paste.** `WordDictionary` bundles a ~51KB
   CMU-derived word list and does its own edit-distance-1 correction plus a
   hard-coded four-entry `nextWords()` stub; there's no bigger vocabulary, no
   bigram-based next-word prediction, no user-facing keyboard customization
   beyond the strategy picker, and no way to copy/cut/paste from the cover
   keyboard. Added as Phases F/G/H below, alongside the original A-E.

## 2. Non-goals

- Not re-litigating milestone 5's injection pipeline, delta-commit semantics,
  or password handling — those are done and out of scope here.
- Not adding swipe/glide typing in the core phases (tracked as stretch work
  in Phase E only). A real predictive-language model *is* now in scope via
  Phase F (SymSpellKt + a larger bundled dictionary).
- Not changing `KeyboardStrategy` persistence format or DataStore schema
  beyond additive keys.

## 3. Inspiration (what mature keyboards get right that we still don't)

| Feature | Samsung Keyboard | Gboard | Us (today) |
|---|---|---|---|
| Drag-resize height | ✅ (grip handle above keys) | ✅ (one-handed + height slider) | ❌ fixed `screenHeightDp/8` |
| One-handed / side-anchored mode | ✅ | ✅ | ❌ |
| Floating/detached keyboard | ✅ | ✅ | ❌ |
| Expandable multi-line compose preview | ✅ (candidate + preview strip grows) | ✅ (app content scrolls via insets) | ❌ single line, ellipsis |
| Haptic intensity setting | ✅ (3 levels) | ✅ (system + in-app) | ❌ OS toggle only, tiers inconsistent |
| Switching IME auto-focuses/show | ✅ | ✅ | ❌ (this bug) |

## 4. Phases

### Phase A — Fix the broken System-keyboard toggle (bug, P0)

**Scope:** `CoverAppGridOverlay.kt` `onKeyboardStrategyToggle` callback and
`WidgetGridPageTile`/`CoverSearchInputRow` focus wiring.

**Deliverables:**
- When toggling **to** `SYSTEM_IME`, request focus on the `OutlinedTextField`
  via the existing `searchFieldFocusRequester` and call
  `keyboardController?.show()` on the next frame (post `LaunchedEffect` keyed
  on `keyboardStrategy`), matching the symmetry already present for the T9
  path (`onCoverKeyboardVisibilityChanged(true)`).
- When toggling **to** `T9`, keep the current clear-focus/hide behavior so the
  two strategies never fight over the soft keyboard.
- Regression-proof with an instrumented test asserting the system IME
  field gains focus (`isFocused == true`) immediately after the toggle tap,
  extending `CoverAppGridOverlayInputModeInstrumentedTest`.

**Acceptance:**
- Tapping the input-mode toggle while `T9 → SYSTEM_IME` shows the system IME
  without a second manual tap on the field.
- No regression in the `T9 → SYSTEM_IME → T9` round trip (existing tests +
  new assertion pass).

**Est. 0.5 eng-day.**

### Phase B — Unify & upgrade haptic + press feedback

**Scope:** `HapticTier`, `CoverCompactQwertyKeyboard`, `KeyboardStrategy`/
`LauncherSettingsStore` (new preference), `InputCustomizationCard`.

**Deliverables:**
- Change `CoverCompactQwertyKeyboard`'s `key()` default tier for letter/
  number keys from `HapticTier.Light` to `HapticTier.Standard`, matching the
  IME and bringing the two typing surfaces to parity. Reserve `Light` for
  genuinely secondary taps (suggestion-strip chips, punctuation popup).
  `Strong` remains for shift/enter/clear/commit as today.
- Add a user-facing **Haptic strength** setting — `Off / Light / Standard /
  Strong` — persisted in `LauncherSettingsStore`, surfaced as a segmented
  control in `InputCustomizationCard` under the existing keyboard-strategy
  picker. `rememberHapticPerformer` scales the resolved tier by this
  preference (e.g. `Strong` setting bumps `Light→Standard`, `Standard→Strong`;
  `Off` disables all tiers; `Light` setting caps everything at `Light`).
- Verify (and if needed wire) haptic parity for `CoverT9InputMethodService`
  keys now that both surfaces share the same resolved-tier pipeline.

**Acceptance:**
- Typing a sentence on the cover QWERTY and the system T9 IME produces the
  same perceived haptic strength for equivalent keys.
- New `Haptic strength` setting changes feedback intensity live and survives
  app restart (DataStore-backed).
- Unit tests cover the tier-scaling function for all four strength levels.

**Est. 1.5 eng-days.**

### Phase C — Expandable/scrollable typed-text visibility

**Scope:** `CoverScreenInputInjectionEngine.kt` buffer read-out,
`OverlaySearchWidgets.kt` search field, shared `primitives/`.

**Deliverables:**
- New `ui/keyboard/primitives/ComposingTextPreview.kt`: a Compose primitive
  that renders up to `maxCollapsedLines` (default 2) with `TextOverflow
  .Ellipsis`, shows a trailing "chars / expand" affordance when content
  exceeds that, and expands (on tap) into a scrollable, up-to-N-line (e.g. 5)
  view with the cursor always kept in view (`BringIntoViewRequester` on
  cursor position).
- Replace the hard `maxLines = 1` read-outs in `CoverScreenInputInjectionEngine
  .kt` (overlay header + preview) with `ComposingTextPreview`.
- Replace the single-line search field text rendering in
  `OverlaySearchWidgets.kt` (`CoverSearchInputRow`, both `T9` and `SYSTEM_IME`
  branches) with the same primitive so long queries stay reviewable.
- Preserve existing `n chars` counter; add a line-count indicator when
  collapsed content is truncated (e.g. "3/7 lines").

**Acceptance:**
- Typing a 300-character multi-sentence message shows the last two lines by
  default and expands to a scrollable 5-line view on tap without losing
  cursor position or dismissing the keyboard.
- No change in behavior/height for short (single-line) input — zero visual
  regression on existing screenshot tests.

**Est. 2 eng-days.**

### Phase D — Resizable & adaptive keyboard surface

**Scope:** `CoverCompactQwertyKeyboard`, `CoverT9InputMethodService` Compose
host, new `ui/keyboard/primitives/ResizableKeyboardSurface.kt`,
`LauncherSettingsStore` (new persisted height/anchor preference).

**Deliverables:**
- **Drag-resize handle**: a thin grip bar above the top key row; dragging
  vertically scales `keyHeight` within a clamped range (current
  `[44, 64].dp` becomes the *minimum*; allow up to `88.dp` for "tall" mode).
  Persist the chosen height per keyboard surface (cover overlay, launcher
  search, IME) in `LauncherSettingsStore`.
- **Compact / Standard / Tall presets** (mirrors Samsung's height presets) in
  addition to free-drag. Update (#120): these chips now live only in
  Customize > Input. The keyboard's top row holds the grip, the one-handed
  toggle and small copy/cut/paste icons. The four key rows shrink evenly to
  fit the screen so the bottom row is never squished.
- **One-handed mode**: a toggle that shrinks keyboard width to ~80% and
  anchors it left or right (swappable via a small arrow chip), matching
  Gboard/Samsung one-handed mode — valuable given the cover display's narrow,
  thumb-reach-constrained geometry.
- Expose the new size/anchor controls in `InputCustomizationCard` so users
  can set defaults without opening the keyboard first.

**Acceptance:**
- Dragging the grip handle resizes keys live with haptic tick at each preset
  boundary; the chosen height persists across app restarts and is shared by
  the overlay, launcher search, and IME surfaces.
- One-handed mode shifts the keyboard to the chosen side and restores full
  width when turned off, with no clipped keys at any supported display size.
- Automated tests cover the preference round-trip and clamping bounds.

**Est. 3 eng-days.**

### Phase E — Advanced typing features (stretch)

**Scope:** optional, lower-priority enhancements once A-D are stable.

**Status:** split into three standalone follow-on issues (#101 floating mode,
#102 swipe-glide, #103 per-app height/anchor memory) per this phase's own
"split into child issues" acceptance criteria. The clipboard/undo-strip item
originally scoped here shipped separately as Phase H.

**Deliverables (pick per capacity):**
- Floating/detached keyboard mode (free-positioned draggable window) for the
  cover overlay path.
- Swipe-glide trail visualization on `CoverCompactQwertyKeyboard` feeding the
  existing dictionary for gesture-typing.
- Per-app keyboard height/anchor memory (extends milestone 5's per-app mode
  memory with size/anchor).

**Acceptance:** each deliverable ships behind its own flag/toggle and does
not regress Phases A-D; treat as independently shippable follow-on issues.

**Est. 1-4 eng-days per deliverable, scoped at implementation time.**

### Phase F — Larger dictionary + SymSpellKt predictive text

**Scope:** `ui/keyboard/WordDictionary.kt`, new bundled dictionary asset,
`app/build.gradle.kts`.

**Deliverables:**
- Bundle a frequency-ranked English word list trimmed to the top ~100k words
  (open, permissively-licensed, e.g. `hermitdave/FrequencyWords`), replacing
  the current ~51KB CMU-derived `english_words.bin` as the base vocabulary
  (now `assets/english_words.bin` + `assets/english_bigrams.bin`, see
  `assets/DICTIONARY_LICENSE.txt`).
- Integrate `com.darkrockstudios:symspellkt` (MIT, Maven Central, Kotlin
  Multiplatform/Android) for SymSpell fuzzy-correction plus bigram-based
  context-aware next-word suggestions, replacing the homegrown edit-distance-1
  matcher and the hard-coded `nextWords()` stub.
- Preserve existing contact-name / app-label enrichment and learned/suppressed
  overrides.

**Acceptance:** noticeably better corrections/suggestions than before, no
visible latency on the suggestion strip, unit tests cover suggestion quality
and override behavior. **Est. 2.5 eng-days.**

### Phase G — Keyboard customization settings

**Scope:** `LauncherSettingsStore.kt`, `InputCustomizationComponents.kt`,
`CoverCompactQwertyKeyboard.kt`, `KeyboardKey.kt`.

**Deliverables:** persisted toggles for key-press sound, suggestion-strip
visibility, autocorrect vs. suggest-only, and popup key-preview, surfaced in
`InputCustomizationCard`.

**Acceptance:** each toggle changes behavior live and persists across
restart; disabling autocorrect never silently rewrites the typed word.
**Est. 1.5 eng-days.**

### Phase H — Copy/cut/paste toolbar + clipboard history

**Scope:** `CoverScreenInputInjectionEngine.kt`, new
`ui/keyboard/primitives/ClipboardToolbar.kt`.

**Deliverables:** a toolbar row above the suggestion strip with copy/cut/
paste (via accessibility `ACTION_COPY`/`ACTION_CUT`/`ACTION_PASTE` on the
focused node) plus a short clipboard-history chip row for quick re-paste.
Supersedes the "Clipboard/undo strip" bullet originally listed under Phase E.

**Acceptance:** copy/cut/paste work end-to-end against a real focused
editor; history is most-recent-first and re-paste works without re-copying.
**Est. 2 eng-days.**

## 5. Sequencing

A (bug fix, unblock users today; also folds in the hint/placeholder-text
buffer-leak fix) → F (dictionary/predictive-text foundation) → B (haptics,
cheap/high-perceived-value) → C (visibility) → H (clipboard) → G
(customization) → D (resizability, biggest surface area) → E (stretch, split
into standalone follow-ons; clipboard bullet superseded by H). A and B can
land in parallel; C and D touch overlapping files (`CoverCompactQwertyKeyboard`)
and should land in sequence to avoid merge churn. F lands before B/C/G since
later suggestion-strip and customization work should sit on the final
dictionary API rather than the old one.
