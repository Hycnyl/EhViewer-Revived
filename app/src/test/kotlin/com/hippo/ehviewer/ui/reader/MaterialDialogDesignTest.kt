package com.hippo.ehviewer.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.media.ExifInterface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hippo.ehviewer.image.ImageDimensions
import com.hippo.ehviewer.image.ImageFileInfo
import com.hippo.ehviewer.ui.main.GalleryViewToggle
import com.materialkolor.dynamiccolor.ColorSpec.SpecVersion
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.from
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.toColorScheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rUS-w1000dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaterialDialogDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun lightPortrait() = render(false, false, ImageFileInfo(ImageDimensions(1920, 1080, "image/jpeg"), 1_200_000, ExifInterface.ORIENTATION_ROTATE_90), "light-portrait")

    @Test
    fun darkWidePortrait() = render(true, true, ImageFileInfo(ImageDimensions(1200, 2400, "image/webp"), 350_000), "dark-wide-portrait")

    @Test
    fun lightWideLandscape() = render(false, true, ImageFileInfo(ImageDimensions(2400, 1200, "image/webp"), 350_000), "light-wide-landscape")

    @Test
    fun wideDialogUsesWindowWidth() {
        var confirmed = false
        compose.setContent {
            MaterialTheme {
                ImageInfoDialog(
                    ImageFileInfo(ImageDimensions(2400, 1200, "image/webp"), 350_000),
                    12,
                    48,
                    onDismiss = {},
                    onConfirm = { confirmed = true },
                )
            }
        }
        compose.waitForIdle()
        val diagram = compose.onNodeWithContentDescription("Resolution: 2400 × 1200 px").fetchSemanticsNode().boundsInRoot
        val metadata = compose.onNodeWithText("Format").fetchSemanticsNode().boundsInRoot
        assertTrue("A real wide dialog must place metadata beside its diagram", metadata.left > diagram.right)
        val dialog = compose.onNode(isDialog())
        assertTrue("Dialog wraps its content height", dialog.fetchSemanticsNode().boundsInRoot.height < 600f)
        val file = File("build/reports/material-dialog-ui/wide-dialog.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { dialog.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("OK").performClick()
        assertTrue(confirmed)
    }

    private fun render(dark: Boolean, wide: Boolean, info: ImageFileInfo, name: String) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, if (dark) 1.3f else 1f)) {
                MaterialTheme(colorScheme = SchemeTonalSpot(Hct.from(Color(0xFF63758F)), dark, 0.0, SpecVersion.SPEC_2021).toColorScheme()) {
                    Surface(Modifier.width(if (wide) 800.dp else 360.dp).testTag("design")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Image information", style = MaterialTheme.typography.titleLarge)
                            Box(Modifier.width(if (wide) 760.dp else 280.dp).heightIn(max = 620.dp)) { ImageInfoContent(info, 12, 48) }
                            var gridView by remember { mutableStateOf(false) }
                            Row {
                                GalleryViewToggle(gridView, onToggle = { gridView = it })
                                Text("Gallery view", modifier = Modifier.padding(12.dp))
                            }
                            LinearWavyProgressIndicator(progress = { 0.85f }, modifier = Modifier.height(12.dp))
                            LinearWavyProgressIndicator(progress = { 1f }, modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val resolution = "Resolution: ${info.displayWidth} × ${info.displayHeight} px"
        val diagram = compose.onNodeWithContentDescription(resolution).fetchSemanticsNode().boundsInRoot
        val metadata = compose.onNodeWithText("Format").fetchSemanticsNode().boundsInRoot
        assertTrue("Metadata follows the available width", if (wide) metadata.left > diagram.right else metadata.top > diagram.bottom)
        compose.onNodeWithContentDescription("Cover view").performClick()
        compose.onNodeWithContentDescription("List view").assertExists().performClick()
        compose.onNodeWithContentDescription("Cover view").assertExists()
        val image = compose.onNodeWithTag("design").captureToImage().asAndroidBitmap()
        val file = File("build/reports/material-dialog-ui/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
