package com.hippo.ehviewer.ui.settings

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ehviewer.core.ui.component.IconTooltip
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
    fun lightColorChoicesAndLivePreview() = colors(false)

    @Test
    fun darkLargeTextColors() = colors(true)

    private fun colors(dark: Boolean) {
        var source by mutableIntStateOf(1)
        var preset by mutableIntStateOf(themePresetColors.first().first)
        var style by mutableIntStateOf(0)
        var edits = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, if (dark) 1.3f else 1f)) {
                val system = seedColorScheme(0xFF6750A4.toInt(), dark, 0)
                MaterialTheme(colorScheme = system) {
                    Surface(Modifier.width(if (dark) 700.dp else 360.dp).testTag("theme")) {
                        ThemeColorControls(source, preset, 0xFF006A60.toInt(), style, dark, system, { source = it }, { preset = it }, { style = it }, { edits++ })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Blue").performClick()
        assertEquals(themePresetColors[1].first, preset)
        compose.onNodeWithText("Tonal Spot").performClick()
        compose.onNodeWithText("Vibrant").performClick()
        assertEquals(1, style)
        screenshot("theme", if (dark) "dark-colors" else "light-colors")
        compose.onNodeWithText("Custom").performClick()
        compose.onNodeWithText("Edit color · #006A60").performClick()
        assertEquals(2, source)
        assertEquals(1, edits)
        compose.onNodeWithText("Dynamic").performClick()
        assertEquals(0, source)
        compose.onNodeWithText("Follow wallpaper colors").assertIsDisplayed()
    }

    @Test
    fun customColorConfirmationAndCancel() {
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
        field.performTextReplacement("#2468AC")
        val dialog = compose.onNode(isDialog())
        val file = File("build/reports/theme-tooltip-ui/custom-picker.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { dialog.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("OK").performClick()
        assertEquals(0xFF2468AC.toInt(), saved)
        compose.runOnIdle { visible = true }
        compose.onNode(hasSetTextAction()).performTextReplacement("#ABCDEF")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0xFF2468AC.toInt(), saved)
    }

    private fun tooltip(onClick: () -> Unit) {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.padding(80.dp)) {
                    IconTooltip("Action") {
                        IconButton(onClick = onClick) { Icon(Icons.Default.Info, contentDescription = "Action") }
                    }
                }
            }
        }
    }

    @Test
    fun nativeLongPressDoesNotClick() {
        var clicks = 0
        compose.mainClock.autoAdvance = false
        tooltip { clicks++ }
        compose.onNodeWithContentDescription("Action").assertHasClickAction().performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Action").assertIsDisplayed()
        assertEquals(0, clicks)
        compose.mainClock.advanceTimeBy(2200)
        compose.onNodeWithText("Action").assertDoesNotExist()
        compose.onNodeWithContentDescription("Action").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun nativeHoverAndExit() {
        var clicks = 0
        compose.mainClock.autoAdvance = false
        tooltip { clicks++ }
        compose.onNodeWithContentDescription("Action").performMouseInput {
            enter(center)
            moveTo(center)
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Action").assertIsDisplayed()
        compose.onNodeWithContentDescription("Action").performMouseInput { exit() }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Action").assertDoesNotExist()
        assertEquals(0, clicks)
    }

    private fun screenshot(tag: String, name: String) {
        val image = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val file = File("build/reports/theme-tooltip-ui/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
