// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.ui.screen

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.fork.SwipeToDismissBox
import androidx.compose.material3.fork.SwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ehviewer.core.database.model.QuickSearch
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.FastScrollLazyColumn
import com.ehviewer.core.ui.component.IconTooltip
import com.ehviewer.core.ui.util.HapticFeedbackType
import com.ehviewer.core.ui.util.rememberHapticFeedback
import com.ehviewer.core.ui.util.thenIf
import com.ehviewer.core.util.launch
import com.ehviewer.core.util.logcat
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhTagDatabase
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.search.CombinedSearchSource
import com.hippo.ehviewer.search.SearchExpression
import com.hippo.ehviewer.search.SearchName
import com.hippo.ehviewer.search.SearchPlacement
import com.hippo.ehviewer.search.SearchTerm
import com.hippo.ehviewer.search.TagFavorites
import com.hippo.ehviewer.search.TagIdentity
import com.hippo.ehviewer.search.TagMatch
import com.hippo.ehviewer.search.automaticallyNamed
import com.hippo.ehviewer.ui.search.TagCatalogPane
import com.hippo.ehviewer.ui.search.TagLibraryMenu
import com.hippo.ehviewer.ui.search.describe
import com.hippo.ehviewer.ui.search.displayBaseName
import com.hippo.ehviewer.ui.search.expressionForDisplay
import com.hippo.ehviewer.ui.search.searchParametersLabel
import com.hippo.ehviewer.ui.tools.DialogState
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.ui.tools.awaitSelectItem
import com.hippo.ehviewer.util.addTextToClipboard
import kotlinx.coroutines.CoroutineScope
import moe.tarsin.coroutines.runSuspendCatching
import moe.tarsin.snackbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
context(_: Context, _: CoroutineScope, _: DialogState, _: SnackbarHostState)
internal fun CombinedSearchSheet(
    current: () -> CombinedSearchSource,
    onOpenBuilder: (CombinedSearchSource) -> Unit,
    editingSource: (QuickSearch) -> CombinedSearchSource,
    onRun: (QuickSearch) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val focus by combinedSearchFocus.collectAsState()
    LaunchedEffect(focus) {
        if (focus != null) tab = 0
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.combined_search)) },
            colors = topBarOnDrawerColor(),
            windowInsets = WindowInsets(),
            actions = {
                if (tab == 0) {
                    val help = stringResource(R.string.tooltip_help)
                    val add = stringResource(R.string.combined_new)
                    val currentLabel = stringResource(R.string.combined_start_current)
                    val blank = stringResource(R.string.combined_start_blank)
                    IconTooltip(help) {
                        IconButton(
                            onClick = {
                                launch {
                                    awaitConfirmationOrCancel(title = R.string.combined_search, showCancelButton = false) {
                                        Text(stringResource(R.string.combined_help))
                                    }
                                }
                            },
                            shapes = IconButtonDefaults.shapes(),
                        ) { Icon(Icons.AutoMirrored.Default.Help, contentDescription = help) }
                    }
                    IconTooltip(add) {
                        IconButton(
                            onClick = {
                                launch {
                                    val source = current()
                                    val choice = awaitSelectItem(listOf(currentLabel, blank), title = R.string.combined_new)
                                    val mode = if (source.filters.mode == ListUrlBuilder.MODE_SUBSCRIPTION) source.filters.mode else ListUrlBuilder.MODE_NORMAL
                                    onOpenBuilder(if (choice == 0) source else CombinedSearchSource(ListUrlBuilder(mode)))
                                }
                            },
                            shapes = IconButtonDefaults.shapes(),
                        ) { Icon(Icons.Default.Add, contentDescription = add) }
                    }
                } else {
                    TagLibraryMenu()
                }
            },
        )
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.combined_saved)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.combined_favorite_tags)) })
        }
        if (tab == 0) {
            SavedCombinations(
                focus = focus,
                onEdit = { onOpenBuilder(editingSource(it)) },
                onRun = onRun,
            )
        } else {
            FavoriteTagsPanel { expression ->
                val source = current()
                val mode = if (source.filters.mode == ListUrlBuilder.MODE_SUBSCRIPTION) source.filters.mode else ListUrlBuilder.MODE_NORMAL
                onOpenBuilder(CombinedSearchSource(ListUrlBuilder(mode = mode, mKeyword = expression.text)))
            }
        }
    }
}

@Composable
context(_: Context, _: CoroutineScope, _: DialogState, _: SnackbarHostState)
private fun SavedCombinations(focus: Long?, onEdit: (QuickSearch) -> Unit, onRun: (QuickSearch) -> Unit) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val savedRows by EhDB.quickSearches.collectAsState(null)
    val items = remember { mutableStateListOf<QuickSearch>() }
    var dragging by remember { mutableStateOf(false) }
    var highlighted by remember { mutableStateOf<Long?>(null) }
    val animateItems by Settings.animateItems.collectAsState()
    val haptic = rememberHapticFeedback()
    val dialog = contextOf<DialogState>()
    val positionalThreshold = SwipeToDismissBoxDefaults.positionalThreshold
    val translateSetting by Settings.showTagTranslations.collectAsState()
    val translate = translateSetting && EhTagDatabase.translatable
    val chinese = LocalConfiguration.current.locales[0].language == "zh"
    LaunchedEffect(savedRows, dragging) {
        if (!dragging) {
            items.clear()
            items.addAll(savedRows.orEmpty())
        }
    }
    LaunchedEffect(focus, savedRows) {
        if (focus != null) {
            val index = savedRows.orEmpty().indexOfFirst { it.id == focus }
            if (index >= 0) {
                listState.requestScrollToItem(index)
                highlighted = focus
                combinedSearchFocus.compareAndSet(focus, null)
            }
        }
    }
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index in items.indices && to.index in items.indices) {
            items.add(to.index, items.removeAt(from.index))
            haptic.performHapticFeedback(HapticFeedbackType.MOVE)
        }
    }

    if (savedRows?.isEmpty() == true) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.combined_saved_empty))
        }
    }
    FastScrollLazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        state = listState,
        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Bottom).asPaddingValues(),
    ) {
        itemsIndexed(items, key = { _, item -> requireNotNull(item.id) }) { _, item ->
            ReorderableItem(reorder, requireNotNull(item.id), animateItemModifier = Modifier.thenIf(animateItems) { animateItem() }) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 1.dp, label = "Search order")
                val dismiss = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold) }
                SwipeToDismissBox(
                    state = dismiss,
                    backgroundContent = {},
                    enableDismissFromStartToEnd = false,
                    onDismiss = {
                        dialog.runCatching {
                            awaitConfirmationOrCancel(confirmText = R.string.delete) {
                                Text(stringResource(R.string.combined_delete, item.displayBaseName(context)))
                            }
                        }.onSuccess {
                            runSuspendCatching { EhDB.deleteQuickSearch(item) }.onFailure {
                                logcat(it)
                                dismiss.reset()
                            }
                        }.onFailure {
                            dismiss.reset()
                        }
                    },
                ) {
                    val expression = item.expressionForDisplay()
                    val description = SearchExpression.parse(expression).describe(context, translate)
                    val primary = if (item.automaticallyNamed) {
                        description.ifBlank { item.displayBaseName(context) }
                    } else {
                        item.displayBaseName(context)
                    }
                    var expanded by remember { mutableStateOf(false) }
                    ListItem(
                        modifier = Modifier.clip(CardDefaults.shape).clickable { onRun(item) },
                        shadowElevation = elevation,
                        headlineContent = { Text(primary, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                if (!item.automaticallyNamed && description.isNotBlank() && description != primary) {
                                    Text(description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (expression.isNotBlank() && expression != primary && (chinese || expression != description || !item.automaticallyNamed)) {
                                    Text(expression, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                                val parameters = searchParametersLabel(item)
                                if (parameters.isNotBlank()) Text(parameters, style = MaterialTheme.typography.bodySmall)
                                SearchName.of(item).afterGid?.let {
                                    Text(stringResource(R.string.combined_resume_summary, it), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        trailingContent = {
                            Row {
                                Box {
                                    val more = stringResource(R.string.combined_actions)
                                    IconTooltip(more) {
                                        IconButton(onClick = { expanded = true }, shapes = IconButtonDefaults.shapes()) {
                                            Icon(Icons.Filled.MoreVert, contentDescription = more)
                                        }
                                    }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.combined_edit)) },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                            onClick = {
                                                expanded = false
                                                onEdit(item)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.combined_copy_expression)) },
                                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                            onClick = {
                                                expanded = false
                                                addTextToClipboard(expression)
                                            },
                                        )
                                    }
                                }
                                val label = stringResource(R.string.tooltip_reorder)
                                IconTooltip(label) {
                                    IconButton(
                                        onClick = {},
                                        shapes = IconButtonDefaults.shapes(),
                                        modifier = Modifier.draggableHandle(
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.START)
                                                dragging = true
                                            },
                                            onDragStopped = {
                                                haptic.performHapticFeedback(HapticFeedbackType.END)
                                                val changed = items.mapIndexedNotNull { index, entry ->
                                                    entry.takeUnless { it.position == index }?.copy(position = index)
                                                }
                                                launch {
                                                    try {
                                                        if (changed.isNotEmpty()) EhDB.updateQuickSearch(changed)
                                                    } finally {
                                                        dragging = false
                                                    }
                                                }
                                            },
                                        ),
                                    ) { Icon(Icons.Default.Reorder, contentDescription = label) }
                                }
                            }
                        },
                        colors = listItemOnDrawerColor(highlighted == item.id),
                    )
                }
            }
        }
    }
}

@Composable
context(_: CoroutineScope, _: DialogState, _: SnackbarHostState)
private fun FavoriteTagsPanel(onUse: (SearchExpression) -> Unit) {
    val favorites by TagFavorites.entries.collectAsState(emptyList())
    var selectedKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selected = remember(selectedKeys) { selectedKeys.mapNotNull(TagIdentity::from).toSet() }
    val favoriteKeys = favorites.mapTo(hashSetOf()) { it.tag }
    Column(Modifier.fillMaxSize()) {
        if (selected.isNotEmpty()) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = {
                        onUse(SearchExpression(selected.map { SearchTerm.forTag(it, SearchPlacement.Required, TagMatch.Exact) }))
                    },
                    enabled = selected.size <= 15,
                ) { Text(stringResource(R.string.combined_use_selected, selected.size)) }
                if (selected.all { it in favoriteKeys }) {
                    TextButton(
                        onClick = {
                            launch {
                                awaitConfirmationOrCancel(confirmText = R.string.combined_unfavorite_tag) {
                                    Text(stringResource(R.string.combined_remove_favorites_confirm, selected.size))
                                }
                                TagFavorites.remove(selected)
                                selectedKeys = emptyList()
                            }
                        },
                    ) { Text(stringResource(R.string.combined_unfavorite_tag)) }
                }
                TextButton(onClick = { selectedKeys = emptyList() }) {
                    Text(stringResource(R.string.read_cache_selection_cancel))
                }
            }
            if (selected.size > 15) {
                Text(stringResource(R.string.combined_error_selected), modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
            }
        }
        TagCatalogPane(
            favorites = favorites,
            selected = selected,
            onChoose = { tag ->
                val key = tag.qualifiedName
                selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}
