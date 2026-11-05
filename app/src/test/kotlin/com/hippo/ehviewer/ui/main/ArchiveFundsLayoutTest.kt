package com.hippo.ehviewer.ui.main

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hippo.ehviewer.client.parser.Archive
import com.hippo.ehviewer.client.parser.Funds
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w800dp-h1200dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArchiveFundsLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun normalText() = checkLayout("normal", 320, 1f)

    @Test
    fun largerText() = checkLayout("larger", 480, 1.5f)

    @Test
    fun largestText() = checkLayout("largest", 600, 2f)

    @Test
    fun narrowLargeTextWraps() = checkLayout("narrow-large", 320, 2f, stacked = true)

    @Test
    fun rightToLeft() = checkLayout("rtl", 320, 1f, direction = LayoutDirection.Rtl)

    private fun checkLayout(
        name: String,
        width: Int,
        fontScale: Float,
        stacked: Boolean = false,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale),
                LocalLayoutDirection provides direction,
            ) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize()) {
                        Surface(Modifier.width(width.dp).height(400.dp).testTag("archive")) {
                            ArchiveList(
                                funds = Funds(gp = 12345, credit = 678901),
                                items = listOf(
                                    Archive("org", "Original", "128 MB", "Free!", false),
                                    Archive("res", "Resample", "64 MB", "Free!", false),
                                    Archive("org", "Original", "128 MB", "Free", true),
                                ),
                                onItemClick = {},
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val gp = compose.onNodeWithText("GP", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val credit = compose.onNodeWithText("C", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        if (stacked) {
            assertTrue("Large text must wrap instead of squeezing the two currencies", credit.top >= gp.bottom)
        } else {
            assertEquals("GP and C badge centers must align", gp.center.y, credit.center.y, 0.5f)
        }
        val image = compose.onNodeWithTag("archive").captureToImage().asAndroidBitmap()
        val output = File("build/reports/archive-ui/$name.png")
        output.parentFile.mkdirs()
        output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
