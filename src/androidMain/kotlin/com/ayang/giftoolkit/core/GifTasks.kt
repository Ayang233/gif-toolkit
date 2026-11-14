package com.ayang.giftoolkit.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

data class EditOptions(
    val rotate: Float = 0f,
    val mirror: Boolean = false,
    val flip: Boolean = false,
    val reverse: Boolean = false,
    val crop: RectF? = null,
    val text: String = "",
    val textColor: Int = Color.WHITE,
    val textSize: Float = 0.08f,
    val mosaic: RectF? = null,
    val scale: Float = 1f,
    val frameStep: Int = 1,
    val delayMs: Int = 100,
)

object GifTasks {

    fun writeGif(frames: List<GifFrame>, file: File, colorCount: Int = 255) {
        if (frames.isEmpty()) throw IllegalArgumentException("没有可用的帧")
        FileOutputStream(file).use { GifEncoder.encode(frames, it, colorCount) }
    }

    fun transform(frames: List<GifFrame>, o: EditOptions): List<GifFrame> {
        if (frames.isEmpty()) return emptyList()
        val step = o.frameStep.coerceAtLeast(1)
        val source = if (o.reverse) frames.asReversed() else frames
        val picked = if (step > 1) source.filterIndexed { i, _ -> i % step == 0 } else source
        val result = ArrayList<GifFrame>(picked.size)
        var targetW = -1
        var targetH = -1
        for (f in picked) {
            var bmp = f.bitmap
            o.crop?.let { r ->
                val x = (r.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                val y = (r.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                val w = ((r.right - r.left) * bmp.width).toInt().coerceIn(1, bmp.width - x)
                val h = ((r.bottom - r.top) * bmp.height).toInt().coerceIn(1, bmp.height - y)
                bmp = Bitmap.createBitmap(bmp, x, y, w, h)
            }
            if (o.rotate != 0f || o.mirror || o.flip) {
                val m = Matrix().apply {
                    if (o.rotate != 0f) postRotate(o.rotate)
                    postScale(if (o.mirror) -1f else 1f, if (o.flip) -1f else 1f)
                }
                val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (rotated != bmp) bmp.recycle()
                bmp = rotated
            }
            if (o.scale < 0.999f) {
                val nw = (bmp.width * o.scale).toInt().coerceAtLeast(8)
                val nh = (bmp.height * o.scale).toInt().coerceAtLeast(8)
                val scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true)
                if (scaled != bmp) bmp.recycle()
                bmp = scaled
            }
            if (targetW < 0) { targetW = bmp.width; targetH = bmp.height }
            if (bmp.width != targetW || bmp.height != targetH) {
                val fit = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
                if (fit != bmp) bmp.recycle()
                bmp = fit
            }
            bmp = bmp.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(bmp)
            o.mosaic?.let { applyMosaic(canvas, bmp, it) }
            if (o.text.isNotBlank()) drawText(canvas, bmp, o)
            result.add(GifFrame(bmp, if (o.delayMs > 0) o.delayMs else f.delayMs))
        }
        return result
    }

    private fun applyMosaic(canvas: Canvas, bmp: Bitmap, r: RectF) {
        val x = (r.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        val y = (r.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        val w = ((r.right - r.left) * bmp.width).toInt().coerceIn(1, bmp.width - x)
        val h = ((r.bottom - r.top) * bmp.height).toInt().coerceIn(1, bmp.height - y)
        val region = Bitmap.createBitmap(bmp, x, y, w, h)
        val tiny = Bitmap.createScaledBitmap(region, max(1, w / 14), max(1, h / 14), true)
        val big = Bitmap.createScaledBitmap(tiny, w, h, false)
        canvas.drawBitmap(big, x.toFloat(), y.toFloat(), null)
        region.recycle(); tiny.recycle(); big.recycle()
    }

    private fun drawText(canvas: Canvas, bmp: Bitmap, o: EditOptions) {
        val size = (bmp.height * o.textSize).coerceAtLeast(12f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = o.textColor
            textSize = size
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = size
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeWidth = size / 10f
        }
        val x = bmp.width / 2f
        val y = bmp.height - size * 0.6f
        o.text.lines().forEachIndexed { i, line ->
            val ly = y - (o.text.lines().size - 1 - i) * size * 1.2f
            canvas.drawText(line, x, ly, stroke)
            canvas.drawText(line, x, ly, fill)
        }
    }

    fun splitToPng(context: Context, frames: List<GifFrame>): List<File> {
        val dir = File(MediaTools.outputDir(context), "split_${System.currentTimeMillis()}").apply { mkdirs() }
        return frames.mapIndexed { i, f ->
            File(dir, "frame_%03d.png".format(i + 1)).also { out ->
                FileOutputStream(out).use { f.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    fun connect(context: Context, uris: List<Uri>): List<GifFrame> {
        val all = ArrayList<GifFrame>()
        var w = 0; var h = 0
        for (u in uris) {
            for (f in MediaTools.decodeGif(context, u)) {
                w = max(w, f.bitmap.width); h = max(h, f.bitmap.height)
                all.add(f)
            }
        }
        if (all.isEmpty()) return emptyList()
        return all.map { f ->
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val src = Bitmap.createScaledBitmap(f.bitmap,
                (f.bitmap.width * min(w.toFloat() / f.bitmap.width, h.toFloat() / f.bitmap.height)).toInt().coerceAtLeast(1),
                (f.bitmap.height * min(w.toFloat() / f.bitmap.width, h.toFloat() / f.bitmap.height)).toInt().coerceAtLeast(1), true)
            Canvas(bmp).drawBitmap(src, (w - src.width) / 2f, (h - src.height) / 2f, null)
            GifFrame(bmp, f.delayMs)
        }
    }

    fun puzzle(context: Context, uris: List<Uri>, cols: Int): List<GifFrame> {
        val sources = uris.take(4).map { MediaTools.decodeGif(context, it) }.filter { it.isNotEmpty() }
        if (sources.isEmpty()) return emptyList()
        // 2 个用 1×2，3 个用 2×2（右下留空），4 个用 2×2
        val c = if (sources.size <= 2) 2 else cols.coerceAtLeast(2)
        val rows = (sources.size + c - 1) / c
        val cellW = sources.maxOf { it[0].bitmap.width } / c
        val cellH = sources.maxOf { it[0].bitmap.height } / rows
        val w = (cellW * c).coerceAtLeast(16)
        val h = (cellH * rows).coerceAtLeast(16)
        val count = sources.minOf { it.size }
        return (0 until count).map { i ->
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            bmp.eraseColor(Color.BLACK)
            sources.forEachIndexed { si, frames ->
                val f = frames[i]
                // 等比缩放后在格子里居中，避免拉伸变形
                val scale = min(
                    cellW.toFloat() / f.bitmap.width.coerceAtLeast(1),
                    cellH.toFloat() / f.bitmap.height.coerceAtLeast(1),
                )
                val sw = (f.bitmap.width * scale).toInt().coerceAtLeast(1)
                val sh = (f.bitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(f.bitmap, sw, sh, true)
                canvas.drawBitmap(
                    scaled,
                    (si % c) * cellW + (cellW - sw) / 2f,
                    (si / c) * cellH + (cellH - sh) / 2f,
                    null,
                )
                scaled.recycle()
            }
            GifFrame(bmp, sources[0][i].delayMs)
        }
    }
}
