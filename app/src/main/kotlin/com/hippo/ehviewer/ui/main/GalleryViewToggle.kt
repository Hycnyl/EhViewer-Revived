package com.hippo.ehviewer.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.IconTooltip

@Composable
internal fun GalleryViewToggle(gridView: Boolean, onToggle: (Boolean) -> Unit) {
    IconTooltip(stringResource(if (gridView) R.string.tooltip_list_view else R.string.tooltip_cover_view)) {
        IconButton(onClick = { onToggle(!gridView) }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                imageVector = if (gridView) Icons.AutoMirrored.Default.ViewList else Icons.Default.GridView,
                contentDescription = stringResource(if (gridView) R.string.tooltip_list_view else R.string.tooltip_cover_view),
            )
        }
    }
}
