package com.ayang.giftoolkit.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.ayang.giftoolkit.ui.GroupCard
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.Tool
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主页：单列工具列表。
 *
 * 之前是「2 列网格卡片」，一列只剩 ~150dp，长标题被截成「动态照片转 …」，
 * 而且格子里的图标块 + 文字挤在一起，更像九宫格而不是 Miuix。
 * 现在换成 Miuix 的标准行式列表（图标 + 标题 + 说明 + 箭头），
 * 一行一个功能，说明能完整显示，也更像系统设置页。
 */
@Composable
fun HomeScreen(onOpen: (Tool) -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = "GIF 助手",
                largeTitle = "GIF 助手",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            // 底部留出玻璃底栏的高度，滚到末尾时最后一行不会被胶囊压住
            contentPadding = PaddingValues(top = 2.dp, bottom = 128.dp),
        ) {
            Tool.grouped.forEach { (group, tools) ->
                item(key = "h_$group") { GroupHeader(group) }
                item(key = "c_$group") {
                    GroupCard {
                        tools.forEach { tool ->
                            ToolRow(tool, onOpen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolRow(tool: Tool, onOpen: (Tool) -> Unit) {
    ArrowPreference(
        title = tool.title,
        summary = tool.summary,
        startAction = {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                // Miuix 的行内间距是 8dp，线框图标再补 4dp 才不至于和标题粘在一起
                modifier = Modifier.padding(end = 4.dp).size(24.dp),
                tint = MiuixTheme.colorScheme.onBackground,
            )
        },
        onClick = { onOpen(tool) },
    )
}
