// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.coil

import coil3.disk.DiskCache
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.util.withIOContext
import com.hippo.ehviewer.EhApplication.Companion.imageCache
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.client.getImageKey
import com.hippo.ehviewer.client.getThumbKey
import com.hippo.ehviewer.spider.UNKNOWN_RESCUE_SCAN
import com.hippo.ehviewer.spider.readCachedSpiderPageCounts
import com.hippo.ehviewer.spider.rescuePageCount
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import okio.Path

internal data class ReadCachedGallery(
    val gid: Long,
    val info: GalleryInfo?,
    val preview: Path,
    val pageLimit: Int,
    val cachedPages: Int,
    val bytes: Long,
    val latestWriteMillis: Long,
)

internal suspend fun listReadCachedGalleries(): List<ReadCachedGallery> = withIOContext {
    if (imageCache.size == 0L) return@withIOContext emptyList()
    val context = currentCoroutineContext()
    val checkpoint = { context.ensureActive() }
    val pageCounts = readCachedSpiderPageCounts(checkpoint).toMutableMap()
    EhDB.getReadCacheHistoryGids().forEach { pageCounts.putIfAbsent(it, 0) }
    findReadCachedGalleries(imageCache, EhDB.getReadCacheGalleryInfo(), pageCounts, checkpoint)
}

internal fun findReadCachedGalleries(
    cache: DiskCache,
    galleries: List<GalleryInfo>,
    pageCounts: Map<Long, Int>,
    checkpoint: () -> Unit = {},
): List<ReadCachedGallery> {
    // Coil hashes the original key, so match known gallery/page keys against its existing
    // filenames. Inspection leaves the journal's access order alone.
    val files = cache.existingDataFiles().associateBy { it.name.substringBefore('.').decodeHex() }
    if (files.isEmpty()) return emptyList()
    val digest = MessageDigest.getInstance("SHA-256")
    fun dataFile(key: String): Path? = files[digest.digest(key.toByteArray()).toByteString()]
    val byGid = galleries.associateBy { it.gid }
    return (pageCounts.keys + byGid.keys).mapNotNull { gid ->
        checkpoint()
        if (gid <= 0) return@mapNotNull null
        val info = byGid[gid]
        val limit = rescuePageCount(info?.pages ?: 0, pageCounts[gid] ?: 0)
            .takeIf { it > 0 } ?: UNKNOWN_RESCUE_SCAN.takeIf { gid in pageCounts } ?: return@mapNotNull null
        var firstPage: Path? = null
        var count = 0
        var bytes = 0L
        var latest = 0L
        repeat(limit) { index ->
            if (index % 128 == 0) checkpoint()
            val data = dataFile(getImageKey(gid, index)) ?: return@repeat
            val image = cache.fileSystem.metadataOrNull(data) ?: return@repeat
            val meta = cache.fileSystem.metadataOrNull(data.parent!! / (data.name.removeSuffix(".1") + ".0"))
            val size = image.size ?: 0L
            if (!image.isRegularFile || size <= 0 || meta?.isRegularFile != true) return@repeat
            if (firstPage == null) firstPage = data
            count++
            bytes += size
            latest = maxOf(latest, image.lastModifiedAtMillis ?: 0L, meta.lastModifiedAtMillis ?: 0L)
        }
        val pagePreview = firstPage ?: return@mapNotNull null
        val cover = info?.thumbKey?.let { dataFile(it) ?: dataFile(getThumbKey(it)) }
        ReadCachedGallery(gid, info, cover ?: pagePreview, limit, count, bytes, latest)
    }.sortedWith(compareByDescending<ReadCachedGallery> { it.latestWriteMillis }.thenByDescending { it.gid })
}
