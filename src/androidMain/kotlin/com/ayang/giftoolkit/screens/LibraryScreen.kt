package com.ayang.giftoolkit.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ayang.giftoolkit.AppState
import com.ayang.giftoolkit.core.MediaTools
import com.ayang.giftoolkit.ui.GroupCard
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.IconSizes
import com.ayang.giftoolkit.ui.LeadingIcon
import com.ayang.giftoolkit.ui.Os4ListPage
import com.ayang.giftoolkit.ui.rememberGlassScope
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.FolderOutline
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Download
import top.yukonga.miuix.kmp.icon.glass.File
import top.yukonga.miuix.kmp.icon.glass.Share
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.io.File

@Composable
fun LibraryScreen(app: AppState) {
    val context = LocalContext.current
    // 行内不再塞「分享 / 删除」两个图标按钮（挤成一条，容易误触），
    // 改成点整行弹操作面板 —— 保存 / 分享 / 删除。
    var actionTarget by remember { mutableStateOf<File?>(null) }
    // 面板的 show 与"当前文件"要分开：关闭时先收起面板，等退场动画跑完再清文件，
    // 否则退场过程中标题和条目会瞬间变空
    var sheetVisible by remember { mutableStateOf(false) }
    val files = remember(app.libraryToken) {
        MediaTools.outputDir(context).listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    Box(Modifier.fillMaxSize()) {
        val glass = rememberGlassScope()
        Os4ListPage(
            title = "我的作品",
            glass = glass,
            subtitle = "共 ${files.size} 个导出文件",
        ) {
            if (files.isEmpty()) {
                item {
                    // 空态居中：之前是一段左对齐的灰字，看起来像"页面没加载完"。
                    Box(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = MiuixIcons.Glass.FolderOutline,
                                contentDescription = null,
                                modifier = Modifier.size(IconSizes.Empty),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "还没有导出过文件",
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "回到「工具」挑一个功能，生成结果会出现在这里",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                item { GroupHeader("本地导出") }
                item {
                    GroupCard {
                        files.forEach { file ->
                            FileRow(file = file, onClick = {
                                actionTarget = file
                                sheetVisible = true
                            })
                        }
                    }
                }
            }
        }

        actionTarget?.let { file ->
            FileActionSheet(
                file = file,
                visible = sheetVisible,
                onDismiss = { sheetVisible = false },
                onDismissFinished = {
                    sheetVisible = false
                    actionTarget = null
                },
                onSave = {
                    MediaTools.saveToGallery(context, file, mimeOf(file), file.name)
                    sheetVisible = false
                    actionTarget = null
                    app.libraryToken++
                },
                onShare = {
                    MediaTools.share(context, file, mimeOf(file))
                    sheetVisible = false
                    actionTarget = null
                },
                onDelete = {
                    file.delete()
                    sheetVisible = false
                    actionTarget = null
                    app.libraryToken++
                },
            )
        }
    }
}

/**
 * 作品列表行。
 *
 * 用库的 [ArrowPreference] 而不是 `BasicComponent` + 自己拼一个 `ChevronForward`：
 * 箭头图标、`onSurfaceVariantActions` 颜色、16dp 尺寸、行尾间距都是库内部定好的，
 * 自己拼一遍等于把这几项抄进业务代码，上游一调就只剩这一行不一致。
 */
@Composable
private fun FileRow(
    file: File,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = file.name,
        summary = "${formatSize(file.length())} · ${formatTime(file.lastModified())}",
        startAction = { LeadingIcon(MiuixIcons.Glass.Light.File) },
        onClick = onClick,
    )
}

/**
 * 文件操作面板。
 *
 * 用 [WindowBottomSheet]（系统窗口版）而不是 [GlassBottomSheet]，原因是被实测否掉的：
 *
 * `GlassBottomSheet` 是 **Scaffold 作用域**组件 —— `MiuixPopupUtils.DialogLayout` 从
 * `LocalDialogStates` 取宿主列表来绘制，而这个 local 由 Scaffold 提供并负责渲染。
 * 这个面板挂在 `Box` 里，与 `Os4ListPage` 内部的 Scaffold 是**兄弟**关系，
 * 拿不到那个 local，于是被加进一个没人绘制的列表 —— 静默不显示（`renderInRootScaffold`
 * 的 true/false 都要宿主，都救不了；实测点击文件后毫无反应）。
 *
 * 要给它造宿主就得在页面外包一层 Scaffold，那会改变 inset 与背景录制层，风险大于收益。
 * [WindowBottomSheet] 是独立系统窗口，不依赖 Compose 宿主，行为稳定。
 * 代价是拿不到玻璃材质（它的参数表里没有 `backdrop`）—— 这一项记为"验证后保留原实现"。
 */
@Composable
private fun FileActionSheet(
    file: File,
    visible: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    WindowBottomSheet(
        show = visible,
        title = file.name,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        BasicComponent(
            title = "保存到相册",
            startAction = { LeadingIcon(MiuixIcons.Glass.Light.Download) },
            onClick = onSave,
        )
        BasicComponent(
            title = "分享",
            startAction = { LeadingIcon(MiuixIcons.Glass.Light.Share) },
            onClick = onShare,
        )
        BasicComponent(
            title = "删除",
            titleColor = BasicComponentDefaults.titleColor(
                color = MiuixTheme.colorScheme.error,
            ),
            startAction = { LeadingIcon(MiuixIcons.Glass.Light.Delete, MiuixTheme.colorScheme.error) },
            onClick = onDelete,
        )
    }
}

private fun mimeOf(file: File): String = when (file.extension.lowercase()) {
    "mp4" -> "video/mp4"
    "zip" -> "application/zip"
    else -> "image/gif"
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatTime(millis: Long): String {
    val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(millis))
}
