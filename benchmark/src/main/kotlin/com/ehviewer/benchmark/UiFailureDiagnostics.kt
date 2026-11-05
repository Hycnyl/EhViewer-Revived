package com.ehviewer.benchmark

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Diagnostic artifacts only on failure; never included in measured successful interactions. */
class UiFailureDiagnostics : TestWatcher() {
    override fun failed(e: Throwable, description: Description) {
        runCatching {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?.let(::File) ?: instrumentation.context.cacheDir
            directory.mkdirs()
            val name = "${description.testClass.simpleName}_${description.methodName}_failure"
            val device = UiDevice.getInstance(instrumentation)
            device.takeScreenshot(File(directory, "$name.png"))
            val hierarchy = File(directory, "$name.xml")
            device.dumpWindowHierarchy(hierarchy)
            hierarchy.readText().chunked(3000).forEach { Log.e("BenchmarkUiFailure", it) }
        }.onFailure { Log.e("BenchmarkUiFailure", "Could not capture diagnostic UI", it) }
    }
}
