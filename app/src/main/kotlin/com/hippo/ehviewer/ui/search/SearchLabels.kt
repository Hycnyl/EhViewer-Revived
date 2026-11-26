// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.ui.search

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ehviewer.core.database.model.QuickSearch
import com.ehviewer.core.i18n.R
import com.hippo.ehviewer.client.EhTagDatabase
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.search.QuickSearchConflict
import com.hippo.ehviewer.search.QuickSearchConflictChoice
import com.hippo.ehviewer.search.SearchExpression
import com.hippo.ehviewer.search.SearchName
import com.hippo.ehviewer.search.SearchTerm
import com.hippo.ehviewer.search.TagIdentity
import com.hippo.ehviewer.search.expression
import com.hippo.ehviewer.ui.main.AdvanceTable
import com.hippo.ehviewer.ui.main.searchCategoryOptions
import com.hippo.ehviewer.ui.tools.DialogState
import com.hippo.ehviewer.ui.tools.dialog
import kotlin.coroutines.resume

internal fun TagIdentity.displayName(translate: Boolean) =
    if (translate) EhTagDatabase.translation(this) ?: name else name

internal fun namespaceDisplayName(namespace: String, translate: Boolean) =
    if (translate) EhTagDatabase.getTranslation(tag = namespace) ?: namespace else namespace

internal fun SearchTerm.displayLabel(context: Context, translate: Boolean): String {
    val localized = if (translate) tag?.let(EhTagDatabase::translation) else null
    if (localized == null) return raw.removePrefix(placement.operator)
    val label = if (match.exact) localized else context.getString(R.string.combined_prefix_label, localized)
    return if (match.weak) context.getString(R.string.combined_weak_label, label) else label
}

internal fun SearchExpression.describe(context: Context, translate: Boolean): String =
    describe(
        joiner = " · ",
        describeTerm = { it.displayLabel(context, translate) },
        alternatives = {
            context.getString(R.string.combined_any_summary, it.joinToString(" " + context.getString(R.string.combined_or_joiner).trim() + " "))
        },
        exclusion = { context.getString(R.string.combined_excluded_summary, it) },
    )

internal fun QuickSearch.expressionForDisplay() = ListUrlBuilder(this).expression().text.ifBlank { keyword.orEmpty() }

internal fun QuickSearch.displayBaseName(context: Context) = SearchName.of(this).label.ifBlank { context.getString(R.string.combined_all_galleries) }

@Composable
internal fun searchParametersLabel(item: QuickSearch, showDefaults: Boolean = false): String {
    val labels = mutableListOf<String>()
    if (item.mode == ListUrlBuilder.MODE_SUBSCRIPTION) labels += stringResource(R.string.subscription)
    if (item.category > 0 && item.category != EhUtils.ALL_CATEGORY) {
        labels += searchCategoryOptions.filter { item.category and it.first != 0 }
            .map { stringResource(it.second) }.joinToString(", ")
    } else if (showDefaults) {
        labels += stringResource(R.string.combined_all_categories)
    }
    if (item.minRating > 0) labels += stringResource(R.string.combined_rating_summary, item.minRating)
    if (item.pageFrom > 0 || item.pageTo > 0) {
        val min = item.pageFrom.takeIf { it > 0 }?.toString() ?: "–"
        val max = item.pageTo.takeIf { it > 0 }?.toString() ?: "–"
        labels += stringResource(R.string.combined_pages_summary, min, max)
    }
    val advanced = item.advanceSearch.coerceAtLeast(0)
    if (advanced and AdvanceTable.SH != 0) labels += stringResource(R.string.search_sh)
    if (advanced and AdvanceTable.STO != 0) labels += stringResource(R.string.search_sto)
    val disable = stringResource(R.string.search_sf)
    if (advanced and AdvanceTable.SFL != 0) labels += disable + stringResource(R.string.search_sfl)
    if (advanced and AdvanceTable.SFU != 0) labels += disable + stringResource(R.string.search_sfu)
    if (advanced and AdvanceTable.SFT != 0) labels += disable + stringResource(R.string.search_sft)
    return labels.joinToString(" · ")
}

context(_: DialogState)
internal suspend fun resolveCombinedSearchConflict(conflict: QuickSearchConflict, allowReplace: Boolean = true): QuickSearchConflictChoice = dialog { answer ->
    AlertDialog(
        onDismissRequest = { answer.cancel() },
        title = { Text(stringResource(R.string.combined_conflict_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((title, record) in listOf(R.string.combined_existing to conflict.existing, R.string.combined_incoming to conflict.incoming)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                        Text(record.expressionForDisplay().ifBlank { stringResource(R.string.combined_all_galleries) })
                        Text(searchParametersLabel(record, showDefaults = true))
                    }
                }
            }
        },
        confirmButton = {
            if (allowReplace) {
                TextButton(onClick = { answer.resume(QuickSearchConflictChoice.Replace) }) {
                    Text(stringResource(R.string.combined_replace))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { answer.resume(QuickSearchConflictChoice.KeepExisting) }) {
                Text(stringResource(R.string.combined_keep_existing))
            }
            TextButton(onClick = { answer.resume(QuickSearchConflictChoice.KeepBoth) }) {
                Text(stringResource(R.string.combined_keep_both))
            }
        },
    )
}
