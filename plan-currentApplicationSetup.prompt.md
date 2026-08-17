### CoverOS GitHub Copilot Plan Prompt

> **Context excerpt from project documentation**  
> "CoverOS is a specialized Android application designed to transform the outer cover display of clamshell foldable smartphones (such as the Samsung Galaxy Z Flip series) into a fully functional, customizable desktop environment."  
> "It detects the presence of the secondary hardware display, attaches an interactive Jetpack Compose UI canvas directly to the outer screen's window manager, and acts as a gateway to launch full Android applications, render interactive widgets, and display notifications."

---

### Goals and Scope
**Primary goal:** produce a concrete, actionable development plan that GitHub Copilot can use to implement, harden, and test the core runtime features listed below.

**Scope includes**
- Map build and tooling baseline and produce reproducible build checks.
- Map AndroidManifest components and the startup flow end-to-end.
- Trace overlay, launch-routing, and wallpaper pipelines with failure paths.
- Identify reliability gaps and produce prioritized, implementable tasks with acceptance criteria and test cases.

---

### Tasks for Copilot to implement (ordered, with file paths and acceptance criteria)

1. **Map build and tooling baseline**
    - **Deliverables**
        - `docs/BUILD_BASELINE.md` describing AGP/Kotlin/Gradle versions and how to reproduce the build locally.
        - Gradle sanity script `scripts/ci-checks.sh` to run `./gradlew clean assembleDebug lint ktlint detekt`.
    - **Action items**
        - Read `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties`.
        - Add a `./gradlew :app:dependencies` snapshot to `docs/BUILD_BASELINE.md`.
        - Add a CI job snippet for GitHub Actions `ci/build.yml` that runs the sanity script.
    - **Acceptance criteria**
        - `./scripts/ci-checks.sh` exits 0 on a clean checkout on a machine with JDK 17.
        - `docs/BUILD_BASELINE.md` contains exact versions and commands to reproduce.

2. **Map manifest components and startup flow**
    - **Deliverables**
        - `docs/MANIFEST_FLOW.md` with a diagram and step-by-step startup sequence.
        - Unit test stubs and instrumentation test plan for startup gating.
    - **Action items**
        - Inspect `app/src/main/AndroidManifest.xml` and list components: `MainActivity`, Foreground service, AccessibilityService, NotificationListener, BootReceiver.
        - Trace runtime startup: `MainActivity` → `PermissionScreen` → `ForegroundServiceHelper` → `ForegroundService`.
        - Create a sequence diagram (ASCII or Mermaid) and include in `docs/MANIFEST_FLOW.md`.
        - Add a small instrumentation test that simulates missing permissions and asserts service start is blocked.
    - **Acceptance criteria**
        - `docs/MANIFEST_FLOW.md` contains component list, intent filters, and a Mermaid sequence diagram.
        - Test demonstrates the app does not mark itself "ready" when service start fails.

3. **Trace overlay, launch-routing, and wallpaper pipelines**
    - **Deliverables**
        - `docs/RUNTIME_PIPELINES.md` with three subsections: Overlay, Launch Routing, Wallpaper.
        - Annotated code pointers and failure-path tables referencing exact files.
    - **Action items**
        - Overlay: trace code in `app/src/main/java/.../services/OverlayWindowController.kt` and `ForegroundService.kt`. Document window types, attach flow, and focus handling.
        - Launch routing: trace `app/src/main/java/.../ui/controllers/CoverAppLauncher.kt` and document `ActivityOptions.setLaunchDisplayId()` usage and overlay hide/resume handshake.
        - Wallpaper: trace picker → persistence → render paths:
            - Picker: `ui/LauncherSettingsHub.kt`
            - Persistence: `datastore/Prefrences.kt`
            - Render: `ui/CoverAppGridOverlay.kt`, `ui/WallpaperBitmapCache.kt`, `ui/settings/WallpaperCustomizationComponents.kt`
        - For each pipeline produce a table of **inputs**, **transformations**, **outputs**, and **failure modes**.
    - **Acceptance criteria**
        - `docs/RUNTIME_PIPELINES.md` contains annotated file references and a failure-mode table for each pipeline.
        - Each failure mode includes a recommended mitigation and a one-line test to reproduce.

4. **Identify reliability gaps and immediate development priorities**
    - **Deliverables**
        - `docs/RELIABILITY_PRIORITIES.md` with prioritized list, estimated effort, and owner suggestions.
    - **Action items**
        - Address the four key gaps you listed:
            - Permission gating mismatch between `PermissionScreen` and `ForegroundServiceHelper`.
            - Display dependency when only display 0 is exposed.
            - Notification listener treated as required but not integrated into suppression decisions.
            - Release hardening for high-privilege permissions.
        - For each gap produce:
            - Root cause hypothesis.
            - Concrete code changes (file + function) to implement.
            - Tests and runtime diagnostics to add.
    - **Acceptance criteria**
        - Each gap has a one-commit fix plan and a test that fails before the fix and passes after.

5. **Wallpaper UX reliability pass**
    - **Deliverables**
        - Code changes and tests to ensure “never silently black” behavior.
        - Runtime diagnostics and telemetry logs for decode inputs.
    - **Action items**
        - Add explicit decode state machine in `datastore/Prefrences.kt` and `ui/CoverAppGridOverlay.kt` with states: `IDLE`, `LOADING`, `RETRYING`, `FAILED`, `READY`.
        - Surface decode status in `WallpaperCustomizationComponents.kt` UI with explicit messages and retry button.
        - Add diagnostic logging for: URI scheme, file length, decode bounds, decode attempts, and last error.
        - Implement a fallback placeholder (non-black) and a visible failure card that explains the reason and offers retry.
    - **Acceptance criteria**
        - When decode fails, overlay shows a failure card with the last error and a retry action.
        - Logs include URI scheme and file length for every decode attempt.

---

### Tests and Diagnostics to add
- **Unit tests**
    - `CoverDisplayHelper` selection logic with mocked `DisplayManager` (cases: only default display, secondary present, transient display).
    - Permission gating logic: `PermissionScreen` vs `ForegroundServiceHelper`.
- **Instrumentation tests**
    - Simulate fold/unfold transitions and assert overlay attach/detach stability.
    - Simulate notification listener unavailable and assert suppression/resume behavior.
- **Runtime diagnostics**
    - Add `logs/coveros_diagnostics.log` entries for:
        - Display topology changes with timestamps.
        - Service start attempts and reasons for failure.
        - Wallpaper decode attempts with URI scheme and file length.
- **CI checks**
    - Lint rules and a `gradlew connectedAndroidTest` job for critical instrumentation tests.

---

### Pull Request template and labels
**PR template** (add to `.github/PULL_REQUEST_TEMPLATE.md`) should require:
- Summary of change
- Files changed and why
- How to test locally (commands)
- Acceptance criteria checklist (from above)
- Link to relevant docs in `docs/`

**Suggested labels**
- `area:build`, `area:manifest`, `area:overlay`, `area:wallpaper`, `priority:high`, `needs-review`, `ci-required`

---

### Copilot prompt block
Use this block as the direct prompt for GitHub Copilot when creating issues, commits, or PRs. Paste into a new issue or PR description.

```text
Task: Implement the next development sprint for CoverOS focusing on build baseline, manifest/startup mapping, runtime pipeline tracing, reliability gaps, and wallpaper UX hardening.

Context:
- Project path: C:\Cover-Screen-OS\app\src\main\java\com\tyejaedon\coverscreenos
- Key files: 
  - Manifest: app/src/main/AndroidManifest.xml
  - MainActivity: app/src/main/java/com/tyejaedon/coverscreenos/MainActivity.kt
  - Foreground service: app/src/main/java/com/tyejaedon/coverscreenos/services/ForegroundService.kt
  - Overlay controller: app/src/main/java/com/tyejaedon/coverscreenos/services/OverlayWindowController.kt
  - Display helper: app/src/main/java/com/tyejaedon/coverscreenos/helpers/CoverDisplayHelper.kt
  - App launcher: app/src/main/java/com/tyejaedon/coverscreenos/ui/controllers/CoverAppLauncher.kt
  - Accessibility service: app/src/main/java/com/tyejaedon/coverscreenos/services/CoverAccessibilityService.kt
  - Notification listener: app/src/main/java/com/tyejaedon/coverscreenos/services/CoverNotificationListenerService.kt
  - Wallpaper picker: app/src/main/java/com/tyejaedon/coverscreenos/ui/LauncherSettingsHub.kt
  - Wallpaper persistence: app/src/main/java/com/tyejaedon/coverscreenos/datastore/Prefrences.kt
  - Wallpaper render: app/src/main/java/com/tyejaedon/coverscreenos/ui/CoverAppGridOverlay.kt
  - Wallpaper cache: app/src/main/java/com/tyejaedon/coverscreenos/ui/WallpaperBitmapCache.kt
  - Wallpaper preview: app/src/main/java/com/tyejaedon/coverscreenos/ui/settings/WallpaperCustomizationComponents.kt

Deliverables:
1. docs/BUILD_BASELINE.md and scripts/ci-checks.sh
2. docs/MANIFEST_FLOW.md with Mermaid sequence diagram
3. docs/RUNTIME_PIPELINES.md with failure-mode tables
4. docs/RELIABILITY_PRIORITIES.md with prioritized fixes
5. Wallpaper reliability changes: state machine, UI status, diagnostic logs, and tests

Acceptance criteria:
- All docs added and referenced in PR
- CI script runs and exits 0 on a clean checkout
- Tests demonstrate permission gating and wallpaper failure behavior
- Overlay does not silently show black wallpaper; failure card appears with retry

Implementation notes:
- Add diagnostic logs for wallpaper decode: URI scheme, file length, decode bounds, attempt count, last error
- Add state enum for wallpaper decode states and persist minimal state in DataStore
- Make permission gating atomic: service start should only be attempted after required permissions are confirmed; otherwise app should show clear UI state "Waiting for permissions"

Create the PR with the above deliverables, include test results, and tag reviewers: @tyejaedon and @android-maintainer.
```

---

