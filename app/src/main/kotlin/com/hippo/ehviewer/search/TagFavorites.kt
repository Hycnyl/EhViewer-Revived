// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.search

import com.ehviewer.core.preferences.DataStorePreferences
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal data class FavoriteTag(val tag: TagIdentity, val addedAtMillis: Long)

@Serializable
private data class TagFavoriteBackup(
    val format: String = FavoriteTagFile.FORMAT,
    val version: Int = FavoriteTagFile.VERSION,
    val tags: List<FavoriteTag>,
    val complete: Boolean = true,
)

internal data class TagImportResult(val added: Int, val existing: Int, val invalid: Int)

internal object FavoriteTagFile {
    const val FORMAT = "ehviewer-revived-tag-favorites"
    const val VERSION = 1
    private val json = Json { encodeDefaults = true }

    fun encode(tags: List<FavoriteTag>): String = json.encodeToString(TagFavoriteBackup(tags = tags))

    fun decode(text: String): Pair<List<FavoriteTag>, Int> {
        val root = json.parseToJsonElement(text) as? JsonObject ?: error("Expected tag favorites object")
        require(root["format"] == JsonPrimitive(FORMAT) && root["version"] == JsonPrimitive(VERSION) && root["complete"] == JsonPrimitive(true))
        val records = root["tags"] as? JsonArray ?: error("Expected tag favorites array")
        val valid = records.mapNotNull { element ->
            runCatching {
                val record = json.decodeFromJsonElement(FavoriteTag.serializer(), element)
                val tag = requireNotNull(TagIdentity.from(record.tag.namespace, record.tag.name))
                require(record.addedAtMillis >= 0)
                record.copy(tag = tag)
            }.getOrNull()
        }
        return valid to records.size - valid.size
    }

    fun merge(current: List<FavoriteTag>, incoming: List<FavoriteTag>, invalid: Int): Pair<List<FavoriteTag>, TagImportResult> {
        val merged = current.associateByTo(linkedMapOf()) { it.tag }
        val existing = incoming.count { merged.putIfAbsent(it.tag, it) != null }
        return merged.values.toList() to TagImportResult(incoming.size - existing, existing, invalid)
    }
}

internal object TagFavorites : DataStorePreferences("tag_favorites") {
    private val document = stringPref("tag_favorites_document", "")

    val entries = document.valueFlow().map(::readStored)

    private fun readStored(text: String) = if (text.isBlank()) emptyList() else FavoriteTagFile.decode(text).first.distinctBy(FavoriteTag::tag)

    suspend fun toggle(tag: TagIdentity): Boolean {
        var added = false
        withMutableSnapshot { snapshot ->
            val current = readStored(snapshot[document])
            added = current.none { it.tag == tag }
            val updated = if (added) current + FavoriteTag(tag, System.currentTimeMillis()) else current.filterNot { it.tag == tag }
            snapshot[document] = FavoriteTagFile.encode(updated)
        }
        return added
    }

    suspend fun remove(tags: Set<TagIdentity>) {
        withMutableSnapshot { snapshot ->
            snapshot[document] = FavoriteTagFile.encode(readStored(snapshot[document]).filterNot { it.tag in tags })
        }
    }

    suspend fun export(): String = FavoriteTagFile.encode(readStored(snapshot()[document]))

    suspend fun importDocument(text: String): TagImportResult {
        val (incoming, invalid) = FavoriteTagFile.decode(text)
        var result = TagImportResult(0, 0, invalid)
        withMutableSnapshot { snapshot ->
            val (items, stats) = FavoriteTagFile.merge(readStored(snapshot[document]), incoming, invalid)
            if (stats.added > 0) snapshot[document] = FavoriteTagFile.encode(items)
            result = stats
        }
        return result
    }
}
