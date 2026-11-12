package com.hippo.ehviewer.ui.settings

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hippo.ehviewer.ui.theme.seedColorScheme
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
@Config(application = Application::class, sdk = [35], qualifiers = "en-rUS-w1000dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeAndTooltipTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun unifiedLightPalette() = colors(false)

    @Test
    fun unifiedDarkLargeTextPalette() = colors(true)

    private fun colors(dark: Boolean) {
        var palette by mutableStateOf(ThemePalette(1, themePresetColors.first().first, 0xFF2468AC.toInt(), listOf(0xFF2468AC.toInt(), 0xFFEF7234.toInt())))
        var style by mutableIntStateOf(0)
        var adds = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, if (dark) 1.3f else 1f)) {
                val system = seedColorScheme(0xFF6750A4.toInt(), dark, 0)
                MaterialTheme(colorScheme = system) {
                    Surface(Modifier.width(if (dark) 700.dp else 360.dp).testTag("theme")) {
                        ThemeColorControls(palette, style, dark, system, { palette = it }, { style = it }, { adds++ })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Blue").performClick().assertIsSelected()
        assertEquals(themePresetColors[1].first, palette.selectedColor)
        compose.onNodeWithText("Tonal Spot").performClick()
        compose.onNodeWithText("Vibrant").performClick()
        assertEquals(1, style)
        compose.onNodeWithContentDescription("Custom #2468AC").performClick().assertIsSelected()
        assertEquals(2, palette.source)
        compose.onNodeWithContentDescription("Dynamic").performClick().assertIsSelected()
        assertEquals(0, palette.source)
        compose.onNodeWithText("Follow wallpaper colors").assertDoesNotExist()
        compose.onNodeWithText("Choose a seed color to generate a coordinated Material palette.").assertDoesNotExist()
        val dynamic = compose.onNodeWithContentDescription("Dynamic").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithContentDescription("Purple").fetchSemanticsNode().boundsInRoot
        assertTrue(dynamic.left < first.left && dynamic.top == first.top)
        screenshot("theme", if (dark) "dark-large-palette" else "light-phone-palette")
        compose.onNodeWithContentDescription("Add color").performClick()
        assertEquals(1, adds)
    }

    @Test
    fun longPressDeletesOnlyAfterConfirmationAndRestoresPresets() {
        val blue = themePresetColors[1].first
        var palette by mutableStateOf(ThemePalette(1, blue, 0xFF2468AC.toInt(), listOf(0xFF2468AC.toInt())))
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.width(360.dp).testTag("theme")) {
                    ThemeColorControls(palette, 0, false, seedColorScheme(blue, false, 0), { palette = it }, {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription("Blue").performTouchInput { longClick() }
        assertEquals(blue, palette.selectedColor)
        compose.onNodeWithContentDescription("Delete Blue").assertIsDisplayed()
        screenshot("theme", "delete-color")
        compose.onNodeWithContentDescription("Delete Blue").performClick()
        compose.onNodeWithContentDescription("Blue").assertDoesNotExist()
        assertEquals(0, palette.source)
        compose.onNodeWithContentDescription("Restore presets").performClick()
        compose.onNodeWithContentDescription("Blue").assertIsDisplayed()
        compose.onNodeWithContentDescription("Custom #2468AC").assertIsDisplayed()
        assertEquals(0, palette.source)
        compose.onNodeWithContentDescription("Restore presets").assertDoesNotExist()
    }

    @Test
    fun multipleCustomColorsKeepLegacySelectionAndCanBeRemovedIndividually() {
        val original = 0xFF2468AC.toInt()
        val next = 0xFFEF7234.toInt()
        val migrated = loadThemePalette(2, themePresetColors.first().first, original, null, "")
        assertEquals(original, migrated.selectedColor)
        assertEquals(listOf(original), migrated.customColors)
        var palette by mutableStateOf(migrated)
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.width(360.dp)) {
                    ThemeColorControls(palette, 2, false, seedColorScheme(original, false, 0), { palette = it }, {}, { palette = palette.add(next) })
                }
            }
        }
        compose.onNodeWithContentDescription("Add color").performClick()
        assertEquals(listOf(original, next), palette.customColors)
        assertEquals(next, palette.selectedColor)
        compose.onNodeWithContentDescription("Custom #2468AC").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Delete Custom #2468AC").performClick()
        assertEquals(next, palette.selectedColor)
        assertEquals(listOf(next), decodeThemeColors(encodeThemeColors(palette.customColors)))
        compose.onNodeWithContentDescription("Custom #EF7234").performClick().assertIsSelected()
    }

    @Test
    fun gradientPickerRetainsExactHexAndContinuousTone() {
        var saved = 0xFF006A60.toInt()
        var visible by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (visible) {
                    CustomThemeColorDialog(saved, 0, false, onDismiss = { visible = false }) {
                        saved = it
                        visible = false
                    }
                }
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("12")
        compose.onNodeWithText("OK").assertIsNotEnabled()
        assertEquals(0xFF006A60.toInt(), saved)
        val tone = compose.onNodeWithContentDescription("Tone")
        assertEquals(0, tone.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].steps)
        tone.performSemanticsAction(SemanticsActions.SetProgress) { it(23.4f) }
        assertEquals(23.4f, tone.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0.1f)
        field.performTextReplacement("#2468AC")
        val file = File("build/reports/theme-tooltip-ui/gradient-picker.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("OK").performClick()
        assertEquals(0xFF2468AC.toInt(), saved)
        compose.runOnIdle { visible = true }
        compose.onNode(hasSetTextAction()).performTextReplacement("#ABCDEF")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0xFF2468AC.toInt(), saved)
    }

    private fun screenshot(tag: String, name: String) {
        val file = File("build/reports/theme-tooltip-ui/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
