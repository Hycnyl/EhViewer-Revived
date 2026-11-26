// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.search

import com.ehviewer.core.database.model.QuickSearch
import com.ehviewer.core.model.GalleryInfo.Companion.S_LANG_TAGS
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
    val tag = S_LANG_TAGS.getOrNull(language)?.let(TagIdentity::from) ?: return this
    return copy(terms = listOf(SearchTerm.forTag(tag, SearchPlacement.Required, TagMatch.Exact)) + terms)
}

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

internal data class SearchName(val label: String, val afterGid: Long?) {
    // Old readers use the last '@' as the cursor separator. An empty suffix
    // disambiguates a literal '@' in a newly generated name on those readers.
    fun stored() = when {
        afterGid != null -> "$label@$afterGid"
        '@' in label -> "$label@"
        else -> label
    }

    companion object {
        fun of(item: QuickSearch): SearchName {
            val name = item.name
            if (name == item.keyword) return SearchName(name, null)
            val cursor = name.substringAfterLast('@', "").toLongOrNull()?.takeIf { it > 0 }
            return when {
                cursor != null -> SearchName(name.substringBeforeLast('@'), cursor)
                name.endsWith('@') -> SearchName(name.dropLast(1), null)
                else -> SearchName(name, null)
            }
        }
    }
}

internal const val EMPTY_SEARCH_NAME = "All galleries"

internal val QuickSearch.automaticallyNamed: Boolean
    get() = mode in setOf(MODE_NORMAL, MODE_SUBSCRIPTION) && SearchName.of(this).label.let { it == keyword.orEmpty() || (keyword.isNullOrBlank() && it == EMPTY_SEARCH_NAME) }

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
