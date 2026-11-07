package com.hippo.ehviewer.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.media.ExifInterface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hippo.ehviewer.client.parser.Archive
import com.hippo.ehviewer.client.parser.Funds
import com.hippo.ehviewer.image.ImageDimensions
import com.hippo.ehviewer.image.ImageFileInfo
import com.hippo.ehviewer.ui.main.ArchiveList
import com.hippo.ehviewer.ui.main.quotaAlertColors
import com.materialkolor.dynamiccolor.ColorSpec.SpecVersion
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.from
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.toColorScheme
import java.io.File
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rUS-w500dp-h1600dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaterialDialogDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun lightPortrait() = render(false, ImageFileInfo(ImageDimensions(1920, 1080, "image/jpeg"), 1_200_000, ExifInterface.ORIENTATION_ROTATE_90))

    @Test
    fun darkLandscape() = render(true, ImageFileInfo(ImageDimensions(2400, 1200, "image/webp"), 350_000))

    private fun render(dark: Boolean, info: ImageFileInfo) {
        val archives = listOf(
            Archive("org", "Original", "128 MB", "Free!", false),
            Archive("res", "Resample", "64 MB", "20 GP", false),
            Archive("org", "Original", "128 MB", "Free", true),
        )
        var selected: Archive? = null
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, if (dark) 1.3f else 1f)) {
                MaterialTheme(colorScheme = SchemeTonalSpot(Hct.from(Color(0xFF63758F)), dark, 0.0, SpecVersion.SPEC_2021).toColorScheme()) {
                    Surface(Modifier.width(360.dp).testTag("design")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Image information", style = MaterialTheme.typography.titleLarge)
                            Box(Modifier.width(280.dp).heightIn(max = 500.dp)) { ImageInfoContent(info, 12, 48) }
                            Text("Archive download", style = MaterialTheme.typography.titleLarge)
                            ArchiveList(Funds(gp = 12345, credit = 678901), archives, { selected = it }, Modifier.width(280.dp).heightIn(max = 520.dp))
                            val (warning, critical) = quotaAlertColors(MaterialTheme.colorScheme)
                            assertTrue("Warning and critical colors must remain distinct", warning != critical)
                            assertTrue("Warning must retain semantic color rather than turn white", Hct.from(warning).chroma > 10.0)
                            assertTrue("Critical must retain semantic color rather than turn white", Hct.from(critical).chroma > 10.0)
                            LinearProgressIndicator(progress = { 0.85f }, color = warning)
                            LinearProgressIndicator(progress = { 1f }, color = critical)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(if (dark) "Resolution: 2400 × 1200 px" else "Resolution: 1080 × 1920 px").assertExists()
        compose.onNodeWithText("Current Funds:").assertDoesNotExist()
        compose.onNodeWithText("Download options").assertDoesNotExist()
        compose.onAllNodesWithText("H@H", useUnmergedTree = true).assertCountEquals(1)
        val image = compose.onNodeWithTag("design").captureToImage().asAndroidBitmap()
        val file = File("build/reports/material-dialog-ui/${if (dark) "dark-landscape" else "light-portrait"}.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNode(hasText("Original") and hasText("H@H")).performClick()
        assertSame(archives[2], selected)
    }
}
