package com.tyejaedon.coverscreenos.ui

import android.graphics.drawable.Drawable
import com.tyejaedon.coverscreenos.models.AppModel
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockSearchRankingTest {

    @Test
    fun `exact app name ranks before partial matches`() {
        val apps = listOf(
            app(name = "Calculator", packageName = "com.android.calculator2"),
            app(name = "Calendar", packageName = "com.android.calendar"),
            app(name = "My Calc Notes", packageName = "com.example.notes")
        )

        val results = rankSearchResults(allApps = apps, query = "Calculator")

        assertTrue(results.isNotEmpty())
        assertEquals("Calculator", results.first().name)
    }

    @Test
    fun `tokenized prefix matching keeps intuitive ordering`() {
        val apps = listOf(
            app(name = "Google Play Store", packageName = "com.android.vending"),
            app(name = "Play Books", packageName = "com.google.android.apps.books"),
            app(name = "Gallery", packageName = "com.android.gallery3d")
        )

        val results = rankSearchResults(allApps = apps, query = "play st")

        assertTrue(results.isNotEmpty())
        assertEquals("Google Play Store", results.first().name)
    }

    @Test
    fun `acronym search returns matching app`() {
        val apps = listOf(
            app(name = "Samsung Smart Things", packageName = "com.samsung.smartthings"),
            app(name = "Settings", packageName = "com.android.settings")
        )

        val results = rankSearchResults(allApps = apps, query = "sst")

        assertTrue(results.any { it.name == "Samsung Smart Things" })
    }

    @Test
    fun `pushSearchHistory prepends unique query and caps size`() {
        val history = listOf("camera", "settings", "gallery")

        val updated = pushSearchHistory(
            currentHistory = history,
            query = "Settings",
            maxEntries = 3
        )

        assertEquals(listOf("Settings", "camera", "gallery"), updated)
    }

    @Test
    fun `lockSearchNoResultGuidance returns actionable message`() {
        val guidance = lockSearchNoResultGuidance("zzzz")

        assertTrue(guidance.contains("shorter keyword"))
        assertTrue(guidance.contains("initials"))
    }

    private fun app(name: String, packageName: String): AppModel {
        return AppModel(
            name = name,
            packageName = packageName,
            iconDrawable = mockk<Drawable>()
        )
    }
}

