// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.ui.screen

import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.IconTooltip
import com.ehviewer.core.ui.component.LabeledCheckbox
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
import com.hippo.ehviewer.search.SearchProblem
import com.hippo.ehviewer.search.SearchTerm
import com.hippo.ehviewer.search.TagFavorites
import com.hippo.ehviewer.search.TagMatch
import com.hippo.ehviewer.search.automaticallyNamed
import com.hippo.ehviewer.search.combinedCategory
import com.hippo.ehviewer.search.combinedSearchUrl
import com.hippo.ehviewer.search.expression
import com.hippo.ehviewer.search.filterLanguage
import com.hippo.ehviewer.search.replaceFilterLanguage
import com.hippo.ehviewer.search.sameSearch
import com.hippo.ehviewer.search.withLanguage
import com.hippo.ehviewer.ui.main.AdvancedSearchOption
import com.hippo.ehviewer.ui.main.SearchFilter
import com.hippo.ehviewer.ui.search.CompactTagPicker
import com.hippo.ehviewer.ui.search.awaitCombinationName
import com.hippo.ehviewer.ui.search.displayLabel
import com.hippo.ehviewer.ui.tools.DialogState
import com.hippo.ehviewer.util.addTextToClipboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import moe.tarsin.coroutines.runSuspendCatching
import moe.tarsin.snackbar

internal val combinedSearchFocus = MutableStateFlow<Long?>(null)

@Composable
context(_: Context, _: SnackbarHostState)
internal fun CombinedSearchBuilderDialog(
    source: CombinedSearchSource,
    onDismiss: () -> Unit,
    onRun: (ListUrlBuilder) -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val dialogs = remember { DialogState() }
    val scope = rememberCoroutineScope()
    val initial = source.filters
    val standardSearch = initial.mode in setOf(ListUrlBuilder.MODE_NORMAL, ListUrlBuilder.MODE_SUBSCRIPTION)
    val initialQuery = remember(initial) {
        val query = initial.expression()
        if (standardSearch) query.withLanguage(initial.language) else query
    }
    var raw by rememberSaveable { mutableStateOf(initialQuery.text) }
    var category by rememberSaveable { mutableIntStateOf(initial.combinedCategory) }
    var advancedFlags by rememberSaveable { mutableIntStateOf(initial.advanceSearch.coerceAtLeast(0)) }
    var minimumRating by rememberSaveable { mutableIntStateOf(initial.minRating.coerceAtLeast(0)) }
    var firstPage by rememberSaveable { mutableIntStateOf(initial.pageFrom.coerceAtLeast(0)) }
    var lastPage by rememberSaveable { mutableIntStateOf(initial.pageTo.coerceAtLeast(0)) }
    var placementIndex by rememberSaveable { mutableIntStateOf(SearchPlacement.Required.ordinal) }
    var saveProgress by rememberSaveable { mutableStateOf(Settings.qSSaveProgress.value) }
    var saving by remember { mutableStateOf(false) }
    val favorites by TagFavorites.entries.collectAsState(emptyList())
    val showTranslations by Settings.showTagTranslations.collectAsState()
    val translate = showTranslations && EhTagDatabase.translatable
    val query = remember(raw) { SearchExpression.parse(raw) }
    val placement = SearchPlacement.entries[placementIndex]
    val placedTerms = query.terms.withIndex().filter { it.value.placement == placement }
    val selectedTags = placedTerms.mapNotNullTo(hashSetOf()) { it.value.tag }
    val url = combinedSearchUrl(initial.mode, query, category, -1, advancedFlags, minimumRating, firstPage, lastPage)
    val unchangedLegacy = source.savedId != null && !standardSearch &&
        initialQuery == query && initial.combinedCategory == category &&
        initial.advanceSearch.coerceAtLeast(0) == advancedFlags &&
        initial.minRating.coerceAtLeast(0) == minimumRating && initial.pageFrom.coerceAtLeast(0) == firstPage && initial.pageTo.coerceAtLeast(0) == lastPage
    val criteria = if (unchangedLegacy) initial.toQuickSearch("") else url.toQuickSearch("")
    val problem = if (category == 0) {
        R.string.combined_select_category
    } else if (unchangedLegacy) {
        null
    } else {
        query.validate().firstOrNull()?.message
    }
    val canSubmit = problem == null && !saving
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

    fun changeTerm(index: Int, replacement: SearchTerm?) {
        raw = SearchExpression(
            query.terms.toMutableList().apply {
                if (replacement == null) removeAt(index) else set(index, replacement)
            },
        ).text
    }

    fun save(asNew: Boolean) {
        if (!canSubmit) return
        saving = true
        scope.launch {
            try {
                val rows = EhDB.getAllQuickSearch()
                val original = if (asNew) null else rows.firstOrNull { it.id == source.savedId }
                if (source.savedId != null && original == null && !asNew) {
                    snackbar(context.getString(R.string.combined_save_conflict))
                    return@launch
                }
                val originalName = original?.let(SearchName::of)
                val suggestedName = if (original != null && !original.automaticallyNamed) originalName!!.label else ""
                val label = with(dialogs) {
                    awaitCombinationName(
                        context,
                        if (asNew) R.string.combined_save_copy else R.string.combined_save,
                        suggestedName,
                        original?.id,
                    )
                }
                val cursor = when {
                    currentPosition != null && saveProgress -> currentPosition
                    original?.sameSearch(criteria) == true -> originalName?.afterGid
                    else -> null
                }
                val candidate = criteria.copy(name = SearchName(label, cursor, usesSavedLanguage = !unchangedLegacy).stored())
                val saved = runSuspendCatching { EhDB.saveQuickSearch(candidate, original, rows) }.onFailure { logcat(it) }.getOrNull()
                if (saved == null) {
                    snackbar(context.getString(R.string.combined_save_conflict))
                } else {
                    if (currentPosition != null) Settings.qSSaveProgress.value = saveProgress
                    onSaved(saved)
                }
            } finally {
                saving = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val focusManager = LocalFocusManager.current
        val pinExpression = LocalConfiguration.current.screenHeightDp >= 540 && LocalDensity.current.fontScale <= 1.4f
        val expressionEditor: @Composable () -> Unit = {
            SearchExpressionEditor(
                raw = raw,
                onChange = { raw = it },
                expression = query.text,
                problem = problem,
            )
        }
        BoxWithConstraints(Modifier.safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            val availableHeight = maxHeight
            Surface(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth().heightIn(max = minOf(availableHeight, 760.dp)),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column {
                    if (pinExpression) expressionEditor()
                    CompactTagPicker(
                        favorites = favorites,
                        selected = selectedTags,
                        onChoose = { tag ->
                            val existing = placedTerms.firstOrNull { it.value.tag == tag }
                            if (existing == null) {
                                raw = SearchExpression(query.terms + SearchTerm.forTag(tag, placement, TagMatch.Exact)).text
                            } else {
                                changeTerm(existing.index, null)
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        if (!pinExpression) item("expression") { expressionEditor() }
                        item("filters") {
                            with(dialogs) {
                                with(scope) {
                                    SearchFilter(
                                        category = category,
                                        onCategoryChange = { category = it },
                                        language = query.filterLanguage,
                                        onLanguageChange = { raw = query.replaceFilterLanguage(it).text },
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
                        item("placements") {
                            PlacementTabs(placement) { placementIndex = it.ordinal }
                        }
                        if (placedTerms.isNotEmpty()) {
                            item("conditions") {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    for ((index, term) in placedTerms) {
                                        ConditionChip(term, term.displayLabel(context, translate), onChange = { changeTerm(index, it) })
                                    }
                                }
                            }
                        }
                        if (currentPosition != null) {
                            item("progress") {
                                Box(Modifier.padding(horizontal = 16.dp)) {
                                    LabeledCheckbox(
                                        checked = saveProgress,
                                        onCheckedChange = { saveProgress = it },
                                        label = stringResource(R.string.save_progress),
                                    )
                                }
                            }
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        if (source.savedId != null) {
                            TextButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    save(asNew = true)
                                },
                                enabled = canSubmit,
                            ) { Text(stringResource(R.string.combined_save_copy)) }
                        }
                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus()
                                save(asNew = false)
                            },
                            enabled = canSubmit,
                        ) { Text(stringResource(R.string.combined_save)) }
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onRun(if (unchangedLegacy) initial else url)
                            },
                            enabled = canSubmit,
                        ) { Text(stringResource(R.string.combined_run)) }
                    }
                }
            }
            dialogs.Place()
        }
    }
}

@Composable
context(_: Context)
private fun SearchExpressionEditor(raw: String, onChange: (String) -> Unit, expression: String, @StringRes problem: Int?) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.combined_expression), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            val copy = stringResource(R.string.combined_copy_expression)
            IconTooltip(copy) {
                IconButton(onClick = { addTextToClipboard(expression) }, enabled = expression.isNotBlank(), shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Default.ContentCopy, contentDescription = copy, modifier = Modifier.size(18.dp))
                }
            }
            val paste = stringResource(R.string.combined_paste_expression)
            IconTooltip(paste) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let(onChange)
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = paste, modifier = Modifier.size(18.dp))
                }
            }
        }
        OutlinedTextField(
            value = raw,
            onValueChange = onChange,
            placeholder = { Text(stringResource(R.string.combined_expression_hint)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            isError = problem != null,
        )
        if (problem != null) {
            Text(stringResource(problem), Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlacementTabs(selected: SearchPlacement, onSelect: (SearchPlacement) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (placement in SearchPlacement.entries) {
            val active = placement == selected
            val label = stringResource(placement.title)
            val colors = MaterialTheme.colorScheme
            Box(Modifier.weight(if (active) 2f else 1f)) {
                IconTooltip(label) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (active) colors.secondaryContainer else colors.surfaceContainer,
                        contentColor = if (active) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = active, role = Role.Tab, onClick = { onSelect(placement) })
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                when (placement) {
                                    SearchPlacement.Required -> Icons.Default.DoneAll
                                    SearchPlacement.Any -> Icons.AutoMirrored.Filled.AltRoute
                                    SearchPlacement.Excluded -> Icons.Default.Block
                                },
                                contentDescription = if (active) null else label,
                                modifier = Modifier.size(18.dp),
                            )
                            if (active) Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConditionChip(term: SearchTerm, label: String, onChange: (SearchTerm?) -> Unit) {
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
