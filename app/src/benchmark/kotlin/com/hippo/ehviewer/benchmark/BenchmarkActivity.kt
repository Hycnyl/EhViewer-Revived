package com.hippo.ehviewer.benchmark

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import com.ehviewer.core.model.BaseGalleryInfo
import com.ehviewer.core.model.GalleryCommentList
import com.ehviewer.core.model.GalleryDetail
import com.ehviewer.core.model.V1GalleryPreview
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.ui.screen.detailCache
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Synthetic performance variants only. Never compiled into a distributed APK. */
class BenchmarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scene = intent.getStringExtra("scene") ?: "list"
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { prepare(scene) }
            val target = Intent(this@BenchmarkActivity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                if (scene == "pager" || scene == "webtoon") {
                    action = Intent.ACTION_VIEW
                    setDataAndType(
                        FileProvider.getUriForFile(this@BenchmarkActivity, "$packageName.fileprovider", File(cacheDir, "offline-reader.cbz")),
                        "application/vnd.comicbook+zip",
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    action = Intent.ACTION_MAIN
                }
            }
            startActivity(target)
            finish()
        }
    }

    private suspend fun prepare(scene: String) {
        Settings.needSignIn.value = false
        Settings.launchPage.value = 4 // Actual local Favourites screen and its GalleryList.
        Settings.recentFavCat = -2
        Settings.listMode.value = if (scene == "grid") 1 else 0
        Settings.readingMode.value = if (scene == "webtoon") 4 else 1
        Settings.showNavigationOverlayNewUser.value = false
        Settings.showNavigationOverlayOnStart.value = false
        Settings.appLinkVerifyTip = true
        val prefs = getSharedPreferences("offline-benchmark", MODE_PRIVATE)
        if (!File(cacheDir, "offline-reader.cbz").isFile) generateImages()
        if (!prefs.getBoolean("seeded-v1", false)) {
            for (index in 119 downTo 0) EhDB.putLocalFavorites(gallery(index))
            check(prefs.edit().putBoolean("seeded-v1", true).commit())
        }
        // Keep every synthetic detail offline, including viewports reached after scrolling.
        detailCache.resize(160)
        val previews = List(24) { page ->
            V1GalleryPreview("https://offline.ehviewer.invalid/thumb/$page.jpg", page, "0123456789")
        }
        for (index in 0 until 120) {
            val info = gallery(index)
            detailCache[info.gid] = GalleryDetail(
                galleryInfo = info,
                tagGroups = emptyList(),
                comments = GalleryCommentList(emptyList(), false),
                previewList = previews,
                language = "English",
                size = "24 pages, local fixture",
            )
        }
    }

    private fun gallery(index: Int) = BaseGalleryInfo(
        gid = 9000000L + index,
        token = "0123456789",
        title = "Offline gallery %03d".format(index + 1),
        thumbKey = "https://offline.ehviewer.invalid/thumb/$index.jpg",
        category = 512,
        posted = "2026-01-01 00:00",
        uploader = "Local benchmark fixture",
        rating = 4.5f,
        simpleTags = listOf("language:english"),
        pages = 24,
        thumbWidth = 240,
        thumbHeight = 356,
        simpleLanguage = "EN",
    )

    private fun generateImages() {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        ZipOutputStream(File(cacheDir, "offline-reader.cbz").outputStream().buffered()).use { zip ->
            repeat(24) { page ->
                val bitmap = Bitmap.createBitmap(1080, 1600, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.rgb(242, 239, 228))
                repeat(48) { tile ->
                    paint.color = Color.rgb((tile * 43 + page * 17) % 220, (tile * 71 + 30) % 220, (page * 37 + tile * 11) % 220)
                    val x = (tile % 6) * 180f
                    val y = (tile / 6) * 200f
                    canvas.drawRoundRect(x + 8, y + 8, x + 172, y + 192, 20f, 20f, paint)
                }
                paint.color = Color.WHITE
                paint.textSize = 72f
                canvas.drawText("Page ${page + 1}", 60f, 100f, paint)
                zip.putNextEntry(ZipEntry("%03d.jpg".format(page)))
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, zip))
                zip.closeEntry()
                val thumb = Bitmap.createScaledBitmap(bitmap, 240, 356, true)
                File(cacheDir, "offline-thumb-$page.jpg").outputStream().use { check(thumb.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
                thumb.recycle()
                bitmap.recycle()
            }
        }
    }
}

internal class OfflineThumbs(private val directory: File) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data.toString()
        if (data.startsWith("https://offline.ehviewer.invalid/thumb/")) {
            val index = data.substringAfterLast('/').substringBefore('.').toInt()
            return chain.withRequest(chain.request.newBuilder().data(File(directory, "offline-thumb-${index % 24}.jpg")).build()).proceed()
        }
        return chain.proceed()
    }
}
