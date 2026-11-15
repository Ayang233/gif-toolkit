package com.ayang.giftoolkit.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Photos

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

/** 一张不带内边距的分组卡片。Miuix 卡片内不用分割线，靠行高与留白区分。 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.padding(horizontal = 12.dp),
        insideMargin = PaddingValues(0.dp),
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
 * Miuix 的 [top.yukonga.miuix.kmp.basic.BasicComponent] 在图标和标题之间只留 8dp，
 * 线框图标在这个距离上会显得贴字，补 4dp 到 12dp 更接近系统设置页。
 */
@Composable
fun LeadingIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onBackground) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.padding(end = 4.dp).size(24.dp),
        tint = tint,
    )
}

/** 滑块行：走官方 SliderPreference，标题 / 数值 / 滑块的排版与系统设置页一致。 */
@Composable
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

/** 开关行，整行可点。`onChange` 放最后，调用处才能用尾随 lambda。 */
@Composable
fun SwitchRow(
    title: String,
    summary: String,
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

/** 纯信息行（无点击）。 */
@Composable
fun InfoRow(title: String, summary: String, icon: ImageVector? = null) {
    top.yukonga.miuix.kmp.basic.BasicComponent(
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
 * 三段式分段控件（主题：跟随系统 / 浅色 / 深色）。
 *
 * 没有用库里的 [top.yukonga.miuix.kmp.basic.TabRow]：那个组件是给可横向滚动的
 * 标签栏用的，未选中项会各画一圈 1dp 描边，渲染出来是"三个独立小按钮"，
 * 不是「一整条凹槽 + 一个滑块」的分段控件。
 *
 * 配色复用 [pageColor] / [cardColor]：凹槽永远比滑块深一档，
 * 静态配色与 Monet、浅色与深色都成立。
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    // 不要用 `background` 当滑块色：Monet 下它不是白色，会和凹槽撞成一个色，
    // 于是"选中的那块"看起来是灰的、未选中的反而发白 —— 层次整个反了。
    // 统一从 card/page 推导：凹槽 = 页面色（深一档），滑块 = 卡片色（浅一档）。
    val trackColor = pageColor()
    val pillColor = cardColor()

    // 同心圆角：滑块半径必须等于「凹槽半径 − 内边距」，否则滑块四角会盖不满凹槽，
    // 边角会露出 1~2dp 的凹槽底色，看起来就是"圆角没对齐"。
    val trackCorner = 11.dp
    val trackInset = 3.dp
    val pillCorner = trackCorner - trackInset

    Row(
        modifier = modifier
            .fillMaxWidth()
            .squircleBackground(color = trackColor, cornerRadius = trackCorner)
            .padding(trackInset),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .squircleSurface(
                        color = if (selected) pillColor else Color.Transparent,
                        cornerRadius = pillCorner,
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) {
                        MiuixTheme.colorScheme.onBackground
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        }
    }
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
                    imageVector = MiuixIcons.Photos,
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
 * 2. **不铺实心底栏，改用渐变遮罩**：实心色块会在页面上切出一条"带子"，
 *    完全不加又会让人文字从按钮底下穿过去。
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
    val background = pageColor()
    // 通栏控件左右留白要参与圆角安全区推导：屏幕圆角是圆弧，
    // 距离底部越近，水平方向的可用宽度越窄，贴边会被切掉一角。
    val bottomGap = safeBottomGap(corner = rememberDeviceCornerRadius(), sideGap = sideGap + 4.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 渐变遮罩：最上面完全透明，到按钮所在高度时已经是纯页面色。
            // 放在最外层，这样连导航栏内边距也一起盖住，底部不会漏出别的颜色。
            .background(
                Brush.verticalGradient(
                    0f to background.copy(alpha = 0f),
                    0.55f to background,
                    1f to background,
                ),
            )
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
            disabledTrackColor = lerp(pageColor(), primary, 0.35f),
            disabledProgressColor = lerp(pageColor(), primary, 0.35f),
            disabledContentColor = lerp(pageColor(), MiuixTheme.colorScheme.onPrimary, 0.55f),
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

/** 空白占位，用于卡片底部留白。 */
@Composable
fun Gap(height: Int = 12) {
    Spacer(Modifier.height(height.dp))
}
