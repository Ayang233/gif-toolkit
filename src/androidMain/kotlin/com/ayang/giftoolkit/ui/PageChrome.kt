package com.ayang.giftoolkit.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 底栏在内容区上占掉的**视觉**高度：胶囊 + 它的悬浮外边距 + 一点呼吸空间。
 *
 * 为什么不用 `Scaffold` 的 innerPadding：底栏是画在录制层**外面**的浮层（见 AGENTS.md 第 1 条），
 * 不占 Scaffold 的 bottomBar 槽位，所以 Scaffold 不知道它存在。这个值由 `App.kt` 单点算、
 * 单点下发 —— 高度公式一变（比如以后调胶囊高度），也只改一处。
 */
val LocalBottomBarClearance = compositionLocalOf { 0.dp }

/** 玻璃底栏胶囊高度，与 `GlassNavigationBarDefaults.Height` 保持一致。 */
val GlassBarHeight: Dp = 54.dp

/**
 * 底栏占位高度。
 *
 * 外边距规则镜像 `GlassNavigationBar` 内部那套（导航栏 ≤16dp → 28dp；≥28dp → 跟随；
 * 中间 → 12dp + 导航栏），这样内容末尾的留白和胶囊实际位置是同一套几何。
 * 库没有把这个值暴露出来，只能在这里复刻 —— 上游若改了，这里要跟着改。
 */
@Composable
fun rememberBottomBarClearance(): Dp {
    val navInset = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()
    val floatingMargin = when {
        navInset <= 16.dp -> 28.dp
        navInset >= 28.dp -> navInset
        else -> 12.dp + navInset
    }
    return remember(navInset, floatingMargin) { GlassBarHeight + floatingMargin + 12.dp }
}

/**
 * 页面滚动修饰符，对齐官方 `utils.pageScrollModifiers`。
 *
 * 关键是 [overScrollVertical] 把下拉过冲量喂给顶栏状态：有了它，顶栏的 band（渐进模糊带）
 * 才会跟着手指一起走。只挂 `nestedScroll` 时，band 只会「到了某个位置忽然出现」，很生硬。
 *
 * [scrollEndHaptic] 给滚动到边界一个轻微触感反馈，这是 OS4 列表的默认手感。
 */
@Composable
fun Modifier.pageScrollModifiers(
    scrollBehavior: ScrollBehavior,
    scrollEndHaptic: Boolean = true,
): Modifier {
    val state = scrollBehavior.state
    val onOffsetChanged: (Float) -> Unit = remember(state) {
        { offset -> state.overscrollOffset = offset }
    }
    return this
        .then(if (scrollEndHaptic) Modifier.scrollEndHaptic() else Modifier)
        .overScrollVertical(onOffsetChanged = onOffsetChanged)
        .nestedScroll(scrollBehavior.nestedScrollConnection)
        .fillMaxHeight()
}

/**
 * 页面正文的内边距：顶部让开顶栏，底部让开玻璃底栏。
 *
 * 取两者较大的那个，而不是相加 —— 工具页的 `bottomBar`（主操作条）已经占了一部分，
 * 再叠加底栏留白会在按钮下面留出一条空带。
 */
@Composable
fun pageContentPadding(
    innerPadding: PaddingValues,
    extraTop: Dp = 2.dp,
    extraBottom: Dp = 12.dp,
    horizontal: Dp = 0.dp,
    reserveNavBar: Boolean = true,
): PaddingValues {
    // 二级页没有底栏（进入子页面时它已淡出），此时不该为它预留高度，
    // 否则列表末尾会多出一块 100dp 左右的空白。
    val clearance = if (reserveNavBar) LocalBottomBarClearance.current else 0.dp
    val top = innerPadding.calculateTopPadding() + extraTop
    val bottom = maxOf(innerPadding.calculateBottomPadding(), clearance) + extraBottom
    return remember(top, bottom, horizontal) {
        PaddingValues(start = horizontal, end = horizontal, top = top, bottom = bottom)
    }
}

/**
 * 一种页面骨架，六个页面共用。
 *
 * 把「Scaffold + 玻璃顶栏 + 录制层 + 懒列表」固定成**唯一**写法，比在每个页面各写一遍更安全：
 * 玻璃顶栏必须是录制层的兄弟节点，这个顺序一旦写错就会采样到自己、libhwui 原生栈溢出闪退
 * （AGENTS.md 第 1 条，历史真崩过）。集中到一处以后，页面里就不再有出错的机会。
 */
@Composable
fun Os4ListPage(
    title: String,
    glass: GlassScope,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    extraBottom: Dp = 12.dp,
    horizontalPadding: Dp = 0.dp,
    reserveNavBar: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { listState.canScrollBackward } }

    Scaffold(
        topBar = {
            PageTopBar(
                title = title,
                glass = glass,
                isScrolled = isScrolled,
                scrollBehavior = scrollBehavior,
                subtitle = subtitle,
                onBack = onBack,
                // 宽屏顶栏没有大标题这一档（官方 AdaptiveTopAppBar 同款判断）
                expandable = !LocalWideLayout.current,
                actions = actions,
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        ContentBackdropBox(glass) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(scrollBehavior),
                // 顶部改用 contentPadding：内容才能滚到顶栏下面被玻璃采样到。
                contentPadding = pageContentPadding(
                    innerPadding = padding,
                    extraBottom = extraBottom,
                    horizontal = horizontalPadding,
                    reserveNavBar = reserveNavBar,
                ),
                content = content,
            )
        }
    }
}
