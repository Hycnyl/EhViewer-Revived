// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.search

import com.ehviewer.core.database.model.QuickSearch

internal data class QuickSearchConflict(val existing: QuickSearch, val incoming: QuickSearch)

internal enum class QuickSearchConflictChoice { Replace, KeepBoth, KeepExisting }

internal suspend fun mergeSearchBackups(
    current: List<QuickSearch>,
    incoming: List<QuickSearch>,
    resolve: suspend (QuickSearchConflict) -> QuickSearchConflictChoice,
): List<QuickSearch> {
    val result = current.toMutableList()
    for (item in incoming) {
        if (result.any { it.sameRecord(item) }) continue

        val label = SearchName.of(item).label
        val conflict = result.indexOfFirst { SearchName.of(it).label == label && !it.sameSearch(item) }
        if (conflict >= 0) {
            val previous = result[conflict]
            when (resolve(QuickSearchConflict(previous, item))) {
                QuickSearchConflictChoice.Replace -> {
                    result[conflict] = item.copy(id = previous.id, position = previous.position)
                    continue
                }
                QuickSearchConflictChoice.KeepExisting -> continue
                QuickSearchConflictChoice.KeepBoth -> Unit
            }
        }
        result += item.copy(id = null, position = (result.lastOrNull()?.position ?: -1) + 1)
    }
    return result
}
