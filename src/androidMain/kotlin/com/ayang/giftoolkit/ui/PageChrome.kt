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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
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

/**
 * 底栏占位高度。
 *
 * 胶囊高度直接取库的 `GlassNavigationBarDefaults.Height`（54dp），不再本地留一份 54.dp ——
 * 之前硬编码过，上游一改这个值，顶栏高度和内容末尾留白就对不上了。
 *
 * 外边距规则仍要镜像 `GlassNavigationBar` 内部那套（导航栏 ≤16dp → 28dp；≥28dp → 跟随；
 * 中间 → 12dp + 导航栏），因为库**没有把这个 margin 暴露出来**（`floatingMargin` 是函数内
 * 局部变量）。这是全项目唯一"复刻库内部公式"的地方，上游若改规则这里要跟着改。
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
    return remember(navInset, floatingMargin) {
        GlassNavigationBarDefaults.Height + floatingMargin + 12.dp
    }
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
    // 宽屏玻璃侧栏叠在内容之上，内容靠内边距让位（随展开进度变化，折叠态为 0）。
    // 官方 `AppContent.kt:219-229` 也是让页面内容让位，而不是把录制层挤窄。
    //
    // **只让 start 一侧**：Rail 只在起始边（LTR 是左边）。之前 start / end 都加了同一个值，
    // 展开态下右侧也白白空出 250dp，内容被压成一条竖带（1200dp 下实测可见）。
    //
    // 侧栏让位宽度是**逐帧变化**的值，所以这里拿到函数后不立即求值：
    // 交给 `PaddingValues.calculate*Padding`（LazyColumn 在量测期调用），
    // 侧栏展开动画期间只重新测量、不重组整个页面。
    //
    // **起始/结束边的系统安全区必须一起算进来。** Scaffold 的 `innerPadding` 横向部分
    // = `systemBars ∪ displayCutout`，横屏时挖孔会落在起始边（本机 128px = 64dp）。
    // 侧栏自己是吃这段安全区的（`GlassNavigationRail` 内部 `windowInsetsPadding`），
    // 内容以前只让 `railInset`，于是横屏下侧栏整体右移 64dp、内容没跟上 ——
    // 面板右边界压在列表行图标上，这就是「1200dp 横屏侧栏遮挡一级页面内容」的根因。
    // 竖屏时挖孔在顶部、横向安全区为 0，两个数字刚好相等，所以上一轮漏掉了这个场景。
    val railInset = LocalRailInset.current
    val layoutDirection = LocalLayoutDirection.current
    val safeStart = innerPadding.calculateLeftPadding(layoutDirection)
    val safeEnd = innerPadding.calculateRightPadding(layoutDirection)
    val top = innerPadding.calculateTopPadding() + extraTop
    val bottom = maxOf(innerPadding.calculateBottomPadding(), clearance) + extraBottom
    return remember(top, bottom, horizontal, safeStart, safeEnd, railInset) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
                val extra = if (layoutDirection == LayoutDirection.Ltr) railInset() else 0.dp
                return horizontal + safeStart + extra
            }

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp {
                val extra = if (layoutDirection == LayoutDirection.Rtl) railInset() else 0.dp
                return horizontal + safeEnd + extra
            }

            override fun calculateTopPadding(): Dp = top

            override fun calculateBottomPadding(): Dp = bottom
        }
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
    /**
     * 页面级弹层槽位（Dialog / BottomSheet）。
     *
     * 必须是**Scaffold 作用域内**、且在 `ContentBackdropBox` **之外**的一组兄弟节点：
     * - 在 Scaffold 里：`GlassDialog` / `GlassBottomSheet` 靠 `LocalDialogStates` 找宿主
     *   （由 Scaffold 提供并绘制，`Scaffold.kt:108-118`）。放到页面外面就只剩一个没人绘制的列表，
     *   点了没反应、也不报错 —— 之前作品操作面板退回系统窗口版就是这个原因。
     * - 在录制层外：弹层要采样页面背景，如果它自己也在录制层里，就会采样到自己 →
     *   libhwui `prepareNode` 原生栈溢出（AGENTS.md 第 1 条）。
     *
     * 参数就是页面正文录制层（玻璃关闭时为 `null`，组件会退回不透明底），
     * 弹层把它直接传给 `GlassBottomSheet(backdrop = ...)`。
     */
    pageOverlay: @Composable (LayerBackdrop?) -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { listState.canScrollBackward } }

    Scaffold(
        // 录制层**不能**挂在 Scaffold 上，只能挂在内容区（见 ContentBackdropBox）。
        //
        // 曾尝试照官方 `GlassPage.kt:242`（`Scaffold(modifier = Modifier.layerBackdrop(pageBackdrop))`）
        // 把录制层上移，理由是那样顶栏的 `localPositionOf` 反算能落在录制内容上。
        // 实测直接 SIGSEGV：`RenderNode::prepareTreeImpl` 在 prepareTreeImpl / prepareListAndChildren
        // 之间无限递归（cause: stack overflow）。
        //
        // 为什么官方敢那么写：它顶栏采的是 `LocalTopAppBarBackdrop`（由 BlurredBar 提供的**另一个**
        // backdrop），而挂在 Scaffold 上的 `pageBackdrop` 只给底栏/弹层用，两者不是同一个 ——
        // 所以顶栏不在自己采样源的录制树里。我们顶栏与内容**共用** `GlassScope.contentBackdrop`，
        // 录制层一旦包含顶栏，顶栏采样时就会把"正在采样的自己"录进去，递归到栈溢出。
        //
        // 结论：录制层排除顶栏是这套结构下的唯一安全解。顶栏反算得到负 y，
        // 采样区间落在内容区顶部 —— 这正是"顶栏正下方的内容"，语义是对的。
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
        // 与录制层同级：能拿 Scaffold 的弹层宿主，又不会被录进 backdrop。
        //
        // 采样源优先取**一级页录制层**（[LocalRootBackdrop]，录在 Pager 外面）。
        // 页面自己那层只在没有 Pager 的二级页里用：Pager 页面的页面内录制层上报的是槽位坐标
        // （第 2 个 tab 是 x = 1080），跟弹层的真实窗口坐标差一整页宽，采样会整块落空。
        pageOverlay(
            LocalRootBackdrop.current ?: glass.contentBackdrop.takeIf { glass.enabled },
        )
    }
}
