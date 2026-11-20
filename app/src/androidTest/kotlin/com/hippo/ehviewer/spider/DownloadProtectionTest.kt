// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.spider

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ehviewer.core.files.exists
import com.ehviewer.core.model.BaseGalleryInfo
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.archiveFile
import com.hippo.ehviewer.download.downloadLocation
import com.hippo.ehviewer.download.readDirectoryIdentity
import com.hippo.ehviewer.download.tempDownloadDir
import com.hippo.ehviewer.jni.archiveFdBatch
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import splitties.init.appCtx

@RunWith(AndroidJUnit4::class)
class DownloadProtectionTest {
    @Before
    fun prepareDownloadsOnFreshInstallation() {
        val root = File(requireNotNull(appCtx.getExternalFilesDir(null)), "download")
        check(root.isDirectory || root.mkdirs()) { "Cannot create fixture download root: $root" }
        assertEquals(root.toOkioPath(), downloadLocation)
    }

    private fun gallery() = BaseGalleryInfo(System.nanoTime(), "abcdef1234", title = "Local fixture", pages = 2)

    @Test
    fun nativeWriterReportsSourceAndOutputFailures() {
        val input = File.createTempFile("archive-input", ".bin", appCtx.cacheDir)
        val output = File.createTempFile("archive-output", ".cbz", appCtx.cacheDir)
        try {
            input.writeBytes(ByteArray(65536) { it.toByte() })
            fun write(inMode: Int, outMode: Int) {
                ParcelFileDescriptor.open(input, inMode).use { src ->
                    ParcelFileDescriptor.open(output, outMode).use { dst ->
                        archiveFdBatch(intArrayOf(src.fd), arrayOf("page"), dst.fd, 1)
                    }
                }
            }
            assertThrows(IOException::class.java) { write(ParcelFileDescriptor.MODE_WRITE_ONLY, ParcelFileDescriptor.MODE_WRITE_ONLY) }
            assertThrows(IOException::class.java) { write(ParcelFileDescriptor.MODE_READ_ONLY, ParcelFileDescriptor.MODE_READ_ONLY) }
        } finally {
            input.delete()
            output.delete()
        }
    }

    @Test
    fun interruptedDownloadKeepsOnlyVerifiedSourcesEligibleForCleanup() = runBlocking(Dispatchers.IO) {
        val book = gallery()
        val dir = downloadLocation / "${book.gid}-archive-test"
        val temp = requireNotNull(book.tempDownloadDir).toFile()
        val first = File(temp, perFilename(0, "jpg"))
        val second = File(temp, perFilename(1, "jpg"))
        val archive = dir / "${book.gid}.cbz"
        val old = Settings.saveAsCbz.value
        try {
            Settings.saveAsCbz.value = true
            Settings.saveAsCbz.valueFlow().first { it }
            assertTrue(dir.toFile().mkdirs())
            assertTrue(temp.mkdirs())
            writeComicInfo(book.getComicInfo(), dir / COMIC_INFO_FILE)
            first.writeBytes(byteArrayOf(11, 12))
            suspend fun open() = SpiderDen(book, dir.name).also { it.setMode(SpiderQueen.MODE_DOWNLOAD) }
            val failed = open()
            assertTrue(failed.archive(2))
            assertFalse(failed.postArchive())
            assertTrue(first.exists())
            assertFalse(archive.exists())

            second.writeBytes(byteArrayOf(20, 21))
            val marker = dir / ".${archive.name}.download-incomplete"
            marker.toFile().writeText(archive.name)
            archive.toFile().writeText("interrupted")
            assertNull(dir.completedArchive(archive.name))
            val cachedOutput = CachedPageOutput({}) { error("Must not offer to overwrite an incomplete managed archive") }
            assertThrows(IllegalStateException::class.java) { runBlocking { cachedOutput.write(first, archive) } }
            val retry = open()
            assertFalse(retry.archive(2))
            assertFalse(marker.exists())
            ZipFile(archive.toFile()).use { zip ->
                assertEquals(listOf(first.name, second.name, COMIC_INFO_FILE), zip.entries().asSequence().map { it.name }.toList())
                assertArrayEquals(first.readBytes(), zip.getInputStream(zip.getEntry(first.name)).use { it.readBytes() })
            }
            val backup = File(dir.toFile(), "moved.cbz")
            assertTrue(archive.toFile().renameTo(backup))
            assertFalse(retry.postArchive())
            assertTrue(first.exists())
            assertTrue(backup.renameTo(archive.toFile()))
            second.writeText("replaced after archiving")
            val extra = File(temp, "keep.txt").apply { writeText("unrelated") }
            assertTrue(retry.postArchive())
            assertFalse(first.exists())
            assertTrue(second.exists())
            assertTrue(extra.exists())
        } finally {
            temp.deleteRecursively()
            dir.toFile().deleteRecursively()
            Settings.saveAsCbz.value = old
        }
        Unit
    }

    @Test
    fun publicationFailurePreservesSourceAndExistingUnmarkedArchive() {
        val dir = File(appCtx.cacheDir, "publish-${System.nanoTime()}")
        assertTrue(dir.mkdirs())
        try {
            val source = File(dir, "verified.zip")
            ZipOutputStream(source.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("page"))
                zip.write(ByteArray(131072) { it.toByte() })
                zip.closeEntry()
            }
            val destination = File(dir, "123.cbz").toOkioPath()
            val expected = source.readBytes()
            assertThrows(IOException::class.java) {
                publishDownloadArchive(source, destination) {
                    if (destination.exists() && destination.hasIncompleteDownloadArchive()) throw IOException("Interrupted before final verification")
                }
            }
            assertArrayEquals(expected, source.readBytes())
            assertNull(dir.toOkioPath().completedArchive(destination.name))
            publishDownloadArchive(source, destination) {}
            assertArrayEquals(expected, destination.toFile().readBytes())
            destination.toFile().writeText("pre-existing archive")
            assertThrows(IllegalStateException::class.java) { publishDownloadArchive(source, destination) {} }
            assertEquals("pre-existing archive", destination.toFile().readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun restorationProtectsRenamedDirectoryBeforeAndAfterConfirmation() = runBlocking(Dispatchers.IO) {
        val book = gallery()
        val root = downloadLocation
        val old = root / "${book.gid}-before"
        val moved = root / "${book.gid}-after"
        val orphan = root / "${book.gid + 1}-unused"
        try {
            assertTrue(old.toFile().mkdirs())
            writeComicInfo(book.getComicInfo(), old / COMIC_INFO_FILE)
            assertTrue(DownloadManager.restoreFromDirectory(root, book, old.name))
            val item = requireNotNull(DownloadManager.getDownloadInfo(book.gid))
            val state = item.state
            assertTrue(old.toFile().renameTo(moved.toFile()))
            assertTrue(DownloadManager.reattachDownloadDirectory(root, book, moved))
            assertSame(item, DownloadManager.getDownloadInfo(book.gid))
            assertEquals(moved.name, item.dirname)
            assertEquals(state, item.state)
            assertEquals(moved.name, EhDB.getAllDownloadInfo().single { it.gid == book.gid }.dirname)
            File(moved.toFile(), "${book.gid}.zip").writeText("old archive")
            File(moved.toFile(), "${book.gid}.cbz").writeText("in progress")
            File(moved.toFile(), ".${book.gid}.cbz.download-incomplete").writeText("busy")
            assertEquals("${book.gid}.zip", item.archiveFile?.name)
            assertFalse(DownloadManager.isRedundantDirectory(root, moved))
            assertFalse(DownloadManager.deleteRedundantDirectory(root, moved))
            assertTrue(moved.exists())

            assertTrue(orphan.toFile().mkdirs())
            assertTrue(DownloadManager.isRedundantDirectory(root, orphan))
            // The dialog is open. Ownership changes before its delete button is pressed.
            writeComicInfo(book.getComicInfo(), orphan / COMIC_INFO_FILE)
            assertFalse(DownloadManager.deleteRedundantDirectory(root, orphan))
            assertTrue(orphan.exists())
            SpiderInfo(book.gid + 1, book.token, 2).write(orphan / SpiderQueen.SPIDER_INFO_FILENAME)
            assertNull(readDirectoryIdentity(orphan))
            assertFalse(DownloadManager.deleteRedundantDirectory(root, orphan))
            assertNotNull(readDirectoryIdentity(moved))
        } finally {
            DownloadManager.deleteDownload(book.gid, false)
            EhDB.removeDownloadDirname(book.gid)
            listOf(old, moved, orphan).forEach { it.toFile().deleteRecursively() }
        }
    }
}
