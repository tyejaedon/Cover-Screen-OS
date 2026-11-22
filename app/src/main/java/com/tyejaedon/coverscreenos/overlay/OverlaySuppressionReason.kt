package com.tyejaedon.coverscreenos.overlay

/**
 * Categorizes why the cover launcher overlay is temporarily suppressed rather than
 * permanently destroyed.
 *
 * Suppression keeps the `TYPE_ACCESSIBILITY_OVERLAY` window mounted (non-touchable
 * and transparent) so another surface or posture can own the cover screen, allowing
 * fast restoration once the blocking condition ends.
 */
internal enum class OverlaySuppressionReason {
    /** Overlay is not suppressed and may attach or show on the cover display. */
    NONE,
    /** Suppressed while an app launched from the cover launcher owns the foreground. */
    APP_LAUNCH,
    /** Suppressed so an incoming or ongoing call screen can receive interaction unimpeded. */
    INCOMING_CALL,
    /** Suppressed because the foldable is half-folded or flat and the inner display is active. */
    POSTURE_UNFOLDED
}

