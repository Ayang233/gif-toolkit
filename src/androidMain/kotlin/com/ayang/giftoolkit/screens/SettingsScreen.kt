package com.ayang.giftoolkit.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.ayang.giftoolkit.AppState
import com.ayang.giftoolkit.ThemeMode
import com.ayang.giftoolkit.ui.GroupCard
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.LeadingIcon
import com.ayang.giftoolkit.ui.SegmentedControl
import com.ayang.giftoolkit.ui.SwitchRow
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun SettingsScreen(
    app: AppState,
    onOpenAbout: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val modes = ThemeMode.entries

    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
                largeTitle = "设置",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = 2.dp, bottom = 128.dp),
        ) {
            // 主题：三段式分段控件，比"点一下轮换到下一个"直观得多，
            // 也和系统「主题设置」页的形态一致。直接放在页面底色上（不进卡片），
            // 浅灰凹槽在白色页面上才读得出来。
            item { GroupHeader("主题") }
            item {
                SegmentedControl(
                    options = modes.map { it.label },
                    selectedIndex = modes.indexOf(app.themeMode).coerceAtLeast(0),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    onSelect = { index -> app.themeMode = modes[index] },
                )
            }

            item { GroupHeader("外观") }
            item {
                GroupCard {
                    SwitchRow(
                        title = "动态取色",
                        summary = "用 Monet 从壁纸生成整套配色",
                        checked = app.monetEnabled,
                        onChange = { app.monetEnabled = it },
                        icon = MiuixIcons.Background,
                    )
                    SwitchRow(
                        title = "玻璃材质",
                        summary = "底栏使用 OS4 玻璃，低端机可关",
                        checked = app.glassEnabled,
                        onChange = { app.glassEnabled = it },
                        icon = MiuixIcons.Layers,
                    )
                }
            }

            item { GroupHeader("关于") }
            item {
                GroupCard {
                    // 版本 / 导出位置 / 隐私这类内容收进独立的关于页，
                    // 设置页只留一行入口 —— 与 KernelSU 的「关于」一致。
                    ArrowPreference(
                        title = "关于",
                        summary = "版本、源代码与开源许可",
                        startAction = { LeadingIcon(MiuixIcons.Info) },
                        onClick = onOpenAbout,
                    )
                }
            }

        }
    }
}
