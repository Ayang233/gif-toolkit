package com.ayang.giftoolkit.task

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.ayang.giftoolkit.ToolForm
import com.ayang.giftoolkit.core.EditOptions
import com.ayang.giftoolkit.core.GifFrame
import com.ayang.giftoolkit.core.GifTasks
import com.ayang.giftoolkit.core.MediaTools
import com.ayang.giftoolkit.core.MotionPhoto
import com.ayang.giftoolkit.ui.Tool
import com.ayang.giftoolkit.ui.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 用户主动取消。单独一个类型，好和真正的失败区分开（失败要显示原因，取消不用）。 */
private class TaskCancelled : Exception("cancelled")

/** 协作式取消检查点：解码是阻塞循环，靠这个函数在每帧回调里中断。 */
private fun ToolForm.checkCancelled() {
    if (cancelRequested) throw TaskCancelled()
}

/** 执行一个工具任务，结果写回 [f]。所有 IO 都在 IO 线程。 */
suspend fun runTask(context: Context, tool: Tool, f: ToolForm) = withContext(Dispatchers.IO) {
    // 上一次的半成品要清掉，否则取消后残留的文件会被当成"上次结果"
    var createdFile: java.io.File? = null
    try {
        f.cancelRequested = false
        f.busy = true
        f.progress = 0.05f
        f.message = "正在处理…"
        f.result = null

        val ext = when (tool) {
            Tool.GIF_TO_VIDEO -> "mp4"
            Tool.GIF_SPLIT -> "zip"
            else -> "gif"
        }
        val out = MediaTools.newFile(context, tool.id, ext).also { createdFile = it }
        var frames: List<GifFrame> = emptyList()

        when (tool) {
            Tool.VIDEO_TO_GIF -> {
                frames = MediaTools.videoFrames(
                    context = context,
                    uri = f.uris[0],
                    startMs = (f.startSec * 1000).toLong(),
                    endMs = ((f.startSec + f.durationSec) * 1000).toLong(),
                    fps = f.fps.toInt(),
                    width = f.width.toInt(),
                ) { f.progress = 0.05f + it / 200f; f.checkCancelled() }
                if (frames.isEmpty()) error("无法读取该视频，换一个文件试试")
                f.checkCancelled()
                f.progress = 0.65f
                frames = settle(frames, f)
                GifTasks.writeGif(frames, out, f.quality.toInt())
            }

            Tool.MVIMG_TO_GIF -> {
                val video = MotionPhoto.extractVideo(context, f.uris[0])
                    ?: error("文件里没有内嵌视频，可能不是动态照片")
                frames = MediaTools.videoFrames(
                    context = context,
                    uri = Uri.fromFile(video),
                    startMs = 0L,
                    endMs = 0L,
                    fps = f.fps.toInt(),
                    width = f.width.toInt(),
                ) { f.progress = 0.05f + it / 200f; f.checkCancelled() }
                video.delete()
                if (frames.isEmpty()) error("无法读取内嵌视频，换一个文件试试")
                f.checkCancelled()
                f.progress = 0.65f
                frames = settle(frames, f)
                GifTasks.writeGif(frames, out, f.quality.toInt())
            }

            Tool.IMAGES_TO_GIF -> {
                val bitmaps = MediaTools.decodeImages(context, f.uris, f.width.toInt())
                if (bitmaps.size < 2) error("至少需要 2 张图片")
                val w = bitmaps[0].width
                val h = bitmaps[0].height
                frames = bitmaps.map { bmp ->
                    val fit = if (bmp.width == w && bmp.height == h) bmp
                    else Bitmap.createScaledBitmap(bmp, w, h, true)
                    GifFrame(fit, f.delayMs.toInt())
                }
                f.progress = 0.65f
                GifTasks.writeGif(frames, out, f.quality.toInt())
            }

            Tool.GIF_EDIT -> {
                val src = MediaTools.decodeGif(context, f.uris[0])
                if (src.isEmpty()) error("无法读取该 GIF")
                val options = EditOptions(
                    rotate = f.rotate,
                    mirror = f.mirror,
                    flip = f.flip,
                    reverse = f.reverse,
                    crop = if (f.cropCenter) RectF(0.1f, 0.1f, 0.9f, 0.9f) else null,
                    text = f.text,
                    textSize = 0.08f,
                    mosaic = if (f.mosaic) mosaicRect(f.mosaicPos) else null,
                    delayMs = 0,
                )
                frames = GifTasks.transform(src, options)
                f.progress = 0.65f
                GifTasks.writeGif(frames, out, 255)
            }

            Tool.GIF_COMPRESS -> {
                val src = MediaTools.decodeGif(context, f.uris[0])
                if (src.isEmpty()) error("无法读取该 GIF")
                frames = GifTasks.transform(
                    src,
                    EditOptions(scale = f.scale, frameStep = f.frameStep.toInt(), delayMs = 0),
                )
                f.progress = 0.65f
                GifTasks.writeGif(frames, out, f.quality.toInt())
            }

            Tool.GIF_CONNECT -> {
                if (f.uris.size < 2) error("至少需要 2 个 GIF")
                frames = GifTasks.connect(context, f.uris)
                if (frames.isEmpty()) error("拼接失败，请检查素材")
                f.progress = 0.65f
                GifTasks.writeGif(frames, out, 255)
            }

            Tool.GIF_PUZZLE -> {
                if (f.uris.size < 2) error("至少需要 2 个 GIF")
                frames = GifTasks.puzzle(context, f.uris, cols = f.puzzleCols)
                if (frames.isEmpty()) error("拼图失败，请检查素材")
                f.progress = 0.65f
                GifTasks.writeGif(frames, out, 255)
            }

            Tool.GIF_SPLIT -> {
                val src = MediaTools.decodeGif(context, f.uris[0])
                if (src.isEmpty()) error("无法读取该 GIF")
                ZipOutputStream(FileOutputStream(out)).use { zip ->
                    src.forEachIndexed { index, frame ->
                        // 逐帧检查：拆帧是最容易被中途叫停的一步
                        f.checkCancelled()
                        zip.putNextEntry(ZipEntry("frame_%03d.png".format(index + 1)))
                        frame.bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
                        zip.closeEntry()
                        f.progress = 0.05f + (index + 1) * 0.9f / src.size
                    }
                }
            }

            Tool.GIF_TO_VIDEO -> {
                val src = MediaTools.decodeGif(context, f.uris[0])
                if (src.isEmpty()) error("无法读取该 GIF")
                MediaTools.writeMp4(src, out, f.fps.toInt())
                f.progress = 0.95f
            }

            Tool.GIF_PLAYER -> {
                val src = MediaTools.decodeGif(context, f.uris[0])
                if (src.isEmpty()) error("无法读取该 GIF")
                f.preview = src
                f.stillPreview = src.first().bitmap
                f.message = "已加载 ${src.size} 帧"
                f.progress = 1f
                f.busy = false
                return@withContext
            }
        }

        // 编码阶段结束时再确认一次：用户在写盘途中点了取消，产物不该留下
        f.checkCancelled()
        f.resultMime = when (tool) {
            Tool.GIF_TO_VIDEO -> "video/mp4"
            Tool.GIF_SPLIT -> "application/zip"
            else -> "image/gif"
        }
        if (tool != Tool.GIF_SPLIT && tool != Tool.GIF_TO_VIDEO) {
            f.preview = if (frames.size <= 120) frames else emptyList()
            f.stillPreview = frames.firstOrNull()?.bitmap
        }
        f.result = out
        f.progress = 1f
        f.message = "已生成 ${out.name} · ${formatSize(out.length())}"
        f.busy = false
    } catch (e: TaskCancelled) {
        // 取消不是失败：不弹错误、不留半成品
        createdFile?.delete()
        f.preview = emptyList()
        f.stillPreview = null
        f.message = "已取消"
        f.progress = 0f
        f.busy = false
    } catch (e: Exception) {
        f.message = "失败：${e.message ?: "未知错误"}"
        f.progress = 0f
        f.busy = false
    }
}

/** 稳定化 + 色键，两个可选的预处理。 */
private fun settle(frames: List<GifFrame>, f: ToolForm): List<GifFrame> {
    var out = frames
    if (f.stabilize) out = com.ayang.giftoolkit.core.Stabilizer.stabilize(out)
    if (f.colorKey) {
        out = out.map { frame ->
            GifFrame(
                com.ayang.giftoolkit.core.ColorKey.apply(frame.bitmap, f.keyColor, f.keyTolerance.toInt()),
                frame.delayMs,
            )
        }
    }
    return out
}

/** 预览用静帧：视频取一帧、GIF 取首帧、图片直接缩放。 */
suspend fun previewBitmap(context: Context, tool: Tool, f: ToolForm): Bitmap? = withContext(Dispatchers.IO) {
    val uri = f.uris.firstOrNull() ?: return@withContext null
    runCatching {
        when (tool) {
            Tool.VIDEO_TO_GIF, Tool.MVIMG_TO_GIF -> {
                val extracted = if (tool == Tool.MVIMG_TO_GIF) {
                    MotionPhoto.extractVideo(context, uri) ?: return@runCatching null
                } else {
                    null
                }
                val target = extracted?.let { Uri.fromFile(it) } ?: uri
                try {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, target)
                        val meta = MediaTools.readVideoMeta(retriever)
                        retriever.getFrameAtTime(
                            (f.startSec * 1_000_000).toLong(),
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        )?.let { raw ->
                            // 预览必须和导出走同一条「定向 + 短边缩放」的路径，
                            // 否则会出现"预览是竖的、导出是横的"这种对不上的情况
                            val oriented = MediaTools.orientForDisplay(raw, meta)
                            val fitted = MediaTools.fitShortSide(oriented, f.width.toInt())
                            val out = fitted.copy(Bitmap.Config.ARGB_8888, false)
                            if (oriented !== raw) oriented.recycle()
                            if (fitted !== oriented) fitted.recycle()
                            out
                        }
                    } finally {
                        retriever.release()
                    }
                } finally {
                    // 动态照片的临时视频只看一帧，用完就删，别留在 filesDir 里
                    extracted?.delete()
                }
            }

            Tool.IMAGES_TO_GIF -> MediaTools.decodeImage(context, uri, f.width.toInt())
            else -> MediaTools.decodeGif(context, uri).firstOrNull()?.bitmap
        }
    }.getOrNull()
}

private fun mosaicRect(pos: Int): RectF = when (pos) {
    0 -> RectF(0.02f, 0.02f, 0.38f, 0.14f)
    1 -> RectF(0.62f, 0.02f, 0.98f, 0.14f)
    2 -> RectF(0.30f, 0.44f, 0.70f, 0.56f)
    3 -> RectF(0.02f, 0.86f, 0.38f, 0.98f)
    else -> RectF(0.62f, 0.86f, 0.98f, 0.98f)
}

