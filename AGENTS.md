# AGENTS.md

Machine-readable operating guide for AI coding agents working in the Cover-Screen-OS repository.
Human contributors should read `README.md` first.

---

## 1. Project Identity & Purpose

Cover-Screen-OS is a specialized Android application designed to transform the outer cover display of clamshell foldable smartphones (such as Samsung Galaxy Z Flip series) into a fully functional desktop/launcher environment.

Because OEMs restrict replacing the cover screen's default Home app, Cover-Screen-OS operates primarily via an **accessibility-service-hosted overlay** (`TYPE_ACCESSIBILITY_OVERLAY`) attached to the secondary hardware display detected via `DisplayManager`.

| Property        | Value                                                           |
|-----------------|-----------------------------------------------------------------|
| Language        | Kotlin `2.4.10`                                                 |
| AGP             | `9.3.2`                                                         |
| JDK             | 17                                                              |
| Min/Target SDK  | 28 / 35                                                         |
| UI Framework    | Jetpack Compose (BOM `2026.08.00`), Material3 `1.4.0`          |
| Visual Effects  | Haze `1.7.3`                                                    |
| Testing         | JUnit 4, Robolectric `4.16.1`, MockK `1.14.11`                  |
| Persistence     | DataStore Preferences                                           |

---

## 2. Core Subsystems & Boundaries

```
Cover-Screen-OS/
├── app/src/main/java/com/coverscreen/
│   ├── core/           # Runtime orchestration, shared configuration, contracts
│   ├── overlay/        # AccessibilityService overlay hosting (TYPE_ACCESSIBILITY_OVERLAY)
│   ├── homescreen/     # Cover homescreen root, setup, and entry points
│   ├── launcher/       # App grid, dock, launcher rendering, widget host
│   ├── notifications/  # NotificationListenerService, media and call signals
│   ├── permissions/    # Accessibility and overlay permission onboarding flows
│   └── platform/       # DisplayManager, WindowManager fold state, system adapters
```

### Architectural Rules
1. **Secondary Display Isolation**:
   - Never assume default display (`DEFAULT_DISPLAY`). All cover UI and overlays must be explicitly associated with the cover `Display` obtained from `DisplayManager`.
   - Activities launched on the cover display must pass `ActivityOptions.makeBasic().setLaunchDisplayId(coverDisplayId)`.
2. **Overlay Stability**:
   - The overlay is hosted by `AccessibilityService` using `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY`.
   - Ensure touch dispatch and back-gesture handling properly route to the underlying target when minimized or inactive.
3. **Folding Posture Awareness**:
   - Use Jetpack `WindowManager` folding features to determine posture (Flat, Half-Folded, Closed). The cover screen overlay must smoothly transition state based on fold posture events.

---

## 3. Canonical Quality Toolchain

Run these commands in order from the repository root on Windows:

```powershell
# 1. Unit & Robolectric tests
.\gradlew.bat :app:testDebugUnitTest

# 2. Lint check
.\gradlew.bat :app:lintDebug

# 3. Assemble check
.\gradlew.bat :app:assembleDebug
```

### Single Test Execution (Token Efficient)
```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.coverscreen.*TestClassName*"
```

---

## 4. Guardrails & Best Practices

- **Resource Cleanup**: Always unregister display listeners and fold state collectors in `onDestroy()` / `onServiceDisconnected()`.
- **Battery Optimization**: Do not perform heavy computations on the main thread during display change events. Keep animations hardware-accelerated.
- **OEM Compat**: Account for Samsung OneUI specific behaviors (e.g., screen off / AOD handoff).
