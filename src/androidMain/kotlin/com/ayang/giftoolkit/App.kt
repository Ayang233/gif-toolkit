package com.ayang.giftoolkit

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ayang.giftoolkit.data.SettingsStore
import com.ayang.giftoolkit.screens.AboutScreen
import com.ayang.giftoolkit.screens.HomeScreen
import com.ayang.giftoolkit.screens.LicenseDetailScreen
import com.ayang.giftoolkit.screens.LicensesScreen
import com.ayang.giftoolkit.screens.ThemeScreen
import com.ayang.giftoolkit.screens.LibraryScreen
import com.ayang.giftoolkit.screens.SettingsScreen
import com.ayang.giftoolkit.screens.ToolScreen
import com.ayang.giftoolkit.ui.AnimatedMiuixTheme
import com.ayang.giftoolkit.ui.LocalAmoled
import com.ayang.giftoolkit.ui.LocalGlassEnabled
import com.ayang.giftoolkit.ui.LocalNavBarTier
import com.ayang.giftoolkit.ui.LocalPanelTier
import com.ayang.giftoolkit.ui.LocalTopBarTier
import com.ayang.giftoolkit.ui.SurfaceTier
import com.ayang.giftoolkit.ui.backTransition
import com.ayang.giftoolkit.ui.cardColor
import com.ayang.giftoolkit.ui.clampedBy
import com.ayang.giftoolkit.ui.deviceSurfaceCeiling
import com.ayang.giftoolkit.ui.pageColor
import com.ayang.giftoolkit.ui.rememberDeviceCornerRadius
import com.ayang.giftoolkit.ui.Tool
import kotlin.math.abs
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.All
import top.yukonga.miuix.kmp.icon.glass.Folder
import top.yukonga.miuix.kmp.icon.glass.Settings
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

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

    // 表面档位：用户偏好 × 设备能力，一次算清，往下只用结果（AGENTS.md 硬约束）。
    // 之前只判 isRenderEffectSupported()，漏了 AGSL —— 能模糊但 AGSL 不可用的机型上，
    // 玻璃材质层会被画成黑块，这是真机黑块的候选原因之一。
    val ceiling = remember { deviceSurfaceCeiling() }
    val masterOn = app.glassEnabled
    val topTier = if (masterOn) app.topBarTier.clampedBy(ceiling) else SurfaceTier.Solid
    val navTier = if (masterOn) app.navBarTier.clampedBy(ceiling) else SurfaceTier.Solid
    val panelTier = if (masterOn) app.panelTier.clampedBy(ceiling) else SurfaceTier.Solid
    // 只要有一个组件还在采样背景，内容区就得挂录制层
    val needBackdrop = topTier != SurfaceTier.Solid ||
        navTier != SurfaceTier.Solid ||
        panelTier != SurfaceTier.Solid

    CompositionLocalProvider(
        LocalGlassEnabled provides needBackdrop,
        LocalTopBarTier provides topTier,
        LocalNavBarTier provides navTier,
        LocalPanelTier provides panelTier,
        LocalAmoled provides app.amoledEnabled,
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

            val backdrop = rememberLayerBackdrop()
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

            val pop: () -> Unit = { stack.removeLastOrNull() }

            // NavDisplay 的预测性返回只在 stack.size > 1 时启用（源码 enabled = backStack.size > 1），
            // 到栈底就交还给系统 → 直接 finish。这里在栈底接管：先回第一个 tab，再问是否退出。
            BackHandler(enabled = stack.size <= 1) {
                if (tab != 0) {
                    tab = 0
                    scope.launch { pagerState.animateToTab(0) }
                } else {
                    confirmExit = true
                }
            }

            // 页面底色见 ui/Widgets.kt 的 pageColor()：卡片是 surfaceContainer，
            // 页面必须是另一个角色，两套配色（Miuix 静态 / Monet）才都有层次。
            Box(Modifier.fillMaxSize().background(pageColor())) {
                // 只有内容区域参与背景录制；玻璃条画在它之上，才不会采样到自己。
                // 关闭玻璃时完全不录制 —— 每帧整屏录制本身开销不小，没必要白付。
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (needBackdrop) Modifier.layerBackdrop(backdrop) else Modifier),
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
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                // 只留当前页与正在滑动的那一页：每页都挂着玻璃录制层，
                                // 多留一页就多一次整屏 GPU 录制。
                                beyondViewportPageCount = 0,
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

                // 进出工具页用 visible 做「缩小 + 淡出」，而不是整块移除 ——
                // 直接移除会让底栏"啪"地消失，和 NavDisplay 的转场对不上。
                //
                // 参数对齐 InstallerX 的 SettingsFloatingBottomBar：
                // 左右 24dp（原来 12dp 是"过宽"的直接原因）、
                // 底部 max(24, 导航栏 + 8)、高度 60dp（库默认 54 在只有三项时
                // 会让指示块变成细长椭圆）、指示器用主色而不是黑 6%（黑 6% 在白底上是一条灰带）。
                val darkBar = MiuixTheme.colorScheme.background.luminance() < 0.5f
                val navBottomInset = WindowInsets.navigationBars
                    .asPaddingValues().calculateBottomPadding()
                val navBottomMargin = maxOf(24.dp, navBottomInset + 8.dp)
                GlassNavigationBar(
                    items = navItems,
                    selectedIndex = tab,
                    onSelect = { index ->
                        // 先把高亮置为目标值再启动滚动：指示器和页面位移同时起步，
                        // 不是等页面滚完才追上去（InstallerX 的 MainPagerState 做法）
                        tab = index
                        scope.launch { pagerState.animateToTab(index) }
                    },
                    backdrop = if (navTier == SurfaceTier.Solid) null else backdrop,
                    // 实色档由库的 fallback 兜底：仍然是浮起胶囊 + 阴影 + 主色指示器，
                    // 不会塌回通栏白底条。
                    height = 60.dp,
                    alpha = if (navTier == SurfaceTier.Light) 0.9f else 1f,
                    material = if (navTier == SurfaceTier.Light) null else GlassNavigationBarDefaults.material(),
                    indicatorColor = MiuixTheme.colorScheme.primary.copy(
                        alpha = if (darkBar) 0.22f else 0.15f,
                    ),
                    indicatorPressedColor = MiuixTheme.colorScheme.primary.copy(
                        alpha = if (darkBar) 0.30f else 0.22f,
                    ),
                    selectedColor = MiuixTheme.colorScheme.primary,
                    unselectedColor = MiuixTheme.colorScheme.onBackground,
                    visible = stack.size <= 1,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // 左右各留 24dp：胶囊必须退进屏幕圆角里，否则大圆角机型两端的
                        // 圆弧会被屏幕边缘切掉；间距也不能太窄，否则三条 tab 挤成一条长带。
                        .padding(horizontal = 24.dp)
                        .padding(bottom = navBottomMargin),
                    defaultWindowInsetsPadding = false,
                )
            }

            if (confirmExit) {
                ExitConfirm(
                    onDismiss = { confirmExit = false },
                    onConfirm = {
                        confirmExit = false
                        (context as? Activity)?.finish()
                    },
                )
            }
        }
    }
}

private val navItems = listOf(
    GlassNavigationItem(icon = MiuixIcons.Glass.All, label = "工具", contentDescription = "工具"),
    GlassNavigationItem(icon = MiuixIcons.Glass.Folder, label = "作品", contentDescription = "作品"),
    GlassNavigationItem(icon = MiuixIcons.Glass.Settings, label = "设置", contentDescription = "设置"),
)

/**
 * 一级页切换的滚动曲线（照搬 InstallerX `MainPagerState.animateToPage`）。
 *
 * 不用 `animateScrollToPage`：它走 Pager 默认的弹簧，跨两页时会"先黏一下再弹"，
 * 而且这条曲线和底栏指示器自己的弹簧对不上，看起来是两段动作。
 * 这里换成 EaseInOut + 时长随距离线性增长（100ms/页 + 100ms），
 * 用 `MutatePriority.UserInput` 保证用户手指一碰就能接管。
 *
 * 调用方必须**先**把 tab 置为目标值再启动滚动 —— 指示器与页面同时起步，
 * 这正是 KernelSU / InstallerX 那种"跟手"的来源。
 */
private suspend fun PagerState.animateToTab(target: Int) {
    if (target == currentPage && currentPageOffsetFraction == 0f) return
    scroll(MutatePriority.UserInput) {
        val info = layoutInfo
        val pageSize = info.pageSize + info.pageSpacing
        val distanceInPages = target - currentPage - currentPageOffsetFraction
        val scrollPixels = distanceInPages * pageSize
        if (scrollPixels == 0f) return@scroll
        val duration = 100 * abs(target - currentPage).coerceAtLeast(1) + 100
        var previous = 0f
        animate(
            initialValue = 0f,
            targetValue = scrollPixels,
            animationSpec = tween(durationMillis = duration, easing = EaseInOut),
        ) { value, _ ->
            previous += scrollBy(value - previous)
        }
    }
    if (currentPage != target) scrollToPage(target)
}

@Composable
private fun ExitConfirm(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.windowDimming)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.padding(horizontal = 36.dp),
            cornerRadius = 28.dp,
            insideMargin = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
            colors = CardDefaults.defaultColors(color = cardColor()),
        ) {
            Text(
                text = "退出 GIF 助手？",
                style = MiuixTheme.textStyles.title3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "正在进行的任务会被中断。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            // Miuix 的对话框动作是竖排通栏按钮（见 HyperStar / KernelSU 的弹窗），不是左右并排
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                cornerRadius = 14.dp,
                insideMargin = PaddingValues(0.dp),
            ) { Text("继续使用", style = MiuixTheme.textStyles.button) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                cornerRadius = 14.dp,
                insideMargin = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(contentColor = MiuixTheme.colorScheme.error),
            ) { Text("退出", style = MiuixTheme.textStyles.button) }
        }
    }
}
