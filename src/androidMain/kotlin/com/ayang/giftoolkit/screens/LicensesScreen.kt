package com.ayang.giftoolkit.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ayang.giftoolkit.ui.GroupCard
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
        subtitle = "共 ${thirdPartyLibraries.size} 个组件",
        onBack = onBack,
        reserveNavBar = false,
    ) {
        // 只有一个分组，不需要再顶一个"第三方组件"分组标题 —— 那是给多分组页面用的。
        // 顶部间距由卡片的 top padding 补，和 GroupHeader 的视觉留白接近。
        item {
            GroupCard(modifier = Modifier.padding(top = 8.dp)) {
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

/** 第三方组件清单里某一项的许可正文。 */
@Composable
fun LicenseDetailScreen(
    index: Int,
    onBack: () -> Unit,
) {
    val entry = thirdPartyLibraries.getOrNull(index)
    LicenseTextScreen(
        asset = entry?.asset.orEmpty(),
        title = entry?.name ?: "开源许可",
        license = entry?.license.orEmpty(),
        onBack = onBack,
    )
}

/**
 * 许可正文。文本来自 assets，不是硬编码在代码里。
 *
 * 从 [LicenseDetailScreen] 拆出来的原因：关于页多了一行「开源许可」，
 * 正文是本项目自己的 GPL-3.0，不在第三方清单里。两处共用这一个阅读页，
 * 传 asset 即可，不必再复制一遍正文渲染。
 */
@Composable
fun LicenseTextScreen(
    asset: String,
    title: String,
    license: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val glass = rememberGlassScope()
    val body = remember(asset) {
        if (asset.isBlank()) {
            ""
        } else {
            runCatching {
                context.assets.open(asset).bufferedReader().use { reader ->
                    normalizeLicense(reader.readText())
                }
            }.getOrNull().orEmpty()
        }
    }

    Os4ListPage(
        title = title,
        glass = glass,
        subtitle = license,
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
