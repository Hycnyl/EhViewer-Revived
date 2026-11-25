// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.ui.settings

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.CrystalCard
import com.ehviewer.core.ui.component.ElevatedCard
import com.ehviewer.core.ui.component.FastScrollLazyColumn
import com.ehviewer.core.ui.component.FastScrollLazyVerticalStaggeredGrid
import com.ehviewer.core.ui.component.IconTooltip
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
import com.hippo.ehviewer.ui.screen.CheckableItem
import com.hippo.ehviewer.ui.screen.collectListThumbSizeAsState
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import moe.tarsin.coroutines.runSuspendCatching

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.ReadCacheGalleryScreen(navigator: DestinationsNavigator) = Screen(navigator) {
    var galleries by remember { mutableStateOf<List<ReadCachedGallery>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listThumbHeight by collectListThumbSizeAsState()
    LaunchedEffect(refresh) {
        loadFailed = false
        galleries = null
        runSuspendCatching { listReadCachedGalleries() }.onSuccess {
            galleries = it
        }.onFailure {
            logcat(it)
            loadFailed = true
        }
    }

    ReadCacheGalleryLayout(
        galleries = galleries,
        listThumbHeight = listThumbHeight,
        loadFailed = loadFailed,
        onRetry = { refresh++ },
        navigationIcon = { NavigationIcon() },
        onClear = { chosen ->
            awaitConfirmationOrCancel(confirmText = R.string.read_cache_cleanup_action, title = R.string.read_cache_cleanup_by_gallery) {
                Text(stringResource(R.string.read_cache_gallery_confirm, chosen.size))
            }
            clearReadingCache(scope) {
                chosen.sumOf { gallery -> imageCache.clearGalleryImages(gallery.gid, 0 until gallery.pageLimit) }
            }
            refresh++
        },
    )
}

@Composable
internal fun ReadCacheGalleryLayout(
    galleries: List<ReadCachedGallery>?,
    listThumbHeight: Dp,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    navigationIcon: @Composable () -> Unit,
    onClear: suspend (List<ReadCachedGallery>) -> Unit,
) {
    var grid by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSaveable { mutableStateOf(LongArray(0)) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val galleryIds = remember(galleries) { galleries.orEmpty().mapTo(linkedSetOf()) { it.gid } }
    val selectedIds = remember(selection, galleryIds) { selection.toSet().intersect(galleryIds) }
    val selecting = selectedIds.isNotEmpty()

    fun clearSelected() {
        if (busy || !selecting) return
        val chosen = galleries.orEmpty().filter { it.gid in selectedIds }
        busy = true
        scope.launch {
            try {
                onClear(chosen)
                selection = LongArray(0)
            } finally {
                busy = false
            }
        }
    }

    BackHandler(enabled = selecting && !busy) { selection = LongArray(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selecting) {
                            stringResource(R.string.read_cache_gallery_selected, selectedIds.size)
                        } else {
                            stringResource(R.string.read_cache_cleanup_by_gallery)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = navigationIcon,
                actions = {
                    GalleryViewToggle(grid, onToggle = { grid = it })
                    if (galleryIds.isNotEmpty()) {
                        GallerySelectionMenu(
                            enabled = !busy,
                            canSelectAll = selectedIds.size < galleryIds.size,
                            canCancel = selecting,
                            onSelectAll = { selection = galleryIds.toLongArray() },
                            onInvert = { selection = (galleryIds - selectedIds).toLongArray() },
                            onCancel = { selection = LongArray(0) },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (selecting) {
                val action = stringResource(if (busy) R.string.read_cache_cleanup_running else R.string.read_cache_cleanup_action)
                ExtendedFloatingActionButton(
                    onClick = ::clearSelected,
                    icon = { Icon(Icons.Default.Delete, contentDescription = action) },
                    text = { Text(action) },
                )
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when {
            loadFailed -> Box(modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.read_cache_gallery_load_failed))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
            }
            galleries == null -> Box(modifier, contentAlignment = Alignment.Center) { CircularWavyProgressIndicator() }
            galleries.isEmpty() -> Box(modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.read_cache_gallery_empty))
            }
            else -> ReadCacheGalleryContent(
                galleries = galleries,
                listThumbHeight = listThumbHeight,
                grid = grid,
                selected = selectedIds,
                onToggle = { gid ->
                    if (!busy && selecting) {
                        selection = (if (gid in selectedIds) selectedIds - gid else selectedIds + gid).toLongArray()
                    }
                },
                onSelect = { gid ->
                    if (!busy) selection = (selectedIds + gid).toLongArray()
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun GallerySelectionMenu(
    enabled: Boolean,
    canSelectAll: Boolean,
    canCancel: Boolean,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onCancel: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(R.string.read_cache_selection_menu)
    Box {
        IconTooltip(label) {
            IconButton(onClick = { expanded = true }, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Default.Checklist, contentDescription = label)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tooltip_select_all)) },
                leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null) },
                enabled = canSelectAll,
                onClick = {
                    expanded = false
                    onSelectAll()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.read_cache_selection_invert)) },
                leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                onClick = {
                    expanded = false
                    onInvert()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.read_cache_selection_cancel)) },
                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                enabled = canCancel,
                onClick = {
                    expanded = false
                    onCancel()
                },
            )
        }
    }
}

@Composable
private fun ReadCacheGalleryContent(
    galleries: List<ReadCachedGallery>,
    listThumbHeight: Dp,
    grid: Boolean,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontalMargin = dimensionResource(com.hippo.ehviewer.R.dimen.gallery_list_margin_h)
    val verticalMargin = dimensionResource(com.hippo.ehviewer.R.dimen.gallery_list_margin_v)
    val contentPadding = PaddingValues(
        start = horizontalMargin,
        end = horizontalMargin,
        top = verticalMargin,
        bottom = verticalMargin + if (selected.isNotEmpty()) 72.dp else 0.dp,
    )
    if (grid) {
        val spacing = dimensionResource(com.hippo.ehviewer.R.dimen.gallery_grid_interval)
        val minTileWidth = 96.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        FastScrollLazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(minTileWidth),
            modifier = modifier,
            contentPadding = contentPadding,
            verticalItemSpacing = spacing,
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items(galleries, key = { it.gid }) { gallery ->
                ReadCachedGalleryItem(
                    gallery = gallery,
                    listThumbHeight = listThumbHeight,
                    grid = true,
                    checked = gallery.gid in selected,
                    onClick = { onToggle(gallery.gid) },
                    onLongClick = { onSelect(gallery.gid) },
                )
            }
        }
    } else {
        FastScrollLazyColumn(
            modifier = modifier,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(dimensionResource(com.hippo.ehviewer.R.dimen.gallery_list_interval)),
        ) {
            items(galleries, key = { it.gid }) { gallery ->
                ReadCachedGalleryItem(
                    gallery = gallery,
                    listThumbHeight = listThumbHeight,
                    grid = false,
                    checked = gallery.gid in selected,
                    onClick = { onToggle(gallery.gid) },
                    onLongClick = { onSelect(gallery.gid) },
                )
            }
        }
    }
}

@Composable
private fun ReadCachedGalleryItem(
    gallery: ReadCachedGallery,
    listThumbHeight: Dp,
    grid: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val info = gallery.info
    val title = if (info != null && (!info.title.isNullOrBlank() || !info.titleJpn.isNullOrBlank())) {
        EhUtils.getSuitableTitle(info)
    } else {
        stringResource(R.string.read_cache_gallery_gid, gallery.gid)
    }
    val details = stringResource(R.string.read_cache_gallery_details, gallery.cachedPages, Formatter.formatShortFileSize(context, gallery.bytes))
    val date = remember(context, gallery.latestWriteMillis) { formatReadCacheTime(context, gallery.latestWriteMillis) }
    val selectionSemantics = Modifier.semantics {
        selected = checked
        if (grid) contentDescription = title
    }
    CheckableItem(checked, checkAlignment = if (grid) Alignment.BottomEnd else Alignment.TopEnd) { interactionSource ->
        if (grid) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = interactionSource,
            ) {
                ReadCacheCover(gallery, Modifier.fillMaxWidth().aspectRatio(DEFAULT_RATIO).then(selectionSemantics))
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(details, style = MaterialTheme.typography.labelMedium)
                    Text(date, modifier = Modifier.heightIn(min = 20.dp).padding(end = 22.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            CrystalCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = interactionSource,
            ) {
                Row(modifier = selectionSemantics, verticalAlignment = Alignment.CenterVertically) {
                    ReadCacheCover(gallery, Modifier.height(listThumbHeight).aspectRatio(DEFAULT_RATIO).clip(ShapeDefaults.Small))
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 8.dp, top = 4.dp, end = 28.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(details, style = MaterialTheme.typography.labelMedium)
                        Text(date, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
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
