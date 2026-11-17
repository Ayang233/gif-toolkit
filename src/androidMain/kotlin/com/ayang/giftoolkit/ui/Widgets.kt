package com.ayang.giftoolkit.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ImageOutline

/**
 * 分组标题。
 *
 * 直接用库里的 [SmallTitle] 而不是自写 Text：官方样式是 `subtitle` 字号 +
 * `PaddingValues(28.dp, 8.dp)` + `onBackgroundVariant` 颜色。
 * 之前自写的是 `footnote1` + 16dp 缩进，字号偏小、缩进也比 OS4 少 12dp ——
 * 这正是"看着不像系统设置页"的一个具体来源。卡片外边距 12dp + 标题缩进 28dp
 * 才能让标题和卡片内文字（12+16）对齐。
 */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    top.yukonga.miuix.kmp.basic.SmallTitle(text = text, modifier = modifier)
}

/**
 * AMOLED 纯黑开关。
 *
 * 只在深色模式生效：页面底纯黑、卡片近黑。不叠加到浅色上，
 * 因为浅色 + 纯黑页面是另一套设计（HyperOS 的 AMOLED 也只作用于深色）。
 */
val LocalAmoled = compositionLocalOf { false }

/**
 * 卡片底色 = 库的 `surfaceContainer`。
 *
 * 静态配色下它就是纯白（页面 #F7F7F7 / 卡片 #FFF），和 KernelSU、Updater 完全一致。
 * 不要拿 `surface` 当卡片色：Monet 下它会被映射成和页面同一个值，卡片直接消失。
 */
@Composable
fun cardColor(): Color {
    val card = MiuixTheme.colorScheme.surfaceContainer
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    // AMOLED：卡片用近黑，纯黑页面上仍能读出"卡片浮起"的层次
    return if (LocalAmoled.current && dark) Color(0xFF0D0D0D) else card
}

/**
 * 页面底色：由卡片色**算深一点**得到（浅色叠 3.5% 黑，深色叠 18% 黑）。
 *
 * 为什么不直接用 `surface`：Miuix 在 Monet 下会把 `surface` / `surfaceContainer`
 * 压成同一个值（浅色都是 #ECEEF0、深色都是 #1E2022），无论用哪个当页面，
 * 都会和卡片糊成一片。以卡片色为基准反推页面，四套配色（静态/ Monet × 明/暗）
 * 都能保证"卡片浮在页面之上"的层次。
 */
@Composable
fun pageColor(): Color {
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    if (LocalAmoled.current && dark) return Color.Black
    val card = cardColor()
    val shade = if (card.luminance() < 0.5f) {
        Color.Black.copy(alpha = 0.18f)
    } else {
        Color.Black.copy(alpha = 0.035f)
    }
    return shade.compositeOver(card)
}

/**
 * 一张不带内边距的分组卡片。Miuix 卡片内不用分割线，靠行高与留白区分。
 *
 * [bottomPadding] 默认 12dp：官方示例里卡片之间是 `padding(horizontal = 12.dp).padding(bottom = 12.dp)`。
 * 之前卡片上下贴在一起，一串分组读下来像一整块，分不清组边界。
 *
 * 卡片**不再自带录制层**：卡内下拉的采样源改成整页录制层（见 [LocalPageBackdrop]），
 * 卡片自己那层只在弹层落在卡片范围内时才够用，弹层一伸出卡片就露硬边。
 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = bottomPadding),
        // insideMargin 不再传：`CardDefaults.InsideMargin` 本来就是 `PaddingValues(0.dp)`，
        // 之前显式写一遍等于把库的默认值抄进了调用点。行内边距由各 Preference 自己管。
        // 走 cardColor() 而不是库默认的 surfaceContainer：
        // AMOLED 下卡片要单独压成近黑，否则纯黑页面上会浮出一块 #242424
        colors = CardDefaults.defaultColors(color = cardColor()),
    ) {
        content()
    }
}

/**
 * 行首图标。
 *
 * 尺寸统一走 [IconSizes.Row]：Glass 图标是 28dp 画布（图形本身占不满，四周留了描边余量），
 * 直接塞进 24dp 槽位会显得比旧线框图标小一圈。26dp 是"视觉大小与旧图标对齐"的实测值。
 *
 * **不再自己补 `padding(end = 4.dp)`。** 之前补那是为了把图标从文字边上推开一点 ——
 * 当时用的是 24dp 线框图标，`BasicComponent` 的固定 8dp 间距会显得贴字。
 * 现在图标全换成 Glass 族，28dp 画布里的图形本身四周还有 2~3dp 空白，
 * 实际视觉间距已经有 10dp 上下，再补 4dp 就过宽了。
 * 间距交回库：`BasicComponent` 里 `spacerPx = 8.dp` 是写死的，没有可调参数，
 * 调用方叠 padding 只会让这一行和别的行不一致。
 */
@Composable
fun LeadingIcon(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onBackground,
    size: Dp = IconSizes.Row,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(size),
        tint = tint,
    )
}

/** 图标尺寸的唯一出处，避免每个页面各写一个 `size(...)`。 */
object IconSizes {
    /** 列表行首。 */
    val Row: Dp = 26.dp

    /** 顶栏动作按钮（`GlassIconButton` 内部 44dp 胶囊）。 */
    val Action: Dp = 28.dp

    /** 空态插画。 */
    val Empty: Dp = 44.dp
}

/**
 * 下拉选择行。
 *
 * 玻璃档走 [GlassDropdownPreference]：弹出的是 Scaffold 托管的**玻璃浮层**，
 * 和 InstallerX 的 `InstallerMiuixSpinnerPreference` 同一个做法；
 * 普通 `WindowDropdownPreference` 弹出来是白底卡片，一眼就不是 OS4。
 *
 * `backdrop` 取 [LocalPageBackdrop]（页面正文录制层），与官方 `GlassPage.kt:447` 一致。
 * 之前传的是「卡片局部录制层」（照 `BlurSection.kt:125` 抄的，但那页的录制层是套住整列行的大容器，
 * 不是单张卡片）：单行卡片上的弹层会伸出卡片下边界，边界之外没有像素可采，
 * 弹层下半截只剩材质、看不到背景模糊 —— 实机截图见
 * `_archive/screenshots/os4-round5/cmp_popup2_backanim.png`。
 *
 * 传整页层不会偏移：库用 `layerCoordinates.localPositionOf(...)` 换算坐标。
 * 早期那次的脏斑是录制层没垫底色，`rememberPageBackdrop` 已经用 `drawRect(pageColor())` 补掉了。
 */
@Composable
fun DropdownRow(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    onSelect: (Int) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val glassEnabled = LocalGlassEnabled.current
    val pageBackdrop = LocalPageBackdrop.current
    GlassDropdownPreference(
        onSelectedIndexChange = onSelect,
        items = items,
        selectedIndex = selectedIndex,
        title = title,
        // 没有页面上下文（不在 ContentBackdropBox 里）或关掉玻璃时退回不透明底卡，
        // 这是库的官方兜底，不是我们的主路径。
        backdrop = pageBackdrop.takeIf { glassEnabled },
        summary = summary,
        enabled = enabled,
        visuals = GlassPopupDefaults.visuals(
            style = GlassDefaults.style(MiuixTheme.colorScheme.background.luminance() < 0.5f),
        ),
    )
}

/** 滑块行：走官方 SliderPreference，标题 / 数值 / 滑块的排版与系统设置页一致。 */@Composable
fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    SliderPreference(
        value = value,
        onValueChange = onChange,
        title = label,
        valueText = valueText,
        valueRange = range,
        steps = steps,
    )
}

/**
 * 开关行，整行可点。`onChange` 放最后，调用处才能用尾随 lambda。
 *
 * [summary] 可空：标题已经说清的事不要再复述一遍（"水平镜像 / 左右翻转"这种），
 * 没有增量信息时就不传。不传时用命名参数写 `checked =`，别用位置参数跳过它。
 */
@Composable
fun SwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    icon: ImageVector? = null,
    onChange: (Boolean) -> Unit,
) {
    SwitchPreference(
        checked = checked,
        onCheckedChange = onChange,
        title = title,
        summary = summary,
        startAction = icon?.let { vector -> { LeadingIcon(vector) } },
    )
}

/** 说明段落。 */
@Composable
fun NoteText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * 预览舞台。
 *
 * 卡片本身是浅灰（surface），舞台用 surfaceContainer 托一层，
 * 透明背景的 GIF 也能看清边界；空态给一个图标 + 提示语，避免一大块空白。
 */
@Composable
fun PreviewStage(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    aspect: Float = 4f / 3f,
    placeholder: String = "选择素材后显示预览",
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect.coerceIn(0.4f, 3f))
            .clip(RoundedCornerShape(12.dp))
            // 卡片是 surfaceContainer（白），舞台用 surface（#F7F7F7）才看得出"一块画布"；
            // 用 surfaceContainer 会和卡片糊成一片，透明度高的素材完全没边界。
            .background(pageColor()),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "预览",
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = MiuixIcons.Glass.ImageOutline,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = placeholder,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/**
 * 页面底部的主操作区。
 *
 * 三条决定都来自参考实现：
 * 1. **进度画在按钮内部**（轨道是按钮本身，填充从左往右铺），不再单独拉一条进度条。
 * 2. **容器只铺一层与页面同色的不透明底，不铺渐变**。
 *    之前是 `Brush.verticalGradient(0f 透明 → 0.55f 纯色)`，那才是"按钮底部能看到一层
 *    额外背景"的来源：渐变在按钮上方切出一条从透明到实色的过渡带，本身就是一个可见的色阶。
 *    但完全不加也不成立 —— 二级页底部只有一个按钮，列表内容会从按钮周围的缝隙里透出来
 *    （实机上能看到滑块的蓝色圆弧从按钮左下角露出来），官方的 `GlassPage` 之所以不用背景，
 *    是因为它的 `bottomBar` 里是带材质的玻璃导航胶囊，本身就挡住了。
 *    所以这里用 `pageColor()` 纯色：与页面底色**完全相同**，视觉上等于没有额外背景，
 *    同时把穿透内容挡住。
 * 3. **生成中仍可点击 = 取消任务**，文案直接写「取消生成 · 45%」，
 *    比禁用掉按钮更符合"我在等一个可以中断的任务"的直觉。
 */
@Composable
fun PrimaryActionBar(
    text: String,
    enabled: Boolean,
    busy: Boolean = false,
    progress: Float = 0f,
    onClick: () -> Unit,
    onCancel: () -> Unit = {},
) {
    val sideGap = 12.dp
    // 通栏控件左右留白要参与圆角安全区推导：屏幕圆角是圆弧，
    // 距离底部越近，水平方向的可用宽度越窄，贴边会被切掉一角。
    // 24dp 是官方 `bottomBar` 的下限（`maxOf(24.dp, insets + 8.dp)`），
    // 大圆角机型上 safeBottomGap 会算出比它更大的值。
    val bottomGap = maxOf(
        safeBottomGap(corner = rememberDeviceCornerRadius(), sideGap = sideGap + 4.dp),
        24.dp,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 纯页面色、不透明。背景在整个内边距之外，导航栏区域也一起盖住，
            // 底部不会漏出别的颜色。
            .background(pageColor())
            .navigationBarsPadding()
            .padding(start = sideGap, end = sideGap, top = 20.dp)
            .padding(bottom = bottomGap),
    ) {
        ActionButton(
            text = text,
            enabled = enabled,
            busy = busy,
            progress = progress,
            onClick = onClick,
            onCancel = onCancel,
        )
    }
}

/**
 * 主按钮本体。
 *
 * 生成中：轨道换成 `secondaryVariant`（浅），填充是 `primary`，文字色在填充扫过 45%
 * 时从 `onSecondaryContainer` 切到 `onPrimary` —— 这一条是关键，否则填充扫到一半时
 * 文字会同时压在两块底色上、谁都读不清。我们之前用「白 22% 叠浅蓝 + 灰白字」，
 * 恰好落在这个最差区间里。
 *
 * 空闲：轨道直接是 `primary`，禁用态改成与页面色不透明混合，
 * 而不是 `alpha` —— 半透明会让底下的列表文字透上来。
 */
@Composable
private fun ActionButton(
    text: String,
    enabled: Boolean,
    busy: Boolean,
    progress: Float,
    onClick: () -> Unit,
    onCancel: () -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    val animatedProgress by animateFloatAsState(
        targetValue = if (busy) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "actionButtonProgress",
    )

    val colors = if (busy) {
        ProgressButtonDefaults.progressButtonColors(
            trackColor = MiuixTheme.colorScheme.secondaryVariant,
            progressColor = primary,
            contentColor = if (animatedProgress < 0.45f) {
                MiuixTheme.colorScheme.onSecondaryContainer
            } else {
                MiuixTheme.colorScheme.onPrimary
            },
        )
    } else {
        ProgressButtonDefaults.progressButtonColors(
            trackColor = primary,
            progressColor = primary,
            contentColor = MiuixTheme.colorScheme.onPrimary,
        )
    }

    val label = if (busy) {
        "取消生成 · ${(animatedProgress * 100).toInt()}%"
    } else {
        text
    }

    ProgressButton(
        progress = if (busy) animatedProgress else 0f,
        onClick = { if (busy) onCancel() else onClick() },
        // 生成中必须保持 enabled：它现在承担的是「取消」，不是不可用的占位
        enabled = if (busy) true else enabled,
        colors = colors,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (busy) {
            InfiniteProgressIndicator(
                color = colors.contentColor,
                size = 16.dp,
                strokeWidth = 2.dp,
                orbitingDotSize = 1.5.dp,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MiuixTheme.textStyles.button,
            color = colors.contentColor,
        )
    }
}
