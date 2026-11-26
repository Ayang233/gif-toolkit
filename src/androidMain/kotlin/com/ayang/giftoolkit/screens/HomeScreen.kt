package com.ayang.giftoolkit.screens

import androidx.compose.runtime.Composable
import com.ayang.giftoolkit.ui.GroupCard
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.LeadingIcon
import com.ayang.giftoolkit.ui.Os4ListPage
import com.ayang.giftoolkit.ui.Tool
import com.ayang.giftoolkit.ui.rememberGlassScope
import top.yukonga.miuix.kmp.preference.ArrowPreference

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
    val glass = rememberGlassScope()
    Os4ListPage(
        title = "GifToolkit",
        glass = glass,
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

@Composable
private fun ToolRow(tool: Tool, onOpen: (Tool) -> Unit) {
    ArrowPreference(
        title = tool.title,
        summary = tool.summary,
        startAction = { LeadingIcon(tool.icon) },
        onClick = { onOpen(tool) },
    )
}
