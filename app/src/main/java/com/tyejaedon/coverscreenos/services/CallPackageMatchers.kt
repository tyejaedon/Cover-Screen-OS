package com.tyejaedon.coverscreenos.services

internal object CallPackageMatchers {
    private val INCOMING_CALL_PACKAGE_PREFIXES = arrayOf(
        "com.samsung.android.incallui",
        "com.android.incallui",
        "com.google.android.dialer",
        "com.android.server.telecom",
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    fun isIncomingCallPackage(packageName: String): Boolean {
        return INCOMING_CALL_PACKAGE_PREFIXES.any { prefix ->
            packageName.startsWith(prefix)
        }
    }
}

