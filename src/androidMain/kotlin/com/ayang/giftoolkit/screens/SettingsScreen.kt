package com.ayang.giftoolkit.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.ayang.giftoolkit.ui.ContentBackdropBox
import com.ayang.giftoolkit.ui.GroupCard
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.LeadingIcon
import com.ayang.giftoolkit.ui.PageTopBar
import com.ayang.giftoolkit.ui.rememberGlassScope
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun SettingsScreen(
    app: AppState,
    onOpenTheme: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val glass = rememberGlassScope()
    val listState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { listState.canScrollBackward } }

    Scaffold(
        topBar = {
            PageTopBar(
                title = "设置",
                glass = glass,
                isScrolled = isScrolled,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        // 正文在录制层里，顶栏画在录制层外面（兄弟节点）——
        // 这个结构调整是为玻璃服务的前提，不是随手加的 Box。
        ContentBackdropBox(glass) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                // 顶部留白改用 contentPadding：内容才能滚到顶栏下面被玻璃采样到
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 2.dp,
                    bottom = padding.calculateBottomPadding() + 128.dp,
                ),
            ) {
            item { GroupHeader("外观") }
            item {
                GroupCard {
                    // 主题相关全部收进独立页：设置首页只留入口，
                    // 和 KernelSU / InstallerX 的组织方式一致，也避免这页越堆越长。
                    ArrowPreference(
                        title = "主题",
                        summary = "主题模式、AMOLED、玻璃与预测性返回",
                        startAction = { LeadingIcon(MiuixIcons.Theme) },
                        onClick = onOpenTheme,
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
}
