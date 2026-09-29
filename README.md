# 📱 Cover Screen OS

![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue?logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-green?logo=jetpackcompose)
![Android](https://img.shields.io/badge/Android-Overlay-orange?logo=android)
![License](https://img.shields.io/badge/License-MIT-lightgrey)
![Build](https://img.shields.io/badge/Build-Passing-brightgreen)

**Cover Screen OS** is a specialized Android application designed to transform the outer cover display of clamshell foldable smartphones (such as the Samsung Galaxy Z Flip series) into a fully functional, customizable desktop environment.

Since Android OEMs (like Samsung) do not natively allow replacing the cover screen's default Home App via standard system settings, Cover Screen OS uses an **accessibility-service-hosted overlay** on the cover display by default. It detects the secondary hardware display and attaches an interactive Jetpack Compose UI to launch full Android applications, render interactive widgets, and display notifications.

---

## Project Organization

A staged module-organization roadmap is documented in `docs/architecture/module-organization-plan.md`.

---

## 🏗 System Architecture Blueprint

+-----------------------------------------------------------------------+
|                            COVER Screen OS ENGINE                            |
+-----------------------------------------------------------------------+
|
+-------------------------+-------------------------+
|                                                   |
v                                                   v
+-------------------------+                         +-------------------------+
| Hardware & State Subsys |                         | Window & Layout Subsys  |
+-------------------------+                         +-------------------------+
| • DisplayManager        |                         | • Accessibility service |
|   (Target Cover Display)|                         |   (TYPE_ACCESSIBILITY_  |
| • Jetpack WindowManager |                         |    OVERLAY)             |
|   (Hinge / Fold State)  |                         | • Jetpack Compose       |
| • Foreground Service    |                         |   (Cover UI Viewport)   |
+-------------------------+                         +-------------------------+
|                                                   |
+-------------------------+-------------------------+
|
+-------------------------+-------------------------+
|                                                   |
v                                                   v
+-------------------------+                         +-------------------------+
| App & Widget Engine     |                         | System Intercept Subsys |
+-------------------------+                         +-------------------------+
| • ActivityLauncher      |                         | • AccessibilityService  |
|   (setLaunchDisplayId)  |                         |   (Back / Home Gestures)|
| • AppWidgetHost         |                         | • NotificationListener  |
|   (3rd-Party Widgets)   |                         |   (Read / Dismiss / UI) |
+-------------------------+                         +-------------------------+


### 🔍 Core Subsystem Breakdown
1. **Hardware & State Subsystem**
   - Detects secondary display via `DisplayManager`
   - Monitors hinge/fold state with Jetpack `WindowManager`
   - Runs persistent Foreground Service to survive OEM battery optimizations  

2. **Window & Layout Subsystem**
   - Attaches `ComposeView` overlays to secondary display
   - Provides adaptive grid layouts optimized for cover screen  

3. **App & Widget Execution Engine**
   - Routes apps to cover display via `ActivityOptions.setLaunchDisplayId()`
   - Hosts third-party widgets using `AppWidgetHost`  

4. **System Intercept Subsystem**
   - Captures navigation gestures with `AccessibilityService`
   - Displays and manages notifications via `NotificationListenerService`

---

## ⚙️ Technical Stack & Android APIs

| **Layer** | **Technology / API** | **Function** |
|-----------|-----------------------|--------------|
| Language | Kotlin | Core application development |
| UI Framework | Jetpack Compose | Reactive layout rendering |
| Concurrency | Coroutines & Flow | Async event processing |
| Display API | `DisplayManager` | Detect secondary hardware display |
| Window API | `WindowManager` | Overlay views with `TYPE_ACCESSIBILITY_OVERLAY` |
| App Routing | `ActivityOptions` | Force app execution on cover display |
| Widget Engine | `AppWidgetHost` | Embed third-party widgets |
| Permissions | System Services | Accessibility, Notification Listener, Notifications, Battery exemption |

---

## 🔑 Required Permissions

- `POST_NOTIFICATIONS` – Persistent foreground notification (Android 13+)
- `BIND_ACCESSIBILITY_SERVICE` – Intercept gestures  
- `BIND_NOTIFICATION_LISTENER_SERVICE` – Read/dismiss notifications  
- `QUERY_ALL_PACKAGES` – Build custom app drawer  
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` – Prevent background killing  
The inner-display setup screen guides users through notifications, **Cover Screen OS Launcher** accessibility, notification access, and battery exemption with per-step status checks and system-settings shortcuts. For the cover keyboard fallback, also enable the separate **Cover Screen OS** input accessibility service; the readiness screen tracks both services independently. The launcher can start without the input service when using a system IME. Upgrades ignore the former legacy-host preference, so users who previously enabled only the input service must enable the launcher service too. Neither overlay requires the Draw over other apps permission.

---

## Install and use the CoverScreenOS keyboard

1. Install the app APK on the phone (or run the `app` configuration from Android Studio).
2. Open Android **Settings → System → Keyboard → On-screen keyboard → Manage on-screen keyboards** and enable **CoverScreenOS Keyboard**. On Samsung phones, look under **General management → Keyboard list and default** instead.
3. Open a text field and use the system keyboard picker (keyboard icon in the navigation bar or **Change keyboard** in the notification shade) to select **CoverScreenOS Keyboard**. To use it everywhere by default, set it as the default keyboard in the same system settings page.
4. The keyboard starts in QWERTY for text and numeric mode for number, phone, and date/time fields. Tap **ABC**, **T9**, **123**, or **#+=** to change layouts while typing. From the keyboard's entry in Android's keyboard settings, open **Settings** to choose a separate default layout for each field class.

The system IME writes through each app's `InputConnection`; it does not require accessibility-based text injection. Enabling the keyboard is separate from granting accessibility access for the cover launcher.
Contact-name suggestions are optional and require explicit consent in keyboard Settings plus a `READ_CONTACTS` grant; turning either off disables them.

---

## Launcher Customization Hub

The in-app home surface now includes a launcher customization hub focused on cover-display ergonomics.

- **Dock management:**
  - Long-press + drag to reorder four dock slots.
  - Haptic feedback and lift/shadow animation while dragging for tactile precision.
  - Neighboring slots softly "snap" aside during drag to clarify insertion target.
  - Choose and clear apps per slot.
  - Dock slot choices persist across restarts.
- **Wallpaper controls:**
  - Pick any local image URI as cover wallpaper.
  - Choose wallpaper scale mode: `Crop` or `Fit`.
  - Tune readability with dim overlay and blur sliders.
  - One-tap reset restores dock + wallpaper style defaults, with an Undo snackbar.
  - Settings persist and are applied live to the overlay launcher.

Settings are persisted with AndroidX DataStore in `LauncherSettingsStore`.

## Cover Launch Validation Checklist (Real Device)

Validate multi-display launch routing on target foldable hardware after each launcher-routing change:

- **Overlay hide/recover timing:**
  - Tap app from grid, dock, and search.
  - Confirm launcher overlay hides immediately before app focus transfer.
  - Confirm overlay resumes only when lock/system foreground signals indicate safe restoration.
- **Window focus transfer:**
  - Confirm launched app receives input focus instantly on the cover panel.
  - Verify back/home gestures return cleanly without stuck overlay suppression.
- **Cover viewport + density mapping:**
  - Confirm app windows open with correct bounds, scale, and density on the outer display.
  - Verify inner-display tasks remain untouched (no task migration from inner to cover).

---

## 🚀 Development Roadmap

**Phase 1:** Display detection & overlay boilerplate  
**Phase 2:** Multi-display app routing & launcher drawer  
**Phase 3:** Widget integration & dashboard  
**Phase 4:** Gesture navigation & notification center  
**Phase 5:** Input handling & edge case hardening  

---

## ⚡ Technical Challenges & Mitigations

1. **Keyboard Lockout Issue**  
   - Mitigation: Custom T9 keypad or voice-to-text  

2. **OEM Background Process Killing**  
   - Mitigation: Persistent Foreground Service + user onboarding  

3. **Window Focus Conflicts**  
   - Mitigation: Pause overlay interception for secure windows  

---

## 🎓 Skills Demonstrated

- Advanced Android architecture (multi-display, WindowManager, IPC)  
- Modern UI engineering (Jetpack Compose + AndroidView interop)  
- System privilege operations (Accessibility, Notification Listener, Intent routing)  

---
