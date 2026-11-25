package com.hippo.ehviewer.ui.main

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.hippo.ehviewer.client.parser.Limits
import com.materialkolor.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rUS-w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GalleryDialogUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun quotaUsesExactThresholdsAndRequiresAKnownLimit() {
        assertNull(Limits(0, -1, 0).quotaLevel())
        assertNull(Limits(0, 0, 0).quotaLevel())
        assertEquals(ImageQuotaLevel.Available, Limits(75, 100, 0).quotaLevel())
        assertEquals(ImageQuotaLevel.NearLimit, Limits(76, 100, 0).quotaLevel())
        assertEquals(ImageQuotaLevel.NearLimit, Limits(99, 100, 0).quotaLevel())
        assertEquals(ImageQuotaLevel.Exhausted, Limits(100, 100, 0).quotaLevel())
        assertEquals(ImageQuotaLevel.Exhausted, Limits(101, 100, 0).quotaLevel())
        assertEquals(ImageQuotaLevel.NearLimit, Limits(Int.MAX_VALUE - 1, Int.MAX_VALUE, 0).quotaLevel())
    }

    @Test
    fun quotaTintsFollowTheThemeAndRetainTheirHueAndContrast() {
        val blue = Color(0xFF415E99)
        val purple = Color(0xFF7D408B)
        val light = Color(0xFFEDEBF3)
        val dark = Color(0xFF26232B)
        val hueRanges = mapOf(
            ImageQuotaLevel.Available to 130.0..160.0,
            ImageQuotaLevel.NearLimit to 76.0..108.0,
            ImageQuotaLevel.Exhausted to 10.0..40.0,
        )
        for ((level, hues) in hueRanges) {
            assertNotEquals(level.tint(blue, light), level.tint(purple, light))
            for (background in listOf(light, dark)) {
                val tint = level.tint(blue, background)
                assertTrue(Hct.fromInt(tint.toArgb()).hue in hues)
                val first = tint.luminance()
                val second = background.luminance()
                assertTrue((maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f) >= 3f)
            }
        }
    }

    @Test
    fun quotaTipCanBeAcknowledgedOrTurnedOffFromItsIcon() {
        compose.setContent {
            MaterialTheme {
                var enabled by remember { mutableStateOf(true) }
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    title = { ImageQuotaTitle(ImageQuotaLevel.NearLimit.takeIf { enabled }) { enabled = false } },
                    text = { Text("Quota figures") },
                )
            }
        }

        val icon = compose.onNodeWithContentDescription("Image quota nearly exhausted")
        icon.performClick()
        compose.onNodeWithText("Image Quota Usage Tip").assertExists()
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("Image Quota Usage Tip").assertDoesNotExist()
        icon.performClick()
        compose.onNodeWithText("Turn off tip").performClick()
        compose.onNodeWithText("Image Quota Usage Tip").assertDoesNotExist()
        icon.assertDoesNotExist()
        compose.onNodeWithText("Quota figures").assertExists()
    }

    @Test
    fun longTagKeepsItsFullTextInAScrollablePreview() {
        val value = "parody:" + "blue archive ".repeat(24).trim()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(240.dp)) { TagActionLabel("Search", value) }
            }
        }

        compose.onNodeWithText("Search").assertExists()
        compose.onNodeWithText(value).assertExists()
        val key = SemanticsProperties.HorizontalScrollAxisRange
        val scrollable = compose.onNode(SemanticsMatcher.keyIsDefined(key), useUnmergedTree = true)
        assertTrue(scrollable.fetchSemanticsNode().config[key].maxValue() > 0f)
    }
}
