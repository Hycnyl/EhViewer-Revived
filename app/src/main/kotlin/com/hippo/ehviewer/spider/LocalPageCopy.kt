// Copyright 2026 Hycnyl. SPDX-License-Identifier: GPL-3.0-or-later
package com.hippo.ehviewer.spider

import android.os.ParcelFileDescriptor
import com.ehviewer.core.files.SystemFileSystem
import com.ehviewer.core.files.delete
import com.ehviewer.core.files.exists
import com.ehviewer.core.files.isFile
import com.ehviewer.core.files.moveTo
import com.ehviewer.core.files.openFileDescriptor
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import moe.tarsin.coroutines.runSuspendCatching
import okio.Path

internal inline fun <T> Path.withRescueInput(block: (java.io.InputStream) -> T): T = ParcelFileDescriptor.AutoCloseInputStream(openFileDescriptor("r")).use(block)

// Write .tmp, close, re-read and verify CRC/length, then publish. The marker also protects
// readers after a crash on SAF providers which cannot rename. SpiderDen ignores marked pages.
internal fun commitRescueFile(source: File, destination: Path, checkpoint: () -> Unit) = commitLocalFile({ source.inputStream() }, destination, checkpoint)

// Used only after an explicit export overwrite choice. Retain the previous file until
// publication succeeds, and restore it if cancellation or an I/O error interrupts the write.
internal fun overwriteRescueFile(source: File, destination: Path, checkpoint: () -> Unit) {
    val previous = destination.parent!! / ".${destination.name}.${UUID.randomUUID()}.previous"
    val marker = destination.parent!! / rescueMarkerName(destination.name)
    val wasIncomplete = marker.exists()
    fun copyExisting(from: Path, to: Path) {
        if (from.withRescueInput { it.read() } == -1) {
            ParcelFileDescriptor.AutoCloseOutputStream(to.openFileDescriptor("wt")).use {}
        } else {
            copyLocalPageSafely(from, to)
        }
    }
    var replacing = false
    var keepPrevious = false
    try {
        copyExisting(destination, previous)
        checkpoint()
        replacing = true
        destination.delete()
        marker.delete()
        commitRescueFile(source, destination, checkpoint)
    } catch (error: Throwable) {
        if (replacing) {
            try {
                destination.delete()
                marker.delete()
                copyExisting(previous, destination)
                if (wasIncomplete) ParcelFileDescriptor.AutoCloseOutputStream(marker.openFileDescriptor("wt")).use { it.write(destination.name.toByteArray()) }
            } catch (restoreError: Throwable) {
                keepPrevious = true
                throw IOException("Previous file retained at $previous after restore failed", restoreError).apply { addSuppressed(error) }
            }
        }
        throw error
    } finally {
        if (!keepPrevious) runCatching { previous.delete() }
    }
}

internal fun copyLocalPageSafely(source: Path, destination: Path) = commitLocalFile(
    { ParcelFileDescriptor.AutoCloseInputStream(source.openFileDescriptor("r")) },
    destination,
    {},
)

internal fun commitLocalFile(openSource: () -> InputStream, destination: Path, checkpoint: () -> Unit) {
    val parent = destination.parent!!
    commitLocalFile(openSource, destination, parent / rescueMarkerName(destination.name), parent / ".${destination.name}.${UUID.randomUUID()}.tmp", checkpoint)
}

private fun downloadMarker(name: String) = ".$name.download-incomplete"

internal fun Path.hasIncompleteDownloadArchive() = (parent!! / downloadMarker(name)).exists()

internal fun Path.completedArchive(name: String): Path? = (this / name).takeIf { it.isFile && !(this / downloadMarker(name)).exists() && !(this / rescueMarkerName(name)).exists() }

internal fun prepareDownloadArchive(destination: Path): Path {
    val parent = destination.parent!!
    val marker = parent / downloadMarker(destination.name)
    check(!(parent / rescueMarkerName(destination.name)).exists()) { "A cache export is incomplete" }
    check(marker.isFile || !destination.exists()) { "Destination already exists: $destination" }
    val temporary = parent / ".${destination.name}.download-staging"
    if (temporary.isFile) SystemFileSystem.delete(temporary)
    check(!temporary.exists()) { "Staging path is occupied: $temporary" }
    return temporary
}

internal fun publishDownloadArchive(temporary: Path, destination: Path, expected: RescueFingerprint, checkpoint: () -> Unit) {
    val parent = destination.parent!!
    val marker = parent / downloadMarker(destination.name)
    check(temporary.withRescueInput { readRescueBytes(it, checkpoint = checkpoint) } == expected) { "Staging verification failed" }
    check(!(parent / rescueMarkerName(destination.name)).exists()) { "A cache export is incomplete" }
    // A verified replacement is available before recovering a previous incomplete publication.
    if (marker.isFile) {
        if (destination.exists()) {
            check(destination.isFile)
            SystemFileSystem.delete(destination)
        }
        SystemFileSystem.delete(marker)
    }
    publishStagedFile(temporary, destination, marker, expected, checkpoint)
}

private fun commitLocalFile(openSource: () -> InputStream, destination: Path, marker: Path, temporary: Path, checkpoint: () -> Unit) {
    check(!destination.exists() && !marker.exists() && !temporary.exists()) { "Destination already exists: $destination" }
    val expected = openSource().use { readRescueBytes(it, checkpoint = checkpoint) }
    try {
        openSource().use { input ->
            ParcelFileDescriptor.AutoCloseOutputStream(temporary.openFileDescriptor("wt")).use { readRescueBytes(input, it, checkpoint = checkpoint) }
        }
        check(temporary.withRescueInput { readRescueBytes(it, checkpoint = checkpoint) } == expected) { "Staging verification failed" }
        publishStagedFile(temporary, destination, marker, expected, checkpoint)
    } finally {
        runCatching { SystemFileSystem.delete(temporary) }
    }
}

private fun publishStagedFile(temporary: Path, destination: Path, marker: Path, expected: RescueFingerprint, checkpoint: () -> Unit) {
    var ownsDestination = false
    var ownsMarker = false
    var committed = false
    try {
        checkpoint()
        check(!destination.exists() && !marker.exists()) { "Destination appeared during export: $destination" }
        ownsMarker = true
        ParcelFileDescriptor.AutoCloseOutputStream(marker.openFileDescriptor("wt")).use { it.write(destination.name.toByteArray()) }
        check(!destination.exists()) { "Destination appeared during export: $destination" }
        ownsDestination = true
        runSuspendCatching { temporary moveTo destination }
        if (!destination.exists()) {
            temporary.withRescueInput { input ->
                ParcelFileDescriptor.AutoCloseOutputStream(destination.openFileDescriptor("wt")).use { readRescueBytes(input, it, checkpoint = checkpoint) }
            }
        }
        check(destination.withRescueInput { readRescueBytes(it, checkpoint = checkpoint) } == expected) { "Destination verification failed" }
        marker.delete()
        check(!marker.exists()) { "Cannot remove incomplete marker: $marker" }
        committed = true
    } finally {
        if (!committed && ownsDestination) runCatching { SystemFileSystem.delete(destination) }
        // If cleanup cannot remove the incomplete destination, retain its reader guard.
        if (!committed && ownsMarker && (!ownsDestination || !destination.exists())) runCatching { SystemFileSystem.delete(marker) }
    }
}
