package com.tyejaedon.coverscreenos.notifications

/**
 * Identifies Android packages that present incoming or active voice/VoIP call UIs so the cover
 * screen overlay can yield visibility to native call controls.
 */
internal object CallPackageMatchers {
    private val INCOMING_CALL_EXACT_PACKAGES = setOf(
        "com.google.android.dialer",
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    // System/OEM telecom and InCallUI components may append subpackage suffixes on some ROMs.
    private val INCOMING_CALL_SURFACE_PREFIXES = arrayOf(
        "com.samsung.android.incallui",
        "com.android.incallui",
        "com.android.server.telecom"
    )

    /**
     * Returns `true` when [packageName] matches a known dialer, VoIP app, or telecom InCallUI
     * package (either exact match or subpackage prefix).
     */
    fun isIncomingCallPackage(packageName: String): Boolean {
        val normalizedPackage = packageName.trim().lowercase()
        if (normalizedPackage.isEmpty()) return false
        if (normalizedPackage in INCOMING_CALL_EXACT_PACKAGES) return true

        return INCOMING_CALL_SURFACE_PREFIXES.any { prefix ->
            normalizedPackage == prefix || normalizedPackage.startsWith("$prefix.")
        }
    }
}

