package com.ayang.giftoolkit.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ayang.giftoolkit.ToolForm
import com.ayang.giftoolkit.core.GifFrame
import com.ayang.giftoolkit.core.MediaTools
import com.ayang.giftoolkit.task.previewBitmap
import com.ayang.giftoolkit.task.runTask
import com.ayang.giftoolkit.ui.DropdownRow
import com.ayang.giftoolkit.ui.GroupCard
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.IconSizes
import com.ayang.giftoolkit.ui.NoteText
import com.ayang.giftoolkit.ui.Os4ListPage
import com.ayang.giftoolkit.ui.PreviewStage
import com.ayang.giftoolkit.ui.PrimaryActionBar
import com.ayang.giftoolkit.ui.SliderRow
import com.ayang.giftoolkit.ui.SwitchRow
import com.ayang.giftoolkit.ui.Tool
import com.ayang.giftoolkit.ui.pageColor
import com.ayang.giftoolkit.ui.rememberGlassScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Clear
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

@Composable
fun ToolScreen(
    tool: Tool,
    onBack: () -> Unit,
    onExported: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val form = remember(tool.id) { ToolForm(tool) }
    val glass = rememberGlassScope()

    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            form.uris = listOf(uri)
            form.result = null
            form.message = ""
        }
    }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            form.uris = uris
            form.result = null
            form.message = ""
        }
    }

    // 素材变化时刷新静帧预览；防抖 200ms，避免拖动滑块时疯狂解码
    LaunchedEffect(form.uris, tool.id) {
        form.stillPreview = null
        if (form.uris.isEmpty()) return@LaunchedEffect
        delay(200)
        form.stillPreview = previewBitmap(context, tool, form)
    }

    Os4ListPage(
        title = tool.title,
        glass = glass,
        subtitle = tool.summary,
        onBack = onBack,
        reserveNavBar = false,
        // 底部主操作条自带渐变遮罩，再给结果卡留一点呼吸，别贴着按钮
        extraBottom = 20.dp,
        // 主操作放在 Scaffold 的 bottomBar 里：内容区自动让出高度，
        // 不会再出现"参数滑块贴着按钮"或者滚动到头还要被压住的情况。
        bottomBar = {
            PrimaryActionBar(
                text = tool.actionText,
                enabled = form.canRun,
                busy = form.busy,
                progress = form.progress,
                onClick = {
                    scope.launch {
                        runTask(context, tool, form)
                        if (form.result != null) onExported()
                    }
                },
                // 生成中点按钮 = 取消。只置标记，真正的中断发生在 TaskRunner 的
                // 逐帧检查点里 —— 阻塞解码靠 Job.cancel() 是打断不了的。
                onCancel = { form.cancelRequested = true },
            )
        },
    ) {
        item {
            GroupHeader("素材")
            GroupCard {
                BasicComponent(
                    title = if (tool.acceptsMultiple) "选择多个文件" else "选择文件",
                    summary = when {
                        form.uris.isEmpty() -> "从系统文件选择器导入"
                        tool.acceptsMultiple -> "已选择 ${form.uris.size} 个文件"
                        else -> "已选择 1 个文件"
                    },
                    endActions = {
                        if (form.uris.isNotEmpty()) {
                            IconButton(onClick = {
                                form.uris = emptyList()
                                form.stillPreview = null
                                form.result = null
                                form.message = ""
                            }) {
                                Icon(
                                    imageVector = MiuixIcons.Glass.Light.Clear,
                                    contentDescription = "清除",
                                    modifier = Modifier.size(IconSizes.Inline),
                                )
                            }
                        }
                    },
                    onClick = {
                        if (tool.acceptsMultiple) pickMany.launch(tool.mime)
                        else pickOne.launch(tool.mime)
                    },
                )
            }
        }

        item {
            GroupHeader("预览")
            GroupCard {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val still = form.stillPreview
                    if (tool == Tool.GIF_PLAYER && form.preview.isNotEmpty()) {
                        AnimatedPreview(form.preview, form.playSpeed)
                    } else {
                        PreviewStage(
                            bitmap = still,
                            // 有素材就按素材真实比例显示，不再强行套 4:3 留白；
                            // 空态用 16:9，省下来的高度正好给下面的参数区。
                            aspect = if (still != null) {
                                still.width.toFloat() / still.height.coerceAtLeast(1)
                            } else {
                                16f / 9f
                            },
                        )
                    }
                }
            }
        }

        item {
            GroupHeader("参数")
            GroupCard { ParamPanel(tool, form) }
        }

        if (form.message.isNotBlank()) {
            item { NoteText(form.message) }
        }

        form.result?.let { file ->
            item {
                GroupHeader("结果")
                ResultCard(
                    file = file,
                    mime = form.resultMime,
                    previewFrames = form.preview,
                    onShared = { MediaTools.share(context, file, form.resultMime) },
                    onSaved = {
                        MediaTools.saveToGallery(context, file, form.resultMime, file.name)
                        form.message = "已保存到相册：${file.name}"
                    },
                )
            }
        }
    }
}

@Composable
private fun ParamPanel(tool: Tool, form: ToolForm) {
    Column(Modifier.fillMaxWidth()) {
        when (tool) {
            Tool.VIDEO_TO_GIF, Tool.MVIMG_TO_GIF -> {
                SliderRow("分辨率（短边）", "${form.width.toInt()} px", form.width, 144f..640f) {
                    form.width = it
                }
                SliderRow("帧率", "${form.fps.toInt()} fps", form.fps, 5f..25f, steps = 19) {
                    form.fps = it
                }
                if (tool == Tool.VIDEO_TO_GIF) {
                    SliderRow("起始时间", "${form.startSec.toInt()} s", form.startSec, 0f..30f, steps = 29) {
                        form.startSec = it
                    }
                    SliderRow("截取时长", "%.1f s".format(form.durationSec), form.durationSec, 0.5f..15f, steps = 28) {
                        form.durationSec = it
                    }
                }
                SliderRow("色彩数量", "${form.quality.toInt()} 色", form.quality, 32f..255f) {
                    form.quality = it
                }
                SwitchRow("防抖", "减少画面抖动", form.stabilize) {
                    form.stabilize = it
                }
                SwitchRow("色度抠图", "把选定颜色抠成透明背景", form.colorKey) {
                    form.colorKey = it
                }
                if (form.colorKey) {
                    SliderRow("容差", "${form.keyTolerance.toInt()}%", form.keyTolerance, 5f..80f) {
                        form.keyTolerance = it
                    }
                    ColorSwatches(form)
                }
            }

            Tool.IMAGES_TO_GIF -> {
                SliderRow("分辨率（短边）", "${form.width.toInt()} px", form.width, 144f..640f) {
                    form.width = it
                }
                SliderRow("每帧间隔", "${form.delayMs.toInt()} ms", form.delayMs, 50f..600f, steps = 10) {
                    form.delayMs = it
                }
                SliderRow("色彩数量", "${form.quality.toInt()} 色", form.quality, 32f..255f) {
                    form.quality = it
                }
            }

            Tool.GIF_EDIT -> {
                // steps 是**中间档位数**（`basic/Slider.kt`），不是总档位：
                // 0..270 配 steps = 3 会得到 0/67.5/135/202.5/270 五档，而下游是
                // `postRotate(rotate)` 直接转浮点角 —— 67.5° 会把画面斜切、四角露出空区。
                // steps = 2 才是 0/90/180/270 四档。
                SliderRow("旋转", "${form.rotate.toInt()}°", form.rotate, 0f..270f, steps = 2) {
                    form.rotate = it
                }
                // 这几行的标题已经把动作说完了，副标题只复述同义词，直接去掉。
                SwitchRow("水平镜像", checked = form.mirror) { form.mirror = it }
                SwitchRow("垂直翻转", checked = form.flip) { form.flip = it }
                SwitchRow("倒放", checked = form.reverse) { form.reverse = it }
                SwitchRow("居中裁剪", "保留画面中央 80%", form.cropCenter) { form.cropCenter = it }
                SwitchRow("去水印", "在指定区域打马赛克", form.mosaic) { form.mosaic = it }
                if (form.mosaic) {
                    // 五个位置用"点一下轮换到下一个"太笨：看不见还有哪些选项，也退不回去。
                    // 换成 Miuix 的锚定下拉（KernelSU 设置里选"启用（默认）"就是这种弹窗）。
                    DropdownRow(
                        items = watermarkNames,
                        selectedIndex = form.mosaicPos % watermarkNames.size,
                        title = "水印位置",
                        onSelect = { form.mosaicPos = it },
                    )
                }
                SubtitleField(form)
            }

            Tool.GIF_COMPRESS -> {
                SliderRow("尺寸比例", "${(form.scale * 100).toInt()}%", form.scale, 0.3f..1f) {
                    form.scale = it
                }
                SliderRow("抽帧间隔", "每 ${form.frameStep.toInt()} 帧取 1 帧", form.frameStep, 1f..4f, steps = 2) {
                    form.frameStep = it
                }
                SliderRow("色彩数量", "${form.quality.toInt()} 色", form.quality, 32f..255f) {
                    form.quality = it
                }
            }

            // 这三个工具没有可调参数，卡片本身就够表达，不留说明段落
            Tool.GIF_CONNECT -> Unit
            Tool.GIF_PUZZLE -> {
                // 列数是离散档位，用弹窗选比滑块自然；2 个素材时列数固定为 2，不必给选项
                if (form.uris.size > 2) {
                    DropdownRow(
                        items = puzzleColsItems,
                        selectedIndex = (form.puzzleCols - 2).coerceIn(0, puzzleColsItems.lastIndex),
                        title = "宫格列数",
                        onSelect = { form.puzzleCols = it + 2 },
                    )
                }
            }
            Tool.GIF_SPLIT -> Unit

            Tool.GIF_TO_VIDEO -> SliderRow("视频帧率", "${form.fps.toInt()} fps", form.fps, 5f..30f, steps = 24) {
                form.fps = it
            }

            Tool.GIF_PLAYER -> SliderRow("播放速度", "%.2fx".format(form.playSpeed), form.playSpeed, 0.25f..3f) {
                form.playSpeed = it
            }
        }
    }
}

private val watermarkNames = listOf("左上角", "右上角", "中央", "左下角", "右下角")
private val puzzleColsItems = listOf("2 列", "3 列", "4 列")

/** 抠图颜色：固定几个常用色，比取色器更快。 */
@Composable
private fun ColorSwatches(form: ToolForm) {
    // 色值 + 读屏用的名字。`contentDescription` 必须给：自绘色块没有文字，
    // 不给的话 TalkBack 只会读出一个空标签（官方 `RadioButtonPreference` 的语义是
    // `selectable(role = Role.RadioButton)` + 标题文字，这里按同一套语义补齐）。
    val presets = listOf(
        0xFF00B441 to "绿色",
        0xFF3482FF to "蓝色",
        0xFFFFFFFF to "白色",
        0xFF000000 to "黑色",
        0xFFFF3B30 to "红色",
    )
    // 触觉反馈：这是本项目唯一自绘的"选择"控件，官方对应物是 RadioButtonPreference /
    // RadioButton —— 它们在选中项变化时调用 `HapticFeedbackType.ToggleOn`
    // （miuix-preference `RadioButtonPreference.kt:131`、miuix-ui `RadioButton.kt:101`）。
    // 点已经选中的颜色不发生状态变化，按迁移指南"无状态变化不加反馈"的规矩直接跳过。
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 12 + 28 + 12 = 52dp：补到官方 56dp 最小行高（`Component.kt` 的 BasicComponent）。
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 标题与同卡其它行同一档（`BasicComponent` 的 title 用 headline1，`Component.kt:92`）。
        Text("抠图颜色", style = MiuixTheme.textStyles.headline1, modifier = Modifier.weight(1f))
        presets.forEach { (value, name) ->
            val selected = form.keyColor == value.toInt()
            // **固定 28dp 槽位**：之前选中时 24→28dp，整行会跟着变宽、右侧色块一起位移。
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = {
                            if (!selected) {
                                hapticFeedback.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn,
                                )
                                form.keyColor = value.toInt()
                            }
                        },
                    )
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(value))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

/** 字幕输入：Miuix TextField + TextFieldState，失焦不丢内容。 */
@Composable
private fun SubtitleField(form: ToolForm) {
    val state = remember { androidx.compose.foundation.text.input.TextFieldState(form.text) }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { form.text = it }
    }
    TextField(
        state = state,
        label = "字幕内容",
        useLabelAsPlaceholder = true,
        // 不再自己加横向内边距：TextField 自带 16dp（TextFieldDefaults.InsideMargin），
        // 卡片 12dp + 16dp 正好和行标题的 28dp 对齐。再补 12dp 会右移一截。
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    )
}

@Composable
private fun AnimatedPreview(frames: List<GifFrame>, speed: Float) {
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(frames, speed) {
        if (frames.isEmpty()) return@LaunchedEffect
        while (true) {
            val frame = frames[index % frames.size]
            delay((frame.delayMs / speed).toLong().coerceIn(30L, 2000L))
            index = (index + 1) % frames.size
        }
    }
    val frame = frames.getOrNull(index % frames.size) ?: return
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(frame.bitmap.width.toFloat() / frame.bitmap.height.coerceAtLeast(1))
            .clip(RoundedCornerShape(12.dp))
            .background(pageColor()),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = frame.bitmap.asImageBitmap(),
            contentDescription = "GIF 预览",
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun ResultCard(
    file: File,
    mime: String,
    previewFrames: List<GifFrame>,
    onShared: () -> Unit,
    onSaved: () -> Unit,
) {
    GroupCard {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (mime == "image/gif" && previewFrames.isNotEmpty()) {
                AnimatedPreview(previewFrames, 1f)
                Spacer(Modifier.height(10.dp))
            }
            Text(file.name, style = MiuixTheme.textStyles.body1)
            Text(
                text = formatSize(file.length()),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onShared, modifier = Modifier.weight(1f)) { Text("分享") }
                Button(onClick = onSaved, modifier = Modifier.weight(1f)) { Text("保存到相册") }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
