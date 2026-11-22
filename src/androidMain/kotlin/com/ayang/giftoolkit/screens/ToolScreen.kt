package com.ayang.giftoolkit.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
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
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Clear
import top.yukonga.miuix.kmp.squircle.squircleSurface
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

    // 字幕输入的 TextFieldState 放在**页面级**，不能放在 LazyColumn 的 item 里：
    // item 滚出可视区会被回收，`remember` 跟着丢，重新组合时又要从 `form.text` 重建 ——
    // 刚敲进去还没同步回 `form.text` 的那几个字就没了（实测能复现"输入后关键盘变空"）。
    val subtitleState = remember(tool.id) {
        if (tool == Tool.GIF_EDIT) androidx.compose.foundation.text.input.TextFieldState(form.text) else null
    }
    if (subtitleState != null) {
        LaunchedEffect(subtitleState) {
            snapshotFlow { subtitleState.text.toString() }.collect { form.text = it }
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
                // 官方自定义内容放 Card 里时用 `insideMargin = PaddingValues(16.dp)`；
                // 这里等价：水平 16 与行标题对齐，垂直也取 16（原来是 12，比官方紧一档）。
                Box(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
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

        // 抠图颜色：直接用官方 `ColorPalette`（`miuix-ui/basic/ColorPalette.kt`），
        // 摆法照官方 `component/ColorPickerSection.kt`：独立一张卡、`insideMargin` 16dp、
        // 全宽铺开、`showPreview = false`（预览色块在这里没有意义，色块本身就在网格里）。
        //
        // 之前是自绘的 5 个圆形色块：`clip(CircleShape)` + `border(2dp/1dp)` + 手调
        // `HapticFeedbackType.ToggleOn` + 自补 `semantics` —— 选中环、边框粗细、触觉、
        // 读屏语义全是我们自己维护的一套，而库本来就有官方的颜色选择组件
        // （7×12 HSV 网格 + 灰阶列 + 选中指示 + 触觉 + squircle 轮廓）。
        // 换掉以后这块只剩一次 `form.keyColor` 的读写，不再有第二套颜色选择实现。
        if (form.colorKey) {
            item(key = "keycolor-header") { GroupHeader("抠图颜色") }
            item(key = "keycolor") {
                GroupCard {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                        ColorPalette(
                            color = Color(form.keyColor),
                            onColorChanged = { form.keyColor = it.toArgb() },
                            showPreview = false,
                        )
                    }
                }
            }
        }

        // 字幕输入**不能**塞进上面那张卡片：MIUIX 的 `TextField` 自己就是一张卡
        // （外层 12dp 边距 + 16dp 圆角 + `secondaryContainer` 底色 + 16dp 内边距），
        // 套进 Card 就成了两层圆角贴在一起、还紧贴卡片边缘 —— 这是"比例不像 OS4"的来源。
        // 官方示例 `component/TextFieldSection.kt` 就是独立摆放：
        // `Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)`。
        //
        // 标题走 `GroupHeader`、输入框**不带 label**（同官方示例第一行那个纯 TextField）：
        // 之前用 `label` + `useLabelAsPlaceholder` 把提示文字画在行内，而占位文字是 label 层、
        // 光标跟输入行层，两层的行盒不一致 —— 竖杠比 CJK 字形高出一截、打字瞬间文字还会往上跳。
        // 官方 example（拉丁占位符）看不出来只是因为字母上伸部填满了行盒。
        if (subtitleState != null && tool == Tool.GIF_EDIT) {
            item(key = "subtitle-header") { GroupHeader("字幕内容") }
            item(key = "subtitle") { SubtitleField(state = subtitleState) }
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

/**
 * 字幕输入：Miuix `TextField` + `TextFieldState`，失焦不丢内容。
 *
 * 标题在调用处用 `GroupHeader("字幕内容")` 单独占一条，输入框本身**不带 label**
 * （官方 example `component/TextFieldSection.kt` 第一行就是这个形态）。
 *
 * 摆法照官方 `component/TextFieldSection.kt`：TextField **独立**占一条，
 * `padding(horizontal = 12.dp).padding(bottom = 12.dp)` —— 它自己就是一张卡
 * （`TextFieldDefaults.CornerRadius` 16dp、`secondaryContainer` 底色、`InsideMargin` 16dp）。
 *
 * 早先它被塞进 `GroupCard` 里、只给 `vertical = 10.dp`：结果是两层圆角套在一起、
 * 输入框紧贴卡片边缘、底部圆角还被卡片圆角截掉一截，和 OS4 的比例明显不同。
 * 横向内边距也不要自己补 —— TextField 内部 `InsideMargin.width` 就是 16dp，
 * 和卡片里行标题的 28dp（12 外边距 + 16 内边距）本来就对齐。
 */
@Composable
private fun SubtitleField(state: androidx.compose.foundation.text.input.TextFieldState) {
    // 位置交给官方机制：
    // 1) `Modifier.bringIntoViewRequester` 把这个**整个输入框**（含内边距）作为滚动目标 ——
    //    `TextField` 自己只请求"文本行"可见，行底到框底还差 16dp 内边距，看起来就是被切掉一截；
    // 2) 滚动量由 `LocalBringIntoViewSpec` 算（见 `Os4ListPage`），那里扣掉了底栏高度；
    // 3) 位移由键盘动画驱动（`imePadding`），这里不写任何动画时长/曲线。
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val requester = remember { BringIntoViewRequester() }
    // 键盘高度每帧都在变：每次变化都重新请求一次「把整个输入框bringIntoView」，
    // 滚动量与动画仍由框架给（`LocalBringIntoViewSpec` 只负责扣掉底栏）。
    val imePx = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imePx) {
        if (focused) requester.bringIntoView()
    }
    Box(
        Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .bringIntoViewRequester(requester),
    ) {
        // 摆法照官方 `component/TextFieldSection.kt` 的**第一行**：纯 `TextField`，
        // 不带 `label` / `useLabelAsPlaceholder` —— 标题由上面的 `GroupHeader("字幕内容")` 提供。
        // 行内占位符会同时引入两套文本层（label 层画提示、输入层画光标），行盒不一致，
        // 竖杠就会比 CJK 字形高一截、开始打字时文字还会往上跳一次。
        TextField(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interactionSource,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
    }
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
            // 与预览舞台同源：连续曲率圆角走库的 squircleSurface，不自造 clip(RoundedCornerShape)。
            .squircleSurface(color = pageColor(), cornerRadius = 12.dp),
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
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
            // 官方 `component/CardSection.kt:66` 的并排按钮组用 12dp
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
