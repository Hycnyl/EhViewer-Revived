package com.hippo.ehviewer.image

import android.graphics.BitmapFactory
import java.io.DataInputStream
import java.io.File

internal data class ImageDimensions(val width: Int, val height: Int)

/** Dimensions of the encoded file, before Coil sampling, cropping or orientation transforms. */
internal fun readImageDimensions(file: File): ImageDimensions? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        return ImageDimensions(bounds.outWidth, bounds.outHeight)
    }
    // The reader also supports animated WebP through its native decoder on older Android.
    // VP8X stores the full canvas size in its fixed header, without decoding any frames.
    val header = ByteArray(30)
    DataInputStream(file.inputStream()).use { it.readFully(header) }
    fun tag(offset: Int) = String(header, offset, 4, Charsets.US_ASCII)
    if (tag(0) != "RIFF" || tag(8) != "WEBP" || tag(12) != "VP8X") return null
    if (header[16] != 10.toByte() || (17..19).any { header[it] != 0.toByte() }) return null
    fun size(offset: Int) = 1 + (header[offset].toInt() and 0xff) +
        ((header[offset + 1].toInt() and 0xff) shl 8) +
        ((header[offset + 2].toInt() and 0xff) shl 16)
    return ImageDimensions(size(24), size(27))
}
