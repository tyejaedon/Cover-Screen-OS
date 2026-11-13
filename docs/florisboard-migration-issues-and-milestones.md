# FlorisBoard Keyboard Migration: Milestones and Issues

This document records the milestones and issues tracking the migration of Cover-Screen-OS to the FlorisBoard engine and clamshell innovations.

---

## Milestone 10: [FlorisBoard Keyboard Migration & Innovations](https://github.com/tyejaedon/Cover-Screen-OS/milestone/10)

### Summary
Migrate from the custom legacy keyboard to an adapted FlorisBoard (`keyboard3`) engine, featuring the Unified `CoverEditorBridge`, Clamshell Ultra-Compact Layouts, T9 9-Key Hybrid Keypad, and low-memory optimizations for foldable cover displays.

---

### [Issue #152](https://github.com/tyejaedon/Cover-Screen-OS/issues/152): M1 - Modular Engine Extraction & Dependency Setup
- **Title**: `[FlorisBoard] M1: Modular Engine Extraction & Dependency Setup`
- **Labels**: `area:input`, `type:feature`, `priority:p1`
- **Summary**:
  - Set up `:keyboard-floris` Android Library module.
  - Configure `libs.versions.toml` with `k3lp-core`, `cache4k`, and Kotlin serialization.
  - Link `:keyboard-floris` to `:app` and ensure clean module compilation.
- **Status**: Completed on branch `feat/keyboard-m1-module-setup`.

---

### [Issue #153](https://github.com/tyejaedon/Cover-Screen-OS/issues/153): M2 - Unified Cover Editor Bridge & Overlay Multiplexing
- **Title**: `[FlorisBoard] M2: Unified Cover Editor Bridge & Overlay Multiplexing`
- **Labels**: `area:input`, `type:feature`, `priority:p0`
- **Summary**:
  - Implement `CoverEditorBridge` conforming to `K3Editor`.
  - Provide `CoverOverlayInputCallbacks` for accessibility node text injection and launcher search buffers.
  - Support `replaceText`, `deleteSurroundingText`, `moveCursor`, and `commitCandidate`.
- **Status**: Completed on branch `feat/keyboard-m1-module-setup`.

---

### [Issue #154](https://github.com/tyejaedon/Cover-Screen-OS/issues/154): M3 - Clamshell Layout Innovations & T9 Hybrid Mode
- **Title**: `[FlorisBoard] M3: Clamshell Layout Innovations & T9 Hybrid Mode`
- **Labels**: `area:input`, `ui/ux`, `type:feature`, `priority:p1`
- **Summary**:
  - Implement `ClamshellLayoutSpec` enforcing height limits (38%–44% of cover display) with a 34dp floor.
  - Build `CoverFlorisKeyboardSurface` with 1-tap instant toggle between QWERTY and T9 9-Key predictive keypad.
  - Add spacebar horizontal drag cursor scrub, one-handed edge anchoring (`LEFT`, `RIGHT`, `CENTER`), and hold-to-repeat backspace.
- **Status**: Completed on branch `feat/keyboard-m1-module-setup`.

---

### [Issue #155](https://github.com/tyejaedon/Cover-Screen-OS/issues/155): M4 - Memory Optimization & Secondary Display Lifecycle
- **Title**: `[FlorisBoard] M4: Memory Optimization & Secondary Display Lifecycle`
- **Labels**: `area:input`, `area:performance`, `type:perf`, `priority:p1`
- **Summary**:
  - Implement mmap-backed dictionary trie to keep active heap overhead below 38MB.
  - Unload Compose view tree and glyph cache textures when overlay is hidden or phone is unfolded.
  - Harmonize Snygg theme styling with Haze glassmorphism and Material3 tokens.
- **Status**: Scheduled.

---

### [Issue #156](https://github.com/tyejaedon/Cover-Screen-OS/issues/156): M5 - Quality Assurance, Automated Tests & Beta Release
- **Title**: `[FlorisBoard] M5: Quality Assurance, Automated Tests & Beta Release`
- **Labels**: `area:input`, `type:test`, `type:stability`, `priority:p1`
- **Summary**:
  - Add Robolectric and instrumented test coverage for overlay injection, fold posture changes, and IME actions.
  - Validate with LeakCanary for zero memory leaks during rapid typing.
  - Bump version and assemble signed/unsigned release APK.
- **Status**: Scheduled.
