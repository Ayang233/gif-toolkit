package com.ayang.giftoolkit.ui

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Merge
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Recording
import top.yukonga.miuix.kmp.icon.extended.VerticalSplit
import top.yukonga.miuix.kmp.icon.extended.ZoomOut

/** 一个工具入口。id 是路由参数，也是导出文件的命名前缀。 */
enum class Tool(
    val id: String,
    val title: String,
    val summary: String,
    val group: String,
    val acceptsMultiple: Boolean,
    val mime: String,
) {
    VIDEO_TO_GIF("video2gif", "视频转 GIF", "截取片段，自定义分辨率与帧率", "创建", false, "video/*"),
    MVIMG_TO_GIF("mvimg2gif", "动态照片转 GIF", "提取内嵌视频，可做防抖", "创建", false, "image/jpeg"),
    IMAGES_TO_GIF("images2gif", "图片合成 GIF", "多张图片按顺序合成动图", "创建", true, "image/*"),

    GIF_EDIT("edit", "GIF 编辑", "裁剪、旋转、镜像、倒放、字幕", "处理", false, "image/gif"),
    GIF_COMPRESS("compress", "GIF 压缩", "降尺寸、抽帧、减色", "处理", false, "image/gif"),
    GIF_CONNECT("connect", "GIF 拼接", "多个 GIF 首尾相接", "处理", true, "image/gif"),
    GIF_PUZZLE("puzzle", "GIF 拼图", "2~4 个同屏宫格播放", "处理", true, "image/gif"),

    GIF_SPLIT("split", "GIF 分解", "拆成逐帧 PNG 并打包", "输出", false, "image/gif"),
    GIF_TO_VIDEO("tovideo", "GIF 转视频", "导出为 MP4 视频", "输出", false, "image/gif"),
    GIF_PLAYER("player", "GIF 播放", "调速、逐帧浏览", "输出", false, "image/gif"),
    ;

    /** 列表行里对应的符号。用 extended 线框集 —— glass 定色图标是给玻璃底栏用的，放在白底列表上偏重。 */
    val icon: ImageVector
        get() = when (this) {
            VIDEO_TO_GIF -> MiuixIcons.Recording
            MVIMG_TO_GIF -> MiuixIcons.Photos
            IMAGES_TO_GIF -> MiuixIcons.Album
            GIF_EDIT -> MiuixIcons.Edit
            GIF_COMPRESS -> MiuixIcons.ZoomOut
            GIF_CONNECT -> MiuixIcons.Merge
            GIF_PUZZLE -> MiuixIcons.GridView
            GIF_SPLIT -> MiuixIcons.VerticalSplit
            GIF_TO_VIDEO -> MiuixIcons.ConvertFile
            GIF_PLAYER -> MiuixIcons.Play
        }

    /** 工具页里主按钮的文案。 */
    val actionText: String
        get() = when (this) {
            GIF_PLAYER -> "加载并播放"
            GIF_SPLIT -> "导出图像包"
            GIF_TO_VIDEO -> "导出视频"
            else -> "开始生成"
        }

    companion object {
        fun fromId(id: String): Tool? = entries.firstOrNull { it.id == id }

        /** 按分组顺序返回，供主页分块渲染。 */
        val grouped: List<Pair<String, List<Tool>>>
            get() = entries.groupBy { it.group }.toList()
    }
}
