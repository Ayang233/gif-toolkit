package com.ayang.giftoolkit.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ayang.giftoolkit.ui.GroupCard
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.LeadingIcon
import com.ayang.giftoolkit.ui.Os4ListPage
import com.ayang.giftoolkit.ui.rememberGlassScope
import com.ayang.giftoolkit.ui.thirdPartyLibraries
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.File
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 第三方组件列表。整页只有一种许可（Apache-2.0），点进去看正文。 */
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    onOpen: (Int) -> Unit,
) {
    val glass = rememberGlassScope()
    Os4ListPage(
        title = "开源许可",
        glass = glass,
        subtitle = "共 ${thirdPartyLibraries.size} 个第三方组件",
        onBack = onBack,
        reserveNavBar = false,
    ) {
        item { GroupHeader("第三方组件") }
        item {
            GroupCard {
                thirdPartyLibraries.forEachIndexed { index, entry ->
                    ArrowPreference(
                        title = entry.name,
                        summary = "${entry.owner} · ${entry.license}",
                        startAction = { LeadingIcon(MiuixIcons.Glass.Light.File) },
                        onClick = { onOpen(index) },
                    )
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
    val glass = rememberGlassScope()
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

    Os4ListPage(
        title = entry?.name ?: "开源许可",
        glass = glass,
        subtitle = entry?.license ?: "",
        onBack = onBack,
        extraBottom = 24.dp,
        horizontalPadding = 16.dp,
        reserveNavBar = false,
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

/**
 * 许可证原文是按 80 列硬换行的，直接塞进 Text 会得到一截一截的断句
 * （"reproduction," 单独一行、"by" 挂着）。这里按空行切段、段内把换行压成空格，
 * 交给 Compose 自己按宽度排版；段与段之间保留空行。
 */
private fun normalizeLicense(raw: String): String = raw
    .replace("\r\n", "\n")
    .split(Regex("\n\\s*\n"))
    .joinToString("\n\n") { paragraph -> paragraph.replace(Regex("\\s*\n\\s*"), " ").trim() }
