// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.search

import com.ehviewer.core.database.model.QuickSearch
import com.ehviewer.core.model.GalleryInfo.Companion.S_LANG_TAGS
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_NORMAL
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_SUBSCRIPTION
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_TAG
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_UPLOADER
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data class CombinedSearchSource(
    val filters: ListUrlBuilder = ListUrlBuilder(),
    val currentResult: ListUrlBuilder? = null,
    val firstVisibleGid: Long? = null,
    val savedId: Long? = null,
)

internal fun ListUrlBuilder.expression(): SearchExpression = when (mode) {
    MODE_TAG -> TagIdentity.from(keyword.orEmpty())?.let {
        SearchExpression(listOf(SearchTerm.forTag(it, SearchPlacement.Required, TagMatch.Exact)))
    } ?: SearchExpression.parse(keyword.orEmpty())
    MODE_UPLOADER -> {
        val name = keyword.orEmpty().removePrefix("uploader:")
        SearchExpression.parse(if (' ' in name && '"' !in name) "uploader:\"$name\"" else keyword.orEmpty())
    }
    MODE_NORMAL, MODE_SUBSCRIPTION -> SearchExpression.parse(keyword.orEmpty())
    else -> SearchExpression()
}

internal fun SearchExpression.withLanguage(language: Int): SearchExpression {
    if (terms.any { it.namespace?.value == "language" || it.qualifier == "gid" }) return this
    val tag = filterLanguageTags.getOrNull(language) ?: return this
    return copy(terms = listOf(SearchTerm.forTag(tag, SearchPlacement.Required, TagMatch.Exact)) + terms)
}

private val filterLanguageTags = S_LANG_TAGS.map(TagIdentity::from)

internal val SearchExpression.filterLanguage: Int
    get() {
        val term = terms.singleOrNull { it.namespace?.value == "language" } ?: return -1
        if (term.placement != SearchPlacement.Required || term.match != TagMatch.Exact) return -1
        return term.tag?.let(filterLanguageTags::indexOf) ?: -1
    }

internal fun SearchExpression.replaceFilterLanguage(language: Int): SearchExpression =
    SearchExpression(terms.filterNot { it.namespace?.value == "language" }).withLanguage(language)

internal val ListUrlBuilder.combinedCategory
    get() = category.takeIf { it > 0 } ?: EhUtils.ALL_CATEGORY

internal fun combinedSearchUrl(
    mode: Int,
    query: SearchExpression,
    category: Int,
    language: Int,
    advanced: Int,
    rating: Int,
    pageFrom: Int,
    pageTo: Int,
): ListUrlBuilder = ListUrlBuilder(
    mode = if (mode == MODE_SUBSCRIPTION) MODE_SUBSCRIPTION else MODE_NORMAL,
    category = category,
    mKeyword = query.withLanguage(language).text.ifBlank { null },
    advanceSearch = advanced.coerceAtLeast(0),
    minRating = rating.coerceAtLeast(0),
    pageFrom = pageFrom.coerceAtLeast(0),
    pageTo = pageTo.coerceAtLeast(0),
)

// The markers belong to the existing name/cursor field. Older versions display
// the same title and still read the decimal cursor after the final '@'.
private const val SAVED_LANGUAGE_MARKER = '\u2063'
private const val DEFAULT_LANGUAGE_MARKER = '\u2060'

internal data class SearchName(val label: String, val afterGid: Long?, val usesSavedLanguage: Boolean? = null) {
    fun stored(): String {
        val marker = when (usesSavedLanguage) {
            true -> SAVED_LANGUAGE_MARKER.toString()
            false -> DEFAULT_LANGUAGE_MARKER.toString()
            null -> ""
        }
        val name = label + marker
        return when {
            afterGid != null -> "$name@$afterGid"
            '@' in label || marker.isNotEmpty() -> "$name@"
            else -> name
        }
    }

    companion object {
        fun of(item: QuickSearch): SearchName {
            val name = item.name
            if (name == item.keyword) return SearchName(name, null)
            val cursor = name.substringAfterLast('@', "").toLongOrNull()?.takeIf { it > 0 }
            if (cursor == null && !name.endsWith('@')) return SearchName(name, null)
            val label = name.substringBeforeLast('@')
            return when (label.lastOrNull()) {
                SAVED_LANGUAGE_MARKER -> SearchName(label.dropLast(1), cursor, true)
                DEFAULT_LANGUAGE_MARKER -> SearchName(label.dropLast(1), cursor, false)
                else -> SearchName(label, cursor)
            }
        }
    }
}

internal const val EMPTY_SEARCH_NAME = "All galleries"

internal val QuickSearch.automaticallyNamed: Boolean
    get() {
        if (mode != MODE_NORMAL && mode != MODE_SUBSCRIPTION) return false
        val name = SearchName.of(this)
        return name.usesSavedLanguage == null && (name.label == keyword.orEmpty() || (keyword.isNullOrBlank() && name.label == EMPTY_SEARCH_NAME))
    }

internal val QuickSearch.usesSavedLanguage: Boolean
    get() = SearchName.of(this).usesSavedLanguage ?: automaticallyNamed

internal fun QuickSearch.renameSearch(label: String): QuickSearch =
    copy(name = SearchName.of(this).copy(label = label, usesSavedLanguage = usesSavedLanguage).stored())

internal fun QuickSearch.toListUrlBuilder(defaultLanguage: Int): ListUrlBuilder = ListUrlBuilder(this).apply {
    if (!usesSavedLanguage) language = defaultLanguage
}

internal fun List<QuickSearch>.hasSearchName(label: String, exceptId: Long?): Boolean =
    any { (exceptId == null || it.id != exceptId) && SearchName.of(it).label.equals(label, ignoreCase = true) }

private fun SearchExpression.comparableTerms() = terms.map { term ->
    val canonical = term.tag?.let { SearchTerm.forTag(it, term.placement, term.match).raw } ?: term.raw
    canonical.lowercase(Locale.ROOT)
}

internal fun QuickSearch.sameSearch(other: QuickSearch): Boolean = mode == other.mode &&
    category == other.category &&
    advanceSearch.coerceAtLeast(0) == other.advanceSearch.coerceAtLeast(0) &&
    minRating.coerceAtLeast(0) == other.minRating.coerceAtLeast(0) &&
    pageFrom.coerceAtLeast(0) == other.pageFrom.coerceAtLeast(0) &&
    pageTo.coerceAtLeast(0) == other.pageTo.coerceAtLeast(0) &&
    if (mode == MODE_NORMAL || mode == MODE_SUBSCRIPTION) {
        SearchExpression.parse(keyword.orEmpty()).comparableTerms() == SearchExpression.parse(other.keyword.orEmpty()).comparableTerms()
    } else {
        keyword.orEmpty() == other.keyword.orEmpty()
    }

internal fun QuickSearch.sameRecord(other: QuickSearch): Boolean = sameSearch(other) && SearchName.of(this) == SearchName.of(other)
