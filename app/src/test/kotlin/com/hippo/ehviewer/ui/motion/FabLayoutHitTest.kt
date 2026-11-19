package com.hippo.ehviewer.ui.motion

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.ehviewer.core.ui.component.FabLayout
import com.ehviewer.core.ui.util.LocalSnackBarFabPadding
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class FabLayoutHitTest {
    @get:Rule val compose = createComposeRule()
    private val lastPage = CountDownLatch(1)
    private var expanded by mutableStateOf(false)

    private fun showMenu() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalSnackBarFabPadding provides remember { mutableStateOf(0.dp) }) {
                MaterialTheme {
                    Box(Modifier.size(320.dp, 480.dp).testTag("page")) {
                        with(scope) {
                            FabLayout(
                                hidden = false,
                                expanded = expanded,
                                onExpandChanged = { expanded = it },
                                expandLabel = "More actions",
                                collapseLabel = "Close actions",
                                autoCancel = true,
                            ) {
                                onClick(Icons.Default.Close, "Refresh") {}
                                onClick(Icons.Default.Close, "Last page") { lastPage.countDown() }
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
    }

    private fun bounds(label: String): Rect = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot

    private fun tap(pointInRoot: Offset) {
        val page = compose.onNodeWithTag("page")
        val origin = page.fetchSemanticsNode().boundsInRoot.topLeft
        page.performTouchInput { click(pointInRoot - origin) }
    }

    @Test
    fun tappingCollapsedPlusCenterOpensOnlyTheMenu() {
        showMenu()
        tap(bounds("More actions").center)
        compose.mainClock.advanceTimeBy(400)
        assertTrue(expanded)
        assertFalse(lastPage.await(200, TimeUnit.MILLISECONDS))
        val primary = bounds("Close actions")
        tap(primary.center)
        compose.mainClock.advanceTimeBy(144)
        tap(Offset(primary.center.x, primary.top + 2f))
        compose.mainClock.advanceTimeBy(400)
        assertTrue("Tapping inside the plus during collapse must reopen it", expanded)
        assertFalse(lastPage.await(200, TimeUnit.MILLISECONDS))
        tap(bounds("Last page").center)
        assertTrue(lastPage.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun restoredExpandedMenuKeepsLastPageAboveToggle() {
        expanded = true
        showMenu()
        val last = bounds("Last page")
        val toggle = bounds("Close actions")
        assertTrue("Restored Last page overlaps the toggle: last=$last, toggle=$toggle", last.bottom <= toggle.top)
        tap(last.center)
        assertTrue(lastPage.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun collapsedMenuExposesNoLastPageAction() {
        showMenu()
        compose.onNodeWithContentDescription("Last page").assertDoesNotExist()
    }

    @Test
    fun tapNearPlusWhileClosingCannotTriggerLastPage() {
        showMenu()
        val primary = bounds("More actions")
        tap(primary.center)
        compose.mainClock.advanceTimeBy(400)
        assertTrue(expanded)
        tap(primary.center)
        compose.mainClock.advanceTimeBy(144)
        assertFalse(expanded)
        // During collapse the last item travels across the top edge of the primary button.
        tap(Offset(primary.center.x, primary.top - 2f))
        assertFalse("A touch 2 px above the plus during collapse triggered Last page", lastPage.await(500, TimeUnit.MILLISECONDS))
    }
}
