package com.ayang.giftoolkit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全局「是否启用 OS4 玻璃」。
 *
 * 三个条件同时成立才算开：系统设置里开着、系统版本够、硬件支持 RenderEffect。
 * 任何一条不满足就整体退回普通 Miuix —— 不能让某个页面单独尝鲜，
 * 否则会出现「顶栏是玻璃、底栏是实心」的割裂，也正是需求里说的那种不一致。
 */
val LocalGlassEnabled = compositionLocalOf { false }

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
    val backdrop = rememberLayerBackdrop()
    return remember(enabled, backdrop) { GlassScope(enabled, backdrop) }
}

/**
 * 页面顶栏：玻璃可用时用 [GlassTopAppBar]，否则退回普通 [TopAppBar]。
 *
 * 颜色与间距都交给 Miuix 自己的两套实现，调用方不要再叠 padding，
 * 否则玻璃的 band 会和标题错位（大标题折叠动画按顶栏自身高度计算）。
 */
@Composable
fun PageTopBar(
    title: String,
    glass: GlassScope,
    isScrolled: Boolean,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior? = null,
    largeTitle: String = title,
    subtitle: String = "",
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    if (glass.enabled) {
        GlassTopAppBar(
            title = title,
            isContentScrolled = isScrolled,
            progressiveBlur = true,
            backdrop = glass.contentBackdrop,
            largeTitle = largeTitle,
            subtitle = subtitle,
            scrollBehavior = scrollBehavior,
            style = GlassDefaults.style(MiuixTheme.colorScheme.background.luminance() < 0.5f),
            navigationIcon = navigationIcon,
            actions = actions,
        )
    } else {
        TopAppBar(
            title = title,
            largeTitle = largeTitle,
            subtitle = subtitle,
            scrollBehavior = scrollBehavior,
            navigationIcon = navigationIcon,
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
