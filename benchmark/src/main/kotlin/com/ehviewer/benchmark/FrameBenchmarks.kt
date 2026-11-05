package com.ehviewer.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrameBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun galleryList() = measure("list") { scrollGallery() }

    @Test fun galleryGrid() = measure("grid") { scrollGallery() }

    @Test fun pager() = measure("pager") { turnPages() }

    @Test fun webtoon() = measure("webtoon") { scrollWebtoon() }

    @Test fun navigation() = measure("list") { navigateGallery() }

    @Test fun drawer() = measure("list") { toggleDrawer() }

    private fun measure(scene: String, action: MacrobenchmarkScope.() -> Unit) = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.WARM,
        iterations = 3,
        setupBlock = {
            killProcess()
            pressHome()
            prepare(scene)
        },
        measureBlock = action,
    )
}
