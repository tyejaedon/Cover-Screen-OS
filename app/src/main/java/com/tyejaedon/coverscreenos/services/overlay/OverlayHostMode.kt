package com.tyejaedon.coverscreenos.services.overlay

/**
 * Runtime selector for the launcher overlay hosting strategy.
 *
 * Part of the Phase 3 migration described in
 * `docs/architecture/Overlay-architecture-shift-plan.md` §6.3.
 *
 * - [LEGACY_WINDOW]: the historical `TYPE_APPLICATION_OVERLAY` window
 *   attached from the [ForegroundService] via `WindowManagerOverlayHost`.
 *   Requires the `SYSTEM_ALERT_WINDOW` runtime permission ("Draw over
 *   other apps"). This is the **default** through Phase 3.
 * - [ACCESSIBILITY]: the new `TYPE_ACCESSIBILITY_OVERLAY` window hosted
 *   by [CoverAccessibilityService] via `AccessibilityOverlayHost`. Does
 *   not require the draw-over permission. Selected via the debug menu
 *   for dogfood, and will become the default in Phase 4.
 *
 * Persisted through
 * [com.tyejaedon.coverscreenos.datastore.LauncherSettingsStore.setOverlayHostMode]
 * and read back on every façade dispatch by [OverlayWindowController].
 */
enum class OverlayHostMode {
    ACCESSIBILITY,
    LEGACY_WINDOW;

    companion object {
        /**
         * Default until Phase 4: keep the legacy `SYSTEM_ALERT_WINDOW`
         * path in effect so existing installs are unaffected by the mere
         * introduction of this preference.
         */
        val DEFAULT: OverlayHostMode = LEGACY_WINDOW

        /**
         * Resolves a persisted preference value back into an
         * [OverlayHostMode], falling back to [DEFAULT] for unknown /
         * legacy / null values.
         */
        fun fromStorageValue(value: String?): OverlayHostMode {
            if (value.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull { it.name == value } ?: DEFAULT
        }
    }
}

