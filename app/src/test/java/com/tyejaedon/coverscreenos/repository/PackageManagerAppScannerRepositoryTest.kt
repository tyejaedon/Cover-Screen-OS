package com.tyejaedon.coverscreenos.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PackageManagerAppScannerRepositoryTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var repository: PackageManagerAppScannerRepository

    @Before
    fun setup() {
        context = mockk()
        packageManager = mockk()

        every { context.packageManager } returns packageManager

        repository = PackageManagerAppScannerRepository(
            context = context,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `scanInstalledApplications returns mapped sorted apps and filters non-launchable system apps`() = runBlocking {
        val hiddenSystemService = createAppInfo(
            packageName = "android.system.hidden",
            label = "Hidden Service",
            flags = ApplicationInfo.FLAG_SYSTEM
        )
        val launchableSystemApp = createAppInfo(
            packageName = "android.system.settings",
            label = "Settings",
            flags = ApplicationInfo.FLAG_SYSTEM
        )
        val userAppWithoutLauncher = createAppInfo(
            packageName = "com.example.worker",
            label = "Worker App",
            flags = 0
        )

        every {
            packageManager.getInstalledApplications(0)
        } returns listOf(hiddenSystemService, userAppWithoutLauncher, launchableSystemApp)

        every { packageManager.getLaunchIntentForPackage("android.system.hidden") } returns null
        every { packageManager.getLaunchIntentForPackage("android.system.settings") } returns Intent()
        every { packageManager.getLaunchIntentForPackage("com.example.worker") } returns null

        val result = repository.scanInstalledApplications()

        assertEquals(2, result.size)
        assertEquals("Settings", result[0].name)
        assertEquals("android.system.settings", result[0].packageName)
        assertEquals("Worker App", result[1].name)
        assertEquals("com.example.worker", result[1].packageName)
        assertTrue(result.none { it.packageName == "android.system.hidden" })
    }

    private fun createAppInfo(
        packageName: String,
        label: String,
        flags: Int
    ): ApplicationInfo {
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            this.flags = flags
        }
        val icon = mockk<Drawable>()

        every { packageManager.getApplicationLabel(appInfo) } returns label
        every { packageManager.getApplicationIcon(appInfo) } returns icon

        return appInfo
    }
}

