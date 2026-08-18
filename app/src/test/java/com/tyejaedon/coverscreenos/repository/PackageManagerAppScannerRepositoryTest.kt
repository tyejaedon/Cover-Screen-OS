package com.tyejaedon.coverscreenos.repository

import android.content.Context
import android.graphics.drawable.ColorDrawable
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.tyejaedon.coverscreenos.models.AppModel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PackageManagerAppScannerRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: PackageManagerAppScannerRepository
    private var nowElapsedMs = 10_000L

    @Before
    fun setup() {
        context = mockk(relaxed = true)

        repository = PackageManagerAppScannerRepository(
            context = context,
            ioDispatcher = Dispatchers.Unconfined,
            elapsedRealtimeProvider = { nowElapsedMs }
        )
    }

    @Test
    fun `scanInstalledApplications returns cached apps when cache is fresh`() = runBlocking {
        val seededApps = listOf(
            AppModel(
                name = "Worker App",
                packageName = "com.example.worker",
                iconDrawable = ColorDrawable(0x112233)
            )
        )

        seedRepositoryCache(cachedApps = seededApps, cachedAtElapsedMs = nowElapsedMs)
        val result = repository.scanInstalledApplications()

        assertEquals(seededApps, result)
        verify(exactly = 0) { context.packageManager }
    }

    private fun seedRepositoryCache(cachedApps: List<AppModel>, cachedAtElapsedMs: Long) {
        val repositoryClass = PackageManagerAppScannerRepository::class.java

        repositoryClass.getDeclaredField("cachedApps").apply {
            isAccessible = true
            set(null, cachedApps)
        }
        repositoryClass.getDeclaredField("cachedAtElapsedMs").apply {
            isAccessible = true
            setLong(null, cachedAtElapsedMs)
        }
    }
}

