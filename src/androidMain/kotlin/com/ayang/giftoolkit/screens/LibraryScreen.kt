package com.ayang.giftoolkit.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ayang.giftoolkit.AppState
import com.ayang.giftoolkit.core.MediaTools
import com.ayang.giftoolkit.ui.GroupCard
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.LeadingIcon
import com.ayang.giftoolkit.ui.NoteText
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

@Composable
fun LibraryScreen(app: AppState) {
    val context = LocalContext.current
    // 行内不再塞「分享 / 删除」两个图标按钮（挤成一条，容易误触），
    // 改成点整行弹操作面板 —— 保存 / 分享 / 删除。
    var actionTarget by remember { mutableStateOf<File?>(null) }
    val scrollBehavior = MiuixScrollBehavior()
    val files = remember(app.libraryToken) {
        MediaTools.outputDir(context).listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = "我的作品",
                    largeTitle = "我的作品",
                    subtitle = "共 ${files.size} 个导出文件",
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(top = 2.dp, bottom = 132.dp),
            ) {
                if (files.isEmpty()) {
                    item {
                        NoteText("还没有导出过文件。回到首页挑一个功能，生成结果会出现在这里。")
                    }
                } else {
                    item { GroupHeader("本地导出") }
                    item {
                        GroupCard {
                            files.forEach { file ->
                                FileRow(file = file, onClick = { actionTarget = file })
                            }
                        }
                    }
                }
            }
        }

        actionTarget?.let { file ->
            FileActionSheet(
                file = file,
                onDismiss = { actionTarget = null },
                onSave = {
                    MediaTools.saveToGallery(context, file, mimeOf(file), file.name)
                    actionTarget = null
                    app.libraryToken++
                },
                onShare = {
                    MediaTools.share(context, file, mimeOf(file))
                    actionTarget = null
                },
                onDelete = {
                    file.delete()
                    actionTarget = null
                    app.libraryToken++
                },
            )
        }
    }
}

@Composable
private fun FileRow(
    file: File,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = file.name,
        summary = "${formatSize(file.length())} · ${formatTime(file.lastModified())}",
        startAction = { LeadingIcon(MiuixIcons.File) },
        endActions = {
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        onClick = onClick,
    )
}

/**
 * 文件操作面板。
 *
 * 用对话框式弹层而不是行内图标：条目变干净，操作也只有点开时才出现，
 * 删除这种不可逆动作还多了一次确认机会。
 */
@Composable
private fun FileActionSheet(
    file: File,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.windowDimming)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                // 吞掉卡片内部的点击，否则点任意一行都会冒泡到蒙层把面板关掉
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
            cornerRadius = 24.dp,
            insideMargin = PaddingValues(vertical = 6.dp),
        ) {
            Text(
                text = file.name,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
            )
            BasicComponent(
                title = "保存到相册",
                startAction = { LeadingIcon(MiuixIcons.Download) },
                onClick = onSave,
            )
            BasicComponent(
                title = "分享",
                startAction = { LeadingIcon(MiuixIcons.Share) },
                onClick = onShare,
            )
            BasicComponent(
                title = "删除",
                titleColor = BasicComponentDefaults.titleColor(
                    color = MiuixTheme.colorScheme.error,
                ),
                startAction = { LeadingIcon(MiuixIcons.Delete, MiuixTheme.colorScheme.error) },
                onClick = onDelete,
            )
        }
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
