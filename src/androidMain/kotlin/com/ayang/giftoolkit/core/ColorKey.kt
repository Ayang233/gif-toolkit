package com.ayang.giftoolkit.core

import android.graphics.Bitmap
import kotlin.math.sqrt

/** Chroma-key: make pixels close to a picked color transparent. */
object ColorKey {

    fun apply(src: Bitmap, color: Int, tolerancePct: Int): Bitmap {
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)

        val tr = (color shr 16) and 0xFF
        val tg = (color shr 8) and 0xFF
        val tb = color and 0xFF
        val threshold = tolerancePct.coerceIn(1, 100) * 2.2f
        val edge = 36f

        for (i in px.indices) {
            val c = px[i]
            if ((c ushr 24) == 0) continue
            val dr = ((c shr 16) and 0xFF) - tr
            val dg = ((c shr 8) and 0xFF) - tg
            val db = (c and 0xFF) - tb
            val dist = sqrt((dr * dr + dg * dg + db * db).toFloat())
            val alpha = when {
                dist <= threshold -> 0
                dist >= threshold + edge -> 255
                else -> (((dist - threshold) / edge) * 255f).toInt()
            }
            px[i] = if (alpha < 128) 0 else (0xFF shl 24) or (c and 0x00FFFFFF)
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}
