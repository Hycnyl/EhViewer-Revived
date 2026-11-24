package com.hippo.ehviewer.coil

import coil3.disk.DiskCache
import com.hippo.ehviewer.client.getImageKey
import okio.Path

inline fun DiskCache.edit(key: String, crossinline block: DiskCache.Editor.() -> Unit): Boolean {
    val editor = openEditor(key) ?: return false
    editor.runCatching {
        block(this)
    }.onFailure {
        editor.abort()
        throw it
    }.onSuccess {
        editor.commit()
    }
    return true
}

suspend inline fun DiskCache.suspendEdit(key: String, crossinline block: suspend DiskCache.Editor.() -> Unit): Boolean {
    val editor = openEditor(key) ?: return false
    editor.runCatching {
        block(this)
    }.onFailure {
        editor.abort()
        throw it
    }.onSuccess {
        editor.commit()
    }
    return true
}

inline fun <T> DiskCache.read(key: String, block: DiskCache.Snapshot.() -> T): T? = openSnapshot(key)?.use(block)

private val cacheDataFileName = Regex("[a-f0-9]{64}\\.1")

internal fun DiskCache.existingDataFiles(): List<Path> =
    if (size == 0L) emptyList() else fileSystem.list(directory).filter { cacheDataFileName.matches(it.name) }

internal fun DiskCache.clearGalleryImages(gid: Long, pageIndices: IntRange): Int = pageIndices.count { index ->
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
    remove(getImageKey(gid, index))
}
