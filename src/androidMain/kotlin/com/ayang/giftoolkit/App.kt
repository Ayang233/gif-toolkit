package com.ayang.giftoolkit

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ayang.giftoolkit.data.SettingsStore
import com.ayang.giftoolkit.screens.AboutScreen
import com.ayang.giftoolkit.screens.HomeScreen
import com.ayang.giftoolkit.screens.LibraryScreen
import com.ayang.giftoolkit.screens.LicenseDetailScreen
import com.ayang.giftoolkit.screens.LicensesScreen
import com.ayang.giftoolkit.screens.SettingsScreen
import com.ayang.giftoolkit.screens.ThemeScreen
import com.ayang.giftoolkit.screens.ToolScreen
import com.ayang.giftoolkit.ui.AnimatedMiuixTheme
import com.ayang.giftoolkit.ui.LocalAmoled
import com.ayang.giftoolkit.ui.LocalBottomBarClearance
import com.ayang.giftoolkit.ui.LocalGlassEnabled
import com.ayang.giftoolkit.ui.LocalRailInset
import com.ayang.giftoolkit.ui.LocalTopBarTitleAlpha
import com.ayang.giftoolkit.ui.LocalRootBackdrop
import com.ayang.giftoolkit.ui.LocalWideLayout
import com.ayang.giftoolkit.ui.Tool
import com.ayang.giftoolkit.ui.backTransition
import com.ayang.giftoolkit.ui.isGlassSupported
import com.ayang.giftoolkit.ui.pageColor
import com.ayang.giftoolkit.ui.rememberBottomBarClearance
import com.ayang.giftoolkit.ui.rememberDeviceCornerRadius
import com.ayang.giftoolkit.ui.rememberPageBackdrop
import com.ayang.giftoolkit.ui.rememberWideLayout
import com.ayang.giftoolkit.ui.shouldExpandNavigationRail
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassNavigationRail
import top.yukonga.miuix.kmp.glass.GlassNavigationRailDefaults
import top.yukonga.miuix.kmp.glass.rememberGlassNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.All
import top.yukonga.miuix.kmp.icon.glass.FolderOutline
import top.yukonga.miuix.kmp.icon.glass.SettingsOutline
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride
import top.yukonga.miuix.kmp.utils.springAnimateToPage

@Composable
fun GifApp() {
    val context = LocalContext.current
    val app = remember(context) { AppState(SettingsStore(context)) }

    val mode = when {
        !app.monetEnabled -> when (app.themeMode) {
            ThemeMode.System -> ColorSchemeMode.System
            ThemeMode.Light -> ColorSchemeMode.Light
            ThemeMode.Dark -> ColorSchemeMode.Dark
        }

        else -> when (app.themeMode) {
            ThemeMode.System -> ColorSchemeMode.MonetSystem
            ThemeMode.Light -> ColorSchemeMode.MonetLight
            ThemeMode.Dark -> ColorSchemeMode.MonetDark
        }
    }
    val controller = remember(mode) { ThemeController(colorSchemeMode = mode) }

    // 玻璃只有一个决策点：用户开关 && 设备能力。
    // Miuix 的能力检测只是版本号判断（SDK>=31），查不出某台 GPU 上模糊会不会翻车，
    // 所以"关掉玻璃"必须一直留给用户当降级手段。
    val glassEnabled = app.glassEnabled && isGlassSupported()

    // 宽屏：≥840dp（或横向 ≥600dp）改用侧边 Rail，底栏让位。
    // 阈值与官方 utils/AdaptiveUtils.kt 一致，保证和生态里其它应用同时切换。
    val wide = rememberWideLayout()
    // 底栏占位高度由这里单点计算、单点下发；宽屏没有底栏，所以是 0。
    val bottomClearance = rememberBottomBarClearance()

    // 页面栈与一级导航的在场条件在这里就定，供下面的 CompositionLocal 使用。
    val stack: NavBackStack = remember { navBackStackOf(Route.Root) }
    // 一级导航是否在场：只有 Rail 需要这个判据（底栏已经跟着一级页走了，见下）。
    val showPrimaryNav = stack.size <= 1
    // 侧栏展开与否跟官方同一档阈值（≥1200dp 才展开，见 ui/Adaptive.kt 的 shouldExpandNavigationRail）。
    // 手机横屏约 900dp，官方在那里是**折叠成顶部胶囊**（只显示文字），不是左侧面板；
    // 之前写死 `initialExpanded = true` + `showToggle = false`，等于自己造了一套"常驻展开"的规则。
    val expandRail = shouldExpandNavigationRail()
    val railState = rememberGlassNavigationRailState(initialExpanded = expandRail)
    LaunchedEffect(expandRail) { railState.expanded = expandRail }
    val railVisible = wide && showPrimaryNav
    // 内容让位：跟着侧栏的形变进度走（官方 AppContent.kt:219-229 同款式子），
    // 折叠态 progress = 0，页面不需要让出任何宽度。
    // 下发的是**函数**：progress 逐帧变化，这里若解包成 Dp，`LocalRailInset` 的每个消费者
    // （页面内边距、底部主操作条）会在侧栏展开的每一帧重组。读取统一推到量测/绘制期。
    val railInset: () -> Dp = remember(railVisible) {
        if (railVisible) {
            ({
                (GlassNavigationRailDefaults.ExpandedWidth + GlassNavigationRailDefaults.Margin) * railState.progress
            })
        } else {
            ({ 0.dp })
        }
    }

    CompositionLocalProvider(
        LocalGlassEnabled provides glassEnabled,
        LocalAmoled provides app.amoledEnabled,
        LocalWideLayout provides wide,
        LocalRailInset provides railInset,
        // 折叠态（顶部居中胶囊）与居中标题抢同一条位置：标题透明度跟着折叠进度走，
        // 官方 `AppContent.kt:208-214` 就是这么接的。二级页没有 Rail，标题恒为 1。
        LocalTopBarTitleAlpha provides { if (railVisible) railState.progress else 1f },
        LocalBottomBarClearance provides if (wide) 0.dp else bottomClearance,
    ) {
        AnimatedMiuixTheme(controller) {
            val view = LocalView.current

            // 状态栏 / 导航栏图标跟随主题明暗反色：之前窗口主题固定浅色，浅色下图标还是白的，几乎看不见
            val isDarkSurface = MiuixTheme.colorScheme.background.luminance() < 0.5f
            SideEffect {
                val window = (context as? Activity)?.window ?: return@SideEffect
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDarkSurface
                    isAppearanceLightNavigationBars = !isDarkSurface
                }
            }

            // 一级页（三个 tab）共用的录制层：录 **Pager 以外** 的内容，见下面 entry<Route.Root>。
            // 为什么不在这一层录：底栏要跟着一级页一起被二级页盖住（官方底栏就在一级页内部），
            // 所以底栏必须进 NavDisplay；一旦它成为录制层的子节点就会采样到自己 → libhwui 闪退。
            // 把录制层下沉到一级页内部、底栏做它的兄弟，两个条件才同时满足。
            val rootBackdrop = rememberPageBackdrop()

            var tab by rememberSaveable { mutableIntStateOf(0) }
            val scope = rememberCoroutineScope()
            // 三个一级页用 Pager 承载：切 tab 时是连续位移（还能左右滑），
            // 不是原来 Crossfade 那种纯淡入淡出 —— 这是"切页面生硬"的直接原因。
            val pagerState = rememberPagerState(initialPage = tab, pageCount = { 3 })
            // 滑动结束后把 tab 拉回一致；用 settledPage 而不是 currentPage，
            // 否则快速滑动时底栏高亮会在中途页码之间闪。
            LaunchedEffect(pagerState.settledPage) {
                if (tab != pagerState.settledPage) tab = pagerState.settledPage
            }
            val pop: () -> Unit = { stack.removeLastOrNull() }

            // 切 tab：先置高亮，再启动弹簧滚动 —— 指示器与页面同时起步，
            // 用库自己的 springAnimateToPage（stiffness 322.2），不再手写 EaseInOut 曲线。
            val goTab: (Int) -> Unit = { index ->
                tab = index
                scope.launch { pagerState.springAnimateToPage(index) }
            }

            // NavDisplay 的预测性返回只在 stack.size > 1 时启用（源码 enabled = backStack.size > 1），
            // 到栈底就交还给系统 → 直接 finish。这里只接管一件事：栈底时先回第一个 tab。
            //
            // 回到 tab 0 之后**不再拦**（enabled 里带上 tab != 0）：交还给系统，
            // 预测性返回 → Activity finish → 回桌面。官方示例 `MainScreenBackHandler`
            // （AppContent.kt:910）就是这么写的：`isBackEnabled` 带 `selectedPage != 0`，
            // 一级页按返回直接退到桌面，没有二次确认。之前那句「退出 GIF 助手？」确认弹窗
            // 是本项目自己加的拦截，与官方行为不一致，本轮移除（见 docs/os4-round5-audit.md）。
            BackHandler(enabled = stack.size <= 1 && tab != 0) {
                goTab(0)
            }

            // 页面底色见 ui/Widgets.kt 的 pageColor()：卡片是 surfaceContainer，
            // 页面必须是另一个角色，两套配色（Miuix 静态 / Monet）才都有层次。
            //
            // 这一层 Scaffold 是**弹层宿主**，不负责布局（contentWindowInsets = 0、底色透明）。
            // 官方 `GlassPageHost`（GlassPage.kt:652）就是这层壳：GlassDialog / GlassBottomSheet /
            // GlassDropdownPreference 的 `renderInRootScaffold` 默认为 true，会去找**最外层** Scaffold 的
            // `LocalRootDialogStates` 并由它绘制。没有这层壳时它们退回页面自己的 Scaffold ——
            // 而页面在 HorizontalPager 里，弹层的窗口坐标和页面录制层的坐标会差整整一页宽
            // （实测 offset.x = -1080），玻璃采样落在录制区之外，面板只剩材质、看不到背景。
            //
            // 顺带解决压盖：弹层现在画在最外层，永远在底栏之上，不再需要 `modalOverlayOpen`
            // 那种"开面板就把导航让出去"的状态联动。
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0),
            ) {
                Box(Modifier.fillMaxSize().background(pageColor())) {
                    // 转场档位来自主题设置；圆角裁剪半径取设备真实屏幕圆角 ——
                    // 之前没传 effects，默认 cornerClipRadius = 0.dp，等于把圆角裁剪关掉了。
                    val pageBackground = pageColor()
                    val pageTransition = remember(app.backAnimation, app.backExitDirection) {
                        backTransition(app.backAnimation, app.backExitDirection)
                    }
                    val cornerRadius = rememberDeviceCornerRadius()
                    val navEffects = remember(cornerRadius, pageBackground) {
                        NavDisplayEffects(
                            enableCornerClip = true,
                            cornerClipRadius = cornerRadius,
                            dimAmount = 0.5f,
                            backdropColor = pageBackground,
                        )
                    }
                    NavDisplay(
                        backStack = stack,
                        onBack = pop,
                        transition = pageTransition,
                        effects = navEffects,
                    ) {
                        entry<Route.Root> {
                            // 一级页 = Pager + 底栏，两者都在这一层。
                            //
                            // 这是官方 `CompactScreenLayout`（AppContent.kt:581-627）的结构：
                            // 底栏属于一级页，二级页压栈时它跟着一级页一起被新页面**盖住**
                            // （官方实测：进入二级页的过渡帧里底栏原地不动、随后被新页覆盖，
                            // 没有任何自己的缩放/淡出动画；退出时也是被"揭开"）。
                            // 之前底栏画在 NavDisplay 外面并靠 `stack.size` 挂载/卸载：
                            // 进二级页它在新页盖上来之前就消失，返回时又立刻冒在退出中的二级页之上。
                            Box(Modifier.fillMaxSize()) {
                                // 录制层只包 Pager，底栏做它的**兄弟** —— 底栏采样它、又不在它里面，
                                // 才不会采样到自己（AGENTS.md 第 1 条）。
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .then(if (glassEnabled) Modifier.layerBackdrop(rootBackdrop) else Modifier),
                                ) {
                                    // 弹层的采样源 = 这一层（Pager 之外、x 从 0 开始）。
                                    // 用页面自己的录制层不行：Pager 的每一页是按页宽错位的，
                                    // 页面内录制层上报的窗口坐标是"槽位坐标"（第 2 页 x = 1080），
                                    // 而弹层上报的是真实坐标 —— 两者相减正好差一页宽，采样全落在外面。
                                    CompositionLocalProvider(
                                        LocalRootBackdrop provides rootBackdrop.takeIf { glassEnabled },
                                    ) {
                                        // 吸附曲线与手势接管器必须共用**同一个** flingBehavior 实例，
                                        // 否则松手后的吸附和手势识别器算出来的目标页会是两套物理参数。
                                        // 注意 `PagerDefaults.flingBehavior` 本身就是 @Composable（内部已 remember），
                                        // 不能再套一层 remember。
                                        val flingBehavior = PagerDefaults.flingBehavior(
                                            state = pagerState,
                                            snapAnimationSpec = PagerNavigationSpringSpec,
                                        )
                                        HorizontalPager(
                                            state = pagerState,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                // Cross-Axis 接管：官方 example 的 pagerInterceptionMode 默认值就是
                                                // CrossAxis（AppState.kt:34 → 1），只有这条路径才是"首尾页拖不动"。
                                                //
                                                // 为什么原生 Pager 拖得动：MiuixTheme 全局注入了
                                                // `LocalOverscrollFactory = MiuixOverscrollFactory`（MiuixTheme.kt:40），
                                                // 原生 Pager 到边界时被拒的位移会交给 overscroll 工厂做弹性过冲 ——
                                                // 那个"还在动"是过冲动画，不是翻页。CrossAxis 模式下 Pager 自身的
                                                // scrollable 被整个移除（见下面的 userScrollEnabled = false），
                                                // 位移不再经过 overscroll 工厂，边界就是死的。
                                                .pagerGestureOverride(
                                                    pagerState = pagerState,
                                                    flingBehavior = flingBehavior,
                                                    mode = PagerInterceptionMode.CrossAxis,
                                                ),
                                            // 必须关掉：CrossAxis 靠 pagerGestureOverride 装的识别器驱动，
                                            // 两个识别器同时存在会互相抢手势。
                                            userScrollEnabled = false,
                                            // CrossAxis 下垂直滚动与速度交给页面内容自己处理（含跨页时）。
                                            pageNestedScrollConnection = PagerGestureNestedScrollConnection,
                                            verticalAlignment = Alignment.Top,
                                            // 只多留一页：0 会让首次横滑出现白帧（滑入中的页面还没组合），
                                            // 官方给 4 是因为它每页都轻；我们每页挂一个玻璃录制层，
                                            // 多留一页就多一次整屏 GPU 录制，所以取 1 而不是照抄 4。
                                            beyondViewportPageCount = 1,
                                            flingBehavior = flingBehavior,
                                        ) { index ->
                                            when (index) {
                                                0 -> HomeScreen(onOpen = { tool -> stack.add(Route.Tool(tool.id)) })
                                                1 -> LibraryScreen(app = app)
                                                else -> SettingsScreen(
                                                    app = app,
                                                    onOpenTheme = { stack.add(Route.Theme) },
                                                    onOpenAbout = { stack.add(Route.About) },
                                                )
                                            }
                                        }
                                    }
                                }

                                // 底栏：一级页自己的浮层。宽屏没有它（用 Rail），
                                // 二级页也不用管它 —— 它根本不在那些页面的组合里。
                                // 参数一律吃库的默认值（高度 54dp、指示器色、悬浮外边距），
                                // 几何不该我们维护。
                                if (!wide) {
                                    GlassNavigationBar(
                                        items = navItems,
                                        selectedIndex = tab,
                                        onSelect = { goTab(it) },
                                        // 关掉玻璃时由库的 fallback 兜底：仍然是浮起胶囊 + 阴影 + 指示器，
                                        // 不会塌回通栏白底条。
                                        backdrop = rootBackdrop.takeIf { glassEnabled },
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            // 左右各留 24dp：胶囊必须退进屏幕圆角里，否则大圆角机型两端的
                                            // 圆弧会被屏幕边缘切掉；间距也不能太窄，否则三条 tab 挤成一条长带。
                                            .padding(horizontal = 24.dp),
                                    )
                                }
                            }
                        }
                        entry<Route.Tool> { route ->
                            val tool = Tool.fromId(route.id)
                            if (tool != null) {
                                ToolScreen(
                                    tool = tool,
                                    onBack = pop,
                                    onExported = { app.libraryToken++ },
                                )
                            }
                        }
                        entry<Route.Theme> {
                            ThemeScreen(app = app, onBack = pop)
                        }
                        entry<Route.About> {
                            AboutScreen(
                                onBack = pop,
                                onOpenLicenses = { stack.add(Route.Licenses) },
                            )
                        }
                        entry<Route.Licenses> {
                            LicensesScreen(
                                onBack = pop,
                                onOpen = { index -> stack.add(Route.License(index)) },
                            )
                        }
                        entry<Route.License> { route ->
                            LicenseDetailScreen(index = route.index, onBack = pop)
                        }
                    }

                    // 侧栏必须与一级页的录制层**不是父子**关系：它采样那一层，自己不能在里面，
                    // 否则采样到自己 → libhwui 原生栈溢出闪退。
                    // 它在一级页外面，所以只在栈底（`showPrimaryNav`）挂载；
                    // 二级页按官方以外的约定不显示一级导航（见 docs/os4-round6-audit.md §1.1）。
                    if (railVisible) {
                        GlassNavigationRail(
                            items = navItems,
                            selectedIndex = tab,
                            onSelect = { goTab(it) },
                            backdrop = if (glassEnabled) rootBackdrop else null,
                            state = railState,
                            // 折叠态就是官方的"顶部居中胶囊"（只显示文字），左侧那个方块按钮
                            // 是库自带的展开/收起入口 —— 交回默认值，不再用 showToggle = false 把它藏起来。
                        )
                    }
                }
            }
        }
    }
}

/**
 * 底栏 / 侧边栏的两组目标。
 *
 * 图标用 Glass 家族的 Outline 档（`FolderOutline` / `SettingsOutline`）：
 * 这是同一套图形里的细线权重，和首页的定色档同源，比混用旧的 `extended` 线框更统一。
 * 注意新版把 `Glass.Folder` / `Glass.Settings` 改名成了 `*Outline` / `*Fill`。
 */
private val navItems = listOf(
    GlassNavigationItem(icon = MiuixIcons.Glass.All, label = "工具", contentDescription = "工具"),
    GlassNavigationItem(icon = MiuixIcons.Glass.FolderOutline, label = "作品", contentDescription = "作品"),
    GlassNavigationItem(icon = MiuixIcons.Glass.SettingsOutline, label = "设置", contentDescription = "设置"),
)

