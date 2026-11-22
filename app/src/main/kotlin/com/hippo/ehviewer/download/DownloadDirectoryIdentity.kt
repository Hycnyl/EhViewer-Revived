// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.download

import com.ehviewer.core.files.find
import com.hippo.ehviewer.client.parser.GalleryDetailUrlParser
import com.hippo.ehviewer.spider.COMIC_INFO_FILE
import com.hippo.ehviewer.spider.SpiderQueen.Companion.SPIDER_INFO_FILENAME
import com.hippo.ehviewer.spider.readComicInfo
import com.hippo.ehviewer.spider.readCompatFromPath
import okio.Path

internal suspend fun readDirectoryIdentity(directory: Path) = readDirectoryIdentity(directory.find(SPIDER_INFO_FILENAME), directory.find(COMIC_INFO_FILE))

internal suspend fun readDirectoryIdentity(spiderInfo: Path?, comicInfo: Path?): GalleryDetailUrlParser.Result? {
    val state = spiderInfo?.let { file ->
        readCompatFromPath(file)?.run { GalleryDetailUrlParser.Result(gid, token) }
    }
    val comic = comicInfo?.let { file ->
        readComicInfo(file)?.run { GalleryDetailUrlParser.parse(web) }
    }
    if (state != null && comic != null && (state.gid != comic.gid || state.token != comic.token)) return null
    return state ?: comic
}
