package com.ayang.giftoolkit.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ayang.giftoolkit.ui.ContentBackdropBox
import com.ayang.giftoolkit.ui.PageTopBar
import com.ayang.giftoolkit.ui.rememberGlassScope
import com.ayang.giftoolkit.ui.GroupCard
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.LeadingIcon
import com.ayang.giftoolkit.ui.thirdPartyLibraries
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 第三方组件列表。整页只有一种许可（Apache-2.0），点进去看正文。 */
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    onOpen: (Int) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val glass = rememberGlassScope()
    val listState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { listState.canScrollBackward } }
    Scaffold(
        topBar = {
            PageTopBar(
                title = "开源许可",
                glass = glass,
                isScrolled = isScrolled,
                subtitle = "共 ${thirdPartyLibraries.size} 个第三方组件",
                scrollBehavior = scrollBehavior,
                onBack = onBack,
            )
        },
    ) { padding ->
        ContentBackdropBox(glass) {
            LazyColumn(
                state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
        ) {
            item { GroupHeader("第三方组件") }
            item {
                GroupCard {
                    thirdPartyLibraries.forEachIndexed { index, entry ->
                        ArrowPreference(
                            title = entry.name,
                            summary = "${entry.owner} · ${entry.license}",
                            startAction = { LeadingIcon(MiuixIcons.File) },
                            onClick = { onOpen(index) },
                        )
                    }
                }
            }
        }
        }
    }
}

/** 许可正文。文本来自 assets，不是硬编码在代码里。 */
@Composable
fun LicenseDetailScreen(
    index: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val glass = rememberGlassScope()
    val listState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { listState.canScrollBackward } }
    val entry = thirdPartyLibraries.getOrNull(index)
    val body = remember(index) {
        entry?.let {
            runCatching {
                context.assets.open(it.asset).bufferedReader().use { reader ->
                    normalizeLicense(reader.readText())
                }
            }.getOrNull()
        }.orEmpty()
    }

    Scaffold(
        topBar = {
            PageTopBar(
                title = entry?.name ?: "开源许可",
                glass = glass,
                isScrolled = isScrolled,
                subtitle = entry?.license ?: "",
                scrollBehavior = scrollBehavior,
                onBack = onBack,
            )
        },
    ) { padding ->
        ContentBackdropBox(glass) {
            LazyColumn(
                state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            item {
                Text(
                    text = body.ifBlank { "许可证正文读取失败。" },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        }
    }
}

/**
 * 许可证原文是按 80 列硬换行的，直接塞进 Text 会得到一截一截的断句
 * （"reproduction," 单独一行、"by" 挂着）。这里按空行切段、段内把换行压成空格，
 * 交给 Compose 自己按宽度排版；段与段之间保留空行。
 */
private fun normalizeLicense(raw: String): String = raw
    .replace("\r\n", "\n")
    .split(Regex("\n\\s*\n"))
    .joinToString("\n\n") { paragraph -> paragraph.replace(Regex("\\s*\n\\s*"), " ").trim() }
