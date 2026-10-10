package com.tyejaedon.coverscreenos.ui.appshell

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRoutesTest {
    @Test
    fun topLevelRoutesHaveCanonicalDeepLinks() {
        assertEquals("coverscreenos://dashboard", HomeRoutes.deepLink(HomeRoutes.Dashboard))
        assertEquals("coverscreenos://customize", HomeRoutes.deepLink(HomeRoutes.Customize))
        assertEquals("coverscreenos://permissions", HomeRoutes.deepLink(HomeRoutes.Permissions))
        assertEquals("coverscreenos://about", HomeRoutes.deepLink(HomeRoutes.About))
    }
}
