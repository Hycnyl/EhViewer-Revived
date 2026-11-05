package com.ehviewer.benchmark

import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Before
    fun seedOfflineContent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.context.startActivity(
            Intent("com.hippo.ehviewer.BENCHMARK").setPackage(targetAppId)
                .putExtra("scene", "list").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        check(UiDevice.getInstance(instrumentation).wait(Until.hasObject(By.textStartsWith("Offline gallery")), 30_000))
    }

    @Test
    fun startup() = rule.collect(targetAppId, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun galleryAndNavigation() = rule.collect(targetAppId) {
        prepare("list")
        scrollGallery()
        navigateGallery()
        toggleDrawer()
        prepare("grid")
        scrollGallery()
    }

    @Test
    fun pagerReader() = rule.collect(targetAppId) {
        prepare("pager")
        turnPages()
        zoomPage()
    }

    @Test
    fun webtoonReader() = rule.collect(targetAppId) {
        prepare("webtoon")
        scrollWebtoon()
        zoomPage()
    }
}
