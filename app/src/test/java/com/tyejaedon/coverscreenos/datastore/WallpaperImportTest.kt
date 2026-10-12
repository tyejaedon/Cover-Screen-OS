package com.tyejaedon.coverscreenos.datastore

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WallpaperImportTest {
    @Test
    fun `imports from different stores serialize with clear while source is streaming`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val firstStore = LauncherSettingsStore(context)
        val secondStore = LauncherSettingsStore(context)
        val sourceUri = Uri.fromFile(File(context.cacheDir, "wallpaper-blocked-${System.nanoTime()}.png"))
        val secondSource = File(context.cacheDir, "wallpaper-second-${System.nanoTime()}.png")
        val directory = File(context.filesDir, "cover_wallpaper")
        val enteredRead = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val secondPng = ByteArrayOutputStream().also { bytes ->
            Bitmap.createBitmap(40, 24, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, bytes)
        }.toByteArray()
        secondSource.writeBytes(secondPng)
        val stream = object : ByteArrayInputStream(ByteArrayOutputStream().also { bytes ->
            Bitmap.createBitmap(24, 32, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, bytes)
        }.toByteArray()) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                enteredRead.countDown()
                check(releaseRead.await(5, TimeUnit.SECONDS)) { "Timed out waiting to resume import" }
                return super.read(buffer, offset, length)
            }
        }
        try {
            val import = async(Dispatchers.IO) {
                firstStore.importWallpaperFromStream(sourceUri, stream)
            }
            assertTrue(withContext(Dispatchers.IO) { enteredRead.await(5, TimeUnit.SECONDS) })
            val partialFiles = directory.listFiles()?.filter { it.name.endsWith(".part") }.orEmpty()
            assertEquals(1, partialFiles.size)

            val nextImport = async(Dispatchers.IO) {
                secondStore.importWallpaperFromUri(Uri.fromFile(secondSource))
            }
            val clear = async(Dispatchers.IO) { secondStore.clearWallpaper() }
            delay(100)
            assertTrue("Another wallpaper mutation removed the active import's partial file", partialFiles.single().exists())

            releaseRead.countDown()
            import.await()
            nextImport.await()
            clear.await()
            val finalUri = firstStore.settings.first().wallpaperUri
            if (finalUri != null) {
                val finalFile = File(Uri.parse(finalUri).path!!)
                assertTrue(finalFile.isFile)
                assertArrayEquals(secondPng, finalFile.readBytes())
            }
            assertTrue(
                directory.listFiles().orEmpty().none {
                    it.name.startsWith("selected_wallpaper_") && (finalUri == null || it.name.endsWith(".part"))
                }
            )
        } finally {
            releaseRead.countDown()
            firstStore.clearWallpaper()
            directory.delete()
            secondSource.delete()
        }
    }

    @Test
    fun `import keeps original bytes and invalid replacement keeps previous wallpaper`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = LauncherSettingsStore(context)
        val source = File(context.cacheDir, "wallpaper-original-${System.nanoTime()}.png")
        val replacementSource = File(context.cacheDir, "wallpaper-replacement-${System.nanoTime()}.png")
        val invalidSource = File(context.cacheDir, "wallpaper-invalid-${System.nanoTime()}.mp4")
        val oversizedSource = File(context.cacheDir, "wallpaper-oversized-${System.nanoTime()}.jpg")
        val wallpaperDirectory = File(context.filesDir, "cover_wallpaper")
        wallpaperDirectory.mkdirs()
        val interruptedPart = File(wallpaperDirectory, "selected_wallpaper_interrupted.img.part")
        val unrelatedPart = File(wallpaperDirectory, "unrelated.part")
        val png = ByteArrayOutputStream().also { bytes ->
            Bitmap.createBitmap(24, 32, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, bytes)
        }.toByteArray()
        val replacementPng = ByteArrayOutputStream().also { bytes ->
            Bitmap.createBitmap(40, 24, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, bytes)
        }.toByteArray()
        source.writeBytes(png)
        replacementSource.writeBytes(replacementPng)
        invalidSource.writeBytes("GIF89a".toByteArray())
        RandomAccessFile(oversizedSource, "rw").use {
            it.setLength(WallpaperMediaLimits.MAX_FILE_BYTES + 1)
        }
        interruptedPart.writeBytes(byteArrayOf(1, 2, 3))
        unrelatedPart.writeBytes(byteArrayOf(4, 5, 6))
        try {
            store.importWallpaperFromUri(Uri.fromFile(source))
            assertFalse(interruptedPart.exists())
            assertTrue(unrelatedPart.exists())
            val importedUri = store.settings.first().wallpaperUri ?: error("Imported wallpaper URI was not saved")
            val managedFile = File(Uri.parse(importedUri).path!!)
            assertArrayEquals(png, managedFile.readBytes())

            val beforeFailedImport = store.settings.first()
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { store.importWallpaperFromUri(Uri.fromFile(invalidSource)) }
            }
            assertEquals(beforeFailedImport, store.settings.first())
            assertTrue(managedFile.isFile)
            assertFalse(File(managedFile.parentFile, "${managedFile.name}.part").exists())

            val managedFilesBeforeOversizedImport = wallpaperDirectory.listFiles()
                ?.map { it.name }?.toSet() ?: error("Could not list wallpaper files")
            val oversizedError = assertThrows(IOException::class.java) {
                runBlocking { store.importWallpaperFromUri(Uri.fromFile(oversizedSource)) }
            }
            assertTrue(oversizedError.message.orEmpty().contains("64 MB"))
            assertEquals(beforeFailedImport, store.settings.first())
            assertTrue(managedFile.isFile)
            assertEquals(managedFilesBeforeOversizedImport, wallpaperDirectory.listFiles()?.map { it.name }?.toSet())

            val cancelledImport = Job()
            cancelledImport.cancel()
            try {
                withContext(cancelledImport) { store.importWallpaperFromUri(Uri.fromFile(replacementSource)) }
                throw AssertionError("Cancelled import completed")
            } catch (_: CancellationException) {
                assertEquals(beforeFailedImport, store.settings.first())
                assertTrue(managedFile.isFile)
            }

            val partialCopy = File(wallpaperDirectory, "selected_wallpaper_cancelled.part")
            val copyJob = Job()
            try {
                withContext(copyJob) {
                    val activeJob = currentCoroutineContext()[Job] ?: error("Copy coroutine has no Job")
                    val cancellableInput = object : java.io.ByteArrayInputStream(ByteArray(16)) {
                        private var cancelled = false

                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            if (!cancelled) {
                                cancelled = true
                                val count = super.read(buffer, offset, minOf(length, 4))
                                activeJob.cancel()
                                return count
                            }
                            return super.read(buffer, offset, length)
                        }

                        @Test
                        fun `cancellation during committed import finalization keeps committed file`() = runBlocking {
                            val persistedUri = AtomicReference<String?>()
                            val commitStarted = CountDownLatch(1)
                            val finishCommit = CountDownLatch(1)
                            val cleanupCompleted = AtomicBoolean(false)
                            val finalization = async(Dispatchers.IO) {
                                finalizeWallpaperImport(
                                    commit = {
                                        persistedUri.set("file:///wallpaper/committed.png")
                                        commitStarted.countDown()
                                        check(finishCommit.await(5, TimeUnit.SECONDS)) { "Timed out waiting to finish commit" }
                                    },
                                    afterCommit = { cleanupCompleted.set(true) }
                                )
                            }
                            try {
                                assertTrue(withContext(Dispatchers.IO) { commitStarted.await(5, TimeUnit.SECONDS) })
                                finalization.cancel()
                                finishCommit.countDown()
                                finalization.join()

                                assertEquals("file:///wallpaper/committed.png", persistedUri.get())
                                assertTrue(cleanupCompleted.get())
                            } finally {
                                finishCommit.countDown()
                                finalization.cancel()
                            }
                        }
                    }
                    store.copyWallpaperImportStream(cancellableInput, partialCopy)
                }
                throw AssertionError("Cancelled copy completed")
            } catch (_: CancellationException) {
                assertFalse(partialCopy.exists())
                assertEquals(beforeFailedImport, store.settings.first())
                assertTrue(managedFile.isFile)
            } finally {
                copyJob.cancel()
            }

            store.importWallpaperFromUri(Uri.fromFile(replacementSource))
            val replacementUri = store.settings.first().wallpaperUri ?: error("Replacement wallpaper URI was not saved")
            assertFalse(importedUri == replacementUri)
            assertFalse(managedFile.exists())
            assertArrayEquals(replacementPng, File(Uri.parse(replacementUri).path!!).readBytes())
        } finally {
            store.clearWallpaper()
            source.delete()
            replacementSource.delete()
            invalidSource.delete()
            oversizedSource.delete()
            unrelatedPart.delete()
            wallpaperDirectory.delete()
        }
    }
}
