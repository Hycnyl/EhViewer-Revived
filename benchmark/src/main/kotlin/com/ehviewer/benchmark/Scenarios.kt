package com.ehviewer.benchmark

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.io.File

internal val targetAppId: String
    get() = checkNotNull(InstrumentationRegistry.getArguments().getString("targetAppId"))

internal fun MacrobenchmarkScope.prepare(scene: String) {
    startActivityAndWait(
        Intent("com.hippo.ehviewer.BENCHMARK").setPackage(packageName)
            .putExtra("scene", scene).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )
    if (scene == "pager" || scene == "webtoon") {
        check(device.wait(Until.hasObject(By.text(" / ")), 20_000)) { "Offline reader did not become ready" }
        waitForReaderImage()
    } else {
        val ready = if (scene == "grid") By.text("EN") else By.textStartsWith("Offline gallery")
        check(device.wait(Until.hasObject(ready), 20_000)) { "Offline favourites gallery did not become ready" }
    }
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.scrollGallery() {
    // Stay below the app/status bars. Cached UiObject2 bounds can span those bars or go stale.
    val x = device.displayWidth / 2
    val h = device.displayHeight
    repeat(5) { device.swipe(x, h * 4 / 5, x, h / 3, 12) }
    repeat(5) { device.swipe(x, h / 3, x, h * 4 / 5, 12) }
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.turnPages() {
    val w = device.displayWidth
    val h = device.displayHeight
    repeat(8) {
        device.swipe(w * 4 / 5, h / 2, w / 5, h / 2, 16)
        device.waitForIdle()
    }
}

internal fun MacrobenchmarkScope.scrollWebtoon() {
    val w = device.displayWidth
    val h = device.displayHeight
    repeat(8) { device.swipe(w / 2, h * 4 / 5, w / 2, h / 5, 16) }
}

internal fun MacrobenchmarkScope.zoomPage() {
    val content = checkNotNull(device.findObject(By.pkg(packageName).depth(0)))
    content.pinchOpen(0.65f)
    content.pinchClose(0.65f)
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.navigateGallery(repetitions: Int = 3) {
    repeat(repetitions) {
        val title = checkNotNull(
            device.findObjects(By.textStartsWith("Offline gallery")).firstOrNull {
                val bounds = it.visibleBounds
                bounds.top >= device.displayHeight / 6 && bounds.bottom < device.displayHeight * 9 / 10 && bounds.width() > 0 && bounds.height() > 0
            },
        ) { "No fully visible offline gallery title" }
        val titleLabel = title.text
        var card = title
        while (!card.isClickable) card = checkNotNull(card.parent) { "Gallery title has no clickable card" }
        card.click()
        check(device.wait(Until.hasObject(By.text("Read")), 10_000)) { "Cached gallery detail did not open: $titleLabel" }
        device.pressBack()
        check(device.wait(Until.hasObject(By.textStartsWith("Offline gallery")), 10_000))
        device.waitForIdle()
    }
}

internal fun MacrobenchmarkScope.toggleDrawer(repetitions: Int = 4) {
    val w = device.displayWidth
    val h = device.displayHeight
    repeat(repetitions) {
        device.swipe(w / 8, h / 2, w * 4 / 5, h / 2, 24)
        check(device.wait(Until.hasObject(By.pkg(packageName).text("Settings")), 5_000)) { "Navigation drawer did not open" }
        device.waitForIdle()
        device.pressBack()
        check(device.wait(Until.gone(By.pkg(packageName).text("Settings")), 5_000)) { "Navigation drawer did not close" }
        check(device.hasObject(By.pkg(packageName).textStartsWith("Offline gallery"))) { "Drawer back exited the gallery" }
        device.waitForIdle()
    }
}

/** Wait for the fixture's actual coloured image, not just its page-count overlay. */
private fun MacrobenchmarkScope.waitForReaderImage() {
    val file = File(InstrumentationRegistry.getInstrumentation().context.cacheDir, "reader-ready.png")
    val deadline = SystemClock.uptimeMillis() + 20_000
    try {
        while (SystemClock.uptimeMillis() < deadline) {
            check(device.takeScreenshot(file))
            val bitmap = checkNotNull(BitmapFactory.decodeFile(file.path))
            var coloured = 0
            for (x in 3..7) {
                for (y in 2..8) {
                    val colour = bitmap.getPixel(bitmap.width * x / 10, bitmap.height * y / 10)
                    val channels = listOf(Color.red(colour), Color.green(colour), Color.blue(colour))
                    if (channels.max() - channels.min() > 30 && channels.max() < 240) coloured++
                }
            }
            bitmap.recycle()
            if (coloured >= 12) return
            SystemClock.sleep(200)
        }
        error("Offline reader page pixels did not load")
    } finally {
        file.delete()
    }
}
