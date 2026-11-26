package com.ayang.giftoolkit.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.roundToInt

object MediaTools {

    /** 方向诊断日志的 tag：`adb logcat -s GifOrientation` 就能看到每段视频的实际旋转情况。 */
    private const val TAG = "GifOrientation"

    fun outputDir(context: Context): File = File(context.filesDir, "outputs").apply { mkdirs() }

    fun newFile(context: Context, prefix: String, ext: String): File =
        File(outputDir(context), "${prefix}_${System.currentTimeMillis()}.$ext")

    fun decodeImage(context: Context, uri: Uri, maxSide: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxSide * 2 && sample < 16) sample *= 2
        val opt = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opt) }
            ?: return null
        val scale = maxSide.toFloat() / maxOf(raw.width, raw.height).coerceAtLeast(1)
        if (scale < 1f) {
            val scaled = Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt().coerceAtLeast(1),
                (raw.height * scale).toInt().coerceAtLeast(1), true)
            if (scaled != raw) raw.recycle()
            scaled
        } else raw
    } catch (e: Exception) { null }

    fun decodeImages(context: Context, uris: List<Uri>, maxSide: Int): List<Bitmap> =
        uris.mapNotNull { decodeImage(context, it, maxSide) }

    fun decodeGif(context: Context, uri: Uri): List<GifFrame> = try {
        context.contentResolver.openInputStream(uri)?.use { GifDecoder.decode(it) } ?: emptyList()
    } catch (e: Exception) { emptyList() }

    /**
     * 视频轨道元数据。`rotation` 是把解码帧转正所需顺时针角度。
     *
     * [width] / [height] 是**编码**尺寸（未旋转），不是显示尺寸。
     */
    data class VideoMeta(val width: Int, val height: Int, val rotation: Int) {
        val displayWidth: Int get() = if (rotation % 180 == 0) width else height
        val displayHeight: Int get() = if (rotation % 180 == 0) height else width
    }

    fun readVideoMeta(retriever: MediaMetadataRetriever): VideoMeta {
        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0
        val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        return VideoMeta(w, h, ((rot % 360) + 360) % 360)
    }

    /**
     * 把解码帧转成实际显示方向。
     *
     * 不能无条件按元数据旋转：`getFrameAtTime` 返回的是解码器输出，
     * 部分机型/容器下它**已经**把旋转应用过了，再转一次就会把竖屏视频转成横的
     * （这正是「竖屏视频导出后变横屏」的成因之一）。
     *
     * 判据只有 90/270 有意义：此时编码尺寸与显示尺寸互换。
     * 若帧宽高已经等于「显示尺寸」，说明解码器已转正，直接返回原图。
     */
    fun orientForDisplay(bmp: Bitmap, meta: VideoMeta): Bitmap {
        if (meta.rotation % 360 == 0) return bmp
        val alreadyApplied = meta.rotation % 180 != 0 &&
            meta.width > 0 && meta.height > 0 &&
            bmp.width == meta.height && bmp.height == meta.width
        if (alreadyApplied) return bmp
        val m = Matrix().apply { postRotate(meta.rotation.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /**
     * 按**短边**缩放到 [shortSide]，与界面上「分辨率（短边）」的措辞保持一致。
     * 短边已经不超过目标值时原样返回（不放大），始终保持比例，不裁切。
     */
    fun fitShortSide(bmp: Bitmap, shortSide: Int): Bitmap {
        val minSide = minOf(bmp.width, bmp.height).coerceAtLeast(1)
        if (shortSide <= 0 || minSide <= shortSide) return bmp
        val scale = shortSide.toFloat() / minSide
        return Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * scale).roundToInt().coerceAtLeast(1),
            (bmp.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    fun videoFrames(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        fps: Int,
        width: Int,
        maxFrames: Int = 240,
        onProgress: (Int) -> Unit = {},
    ): List<GifFrame> {
        val r = MediaMetadataRetriever()
        val out = ArrayList<GifFrame>()
        try {
            r.setDataSource(context, uri)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val meta = readVideoMeta(r)
            val stop = if (endMs > 0L) minOf(endMs, duration) else duration
            val start = startMs.coerceAtLeast(0L).coerceAtMost(stop)
            if (stop <= start) return emptyList()
            val step = 1000.0 / fps.coerceIn(1, 30)
            val frameDelay = (1000.0 / fps).toInt()
            var t = start.toDouble()
            var previous: Bitmap? = null
            var logged = false
            while (t < stop && out.size < maxFrames) {
                val frame = r.getFrameAtTime((t * 1000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST)
                val src = frame ?: previous
                if (src != null) {
                    val oriented = orientForDisplay(src, meta)
                    val fitted = fitShortSide(oriented, width)
                    // 输出帧独占一份位图：下面的回收不会影响已经进结果的帧
                    val target = fitted.copy(Bitmap.Config.ARGB_8888, false)
                    if (!logged) {
                        logged = true
                        Log.d(
                            TAG,
                            "video ${meta.width}x${meta.height} rot=${meta.rotation} " +
                                "frame=${src.width}x${src.height} out=${target.width}x${target.height}",
                        )
                    }
                    out.add(GifFrame(target, frameDelay))
                    if (oriented !== src) oriented.recycle()
                    if (fitted !== oriented) fitted.recycle()
                }
                if (frame != null && previous != null && previous !== frame) previous.recycle()
                previous = frame ?: previous
                onProgress(((t - start) / (stop - start) * 100).toInt().coerceIn(0, 100))
                t += step
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            r.release()
        }
        return out
    }

    fun saveToGallery(context: Context, file: File, mime: String, name: String): Uri? = try {
        val video = mime.startsWith("video")
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/GifToolkit" else "Pictures/GifToolkit")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(collection, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        uri
    } catch (e: Exception) { null }

    fun share(context: Context, file: File, mime: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { e.printStackTrace() }
    }

    /** Encode composed GIF frames into an H.264 MP4 using MediaCodec, no FFmpeg needed. */
    fun writeMp4(frames: List<GifFrame>, file: File, fps: Int = 15) {
        if (frames.isEmpty()) throw IllegalArgumentException("没有可导出的帧")
        val w = frames[0].bitmap.width / 2 * 2
        val h = frames[0].bitmap.height / 2 * 2
        if (w < 16 || h < 16) throw IllegalArgumentException("GIF 尺寸过小")

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            setInteger(MediaFormat.KEY_BIT_RATE, (w * h * 4).coerceAtLeast(1_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps.coerceIn(5, 30))
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var started = false
        val info = MediaCodec.BufferInfo()
        var pts = 0L
        val stepUs = 1_000_000L / fps.coerceIn(5, 30)

        fun drain(endOfStream: Boolean) {
            while (true) {
                val id = codec.dequeueOutputBuffer(info, if (endOfStream) 10000 else 0)
                if (id == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (endOfStream) break
                    return
                }
                if (id == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!started) { track = muxer.addTrack(codec.outputFormat); muxer.start(); started = true }
                    continue
                }
                if (id < 0) continue
                val buf = codec.getOutputBuffer(id)
                if (buf != null && started && info.size > 0) {
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    muxer.writeSampleData(track, buf, info)
                }
                codec.releaseOutputBuffer(id, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }

        try {
            for (f in frames) {
                val bmp = if (f.bitmap.width == w && f.bitmap.height == h) f.bitmap
                else Bitmap.createScaledBitmap(f.bitmap, w, h, true)
                val nv12 = toNv12(bmp, w, h)
                var queued = false
                while (!queued) {
                    val id = codec.dequeueInputBuffer(10000)
                    if (id >= 0) {
                        val buf = codec.getInputBuffer(id)!!
                        buf.clear(); buf.put(nv12)
                        codec.queueInputBuffer(id, 0, nv12.size, pts, 0)
                        pts += stepUs
                        queued = true
                    }
                    drain(false)
                }
                drain(false)
            }
            var sent = false
            while (!sent) {
                val id = codec.dequeueInputBuffer(10000)
                if (id >= 0) {
                    codec.queueInputBuffer(id, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    sent = true
                }
                drain(false)
            }
            drain(true)
        } finally {
            try { codec.stop(); codec.release() } catch (_: Exception) {}
            try { if (started) muxer.stop(); muxer.release() } catch (_: Exception) {}
        }
    }

    private fun toNv12(bmp: Bitmap, w: Int, h: Int): ByteArray {
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val out = ByteArray(w * h * 3 / 2)
        for (i in 0 until w * h) {
            val c = px[i]
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            out[i] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte()
        }
        var off = w * h
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = px[y * w + x]
                val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
                out[off++] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).toByte()
                out[off++] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).toByte()
                x += 2
            }
            y += 2
        }
        return out
    }
}
