package com.ayang.giftoolkit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 一个页面自己的玻璃作用域。
 *
 * **硬约束**：这里提供的 [contentBackdrop] 只能用来给「画在录制层外面」的顶栏/底栏采样。
 * 玻璃容器必须是录制层的**兄弟节点**，一旦成为子节点就会采样到自己，
 * libhwui 的 `RenderNode::prepareTreeImpl` 原生栈溢出直接闪退（AGENTS.md 第 1 条）。
 * 所以页面结构必须是：
 *
 * ```
 * Scaffold(topBar = { GlassTopAppBar(backdrop = contentBackdrop) }) { padding ->
 *     Box(Modifier.contentBackdropModifier) { ...滚动内容... }
 * }
 * ```
 */
@Stable
class GlassScope internal constructor(
    val enabled: Boolean,
    val contentBackdrop: LayerBackdrop?,
) {
    /** 加在「要被采样的内容」上的录制层。 */
    val contentBackdropModifier: Modifier
        get() = if (enabled && contentBackdrop != null) Modifier.layerBackdrop(contentBackdrop) else Modifier
}

/**
 * 页面正文录制层，供**页面内弹层**（下拉）采样。
 *
 * 官方 `GlassPage.kt:447` 给 `GlassDropdownPreference` 传的就是整页的 `backdrop`，
 * 不是卡片自己的局部层 —— 这是本轮改掉的一处偏差。
 *
 * 为什么必须用整页而不是卡片局部层：卡内弹层会**伸出卡片边界**，局部录制层没有那部分像素，
 * 采样到边界之外就只剩一圈材质、没有背景模糊。实测「预测性返回动画」那张单行卡片
 * （卡片只有 ~56dp 高，弹层有 5 项）弹层下半截明显发灰、能看出一条横向硬边；
 * 而「主题模式」那张三行卡片高得多，弹层基本落在卡片里，所以看起来是正常的 ——
 * 两个菜单的「不一致」就是这么来的，不是它们用了不同组件。
 *
 * 坐标不是问题：库内部用 `layerCoordinates.localPositionOf(...)`（`LayerBackdrop.kt:81`）
 * 换算，跨容器是精确的。早期那次的脏斑是录制层**没垫底色**（`rememberPageBackdrop` 已补上
 * `drawRect(pageColor())`），与容器层级无关。
 *
 * **不能**把录制层做进弹层宿主里：弹层由 Scaffold 的 popup 层绘制，是 `ContentBackdropBox` 的
 * 兄弟节点；一旦录制层包含弹层就会采样到自己 → libhwui 原生栈溢出（AGENTS.md 第 1 条）。
 */
val LocalPageBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/**
 * 页面级录制层：先铺一层页面底色，再录内容。
 *
 * **必须先铺底色再录内容**（官方 `rememberBlurBackdrop` 同款）：裸的 `rememberLayerBackdrop()`
 * 只录内容，卡片之间的空隙、列表 padding 区都是透明的；玻璃采样并模糊后，这些位置仍然是透明
 * —— 于是顶上那条带上内容原样透出来（看着像"糊影/重影"），而不是被糊成一层柔和的底色。
 * 填充用 `pageColor()` 而不是 `surface`：页面实际画的就是这个色。
 *
 * 抽成一处是因为 `App.kt`（给底栏/侧栏采样）和每个页面（给顶栏采样）各需要一份，
 * 之前两处各写了一遍同样的 `drawRect + drawContent`。
 */
@Composable
fun rememberPageBackdrop(): LayerBackdrop {
    val surfaceColor = pageColor()
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
fun rememberGlassScope(enabled: Boolean = LocalGlassEnabled.current): GlassScope {
    // 无论开不开都建一个实例：创建本身只是分配对象，
    // 真正花 GPU 的是 layerBackdrop 这个 Modifier，只在 enabled 时才挂上去。
    val backdrop = rememberPageBackdrop()
    return remember(enabled, backdrop) { GlassScope(enabled, backdrop) }
}

/**
 * 页面顶栏：玻璃可用时用 [GlassTopAppBar]，否则退回普通 [TopAppBar]。
 *
 * 颜色与间距都交给 Miuix 自己的两套实现，调用方不要再叠 padding，
 * 否则玻璃的 band 会和标题错位（大标题折叠动画按顶栏自身高度计算）。
 */
/**
 * 页面顶栏。
 *
 * 返回键统一走 [onBack]，不再让每个页面自己写一个 `IconButton` —— 玻璃档下要包成
 * [GlassIconButton] 才能拿到 OS4 那种「图标浮在小玻璃胶囊里」的观感，
 * 而它的大小/材质/投影全都从 `LocalGlassTopAppBarContext` 继承，
 * 页面里手写一个裸 `IconButton` 是拿不到这些的（这也是之前顶栏不像 OS4 的原因之一）。
 */
@Composable
fun PageTopBar(
    title: String,
    glass: GlassScope,
    isScrolled: Boolean,
    onBack: (() -> Unit)? = null,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior? = null,
    largeTitle: String = title,
    subtitle: String = "",
    /**
     * 大标题是否可展开。
     *
     * 宽屏下必须传 `false`：平板/横屏的顶栏是「居中窄标题」，没有大标题这一档
     * （官方 `AdaptiveTopAppBar` 传的就是 `expandable = !isWideScreen`）。
     * 手机上传 `true`，于是「滚动 → 大标题收起成小标题」这套折叠动画照常工作。
     */
    expandable: Boolean = true,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    // 返回键照官方 `component/BackNavigationIcon.kt` 写：
    // 玻璃档用 Glass 族 **ChevronBackward + 28dp + GlassIconButton**，非玻璃档才退回
    // extended 的 `Back` + 24dp + IconButton。之前两档都用 extended 的 `Back`，
    // 线宽比同栏其它 Glass 图标粗一档，也是 AGENTS.md 第 10 条"只用 Glass 族"的自相矛盾处。
    // 28dp 是因为 Glass 族是 28dp 画布（`buildHyperOSIcon` 的 defaultWidth），
    // 塞进 24dp 槽会明显偏小。
    val layoutDirection = LocalLayoutDirection.current
    val backIcon: @Composable () -> Unit = {
        if (onBack != null) {
            val glassIcon: @Composable () -> Unit = {
                Icon(
                    imageVector = MiuixIcons.Glass.ChevronBackward,
                    contentDescription = "返回",
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            // RTL 下箭头要跟着反向（官方同款处理）
                            if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                        },
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
            if (glass.enabled) {
                GlassIconButton(onClick = onBack) { glassIcon() }
            } else {
                IconButton(onClick = onBack) {
                    Icon(MiuixIcons.Back, "返回", Modifier.size(24.dp))
                }
            }
        } else {
            navigationIcon()
        }
    }
    if (glass.enabled) {
        // 渐变带画笔与带宽必须**显式**传，不能吃默认值就走人。
        //
        // `GlassTopAppBar` 内部（GlassTopAppBar.kt:392-458）是二选一：
        //   progressiveBlur && backdrop != null → 走 progressiveTextureBlur（AGSL 运行时着色器）
        //   否则                                → 走 bandBrush 渐变（零 AGSL）
        // 而 `bandBrush` 的类型决定了走哪条绘制路径：传 `TopAppBarBandBrush`（bandBrush() 的返回值）
        // 时库会用 `TopAppBarFadeBrush`，渐变高度 = bandOverhang + 12dp，尾部是 17 段平滑衰减，
        // **这才是"看不到边界"的关键**；传其它 Brush 就退化成 `background(brush)` 的拉伸平涂。
        //
        // 所以哪怕当前走了 progressive 分支，也要把 bandBrush / bandOverhang 备好：
        // AGSL 在个别 GPU 上编译失败是**静默**的（图层不产出内容，不抛异常），
        // 那时库会落回这条分支，参数不对就会露出生硬的矩形边。
        val surfaceColor = MiuixTheme.colorScheme.surface
        val bandBrush = remember(surfaceColor) { GlassTopAppBarDefaults.bandBrush(surfaceColor) }
        GlassTopAppBar(
            title = title,
            isContentScrolled = isScrolled,
            expandable = expandable,
            progressiveBlur = true,
            backdrop = glass.contentBackdrop,
            largeTitle = largeTitle,
            subtitle = subtitle,
            scrollBehavior = scrollBehavior,
            // 不传 style，吃 `GlassDefaults.Style`（= CommonMediumRegularLowLight），
            // 与官方 `AdaptiveTopAppBar` 一致 —— 它也不按明暗分档。
            // 之前用 `GlassDefaults.style(isDark)`，深色下取的是 CommonMediumRegularDark，
            // 玻璃厚度/高光与官方不同；明暗由材质内部的 `isDarkTheme()` 处理，不需要调用方判断。
            // 背景带与标题的透明度乘子。官方在"模糊未生效"时会把它降到 0，避免
            // progressive 与 band 两层同时上色；我们没有那层判断，恒为 1 即可。
            backgroundAlpha = { 1f },
            bandBrush = bandBrush,
            bandOverhang = GlassTopAppBarDefaults.BandOverhang,
            navigationIcon = backIcon,
            actions = actions,
        )
    } else {
        TopAppBar(
            title = title,
            expandable = expandable,
            largeTitle = largeTitle,
            subtitle = subtitle,
            scrollBehavior = scrollBehavior,
            navigationIcon = backIcon,
            actions = actions,
        )
    }
}

/**
 * 包住页面正文的录制层。
 *
 * 用 Box 而不是给 LazyColumn 加 Modifier：懒列表的 item 在滚动时才会被组合，
 * 录制层挂在列表上会在滑动过程中反复重建渲染节点。
 */
@Composable
fun ContentBackdropBox(
    glass: GlassScope,
    content: @Composable () -> Unit,
) {
    val backdrop = glass.contentBackdrop.takeIf { glass.enabled }
    // 页面内弹层（下拉）采样同一个录制层：官方 GlassPage 也是给弹层传整页 backdrop。
    CompositionLocalProvider(LocalPageBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().then(glass.contentBackdropModifier)) { content() }
    }
}
