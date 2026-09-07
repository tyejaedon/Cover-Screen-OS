package com.tyejaedon.coverscreenos.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallPackageMatchersTest {

    @Test
    fun `exact call packages are matched`() {
        assertTrue(CallPackageMatchers.isIncomingCallPackage("com.google.android.dialer"))
        assertTrue(CallPackageMatchers.isIncomingCallPackage("com.whatsapp"))
        assertTrue(CallPackageMatchers.isIncomingCallPackage("com.whatsapp.w4b"))
    }

    @Test
    fun `dedicated in-call surfaces support package and subpackage`() {
        assertTrue(CallPackageMatchers.isIncomingCallPackage("com.samsung.android.incallui"))
        assertTrue(CallPackageMatchers.isIncomingCallPackage("com.samsung.android.incallui.overlay"))
        assertTrue(CallPackageMatchers.isIncomingCallPackage("com.android.incallui"))
        assertTrue(CallPackageMatchers.isIncomingCallPackage("com.android.server.telecom.callui"))
    }

    @Test
    fun `non-call app surfaces are not treated as incoming call`() {
        assertFalse(CallPackageMatchers.isIncomingCallPackage("com.whatsappbusiness"))
        assertFalse(CallPackageMatchers.isIncomingCallPackage("com.whatsapp.chat"))
        assertFalse(CallPackageMatchers.isIncomingCallPackage("com.google.android.dialer.settings"))
        assertFalse(CallPackageMatchers.isIncomingCallPackage("com.example.random"))
    }

    @Test
    fun `matching trims whitespace and ignores blank values`() {
        assertTrue(CallPackageMatchers.isIncomingCallPackage("  com.whatsapp.w4b  "))
        assertFalse(CallPackageMatchers.isIncomingCallPackage("   "))
    }
}

