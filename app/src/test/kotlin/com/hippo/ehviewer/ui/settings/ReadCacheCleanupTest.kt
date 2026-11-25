// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import coil3.disk.DiskCache
import com.ehviewer.core.model.BaseGalleryInfo
import com.hippo.ehviewer.client.getImageKey
import com.hippo.ehviewer.coil.ReadCacheCleaner
import com.hippo.ehviewer.coil.ReadCachedGallery
import com.hippo.ehviewer.coil.clearGalleryImages
import com.hippo.ehviewer.coil.findReadCachedGalleries
import com.hippo.ehviewer.ui.tools.DialogState
import java.time.Instant
import java.time.ZoneId
import me.zhanghai.compose.preference.ProvidePreferenceTheme
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rUS-w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadCacheCleanupTest {
    @get:Rule val compose = createComposeRule()

    @get:Rule val temp = TemporaryFolder()

    private var disk: DiskCache? = null
    private val cache
        get() = disk ?: DiskCache.Builder().directory(temp.newFolder().toOkioPath()).maxSizeBytes(1024 * 1024).build().also { disk = it }

    @After
    fun closeCache() {
        disk?.shutdown()
    }

    private fun storeImage(key: String, dataTime: Long, metadataTime: Long = dataTime): Path {
        val editor = checkNotNull(cache.openEditor(key))
        editor.metadata.toFile().writeText("jpg")
        editor.data.toFile().writeBytes(ByteArray(9) { it.toByte() })
        return checkNotNull(editor.commitAndOpenSnapshot()).use { snapshot ->
            assertTrue(snapshot.data.toFile().setLastModified(dataTime))
            assertTrue(snapshot.metadata.toFile().setLastModified(metadataTime))
            snapshot.data
        }
    }

    private fun exists(key: String): Boolean = cache.openSnapshot(key)?.use { true } == true

    @Test
    fun cutoffFollowsLocalClockAndRemovesOnlyEarlierEntries() {
        val date = Instant.parse("2026-10-04T00:00:00Z").toEpochMilli()
        val cutoff = readCacheCutoffMillis(date, 9, 30, ZoneId.of("Asia/Shanghai"))
        assertEquals(Instant.parse("2026-10-04T01:30:00Z").toEpochMilli(), cutoff)
        assertEquals(Instant.parse("2026-10-05T06:30:00Z").toEpochMilli(), readCacheCutoffMillis(date, 23, 30, ZoneId.of("America/Los_Angeles")))

        storeImage("old", cutoff - 1000)
        storeImage("exact", cutoff)
        storeImage("new", cutoff + 1000)
        storeImage("new-metadata", cutoff - 1000, cutoff + 1000)
        assertEquals(1, ReadCacheCleaner.clearBefore(cache, cutoff))
        assertTrue(!exists("old"))
        assertTrue(exists("exact") && exists("new") && exists("new-metadata"))
    }

    @Test
    fun gallerySelectionUsesExistingPageKeysAndKeepsOtherImages() {
        val start = 1_700_000_000_000L
        val known = BaseGalleryInfo(gid = 100, pages = 1, thumbKey = "cover.jpg")
        val untouched = BaseGalleryInfo(gid = 200, pages = 2)
        val coverOnly = BaseGalleryInfo(gid = 300, pages = 2, thumbKey = "cover-only.jpg")
        storeImage(getImageKey(100, 0), start)
        storeImage(getImageKey(100, 3), start + 3000)
        storeImage(getImageKey(200, 0), start + 1000)
        val preview = storeImage(getImageKey(400, 2), start + 2000)
        storeImage(getImageKey(900, 0), start)
        val cover = storeImage("cover.jpg", start + 4000)
        storeImage("cover-only.jpg", start)

        val rows = findReadCachedGalleries(cache, listOf(known, untouched, coverOnly), mapOf(100L to 4, 400L to 3))
        assertEquals(listOf(100L, 400L, 200L), rows.map { it.gid })
        assertEquals(2, rows.first().cachedPages)
        assertEquals(18L, rows.first().bytes)
        assertEquals(cover, rows.first().preview)
        assertEquals(preview, rows[1].preview)
        assertNotNull(rows.first().info)
        assertNull(rows[1].info)

        assertEquals(3, rows.take(2).sumOf { cache.clearGalleryImages(it.gid, 0 until it.pageLimit) })
        assertTrue(!exists(getImageKey(100, 0)) && !exists(getImageKey(100, 3)) && !exists(getImageKey(400, 2)))
        assertTrue(exists(getImageKey(200, 0)) && exists(getImageKey(900, 0)))
        assertTrue(exists("cover.jpg") && exists("cover-only.jpg"))
    }

    @Test
    fun longPressStartsSelectionAndBothViewsKeepIt() {
        val cleared = mutableListOf<Long>()
        showGalleryList { galleries -> cleared += galleries.map { it.gid } }

        val first = compose.onNodeWithText("Gallery #100")
        first.performClick().assertIsNotSelected()
        compose.onNodeWithText("Clear").assertDoesNotExist()
        first.performTouchInput { longClick() }.assertIsSelected()
        compose.onNodeWithContentDescription("Cover view").performClick()
        compose.onNodeWithContentDescription("Gallery #100").assertIsSelected()
        compose.onNodeWithContentDescription("Gallery #200").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("List view").performClick()
        first.assertIsSelected()
        compose.onNodeWithText("Gallery #200").assertIsSelected()
        compose.onNodeWithText("2 selected").assertExists()
        compose.onNodeWithText("Clear").performClick().assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(100L, 200L), cleared) }
    }

    @Test
    fun selectionMenuCanInvertRefillAndClearSelection() {
        showGalleryList()
        val first = compose.onNodeWithText("Gallery #100")
        val second = compose.onNodeWithText("Gallery #200")
        val third = compose.onNodeWithText("Gallery #300")

        chooseSelectionAction("Select all")
        second.performClick().assertIsNotSelected()

        chooseSelectionAction("Invert selection")
        first.assertIsNotSelected()
        second.assertIsSelected()
        third.assertIsNotSelected()
        chooseSelectionAction("Invert selection")
        first.assertIsSelected()
        second.assertIsNotSelected()
        third.assertIsSelected()

        chooseSelectionAction("Select all")
        second.assertIsSelected()
        chooseSelectionAction("Invert selection")
        compose.onNodeWithText("Clear").assertDoesNotExist()
        chooseSelectionAction("Invert selection")
        compose.onNodeWithText("3 selected").assertExists()
        chooseSelectionAction("Cancel selection")
        first.assertIsNotSelected()
        second.assertIsNotSelected()
        third.assertIsNotSelected()
        compose.onNodeWithText("Clear").assertDoesNotExist()
    }

    private fun showGalleryList(onClear: suspend (List<ReadCachedGallery>) -> Unit = {}) {
        val preview = temp.newFile("preview.png").toOkioPath()
        val rows = listOf(100L, 200L, 300L).map { gid ->
            ReadCachedGallery(gid, null, preview, 1, 1, 9, 1_700_000_000_000 - gid)
        }
        compose.setContent {
            MaterialTheme {
                ReadCacheGalleryLayout(
                    galleries = rows,
                    listThumbHeight = 120.dp,
                    loadFailed = false,
                    onRetry = {},
                    navigationIcon = {},
                    onClear = onClear,
                )
            }
        }
    }

    private fun chooseSelectionAction(action: String) {
        compose.onNodeWithContentDescription("Selection actions").performClick()
        compose.onNodeWithText(action).performClick()
    }

    @Test
    fun timeModeOpensDateThenClockThenConfirmation() {
        compose.setContent {
            val dialog = remember { DialogState() }
            val snackbar = remember { SnackbarHostState() }
            val context = LocalContext.current
            MaterialTheme {
                ProvidePreferenceTheme {
                    Box {
                        with(context) {
                            with(dialog) {
                                with(snackbar) { ReadCacheCleanupPreference {} }
                            }
                        }
                        dialog.Place()
                    }
                }
            }
        }

        compose.onNodeWithText("Clear reading cache").performClick()
        compose.onNodeWithText("By time").performClick()
        compose.onNodeWithText("Choose a date").assertExists()
        compose.onNodeWithText("OK", ignoreCase = true).performClick()
        compose.onNodeWithText("Choose a time").assertExists()
        compose.onNodeWithText("OK", ignoreCase = true).performClick()
        compose.onNodeWithText("Clear images cached before", substring = true).assertExists()
        compose.onNodeWithText("Cancel", ignoreCase = true).performClick()
        compose.onNodeWithText("Clear images cached before", substring = true).assertDoesNotExist()
    }
}
