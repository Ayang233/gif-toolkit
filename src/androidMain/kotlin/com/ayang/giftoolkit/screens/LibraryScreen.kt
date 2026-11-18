package com.ayang.giftoolkit.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
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
    // 删除是不可恢复的（文件在应用私有目录里，删掉就没了），必须二次确认。
    // 面板先收起，再弹确认框 —— 两个弹层不叠在一起。
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var deleteVisible by remember { mutableStateOf(false) }
    val files = remember(app.libraryToken) {
        MediaTools.outputDir(context).listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    // 面板不再需要"让出一层导航"：它由最外层 Scaffold 绘制，永远在底栏之上（官方同理，
    // 面板开着时底栏只是被遮罩压暗）。之前那句 modalOverlayOpen 是"面板画在底栏下面"的补丁，
    // 现在结构改了，补丁连同它的状态泄漏风险一起删掉。

    val glass = rememberGlassScope()
    Os4ListPage(
        title = "我的作品",
        glass = glass,
        subtitle = "共 ${files.size} 个导出文件",
        // 面板要组合在 Scaffold 里才拿得到 LocalDialogStates（玻璃弹层的宿主），
        // 同时必须在录制层之外，否则它会采样到自己。
        pageOverlay = { backdrop ->
            actionTarget?.let { file ->
                FileActionSheet(
                    file = file,
                    backdrop = backdrop,
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
                        sheetVisible = false
                        deleteTarget = file
                        deleteVisible = true
                    },
                )
            }

            // 删除确认：走官方的 GlassDialog（现在有最外层 Scaffold 当宿主）。
            deleteTarget?.let { file ->
                GlassDialog(
                    show = deleteVisible,
                    backdrop = backdrop,
                    title = "删除这个文件？",
                    summary = "${file.name} 会从「我的作品」里永久删除，App 里无法恢复。",
                    onDismissRequest = { deleteVisible = false },
                    onDismissFinished = {
                        deleteVisible = false
                        deleteTarget = null
                    },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassDialogButton(
                            onClick = { deleteVisible = false },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = "取消", style = MiuixTheme.textStyles.button)
                        }
                        GlassDialogButton(
                            onClick = {
                                file.delete()
                                deleteVisible = false
                                deleteTarget = null
                                app.libraryToken++
                            },
                            modifier = Modifier.weight(1f),
                            // 破坏性操作用库的 Destructive 角色（半透明红），
                            // 不用 Primary 蓝 —— 蓝色在这个语境里等于"推荐操作"。
                            role = GlassDialogButtonRole.Destructive,
                        ) {
                            Text(text = "删除", style = MiuixTheme.textStyles.button)
                        }
                    }
                }
            }
        },
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
                                text = "在「工具」里生成后会出现在这里",
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
 * 用官方的 [GlassBottomSheet]，采样页面正文录制层（[LocalPageBackdrop]），
 * 与官方示例 `component/GlassBottomSheetDemo.kt` 同一套：半透明磨砂面板 + 拖拽把手 + 标题。
 *
 * 之前这里是 `WindowBottomSheet`（系统窗口版）：它的参数表里没有 `backdrop`，
 * 所以拿不到任何玻璃效果，实拍是一块不透明的白色面板。
 * 当时退成系统窗口的原因是 `GlassBottomSheet` 依赖 `LocalDialogStates` —— 由 Scaffold 提供并绘制，
 * 而面板挂在 `Os4ListPage` 外面（兄弟节点），拿不到那个 local，静默不显示。
 *
 * 现在改由 `Os4ListPage` 的 `pageOverlay` 槽位承载：槽位在 Scaffold **内部**、录制层**外部**，
 * 宿主与"不要采样自己"两个条件同时满足。业务回调与菜单项保持原样。
 */
@Composable
private fun FileActionSheet(
    file: File,
    backdrop: LayerBackdrop?,
    visible: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassBottomSheet(
        show = visible,
        title = file.name,
        backdrop = backdrop,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        // 行内边距去掉左右：面板本身已经有 24dp 内边距（`BottomSheetDefaults.insideMargin`），
        // 行再叠 16dp 会让标题距面板边 40dp、和面板标题的 24dp 对不齐。
        // 官方示例（`component/GlassBottomSheetDemo.kt`）也是"面板给横向、行只给纵向"。
        val rowMargin = PaddingValues(horizontal = 0.dp, vertical = 16.dp)
        BasicComponent(
            title = "保存到相册",
            startAction = { LeadingIcon(MiuixIcons.Glass.Light.Download) },
            insideMargin = rowMargin,
            onClick = onSave,
        )
        BasicComponent(
            title = "分享",
            startAction = { LeadingIcon(MiuixIcons.Glass.Light.Share) },
            insideMargin = rowMargin,
            onClick = onShare,
        )
        BasicComponent(
            title = "删除",
            titleColor = BasicComponentDefaults.titleColor(
                color = MiuixTheme.colorScheme.error,
            ),
            startAction = { LeadingIcon(MiuixIcons.Glass.Light.Delete, MiuixTheme.colorScheme.error) },
            insideMargin = rowMargin,
            onClick = onDelete,
        )
        // 手势导航条留白：面板的 `defaultWindowInsetsPadding` 只处理 IME，
        // 底部导航条要内容自己让（官方 `GlassBottomSheet` KDoc 原文：
        // "Apply navigation-bar padding inside scrollable content"）。
        // 少了这一行，"删除"会贴着导航条（真机手势导航下最明显）。
        Spacer(Modifier.navigationBarsPadding().height(8.dp))
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
