package com.ayang.giftoolkit

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.ayang.giftoolkit.screens.LibraryScreen
import com.ayang.giftoolkit.screens.SettingsScreen
import com.ayang.giftoolkit.screens.ToolScreen
import com.ayang.giftoolkit.ui.Tool
import com.ayang.giftoolkit.ui.pageColor
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.All
import top.yukonga.miuix.kmp.icon.glass.Folder
import top.yukonga.miuix.kmp.icon.glass.Settings
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
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

    MiuixTheme(controller = controller) {
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

        val pop: () -> Unit = { stack.removeLastOrNull() }

        // NavDisplay 的预测性返回只在 stack.size > 1 时启用（源码 enabled = backStack.size > 1），
        // 到栈底就交还给系统 → 直接 finish。这里在栈底接管：先回第一个 tab，再问是否退出。
        BackHandler(enabled = stack.size <= 1) {
            if (tab != 0) tab = 0 else confirmExit = true
        }

        // 页面底色见 ui/Widgets.kt 的 pageColor()：卡片是 surfaceContainer，
        // 页面必须是另一个角色，两套配色（Miuix 静态 / Monet）才都有层次。
        Box(Modifier.fillMaxSize().background(pageColor())) {
            // 只有内容区域参与背景录制；玻璃条画在它之上，才不会采样到自己。
            // 关闭玻璃时完全不录制 —— 每帧整屏录制本身开销不小，没必要白付。
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (app.glassEnabled) Modifier.layerBackdrop(backdrop) else Modifier),
            ) {
                NavDisplay(
                    backStack = stack,
                    onBack = pop,
                    transition = NavTransitions.MiuixDefault,
                ) {
                    entry<Route.Root> {
                        Crossfade(
                            targetState = tab,
                            animationSpec = tween(durationMillis = 220),
                            label = "rootTab",
                        ) { index ->
                            when (index) {
                                0 -> HomeScreen(onOpen = { tool -> stack.add(Route.Tool(tool.id)) })
                                1 -> LibraryScreen(app = app)
                                else -> SettingsScreen(
                                    app = app,
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
            GlassNavigationBar(
                items = navItems,
                selectedIndex = tab,
                onSelect = { index -> tab = index },
                backdrop = if (app.glassEnabled) backdrop else null,
                visible = stack.size <= 1,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 左右各留 12dp：胶囊必须退进屏幕圆角里。之前贴着屏幕边缘，
                    // 在 2.5D 玻璃 / 大圆角机型上两端的圆弧会被屏幕边缘直接切掉。
                    .padding(horizontal = 12.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
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

private val navItems = listOf(
    GlassNavigationItem(icon = MiuixIcons.Glass.All, label = "工具", contentDescription = "工具"),
    GlassNavigationItem(icon = MiuixIcons.Glass.Folder, label = "作品", contentDescription = "作品"),
    GlassNavigationItem(icon = MiuixIcons.Glass.Settings, label = "设置", contentDescription = "设置"),
)

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
