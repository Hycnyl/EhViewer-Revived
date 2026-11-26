// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.search

import com.ehviewer.core.model.TagNamespace
import java.util.Locale
import kotlinx.serialization.Serializable

private val namespaces = listOf(
    TagNamespace.Artist, TagNamespace.Character, TagNamespace.Cosplayer, TagNamespace.Female,
    TagNamespace.Group, TagNamespace.Language, TagNamespace.Location, TagNamespace.Male,
    TagNamespace.Mixed, TagNamespace.Other, TagNamespace.Parody, TagNamespace.Reclass, TagNamespace.Temp,
)
private val namespaceNames = buildMap {
    namespaces.forEach { namespace ->
        put(namespace.value, namespace)
        namespace.prefix?.let { put(it, namespace) }
    }
    put("char", TagNamespace.Character)
    put("circle", TagNamespace.Group)
    put("lang", TagNamespace.Language)
    put("series", TagNamespace.Parody)
}

internal fun searchNamespace(value: String): TagNamespace? = namespaceNames[value.lowercase(Locale.ROOT)]

@Serializable
@ConsistentCopyVisibility
internal data class TagIdentity private constructor(val namespace: String, val name: String) {
    val qualifiedName: String get() = "$namespace:$name"
    val searchable: Boolean get() = namespace != TagNamespace.Temp.value

    companion object {
        fun from(value: String): TagIdentity? {
            val colon = value.indexOf(':')
            return if (colon < 1) null else from(value.take(colon), value.drop(colon + 1))
        }

        fun from(namespace: String, name: String): TagIdentity? {
            val resolved = searchNamespace(namespace.trim()) ?: return null
            if (name.any { it.isISOControl() || it in "\"$*%" }) return null
            val normalized = name.trim().replace('_', ' ').replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
            return normalized.takeIf { it.isNotBlank() }?.let { TagIdentity(resolved.value, it) }
        }
    }
}

internal enum class SearchPlacement(val operator: String) {
    Required(""),
    Any("~"),
    Excluded("-"),
}

internal enum class TagMatch(val exact: Boolean, val weak: Boolean) {
    Exact(exact = true, weak = false),
    Prefix(exact = false, weak = false),
    WeakExact(exact = true, weak = true),
    WeakPrefix(exact = false, weak = true),
}

internal data class SearchTerm(val raw: String) {
    val placement: SearchPlacement
        get() = when (raw.firstOrNull()) {
            '-' -> SearchPlacement.Excluded
            '~' -> SearchPlacement.Any
            else -> SearchPlacement.Required
        }

    private val body: String get() = raw.removePrefix(placement.operator)
    val weak: Boolean get() = body.startsWith("weak:", ignoreCase = true)
    private val qualified: String get() = if (weak) body.substringAfter(':') else body
    private val qualifierEnd: Int
        get() = qualified.indexOf(':').takeIf { it > 0 && qualified.take(it).all { c -> c.isLetter() } } ?: -1
    val qualifier: String get() = qualified.take(qualifierEnd.coerceAtLeast(0)).lowercase(Locale.ROOT)
    val namespace: TagNamespace? get() = searchNamespace(qualifier)
    private val payload: String get() = qualified.drop(qualifierEnd + 1)
    private val exact: Boolean get() = payload.endsWith('$') || payload.endsWith("$\"")
    private val unquoted: String get() = payload.removeSuffix("$").removeSurrounding("\"").removeSuffix("$")
    val match: TagMatch get() = TagMatch.entries.first { it.weak == weak && it.exact == exact }
    val tag: TagIdentity?
        get() = namespace?.let { TagIdentity.from(it.value, unquoted) }?.takeIf { identity ->
            !identity.name.contains(',') || payload.contains('"')
        }

    val isKeyword: Boolean
        get() = !weak && (qualifier.isEmpty() || qualifier == "title") && !exact
    val hasUnclosedQuote: Boolean get() = raw.count { it == '"' } % 2 != 0
    val hasInvalidOperator: Boolean get() = body.startsWith('-') || body.startsWith('~')
    val hasEmptyPayload: Boolean get() = unquoted.all { it.isWhitespace() || it == ',' }
    val isInvalidId: Boolean
        get() = (qualifier == "uploaduid" || qualifier == "gid") && (unquoted.toLongOrNull()?.let { it > 0 } != true)

    // Empty namespace covers generic "tag:" and unqualified weak/exact terms.
    val tagMatchTarget: Pair<TagNamespace?, String>?
        get() {
            if (namespace == null && qualifier.isNotEmpty() && qualifier != "tag") return null
            val name = unquoted.trim().replace('_', ' ').lowercase(Locale.ROOT)
            return name.takeIf { it.isNotEmpty() && it.none { c -> c in "\"*%$" } }?.let { namespace to it }
        }

    fun inPlacement(placement: SearchPlacement) = SearchTerm(placement.operator + body)
    fun asTitleKeyword() = if (isKeyword && qualifier.isEmpty()) SearchTerm(placement.operator + "title:" + body) else this

    companion object {
        fun forTag(tag: TagIdentity, placement: SearchPlacement, match: TagMatch): SearchTerm {
            val prefix = if (match.weak) "weak:" else ""
            val value = tag.name + if (match.exact) "$" else ""
            val quoted = if (tag.name.any { it.isWhitespace() || it == ':' || it == ',' }) "\"$value\"" else value
            return SearchTerm("${placement.operator}$prefix${tag.namespace}:$quoted")
        }
    }
}

internal enum class SearchProblem {
    UnclosedQuotes,
    EmptyTerm,
    InvalidOperators,
    InvalidIdentifier,
    TooLong,
    TooManyRequired,
    TooManyExcluded,
    TooManyNotes,
    WeakExcluded,
    WeakQualifier,
    InconsistentWeakOr,
    WeakAndActive,
    FavoritesOnly,
    TemporaryTag,
}

internal data class SearchExpression(val terms: List<SearchTerm> = emptyList()) {
    val text: String get() = terms.joinToString(" ") { it.raw }

    fun validate(allowFavoriteNotes: Boolean = false): List<SearchProblem> = buildList {
        if (text.codePointCount(0, text.length) > MaxSearchCharacters) add(SearchProblem.TooLong)
        if (terms.any { it.hasUnclosedQuote }) add(SearchProblem.UnclosedQuotes)
        if (terms.any { it.hasEmptyPayload }) add(SearchProblem.EmptyTerm)
        if (terms.any { it.hasInvalidOperator }) add(SearchProblem.InvalidOperators)
        if (terms.any { it.isInvalidId }) add(SearchProblem.InvalidIdentifier)
        if (!allowFavoriteNotes && terms.any { it.qualifier == "favnote" }) add(SearchProblem.FavoritesOnly)
        if (terms.any { it.namespace == TagNamespace.Temp }) add(SearchProblem.TemporaryTag)

        val notes = terms.count { it.qualifier in noteQualifiers }
        val required = terms.count { it.placement != SearchPlacement.Excluded && it.qualifier !in uncountedQualifiers }
        val excluded = terms.count {
            it.placement == SearchPlacement.Excluded && it.qualifier != "title" && it.qualifier !in uncountedQualifiers
        }
        if (required > 5) add(SearchProblem.TooManyRequired)
        if (excluded > 10) add(SearchProblem.TooManyExcluded)
        if (notes > 10) add(SearchProblem.TooManyNotes)

        val weak = terms.filter { it.weak }
        if (weak.any { it.placement == SearchPlacement.Excluded }) add(SearchProblem.WeakExcluded)
        if (weak.any { it.namespace == null && it.qualifier.isNotEmpty() && it.qualifier != "tag" }) add(SearchProblem.WeakQualifier)
        val alternatives = terms.filter { it.placement == SearchPlacement.Any }
        if (alternatives.any { it.weak } && alternatives.any { !it.weak }) add(SearchProblem.InconsistentWeakOr)
        val activeTargets = terms.filter { !it.weak && it.placement != SearchPlacement.Excluded }.mapNotNull { it.tagMatchTarget }
        if (weak.any { term ->
                term.tagMatchTarget?.let { (namespace, name) ->
                    activeTargets.any { (otherNamespace, otherName) ->
                        name == otherName && (namespace == null || otherNamespace == null || namespace == otherNamespace)
                    }
                } == true
            }
        ) add(SearchProblem.WeakAndActive)
    }

    fun describe(joiner: String, describeTerm: (SearchTerm) -> String, alternatives: (List<String>) -> String, exclusion: (String) -> String): String {
        val any = terms.filter { it.placement == SearchPlacement.Any }.map(describeTerm)
        var addedAlternatives = false
        return terms.mapNotNull { term ->
            when (term.placement) {
                SearchPlacement.Required -> describeTerm(term)
                SearchPlacement.Excluded -> exclusion(describeTerm(term))
                SearchPlacement.Any -> if (addedAlternatives) {
                    null
                } else {
                    addedAlternatives = true
                    alternatives(any)
                }
            }
        }.joinToString(joiner)
    }

    companion object {
        const val MaxSearchCharacters = 200
        private val noteQualifiers = setOf("comment", "favnote")
        private val uncountedQualifiers = noteQualifiers + setOf("uploader", "uploaduid", "gid")

        // Quoted spans travel with their qualifier and operator. Original terms and
        // their order stay intact so server-side handling of adjacent words survives edits.
        fun parse(text: String): SearchExpression {
            val terms = mutableListOf<SearchTerm>()
            var start = 0
            var quoted = false
            fun addTerm(end: Int) {
                val raw = text.substring(start, end)
                if (raw.any { it != ',' }) terms += SearchTerm(raw)
            }
            text.forEachIndexed { index, char ->
                if (char == '"') quoted = !quoted
                if (char.isWhitespace() && !quoted) {
                    addTerm(index)
                    start = index + 1
                }
            }
            addTerm(text.length)
            return SearchExpression(terms)
        }
    }
}
