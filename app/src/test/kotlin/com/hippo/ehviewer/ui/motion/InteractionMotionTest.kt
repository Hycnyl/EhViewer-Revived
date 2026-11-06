package com.hippo.ehviewer.ui.motion

import android.app.Application
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerState2
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalSideDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState2
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ehviewer.core.ui.component.rememberElasticOverscrollEffect
import com.hippo.ehviewer.ui.theme.CustomMotionScheme
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InteractionMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var effect: OverscrollEffect

    private fun content() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme(motionScheme = CustomMotionScheme) {
                    effect = rememberElasticOverscrollEffect()
                    Box(Modifier.size(200.dp).background(Color.White).testTag("viewport")) {
                        LazyColumn(state = rememberLazyListState(), overscrollEffect = effect) {
                            items(20) { index ->
                                Box(Modifier.fillMaxWidth().height(40.dp).background(if (index == 0) Color.Red else Color.Blue))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun boundedPullPreservesImageGeometryAndReturnsToRest() {
        content()
        compose.onNodeWithTag("viewport").performTouchInput {
            down(center)
            moveBy(Offset(0f, 500f))
        }
        val band = redBand()
        assertTrue("Edge translation is bounded by 48 dp", band.first() in 1..48)
        assertEquals("No second Stretch may distort content", 40, band.size)
        assertTrue(effect.isInProgress)
        compose.onNodeWithTag("viewport").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        assertEquals(0, redBand().first())
        assertFalse(effect.isInProgress)
    }

    @Test
    fun touchingDuringReturnKeepsPositionInsteadOfJumping() {
        content()
        compose.onNodeWithTag("viewport").performTouchInput {
            down(center)
            moveBy(Offset(0f, 300f))
            up()
        }
        compose.mainClock.advanceTimeBy(64)
        val before = redBand().first()
        assertTrue(before > 0)
        compose.onNodeWithTag("viewport").performTouchInput { down(center) }
        assertTrue("An interrupted spring must keep its current position", abs(redBand().first() - before) <= 1)
        compose.onNodeWithTag("viewport").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        assertEquals(0, redBand().first())
    }

    @Test
    fun mouseClickDuringReturnDoesNotStrandContent() {
        content()
        compose.onNodeWithTag("viewport").performTouchInput {
            down(center)
            moveBy(Offset(0f, 300f))
            up()
        }
        compose.mainClock.advanceTimeBy(64)
        assertTrue(redBand().first() > 0)
        compose.onNodeWithTag("viewport").performMouseInput {
            moveTo(center)
            press()
            release()
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        assertEquals(0, redBand().first())
        assertFalse(effect.isInProgress)
    }

    @Test
    fun normalScrollingDoesNotStartAnEdgeAnimation() {
        content()
        compose.onNodeWithTag("viewport").performTouchInput {
            down(center)
            moveBy(Offset(0f, -80f))
        }
        assertFalse("Consumed content scrolling must not be translated", effect.isInProgress)
        compose.onNodeWithTag("viewport").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        assertFalse(effect.isInProgress)
    }

    @Test
    fun sideSheetSettlesInsideItsAnchors() {
        compose.mainClock.autoAdvance = false
        lateinit var state: DrawerState2
        lateinit var scope: CoroutineScope
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme(motionScheme = CustomMotionScheme) {
                    state = rememberDrawerState2(DrawerValue.Closed)
                    scope = rememberCoroutineScope()
                    Box(Modifier.size(240.dp)) {
                        ModalSideDrawer(
                            drawerState = state,
                            drawerContent = { Text("Side sheet", Modifier.width(120.dp).height(200.dp).testTag("sheet")) },
                        ) { Box(Modifier.size(240.dp)) }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { scope.launch { state.open() } }
        val positions = mutableListOf<Float>()
        repeat(20) {
            compose.mainClock.advanceTimeBy(32)
            compose.waitForIdle()
            positions += state.currentOffset
        }
        assertTrue("The panel must settle monotonically without overshoot", positions.zipWithNext().all { (a, b) -> b <= a + 0.5f })
        assertTrue(positions.all { it >= -0.5f && it <= 120.5f })
        assertEquals(0f, positions.last(), 0.5f)
        compose.runOnIdle { scope.launch { state.close() } }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(120f, state.currentOffset, 0.5f)
    }

    private fun redBand(): List<Int> {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val image = compose.onNodeWithTag("viewport").captureToImage().asAndroidBitmap()
        return (0 until image.height).filter { image.getPixel(image.width / 2, it) == android.graphics.Color.RED }
    }
}
