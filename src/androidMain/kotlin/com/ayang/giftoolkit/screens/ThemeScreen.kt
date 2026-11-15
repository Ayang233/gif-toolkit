package com.ayang.giftoolkit.screens

import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.ayang.giftoolkit.AppState
import com.ayang.giftoolkit.BackAnimation
import com.ayang.giftoolkit.BackExitDirection
import com.ayang.giftoolkit.ThemeMode
import com.ayang.giftoolkit.ui.ContentBackdropBox
import com.ayang.giftoolkit.ui.GroupCard
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.NoteText
import com.ayang.giftoolkit.ui.PageTopBar
import com.ayang.giftoolkit.ui.SurfaceTier
import com.ayang.giftoolkit.ui.SwitchRow
import com.ayang.giftoolkit.ui.rememberGlassScope
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

/**
 * 主题设置（独立页）。
 *
 * 结构参照 KernelSU / InstallerX 的主题页，但**去掉了它们的选择项**：
 * GIF 助手只有 Miuix 一套引擎、只做 OS4 一版 UI，不做 OS3 / 引擎切换，
 * 所以这里没有「UI 引擎」「Miuix UI 版本」两行。
 *
 * 预测性返回整组只在 API 34+ 显示：低版本系统没有这个交互，露出来只会让人困惑。
 */
@Composable
fun ThemeScreen(
    app: AppState,
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val glass = rememberGlassScope()
    val listState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { listState.canScrollBackward } }

    val modes = ThemeMode.entries
    val animations = BackAnimation.entries

    Scaffold(
        topBar = {
            PageTopBar(
                title = "主题设置",
                glass = glass,
                isScrolled = isScrolled,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, "返回", Modifier.size(24.dp))
                    }
                },
            )
        },
    ) { padding ->
        ContentBackdropBox(glass) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 2.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item { GroupHeader("Miuix") }
                item {
                    GroupCard {
                        // 三档是离散值，用锚定下拉而不是分段控件：
                        // 和系统「主题模式」的交互一致，也避免三个字长不一的分段里字被压扁。
                        WindowDropdownPreference(
                            items = modes.map { it.label },
                            selectedIndex = modes.indexOf(app.themeMode).coerceAtLeast(0),
                            title = "主题模式",
                            onSelectedIndexChange = { app.themeMode = modes[it] },
                        )
                        SwitchRow(
                            title = "AMOLED 纯黑背景",
                            summary = "深色模式下页面底用纯黑，卡片仍比页面亮一档",
                            checked = app.amoledEnabled,
                            onChange = { app.amoledEnabled = it },
                        )
                        SwitchRow(
                            title = "自定义颜色",
                            summary = "用 Monet 从壁纸生成整套配色",
                            checked = app.monetEnabled,
                            onChange = { app.monetEnabled = it },
                        )
                    }
                }

                item { GroupHeader("界面") }
                item {
                    GroupCard {
                        SwitchRow(
                            title = "玻璃材质",
                            summary = "总开关。关掉后下面三个组件都按实色渲染",
                            checked = app.glassEnabled,
                            onChange = { app.glassEnabled = it },
                        )
                        // 组件级三档：粒度和 HyperOS「桌面设置里 Dock 与搜索栏各自配质感」一致。
                        // 用下拉而不是分段控件：三个选项是离散档位，和上面「主题模式」的交互统一。
                        val tiers = SurfaceTier.entries
                        WindowDropdownPreference(
                            items = tiers.map { it.label },
                            selectedIndex = tiers.indexOf(app.topBarTier).coerceAtLeast(0),
                            title = "顶栏质感",
                            summary = app.topBarTier.summary,
                            enabled = app.glassEnabled,
                            onSelectedIndexChange = { app.topBarTier = tiers[it] },
                        )
                        WindowDropdownPreference(
                            items = tiers.map { it.label },
                            selectedIndex = tiers.indexOf(app.navBarTier).coerceAtLeast(0),
                            title = "底栏质感",
                            summary = app.navBarTier.summary,
                            enabled = app.glassEnabled,
                            onSelectedIndexChange = { app.navBarTier = tiers[it] },
                        )
                        WindowDropdownPreference(
                            items = tiers.map { it.label },
                            selectedIndex = tiers.indexOf(app.panelTier).coerceAtLeast(0),
                            title = "弹层质感",
                            summary = app.panelTier.summary,
                            enabled = app.glassEnabled,
                            onSelectedIndexChange = { app.panelTier = tiers[it] },
                        )
                    }
                }
                item {
                    // 档位会被设备能力钳制，说清楚，免得用户以为设置没生效
                    NoteText("设备不支持模糊或玻璃着色器时，实际档位会自动降到可用的一档。")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    item { GroupHeader("预测性返回") }
                    item {
                        GroupCard {
                            WindowDropdownPreference(
                                items = animations.map { it.label },
                                selectedIndex = animations.indexOf(app.backAnimation).coerceAtLeast(0),
                                title = "预测性返回动画",
                                summary = app.backAnimation.summary,
                                onSelectedIndexChange = { app.backAnimation = animations[it] },
                            )
                            // 退出方向只对「缩放」有意义：其余档位要么跟随手势物理方向，
                            // 要么根本没有横向位移，给了选项也不会生效。
                            if (app.backAnimation == BackAnimation.Scale) {
                                val directions = BackExitDirection.entries
                                WindowDropdownPreference(
                                    items = directions.map { it.label },
                                    selectedIndex = directions.indexOf(app.backExitDirection).coerceAtLeast(0),
                                    title = "退出方向",
                                    onSelectedIndexChange = { app.backExitDirection = directions[it] },
                                )
                            }
                        }
                    }
                    item {
                        NoteText(
                            "选「无」会同时关闭系统级预测性返回（下次启动应用生效）。" +
                                "部分系统不支持该开关，此时开关不生效。",
                        )
                    }
                }
            }
        }
    }
}
