// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.ui.settings

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.FastScrollLazyColumn
import com.ehviewer.core.ui.component.FastScrollLazyVerticalGrid
import com.ehviewer.core.ui.component.IconTooltip
import com.ehviewer.core.util.launch
import com.ehviewer.core.util.logcat
import com.hippo.ehviewer.EhApplication.Companion.imageCache
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.coil.ReadCachedGallery
import com.hippo.ehviewer.coil.clearGalleryImages
import com.hippo.ehviewer.coil.listReadCachedGalleries
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.main.DEFAULT_RATIO
import com.hippo.ehviewer.ui.main.GalleryViewToggle
import com.hippo.ehviewer.ui.main.NavigationIcon
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import moe.tarsin.coroutines.runSuspendCatching

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.ReadCacheGalleryScreen(navigator: DestinationsNavigator) = Screen(navigator) {
    var galleryList by remember { mutableStateOf<List<ReadCachedGallery>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSaveable { mutableStateOf(LongArray(0)) }
    val scope = rememberCoroutineScope()
    val selectedIds = remember(selection) { selection.toSet() }
    val galleries = galleryList.orEmpty()
    val chosen = galleries.filter { it.gid in selectedIds }
    LaunchedEffect(refresh) {
        loadFailed = false
        galleryList = null
        runSuspendCatching { listReadCachedGalleries() }.onSuccess { result ->
            galleryList = result
            val existing = result.mapTo(hashSetOf()) { it.gid }
            selection = selection.filter { it in existing }.toLongArray()
        }.onFailure {
            logcat(it)
            loadFailed = true
        }
    }

    fun clearSelected() {
        if (busy || chosen.isEmpty()) return
        busy = true
        launch {
            try {
                awaitConfirmationOrCancel(confirmText = R.string.read_cache_cleanup_action, title = R.string.read_cache_cleanup_by_gallery) {
                    Text(stringResource(R.string.read_cache_gallery_confirm, chosen.size))
                }
                clearReadingCache(scope) {
                    chosen.sumOf { gallery -> imageCache.clearGalleryImages(gallery.gid, 0 until gallery.pageLimit) }
                }
                selection = LongArray(0)
                refresh++
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.read_cache_cleanup_by_gallery)) },
                navigationIcon = { NavigationIcon() },
                actions = {
                    GalleryViewToggle(grid, onToggle = { grid = it })
                    if (galleries.isNotEmpty()) {
                        val selectAll = stringResource(R.string.tooltip_select_all)
                        IconTooltip(selectAll) {
                            IconButton(
                                enabled = !busy && chosen.size < galleries.size,
                                onClick = { selection = galleries.map { it.gid }.toLongArray() },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = selectAll)
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (galleries.isNotEmpty()) {
                BottomAppBar {
                    Text(
                        stringResource(R.string.read_cache_gallery_selected, chosen.size),
                        modifier = Modifier.weight(1f).padding(start = 16.dp, end = 8.dp),
                    )
                    Button(
                        onClick = ::clearSelected,
                        enabled = !busy && chosen.isNotEmpty(),
                        modifier = Modifier.padding(end = 16.dp),
                    ) {
                        Text(stringResource(if (busy) R.string.read_cache_cleanup_running else R.string.read_cache_cleanup_action))
                    }
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when {
            loadFailed -> Box(modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.read_cache_gallery_load_failed))
                    TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.action_retry)) }
                }
            }
            galleryList == null -> Box(modifier, contentAlignment = Alignment.Center) { CircularWavyProgressIndicator() }
            galleries.isEmpty() -> Box(modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.read_cache_gallery_empty))
            }
            else -> ReadCacheGalleryContent(
                galleries = galleries,
                grid = grid,
                selected = selectedIds,
                enabled = !busy,
                onToggle = { gid ->
                    selection = if (gid in selectedIds) selection.filterNot { it == gid }.toLongArray() else selection + gid
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
internal fun ReadCacheGalleryContent(
    galleries: List<ReadCachedGallery>,
    grid: Boolean,
    selected: Set<Long>,
    enabled: Boolean,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (grid) {
        FastScrollLazyVerticalGrid(
            columns = GridCells.Adaptive(144.dp),
            modifier = modifier,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(galleries, key = { it.gid }) {
                ReadCachedGalleryItem(it, grid, it.gid in selected, enabled) { onToggle(it.gid) }
            }
        }
    } else {
        FastScrollLazyColumn(modifier = modifier) {
            items(galleries, key = { it.gid }) {
                ReadCachedGalleryItem(it, grid, it.gid in selected, enabled) { onToggle(it.gid) }
            }
        }
    }
}

@Composable
private fun ReadCachedGalleryItem(gallery: ReadCachedGallery, grid: Boolean, selected: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val info = gallery.info
    val title = if (info != null && (!info.title.isNullOrBlank() || !info.titleJpn.isNullOrBlank())) {
        EhUtils.getSuitableTitle(info)
    } else {
        stringResource(R.string.read_cache_gallery_gid, gallery.gid)
    }
    val details = stringResource(R.string.read_cache_gallery_details, gallery.cachedPages, Formatter.formatShortFileSize(context, gallery.bytes))
    val date = remember(context, gallery.latestWriteMillis) { formatReadCacheTime(context, gallery.latestWriteMillis) }
    val toggle = Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
    if (grid) {
        ElevatedCard(modifier = toggle) {
            Box {
                ReadCacheCover(gallery, Modifier.fillMaxWidth().aspectRatio(DEFAULT_RATIO))
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    enabled = enabled,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        .background(MaterialTheme.colorScheme.surface, ShapeDefaults.Small).padding(4.dp),
                )
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(details, style = MaterialTheme.typography.labelMedium)
                Text(date, style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        ListItem(
            headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Column {
                    Text(details)
                    Text(date)
                }
            },
            leadingContent = { ReadCacheCover(gallery, Modifier.width(52.dp).height(76.dp).clip(ShapeDefaults.Small)) },
            trailingContent = { Checkbox(checked = selected, onCheckedChange = null, enabled = enabled) },
            modifier = toggle,
        )
    }
}

@Composable
private fun ReadCacheCover(gallery: ReadCachedGallery, modifier: Modifier) {
    val context = LocalContext.current
    val model = remember(context, gallery.preview, gallery.latestWriteMillis) {
        ImageRequest.Builder(context)
            .data(gallery.preview.toFile())
            .diskCachePolicy(CachePolicy.DISABLED)
            .networkCachePolicy(CachePolicy.DISABLED)
            .memoryCacheKey("${gallery.preview}:${gallery.latestWriteMillis}")
            .build()
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop,
    )
}
