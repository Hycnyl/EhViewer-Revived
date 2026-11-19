package com.hippo.ehviewer.ui.screen

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.i18n.R

@Composable
internal fun GalleryDownloadButton(
    state: Int,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onRead: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (status, action, onClick) = when (state) {
        DownloadInfo.STATE_INVALID -> Triple(R.string.download, R.string.download, onAdd)
        DownloadInfo.STATE_NONE -> Triple(R.string.download_state_none, R.string.tooltip_start_download, onResume)
        DownloadInfo.STATE_WAIT -> Triple(R.string.download_state_wait, R.string.tooltip_pause_download, onPause)
        DownloadInfo.STATE_DOWNLOAD -> Triple(R.string.download_state_downloading, R.string.tooltip_pause_download, onPause)
        DownloadInfo.STATE_FAILED -> Triple(R.string.download_state_failed, R.string.tooltip_start_download, onResume)
        DownloadInfo.STATE_FINISH -> Triple(R.string.download_state_downloaded, R.string.read, onRead)
        else -> error("Unknown download state $state")
    }
    val existing = state != DownloadInfo.STATE_INVALID
    val enabled = existing || canAdd
    val colors = ButtonDefaults.filledTonalButtonColors()
    Surface(
        modifier = modifier.minimumInteractiveComponentSize(),
        shape = ButtonDefaults.shape,
        color = if (enabled) colors.containerColor else colors.disabledContainerColor,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
    ) {
        Box(
            modifier = Modifier
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = stringResource(action),
                    onLongClickLabel = if (existing) stringResource(R.string.download_remove_dialog_title) else null,
                    onLongClick = if (existing) onRemove else null,
                    onClick = onClick,
                )
                .defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = ButtonDefaults.MinHeight)
                .padding(ButtonDefaults.ContentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(status), style = MaterialTheme.typography.labelLarge, overflow = TextOverflow.Ellipsis, maxLines = 1)
        }
    }
}
