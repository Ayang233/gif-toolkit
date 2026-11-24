package com.ayang.giftoolkit.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
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
 * 页面**真正铺在屏幕上**的那层底色（= `MiuixTheme.colorScheme.surface`）。
 *
 * 与 [pageColor()] 不是一回事，两个都要留着：
 *
 * - [pageColor()] 是「由卡片色推出来的次级表面」，比页面深一档（浅色 #F6F6F6）
 *   或浅一档（深色 #1E1E1E），用在预览舞台、脚本对比卡这类**要跟页面拉开一档**的位置。
 * - [pageBaseColor] 是页面本身。页面是 `Scaffold` 的 `containerColor`，默认就是 `surface`
 *   （静态浅色 #F7F7F7、静态深色 #000000、Monet 深色 #131316 —— 均为第十轮实测截图取色）。
 *
 * 为什么必须分开：玻璃录制层「垫底色」和底部主操作条「遮挡穿透」这两个用途，
 * 垫的必须是**页面真实底色**，否则玻璃面板/操作条会在页面上留下一块比页面亮的方块。
 * 历史上这两处都写成了 [pageColor()]，浅色下只差 1/255 看不出来，
 * 深色静态下是 #1E1E1E 铺在 #000000 上 —— 这就是第十轮「横屏侧栏玻璃发灰」
 * 的直接成因（`docs/os4-round10-audit.md` §2.2）。
 *
 * 顺带记一条已经过时的旧结论：`pageColor()` 最早的注释说「Monet 会把
 * `surface` / `surfaceContainer` 压成同一个值」。第十轮在本基线 `3177a684` 上逐套配色取色，
 * 四套都不同（Monet 浅色页面 #F9F9FF / 卡片 #EDEDF4，Monet 深色 #131316 / #1F1F22），
 * 所以页面直接用 `surface` 不会和卡片糊在一起。
 */
@Composable
fun pageBaseColor(): Color {
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    if (LocalAmoled.current && dark) return Color.Black
    return MiuixTheme.colorScheme.surface
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
    /**
     * 卡内自定义内容的内边距，默认吃库的 `CardDefaults.InsideMargin`（0）。
     *
     * 只有"整块自定义内容"（预览画布、结果卡）需要传 `PaddingValues(16.dp)`，
     * 这是官方 `component/CardSection.kt:43` / `ColorPickerSection.kt:55` 的写法：
     * 内边距交给 `Card(insideMargin = …)`，调用方不再套一层 `Box(Modifier.padding(16.dp))`。
     * 行式内容（Preference / BasicComponent）保持 0 —— 行自己带 16dp 内边距。
     */
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = bottomPadding),
        insideMargin = insideMargin,
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
    val Row: Dp = 28.dp

    /** 顶栏动作按钮（`GlassIconButton` 内部 44dp 胶囊）。 */
    val Action: Dp = 28.dp

    /**
     * 行内小动作（例如素材行的「清除」）。
     *
     * 这类图标跟行标题同排、视觉上要比行首图标轻一档，单独给一个值，
     * 不要在调用点写 `19.dp` —— 尺寸只留一个出处。
     */
    val Inline: Dp = 19.dp

    /** 空态插画。 */
    val Empty: Dp = 44.dp

    /**
     * 媒体区 / 提示语里的内联图形（预览占位图标）。
     *
     * 与 [Empty] 分开：空态是"整页没有内容"的插画，视觉要重；这里是卡片里一块画布的占位，
     * 超过 28dp 会把提示语挤到画布外面。
     */
    val Media: Dp = 28.dp
}

/**
 * 下拉选择行。
 *
 * 玻璃档走 [GlassDropdownPreference]：弹出的是 Scaffold 托管的**玻璃浮层**，
 * 和 InstallerX 的 `InstallerMiuixSpinnerPreference` 同一个做法；
 * 普通 `WindowDropdownPreference` 弹出来是白底卡片，一眼就不是 OS4。
 *
 * 采样源取 [LocalRootBackdrop] **优先**，页面正文层 [LocalPageBackdrop] 只作二级页回退。
 *
 * 这条规则与 Dialog / BottomSheet 是同一条（`PageChrome.kt` 的 `pageOverlay`）：
 * `GlassDropdownPreference` 默认 `renderInRootScaffold = true`，弹层由**最外层** Scaffold 绘制，
 * 上报的是真实窗口坐标；而一级页的页面正文层在 `HorizontalPager` 里，上报的是槽位坐标
 * （第 2 个 tab 报 x = 1080）。两者相减差一整页宽，采样区间整块落在录制层之外 ——
 * 第六轮「作品菜单不模糊」就是这个原因。一级页现在还没有下拉行，所以这个缺陷一直潜伏着，
 * 第十轮按同一条规则提前修掉（`docs/os4-round10-audit.md` §4.3）。
 *
 * 传整页层不会偏移：库用 `layerCoordinates.localPositionOf(...)` 换算坐标。
 * 早期那次的脏斑是录制层没垫底色，`rememberPageBackdrop` 已经用 `drawRect(pageBaseColor())` 补掉了。
 *
 * `visuals` 不再显式传：默认就是 `GlassDefaults.Style`（`GlassPopup.kt` 的
 * `GlassPopupDefaults.visuals()`），与顶栏显式传的 `GlassDefaults.Style` 同档。
 * 第九轮之前这里传 `GlassDefaults.style(isDark)`，深色下会取到
 * `CommonMediumRegularDark`，弹层材质比顶栏厚一档 —— 同一个决策不要在两个地方各写一遍。
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
    // 一级页走 Pager 外那层（真实坐标），二级页没有 root 层就回落到页面层。
    val backdrop = LocalRootBackdrop.current ?: LocalPageBackdrop.current
    GlassDropdownPreference(
        onSelectedIndexChange = onSelect,
        items = items,
        selectedIndex = selectedIndex,
        title = title,
        // 没有页面上下文（不在 ContentBackdropBox 里）或关掉玻璃时退回不透明底卡，
        // 这是库的官方兜底，不是我们的主路径。
        backdrop = backdrop.takeIf { glassEnabled },
        summary = summary,
        enabled = enabled,
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

/**
 * 说明段落。
 *
 * 左边距 28dp、上下 8dp，与官方 `SmallTitleDefaults.InsideMargin`（28dp, 8dp）同一套节奏 ——
 * 28dp = 卡片外边距 12dp + `BasicComponentDefaults.InsideMargin` 16dp，
 * 和分组标题、卡片里的行标题落在同一条左边线上（之前写 10dp，比分组标题松一档）。
 */
@Composable
fun NoteText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * 预览舞台。
 *
 * 舞台底色取 [pageColor]（页面色再深一档的次级表面），透明背景的 GIF 也能看清边界；
 * 空态给一个图标 + 提示语，避免一大块空白。
 */
@Composable
fun PreviewStage(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    aspect: Float = 4f / 3f,
    placeholder: String = "选择素材后显示预览",
) {
    // 高度上限按**窗口高度**算，不是按可用约束算：预览在 LazyColumn 里，
    // item 的 maxHeight 是无穷大，按约束取会退回"宽度算出来的那个离谱高度"。
    // 横屏窗口只有 ~410dp 高，16:9 铺满宽要 620dp —— 预览会把参数区整块顶出屏幕，
    // 空态提示语居中在视口之外，看起来就是"预览区是空的"。上限压到窗口高度的 42%。
    val density = LocalDensity.current
    // 高度上限读窗口容器尺寸（`LocalWindowInfo.containerSize`）—— 与官方 example 的
    // `shouldShowSplitPane()` 同一来源。分屏 / 自由窗口下 containerSize 就是窗口实际拿到的区域，
    // 不需要再自己注入一层 `LocalContainerSize`（1.15 已随上游删除那一层）。
    val windowHeight = LocalWindowInfo.current.containerSize.height
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 宽度取**实际可用宽度**（展开 Rail 时卡片更窄），高度上限取窗口高度的一个比例。
        // 横屏这种"矮窗口"（<500dp）要按更小的比例算：竖屏 42% 时预览能完整落在参数区上方，
        // 横屏 42% 会把预览推到「开始生成」按钮底下，占位图标和提示语被按钮压住，
        // 看上去就是一块空白的圆角框。所以矮窗口取 32%。
        val cap = with(density) {
            val h = windowHeight.toDp()
            h * if (h < 500.dp) 0.32f else 0.42f
        }
        val stageHeight = (maxWidth / aspect.coerceIn(0.4f, 3f)).coerceAtMost(cap)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stageHeight)
                // 连续曲率圆角用库的 `squircleSurface`（官方 Card/Button 本体也是 squircle），
                // 不要自己 clip(RoundedCornerShape(...)) + background()：四角会是正圆弧。
                // 半径取 `CardDefaults.CornerRadius`（16dp）：画布与卡片同档，
                // 之前写死 12dp，卡片 16dp 包着 12dp 的画布，四角在视觉上对不齐。
                .squircleSurface(color = pageColor(), cornerRadius = CardDefaults.CornerRadius),
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
                        modifier = Modifier.size(IconSizes.Media),
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
 *    所以这里用 `pageBaseColor()` 纯色：与页面底色**完全相同**，视觉上等于没有额外背景，
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
    // 左右留白 24dp：MIUIX 示例里页面底部那条 `bottomBar`（GlassPage.kt:289）就是
    // `padding(start = 24.dp, end = 24.dp, top = 20.dp)`。内容卡片是 12dp，两者并存；
    // 「贴着屏幕底部的那一条」用 24dp 是官方约定，之前写 12dp 是拿卡片网格去套底栏。
    val sideGap = 24.dp
    // 展开态玻璃侧栏叠在内容之上，底部主操作条也要跟着让位，否则按钮左端会被面板压住。
    // 折叠态（手机横屏 / 竖屏）让位为 0，与之前完全一致。
    // 让位宽度逐帧变化 → 走 railStartInset()（量测期读取），不在组合期解包。
    // 通栏控件左右留白要参与圆角安全区推导：屏幕圆角是圆弧，
    // 距离底部越近，水平方向的可用宽度越窄，贴边会被切掉一角。
    // 24dp 是官方 `bottomBar` 的下限（`maxOf(24.dp, insets + 8.dp)`），
    // 大圆角机型上 safeBottomGap 会算出比它更大的值。
    //
    // 系统安全区（横屏挖孔、侧边系统栏）也要算进来：这一层不在 Scaffold 的内容槽位里，
    // 拿不到 `innerPadding`，必须自己取（`rememberHorizontalSafeInsets`）。之前完全没算，
    // 于是横屏下按钮左端会压在挖孔/margin 上，右端又会顶到侧边系统栏。
    val safeInsets = rememberHorizontalSafeInsets()
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val safeStart = safeInsets.calculateLeftPadding(layoutDirection)
    val safeEnd = safeInsets.calculateRightPadding(layoutDirection)
    val bottomGap = maxOf(
        safeBottomGap(corner = rememberNavSystemCornerRadius(), sideGap = sideGap + safeStart + 4.dp),
        24.dp,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 遮挡穿透用的必须是**页面真实底色**，不是次级表面 pageColor()：
            // 写错时深色下这条操作条会比页面亮一档，横屏时尤其明显（本轮 §2.2 实测 #1E1E1E vs #000000）。
            .background(pageBaseColor())
            .navigationBarsPadding()
            .padding(end = sideGap + safeEnd, top = 20.dp)
            .padding(bottom = bottomGap)
            // base = 常规边距 + 起始边安全区；railStartInset 再加侧栏让位（量测期读）。
            .railStartInset(sideGap + safeStart),
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
 * 空闲：轨道直接是 `primary`。
 *
 * 禁用态走**方案 A**（`disabledPrimaryButton` + `disabledOnPrimaryButton`，2026-11-18 敲定）：
 * 保留主操作按钮的蓝底语义，不是灰色占位块。对比度实测浅色 1.43:1 / 深色 2.40:1 /
 * Monet 浅色 1.47:1，四方案横向对比记录在 `docs/os4-round10-audit.md` §3 与
 * 「设置 → 主题 → 调试」的对比页（仅 debug 包）。
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
            // 禁用态配色（**方案 A**，2026-11-18 敲定）：MIUIX 主按钮禁用态的标准配对
            // `disabledPrimaryButton` + `disabledOnPrimaryButton`，保留"这是主操作按钮"的蓝底语义。
            // 修前的默认配对是 `disabledSecondaryVariant` + `disabledOnPrimaryButton`，
            // 浅色下灰底白字只有 1.12:1（几乎读不出字）。
            // 三套配色的对比度实测：浅色 1.43:1 / 深色 2.40:1 / Monet 浅色 1.47:1，
            // 完整对比页见 `docs/os4-round10-audit.md` §3。
            disabledTrackColor = MiuixTheme.colorScheme.disabledPrimaryButton,
            disabledProgressColor = MiuixTheme.colorScheme.disabledPrimaryButton,
            disabledContentColor = MiuixTheme.colorScheme.disabledOnPrimaryButton,
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
            // 尺寸走库的默认值（20dp / 2dp / 2dp），不自己另给一套数字。
            InfiniteProgressIndicator(
                color = colors.contentColor,
                size = ProgressIndicatorDefaults.DefaultInfiniteProgressIndicatorSize,
                strokeWidth = ProgressIndicatorDefaults.DefaultInfiniteProgressIndicatorStrokeWidth,
                orbitingDotSize = ProgressIndicatorDefaults.DefaultInfiniteProgressIndicatorOrbitingDotSize,
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
