package com.tyejaedon.coverscreenos.notifications

/**
 * Identifies Android packages that present incoming or active voice/VoIP call UIs so the cover
 * screen overlay can yield visibility to native call controls.
 */
internal object CallPackageMatchers {
    // Exact package IDs for popular standalone dialer and VoIP apps that surface full-screen call alerts.
    private val INCOMING_CALL_EXACT_PACKAGES = setOf(
        "com.google.android.dialer",
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    // System/OEM telecom and InCallUI package prefixes that may append subpackage suffixes on some ROMs.
    private val INCOMING_CALL_SURFACE_PREFIXES = arrayOf(
        "com.samsung.android.incallui",
        "com.android.incallui",
        "com.android.server.telecom"
    )
    private val CALL_ACTIVITY_CLASS_TOKENS = arrayOf("call", "voip", "incall", "ringing", "answer")

    /**
     * Returns `true` when [packageName] matches a known dialer, VoIP app, or telecom InCallUI
     * package (either exact match or subpackage prefix).
     */
    fun isIncomingCallPackage(packageName: String): Boolean {
        // Trimmed, lowercased package identifier so comparison is whitespace- and case-insensitive.
        val normalizedPackage = packageName.trim().lowercase()
        if (normalizedPackage.isEmpty()) return false
        if (normalizedPackage in INCOMING_CALL_EXACT_PACKAGES) return true

        return INCOMING_CALL_SURFACE_PREFIXES.any { prefix ->
            normalizedPackage == prefix || normalizedPackage.startsWith("$prefix.")
        }
    }

    /**
     * Returns `true` when a foreground window belonging to [packageName]/[className] actually
     * represents an incoming/active call UI rather than a general chat screen (e.g. WhatsApp Home).
     */
    fun isIncomingCallWindow(
        packageName: String,
        className: String?,
        isCallNotificationActive: Boolean = false
    ): Boolean {
        if (!isIncomingCallPackage(packageName)) return false
        val normalizedPackage = packageName.trim().lowercase()
        if (INCOMING_CALL_SURFACE_PREFIXES.any { prefix ->
                normalizedPackage == prefix || normalizedPackage.startsWith("$prefix.")
            }
        ) {
            return true
        }
        if (isCallNotificationActive) return true
        val normalizedClass = className?.trim()?.lowercase()?.takeUnless { it.isEmpty() } ?: return true
        return CALL_ACTIVITY_CLASS_TOKENS.any { token -> normalizedClass.contains(token) }
    }
}

