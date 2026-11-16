package com.ayang.giftoolkit.screens

import androidx.compose.runtime.Composable
import com.ayang.giftoolkit.AppState
import com.ayang.giftoolkit.ui.GroupCard
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.LeadingIcon
import com.ayang.giftoolkit.ui.Os4ListPage
import com.ayang.giftoolkit.ui.rememberAppVersion
import com.ayang.giftoolkit.ui.rememberGlassScope
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Info
import top.yukonga.miuix.kmp.icon.glass.Theme
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * 设置首页。
 *
 * 大标题下带版本号副标题，是官方设置页的做法（截图里是 `Settings` + `v1.0.9 (1291)`）：
 * 既给了页面一个稳定的第二行，也省掉了"关于页里才能看版本"的一次跳转。
 */
@Composable
fun SettingsScreen(
    app: AppState,
    onOpenTheme: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val glass = rememberGlassScope()
    val version = rememberAppVersion()

    Os4ListPage(
        title = "设置",
        glass = glass,
        subtitle = version,
    ) {
        item { GroupHeader("外观") }
        item {
            GroupCard {
                // 主题相关全部收进独立页：设置首页只留入口，
                // 和 KernelSU / InstallerX 的组织方式一致，也避免这页越堆越长。
                ArrowPreference(
                    title = "主题",
                    summary = "主题模式、AMOLED、玻璃与预测性返回",
                    startAction = { LeadingIcon(MiuixIcons.Glass.Light.Theme) },
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
                    startAction = { LeadingIcon(MiuixIcons.Glass.Light.Info) },
                    onClick = onOpenAbout,
                )
            }
        }
    }
}
