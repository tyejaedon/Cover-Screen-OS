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

    @Test
    fun customizeCategoriesHaveCanonicalRoutesAndDeepLinks() {
        assertEquals(
            listOf("wallpaper", "dock", "appearance", "input"),
            CustomizeCategory.entries.map { it.segment }
        )
        CustomizeCategory.entries.forEach { category ->
            assertEquals("customize/${category.segment}", category.route)
            assertEquals("coverscreenos://customize/${category.segment}", category.deepLink)
        }
    }
}
