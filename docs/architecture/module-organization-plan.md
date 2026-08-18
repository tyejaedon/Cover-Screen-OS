# Module Organization Plan

This plan incrementally improves code structure while keeping behavior stable.

## Goals

- Group files by feature ownership instead of by technical layer only.
- Keep each file focused on one responsibility.
- Reduce cross-feature coupling for easier maintenance and new feature work.

## Target feature groups

- `core/`
  - App startup/runtime glue, shared contracts, and app-wide configuration.
- `overlay/`
  - Overlay runtime orchestration (`ForegroundService`, overlay attach/reclaim, launch coordination).
- `homescreen/` + `launcher/`
  - Home entry/setup UI plus launcher surface/grid/dock/wallpaper rendering components.
- `notifications/`
  - Notification listener, call-signal helpers, notification state mapping.
- `permissions/`
  - Permission onboarding screens and permission checks.
- `platform/`
  - Receivers, display helpers, and direct Android framework adapters.

## Naming conventions

- Feature-first packages and folders.
- File names mirror primary class/composable name.
- Test folder/package mirrors production package exactly.

## Implementation phases

### Phase 1 (started)

- Fix low-friction structural inconsistencies.
- Co-locate home entry/setup UI under `homescreen/` and rendering/configuration UI under `launcher/`.
- Normalize typo-prone filenames and test folder mismatches.

### Phase 2

- Move service-adjacent classes into feature packages (`overlay`, `notifications`).
- Introduce small internal interfaces around system APIs for testability.
- Reduce large-file hotspots by splitting utility/state objects.

### Phase 3

- Separate `domain` models/use-cases from Android-specific adapters.
- Add package-level README notes for each feature module.
- Enforce structure with lint/checkstyle conventions where possible.

## Guardrails

- Prefer move-only refactors first (no behavior changes) and compile after each batch.
- Keep AndroidManifest component class names valid during moves.
- Update imports/tests in the same commit as each move to avoid broken intermediate states.

