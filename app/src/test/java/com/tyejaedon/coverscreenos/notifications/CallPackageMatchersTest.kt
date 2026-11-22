package com.tyejaedon.coverscreenos.notifications

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

    @Test
    fun `incoming call window distinguishes voip calls from regular voip app screens`() {
        assertFalse(
            CallPackageMatchers.isIncomingCallWindow(
                packageName = "com.whatsapp",
                className = "com.whatsapp.home.ui.HomeActivity",
                isCallNotificationActive = false
            )
        )
        assertTrue(
            CallPackageMatchers.isIncomingCallWindow(
                packageName = "com.whatsapp",
                className = "com.whatsapp.voipcalling.VoipActivityV2",
                isCallNotificationActive = false
            )
        )
        assertTrue(
            CallPackageMatchers.isIncomingCallWindow(
                packageName = "com.whatsapp",
                className = "com.whatsapp.home.ui.HomeActivity",
                isCallNotificationActive = true
            )
        )
        assertTrue(
            CallPackageMatchers.isIncomingCallWindow(
                packageName = "com.samsung.android.incallui",
                className = "com.samsung.android.incallui.MainActivity",
                isCallNotificationActive = false
            )
        )
    }
}

