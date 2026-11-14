package com.ayang.giftoolkit.core

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

data class GifFrame(val bitmap: Bitmap, val delayMs: Int)

/** Minimal, dependency-free GIF89a decoder. Produces fully composed frames. */
object GifDecoder {

    fun decode(input: InputStream): List<GifFrame> {
        val d = input.readBytes()
        if (d.size < 13) return emptyList()
        var p = 6
        fun u8(): Int = if (p < d.size) d[p++].toInt() and 0xFF else 0
        fun u16(): Int = u8() or (u8() shl 8)
        fun blocks(): ByteArray {
            val b = ByteArrayOutputStream()
            while (p < d.size) {
                val n = u8()
                if (n == 0) break
                if (p + n > d.size) break
                b.write(d, p, n)
                p += n
            }
            return b.toByteArray()
        }
        fun table(size: Int): IntArray {
            val t = IntArray(size)
            for (i in 0 until size) {
                val r = u8(); val g = u8(); val b = u8()
                t[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            return t
        }

        val w = u16(); val h = u16()
        val packed = u8(); u8(); u8()
        if (w <= 0 || h <= 0 || w > 4096 || h > 4096) return emptyList()
        val gct = if (packed and 0x80 != 0) table(1 shl ((packed and 7) + 1)) else IntArray(256)

        val frames = ArrayList<GifFrame>()
        val canvas = IntArray(w * h)
        var backup: IntArray? = null
        var pendingDisposal = 0
        var pendingRect: IntArray? = null
        var disposal = 0
        var transparent = -1
        var delay = 10
        var guard = 0

        while (p < d.size && guard++ < 200000) {
            when (u8()) {
                0x21 -> when (u8()) {
                    0xF9 -> {
                        val n = u8()
                        if (n >= 4) {
                            val g = u8()
                            delay = u16()
                            val ti = u8()
                            transparent = if (g and 1 != 0) ti else -1
                            disposal = (g shr 2) and 7
                            repeat(n - 4) { u8() }
                        }
                        u8()
                    }
                    else -> blocks()
                }
                0x2C -> {
                    val left = u16(); val top = u16(); val iw = u16(); val ih = u16(); val ip = u8()
                    if (iw <= 0 || ih <= 0 || iw > w || ih > h) break
                    val ct = if (ip and 0x80 != 0) table(1 shl ((ip and 7) + 1)) else gct
                    val minCode = u8()
                    val idx = lzwDecode(blocks(), minCode, iw * ih)

                    pendingRect?.let { r ->
                        when (pendingDisposal) {
                            2 -> {
                                val y0 = r[1].coerceAtLeast(0); val y1 = (r[1] + r[3]).coerceAtMost(h)
                                val x0 = r[0].coerceAtLeast(0); val x1 = (r[0] + r[2]).coerceAtMost(w)
                                for (y in y0 until y1) {
                                    val off = y * w
                                    for (x in x0 until x1) canvas[off + x] = 0
                                }
                            }
                            3 -> backup?.copyInto(canvas)
                        }
                    }
                    backup = canvas.copyOf()

                    val rows = if (ip and 0x40 != 0) interlaceRows(ih) else IntArray(ih) { it }
                    for (k in 0 until ih) {
                        val src = k * iw
                        val y = top + rows[k]
                        if (y < 0 || y >= h) continue
                        val dst = y * w
                        for (x in 0 until iw) {
                            if (src + x >= idx.size) break
                            val ci = idx[src + x].toInt() and 0xFF
                            if (ci == transparent || ci >= ct.size) continue
                            val px = left + x
                            if (px in 0 until w) canvas[dst + px] = ct[ci]
                        }
                    }
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.setPixels(canvas, 0, w, 0, 0, w, h)
                    frames.add(GifFrame(bmp, delay.coerceAtLeast(2) * 10))

                    pendingDisposal = disposal
                    pendingRect = intArrayOf(left, top, iw, ih)
                    disposal = 0; transparent = -1; delay = 10
                }
                0x3B -> break
            }
        }
        return frames
    }

    private fun interlaceRows(h: Int): IntArray {
        val out = ArrayList<Int>(h)
        for (start in intArrayOf(0, 4, 2, 1)) {
            val step = when (start) { 0, 4 -> 8; 2 -> 4; else -> 2 }
            var y = start
            while (y < h) { out.add(y); y += step }
        }
        return out.take(h).toIntArray()
    }

    private fun lzwDecode(data: ByteArray, minCodeSize: Int, expected: Int): ByteArray {
        if (data.isEmpty() || minCodeSize !in 2..8 || expected <= 0) return ByteArray(0)
        val clear = 1 shl minCodeSize
        val end = clear + 1
        var codeSize = minCodeSize + 1
        var dict = ArrayList<ByteArray>(4096)
        fun reset() {
            dict = ArrayList(4096)
            for (i in 0 until clear) dict.add(byteArrayOf(i.toByte()))
            dict.add(ByteArray(0)); dict.add(ByteArray(0))
            codeSize = minCodeSize + 1
        }
        reset()
        val out = ByteArray(expected)
        var pos = 0
        var bit = 0
        var prev: ByteArray? = null
        while (bit + codeSize <= data.size * 8) {
            var code = 0
            for (b in 0 until codeSize) {
                val byte = data[(bit ushr 3)].toInt() and 0xFF
                code = code or (((byte ushr (bit and 7)) and 1) shl b)
                bit++
            }
            if (code == clear) { reset(); prev = null; continue }
            if (code == end) break
            val entry: ByteArray = when {
                code < dict.size -> dict[code]
                code == dict.size && prev != null && prev.isNotEmpty() -> prev + prev[0]
                else -> break
            }
            if (entry.isEmpty()) break
            for (v in entry) {
                if (pos >= expected) break
                out[pos++] = v
            }
            if (prev != null && dict.size < 4096) {
                dict.add(prev + entry[0])
                if (dict.size >= (1 shl codeSize) && codeSize < 12) codeSize++
            }
            prev = entry
            if (pos >= expected) break
        }
        return if (pos == expected) out else out.copyOf(pos)
    }
}

/** GIF89a encoder with a median-cut global palette and LZW compression. */
object GifEncoder {

    fun encode(frames: List<GifFrame>, out: OutputStream, colorCount: Int = 255) {
        require(frames.isNotEmpty())
        val w = frames[0].bitmap.width
        val h = frames[0].bitmap.height
        val n = colorCount.coerceIn(2, 255)

        val palette = IntArray(256)
        val colors = buildPalette(frames, n)
        colors.copyInto(palette, 1)
        val lut = buildLookup(palette, n + 1)

        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        u16(out, w); u16(out, h)
        out.write(0xF7)
        out.write(0); out.write(0)
        for (c in palette) {
            out.write((c shr 16) and 0xFF); out.write((c shr 8) and 0xFF); out.write(c and 0xFF)
        }
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))

        var previous: IntArray? = null
        for (frame in frames) {
            val bmp = if (frame.bitmap.width == w && frame.bitmap.height == h) frame.bitmap
            else Bitmap.createScaledBitmap(frame.bitmap, w, h, true)
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)

            val hasTransparent = pixels.any { (it ushr 24) < 100 }
            val indices = quantize(pixels, lut)

            // 有透明像素时无法用 dispose=1 擦除旧内容，退回整帧 + 清屏；
            // 全不透明时只写与上一帧的差异矩形，这是 GIF 体积的主要来源。
            if (hasTransparent || previous == null) {
                writeFrame(out, 0, 0, w, h, indices, frame.delayMs / 10, disposal = 2)
            } else {
                var minX = w; var minY = h; var maxX = -1; var maxY = -1
                for (y in 0 until h) {
                    val row = y * w
                    for (x in 0 until w) {
                        if (pixels[row + x] != previous[row + x]) {
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y
                        }
                    }
                }
                if (maxX < 0) {
                    // 与上一帧完全一致：写一个 1×1 的全透明子帧，只占几个字节
                    writeFrame(out, 0, 0, 1, 1, ByteArray(1), frame.delayMs / 10, disposal = 1)
                } else {
                    val fw = maxX - minX + 1
                    val fh = maxY - minY + 1
                    val sub = ByteArray(fw * fh)
                    for (y in 0 until fh) {
                        val srcRow = (minY + y) * w
                        val dstRow = y * fw
                        for (x in 0 until fw) {
                            val i = srcRow + minX + x
                            sub[dstRow + x] = if (pixels[i] == previous[i]) 0 else indices[i]
                        }
                    }
                    writeFrame(out, minX, minY, fw, fh, sub, frame.delayMs / 10, disposal = 1)
                }
            }
            previous = pixels
        }
        out.write(0x3B)
        out.flush()
    }

    /** 写一帧：图形控制扩展（含透明索引 0）+ 图像描述符 + 数据块。 */
    private fun writeFrame(
        out: OutputStream,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        indices: ByteArray,
        delayCs: Int,
        disposal: Int,
    ) {
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, (disposal shl 2 or 1).toByte()))
        u16(out, delayCs.coerceIn(2, 65535))
        out.write(0)
        out.write(0)
        out.write(0x2C)
        u16(out, left); u16(out, top); u16(out, width); u16(out, height)
        out.write(0)
        out.write(8)
        val writer = BlockWriter(out)
        lzwEncode(indices, writer)
        writer.finish()
    }

    private fun u16(out: OutputStream, v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

    private fun buildPalette(frames: List<GifFrame>, n: Int): IntArray {
        val pixels = ArrayList<Int>(60000)
        var total = 0
        for (f in frames) total += f.bitmap.width * f.bitmap.height
        val stride = (total / 60000).coerceAtLeast(1)
        var counter = 0
        for (f in frames) {
            val w = f.bitmap.width; val h = f.bitmap.height
            val px = IntArray(w * h)
            f.bitmap.getPixels(px, 0, w, 0, 0, w, h)
            for (c in px) {
                if (counter++ % stride != 0) continue
                if ((c ushr 24) > 100) pixels.add(c)
                if (pixels.size >= 60000) break
            }
            if (pixels.size >= 60000) break
        }
        if (pixels.isEmpty()) pixels.add(0xFF000000.toInt())

        val boxes = ArrayList<MutableList<Int>>()
        boxes.add(pixels)
        while (boxes.size < n) {
            var bestIdx = -1
            var bestScore = 0L
            for (i in boxes.indices) {
                val b = boxes[i]
                if (b.size < 2) continue
                val range = rangeOf(b)
                val score = range.toLong() * b.size
                if (score > bestScore) { bestScore = score; bestIdx = i }
            }
            if (bestIdx < 0) break
            val b = boxes.removeAt(bestIdx)
            val ch = widestChannel(b)
            b.sortWith(compareBy { c -> when (ch) { 0 -> (c shr 16) and 0xFF; 1 -> (c shr 8) and 0xFF; else -> c and 0xFF } })
            val mid = b.size / 2
            boxes.add(ArrayList(b.subList(0, mid)))
            boxes.add(ArrayList(b.subList(mid, b.size)))
        }
        val result = IntArray(n)
        for (i in boxes.indices) {
            val b = boxes[i]
            if (b.isEmpty()) { result[i] = 0xFF000000.toInt(); continue }
            var r = 0L; var g = 0L; var bl = 0L
            for (c in b) { r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; bl += c and 0xFF }
            val s = b.size
            result[i] = (0xFF shl 24) or ((r / s).toInt() shl 16) or ((g / s).toInt() shl 8) or (bl / s).toInt()
        }
        return result
    }

    private fun rangeOf(b: List<Int>): Int {
        var r0 = 255; var r1 = 0; var g0 = 255; var g1 = 0; var b0 = 255; var b1 = 0
        for (c in b) {
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val x = c and 0xFF
            if (r < r0) r0 = r; if (r > r1) r1 = r
            if (g < g0) g0 = g; if (g > g1) g1 = g
            if (x < b0) b0 = x; if (x > b1) b1 = x
        }
        return maxOf(r1 - r0, g1 - g0, b1 - b0)
    }

    private fun widestChannel(b: List<Int>): Int {
        var r0 = 255; var r1 = 0; var g0 = 255; var g1 = 0; var b0 = 255; var b1 = 0
        for (c in b) {
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val x = c and 0xFF
            if (r < r0) r0 = r; if (r > r1) r1 = r
            if (g < g0) g0 = g; if (g > g1) g1 = g
            if (x < b0) b0 = x; if (x > b1) b1 = x
        }
        val rr = r1 - r0; val gg = g1 - g0; val bb = b1 - b0
        return if (rr >= gg && rr >= bb) 0 else if (gg >= bb) 1 else 2
    }

    private fun buildLookup(palette: IntArray, n: Int): IntArray {
        val lut = IntArray(32768)
        for (ri in 0..31) for (gi in 0..31) for (bi in 0..31) {
            val r = ri shl 3; val g = gi shl 3; val b = bi shl 3
            var best = 1; var bestD = Int.MAX_VALUE
            for (i in 1 until n) {
                val c = palette[i]
                val dr = r - ((c shr 16) and 0xFF)
                val dg = g - ((c shr 8) and 0xFF)
                val db = b - (c and 0xFF)
                val dist = dr * dr + dg * dg + db * db
                if (dist < bestD) { bestD = dist; best = i }
            }
            lut[(ri shl 10) or (gi shl 5) or bi] = best
        }
        return lut
    }

    private fun quantize(pixels: IntArray, lut: IntArray): ByteArray {
        val out = ByteArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            if ((c ushr 24) < 100) { out[i] = 0; continue }
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            out[i] = lut[((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)].toByte()
        }
        return out
    }

    private fun lzwEncode(indices: ByteArray, w: BlockWriter) {
        val minCode = 8
        val clear = 1 shl minCode
        val end = clear + 1
        var codeSize = minCode + 1
        var next = end + 1
        var dict = HashMap<Int, Int>()
        fun reset() { dict.clear(); next = end + 1; codeSize = minCode + 1 }
        w.writeCode(clear, codeSize)
        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val k = indices[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val found = dict[key]
            if (found != null) { prefix = found; continue }
            w.writeCode(prefix, codeSize)
            if (next < 4096) {
                dict[key] = next++
                if (next > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                w.writeCode(clear, codeSize)
                reset()
            }
            prefix = k
        }
        w.writeCode(prefix, codeSize)
        w.writeCode(end, codeSize)
    }

    private class BlockWriter(private val out: OutputStream) {
        private val block = ByteArray(255)
        private var len = 0
        private var acc = 0
        private var bits = 0
        fun writeCode(code: Int, size: Int) {
            acc = acc or (code shl bits)
            bits += size
            while (bits >= 8) {
                block[len++] = (acc and 0xFF).toByte()
                if (len == 255) flush()
                acc = acc ushr 8
                bits -= 8
            }
        }
        private fun flush() {
            if (len == 0) return
            out.write(len)
            out.write(block, 0, len)
            len = 0
        }
        fun finish() {
            if (bits > 0) { block[len++] = (acc and 0xFF).toByte(); if (len == 255) flush() }
            flush()
            out.write(0)
        }
    }
}
