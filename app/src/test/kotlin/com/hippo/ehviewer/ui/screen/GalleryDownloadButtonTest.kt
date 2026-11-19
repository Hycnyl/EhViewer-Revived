package com.hippo.ehviewer.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import com.ehviewer.core.database.model.DownloadInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class GalleryDownloadButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tapsControlDownloadsAndOnlyLongPressRequestsRemoval() {
        var state by mutableIntStateOf(DownloadInfo.STATE_INVALID)
        val actions = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                GalleryDownloadButton(
                    state = state,
                    canAdd = true,
                    onAdd = { actions += "add" },
                    onResume = { actions += "resume" },
                    onPause = { actions += "pause" },
                    onRead = { actions += "read" },
                    onRemove = { actions += "remove" },
                )
            }
        }
        val button = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val hasLongClick = SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)
        button.assert(!hasLongClick)
        button.performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("add"), actions) }

        val states = listOf(
            DownloadInfo.STATE_NONE to "resume",
            DownloadInfo.STATE_WAIT to "pause",
            DownloadInfo.STATE_DOWNLOAD to "pause",
            DownloadInfo.STATE_FAILED to "resume",
            DownloadInfo.STATE_FINISH to "read",
        )
        for ((downloadState, action) in states) {
            compose.runOnIdle {
                state = downloadState
                actions.clear()
            }
            button.assert(hasLongClick)
            button.performTouchInput { click() }
            button.performTouchInput { longClick() }
            compose.runOnIdle { assertEquals("Download state $downloadState", listOf(action, "remove"), actions) }
        }
    }
}
