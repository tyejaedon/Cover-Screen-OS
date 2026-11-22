package com.tyejaedon.coverscreenos.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Accent palette — One UI leans on a single, confident accent against a
// near-monochrome canvas rather than a busy tri-color Material scheme.
// CoverOSPrimary is the "Galaxy" indigo used for every interactive/selected
// state. Secondary/Tertiary are reserved for rare, deliberate highlights
// (e.g. the dock-visibility toggle, the fast-scroll letter badge).
// ---------------------------------------------------------------------------

/** Holds the main "Galaxy" indigo accent color used for active buttons, switches, and selected items in dark mode. */
val CoverOSPrimary = Color(0xFF7C9CFF)
/** Holds the softer dark-blue container fill paired with [CoverOSPrimary] for highlighted pills and selected cards. */
val CoverOSPrimaryContainer = Color(0xFF25305C)
/** Holds the light-blue text/icon color drawn on top of [CoverOSPrimaryContainer] so labels stay easy to read. */
val CoverOSOnPrimaryContainer = Color(0xFFDCE4FF)
/** Holds the deep navy ink color drawn on top of bright [CoverOSPrimary] buttons for strong contrast. */
val CoverOSOnPrimary = Color(0xFF0E1635)

/** Holds the secondary mint-green accent reserved for rare highlights like dock-visibility toggles. */
val CoverOSSecondary = Color(0xFF6EE7C0)
/** Holds the deep teal background fill used behind elements styled with [CoverOSSecondary]. */
val CoverOSSecondaryContainer = Color(0xFF16392F)
/** Holds the dark green text/icon color drawn on top of [CoverOSSecondary] and [CoverOSSecondaryContainer]. */
val CoverOSOnSecondary = Color(0xFF06231B)
/** Holds the warm amber tertiary accent used sparingly for attention badges such as the fast-scroll letter bubble. */
val CoverOSTertiary = Color(0xFFFFB870)
/** Holds the dark bronze container background paired with [CoverOSTertiary]. */
val CoverOSTertiaryContainer = Color(0xFF4A2B0A)
/** Holds the dark brown ink color drawn on top of [CoverOSTertiary] and [CoverOSTertiaryContainer]. */
val CoverOSOnTertiary = Color(0xFF2A1400)

// ---------------------------------------------------------------------------
// Semantic status colors — battery/call/destructive-action indicators used
// across the overlay. Named here (rather than inlined as magic hex literals
// at each call site) per the One UI restyle's "no Color(0xFF...) outside
// Color.kt" rule.
// ---------------------------------------------------------------------------

/** Holds the alert-red color used for destructive actions like clearing notifications or removing items. */
val CoverOSDestructiveColor = Color(0xFFD32F2F)
/** Holds the status-green color indicating active battery charging on the cover screen. */
val CoverOSBatteryChargingColor = Color(0xFF4CAF50)
/** Holds the deep green pill color shown when an active phone or VoIP call is in progress. */
val CoverOSCallActiveColor = Color(0xFF1B5E20)
/** Holds the top slate-blue color of the fallback media gradient when a song has no album artwork. */
val CoverOSMediaPlaceholderGradientStart = Color(0xFF2C3E50)
/** Holds the bottom pure-black color of the fallback media gradient so controls fade cleanly into the screen. */
val CoverOSMediaPlaceholderGradientEnd = Color(0xFF000000)

// ---------------------------------------------------------------------------
// Dark palette — true, AMOLED-friendly black rather than a tinted navy, so
// the wallpaper and blur layers read as the actual surface of the screen.
// ---------------------------------------------------------------------------

/** Holds the pure-black (`#000000`) canvas color so unlit OLED pixels turn off completely and save battery. */
val CoverOSDarkBackground = Color(0xFF000000)
/** Holds the charcoal surface color for cards lifted one step above [CoverOSDarkBackground]. */
val CoverOSDarkSurface = Color(0xFF111214)
/** Holds a slightly lighter dark-gray surface for nested tracks, sliders, and secondary containers. */
val CoverOSDarkSurfaceVariant = Color(0xFF1B1D22)
/** Holds the subtle gray color used for thin 1dp borders and dividers between dark cards. */
val CoverOSDarkOutline = Color(0xFF2C2E34)

/** Holds pure white text/icon ink for content placed directly on [CoverOSDarkBackground]. */
val CoverOSDarkOnBackground = Color(0xFFFFFFFF)
/** Holds slightly softened off-white ink for card text, reducing harsh glare on OLED displays. */
val CoverOSDarkOnSurface = Color(0xFFF2F3F5)
/** Holds muted cool-gray ink for secondary subtitles, timestamps, and inactive icons in dark mode. */
val CoverOSDarkOnSurfaceVariant = Color(0xFFA6AAB4)

// ---------------------------------------------------------------------------
// Light palette — kept for parity / future light-cover theming.
// ---------------------------------------------------------------------------

/** Holds the soft cool-gray light-mode backdrop that lets white cards pop out clearly. */
val CoverOSLightBackground = Color(0xFFF4F5F9)
/** Holds the pure-white surface color for elevated cards in light mode. */
val CoverOSLightSurface = Color(0xFFFFFFFF)
/** Holds the recessed light-gray surface used for slider tracks, chips, and inner containers in light mode. */
val CoverOSLightSurfaceVariant = Color(0xFFE9EBF2)
/** Holds the medium-gray outline color used for borders and dividers in light mode. */
val CoverOSLightOutline = Color(0xFF797D89)

/** Holds the deeper light-mode primary indigo tuned to stay crisp against white backgrounds. */
val CoverOSLightPrimary = Color(0xFF4860B8)
/** Holds white ink rendered on top of [CoverOSLightPrimary] buttons. */
val CoverOSLightOnPrimary = Color(0xFFFFFFFF)
/** Holds the pale blue container tint paired with [CoverOSLightPrimary]. */
val CoverOSLightPrimaryContainer = Color(0xFFDDE4FF)
/** Holds deep navy ink rendered inside [CoverOSLightPrimaryContainer]. */
val CoverOSLightOnPrimaryContainer = Color(0xFF1A2A63)

/** Holds the light-mode secondary teal-green accent. */
val CoverOSLightSecondary = Color(0xFF266B55)
/** Holds white ink rendered on top of [CoverOSLightSecondary]. */
val CoverOSLightOnSecondary = Color(0xFFFFFFFF)
/** Holds the soft mint container tint paired with [CoverOSLightSecondary]. */
val CoverOSLightSecondaryContainer = Color(0xFFB5F1DC)
/** Holds deep forest-green ink rendered inside [CoverOSLightSecondaryContainer]. */
val CoverOSLightOnSecondaryContainer = Color(0xFF0A271F)

/** Holds the light-mode tertiary warm amber-brown accent. */
val CoverOSLightTertiary = Color(0xFF8A4F1A)
/** Holds white ink rendered on top of [CoverOSLightTertiary]. */
val CoverOSLightOnTertiary = Color(0xFFFFFFFF)
/** Holds the soft peach container tint paired with [CoverOSLightTertiary]. */
val CoverOSLightTertiaryContainer = Color(0xFFFFDCC0)
/** Holds dark brown ink rendered inside [CoverOSLightTertiaryContainer]. */
val CoverOSLightOnTertiaryContainer = Color(0xFF2D1500)

/** Holds near-black ink for primary text on [CoverOSLightBackground]. */
val CoverOSLightOnBackground = Color(0xFF14161B)
/** Holds near-black ink for primary text inside [CoverOSLightSurface] cards. */
val CoverOSLightOnSurface = Color(0xFF14161B)
/** Holds muted slate-gray ink for secondary captions and hints in light mode. */
val CoverOSLightOnSurfaceVariant = Color(0xFF53586A)