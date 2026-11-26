// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.ui.screen

import android.content.ClipboardManager
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.IconTooltip
import com.ehviewer.core.ui.component.LabeledCheckbox
import com.ehviewer.core.util.launch
import com.ehviewer.core.util.logcat
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhTagDatabase
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.search.CombinedSearchSource
import com.hippo.ehviewer.search.QuickSearchConflict
import com.hippo.ehviewer.search.QuickSearchConflictChoice
import com.hippo.ehviewer.search.SearchExpression
import com.hippo.ehviewer.search.SearchName
import com.hippo.ehviewer.search.SearchPlacement
import com.hippo.ehviewer.search.SearchProblem
import com.hippo.ehviewer.search.SearchTerm
import com.hippo.ehviewer.search.TagFavorites
import com.hippo.ehviewer.search.TagMatch
import com.hippo.ehviewer.search.automaticallyNamed
import com.hippo.ehviewer.search.combinedSearchUrl
import com.hippo.ehviewer.search.expression
import com.hippo.ehviewer.search.sameSearch
import com.hippo.ehviewer.search.withLanguage
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.main.AdvancedSearchOption
import com.hippo.ehviewer.ui.main.NavigationIcon
import com.hippo.ehviewer.ui.main.SearchFilter
import com.hippo.ehviewer.ui.search.TagCatalogPane
import com.hippo.ehviewer.ui.search.describe
import com.hippo.ehviewer.ui.search.displayLabel
import com.hippo.ehviewer.ui.search.resolveCombinedSearchConflict
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.util.addTextToClipboard
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import moe.tarsin.coroutines.runSuspendCatching
import moe.tarsin.snackbar

internal val combinedSearchFocus = MutableStateFlow<Long?>(null)

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.CombinedSearchBuilderScreen(source: CombinedSearchSource, navigator: DestinationsNavigator) = Screen(navigator) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val formState = rememberLazyListState()
    val initial = source.filters
    val initialExpression = remember(initial) { initial.expression() }
    var raw by rememberSaveable { mutableStateOf(initialExpression.text) }
    var titleKeywords by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableIntStateOf(initial.category.coerceAtLeast(0)) }
    var language by rememberSaveable { mutableIntStateOf(initial.language) }
    var advancedFlags by rememberSaveable { mutableIntStateOf(initial.advanceSearch.coerceAtLeast(0)) }
    var minimumRating by rememberSaveable { mutableIntStateOf(initial.minRating.coerceAtLeast(0)) }
    var firstPage by rememberSaveable { mutableIntStateOf(initial.pageFrom.coerceAtLeast(0)) }
    var lastPage by rememberSaveable { mutableIntStateOf(initial.pageTo.coerceAtLeast(0)) }
    var placementIndex by rememberSaveable { mutableIntStateOf(0) }
    var catalogExpanded by rememberSaveable { mutableStateOf(false) }
    var rawExpanded by rememberSaveable { mutableStateOf(false) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var saveProgress by rememberSaveable { mutableStateOf(Settings.qSSaveProgress.value) }
    var saving by remember { mutableStateOf(false) }
    val favorites by TagFavorites.entries.collectAsState(emptyList())
    val showTranslations by Settings.showTagTranslations.collectAsState()
    val translate = showTranslations && EhTagDatabase.translatable
    val terms = remember(raw) { SearchExpression.parse(raw).terms }
    val pending = remember(titleKeywords) { SearchExpression.parse(titleKeywords).terms.map(SearchTerm::asTitleKeyword) }
    val query = SearchExpression(terms + pending)
    val expression = query.withLanguage(language)
    val selectedPlacement = SearchPlacement.entries[placementIndex]
    val url = combinedSearchUrl(initial.mode, query, category, language, advancedFlags, minimumRating, firstPage, lastPage)
    val unchangedLegacy = source.savedId != null && initial.mode != ListUrlBuilder.MODE_NORMAL && initial.mode != ListUrlBuilder.MODE_SUBSCRIPTION &&
        initialExpression == query && initial.category.coerceAtLeast(0) == category &&
        initial.language == language && initial.advanceSearch.coerceAtLeast(0) == advancedFlags &&
        initial.minRating.coerceAtLeast(0) == minimumRating && initial.pageFrom.coerceAtLeast(0) == firstPage && initial.pageTo.coerceAtLeast(0) == lastPage
    val criteria = if (unchangedLegacy) initial.toQuickSearch("") else url.toQuickSearch("")
    val problems = expression.validate()
    val canSubmit = problems.isEmpty() || unchangedLegacy
    val currentPosition = source.currentResult?.let { result ->
        val comparable = if (result.mode in setOf(ListUrlBuilder.MODE_NORMAL, ListUrlBuilder.MODE_SUBSCRIPTION)) {
            result.copy().apply {
                keyword = result.expression().withLanguage(result.language).text.ifBlank { null }
                language = -1
            }
        } else {
            result
        }
        source.firstVisibleGid?.takeIf { it > 0 && it < Long.MAX_VALUE && comparable.toQuickSearch("").sameSearch(criteria) }?.plus(1)
    }

    fun changeTerm(index: Int, change: SearchTerm?) {
        raw = SearchExpression(
            terms.toMutableList().apply {
                if (change == null) removeAt(index) else set(index, change)
            },
        ).text
    }

    fun finish(id: Long) {
        combinedSearchFocus.value = id
        navigator.popBackStack()
    }

    fun save(asNew: Boolean) {
        if (saving || !canSubmit) return
        focusManager.clearFocus()
        saving = true
        launch {
            try {
                val rows = EhDB.getAllQuickSearch()
                val original = if (asNew) null else rows.firstOrNull { it.id == source.savedId }
                if (source.savedId != null && original == null && !asNew) {
                    snackbar(context.getString(R.string.combined_save_conflict))
                    return@launch
                }
                val originalName = original?.let(SearchName::of)
                val name = if (original != null && !original.automaticallyNamed) originalName!!.label else expression.text
                val cursor = when {
                    currentPosition != null && saveProgress -> currentPosition
                    original?.sameSearch(criteria) == true -> originalName?.afterGid
                    else -> null
                }
                val candidate = criteria.copy(name = SearchName(name, cursor).stored())
                val sameName = rows.filter { it.id != original?.id && SearchName.of(it).label == name }
                val duplicate = sameName.firstOrNull { candidate.sameSearch(it) }
                if (duplicate?.id != null) {
                    awaitConfirmationOrCancel(
                        title = R.string.combined_duplicate,
                        confirmText = R.string.combined_locate,
                    ) { Text(name.ifBlank { context.getString(R.string.combined_all_galleries) }) }
                    finish(duplicate.id!!)
                    return@launch
                }
                val collision = sameName.firstOrNull()
                val target = if (collision == null) {
                    original
                } else {
                    when (resolveCombinedSearchConflict(QuickSearchConflict(collision, candidate), allowReplace = original == null)) {
                        QuickSearchConflictChoice.Replace -> collision
                        QuickSearchConflictChoice.KeepBoth -> original
                        QuickSearchConflictChoice.KeepExisting -> return@launch
                    }
                }
                val saved = runSuspendCatching { EhDB.saveQuickSearch(candidate, target) }.onFailure { logcat(it) }.getOrNull()
                if (saved == null) {
                    snackbar(context.getString(R.string.combined_save_conflict))
                    return@launch
                }
                if (currentPosition != null) Settings.qSSaveProgress.value = saveProgress
                finish(saved)
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (source.savedId == null) R.string.combined_search else R.string.combined_edit)) },
                navigationIcon = { NavigationIcon() },
            )
        },
        bottomBar = {
            Surface {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (source.savedId != null) {
                        TextButton(onClick = { save(asNew = true) }, enabled = canSubmit && !saving) {
                            Text(stringResource(R.string.combined_save_copy))
                        }
                    }
                    OutlinedButton(onClick = { save(asNew = false) }, enabled = canSubmit && !saving) {
                        Text(stringResource(R.string.combined_save))
                    }
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            navigator.popBackStack()
                            navigator.navigate((if (unchangedLegacy) initial else url).asDst())
                        },
                        enabled = canSubmit && !saving,
                    ) {
                        Text(stringResource(R.string.combined_run))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = formState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("keywords") {
                OutlinedTextField(
                    value = titleKeywords,
                    onValueChange = { titleKeywords = it },
                    label = { Text(stringResource(R.string.combined_title_keywords)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        val label = stringResource(R.string.combined_add_keywords)
                        IconTooltip(label) {
                            IconButton(
                                onClick = {
                                    raw = query.text
                                    titleKeywords = ""
                                },
                                enabled = pending.isNotEmpty(),
                                shapes = IconButtonDefaults.shapes(),
                            ) { Icon(Icons.Default.Add, contentDescription = label) }
                        }
                    },
                )
            }
            items(SearchPlacement.entries.size, key = { SearchPlacement.entries[it].name }) { i ->
                val placement = SearchPlacement.entries[i]
                val colors = MaterialTheme.colorScheme
                val tint = when (placement) {
                    SearchPlacement.Required -> colors.primaryContainer
                    SearchPlacement.Any -> colors.secondaryContainer
                    SearchPlacement.Excluded -> colors.errorContainer
                }
                Card(colors = CardDefaults.cardColors(containerColor = tint)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        FilterChip(
                            selected = selectedPlacement == placement,
                            onClick = {
                                placementIndex = i
                                catalogExpanded = true
                                launch { formState.animateScrollToItem(SearchPlacement.entries.size + 1) }
                            },
                            leadingIcon = {
                                Icon(
                                    when (placement) {
                                        SearchPlacement.Required -> Icons.Default.DoneAll
                                        SearchPlacement.Any -> Icons.Default.AltRoute
                                        SearchPlacement.Excluded -> Icons.Default.Block
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(placement.title)) },
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            terms.forEachIndexed { index, term ->
                                if (term.placement == placement) {
                                    ConditionChip(
                                        term = term,
                                        label = term.displayLabel(context, translate),
                                        onChange = { changeTerm(index, it) },
                                        onEditRaw = { rawExpanded = true },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item("catalog") {
                Column {
                    TextButton(onClick = { catalogExpanded = !catalogExpanded }) {
                        Text(stringResource(if (catalogExpanded) R.string.combined_hide_tags else R.string.combined_choose_tags))
                    }
                    if (catalogExpanded) {
                        Text(
                            stringResource(R.string.combined_add_to, stringResource(selectedPlacement.title)),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        TagCatalogPane(
                            favorites = favorites,
                            selected = terms.mapNotNull(SearchTerm::tag).toSet(),
                            onChoose = { tag ->
                                val selected = terms.any { it.tag == tag }
                                val updated = if (selected) terms.filterNot { it.tag == tag } else terms + SearchTerm.forTag(tag, selectedPlacement, TagMatch.Exact)
                                raw = SearchExpression(updated).text
                            },
                            modifier = Modifier.fillMaxWidth().height(380.dp),
                        )
                    }
                }
            }
            item("filters") {
                Column {
                    TextButton(onClick = { filtersExpanded = !filtersExpanded }) {
                        Text(stringResource(R.string.combined_advanced))
                    }
                    val activeLanguage = SearchExpression(expression.terms.filter { it.namespace?.value == "language" })
                    Text(
                        stringResource(
                            R.string.combined_effective_language,
                            activeLanguage.describe(context, translate).ifBlank { stringResource(R.string.combined_site_default_language) },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (filtersExpanded) {
                        SearchFilter(
                            category = category,
                            onCategoryChange = { category = it },
                            language = language,
                            onLanguageChange = { language = it },
                            advancedOption = AdvancedSearchOption(advancedFlags, minimumRating, firstPage, lastPage),
                            onAdvancedOptionChange = {
                                advancedFlags = it.advanceSearch
                                minimumRating = it.minRating
                                firstPage = it.fromPage
                                lastPage = it.toPage
                            },
                        )
                    }
                }
            }
            item("raw") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { rawExpanded = !rawExpanded }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.combined_expression))
                        }
                        val copy = stringResource(R.string.combined_copy_expression)
                        IconTooltip(copy) {
                            IconButton(onClick = { addTextToClipboard(expression.text) }, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.Default.ContentCopy, contentDescription = copy)
                            }
                        }
                        val paste = stringResource(R.string.combined_paste_expression)
                        IconTooltip(paste) {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    val pasted = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                    if (!pasted.isNullOrBlank()) {
                                        raw = pasted
                                        titleKeywords = ""
                                        language = -1
                                        rawExpanded = true
                                    }
                                },
                                shapes = IconButtonDefaults.shapes(),
                            ) { Icon(Icons.Default.ContentPaste, contentDescription = paste) }
                        }
                    }
                    if (rawExpanded) {
                        Text(expression.text.ifBlank { stringResource(R.string.combined_all_galleries) }, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = raw,
                            onValueChange = { raw = it },
                            label = { Text(stringResource(R.string.combined_edit_expression)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 5,
                        )
                    }
                    for (problem in problems) {
                        Text(stringResource(problem.message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (currentPosition != null) {
                item("progress") {
                    LabeledCheckbox(
                        checked = saveProgress,
                        onCheckedChange = { saveProgress = it },
                        label = stringResource(R.string.save_progress),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConditionChip(term: SearchTerm, label: String, onChange: (SearchTerm?) -> Unit, onEditRaw: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        InputChip(
            selected = false,
            onClick = { menu = true },
            label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            for (placement in SearchPlacement.entries) {
                if (placement != term.placement) {
                    DropdownMenuItem(
                        text = { Text(stringResource(placement.title)) },
                        onClick = {
                            menu = false
                            onChange(term.inPlacement(placement))
                        },
                    )
                }
            }
            val tag = term.tag
            if (tag != null) {
                for (match in TagMatch.entries) {
                    if (match != term.match && (!match.weak || term.placement != SearchPlacement.Excluded)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(match.title)) },
                            onClick = {
                                menu = false
                                onChange(SearchTerm.forTag(tag, term.placement, match))
                            },
                        )
                    }
                }
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.combined_edit_expression)) },
                onClick = {
                    menu = false
                    onEditRaw()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.combined_remove_condition)) },
                onClick = {
                    menu = false
                    onChange(null)
                },
            )
        }
    }
}

@get:StringRes
private val SearchPlacement.title: Int
    get() = when (this) {
        SearchPlacement.Required -> R.string.combined_required
        SearchPlacement.Any -> R.string.combined_any
        SearchPlacement.Excluded -> R.string.combined_excluded
    }

@get:StringRes
private val TagMatch.title: Int
    get() = when (this) {
        TagMatch.Exact -> R.string.combined_exact
        TagMatch.Prefix -> R.string.combined_prefix
        TagMatch.WeakExact -> R.string.combined_weak_exact
        TagMatch.WeakPrefix -> R.string.combined_weak_prefix
    }

@get:StringRes
private val SearchProblem.message: Int
    get() = when (this) {
        SearchProblem.UnclosedQuotes -> R.string.combined_error_quotes
        SearchProblem.EmptyTerm -> R.string.combined_error_empty
        SearchProblem.InvalidOperators -> R.string.combined_error_operators
        SearchProblem.InvalidIdentifier -> R.string.combined_error_id
        SearchProblem.TooLong -> R.string.combined_error_length
        SearchProblem.TooManyRequired -> R.string.combined_error_required
        SearchProblem.TooManyExcluded -> R.string.combined_error_excluded
        SearchProblem.TooManyNotes -> R.string.combined_error_notes
        SearchProblem.WeakExcluded -> R.string.combined_error_weak_excluded
        SearchProblem.WeakQualifier -> R.string.combined_error_weak_qualifier
        SearchProblem.InconsistentWeakOr -> R.string.combined_error_weak_or
        SearchProblem.WeakAndActive -> R.string.combined_error_weak_active
        SearchProblem.FavoritesOnly -> R.string.combined_error_favnote
        SearchProblem.TemporaryTag -> R.string.combined_error_temp
    }
