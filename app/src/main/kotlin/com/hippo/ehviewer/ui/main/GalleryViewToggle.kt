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

@Composable
internal fun GalleryViewToggle(gridView: Boolean, onToggle: (Boolean) -> Unit) {
    IconButton(onClick = { onToggle(!gridView) }, shapes = IconButtonDefaults.shapes()) {
        Icon(
            imageVector = if (gridView) Icons.AutoMirrored.Default.ViewList else Icons.Default.GridView,
            contentDescription = stringResource(if (gridView) R.string.settings_eh_list_mode_detail else R.string.settings_eh_list_mode_thumb),
        )
    }
}
