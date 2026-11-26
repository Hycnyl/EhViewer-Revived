// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.ui.search

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.FastScrollLazyColumn
import com.ehviewer.core.ui.component.IconTooltip
import com.ehviewer.core.util.logcat
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhTagDatabase
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.search.FavoriteTag
import com.hippo.ehviewer.search.FavoriteTagFile
import com.hippo.ehviewer.search.TagFavorites
import com.hippo.ehviewer.search.TagIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.tarsin.coroutines.runSuspendCatching
import moe.tarsin.snackbar
import okio.buffer
import okio.source

context(_: SnackbarHostState)
internal suspend fun toggleFavoriteTag(context: Context, tag: TagIdentity) {
    val message = runSuspendCatching {
        if (TagFavorites.toggle(tag)) R.string.combined_tag_added else R.string.combined_tag_removed
    }.getOrElse {
        logcat(it)
        R.string.combined_tag_failed
    }
    snackbar(context.getString(message))
}

@Composable
context(_: SnackbarHostState)
internal fun TagCatalogPane(
    favorites: List<FavoriteTag>,
    selected: Set<TagIdentity>,
    onChoose: (TagIdentity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val translateSetting by Settings.showTagTranslations.collectAsState()
    val translate = translateSetting && with(context) { EhTagDatabase.translatable }
    val revision = EhTagDatabase.revision
    var query by rememberSaveable { mutableStateOf("") }
    var catalogLimit by remember(query) { mutableIntStateOf(80) }
    val favoriteKeys = remember(favorites) { favorites.mapTo(hashSetOf()) { it.tag } }
    val groups = remember(favorites) { favorites.groupBy { it.tag.namespace }.toSortedMap() }
    val matches by produceState<SearchMatches?>(null, query, favorites, revision, catalogLimit) {
        value = null
        if (query.isNotBlank()) {
            delay(150)
            value = withContext(Dispatchers.Default) {
                val filter = EhTagDatabase.catalogQuery(query)
                val saved = favorites.asSequence().map { it.tag }.filter { filter.matches(it, EhTagDatabase.translation(it)) }.toList()
                val catalog = EhTagDatabase.catalogMatches(filter, catalogLimit + saved.size + 1).filterNot { it in favoriteKeys }
                SearchMatches(saved, catalog.take(catalogLimit), catalog.size > catalogLimit)
            }
        }
    }
    var loadingDictionary by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.combined_find_tags)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            singleLine = true,
        )
        FastScrollLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Bottom).asPaddingValues(),
        ) {
            if (!EhTagDatabase.initialized) {
                item("dictionary") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(stringResource(R.string.combined_catalog_unavailable), style = MaterialTheme.typography.bodySmall)
                        TextButton(
                            onClick = {
                                scope.launch {
                                    loadingDictionary = true
                                    try {
                                        EhTagDatabase.launchUpdate().join()
                                        if (!EhTagDatabase.initialized) snackbar(context.getString(R.string.combined_catalog_unavailable))
                                    } finally {
                                        loadingDictionary = false
                                    }
                                }
                            },
                            enabled = !loadingDictionary,
                        ) { Text(stringResource(R.string.action_retry)) }
                    }
                }
            }
            if (query.isBlank()) {
                if (groups.isEmpty()) {
                    item("empty") {
                        Text(stringResource(R.string.combined_favorite_empty), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                for ((namespace, tags) in groups) {
                    item("group-$namespace") { TagCatalogHeading(namespaceDisplayName(namespace, translate)) }
                    items(tags, key = { it.tag.qualifiedName }) { favorite ->
                        TagCatalogItem(favorite.tag, true, favorite.tag in selected, translate, onChoose) {
                            scope.launch { toggleFavoriteTag(context, favorite.tag) }
                        }
                    }
                }
            } else {
                val result = matches
                if (result == null) {
                    item("loading") { CircularWavyProgressIndicator(Modifier.padding(16.dp)) }
                } else if (result.saved.isEmpty() && result.catalog.isEmpty()) {
                    item("no-results") { Text(stringResource(R.string.combined_no_tag_results), Modifier.padding(16.dp)) }
                } else {
                    if (result.saved.isNotEmpty()) item("saved") { TagCatalogHeading(stringResource(R.string.combined_favorite_tags)) }
                    items(result.saved, key = TagIdentity::qualifiedName) { tag ->
                        TagCatalogItem(tag, true, tag in selected, translate, onChoose) {
                            scope.launch { toggleFavoriteTag(context, tag) }
                        }
                    }
                    if (result.catalog.isNotEmpty()) item("catalog") { TagCatalogHeading(stringResource(R.string.combined_dictionary)) }
                    items(result.catalog, key = TagIdentity::qualifiedName) { tag ->
                        TagCatalogItem(tag, false, tag in selected, translate, onChoose) {
                            scope.launch { toggleFavoriteTag(context, tag) }
                        }
                    }
                    if (result.hasMore) {
                        item("more") {
                            TextButton(onClick = { catalogLimit += 80 }, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(stringResource(R.string.combined_more_tags))
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class SearchMatches(val saved: List<TagIdentity>, val catalog: List<TagIdentity>, val hasMore: Boolean)

@Composable
private fun TagCatalogHeading(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun TagCatalogItem(
    tag: TagIdentity,
    starred: Boolean,
    selected: Boolean,
    translate: Boolean,
    onChoose: (TagIdentity) -> Unit,
    onStar: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = selected,
            onClick = { onChoose(tag) },
            enabled = tag.searchable,
            modifier = Modifier.weight(1f),
            label = {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(tag.displayName(translate))
                    Text(tag.qualifiedName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        val label = stringResource(if (starred) R.string.combined_unfavorite_tag else R.string.combined_favorite_tag)
        IconTooltip(label) {
            IconButton(onClick = onStar, shapes = IconButtonDefaults.shapes()) {
                Icon(if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder, contentDescription = label)
            }
        }
    }
}

@Composable
context(_: SnackbarHostState)
internal fun TagLibraryMenu() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val message = try {
                runSuspendCatching {
                    val stats = withContext(Dispatchers.IO) {
                        TagFavorites.importDocument(context.contentResolver.readTagBackup(uri).decodeToString())
                    }
                    context.getString(R.string.combined_import_result, stats.added, stats.existing, stats.invalid)
                }.getOrElse {
                    logcat(it)
                    context.getString(R.string.combined_import_failed)
                }
            } finally {
                busy = false
            }
            snackbar(message)
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val message = try {
                runSuspendCatching {
                    withContext(Dispatchers.IO) {
                        val json = TagFavorites.export()
                        val bytes = json.encodeToByteArray()
                        check(FavoriteTagFile.decode(json).second == 0)
                        val resolver = context.contentResolver
                        checkNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
                        check(resolver.readTagBackup(uri).contentEquals(bytes))
                    }
                    R.string.combined_export_done
                }.getOrElse {
                    logcat(it)
                    withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) } }
                    R.string.combined_export_failed
                }
            } finally {
                busy = false
            }
            snackbar(context.getString(message))
        }
    }
    Box {
        val more = stringResource(R.string.combined_manage_tags)
        IconTooltip(more) {
            IconButton(onClick = { menu = true }, enabled = !busy, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Default.MoreVert, contentDescription = more)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.combined_import_tags)) },
                leadingIcon = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                onClick = {
                    menu = false
                    importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.combined_export_tags)) },
                leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                onClick = {
                    menu = false
                    exporter.launch("ehviewer-tag-favorites.json")
                },
            )
        }
    }
}

private fun ContentResolver.readTagBackup(uri: Uri): ByteArray = checkNotNull(openInputStream(uri)).source().buffer().use { input ->
    require(!input.request(8L * 1024 * 1024 + 1))
    input.readByteArray()
}
