// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.search

import com.ehviewer.core.database.model.QuickSearch
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_NORMAL
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_SUBSCRIPTION
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_TAG
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchExpressionTest {
    @Test
    fun aliasesAndSpacesUseAStableEnglishIdentity() {
        val tag = requireNotNull(TagIdentity.from("char:Example_Name"))
        assertEquals(TagIdentity.from("character", "example name"), tag)
        assertNotEquals(TagIdentity.from("artist", "example name"), tag)
        assertEquals("character:\"example name$\"", SearchTerm.forTag(tag, SearchPlacement.Required, TagMatch.Exact).raw)
        assertEquals(tag, SearchExpression.parse("~c:\"example name\"$").terms.single().tag)
        assertEquals(SearchPlacement.Any, SearchExpression.parse("~c:\"example name\"$").terms.single().placement)
    }

    @Test
    fun editsKeepUnrecognizedAndQuotedTermsInPlace() {
        val raw = "title:\"space adventure\" p:example_series -other:music$ ~l:english$ ~lang:japanese$ comment:\"staff note\" x:arch* uploader:\"example user\""
        val parsed = SearchExpression.parse(raw)
        assertEquals(raw, parsed.text)
        assertTrue(parsed.validate().isEmpty())
        assertEquals(TagMatch.Prefix, parsed.terms[1].match)
        assertEquals(null, parsed.terms[6].tag)
        val exact = SearchTerm.forTag(requireNotNull(parsed.terms[1].tag), SearchPlacement.Required, TagMatch.Exact)
        val updated = SearchExpression(parsed.terms.toMutableList().apply { set(1, exact) })
        assertEquals(raw.replace("p:example_series", "parody:\"example series$\""), updated.text)
        assertEquals(updated, SearchExpression.parse(updated.text))

        val name = parsed.describe(
            joiner = " · ",
            describeTerm = { it.tag?.name?.let { name -> if (name == "english") "英语" else name } ?: it.raw },
            alternatives = { it.joinToString(" 或 ", "至少一项（", "）") },
            exclusion = { "排除$it" },
        )
        assertTrue(name.contains("至少一项（英语 或 japanese）") && name.contains("排除music"))
        assertTrue(name.contains("comment:\"staff note\""))
    }

    @Test
    fun limitsCountEachQualifierAndDistinguishWeakTerms() {
        fun query(text: String) = SearchExpression.parse(text).validate()
        assertEquals(emptyList<SearchProblem>(), query("x".repeat(200)))
        assertEquals(listOf(SearchProblem.TooLong), query("x".repeat(201)))
        assertEquals(listOf(SearchProblem.TooManyRequired), query("alfa beta gamma delta epsilon zeta"))
        assertTrue(query("a novel in the old city on paper").isEmpty())
        assertEquals(listOf(SearchProblem.TooManyExcluded), query((1..11).joinToString(" ") { "-o:tag$it" }))
        assertEquals(emptyList<SearchProblem>(), query((1..11).joinToString(" ") { "-title:$it" }))
        assertEquals(listOf(SearchProblem.TooManyNotes), query((1..10).joinToString(" ") { "comment:t$it" } + " -comment:last"))
        assertEquals(emptyList<SearchProblem>(), query((1..9).joinToString(" ") { "gid:$it -uploaduid:$it" }))
        assertEquals(listOf(SearchProblem.FavoritesOnly), query("favnote:sample"))
        assertTrue(SearchExpression.parse("favnote:sample").validate(allowFavoriteNotes = true).isEmpty())
        assertEquals(listOf(SearchProblem.WeakExcluded), query("-weak:f:example$"))
        assertEquals(listOf(SearchProblem.WeakQualifier), query("weak:title:example"))
        assertEquals(listOf(SearchProblem.InconsistentWeakOr), query("~weak:f:example$ ~o:sample$"))
        assertEquals(listOf(SearchProblem.WeakAndActive), query("weak:f:example$ female:example$"))
        assertEquals(listOf(SearchProblem.InvalidOperators), query("~-o:sample$"))
        assertEquals(listOf(SearchProblem.UnclosedQuotes), query("o:\"one two"))
        assertEquals(listOf(SearchProblem.InvalidIdentifier), query("gid:wrong"))
    }

    @Test
    fun tagScenesConvertToTextSearchAndLanguageIsCaptured() {
        val tag = ListUrlBuilder(MODE_TAG, mKeyword = "other:full color")
        val query = SearchExpression.parse(tag.expression().text + " ~l:english$ ~lang:japanese$")
        val search = combinedSearchUrl(tag.mode, query, 17, 1, 2, 4, 10, 80)
        assertEquals(MODE_NORMAL, search.mode)
        assertEquals("other:\"full color$\" ~l:english$ ~lang:japanese$", search.keyword)
        assertEquals(listOf(17, 2, 4, 10, 80), listOf(search.category, search.advanceSearch, search.minRating, search.pageFrom, search.pageTo))
        val watched = combinedSearchUrl(MODE_SUBSCRIPTION, SearchExpression.parse("title:sample"), 17, 1, 0, 0, 0, 0)
        assertEquals(MODE_SUBSCRIPTION, watched.mode)
        assertEquals("language:chinese$ title:sample", watched.keyword)
        val gid = combinedSearchUrl(MODE_NORMAL, SearchExpression.parse("gid:42"), 0, 1, 0, 0, 0, 0)
        assertEquals("gid:42", gid.keyword)
    }

    @Test
    fun quickSearchNamesKeepCustomNamesCursorsAndLiteralAtSigns() {
        val original = QuickSearch(id = 5, name = "我的收藏@42", keyword = "l:chinese$", position = 2)
        assertEquals(SearchName("我的收藏", 42), SearchName.of(original))
        assertFalse(original.automaticallyNamed)
        assertTrue(original.sameSearch(original.copy(keyword = "language:chinese$")))
        assertFalse(original.sameSearch(original.copy(minRating = 3)))
        val literal = SearchName("title:user@42", null)
        assertEquals("title:user@42@", literal.stored())
        assertEquals(literal, SearchName.of(QuickSearch(name = literal.stored(), keyword = literal.label)))
    }

    @Test
    fun backupMergeDeduplicatesAndRequiresAChoiceForDifferentCriteria() = runBlocking {
        val original = QuickSearch(id = 5, name = "Saved@42", keyword = "l:chinese$", position = 7)
        val importedCopy = original.copy(id = 10, position = 0, keyword = "language:chinese$")
        val changed = importedCopy.copy(minRating = 3)
        var conflicts = 0
        val merged = mergeSearchBackups(listOf(original), listOf(importedCopy, changed, changed)) {
            conflicts++
            assertEquals(original, it.existing)
            QuickSearchConflictChoice.Replace
        }
        assertEquals(1, conflicts)
        assertEquals(listOf(changed.copy(id = original.id, position = 7)), merged)
        val kept = mergeSearchBackups(listOf(original), listOf(changed)) { QuickSearchConflictChoice.KeepBoth }
        assertEquals(listOf(original, changed.copy(id = null, position = 8)), kept)
        assertEquals(listOf(original), mergeSearchBackups(listOf(original), listOf(changed)) { QuickSearchConflictChoice.KeepExisting })
    }

    @Test
    fun tagFilesValidateFormatAndReportDuplicatesAndDamagedRows() {
        val tag = FavoriteTag(requireNotNull(TagIdentity.from("c:example")), 42)
        val other = FavoriteTag(requireNotNull(TagIdentity.from("a:example")), 51)
        val encoded = FavoriteTagFile.encode(listOf(tag, tag, other))
        assertEquals(listOf(tag, tag, other) to 0, FavoriteTagFile.decode(encoded))
        val partlyDamaged = encoded.replace("\"addedAtMillis\":51", "\"addedAtMillis\":-5")
        val (valid, invalid) = FavoriteTagFile.decode(partlyDamaged)
        assertEquals(1, invalid)
        assertEquals(listOf(tag) to TagImportResult(0, 2, 1), FavoriteTagFile.merge(listOf(tag), valid, invalid))
        assertThrows(IllegalArgumentException::class.java) { FavoriteTagFile.decode(encoded.replace("\"complete\":true", "\"complete\":false")) }
    }
}
