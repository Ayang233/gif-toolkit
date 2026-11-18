package com.ayang.giftoolkit.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ayang.giftoolkit.ui.GroupCard
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.Os4ListPage
import com.ayang.giftoolkit.ui.licenseEntries
import com.ayang.giftoolkit.ui.licenseGroups
import com.ayang.giftoolkit.ui.rememberGlassScope
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 开源许可（统一入口）。
 *
 * 关于页只有这一个许可入口：本项目自身的 GPL-3.0 与第三方依赖的 Apache-2.0 在同一页按分组列出，
 * 点任意一行进 [LicenseTextScreen] 看正文。上一轮是「开源许可 / 第三方许可」两个入口、
 * 两个页面，内容还各占一处；合并后路由也只剩 `Route.Licenses` + `Route.License(index)`。
 *
 * 入口行不带前置图标：与关于页同样的排版（InstallerX Revived 的 `MiuixNavigationItemWidget`
 * 就是 `ArrowPreference(title, summary)`，无 `startAction`）。
 */
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    onOpen: (Int) -> Unit,
) {
    val glass = rememberGlassScope()
    Os4ListPage(
        title = "开源许可",
        glass = glass,
        subtitle = "GPL-3.0 · ${licenseEntries.size} 项许可与声明",
        onBack = onBack,
        reserveNavBar = false,
    ) {
        // 分组下标要落到扁平清单上，才能直接当 Route.License 的参数用。
        var index = 0
        licenseGroups.forEach { group ->
            val start = index
            index += group.entries.size
            item(key = "h_${group.title}") { GroupHeader(group.title) }
            item(key = "c_${group.title}") {
                GroupCard {
                    group.entries.forEachIndexed { offset, entry ->
                        ArrowPreference(
                            title = entry.name,
                            summary = "${entry.owner} · ${entry.license}",
                            onClick = { onOpen(start + offset) },
                        )
                    }
                }
            }
        }
        item(key = "note") {
            Text(
                text = "第三方组件按各自的原始许可分发，其许可不因本工程的整体许可而改变。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 4.dp),
            )
        }
    }
}

/** 许可清单里某一项的许可正文。 */
@Composable
fun LicenseDetailScreen(
    index: Int,
    onBack: () -> Unit,
) {
    val entry = licenseEntries.getOrNull(index)
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
 * 本项目许可证、第三方许可证、NOTICE 共用这一个阅读页，传 asset 即可。
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
        // 与其它页面的正文同一条左边线：卡片外边距 12dp + 行内边距 16dp = 28dp。
        horizontalPadding = 28.dp,
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
