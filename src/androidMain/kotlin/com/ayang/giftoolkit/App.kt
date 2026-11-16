package com.ayang.giftoolkit

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.ayang.giftoolkit.ui.LocalWideLayout
import com.ayang.giftoolkit.ui.Tool
import com.ayang.giftoolkit.ui.backTransition
import com.ayang.giftoolkit.ui.isGlassSupported
import com.ayang.giftoolkit.ui.pageColor
import com.ayang.giftoolkit.ui.rememberBottomBarClearance
import com.ayang.giftoolkit.ui.rememberDeviceCornerRadius
import com.ayang.giftoolkit.ui.rememberWideLayout
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
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
import top.yukonga.miuix.kmp.window.WindowDialog

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

    CompositionLocalProvider(
        LocalGlassEnabled provides glassEnabled,
        LocalAmoled provides app.amoledEnabled,
        LocalWideLayout provides wide,
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

            // 同 GlassScope：录制层先铺一层页面底色，空隙才不是透明的（否则底栏采样区会透出内容）。
            val backdropColorForRecord = pageColor()
            val backdrop = rememberLayerBackdrop {
                drawRect(backdropColorForRecord)
                drawContent()
            }
            val stack: NavBackStack = remember { navBackStackOf(Route.Root) }
            var tab by rememberSaveable { mutableIntStateOf(0) }
            var confirmExit by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            // 三个一级页用 Pager 承载：切 tab 时是连续位移（还能左右滑），
            // 不是原来 Crossfade 那种纯淡入淡出 —— 这是"切页面生硬"的直接原因。
            val pagerState = rememberPagerState(initialPage = tab, pageCount = { 3 })
            // 滑动结束后把 tab 拉回一致；用 settledPage 而不是 currentPage，
            // 否则快速滑动时底栏高亮会在中途页码之间闪。
            LaunchedEffect(pagerState.settledPage) {
                if (tab != pagerState.settledPage) tab = pagerState.settledPage
            }
            // 宽屏侧栏固定展开（见下面的 showToggle = false）。
            val railState = rememberGlassNavigationRailState(initialExpanded = true)

            val pop: () -> Unit = { stack.removeLastOrNull() }

            // 切 tab：先置高亮，再启动弹簧滚动 —— 指示器与页面同时起步，
            // 用库自己的 springAnimateToPage（stiffness 322.2），不再手写 EaseInOut 曲线。
            val goTab: (Int) -> Unit = { index ->
                tab = index
                scope.launch { pagerState.springAnimateToPage(index) }
            }

            // NavDisplay 的预测性返回只在 stack.size > 1 时启用（源码 enabled = backStack.size > 1），
            // 到栈底就交还给系统 → 直接 finish。这里在栈底接管：先回第一个 tab，再问是否退出。
            BackHandler(enabled = stack.size <= 1) {
                if (tab != 0) {
                    goTab(0)
                } else {
                    confirmExit = true
                }
            }

            // 页面底色见 ui/Widgets.kt 的 pageColor()：卡片是 surfaceContainer，
            // 页面必须是另一个角色，两套配色（Miuix 静态 / Monet）才都有层次。
            Box(Modifier.fillMaxSize().background(pageColor())) {
                // 宽屏：玻璃侧栏**叠放**在内容之上，不参与 Row 的宽度分配。
                // GlassNavigationRail 的根节点是 `fillMaxSize()`（它自己决定面板位置：
                // 距左边 Margin、宽度 ExpandedWidth），所以一旦放进 Row 当兄弟，
                // 它会吃掉整行宽度，把内容挤成 0 宽 —— 实机上就是一片空白。
                // 官方 AppContent 同样是「内容 Box 在外、Rail 叠在内容 Box 里」。
                //
                // 因此内容要自己让出侧栏宽度，见 railInset。
                val railInset = if (wide) {
                    GlassNavigationRailDefaults.ExpandedWidth + GlassNavigationRailDefaults.Margin
                } else {
                    0.dp
                }
                // 只有内容区域参与背景录制；玻璃条画在它之上，才不会采样到自己。
                // 关闭玻璃时完全不录制 —— 每帧整屏录制本身开销不小，没必要白付。
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(start = railInset)
                        .then(if (glassEnabled) Modifier.layerBackdrop(backdrop) else Modifier),
                ) {
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
                }

                // 侧栏必须与上面的录制层是**兄弟**节点（同一 Box 的相邻子项），
                // 与底栏同理：进了录制层就会采样到自己 → libhwui 原生栈溢出闪退。
                if (wide) {
                    GlassNavigationRail(
                        items = navItems,
                        selectedIndex = tab,
                        onSelect = { goTab(it) },
                        backdrop = if (glassEnabled) backdrop else null,
                        state = railState,
                        // 宽屏上固定展开：折叠形态会变成"停在顶部居中"的胶囊，
                        // 正好压在顶栏标题上。GIF 助手的设置项不多，不需要这个交互。
                        showToggle = false,
                    )
                }

                // 底栏是画在录制层**外面**的浮层，不是内容的一部分。
                // 参数一律用库的默认值：高度 54dp、指示器色、选中色、以及底部悬浮外边距
                // （库内部按导航栏高度算：≤16 → 28dp，≥28 → 跟随，中间 → 12+导航栏）。
                // 之前我们手算 maxOf(24, inset+8) 并传 defaultWindowInsetsPadding = false，
                // 结果同一台设备上比官方高出一截 —— 这类几何不该自己维护。
                //
                // 宽屏下完全不挂载（而不是 visible=false 后留着）：两个导航器同时存在时，
                // 底栏虽然淡出了仍在布局里，会干扰 Rail 的手势区域。
                if (!wide) {
                    GlassNavigationBar(
                        items = navItems,
                        selectedIndex = tab,
                        onSelect = { goTab(it) },
                        // 关掉玻璃时由库的 fallback 兜底：仍然是浮起胶囊 + 阴影 + 指示器，
                        // 不会塌回通栏白底条。
                        backdrop = if (glassEnabled) backdrop else null,
                        visible = stack.size <= 1,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            // 左右各留 24dp：胶囊必须退进屏幕圆角里，否则大圆角机型两端的
                            // 圆弧会被屏幕边缘切掉；间距也不能太窄，否则三条 tab 挤成一条长带。
                            .padding(horizontal = 24.dp),
                    )
                }
            }

            // 退出确认用库的 **WindowDialog**（系统窗口版），不用 GlassDialog。
            //
            // 本想用 GlassDialog（官方 GlassPage.kt:378），但它是 **Scaffold 作用域**组件：
            // `MiuixPopupUtils.DialogLayout` 从 `LocalDialogStates` 取宿主列表来绘制，
            // 而这个 local 由 Scaffold 提供并负责渲染。GifApp 这一层只有 NavDisplay + 浮层底栏，
            // **没有 Scaffold**，于是对话框被加进一个没人绘制的列表 —— 静默不显示，
            // 实测按返回毫无反应。`renderInRootScaffold` 的 true/false 都救不了（两者都要宿主）。
            //
            // 要给 GlassDialog 造宿主就得在 GifApp 外包一层 Scaffold，那会改变所有页面的
            // inset 与背景录制层，风险远大于收益。WindowDialog 是独立的系统 Dialog 窗口，
            // 不依赖 Compose 宿主，行为稳定 —— AGENTS.md 里"确认用 WindowDialog"也是这个理由。
            WindowDialog(
                show = confirmExit,
                title = "退出 GIF 助手？",
                summary = "正在进行的任务会被中断。",
                onDismissRequest = { confirmExit = false },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { confirmExit = false },
                        modifier = Modifier.weight(1f),
                        insideMargin = PaddingValues(vertical = 12.dp),
                    ) {
                        Text("继续使用", style = MiuixTheme.textStyles.button)
                    }
                    Button(
                        onClick = {
                            confirmExit = false
                            (context as? Activity)?.finish()
                        },
                        modifier = Modifier.weight(1f),
                        insideMargin = PaddingValues(vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(contentColor = MiuixTheme.colorScheme.error),
                    ) {
                        Text("退出", style = MiuixTheme.textStyles.button)
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

