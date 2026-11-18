package com.ayang.giftoolkit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.ayang.giftoolkit.BuildConfig
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/**
 * 当前是不是可调试包（debug）。
 *
 * 用 `BuildConfig.DEBUG_UI` 而不是 `ApplicationInfo.FLAG_DEBUGGABLE`：前者是**编译期常量**，
 * release 包里被内联成 `false`，R8 会把 `if (debuggable)` 的两个分支（`Route.ButtonColors`
 * 的目的地、主题页的那组入口）连同本文件这个对比页一起删掉 —— 可以用
 * "release dex 里搜不到「按钮配色对比」"来验收。
 * FLAG_DEBUGGABLE 是运行时读的，R8 推不出取值，页面会留在包里，只能挡住入口。
 */
@Composable
fun rememberIsDebuggable(): Boolean = BuildConfig.DEBUG_UI

/** 一种禁用态配色：轨道底色 + 文字色，外加它用的是哪两个主题角色。 */
private data class ButtonScheme(
    val id: String,
    val trackRole: String,
    val contentRole: String,
    val track: Color,
    val content: Color,
)

/**
 * 开始按钮（`ProgressButton`）禁用态配色的四方案对比页 —— **仅 debug**。
 *
 * 为什么要单独一页：无素材时 `form.canRun = false`，按钮落到 `ProgressButton` 的
 * `disabled*` 三色上。第九轮的实测是 `disabledSecondaryVariant #F2F2F2` +
 * `disabledOnPrimaryButton #FFFFFF` = **1.12:1**，几乎看不见字。四种候选配对分别来自
 * MIUIX 主按钮、MIUIX 次按钮、预览框、以及"保留主色语义"的折中，只有并排看才选得出来。
 *
 * 约束（照用户要求）：
 * - 四个按钮都用**真的** [ProgressButton]，不是 Box + Text 拼的；
 * - 尺寸、圆角、字号、内边距、禁用状态全部一致，只有颜色不同；
 * - 颜色从当前主题实时取，不写死第九轮浅色主题的值 —— 换深色 / Monet 会跟着变；
 * - 对比度是**算出来的**（文字先按自己的 alpha 合成到轨道色上，再算 WCAG 相对亮度比）。
 */
@Composable
fun ButtonColorPreviewScreen(onBack: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val glass = rememberGlassScope()
    val schemes = listOf(
        ButtonScheme(
            id = "A",
            trackRole = "disabledPrimaryButton",
            contentRole = "disabledOnPrimaryButton",
            track = colors.disabledPrimaryButton,
            content = colors.disabledOnPrimaryButton,
        ),
        ButtonScheme(
            id = "B",
            trackRole = "disabledSecondaryVariant",
            contentRole = "disabledOnSecondaryVariant",
            track = colors.disabledSecondaryVariant,
            content = colors.disabledOnSecondaryVariant,
        ),
        ButtonScheme(
            id = "C",
            trackRole = "pageColor()",
            contentRole = "onSurfaceVariantSummary",
            track = pageColor(),
            content = colors.onSurfaceVariantSummary,
        ),
        ButtonScheme(
            id = "D",
            trackRole = "disabledPrimaryButton",
            contentRole = "onSurfaceVariantSummary",
            track = colors.disabledPrimaryButton,
            content = colors.onSurfaceVariantSummary,
        ),
    )

    Os4ListPage(
        title = "按钮配色对比",
        glass = glass,
        subtitle = "无素材（禁用态）· 仅调试包",
        onBack = onBack,
        reserveNavBar = false,
        extraBottom = 24.dp,
    ) {
        item {
            NoteText(
                "已采用：方案 A（2026-11-18 敲定）—— disabledPrimaryButton + disabledOnPrimaryButton。" +
                    "下面四组保留作对照记录，正式按钮的取值与 A 一致。",
            )
        }

        schemes.forEach { scheme ->
            item(key = "scheme_${scheme.id}") {
                GroupHeader(
                    "${scheme.id}${if (scheme.id == "A") "（已采用）" else ""} · " +
                        "${scheme.trackRole} + ${scheme.contentRole}",
                )
                GroupCard {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "${hex(scheme.track)} / ${hex(scheme.content)} · 对比度 ${contrast(scheme)}",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            SchemeButton(
                                track = scheme.track,
                                content = scheme.content,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        item {
            NoteText(
                "四种方案都走同一个 ProgressButton、同一条 PrimaryActionBar 尺寸；" +
                    "按钮真实处于 disabled（不可点、语义仍是按钮），没有为了让字清楚把它改成 enabled。",
            )
        }
    }
}

/**
 * 一个真实的禁用态 [ProgressButton]。
 *
 * 参数与 [ActionButton]（正式按钮）完全一致：`fillMaxWidth()`、默认圆角 16dp、
 * 默认最小高度 50dp、`MiuixTheme.textStyles.button`。唯一变量是颜色。
 */
@Composable
private fun SchemeButton(
    track: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    ProgressButton(
        progress = 0f,
        onClick = {},
        enabled = false,
        colors = ProgressButtonDefaults.progressButtonColors(
            // 正式按钮在可用态用 primary/onPrimary；这里只关心禁用态，
            // 但另外三色也一并指到同一组值，避免"切到 enabled 时"出现第四种颜色。
            trackColor = track,
            progressColor = track,
            contentColor = content,
            disabledTrackColor = track,
            disabledProgressColor = track,
            disabledContentColor = content,
        ),
        modifier = modifier,
    ) {
        Text(
            text = "开始生成",
            style = MiuixTheme.textStyles.button,
            color = content,
        )
    }
}

/**
 * `#AARRGGBB`；不透明时省略 alpha 段写成 `#RRGGBB`。
 *
 * alpha 必须显示出来：`onSurfaceVariantSummary` 是 0x99000000（60% 黑），
 * 只写 `#000000` 会让人以为它是纯黑字、对比度算高了。
 */
private fun hex(color: Color): String {
    val argb = color.toArgb()
    val alpha = (argb ushr 24) and 0xFF
    return if (alpha == 0xFF) {
        String.format(Locale.US, "#%06X", argb and 0xFFFFFF)
    } else {
        String.format(Locale.US, "#%02X%06X", alpha, argb and 0xFFFFFF)
    }
}

/**
 * 文字与轨道的对比度（WCAG 2.1 相对亮度比）。
 *
 * 文字色可能带 alpha（`onSurfaceVariantSummary` 是 0x99），先按它自己的 alpha
 * 合成到轨道色上再算 —— 直接拿半透明色算会虚高。
 */
private fun contrast(scheme: ButtonScheme): String {
    val fg = scheme.content.compositeOver(scheme.track)
    val l1 = fg.luminance()
    val l2 = scheme.track.luminance()
    val ratio = (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
    return String.format(Locale.US, "%.2f:1", ratio)
}
