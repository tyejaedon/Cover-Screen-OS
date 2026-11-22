package com.tyejaedon.coverscreenos.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Accent palette — One UI leans on a single, confident accent against a
// near-monochrome canvas rather than a busy tri-color Material scheme.
// CoverOSPrimary is the "Galaxy" indigo used for every interactive/selected
// state. Secondary/Tertiary are reserved for rare, deliberate highlights
// (e.g. the dock-visibility toggle, the fast-scroll letter badge).
// ---------------------------------------------------------------------------

/** Primary "Galaxy" indigo accent used for active controls, toggles, and selected states in dark mode. */
val CoverOSPrimary = Color(0xFF7C9CFF)
/** Tonal container surface paired with [CoverOSPrimary] for filled chips, badges, and subtle highlights. */
val CoverOSPrimaryContainer = Color(0xFF25305C)
/** High-contrast foreground ink rendered on top of [CoverOSPrimaryContainer]. */
val CoverOSOnPrimaryContainer = Color(0xFFDCE4FF)
/** Dark foreground ink rendered on top of [CoverOSPrimary] to meet legibility contrast ratios. */
val CoverOSOnPrimary = Color(0xFF0E1635)

/** Secondary mint accent reserved for rare, deliberate highlights such as dock-visibility indicators. */
val CoverOSSecondary = Color(0xFF6EE7C0)
/** Deep teal container backing elements styled with the secondary accent. */
val CoverOSSecondaryContainer = Color(0xFF16392F)
/** Dark foreground ink paired with [CoverOSSecondary] and [CoverOSSecondaryContainer]. */
val CoverOSOnSecondary = Color(0xFF06231B)
/** Tertiary warm amber accent used sparingly for focus badges such as the fast-scroll letter indicator. */
val CoverOSTertiary = Color(0xFFFFB870)
/** Warm dark-amber container surface backing tertiary-accented UI elements. */
val CoverOSTertiaryContainer = Color(0xFF4A2B0A)
/** Dark foreground ink paired with [CoverOSTertiary] and [CoverOSTertiaryContainer]. */
val CoverOSOnTertiary = Color(0xFF2A1400)

// ---------------------------------------------------------------------------
// Semantic status colors — battery/call/destructive-action indicators used
// across the overlay. Named here (rather than inlined as magic hex literals
// at each call site) per the One UI restyle's "no Color(0xFF...) outside
// Color.kt" rule.
// ---------------------------------------------------------------------------

/** High-alert red used for destructive actions such as clearing or removing overlay items. */
val CoverOSDestructiveColor = Color(0xFFD32F2F)
/** Status green indicating active battery charging on the cover screen. */
val CoverOSBatteryChargingColor = Color(0xFF4CAF50)
/** Deep green pill tint indicating an ongoing voice or VoIP call. */
val CoverOSCallActiveColor = Color(0xFF1B5E20)
/** Top stop of the fallback gradient behind media controls when album artwork is unavailable. */
val CoverOSMediaPlaceholderGradientStart = Color(0xFF2C3E50)
/** Bottom stop of the fallback gradient behind media controls, blending into true black. */
val CoverOSMediaPlaceholderGradientEnd = Color(0xFF000000)

// ---------------------------------------------------------------------------
// Dark palette — true, AMOLED-friendly black rather than a tinted navy, so
// the wallpaper and blur layers read as the actual surface of the screen.
// ---------------------------------------------------------------------------

/** True pure-black AMOLED canvas background so unlit cover pixels consume minimal power. */
val CoverOSDarkBackground = Color(0xFF000000)
/** Primary dark elevated card surface lifted slightly above [CoverOSDarkBackground]. */
val CoverOSDarkSurface = Color(0xFF111214)
/** Secondary dark surface tier for nested controls, tracks, and subtle containers. */
val CoverOSDarkSurfaceVariant = Color(0xFF1B1D22)
/** Subtle 1dp border/divider stroke separating dark cards and panels. */
val CoverOSDarkOutline = Color(0xFF2C2E34)

/** Pure white primary ink for text and icons placed directly on [CoverOSDarkBackground]. */
val CoverOSDarkOnBackground = Color(0xFFFFFFFF)
/** Softened near-white ink for primary labels inside [CoverOSDarkSurface] cards (reduces halation on OLED). */
val CoverOSDarkOnSurface = Color(0xFFF2F3F5)
/** Muted cool-gray ink for secondary subtitles, timestamps, and inactive icons in dark mode. */
val CoverOSDarkOnSurfaceVariant = Color(0xFFA6AAB4)

// ---------------------------------------------------------------------------
// Light palette — kept for parity / future light-cover theming.
// ---------------------------------------------------------------------------

/** Cool light-gray canvas background creating subtle depth behind white elevated cards. */
val CoverOSLightBackground = Color(0xFFF4F5F9)
/** Crisp pure-white elevated card surface in light mode. */
val CoverOSLightSurface = Color(0xFFFFFFFF)
/** Soft cool-gray recessed surface variant for tracks, chips, and secondary containers in light mode. */
val CoverOSLightSurfaceVariant = Color(0xFFE9EBF2)
/** Medium-contrast border and divider color in light mode. */
val CoverOSLightOutline = Color(0xFF797D89)

/** Light-mode primary indigo tuned for strong contrast against white and light-gray surfaces. */
val CoverOSLightPrimary = Color(0xFF4860B8)
/** Foreground ink rendered on top of [CoverOSLightPrimary]. */
val CoverOSLightOnPrimary = Color(0xFFFFFFFF)
/** Soft pastel-indigo container background in light mode. */
val CoverOSLightPrimaryContainer = Color(0xFFDDE4FF)
/** Deep indigo foreground ink paired with [CoverOSLightPrimaryContainer]. */
val CoverOSLightOnPrimaryContainer = Color(0xFF1A2A63)

/** Light-mode secondary teal-green accent. */
val CoverOSLightSecondary = Color(0xFF266B55)
/** Foreground ink rendered on top of [CoverOSLightSecondary]. */
val CoverOSLightOnSecondary = Color(0xFFFFFFFF)
/** Soft mint container background in light mode. */
val CoverOSLightSecondaryContainer = Color(0xFFB5F1DC)
/** Deep forest-green foreground ink paired with [CoverOSLightSecondaryContainer]. */
val CoverOSLightOnSecondaryContainer = Color(0xFF0A271F)

/** Light-mode tertiary warm-brown/amber accent. */
val CoverOSLightTertiary = Color(0xFF8A4F1A)
/** Foreground ink rendered on top of [CoverOSLightTertiary]. */
val CoverOSLightOnTertiary = Color(0xFFFFFFFF)
/** Soft peach-amber container background in light mode. */
val CoverOSLightTertiaryContainer = Color(0xFFFFDCC0)
/** Deep brown foreground ink paired with [CoverOSLightTertiaryContainer]. */
val CoverOSLightOnTertiaryContainer = Color(0xFF2D1500)

/** Deep near-black ink for primary text on [CoverOSLightBackground]. */
val CoverOSLightOnBackground = Color(0xFF14161B)
/** Deep near-black ink for primary text inside [CoverOSLightSurface] cards. */
val CoverOSLightOnSurface = Color(0xFF14161B)
/** Muted slate ink for secondary labels and supporting copy in light mode. */
val CoverOSLightOnSurfaceVariant = Color(0xFF53586A)