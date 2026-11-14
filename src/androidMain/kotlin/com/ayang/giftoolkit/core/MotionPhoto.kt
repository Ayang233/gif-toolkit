package com.ayang.giftoolkit.core

import android.content.Context
import android.net.Uri
import java.io.File

/** Extracts the embedded MP4 from a Motion Photo / MVIMG JPEG. */
object MotionPhoto {

    fun extractVideo(context: Context, uri: Uri): File? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            var box = -1
            var i = 4
            while (i < bytes.size - 8) {
                if (bytes[i] == 0x66.toByte() && bytes[i + 1] == 0x74.toByte() &&
                    bytes[i + 2] == 0x79.toByte() && bytes[i + 3] == 0x70.toByte()
                ) {
                    box = i - 4
                    break
                }
                i++
            }
            if (box < 0) return null
            val out = File(context.cacheDir, "mvimg_${System.currentTimeMillis()}.mp4")
            out.writeBytes(bytes.copyOfRange(box, bytes.size))
            if (out.length() < 1024) return null
            out
        } catch (e: Exception) {
            null
        }
    }
}
