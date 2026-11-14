package com.ayang.giftoolkit.core

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Lightweight video stabilization without FFmpeg:
 * estimate frame-to-frame global translation by block matching on downscaled
 * grayscale frames, smooth the accumulated motion path, then crop each frame
 * at the compensated position so residual shake is removed.
 */
object Stabilizer {

    private const val S = 64
    private const val RANGE = 6
    private const val STEP = 3
    private const val WINDOW = 5

    fun stabilize(frames: List<GifFrame>): List<GifFrame> {
        if (frames.size < 3) return frames
        val gray = frames.map { gray(it.bitmap) }
        val n = frames.size
        val px = IntArray(n)
        val py = IntArray(n)
        for (i in 1 until n) {
            val (dx, dy) = shift(gray[i - 1], gray[i])
            px[i] = px[i - 1] + dx
            py[i] = py[i - 1] + dy
        }
        val sx = smooth(px)
        val sy = smooth(py)
        val w = frames[0].bitmap.width
        val h = frames[0].bitmap.height
        val cw = (w * 0.9f).toInt().coerceAtLeast(8)
        val ch = (h * 0.9f).toInt().coerceAtLeast(8)
        return frames.mapIndexed { i, f ->
            val bmp = f.bitmap
            if (bmp.width != w || bmp.height != h) return@mapIndexed f
            val ox = sx[i] - px[i]
            val oy = sy[i] - py[i]
            val left = ((w - cw) / 2 + ox).coerceIn(0, w - cw)
            val top = ((h - ch) / 2 + oy).coerceIn(0, h - ch)
            GifFrame(Bitmap.createBitmap(bmp, left, top, cw, ch), f.delayMs)
        }
    }

    private fun gray(bmp: Bitmap): IntArray {
        val small = Bitmap.createScaledBitmap(bmp, S, S, true)
        val px = IntArray(S * S)
        small.getPixels(px, 0, S, 0, 0, S, S)
        val out = IntArray(S * S)
        for (i in px.indices) {
            val c = px[i]
            out[i] = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
        }
        if (small != bmp) small.recycle()
        return out
    }

    private fun shift(a: IntArray, b: IntArray): Pair<Int, Int> {
        var best = Long.MAX_VALUE
        var bx = 0
        var by = 0
        for (oy in -RANGE..RANGE) {
            for (ox in -RANGE..RANGE) {
                var sad = 0L
                var y = RANGE + 2
                while (y < S - RANGE - 2) {
                    var x = RANGE + 2
                    while (x < S - RANGE - 2) {
                        sad += abs(a[y * S + x] - b[(y + oy) * S + x + ox])
                        x += STEP
                    }
                    y += STEP
                }
                if (sad < best) { best = sad; bx = ox; by = oy }
            }
        }
        return bx to by
    }

    private fun smooth(path: IntArray): IntArray {
        val out = IntArray(path.size)
        for (i in path.indices) {
            var sum = 0
            var count = 0
            for (k in i - WINDOW / 2..i + WINDOW / 2) {
                if (k in path.indices) { sum += path[k]; count++ }
            }
            out[i] = sum / count
        }
        return out
    }
}
