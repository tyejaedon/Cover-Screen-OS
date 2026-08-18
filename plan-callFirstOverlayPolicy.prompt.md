Goal:
Implement a call-priority overlay policy in CoverOS so incoming calls (phone + WhatsApp) are always visible above the overlay, while keeping normal app-launch suppression behavior unchanged.

Context:
- Project path: `C:\Cover-Screen-OS`
- Core files:
  - `app/src/main/java/com/tyejaedon/coverscreenos/services/CoverAccessibilityService.kt`
  - `app/src/main/java/com/tyejaedon/coverscreenos/services/ForegroundService.kt`
  - `app/src/main/java/com/tyejaedon/coverscreenos/services/CoverNotificationListenerService.kt`
  - `app/src/main/java/com/tyejaedon/coverscreenos/ui/CoverAppGridOverlay.kt`

Primary UX requirements:
1. If an incoming call is detected, overlay must suppress immediately so user can answer.
2. While call is active, overlay must stay suppressed (no reclaim takeover).
3. User can re-enter ongoing call via notification center tap (Dialer/WhatsApp).
4. After call ends, overlay can reclaim after short grace.
5. Existing app launch behavior remains protected by 45s timeout; this timeout must NOT terminate call-mode suppression.

Implementation tasks:

1) Introduce typed suppression reason model in `ForegroundService.kt`
- Add enum/state:
  - `NONE`
  - `APP_LAUNCH`
  - `INCOMING_CALL`
- Track:
  - current suppression reason
  - reason start elapsed time
  - call active signals (accessibility + notification)

2) Split timeout policy by suppression reason
- Keep `APP_LAUNCH_RESUME_MAX_SUPPRESSION_MS = 45_000` only for `APP_LAUNCH`.
- For `INCOMING_CALL`, use long safety max (existing 2h acceptable), but do not auto-reclaim at 45s.
- Ensure `maybeResumeOverlayAfterAppLaunch(...)` checks reason before timeout reclaim.

3) Dual-source call signal model
- Accessibility source:
  - keep incoming-call package detection in `CoverAccessibilityService.kt`
  - continue `ForegroundService.requestIncomingCallPassthrough(packageName)`
- Notification source:
  - in `CoverNotificationListenerService.kt`, detect active call notifications (category/style/package heuristics)
  - expose call-active updates to `ForegroundService` (new API, e.g. `updateCallNotificationState(...)`)
- Reclaim allowed only when both call signals are inactive (with grace window).

4) Preserve notification center re-entry
- Ensure tapping active call notification still hides overlay and launches source app.
- Validate this for:
  - system dialer
  - `com.whatsapp`
  - `com.whatsapp.w4b`

5) Prevent state drift
- Remove duplicated package-prefix lists by creating one shared matcher utility/class (e.g. `CallPackageMatchers.kt`).
- Use same matcher in `CoverAccessibilityService` and `ForegroundService`.

6) Logging and observability
- Emit structured logs with tag `CoverOverlayReclaim`:
  - suppression reason transitions
  - call signal source changes
  - reclaim blocked/allowed decisions
  - timeout path selected (`APP_LAUNCH` vs `INCOMING_CALL`)

7) Safety/regression checks
- Ensure no overlay deadlock:
  - suppression exits when call ends
  - fallback timeout still exists for abnormal stale states
- Keep existing reclaim debounce and recent-user-app guard behavior intact.

Acceptance criteria:
- Incoming phone call never gets hidden by overlay.
- Incoming WhatsApp call never gets hidden by overlay.
- Ongoing call stays foreground beyond 45s without overlay takeover.
- User can tap active call notification to re-enter call UI.
- Overlay returns normally after call ends + grace period.
- No Kotlin compile errors.

Validation steps:
1. Trigger incoming cellular call and observe no overlay takeover.
2. Trigger incoming WhatsApp call and observe no overlay takeover.
3. Keep call active for >45s; verify overlay does not reclaim.
4. Leave call screen and re-enter via notification center tap.
5. End call; verify overlay reclaim resumes normally.
6. Check logs:
   - reason transitions
   - reclaim blocked during active call
   - reclaim allowed after end + grace.

