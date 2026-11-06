package com.ehviewer.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
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

    @get:Rule val diagnostics = UiFailureDiagnostics()

    @Before
    fun seedOfflineContent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        device.pressHome()
        device.executeShellCommand("am force-stop $targetAppId")
        device.executeShellCommand(
            "am start -W -a com.hippo.ehviewer.BENCHMARK -p $targetAppId --es scene list -f 0x10008000",
        )
        check(device.wait(Until.hasObject(By.textStartsWith("Offline gallery")), 30_000))
    }

    @Test
    fun startup() = collect(includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun galleryAndNavigation() = collect {
        prepare("list")
        scrollGallery()
        // Scrolling can end at any viewport; reset before testing a cached detail.
        prepare("list")
        // ProfileRule repeats this journey until stable; frame sampling keeps its larger loops.
        navigateGallery(repetitions = 1)
        toggleDrawer(repetitions = 1)
        prepare("grid")
        scrollGallery()
    }

    @Test
    fun pagerReader() = collect {
        prepare("pager")
        turnPages()
        zoomPage()
    }

    @Test
    fun webtoonReader() = collect {
        prepare("webtoon")
        scrollWebtoon()
        zoomPage()
    }

    // Filter at capture time: consumer merging can reintroduce unfiltered Startup Profile rules.
    private fun collect(includeInStartupProfile: Boolean = false, profileBlock: MacrobenchmarkScope.() -> Unit) = rule.collect(
        packageName = targetAppId,
        includeInStartupProfile = includeInStartupProfile,
        filterPredicate = { "Lcom/hippo/ehviewer/benchmark/" !in it },
        profileBlock = profileBlock,
    )
}
