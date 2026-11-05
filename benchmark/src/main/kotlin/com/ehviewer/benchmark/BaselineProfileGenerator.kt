package com.ehviewer.benchmark

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
        UiDevice.getInstance(instrumentation).executeShellCommand(
            "am start -W -a com.hippo.ehviewer.BENCHMARK -p $targetAppId --es scene list -f 0x10008000",
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
        // Scrolling can end at any viewport; reset before testing a cached detail.
        prepare("list")
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
