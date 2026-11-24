package com.ayang.giftoolkit.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.FolderOutline
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Download
import top.yukonga.miuix.kmp.icon.glass.File
import top.yukonga.miuix.kmp.icon.glass.Share
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassCard
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassIconButton
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
 * 文件操作面板（只按 MIUIX 官方写法组装，不参考任何第三方 App 的自定义组合）。
 *
 * 依据（全部来自 MIUIX 源码 `5ff41ba9`）：
 * - 面板：官方 [GlassBottomSheet]（`docs/demo/GlassBottomSheetDemo.kt` 的玻璃弹层示例），
 *   标题居中、采样页面录制层、行直接落在面板上 —— 官方玻璃示例不套 `Card` 分组，
 *   只有非玻璃的 `OverlayBottomSheet` 示例（`component/BottomSheetSection.kt`）才用
 *   `SmallTitle + Card(secondaryContainer)`；
 * - 行：官方 [BasicComponent]，最小行高 56dp / 行内边距 16dp / 图文间距 8dp 全吃默认值，
 *   不在调用点手写 `Row + clickable + padding`；
 * - 行图标：官方 example 的 Glass 图标统一 `Modifier.size(28.dp)`
 *   （`GlassPage.kt:325/359/372`、`IconPage.kt:266`、`PullToRefreshPage.kt:121`）；
 * - 收尾：官方 `component/BottomSheetSection.kt:258-266` 的「导航条 + captionBar」留白。
 *
 * 头部 `startAction` / `endAction` 在官方组件里是可选参数（`OverlayBottomSheet` 文档的
 * Action Sheet 示例用了 Cancel / Confirm 两个 TextButton；本项目是三个行内动作，
 * 由拖拽 / 外部点击 / 返回键关闭，因此不额外放头部按钮）。
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
        Column(
            // 底部留白交给官方写法：`navigationBarsPadding()`（面板自己会处理 captionBar 一档）
            // + 16dp 呼吸位，与官方 `component/GlassBottomSheetDemo.kt` 的收尾完全一致。
            // 之前是手算 `navigationBars + captionBar` 的 Spacer —— 又一处自己复刻 inset。
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // 玻璃面板上的内容用库的 `GlassCard`（beta04 新增）：它给"放在玻璃表面上的一组行"
            // 提供官方半透明填充，行本身保持清晰。官方 `GlassBottomSheetDemo.kt:56` 同款，
            // 之前是三行直接铺在玻璃材质上，文字与背景的对比度全靠材质自己撑。
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SheetActionRow(MiuixIcons.Glass.Download, "保存到相册", onClick = onSave)
                SheetActionRow(MiuixIcons.Glass.Share, "分享", onClick = onShare)
                SheetActionRow(MiuixIcons.Glass.Delete, "删除", destructive = true, onClick = onDelete)
            }
        }
    }
}

/**
 * 弹层里的一行：官方 [BasicComponent]（56dp 最小高、16dp 行内边距、8dp 图文间距全吃默认值）。
 *
 * 图标 28dp 是 MIUIX 官方 example 里 Glass 图标的统一尺寸
 * （`GlassPage.kt:325/359/372`、`IconPage.kt:266`、`MainPage.kt:224/254`、`PullToRefreshPage.kt:121`）；
 * 颜色取 `BasicComponentDefaults.titleColor` 的默认值（`onBackground`），破坏性操作用 `error`。
 * 不要在调用点再写 `Row + clickable`，也不要自己塞 `IconButton`。
 */
@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onBackground
    BasicComponent(
        title = label,
        titleColor = BasicComponentDefaults.titleColor(color = color),
        startAction = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(IconSizes.Row),
                tint = color,
            )
        },
        onClick = onClick,
    )
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
