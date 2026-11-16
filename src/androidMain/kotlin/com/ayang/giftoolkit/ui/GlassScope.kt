package com.ayang.giftoolkit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.material.GlassDefaults
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

@Composable
fun rememberGlassScope(enabled: Boolean = LocalGlassEnabled.current): GlassScope {
    // 无论开不开都建一个实例：创建本身只是分配对象，
    // 真正花 GPU 的是 layerBackdrop 这个 Modifier，只在 enabled 时才挂上去。
    //
    // **必须先铺一层页面底色再录内容**（官方 rememberBlurBackdrop 同款）：
    // 裸的 rememberLayerBackdrop() 只录内容，卡片之间的空隙、列表 padding 区都是透明的；
    // 顶栏采样并模糊后，这些位置仍然是透明 —— 于是顶栏那条带上，内容原样透出来（看着像"糊影/重影"），
    // 而不是被糊成一层柔和的底色。填充用 pageColor() 而不是 surface：页面实际画的就是这个色。
    val surfaceColor = pageColor()
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
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
    val backIcon: @Composable () -> Unit = {
        if (onBack != null) {
            if (glass.enabled) {
                GlassIconButton(onClick = onBack) {
                    Icon(MiuixIcons.Back, "返回", Modifier.size(24.dp))
                }
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
        GlassTopAppBar(
            title = title,
            isContentScrolled = isScrolled,
            expandable = expandable,
            progressiveBlur = true,
            backdrop = glass.contentBackdrop,
            largeTitle = largeTitle,
            subtitle = subtitle,
            scrollBehavior = scrollBehavior,
            style = GlassDefaults.style(MiuixTheme.colorScheme.background.luminance() < 0.5f),
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
    Box(Modifier.fillMaxSize().then(glass.contentBackdropModifier)) { content() }
}
