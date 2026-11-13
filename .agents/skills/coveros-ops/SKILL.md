---
name: coveros-ops
description: Operational runbooks, Robolectric test suite execution, display lifecycle verification, and overlay checks for Cover-Screen-OS.
---

# Cover-Screen-OS Operations Skill

This skill provides fast, low-token operational workflows for testing and validating Cover-Screen-OS.

## 1. Fast Test Execution (Robolectric & Unit Tests)

Run concise test runs to verify overlay and lifecycle behaviors:

```powershell
# Run all unit and Robolectric tests concisely
.\gradlew.bat :app:testDebugUnitTest --quiet
```

Target specific subsystems to reduce log noise and token usage:

```powershell
# Test overlay lifecycle and display management
.\gradlew.bat :app:testDebugUnitTest --tests "com.coverscreen.overlay.*"

# Test launcher and widget hosting
.\gradlew.bat :app:testDebugUnitTest --tests "com.coverscreen.launcher.*"

# Test notification listener pipeline
.\gradlew.bat :app:testDebugUnitTest --tests "com.coverscreen.notifications.*"
```

## 2. Compile & Resource Verification

Check Kotlin compilation and Compose UI compilation:

```powershell
.\gradlew.bat :app:assembleDebug -x test
```

## 3. Secondary Display & Accessibility Testing Checklist
When verifying features involving secondary displays:
- Confirm `DisplayManager.getDisplays()` filtering logic.
- Verify `ActivityOptions.setLaunchDisplayId` is set when launching external apps onto cover screen.
- Verify overlay parameters use `FLAG_NOT_TOUCH_MODAL` or `FLAG_NOT_FOCUSABLE` where appropriate to avoid trapping touches.
