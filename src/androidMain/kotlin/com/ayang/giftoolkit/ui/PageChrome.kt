package com.ayang.giftoolkit.ui

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import androidx.compose.foundation.layout.fillMaxHeight
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 一级页底栏在内容区上占掉的高度，由 `App.kt` 里那个真正装着底栏的 `Scaffold` 测出来。
 *
 * 底栏现在放在 `Scaffold` 的 `bottomBar` 槽位（与官方 `CompactScreenLayout` /
 * `GlassPage` 同一个结构），高度由 Scaffold 自己量、以 `innerPadding` 形式下发，
 * 不再由我们复刻库内部的 `floatingMargin` 公式 —— 这正是上一版唯一的"抄库内部实现"处。
 *
 * 二级页（工具页 / 主题 / 关于 / 许可）不在这个 Scaffold 里，读到的就是默认 0。
 */
val LocalPageBottomInset = compositionLocalOf { 0.dp }

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
    /** 底栏高度：已作为真实 padding 施加在列表上（见 `Os4ListPage`），这里不再重复计入。 */
    barReserved: Dp = 0.dp,
): PaddingValues {
    // 一级页底栏由外层 Scaffold 的 bottomBar 槽位占据，它的高度经 `innerPadding` 传到这里。
    // 二级页不在那个 Scaffold 里，读到 0 —— 不会在列表末尾留出一块空带。
    val clearance = LocalPageBottomInset.current
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
    val bottom = (maxOf(innerPadding.calculateBottomPadding(), clearance) - barReserved)
        .coerceAtLeast(0.dp) + extraBottom
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
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun Os4ListPage(
    title: String,
    glass: GlassScope,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    /** 底部固定操作条。为 `null` 表示这一页没有底栏（内容不受底栏遮挡）。 */
    bottomBar: (@Composable () -> Unit)? = null,
    extraBottom: Dp = 12.dp,
    horizontalPadding: Dp = 0.dp,
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
    /**
     * 页面背景，铺在 `Scaffold` **外面**（页面根部）。目前只有关于页用：整屏 OS3 渐变要一直
     * 延伸到状态栏下面，放进 Scaffold 内容区会在顶栏下方切出一条硬边。
     *
     * 传了它意味着"这一页自己铺背景"，于是顶栏改成：滚动到收起前完全透明、收起后才上实色
     * （与参考项目 InstallerX `MiuixAboutPage.kt` 的非玻璃档写法一致），标题透明度跟着
     * [largeHeader] 的滚动进度走。
     */
    background: (@Composable (progress: () -> Float) -> Unit)? = null,
    /**
     * 顶栏的采样源覆盖。默认是页面正文录制层。
     *
     * 只有"正文层在顶栏区域没有像素"的页面要传：关于页的正文层刻意不垫底色、顶部还有 40dp
     * 图标留白，所以它传的是 `rememberCombinedBackdrop(渐变层, 正文层)`（见 AGENTS 第 1 条覆盖约束）。
     */
    topBarBackdrop: Backdrop? = null,
    /**
     * 固定在列表下方、随滚动淡出的自定义头部（关于页的图标 / 名称 / 版本）。
     *
     * 传了它以后：
     * - 列表首项自动插入一块与头部等高的透明占位（`logoSpacer`），头部因此不会被卡片压住；
     * - 滚动进度 = 首项滚动偏移 / 头部高度，作为参数交给 [largeHeader]，页面自己决定怎么淡出；
     * - 顶栏的 `isScrolled` 改为"进度到 1"（与参考项目一致：内容完全收起后顶栏才浮起）。
     */
    largeHeader: (@Composable (progress: () -> Float) -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    // `computedStateOf`（Compose 1.13 新 API，MIUIX 0.9.5-beta02 全面改用）：
    // 每次读重新计算、不缓存结果，但仍然只在结果变化时让组合失效 —— 正好适合这种廉价判据，
    // 省掉派生状态的缓存簿记与失效写回。官方 `MainPage.kt:104` / `AboutPage.kt:93` /
    // `ComponentPage.kt:144` 的 `isScrolled` 就是这一行。
    // 判据必须叠 `overscrollOffset < -1f`：页面不足一屏、继续往上拉时列表本身
    // `canScrollBackward` 仍是 false，但内容确实在动 —— 缺这一项，顶栏的按钮材质与
    // 渐进模糊会"拉不动"，观感上像顶栏卡住了。
    // 与官方 `GlassPage.kt:176` / `IconPage.kt:182` 逐字一致：
    // `canScrollBackward || scrollBehavior.state.overscrollOffset < -1f`；
    // 阈值用 `-1f` 而不是 `< 0f`，是迁移指南第 5.4 条的要求（亚像素与回弹边缘会误触发）。
    val isScrolled by remember {
        computedStateOf {
            listState.canScrollBackward || scrollBehavior.state.overscrollOffset < -1f
        }
    }

    // 头部高度：首帧用 300dp 兜底（与官方 `AboutPage.kt` 的 logoHeightDp 初值一致）。
    // 用 0 会让透明占位高度为 0 —— 首帧卡片压住图标/标题，且滚动进度恒为 0（判据 > 0 不成立）。
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerProgress: () -> Float = {
        when {
            largeHeader == null -> if (isScrolled) 1f else 0f
            listState.firstVisibleItemIndex > 0 -> 1f
            headerHeightPx > 0 ->
                (listState.firstVisibleItemScrollOffset.toFloat() / headerHeightPx).coerceIn(0f, 1f)
            else -> 0f
        }
    }
    val collapsed by remember { computedStateOf { headerProgress() == 1f } }

    val topBar: @Composable () -> Unit = {
        PageTopBar(
            title = title,
            glass = glass,
            isScrolled = if (largeHeader != null) collapsed else isScrolled,
            scrollBehavior = scrollBehavior,
            subtitle = subtitle,
            onBack = onBack,
            // 宽屏顶栏没有大标题这一档（官方 AdaptiveTopAppBar 同款判断）
            expandable = largeHeader == null && !LocalWideLayout.current,
            actions = actions,
            // 有自定义头部时，标题透明度跟着头部收起进度走（参考项目同款）
            titleAlpha = if (largeHeader != null) {
                { ((headerProgress() - 0.35f) / 0.65f).coerceIn(0f, 1f) }
            } else {
                null
            },
            // 自己铺背景的页面：收起前透明、收起后上实色，避免把渐变切出一块白条。
            // 收起后的实色必须是 `pageBaseColor()`，不能用 `colorScheme.surface` ——
            // AMOLED + 动态取色下两者不同值（#000000 vs #131316），顶栏会浮出一条比页面亮的带子。
            background = if (background != null) {
                if (collapsed) pageBaseColor() else Color.Transparent
            } else {
                null
            },
            backdrop = topBarBackdrop,
        )
    }

    val body: @Composable (PaddingValues) -> Unit = { padding ->
        val layoutDirection = LocalLayoutDirection.current
        val density = LocalDensity.current
        val headerPadTop = padding.calculateTopPadding() + 40.dp
        val listTop = padding.calculateTopPadding() + 2.dp
        val headerHeight = with(density) {
            (if (headerHeightPx > 0) headerHeightPx else 300.dp.roundToPx()).toDp()
        }

        ContentBackdropBox(glass) {
                Box(Modifier.fillMaxSize()) {
                    if (largeHeader != null) {
                        // 头部画在列表**下面**：先画的是底层，后画的列表盖在上面。
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = padding.calculateLeftPadding(layoutDirection),
                                    end = padding.calculateRightPadding(layoutDirection),
                                    top = headerPadTop,
                                )
                                .onSizeChanged { headerHeightPx = it.height },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            largeHeader(headerProgress)
                        }
                    }
                    // 有底栏时，把底栏高度做成**真实布局内边距**，让列表视口停在底栏上方。
                    //
                    // 这是官方 Scaffold 的固有模型：正文按整屏测量（`ScaffoldLayout` 里
                    // `bodyContentPlaceable.place(0, 0)`，`contentWindowInsets` 只以 `PaddingValues`
                    // 形式告诉你），底栏是**盖在上面**的。于是"视口底边"实际上在底栏背后，
                    // 输入框获得焦点时 Compose 自带的 bringIntoView 会把它滚到那个位置 ——
                    // 正好被底栏挡住。把底栏高度从视口里扣掉，框架自己算出来的落点就是对的，
                    // 我们不需要任何自定义滚动（动画也由框架给）。
                    val barHeight = if (bottomBar != null) padding.calculateBottomPadding() else 0.dp
                    val barPx = with(density) { barHeight.roundToPx().toFloat() }
                    val gapPx = with(density) { 8.dp.roundToPx().toFloat() }
                    val contentPadding = pageContentPadding(
                        innerPadding = padding,
                        extraBottom = extraBottom,
                        horizontal = horizontalPadding,
                        barReserved = barHeight,
                    )
                    // 用官方 `LocalBringIntoViewSpec` 把"底栏遮挡"告诉框架：
                    // 框架自己算滚动量、自己给动画，我们只改一句"可视下边界在哪儿"。
                    // （不去自己写滚动循环 —— 那样既重复实现，又会在基线上游变化时失真。）
                    val bringIntoViewSpec = remember(gapPx) {
                        object : BringIntoViewSpec {
                            override fun calculateScrollDistance(
                                offset: Float,
                                size: Float,
                                containerSize: Float,
                            ): Float {
                                // containerSize 是滚动容器的整块高度（底栏盖在它上面），
                                // 扣掉底栏与 8dp 呼吸位才是用户真正看得见的下边界。
                                // 容器高度已经由 `padding(bottom = barHeight)` 扣掉了底栏，
                                // 这里只再留一个呼吸位。
                                val visible = containerSize - gapPx
                                val overflow = (offset + size) - visible
                                return when {
                                    overflow > 0f -> overflow
                                    offset < 0f -> offset
                                    else -> 0f
                                }
                            }
                        }
                    }
                    CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = barHeight)
                            .pageScrollModifiers(scrollBehavior),
                        // 顶栏自己画 overscroll 的联动效果（band / 按钮材质），
                        // 列表再叠一层库的 overscroll 会出现两套回弹。官方每个玻璃页都这样做：
                        // `MainPage.kt:142`、`GlassPage.kt:544`、`AboutPage.kt:317`。
                        overscrollEffect = null,
                        // 顶部改用 contentPadding：内容才能滚到顶栏下面被玻璃采样到。
                        contentPadding = contentPadding,
                    ) {
                        if (largeHeader != null) {
                            item(key = "logoSpacer") {
                                // 头部内容高度 + 头部比列表多出来的顶部留白（40dp 相对 2dp）+ 呼吸
                                Spacer(Modifier.height(headerHeight + (headerPadTop - listTop) + 20.dp))
                            }
                        }
                        content()
                    }
                    // 官方 example 的每个列表页都挂 `VerticalScrollBar`（`MainPage.kt:341-346` 等）：
                    // 轨道 padding 用同一份 `contentPadding`，顶栏折叠交给 `topAppBarScrollBehavior`。
                    VerticalScrollBar(
                        adapter = rememberScrollBarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        trackPadding = contentPadding,
                        topAppBarScrollBehavior = scrollBehavior,
                    )
                    }
                }
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

    if (background != null) {
        // 背景铺在 Scaffold 外面：Scaffold 必须透明，否则会盖住整屏渐变。
        Box(Modifier.fillMaxSize()) {
            background(headerProgress)
            Scaffold(
                // 键盘弹出时整页（含底部主操作条）一起抬起来。edge-to-edge 下 `adjustResize`
                // 不会真的缩窗口，IME insets 必须自己消费 —— 官方示例也是给 Scaffold 挂这个
                // （`ComponentPage.kt:148`、`AppContent.kt:655/703`）。
                modifier = Modifier.imePadding(),
                containerColor = Color.Transparent,
                topBar = topBar,
                bottomBar = { bottomBar?.invoke() },
                content = body,
            )
        }
    } else {
        Scaffold(
            // 同上：键盘弹出时整页抬起。
            modifier = Modifier.imePadding(),
            // 页面底色显式写出来，不依赖 Scaffold 的默认值（默认是 `surface`，现在与它同值）。
            // 必须与 `rememberPageBackdrop` 垫的那一层、底部主操作条的遮挡色是**同一个**
            // `pageBaseColor()`：三处只要有一处走偏，玻璃/操作条就会在页面上留一块色差方块。
            containerColor = pageBaseColor(),
            topBar = topBar,
            bottomBar = { bottomBar?.invoke() },
            content = body,
        )
    }
}
