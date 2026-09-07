package com.tyejaedon.coverscreenos.services

internal object CallPackageMatchers {
    private val INCOMING_CALL_EXACT_PACKAGES = setOf(
        "com.google.android.dialer",
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    private val INCOMING_CALL_SURFACE_PREFIXES = arrayOf(
        "com.samsung.android.incallui",
        "com.android.incallui",
        "com.android.server.telecom"
    )

    fun isIncomingCallPackage(packageName: String): Boolean {
        val normalizedPackage = packageName.trim().lowercase()
        if (normalizedPackage.isEmpty()) return false
        if (normalizedPackage in INCOMING_CALL_EXACT_PACKAGES) return true

        return INCOMING_CALL_SURFACE_PREFIXES.any { prefix ->
            normalizedPackage == prefix || normalizedPackage.startsWith("$prefix.")
        }
    }
}

