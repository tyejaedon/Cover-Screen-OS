Goal:
Audit the incoming-call passthrough implementation end-to-end, identify edge-case behavior, and capture concrete pitfalls and missing tests before further hardening.

Scope:
- `app/src/main/java/com/tyejaedon/coverscreenos/services/overlay/ForegroundService.kt`
- `app/src/main/java/com/tyejaedon/coverscreenos/services/overlay/CoverAccessibilityService.kt`
- `app/src/main/java/com/tyejaedon/coverscreenos/services/notifications/CoverNotificationListenerService.kt`
- `app/src/main/java/com/tyejaedon/coverscreenos/services/CallPackageMatchers.kt`
- `app/src/main/java/com/tyejaedon/coverscreenos/services/overlay/OverlayReclaimPolicy.kt`
- `app/src/main/java/com/tyejaedon/coverscreenos/services/overlay/OverlaySuppressionState.kt`
- existing tests under `app/src/test` and `app/src/androidTest`

1) End-to-end incoming-call passthrough flow map

A) Accessibility-driven call signal (primary UI-surface detection)
1. `CoverAccessibilityService.onAccessibilityEvent(...)` listens for window events and tracks latest foreground package/time (`CoverAccessibilityService.kt:72-92`).
2. On `TYPE_WINDOW_STATE_CHANGED`, it checks if the package matches call prefixes via `CallPackageMatchers.isIncomingCallPackage(...)` (`CoverAccessibilityService.kt:100-118`, `288-290`, `CallPackageMatchers.kt:13-16`).
3. If matched, it calls `ForegroundService.requestIncomingCallPassthrough(package)` (`CoverAccessibilityService.kt:114-117`).
4. `ForegroundService` companion forwards that to the active service instance (`ForegroundService.kt:96-98`).
5. `requestIncomingCallPassthroughInternal(...)`:
   - tracks call passthrough signal in shared suppression state (`ForegroundService.kt:384-388`, `798-803`, `OverlayReclaimPolicy.kt:7-13`)
   - suppresses overlay with reason `INCOMING_CALL` if not already in incoming-call suppression (`ForegroundService.kt:389-395`, `569-581`).

B) Notification-driven call signal (secondary/ongoing-call guard)
1. `CoverNotificationListenerService` refreshes active notifications (`CoverNotificationListenerService.kt:252-284`).
2. It detects likely active call notifications using package/category/ongoing/text heuristics (`CoverNotificationListenerService.kt:307-334`).
3. It dispatches deduped call state to `ForegroundService.updateCallNotificationState(...)` (`CoverNotificationListenerService.kt:286-296`).
4. `ForegroundService.updateCallNotificationStateInternal(...)`:
   - updates `callNotificationActive/callNotificationPackage` (`ForegroundService.kt:398-404`)
   - if active, refreshes call timestamps and can force incoming-call suppression (`ForegroundService.kt:405-417`)
   - if inactive, triggers suppression-release evaluation (`ForegroundService.kt:420-424`).

C) Hold-suppressed and release path
1. Suppression poller runs every 60 ms while suppressed (`ForegroundService.kt:583-591`).
2. `maybeResumeOverlayAfterAppLaunch(...)` decides whether to keep suppression or release (`ForegroundService.kt:593-717`).
3. Incoming-call hold logic is delegated to `OverlayReclaimPolicy.shouldKeepOverlaySuppressedForIncomingCall(...)` (`ForegroundService.kt:636-643`, `818-826`, `OverlayReclaimPolicy.kt:32-71`).
4. Overlay reclaim requests are blocked during active/recent call signals via `shouldBlockReclaimForIncomingCall(...)` (`ForegroundService.kt:360-365`, `828-835`, `OverlayReclaimPolicy.kt:73-92`).
5. Once call hold conditions are gone and resume criteria pass, suppression is completed and overlay retargeted (`ForegroundService.kt:710-733`), and state is cleared (`ForegroundService.kt:845-851`, `OverlayReclaimPolicy.kt:15-30`).

2) Potential pitfalls and regression risks

- HIGH: Call passthrough can be bypassed when device is locked.
  - Evidence: `ForegroundService.kt:630-634` runs `resume_locked` before incoming-call hold check at `636-643`.
  - Risk: Incoming-call suppression may release immediately on locked devices, violating call-first priority.

- HIGH: Call package matching is over-broad (false positives likely).
  - Evidence: `CallPackageMatchers.kt:4-11` includes broad roots (`com.whatsapp`, `com.google.android.dialer`), matcher uses prefix `startsWith` (`13-16`). Trigger point: `CoverAccessibilityService.kt:114-117`.
  - Risk: Non-call windows from these apps can trigger incoming-call suppression behavior.

- MEDIUM: Notification call classifier is heuristic/English-centric.
  - Evidence: `CoverNotificationListenerService.kt:307-334` (category checks + text keywords `incoming call`, `ringing`, `calling`).
  - Risk: False positives and false negatives for localized or nonstandard notifications.

- MEDIUM: Suppression may release during ongoing call when foreground signal goes stale.
  - Evidence: Foreground stale cutoff at `ForegroundService.kt:794` (`> 2.5s` returns null). Release criteria in `OverlayReclaimPolicy.kt:61-70`.
  - Risk: If notification signal is unavailable and accessibility events are sparse, call suppression can clear while call remains active.

- MEDIUM: Cross-service state drift risk (dedupe + local state reset).
  - Evidence: Notification dedupe state `CoverNotificationListenerService.kt:57`, `286-291`; foreground-side clearing `ForegroundService.kt:845-851`, `OverlayReclaimPolicy.kt:24-29`.
  - Risk: If ForegroundService resets while listener still holds same deduped state, no redispatch occurs until state changes.

- MEDIUM: Incoming-call signals are dropped when ForegroundService instance is absent.
  - Evidence: Companion dispatch no-op if no active instance `ForegroundService.kt:96-102`; lifecycle ref set/clear `277`, `344-347`; callers `CoverAccessibilityService.kt:116`, `CoverNotificationListenerService.kt:292-295`.
  - Risk: Call-start signals can be lost across service downtime/restarts.

- LOW-MEDIUM: Incoming-call detection only evaluated on `TYPE_WINDOW_STATE_CHANGED`.
  - Evidence: Non-state events skipped for decisions `CoverAccessibilityService.kt:100-107`; call passthrough check after that (`114-117`).
  - Risk: OEM/event-pattern differences (mostly `TYPE_WINDOWS_CHANGED`) could delay/miss accessibility-side detection.

- LOW-MEDIUM: Notification open may proceed even if hide-overlay dispatch failed.
  - Evidence: Hide request attempted at `CoverNotificationListenerService.kt:92-99`; content intent path may return success early (`101-117`); warning only in fallback path (`119-121`).
  - Risk: Launch can succeed while suppression request fails, risking visual overlap during re-entry.

3) Missing test cases matrix

| Scenario | Signals | Timing/Race | Expected | Current coverage |
|---|---|---|---|---|
| Cellular call UI package appears (`com.samsung.android.incallui`) | Accessibility-only | Immediate | Overlay suppresses with `INCOMING_CALL`, reclaim blocked | Missing |
| WhatsApp incoming call UI vs normal WhatsApp chat screen | Accessibility | Immediate | Only call surface triggers call-mode suppression | Missing |
| Active call notification (`CATEGORY_CALL`) without accessibility event | Notification-only | Immediate | Incoming-call suppression still engages | Missing |
| Accessibility start first, notification start later | Both | Out-of-order start | Stable suppression, no duplicate regressions | Missing |
| Notification ends first, accessibility still on call surface | Both | Out-of-order end | Stay suppressed until both inactive (+grace) | Missing |
| Accessibility stale (>2.5s), notification unavailable/disconnected | Accessibility degraded | Stale-event path | Should not reclaim over active call | Missing |
| Reclaim request fired during active call | Reclaim + call active | Concurrent | `requestOverlayReclaim` blocked | Missing |
| Grace window boundaries | Call end | `<5s` and `>5s` | Block during grace, allow after | Missing |
| Device locked during incoming call | Lock + call | Poll loop | Call suppression should still dominate | Missing |
| ForegroundService restart while call still active | Service lifecycle + dedupe | Restart race | Call state re-synced to new service instance | Missing |
| Long call >45s and >2h | Timeout policy | Extended | No 45s reclaim in call mode; max failsafe behavior | Missing |
| Notification open path where hide dispatch fails but content intent succeeds | Re-entry | Failure injection | Deterministic behavior + explicit fallback handling | Missing |

4) Existing tests and coverage gaps

Existing tests found:
- `ForegroundServiceTest` only verifies static action/channel constants.
- `CoverAppLauncherTest` verifies hide-overlay service call ordering before app launch (indirect relevance).
- `CoverLaunchCoordinatorTest` verifies coordinator threading and rollback behavior (not incoming-call suppression).
- Current `androidTest` files are context/launcher routing checks, not call-policy behavior.

Coverage gaps:
- No tests directly exercising:
  - `ForegroundService.requestIncomingCallPassthroughInternal(...)`
  - `ForegroundService.updateCallNotificationStateInternal(...)`
  - `ForegroundService.maybeResumeOverlayAfterAppLaunch(...)` for `INCOMING_CALL`
  - `OverlayReclaimPolicy` decision branches
  - `CoverAccessibilityService` call event routing
  - `CoverNotificationListenerService.isLikelyActiveCallNotification(...)`
  - `CallPackageMatchers` false-positive boundaries

Immediate refinement directions:
1. Add unit tests for `CallPackageMatchers` and `OverlayReclaimPolicy` branch behavior.
2. Add service-level tests for incoming-call suppression state transitions and race ordering.
3. Add regression tests for lock-state precedence vs incoming-call hold logic.
4. Add tests validating both-source inactive requirement before reclaim after call end.

