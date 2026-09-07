

# Cover-Screen OS — Keyboard Input: Review, HCI Analysis & Design Recommendations

## 1. What is actually implemented today

There are effectively **three parallel keyboard subsystems**, and they don't share layout, state, or interaction model:

| Subsystem | File | Where it runs | Purpose |
|---|---|---|---|
| **Cover overlay keyboard** (`TYPE_APPLICATION_OVERLAY` on cover display 1) | [CoverScreenInputInjectionEngine.kt](app/src/main/java/com/tyejaedon/coverscreenos/overlay/input/CoverScreenInputInjectionEngine.kt) — `CoverKeyboardOverlayManager`, `CoverKeyboardOverlayRoot` | Third-party apps launched on cover screen (Chrome, M-Pesa, WhatsApp) | Injects text into the focused editable via `AccessibilityNodeInfo.ACTION_SET_TEXT` / `ACTION_PASTE` |
| **IME** — `CoverT9InputMethodService` | [CoverT9InputMethodService.kt](app/src/main/java/com/tyejaedon/coverscreenos/ime/CoverT9InputMethodService.kt) | Only when the user sets it as the system IME | Standard IME using `commitText` via `InputConnection` |
| **In-launcher search keyboard** | [OverlaySearchWidgets.kt](app/src/main/java/com/tyejaedon/coverscreenos/ui/launcher/OverlaySearchWidgets.kt) `CoverSearchKeyboardHost` | Our own launcher grid search | Local Compose state — no injection at all |

The single piece of *shared* UI code is `CoverCompactQwertyKeyboard` in [CoverCompactQwertyKeyboard.kt](app/src/main/java/com/tyejaedon/coverscreenos/ui/keyboard/CoverCompactQwertyKeyboard.kt).

### 1a. Layout details, per keyboard

**Cover overlay (`CoverKeyboardOverlayRoot`)**
- Header row: status LED + package label + `123 / ABC / SYS` mode chips + close (`x`)
- Live buffer read-out (with `n chars` counter, monospace mask when password)
- Body: one of three keypads
  - `NUMERIC_PIN`: 3×3 digits + `0`, `CLR`, `⌫`, `DONE` — 44 dp targets
  - `T9_MULTITAP`: classic phone-pad + multi-tap cycle (850 ms window) — 44 dp targets (currently commented out from the mode chips; only reachable as an initial default)
  - `QWERTY` (via `CoverCompactQwertyKeyboard`): 10/9/7 + shift/backspace + space/done — **36 dp targets, 2 dp gaps, 14 sp letters**
- Vibration: `EFFECT_CLICK` per key
- Window flags: `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_WATCH_OUTSIDE_TOUCH`, gravity `BOTTOM|CENTER`, `WRAP_CONTENT`

**T9 IME (`CoverT9InputMethodService`)**
- Header text "CoverScreenOS T9" + field-type label
- 3×3 T9 grid with digit + letter subtitle — **58 dp targets** (much bigger than the overlay)
- Row 4: `CLR | 0/SPACE | ⌫` at 54 dp
- Row 5: `ENTER | HideKeyboard` at 50 dp
- Uses `EditorFieldProfile` to derive numeric/sensitive, calls `t9Engine.onTap` for multi-tap

**Launcher search**
- Reuses `CoverCompactQwertyKeyboard` but wrapped in a translucent Material 3 Surface

### 1b. Injection pipeline (cover overlay)

Focus detection → keyboard shows:
1. `onAccessibilityEvent` filters to `event.displayId == COVER_DISPLAY_ID`.
2. `handleFocusOrClickEvent` climbs the source node, opens a `FIELD_FOCUS_GRACE_MS = 750 ms` window (to swallow the WINDOW_STATE_CHANGED echo from our own overlay), calls `suppressHoneyBoardSoftKeyboard()` (`SHOW_MODE_HIDDEN`), builds `CoverFieldMetadata`, and calls `CoverInputSessionManager.onFieldFocused(metadata)`.
3. `onFieldFocused` picks default mode: `NUMERIC_PIN` if numeric/phone/password, else `T9_MULTITAP` (note: the T9 chip is commented out, so users get T9 by default but can only switch to 123 or ABC).
4. Overlay is attached via `WindowManager.addView` on the cover display context.

Per keypress:
1. Compose UI calls `CoverInputSessionManager.appendText(...)` / `replacePreviousChar(...)` / `deleteBackward()`.
2. Session manager keeps its own `buffer` + `cursorPosition` and calls `performDirectInjection(buffer)`.
3. `injectTextIntoFocusedNode` resolves the target node (walking every interactive window and skipping our own package to avoid pointing at the overlay), then does `ACTION_FOCUS + ACTION_ACCESSIBILITY_FOCUS + ACTION_SET_TEXT` with the *entire buffer*, then `ACTION_SET_SELECTION` to place the cursor at end.
4. Fallback: clipboard + `ACTION_PASTE`.
5. Echo-suppression: `isRecentSelfEcho` ignores text-change events within `INJECTION_ECHO_IGNORE_WINDOW_MS = 600 ms`.

DONE:
- `performDirectInjection` (final) → `dispatchActionDone` which does `ACTION_CLICK` (fallback `ACTION_NEXT_AT_MOVEMENT_GRANULARITY`) then `dismissOverlay` after 250 ms.

### 1c. Key design shortcuts / debt

1. **Whole-buffer resend on every keystroke.** Each tap re-writes the *entire* field via `ACTION_SET_TEXT`. On Chrome, WebView, React Native and password managers this can:
  - Trigger repeated on-change handlers (validation, autocomplete, network calls)
  - Break password-manager autofill
  - Cause visible flicker/cursor jumps
  - Hurt latency linearly with buffer length
    IME-based apps get one delta per keystroke via `InputConnection.commitText`.
2. **No `InputConnection` in the overlay path.** The cover-screen keyboard is *not* an IME — it's an overlay that impersonates typing via accessibility. This is a deliberate choice (single IME per user, Samsung One UI restrictions on cover displays), but it means:
  - No `EditorInfo.imeOptions` action label (users see generic "DONE" no matter what the field wants — Search/Go/Send/Next)
  - No `getTextBeforeCursor` / `getTextAfterCursor`, so cursor never syncs from app to overlay
  - No proper `sendKeyEvent(KEYCODE_ENTER)` — we call `ACTION_CLICK` on the field, which does not match Enter semantics for search boxes, chat compose fields, or forms with multiple submit paths
  - No composing region → no autocorrect, no glide typing, no swipe input
3. **`CoverCompactQwertyKeyboard` HCI issues** (used by both cover overlay and launcher search):
  - **36 dp key height** with **2 dp spacing** is below Google's 48 dp minimum touch target (Material spec) and well below Nielsen's 7–10 mm recommendation. On the cover display resolutions we see in the logs (748×720, density 2.125), 36 dp ≈ 76 px ≈ 8.8 mm — borderline; the T9 IME with 58 dp is significantly better.
  - **Shift indicator uses `v` / `^`** as labels; users expect ⇧/⇪ or capital preview. There's no caps-lock (double-tap-to-lock), no auto-capitalization on sentence start, no visual state for "next character will be uppercase".
  - **No number/symbols row and no long-press digits.** To type an email or a URL, users can't reach `@`, `.`, `/`, digits or common punctuation without dropping to `123` (which is a PIN pad, not a symbols page — so still no `@`, `.com`, `-`, `_`).
  - **No key preview (popup)** on touch — historically the single biggest HCI feature of Android/iOS soft keyboards (BlackBerry, Gboard, SwiftKey all show a magnified key). Given the tiny cover display this is essential visual feedback.
  - **Space bar labelled `SPACE`, DONE labelled `DONE`**; excessive ALL-CAPS labels waste width and increase glance time. Standard iconography would fit better.
  - **No repeat-on-hold backspace**, no swipe-left-on-space for backspace, no cursor arrows.
  - **CLR + DONE + SPACE row is shorter (34 dp) than the letter rows (36 dp)** — inconsistent hit targets.
  - **Shift is a top-level Compose `mutableStateOf`** inside the composable — resets on every recomposition when the parent changes; also no `Locked` state.
4. **T9 mode is hidden.** Lines 1198–1202 comment out the T9 chip in the header; but T9 is still the *default* for non-numeric fields (line 246). So a user typing on Chrome sees a T9 pad they can't switch back to once they leave, and the app auto-defaults them into it — a discoverability trap.
5. **Mode is not remembered per field / per app.** `keyboardStrategy` DataStore preference exists (T9 vs. SYSTEM_IME) but only affects the launcher search, not the injection overlay. The overlay always recomputes from `metadata.isNumeric`.
6. **No IME action awareness.** The overlay's `DONE` always fires `ACTION_CLICK` on the field, regardless of whether the field's `imeOptions` says `IME_ACTION_SEARCH`, `IME_ACTION_SEND`, `IME_ACTION_NEXT`, `IME_ACTION_GO`. Compare with `CoverT9InputMethodService.handleEnterPressed` which correctly reads `EditorInfo.IME_MASK_ACTION`. The overlay path has no `EditorInfo` at all.
7. **Password masking is on the overlay's buffer only.** The target field still contains real text at all times (as it must for injection). If a screen recorder is running or an accessibility log is captured, plaintext is visible during typing. Also, injecting a partial password on every keystroke means autofill/password-strength UIs constantly re-evaluate.
8. **No layout persistence, no locale.** `qwertyuiop` is hard-coded — no AZERTY, QWERTZ, Dvorak, Colemak; no RTL support; no localized labels.
9. **No touch feedback beyond a global `EFFECT_CLICK` vibration.** No visual pressed state on `KeypadButton` / `CoverKeypadButton` (only ripple from Material `clickable`, which on a `Box(clip → background)` is *clipped away* — the ripple is drawn on the box that has `clickable`, but the `background` sits under it so the ripple *does* show; still, no scale/color animation, no key-preview bubble).
10. **Overlay dismisses on any `WINDOW_STATE_CHANGED` outside the 750 ms grace + when focus is lost.** Log shows "Dismissing cover overlay: Field lost focus" firing 5+ times as the target app re-lays out. This dismiss-on-blur is aggressive; every scroll/dialog/toast in the host app kills the keyboard mid-typing.
11. **Just fixed** but worth flagging: the accessibility service subscribed to many event types with `TODO()` stubs that threw `NotImplementedError`. That whole `when` block is a design smell — the service should either not subscribe to those events or handle them with domain intent.
12. **Two composable copies of `KeypadButton`** (`KeypadButton` in the overlay file, `CoverKeypadButton` in the shared file), and two different visual languages for what is functionally the same key.

---

## 2. How notable launchers/covers handle this

**Samsung's own Cover Screen apps (Good Lock / MultiStar / Galaxy Z Flip cover)**
- Samsung uses a **stripped, single-row QWERTY at ~44–48 dp**, with **long-press-for-digits** (top row `q..p` doubles as `1..0`), and a numbers/symbols page reached from a single `?123` key. Space bar spans ~40% and includes the Enter-action icon on the right (SEARCH / SEND / GO changes glyph).
- On the Flip cover, Samsung's IME reduces itself in `onEvaluateFullscreenMode` and uses `SHOW_INPUT_REQUESTED` explicitly; they do **not** use overlays — they route through the standard IME framework and let the Compose window handle insets.
- Key-preview popup shown ~20 dp above the pressed key.
- Auto-capitalization on sentence start + double-tap-shift = caps lock.

**Gboard (compact / one-handed / floating)**
- One-handed mode presents a ~74% width QWERTY with a sidebar for `⌫ / space / enter / mic`.
- **Glide typing** on a 5.6 mm key is still usable because of language modelling; ideal for narrow cover displays.
- Symbols accessed via long-press on top row (digits), long-press on `.` (`,/?!@…`), long-press on letters for accents.
- Enter key morphs to a labelled action button using `EditorInfo` (⏎ / 🔍 / ➤ / →).
- Suggestion strip on top (3 candidates + emoji jump).
- IME insets are given to the app via `setImeWindowInsets` so app content scrolls with the keyboard.

**FlickBoard / OpenBoard / AnySoftKeyboard (open-source)**
- FlickBoard uses **12 keys with directional flick** (like Japanese kana input) — extremely compact, ~65×65 dp target. Perfect for narrow displays.
- OpenBoard exposes theming/layouts as JSON, supports 40+ languages.
- All show press-state (color + scale 0.95), key-preview popup, repeat-on-hold for backspace and arrows.

**Apple Watch scribble / QuickType**
- Confirms our overlay direction: on very small displays, Apple uses **word-level entry with a candidate strip**, not glyph-by-glyph typing. Scribble draws letters one at a time, QuickType shows 3-5 candidates.

**BlackBerry / Nokia T9 (historical)**
- Multi-tap (which is what our T9 mode does) is *the slow variant*. The fast variant is **predictive T9** (single tap per letter + dictionary disambiguates the word). Our `t9Engine` looks like it can do this — we should expose predictive candidates.

### Summary of what mature keyboards get right that we don't

| Feature | Samsung Flip | Gboard | OpenBoard | Our overlay | Our IME |
|---|---|---|---|---|---|
| Key-preview popup | ✅ | ✅ | ✅ | ❌ | ❌ |
| ≥ 44 dp targets | ✅ | ✅ | ✅ | ⚠️ 36 dp | ✅ 58 dp |
| Auto-cap / shift lock | ✅ | ✅ | ✅ | ❌ | n/a |
| Enter action from `imeOptions` | ✅ | ✅ | ✅ | ❌ | ✅ |
| Long-press for digits/symbols | ✅ | ✅ | ✅ | ❌ | ❌ |
| Suggestion strip / predictions | ✅ | ✅ | ✅ | ❌ | ❌ |
| Repeat-on-hold backspace | ✅ | ✅ | ✅ | ❌ | ❌ |
| Delta commits (no full re-set) | ✅ IME | ✅ IME | ✅ IME | ❌ full ACTION_SET_TEXT | ✅ commitText |
| Composing region + autocorrect | ✅ | ✅ | ✅ | ❌ | ❌ |
| Symbols page | ✅ ?123 | ✅ ?123 | ✅ | ❌ (only 123 PIN pad) | ❌ |
| Haptic per key | ✅ | ✅ | ✅ | ✅ | ❌ |

---

## 3. Recommended redesign

### 3a. Architecture: pick one primary path and instrument the other

The overlay path exists because Samsung One UI blocks arbitrary IMEs on cover displays for some apps. But it's the *inferior* path — it can't compose, can't handle IME actions, and forces full-buffer resends.

**Proposal:**

1. **Promote `CoverT9InputMethodService` to the primary text-entry engine** and give it a QWERTY variant so it can serve *any* field (not just numeric). Route through `InputConnection` for delta commits, composing text, and correct enter behaviour.
2. **Keep the overlay path as a fallback**, but:
  - Detect at focus time whether the target app respects `EditorInfo`-based IMEs on cover display (probe by checking if `SHOW_MODE_HIDDEN`+focus opens the system IME — if it does, our overlay is redundant).
  - When we do use the overlay, **switch from `ACTION_SET_TEXT(whole buffer)` to delta commits**: keep the pre-focus baseline text, and for each keystroke send only the appended char via a `getSelectionEnd()` + `SET_SELECTION` + `SET_TEXT` on the *new* substring, or via `performAction(ACTION_IME_ENTER)` where possible.
3. **Unify the buffer model** in a `TextBuffer` value type (text, selection start/end, composing start/end) used by both the IME and the overlay, so the same UI can render either.
4. **Sunset the standalone `CoverKeypadButton` duplicate** and share one `KeypadButton` composable with press-state + preview API.

### 3b. Layout: a single adaptive keyboard component

Design a new `CoverAdaptiveKeyboard` composable that hosts three *layouts* rendered by the same key primitives:

- **Numeric** — 3×4 grid, 56 dp targets, unchanged.
- **QWERTY-compact** — 10/9/9 columns (row 3 gains `⇧` and `⌫`), row 4 = `?123 | , | space | . | ⏎action`; letter targets 48 dp min, gaps 4 dp. Long-press top row for digits, long-press `.` for `,;:!?@…`. Include a **key-preview popup** (offset −40 dp, ~1.6× scale).
- **Symbols** (new) — `1..0` row, `-/:;()$&@"` row, `.,?!'` row, `ABC | space | ⏎`.

All three share:
- **Enter-key morphing** driven by `imeOptions` (search 🔍, send ➤, go →, next ⏭, done ✓).
- **Shift state machine**: `Off → Once → Locked` (double-tap), auto-set on sentence start.
- **Repeat-on-hold** for `⌫` and arrow keys.
- **Suggestion strip** (a 32 dp row above the keys) that:
  - In T9 mode: shows predictive candidates from `t9Engine`.
  - In QWERTY mode: shows autocorrect + next-word predictions (backed by a small trigram model or the on-device `TextClassifier`).
  - Tapping a candidate commits it and consumes the current composing region.
- **Cursor row** (optional, toggled from a chip): `◀ ▶` + `⌫`.

### 3c. Interaction / feedback

- Visual press state on every key: `scale=0.92, backgroundColor=+8% luminance` on `POINTER_DOWN`, `keyPreview` popup fades in for 60 ms.
- Haptic tiers: `EFFECT_TICK` for letters, `EFFECT_CLICK` for space/backspace, `EFFECT_HEAVY_CLICK` for Enter/action commit. Respect `Settings.System.HAPTIC_FEEDBACK_ENABLED`.
- Long-press timing = 350 ms (Android system default); disable if user has "reduce motion" or accessibility long-press-timeout set.
- Space bar drag-left = cursor movement (Gboard convention); drag-right past ~40 px = delete word.

### 3d. Focus / lifecycle robustness

Current dismiss triggers are too aggressive. Refactor `onFieldLostFocus` to require *two* signals:
1. A `TYPE_VIEW_FOCUSED` event on a **non-editable** node in the target package.
2. `resolveTargetInputNode()` returning `null` for **≥ 400 ms** continuously (debounce).

Log line `Dismissing cover overlay: Field lost focus` currently repeats 6× in a row after Chrome launches — this is entirely because Chrome re-lays out. That churn shouldn't dismiss us if the target field is still there.

Also: extend `FIELD_FOCUS_GRACE_MS` to cover Chrome's WebView bootstrap (observed ~1.2 s in logs); make it adaptive by watching for `TYPE_WINDOW_CONTENT_CHANGED` from the same package.

### 3e. Correct commit semantics

Replace `dispatchActionDone`'s `ACTION_CLICK` with:

```kotlin
fun dispatchImeAction() {
    val node = resolveTargetInputNode() ?: return
    val editorAction = node.extras?.getInt("android.view.inputmethod.EditorInfo.imeOptions", 0)
        ?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_UNSPECIFIED
    val actionId = when (editorAction) {
        EditorInfo.IME_ACTION_SEARCH -> AccessibilityNodeInfo.ACTION_IME_ENTER
        EditorInfo.IME_ACTION_SEND -> AccessibilityNodeInfo.ACTION_IME_ENTER
        EditorInfo.IME_ACTION_GO,
        EditorInfo.IME_ACTION_DONE -> AccessibilityNodeInfo.ACTION_IME_ENTER
        EditorInfo.IME_ACTION_NEXT -> AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT
        else -> AccessibilityNodeInfo.ACTION_CLICK
    }
    node.performAction(actionId)
}
```

`ACTION_IME_ENTER` (API 30+) is exactly the accessibility hook for this and we're on `targetSdk=37`.

### 3f. Predictive / T9 improvements

- Wire `t9Engine` predictions to a **candidate strip** — even 3 words hugely improves throughput vs. multi-tap.
- Add a small **on-device word list** (top 20k English + user's contacts + installed app names) for the QWERTY autocorrect path — `~200 KB` compressed, matches Gboard's minimum viable dictionary.
- Persist per-app last-mode: SharedPreferences keyed by `metadata.packageName` so returning to WhatsApp uses QWERTY and returning to M-Pesa uses NUMERIC without a chip tap.

### 3g. Security / privacy

- For password fields (`metadata.isPassword`), **defer injection to Enter**: type into overlay buffer only, inject the full text once on `DONE`. This eliminates per-keystroke autofill probing and shortens the plaintext window in accessibility logs.
- Add `FLAG_SECURE` on the overlay window when `isPassword == true` to block screenshots.
- Do not put password text on the clipboard fallback path — fail closed instead.

### 3h. Consistent HCI polish

- Replace `v` / `^` shift labels with `⇧` (Material icon `KeyboardArrowUp`) and add a solid indicator for caps-lock.
- Replace ALL-CAPS `SPACE`/`DONE`/`CLR`/`ENTER` with icons + tooltip.
- Match all target heights within a keypad (36 vs. 34 vs. 44 is jarring). Compute from cover-display height: `keyH = displayH / 8` clamped to `[44, 64]` dp.
- Use `Modifier.pointerInput` with `awaitFirstDown()` for immediate press response instead of `clickable` (which waits for click confirmation and adds 100 ms perceived latency).

### 3i. Discoverability

- Never default to a mode the user cannot leave. Re-enable the `T9` chip (`OverlaySearchWidgets.kt` line 1198 comment) OR change the default for non-numeric fields to `QWERTY`.
- On first attach per app, briefly show a 2-second coach-mark: "Tap `SYS` to switch to your regular keyboard."

### 3j. Testing gaps

`CoverAppGridOverlayInputModeInstrumentedTest` covers the `KeyboardStrategy` toggle in the *launcher search*. There are no tests for:
- The overlay attaching on focus.
- `injectTextIntoFocusedNode` producing correct text on Chrome/M-Pesa/etc.
- The IME action mapping.
- Dismiss-suppression during window churn.

Add unit tests for a new `TextBuffer` type and instrumented tests that spin up a fake accessibility service + fake target Activity.

---

## 4. Concrete migration plan (in order)

1. **Extract shared primitives** — `KeyboardKey`, `KeyPreview`, `SuggestionStrip`, `TextBuffer` — into `ui/keyboard/`.
2. **Rewrite `CoverCompactQwertyKeyboard`** on top of them, add symbols page, IME-action-aware Enter, shift state machine, long-press digits, key-preview popup, repeat-on-hold. Do this once — both the launcher search and the cover overlay pick it up.
3. **Refactor `CoverInputSessionManager`** to store a `TextBuffer` (with composing region) and expose `commitDelta(text)` / `setComposing(text)` / `finishComposing()` instead of `appendText`/`replacePreviousChar`. Implement `commitDelta` on top of `ACTION_SET_TEXT` **only for the delta range** using `ACTION_SET_SELECTION` first, or via `ACTION_PASTE` with a scratch clipboard for larger inserts.
4. **Add `EditorInfo` extraction** from the source node (`extras` bundle carries `EditorInfo.imeOptions` on modern Android; when absent, infer from `InputType`). Route Enter via `ACTION_IME_ENTER`.
5. **Debounce dismiss** to require two consecutive "no editable target" signals + 400 ms.
6. **Upgrade the T9 IME** to use the shared primitives and add a QWERTY layout selector inside the IME. Users who install our IME then get the full experience regardless of overlay.
7. **Per-app mode memory** in DataStore.
8. **Suggestion strip** wired to `t9Engine` first, dictionary autocorrect second.
9. **FLAG_SECURE and deferred injection** for password fields.
10. **Remove dead code**: `InjectionMethod` enum, `TRANSIENT_SYSTEM_UI_PREFIXES` array, the standalone `CoverKeypadButton`, and the commented-out T9 chip (either reinstate or remove entirely).

This sequence keeps the app runnable at every step: (1)-(2) improve UX immediately, (3)-(4) fix injection correctness, (5)-(9) unlock the "feels like a real keyboard" delta.