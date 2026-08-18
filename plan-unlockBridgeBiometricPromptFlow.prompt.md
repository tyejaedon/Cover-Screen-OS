Proposal: Activity-Driven Unlock Orchestrator

Replace direct `createConfirmDeviceCredentialIntent(...)` usage in `CoverAppLauncher` with a two-tier unlock orchestration:

1. **Primary**: `BiometricPrompt` (`BIOMETRIC_STRONG | DEVICE_CREDENTIAL`) hosted in a dedicated activity.
2. **Fallback**: existing `createConfirmDeviceCredentialIntent(...)` path if activity launch/prompt setup fails.

This keeps modern auth UX while preserving reliability for background/service-originated launch flows.

Actors

- **`CoverAppLauncher`** (`app/src/main/java/com/tyejaedon/coverscreenos/ui/controllers/CoverAppLauncher.kt`)
  - Entry point for app launch.
  - Detects lock state.
  - Sends unlock request contract to bridge activity instead of directly launching credential intent.

- **`UnlockBridgeActivity`** (new)
  - Transparent/ephemeral activity.
  - Owns `BiometricPrompt` lifecycle.
  - Emits success/failure/cancel outcome.
  - On success, re-dispatches launch via `CoverAppLauncher.launchPackageOnDisplay(...)`.

- **`ForegroundService`**
  - Keeps overlay suppression/reclaim state intact.
  - Receives hide-overlay intent before launch as today.

- **Android system auth stack**
  - Handles credential/biometric challenge UI.

- **User**
  - Approves or cancels auth challenge.

Data Contract

Unlock request payload (intent extras):

- `EXTRA_PACKAGE_NAME: String` (required)
- `EXTRA_DISPLAY_ID: Int?` (optional; use `Display.INVALID_DISPLAY` sentinel if needed)
- `EXTRA_REQUEST_ID: String` (UUID for tracing/idempotency)
- `EXTRA_REQUEST_TIMESTAMP_ELAPSED: Long` (for stale-request guard)

Optional policy extras:

- `EXTRA_AUTH_REASON: String` (analytics/debug text)
- `EXTRA_ALLOW_FALLBACK_INTENT: Boolean` (default true)

Action Flow (happy path)

1. User taps app tile in overlay.
2. `CoverAppLauncher.launchPackageOnDisplay(...)` sees `isDeviceLocked == true`.
3. `CoverAppLauncher` starts `UnlockBridgeActivity` with request extras (`NEW_TASK`, `EXCLUDE_FROM_RECENTS`).
4. `UnlockBridgeActivity.onCreate` validates extras + staleness.
5. Activity initializes `BiometricPrompt` and calls `authenticate(...)` with:
   - title/subtitle matching existing prompt intent.
   - allowed authenticators: `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`.
6. User authenticates successfully.
7. `onAuthenticationSucceeded`:
   - call `ForegroundService.createHideOverlayIntent(...)` (if not already suppressed enough),
   - call `CoverAppLauncher.launchPackageOnDisplay(...)` with `skipUnlockCheck=true` or equivalent guard to avoid loop.
8. Activity `finish()`.

Failure / Cancel Flow

- **User cancel / auth error**:
  - Do not launch target package.
  - clear pending request.
  - finish activity.
- **BiometricPrompt init failure**:
  - if fallback enabled: attempt legacy `createConfirmDeviceCredentialIntent`.
  - else fail closed and finish.
- **Stale request** (e.g., >15s old):
  - ignore and finish to avoid surprise launches.
- **Duplicate request ID**:
  - ignore newer/older based on policy, ensure idempotent completion.

Lifecycle Model

`CoverAppLauncher` lifecycle touchpoints:

- Launch request created.
- If unlocked: normal immediate launch.
- If locked: enqueue-to-auth-bridge and return `false` (launch deferred).

`UnlockBridgeActivity` lifecycle:

- `onCreate`: parse contract, validate, start auth.
- `onResume`: no-op unless retry required.
- `onPause/onStop`: keep pending state only while prompt active.
- `onDestroy`: always clear one-shot in-memory state to avoid replay.

`ForegroundService` lifecycle impact:

- No major structural change.
- Existing suppression/reclaim policy remains source of truth.
- Auth success path behaves like normal launch path, so reclaim synchronization still applies.

State Machine (simplified)

- `IDLE`
- `PENDING_UNLOCK_REQUEST`
- `AUTH_PROMPT_ACTIVE`
- `AUTH_SUCCESS -> LAUNCH_DISPATCHED -> COMPLETED`
- `AUTH_FAILED|AUTH_CANCELED -> ABORTED`
- `AUTH_SETUP_FAILED -> FALLBACK_INTENT_ACTIVE` (optional)
- `TIMEOUT -> ABORTED`

Idempotency / Safety Rules

- One launch completion per `requestId`.
- Bridge activity must reject duplicate completions.
- Add `skipUnlockCheck` flag (internal only) when launching after successful auth to prevent recursive unlock delegation.
- Enforce max request age before dispatching launch.

Suggested Manifest/UX traits for `UnlockBridgeActivity`

- `excludeFromRecents=true`
- `noHistory=true`
- lightweight/transparent theme
- `exported=false`
- launchMode: singleTask/singleTop (choose based on dedupe strategy)

Logging/Telemetry

Use structured logs with `requestId`:

- `unlock_request_created`
- `unlock_activity_started`
- `auth_prompt_shown`
- `auth_succeeded` / `auth_failed` / `auth_canceled`
- `launch_dispatched_after_auth`
- `fallback_credential_intent_started`
- `unlock_request_timeout_dropped`

Migration Plan

1. Add `UnlockBridgeActivity` and intent contract.
2. Route locked path in `CoverAppLauncher` to bridge activity.
3. Add fallback to existing deprecated method for reliability.
4. Add idempotency + stale request guard.
5. Validate on:
   - biometrics enrolled
   - credential only
   - no secure lock
   - canceled auth
   - repeated rapid launch taps

